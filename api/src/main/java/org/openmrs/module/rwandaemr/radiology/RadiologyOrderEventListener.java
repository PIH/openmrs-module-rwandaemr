package org.openmrs.module.rwandaemr.radiology;

import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.app.Connection;
import ca.uhn.hl7v2.app.Initiator;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.jms.MapMessage;
import javax.jms.Message;

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
				HapiContext context = new DefaultHapiContext();
				Connection connection = context.newClient(getHost(), getPort(), false);
				Initiator initiator = connection.getInitiator();
				ca.uhn.hl7v2.model.Message response = initiator.sendAndReceive(hl7Message);
				log.warn("Got response from PACS: " + response);
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
