package org.openmrs.module.rwandaemr.radiology;

import ca.uhn.hl7v2.AcknowledgmentCode;
import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.model.v23.message.ACK;
import ca.uhn.hl7v2.model.v23.message.ORM_O01;
import lombok.Setter;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Encounter;
import org.openmrs.Order;
import org.openmrs.TestOrder;
import org.openmrs.api.OrderService;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.Daemon;
import org.openmrs.event.Event;
import org.openmrs.event.EventListener;
import org.openmrs.module.DaemonToken;
import org.openmrs.util.ConfigUtil;
import org.openmrs.util.PrivilegeConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.jms.MapMessage;
import javax.jms.Message;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Listener that can be registered with Order events and which will attempt to convert this order to hl7 and send
 */
@Component
public class RadiologyOrderEventListener implements EventListener {

	protected final Log log = LogFactory.getLog(getClass());
	@Setter private static DaemonToken daemonToken;
	private boolean isSubscribed = false;

	private final OrderService orderService;
	private final OrderToORMO01Translator orderToORMTranslator;
	private final RadiologyConfig radiologyConfig;

	/**
	 * Fulfiller comment recorded on a cancelled order once its cancellation has been sent to PACS
	 */
	public static final String CANCELLATION_SENT = "Cancellation sent to PACS";

	private final Object studyLock = new Object();

	// Runtime properties to tune sending to PACS, with defaults that suit the RIS
	public static final String PROPERTY_CONNECT_TIMEOUT = "rwandaemr.radiology.outgoingHL7ConnectTimeoutMs";
	public static final String PROPERTY_ACK_TIMEOUT = "rwandaemr.radiology.outgoingHL7AckTimeoutMs";
	public static final String PROPERTY_MIN_INTERVAL = "rwandaemr.radiology.outgoingHL7MinIntervalMs";
	public static final String PROPERTY_RETRY_COUNT = "rwandaemr.radiology.outgoingHL7RetryCount";
	public static final String PROPERTY_RETRY_DELAY = "rwandaemr.radiology.outgoingHL7RetryDelayMs";
	private static final int DEFAULT_CONNECT_TIMEOUT_MS = 10000;
	private static final int DEFAULT_ACK_TIMEOUT_MS = 30000;
	private static final int DEFAULT_MIN_INTERVAL_MS = 1000;
	private static final int DEFAULT_RETRY_COUNT = 2;
	private static final int DEFAULT_RETRY_DELAY_MS = 3000;

	private static final int MLLP_START_BLOCK = 0x0b;
	private static final int MLLP_END_BLOCK = 0x1c;
	private static final int MLLP_CARRIAGE_RETURN = 0x0d;

	private final HapiContext hapiContext = new DefaultHapiContext();
	private final Object sendLock = new Object();
	private volatile long lastConnectionClosedAt = 0;

	public RadiologyOrderEventListener(@Autowired OrderService orderService,
										  @Autowired OrderToORMO01Translator orderToORMTranslator,
										  @Autowired RadiologyConfig radiologyConfig) {
		this.orderService = orderService;
		this.orderToORMTranslator = orderToORMTranslator;
		this.radiologyConfig = radiologyConfig;
	}

	public String getHost() {
		return RadiologyConfig.getOutgoingHl7Host();
	}

	public Integer getPort() {
		return RadiologyConfig.getOutgoingHl7Port();
	}

	public void setup() {
		if (getHost() != null && getPort() != null) {
			log.info("Sending radiology orders is enabled.  Sending to " + getHost() + ":" + getPort());
			for (Event.Action action : Event.Action.values()) {
				Event.subscribe(Order.class, action.name(), this);
			}
			isSubscribed = true;
		}
		else {
			log.info("Sending radiology orders to PACS is not enabled");
		}
	}

	public void teardown() {
		if (isSubscribed) {
			for (Event.Action action : Event.Action.values()) {
				Event.unsubscribe(Order.class, action, this);
			}
		}
	}

	@Override
	public void onMessage(Message message) {
		try {
			if (!(message instanceof MapMessage)) {
				throw new IllegalArgumentException("message is not a MapMessage");
			}
			MapMessage mapMessage = (MapMessage) message;
			String orderUuid = mapMessage.getString("uuid");
			if (StringUtils.isBlank(orderUuid)) {
				throw new IllegalArgumentException("unable to retrieve order uuid from message: " + message);
			}
			Daemon.runInDaemonThreadAndWait(() -> handleOrder(orderUuid, mapMessage), daemonToken);
		}
		catch (Exception e) {
			log.error("Error processing radiology order event", e);
			throw new RuntimeException(e);
		}
	}

	public void handleOrder(String orderUuid, MapMessage mapMessage) {
		// The order is loaded inside the lock so that it reflects any accession number recorded by the event of another
		// test of the same study that was handled just before
		synchronized (studyLock) {
			Order order = orderService.getOrderByUuid(orderUuid);
			if (order == null) {
				throw new IllegalArgumentException("Order with uuid " + orderUuid + " not found");
			}
			if (!(order instanceof TestOrder)) {
				log.trace("Order is not a TestOrder: " + orderUuid);
				return;
			}
			TestOrder testOrder = (TestOrder) order;
			if (!orderToORMTranslator.isRadiologyOrder(testOrder)) {
				log.trace("TestOrder is not for a radiology orderable: " + orderUuid);
				return;
			}
			if (testOrder.getAction() == Order.Action.DISCONTINUE) {
				// Stopping the previous order fires its own event, which sends the cancellation for that order
				log.debug("Not sending discontinue order " + testOrder.getOrderNumber() + " to PACS");
				return;
			}
			if (orderToORMTranslator.isCancelled(testOrder)) {
				cancelStudy(testOrder);
			}
			else if (StringUtils.isNotBlank(testOrder.getAccessionNumber())) {
				// Already sent, either on its own or combined with other tests.  Updates do not send it again.
				log.debug("Radiology order " + testOrder.getOrderNumber() + " was already sent to PACS in study "
						+ testOrder.getAccessionNumber());
			}
			else {
				sendStudy(getOrdersForNewStudy(testOrder), null);
			}
		}
	}

	/**
	 * Sends the order to PACS on request, for example from the order page: a new study if it was never sent, the study
	 * it is part of again if it was, or a cancellation if it was cancelled
	 */
	public void sendRadiologyOrderMessage(TestOrder testOrder) {
		synchronized (studyLock) {
			if (orderToORMTranslator.isCancelled(testOrder)) {
				cancelStudy(testOrder);
			}
			else {
				List<TestOrder> orders = getOrdersToSendWith(testOrder);
				sendStudy(orders, testOrder.getAccessionNumber());
			}
		}
	}

	/**
	 * @return the orders sent to PACS in one study with the given order: the active orders of the study it was sent in,
	 * or, if it was never sent, the orders a new study would be made of
	 */
	public List<TestOrder> getOrdersToSendWith(TestOrder testOrder) {
		if (StringUtils.isNotBlank(testOrder.getAccessionNumber())) {
			List<TestOrder> study = getStudyOrders(testOrder, false);
			return study.isEmpty() ? Collections.singletonList(testOrder) : study;
		}
		return getOrdersForNewStudy(testOrder);
	}

	/**
	 * @return the accession number the given orders are, or would be, sent to PACS with
	 */
	public String getAccessionNumber(List<TestOrder> orders) {
		String accessionNumber = orders.get(0).getAccessionNumber();
		return StringUtils.isNotBlank(accessionNumber) ? accessionNumber : orders.get(0).getOrderNumber();
	}

	/**
	 * @return the given order, plus, when combining is on, the other active radiology orders of the same modality in the
	 * same encounter that have not been sent yet, in the order they were placed
	 */
	protected List<TestOrder> getOrdersForNewStudy(TestOrder testOrder) {
		List<TestOrder> orders = new ArrayList<>();
		orders.add(testOrder);
		Encounter encounter = testOrder.getEncounter();
		String modality = orderToORMTranslator.getModalityCode(testOrder);
		if (radiologyConfig.isCombineOrdersByModality() && encounter != null && modality != null) {
			for (Order o : encounter.getOrders()) {
				if (o.equals(testOrder) || !(o instanceof TestOrder) || o.getAction() == Order.Action.DISCONTINUE) {
					continue;
				}
				if (orderToORMTranslator.isCancelled(o) || StringUtils.isNotBlank(o.getAccessionNumber())) {
					continue;
				}
				if (modality.equals(orderToORMTranslator.getModalityCode(o))) {
					orders.add((TestOrder) o);
				}
			}
			orders.sort(Comparator.comparing(TestOrder::getOrderId, Comparator.nullsLast(Comparator.naturalOrder())));
		}
		return orders;
	}

	/**
	 * @return the orders sent in the same study as the given order, which share its accession number.  A study only ever
	 * contains orders from one encounter.
	 */
	protected List<TestOrder> getStudyOrders(TestOrder testOrder, boolean includeCancelled) {
		String accessionNumber = testOrder.getAccessionNumber();
		List<TestOrder> study = new ArrayList<>();
		Set<Order> candidates = testOrder.getEncounter() == null ? Collections.singleton(testOrder) :
				testOrder.getEncounter().getOrders();
		for (Order o : candidates) {
			if (o instanceof TestOrder && accessionNumber.equals(o.getAccessionNumber())
					&& (includeCancelled || !orderToORMTranslator.isCancelled(o))) {
				study.add((TestOrder) o);
			}
		}
		if (!study.contains(testOrder) && (includeCancelled || !orderToORMTranslator.isCancelled(testOrder))) {
			study.add(testOrder);
		}
		study.sort(Comparator.comparing(TestOrder::getOrderId, Comparator.nullsLast(Comparator.naturalOrder())));
		return study;
	}

	/**
	 * Registers the orders with PACS as one study, then records the accession number on each order.  A new study uses the
	 * order number of its first order as the accession number.
	 * @throws RuntimeException if PACS could not be reached or did not accept the study
	 */
	protected void sendStudy(List<TestOrder> orders, String accessionNumber) {
		String accession = StringUtils.isNotBlank(accessionNumber) ? accessionNumber : orders.get(0).getOrderNumber();
		if (sendToPacs(orders, accession, "NW")) {
			for (TestOrder o : orders) {
				if (!accession.equals(o.getAccessionNumber())) {
					updateOrder(o, accession, o.getFulfillerComment());
				}
			}
		}
	}

	/**
	 * Cancels the study the order was sent in.  The RIS takes a study as a whole, so the whole study is cancelled, and
	 * any of its tests that are still active are then sent again as a new study.  Each cancelled order is marked once its
	 * cancellation is sent, so that later updates to it do not send it again.
	 */
	protected void cancelStudy(TestOrder testOrder) {
		if (CANCELLATION_SENT.equals(testOrder.getFulfillerComment())) {
			log.debug("Cancellation of radiology order " + testOrder.getOrderNumber() + " was already sent to PACS");
			return;
		}
		boolean wasSent = StringUtils.isNotBlank(testOrder.getAccessionNumber());
		List<TestOrder> study = wasSent ? getStudyOrders(testOrder, true) : Collections.singletonList(testOrder);
		String accession = wasSent ? testOrder.getAccessionNumber() : testOrder.getOrderNumber();
		if (sendToPacs(study, accession, "CA")) {
			List<TestOrder> remaining = new ArrayList<>();
			for (TestOrder o : study) {
				if (orderToORMTranslator.isCancelled(o)) {
					updateOrder(o, o.getAccessionNumber(), CANCELLATION_SENT);
				}
				else {
					remaining.add(o);
				}
			}
			if (!remaining.isEmpty()) {
				log.info("Sending the remaining tests of cancelled radiology study " + accession + " to PACS as a new study");
				sendStudy(remaining, remaining.get(0).getOrderNumber());
			}
		}
	}

	/**
	 * Sends the orders to PACS as one ORM^O01 message
	 * @return true if PACS acknowledged it, false if sending to PACS is not configured
	 * @throws RuntimeException if PACS could not be reached or did not accept the message
	 */
	protected boolean sendToPacs(List<TestOrder> orders, String accessionNumber, String orderControl) {
		if (getHost() == null || getPort() == null) {
			log.warn("Not sending radiology order message as host and port are required: " + getHost() + ":" + getPort());
			return false;
		}
		List<String> orderNumbers = new ArrayList<>();
		for (TestOrder o : orders) {
			orderNumbers.add(o.getOrderNumber());
		}
		log.info("Sending radiology order message to PACS: " + orderControl + " study " + accessionNumber
				+ " with order(s) " + orderNumbers);
		try {
			ORM_O01 hl7Message = orderToORMTranslator.toORM_O01(orders, accessionNumber, orderControl);
			if (log.isTraceEnabled()) {
				log.trace(hl7Message.encode());
			}
			ACK response = (ACK) sendWithRetry(hl7Message, getHost(), getPort());
			String ackCode = response.getMSA().getAcknowledgementCode().getValue();
			if (AcknowledgmentCode.AA.name().equals(ackCode)) {
				log.warn("Got response from PACS: " + response);
				log.info("Radiology order sent successfully");
				if (log.isTraceEnabled()) {
					log.trace(response.encode());
				}
				return true;
			}
			else {
				if (log.isTraceEnabled()) {
					log.trace(response.encode());
				}
				String error = response.getERR() != null ? response.getERR().encode() : response.encode();
				throw new RuntimeException("Radiology order sent failed: " + error);
			}
		}
		catch (Exception e) {
			log.error("Error sending radiology order message to PACS: " + e);
			throw new RuntimeException(e);
		}
	}

	/**
	 * Records the accession number and fulfiller comment on the order.  The fulfiller status is kept as it is, as other
	 * code relies on it to mark orders completed when their results arrive.
	 */
	protected void updateOrder(TestOrder testOrder, String accessionNumber, String fulfillerComment) {
		boolean privilegeAdded = false;
		try {
			Context.addProxyPrivilege(PrivilegeConstants.EDIT_ORDERS);
			privilegeAdded = true;
		}
		catch (Exception e) {
			log.debug("Unable to add proxy privilege " + PrivilegeConstants.EDIT_ORDERS, e);
		}
		try {
			orderService.updateOrderFulfillerStatus(testOrder, testOrder.getFulfillerStatus(), fulfillerComment,
					accessionNumber);
		}
		catch (Exception e) {
			log.error("Unable to record accession number " + accessionNumber + " on radiology order "
					+ testOrder.getOrderNumber(), e);
		}
		finally {
			if (privilegeAdded) {
				Context.removeProxyPrivilege(PrivilegeConstants.EDIT_ORDERS);
			}
		}
	}

	/**
	 * Sends the message to PACS and returns its acknowledgement.  The RIS rejects a connection that arrives too soon after
	 * the previous one, which happens when several tests are ordered together, so sends are done one at a time and
	 * spaced at least the minimum interval apart.  If PACS refuses or closes the connection without acknowledging, the
	 * message is sent again on a new connection after a delay.  A missing acknowledgement after a timeout is not retried,
	 * as PACS may have registered the order and sending it again could duplicate it.
	 */
	protected ca.uhn.hl7v2.model.Message sendWithRetry(ORM_O01 hl7Message, String host, int port) throws Exception {
		int maxAttempts = 1 + getIntProperty(PROPERTY_RETRY_COUNT, DEFAULT_RETRY_COUNT);
		synchronized (sendLock) {
			for (int attempt = 1; ; attempt++) {
				long wait = lastConnectionClosedAt + getIntProperty(PROPERTY_MIN_INTERVAL, DEFAULT_MIN_INTERVAL_MS)
						- System.currentTimeMillis();
				if (wait > 0) {
					Thread.sleep(wait);
				}
				IOException failure;
				try {
					return sendOverMllp(hl7Message, host, port);
				}
				catch (ConnectException | ConnectionClosedException e) {
					failure = e;
				}
				finally {
					lastConnectionClosedAt = System.currentTimeMillis();
				}
				if (attempt >= maxAttempts) {
					throw new ConnectionClosedException("PACS at " + host + ":" + port + " did not accept the order after "
							+ attempt + " attempts: " + failure.getMessage());
				}
				int delay = getIntProperty(PROPERTY_RETRY_DELAY, DEFAULT_RETRY_DELAY_MS);
				log.warn("Attempt " + attempt + " of " + maxAttempts + " to send radiology order to PACS failed: "
						+ failure.getMessage() + ". Retrying in " + delay + " ms");
				Thread.sleep(delay);
			}
		}
	}

	/**
	 * Sends the message using the minimal lower layer protocol (MLLP) framing that HL7 v2 uses over TCP, and reads the
	 * acknowledgement.  Done directly on the socket rather than through a HAPI connection, so that PACS closing the
	 * connection is detected straight away, and the connection is always closed afterwards.
	 */
	protected ca.uhn.hl7v2.model.Message sendOverMllp(ORM_O01 hl7Message, String host, int port) throws Exception {
		Charset charset = getMllpCharset();
		try (Socket socket = new Socket()) {
			socket.connect(new InetSocketAddress(host, port), getIntProperty(PROPERTY_CONNECT_TIMEOUT, DEFAULT_CONNECT_TIMEOUT_MS));
			socket.setSoTimeout(getIntProperty(PROPERTY_ACK_TIMEOUT, DEFAULT_ACK_TIMEOUT_MS));
			OutputStream out = new BufferedOutputStream(socket.getOutputStream());
			out.write(MLLP_START_BLOCK);
			out.write(hl7Message.encode().getBytes(charset));
			out.write(MLLP_END_BLOCK);
			out.write(MLLP_CARRIAGE_RETURN);
			out.flush();

			InputStream in = socket.getInputStream();
			ByteArrayOutputStream response = new ByteArrayOutputStream();
			boolean started = false;
			int previous = -1;
			while (true) {
				int b;
				try {
					b = in.read();
				}
				catch (SocketTimeoutException e) {
					throw new SocketTimeoutException("No acknowledgement from PACS at " + host + ":" + port + " within "
							+ getIntProperty(PROPERTY_ACK_TIMEOUT, DEFAULT_ACK_TIMEOUT_MS) + " ms");
				}
				catch (SocketException e) {
					throw new ConnectionClosedException("PACS at " + host + ":" + port + " reset the connection: " + e.getMessage());
				}
				if (b == -1) {
					throw new ConnectionClosedException("PACS at " + host + ":" + port
							+ " closed the connection without acknowledging the order (connection rejected)");
				}
				if (!started) {
					started = (b == MLLP_START_BLOCK);
				}
				else if (previous == MLLP_END_BLOCK && b == MLLP_CARRIAGE_RETURN) {
					return hapiContext.getGenericParser().parse(new String(response.toByteArray(), charset));
				}
				else if (b != MLLP_END_BLOCK) {
					response.write(b);
				}
				previous = b;
			}
		}
		catch (SocketException e) {
			if (e instanceof ConnectException) {
				throw e;
			}
			// Writing to a connection PACS has already closed
			throw new ConnectionClosedException("PACS at " + host + ":" + port + " closed the connection: " + e.getMessage());
		}
	}

	/**
	 * Uses the same character set as HAPI's own MLLP implementation
	 */
	private Charset getMllpCharset() {
		String charset = System.getProperty("ca.uhn.hl7v2.llp.charset");
		try {
			return StringUtils.isBlank(charset) ? Charset.defaultCharset() : Charset.forName(charset);
		}
		catch (Exception e) {
			return Charset.defaultCharset();
		}
	}

	private int getIntProperty(String propertyName, int defaultValue) {
		String value = ConfigUtil.getProperty(propertyName, null);
		if (StringUtils.isNotBlank(value)) {
			try {
				int parsed = Integer.parseInt(value.trim());
				if (parsed >= 0) {
					return parsed;
				}
			}
			catch (NumberFormatException e) {
				// use the default below
			}
			log.warn("Invalid value for " + propertyName + ": " + value + ", using " + defaultValue);
		}
		return defaultValue;
	}

	/**
	 * PACS refused or closed the connection before acknowledging the message, so it can safely be sent again
	 */
	static class ConnectionClosedException extends IOException {
		ConnectionClosedException(String message) {
			super(message);
		}
	}
}
