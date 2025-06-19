package org.openmrs.module.rwandaemr.radiology;

import lombok.Setter;
import org.apache.commons.io.IOUtils;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.jms.MapMessage;
import javax.jms.Message;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Listener that can be registered with Patient events and which will create a
 * default private insurance for that patient if they do not already have one
 */
@Component
public class RadiologyOrderEventListener implements EventListener {

	protected final Log log = LogFactory.getLog(getClass());
	@Setter private static DaemonToken daemonToken;
	private boolean isSubscribed = false;

	private final OrderService orderService;
	private final OrderToORMO01Translator orderToORMTranslator;

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
			try (Socket socket = new Socket()) {
				String hl7Message = orderToORMTranslator.toORM_O01(testOrder);
				InetAddress inetAddress = InetAddress.getByName(getHost());
				SocketAddress socketAddress = new InetSocketAddress(inetAddress, getPort());
				int timeoutMs = 5000;   // 5s
				socket.connect(socketAddress, timeoutMs);
				IOUtils.write(hl7Message, socket.getOutputStream(), StandardCharsets.UTF_8);
				log.info("Radiology order sent successfully");
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
}
