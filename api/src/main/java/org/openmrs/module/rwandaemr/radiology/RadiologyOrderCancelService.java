package org.openmrs.module.rwandaemr.radiology;

import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Concept;
import org.openmrs.Obs;
import org.openmrs.Order;
import org.openmrs.Provider;
import org.openmrs.TestOrder;
import org.openmrs.api.OrderService;
import org.openmrs.api.context.Context;
import org.openmrs.module.rwandaemr.RwandaEmrService;
import org.openmrs.util.PrivilegeConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * Cancels radiology orders.  An order is cancelled by discontinuing it, and the cancellation is then sent to PACS by
 * {@link RadiologyOrderEventListener}, as for an order discontinued any other way.
 */
@Component
public class RadiologyOrderCancelService {

	public static final String PRIVILEGE_CANCEL_ORDER = "Task: rwandaemr.radiologyOrder.cancel";

	private static final int MAX_REASON_LENGTH = 255;

	private final Log log = LogFactory.getLog(getClass());

	private final OrderService orderService;
	private final OrderToORMO01Translator orderToORMTranslator;
	private final RadiologyConfig radiologyConfig;
	private final RwandaEmrService rwandaEmrService;
	private final RadiologyReportService radiologyReportService;

	public RadiologyOrderCancelService(@Autowired OrderService orderService,
									   @Autowired OrderToORMO01Translator orderToORMTranslator,
									   @Autowired RadiologyConfig radiologyConfig,
									   @Autowired @Qualifier("rwandaEmrService") RwandaEmrService rwandaEmrService,
									   @Autowired RadiologyReportService radiologyReportService) {
		this.orderService = orderService;
		this.orderToORMTranslator = orderToORMTranslator;
		this.radiologyConfig = radiologyConfig;
		this.rwandaEmrService = rwandaEmrService;
		this.radiologyReportService = radiologyReportService;
	}

	/**
	 * @return why the order cannot be cancelled, or null if it can.  An exam that has already been done cannot be
	 * cancelled, as PACS holds its images.
	 */
	public String getReasonOrderCannotBeCancelled(Order order) {
		if (!(order instanceof TestOrder) || !orderToORMTranslator.isRadiologyOrder((TestOrder) order)) {
			return "This is not a radiology order.";
		}
		if (order.getAction() == Order.Action.DISCONTINUE) {
			return "This is the cancellation of another order.";
		}
		if (BooleanUtils.isTrue(order.getVoided()) || order.getDateStopped() != null) {
			return "This order is already cancelled.";
		}
		if (isStudyDone(order)) {
			return "The exam has already been done, as PACS has sent its study, so the order cannot be cancelled.";
		}
		return null;
	}

	/**
	 * @return true if the order can be cancelled now
	 */
	public boolean canCancel(Order order) {
		return getReasonOrderCannotBeCancelled(order) == null;
	}

	/**
	 * Cancels the order by discontinuing it, recording the reason and the logged-in provider
	 * @return the discontinuation order
	 * @throws IllegalArgumentException if the order cannot be cancelled or no reason is given
	 * @throws IllegalStateException if the logged-in user is not linked to a provider
	 */
	public Order cancel(Order order, String reason) {
		String cannotCancel = getReasonOrderCannotBeCancelled(order);
		if (cannotCancel != null) {
			throw new IllegalArgumentException(cannotCancel);
		}
		if (StringUtils.isBlank(reason)) {
			throw new IllegalArgumentException("Please give the reason for cancelling the order");
		}
		String trimmedReason = reason.trim();
		if (trimmedReason.length() > MAX_REASON_LENGTH) {
			throw new IllegalArgumentException("The reason cannot be longer than " + MAX_REASON_LENGTH + " characters");
		}
		Provider provider = radiologyReportService.getCurrentProvider();
		if (provider == null) {
			throw new IllegalStateException("Your user account is not linked to a provider, so the order cannot be cancelled");
		}
		// Users allowed to cancel radiology orders may not otherwise be allowed to change orders
		Context.addProxyPrivilege(PrivilegeConstants.ADD_ORDERS);
		Context.addProxyPrivilege(PrivilegeConstants.EDIT_ORDERS);
		try {
			Order discontinuation = orderService.discontinueOrder(order, trimmedReason, new Date(), provider,
					order.getEncounter());
			log.info("Radiology order " + order.getOrderNumber() + " cancelled by " + provider.getName());
			return discontinuation;
		}
		catch (Exception e) {
			throw new IllegalStateException("The order could not be cancelled: " + e.getMessage(), e);
		}
		finally {
			Context.removeProxyPrivilege(PrivilegeConstants.ADD_ORDERS);
			Context.removeProxyPrivilege(PrivilegeConstants.EDIT_ORDERS);
		}
	}

	/**
	 * @return the order that cancelled the given order, or null if it was not cancelled that way
	 */
	public Order getCancellation(Order order) {
		if (order == null || order.getDateStopped() == null) {
			return null;
		}
		return orderService.getDiscontinuationOrder(order);
	}

	private boolean isStudyDone(Order order) {
		Concept studyConstruct = radiologyConfig.getRadiologyStudyConstruct();
		for (Obs obs : rwandaEmrService.getObsByOrder(order)) {
			if (obs.getConcept().equals(studyConstruct)) {
				return true;
			}
		}
		return false;
	}
}
