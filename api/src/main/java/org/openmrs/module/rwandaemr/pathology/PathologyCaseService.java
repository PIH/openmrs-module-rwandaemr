package org.openmrs.module.rwandaemr.pathology;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Encounter;
import org.openmrs.Order;
import org.openmrs.OrderType;
import org.openmrs.TestOrder;
import org.openmrs.api.APIAuthenticationException;
import org.openmrs.api.OrderService;
import org.openmrs.api.context.Context;
import org.openmrs.util.PrivilegeConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Moves pathology requests through the lab.  A request is the encounter of the request form, and its tests are the
 * pathology orders of that encounter.  The lab receives or rejects the specimen, then grosses and processes it; the
 * pathologist signs out the report.  Each change sets the fulfiller status of the orders, and receiving the specimen
 * gives the case its accession number.
 */
@Component
public class PathologyCaseService {

	private static final int MAX_REASON_LENGTH = 255;

	private final Log log = LogFactory.getLog(getClass());

	private final OrderService orderService;
	private final PathologyConfig pathologyConfig;
	private final PathologyCaseRepository caseRepository;
	private final PathologyAccessionNumberGenerator accessionNumberGenerator;

	public PathologyCaseService(@Autowired OrderService orderService,
								@Autowired PathologyConfig pathologyConfig,
								@Autowired PathologyCaseRepository caseRepository,
								@Autowired PathologyAccessionNumberGenerator accessionNumberGenerator) {
		this.orderService = orderService;
		this.pathologyConfig = pathologyConfig;
		this.caseRepository = caseRepository;
		this.accessionNumberGenerator = accessionNumberGenerator;
	}

	/**
	 * @return the pathology tests of the request that are still ordered: not voided, cancelled or discontinued
	 */
	public List<TestOrder> getActiveOrders(Encounter encounter) {
		List<TestOrder> ret = new ArrayList<TestOrder>();
		OrderType pathologyOrderType = pathologyConfig.getPathologyOrderType();
		if (encounter == null || pathologyOrderType == null) {
			return ret;
		}
		for (Order order : encounter.getOrders()) {
			if (order instanceof TestOrder && pathologyOrderType.equals(order.getOrderType()) && order.isActive()) {
				ret.add((TestOrder) order);
			}
		}
		return ret;
	}

	/**
	 * @return the case of the request, or null if the lab has not received or rejected its specimen yet
	 */
	@Transactional(readOnly = true)
	public PathologyCase getCase(Encounter encounter) {
		return caseRepository.getCase(encounter.getEncounterId());
	}

	/**
	 * @return the status of the request
	 */
	@Transactional(readOnly = true)
	public PathologyStatus getStatus(Encounter encounter) {
		PathologyCase pathologyCase = getCase(encounter);
		return pathologyCase == null ? PathologyStatus.ORDERED : pathologyCase.getStatus();
	}

	/**
	 * Records that the lab has received the specimen, and gives the case its accession number.  The specimen is
	 * received even if the tests are not paid; the queue warns about it.
	 * @return the case
	 */
	@Transactional
	public PathologyCase receive(Encounter encounter) {
		checkPrivilege(PathologyConfig.PRIVILEGE_PROCESS_SPECIMENS);
		List<TestOrder> orders = getOrdersToMove(encounter, PathologyStatus.RECEIVED);
		PathologyCase pathologyCase = getCase(encounter);
		if (pathologyCase == null) {
			pathologyCase = new PathologyCase();
			pathologyCase.setEncounterId(encounter.getEncounterId());
		}
		pathologyCase.setAccessionNumber(accessionNumberGenerator.next());
		pathologyCase.setStatus(PathologyStatus.RECEIVED);
		caseRepository.saveCase(pathologyCase);
		updateOrders(orders, pathologyCase, null);
		log.info("Pathology specimen of encounter " + encounter.getEncounterId() + " received as "
				+ pathologyCase.getAccessionNumber());
		return pathologyCase;
	}

	/**
	 * Moves the case to a step of the lab's work: grossed, processed or additional stains
	 * @return the case
	 */
	@Transactional
	public PathologyCase moveTo(Encounter encounter, PathologyStatus status) {
		if (status != PathologyStatus.GROSSED && status != PathologyStatus.PROCESSED
				&& status != PathologyStatus.ADDITIONAL_STAINS) {
			throw new IllegalArgumentException("A case cannot be moved to " + status.getLabel() + " this way");
		}
		checkPrivilege(PathologyConfig.PRIVILEGE_PROCESS_SPECIMENS);
		List<TestOrder> orders = getOrdersToMove(encounter, status);
		PathologyCase pathologyCase = getCase(encounter);
		pathologyCase.setStatus(status);
		caseRepository.saveCase(pathologyCase);
		updateOrders(orders, pathologyCase, null);
		return pathologyCase;
	}

	/**
	 * Rejects the specimen, for example because it is not labelled or was not fixed properly.  A specimen rejected as it
	 * arrives gets no accession number.
	 * @return the case
	 */
	@Transactional
	public PathologyCase reject(Encounter encounter, String reason) {
		checkPrivilege(PathologyConfig.PRIVILEGE_PROCESS_SPECIMENS);
		if (StringUtils.isBlank(reason)) {
			throw new IllegalArgumentException("Please give the reason the specimen is rejected");
		}
		String trimmedReason = reason.trim();
		if (trimmedReason.length() > MAX_REASON_LENGTH) {
			throw new IllegalArgumentException("The reason cannot be longer than " + MAX_REASON_LENGTH + " characters");
		}
		List<TestOrder> orders = getOrdersToMove(encounter, PathologyStatus.REJECTED);
		PathologyCase pathologyCase = getCase(encounter);
		if (pathologyCase == null) {
			pathologyCase = new PathologyCase();
			pathologyCase.setEncounterId(encounter.getEncounterId());
		}
		pathologyCase.setStatus(PathologyStatus.REJECTED);
		pathologyCase.setRejectionReason(trimmedReason);
		caseRepository.saveCase(pathologyCase);
		updateOrders(orders, pathologyCase, "Specimen rejected: " + trimmedReason);
		return pathologyCase;
	}

	/**
	 * @return why the request cannot be moved to the given status, or null if it can
	 */
	@Transactional(readOnly = true)
	public String getReasonCannotMoveTo(Encounter encounter, PathologyStatus status) {
		if (getActiveOrders(encounter).isEmpty()) {
			return "This request has no pathology tests ordered.";
		}
		PathologyStatus current = getStatus(encounter);
		if (!current.canMoveTo(status)) {
			return "A case that is " + current.getLabel().toLowerCase() + " cannot be moved to "
					+ status.getLabel().toLowerCase() + ".";
		}
		return null;
	}

	private List<TestOrder> getOrdersToMove(Encounter encounter, PathologyStatus status) {
		String reason = getReasonCannotMoveTo(encounter, status);
		if (reason != null) {
			throw new IllegalStateException(reason);
		}
		return getActiveOrders(encounter);
	}

	/**
	 * Sets the fulfiller status of the case on each of its orders, with its accession number.  Tests added to the request
	 * after the specimen was received get the case's accession number this way too.
	 */
	private void updateOrders(List<TestOrder> orders, PathologyCase pathologyCase, String fulfillerComment) {
		for (TestOrder order : orders) {
			updateOrder(order, pathologyCase.getStatus().getFulfillerStatus(), fulfillerComment,
					pathologyCase.getAccessionNumber());
		}
	}

	/**
	 * Lab staff moving cases through the lab may not otherwise be allowed to change orders
	 */
	protected void updateOrder(TestOrder order, Order.FulfillerStatus fulfillerStatus, String fulfillerComment,
							   String accessionNumber) {
		Context.addProxyPrivilege(PrivilegeConstants.EDIT_ORDERS);
		try {
			orderService.updateOrderFulfillerStatus(order, fulfillerStatus, fulfillerComment, accessionNumber);
		}
		finally {
			Context.removeProxyPrivilege(PrivilegeConstants.EDIT_ORDERS);
		}
	}

	protected void checkPrivilege(String privilege) {
		if (!Context.hasPrivilege(privilege)) {
			throw new APIAuthenticationException("You do not have permission to do this: " + privilege);
		}
	}
}
