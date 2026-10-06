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
import org.openmrs.Order;
import org.openmrs.TestOrder;
import org.openmrs.api.OrderService;
import org.openmrs.api.context.Daemon;
import org.openmrs.event.Event;
import org.openmrs.event.EventListener;
import org.openmrs.module.DaemonToken;
import org.openmrs.util.ConfigUtil;
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
										  @Autowired OrderToORMO01Translator orderToORMTranslator) {
		this.orderService = orderService;
		this.orderToORMTranslator = orderToORMTranslator;
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
		Order order = orderService.getOrderByUuid(orderUuid);
		if (order == null) {
			throw new IllegalArgumentException("Order with uuid " + orderUuid + " not found");
		}
		if (order instanceof TestOrder) {
			TestOrder testOrder = (TestOrder) order;
			if (orderToORMTranslator.isRadiologyOrder(testOrder)) {
				sendRadiologyOrderMessage(testOrder);
			}
			else {
				log.trace("TestOrder is not for a radiology orderable: " + orderUuid);
			}
		}
		else {
			log.trace("Order is not a TestOrder: " + orderUuid);
		}
	}

	public void sendRadiologyOrderMessage(TestOrder testOrder) {
		if (getHost() != null && getPort() != null) {
			log.info("Sending radiology order message to PACS");
			try {
				ORM_O01 hl7Message = orderToORMTranslator.toORM_O01(testOrder);
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
		else {
			log.warn("Not sending radiology order message as host and port are required: " + getHost() + ":" + getPort());
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
