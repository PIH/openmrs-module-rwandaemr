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
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Moves pathology requests through the lab.  A request is the encounter of the request form, and its tests are the
 * pathology orders of that encounter.  The lab receives or rejects the specimen from the queue; grossing, processing,
 * additional stains, sending for review and sign out are recorded from the results saved on the pathology report.  Each change sets the
 * fulfiller status of the orders, and receiving the specimen gives the case its accession number.
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
	 * Records the results saved on the pathology report of the case, and moves the case to each step the results show
	 * was done: grossed, processed, additional stains, sent for review, then signed out.  A case never moves back, so results removed from
	 * the report later leave the case where it is.  Results that need a step not recorded yet record it too: slides are
	 * processed before stains, review or sign out.  Only a pathologist signs out; the report of someone else stays
	 * where it is until a pathologist saves it.
	 * @param request the request encounter of the case
	 * @param report the report encounter
	 * @param stepsWithResults the steps the report has results for
	 * @return the case
	 */
	@Transactional
	public PathologyCase recordReport(Encounter request, Encounter report, Set<PathologyStatus> stepsWithResults) {
		if (!hasPrivilege(PathologyConfig.PRIVILEGE_PROCESS_SPECIMENS) && !hasPrivilege(PathologyConfig.PRIVILEGE_SIGN_OUT)) {
			throw new APIAuthenticationException("You do not have permission to do this: "
					+ PathologyConfig.PRIVILEGE_PROCESS_SPECIMENS);
		}
		PathologyCase pathologyCase = getCase(request);
		if (pathologyCase == null || pathologyCase.getStatus() == PathologyStatus.REJECTED) {
			throw new IllegalStateException("The lab has not received the specimen of this request");
		}
		if (pathologyCase.getReportEncounterId() == null) {
			pathologyCase.setReportEncounterId(report.getEncounterId());
			pathologyCase.setReportEncounterUuid(report.getUuid());
			caseRepository.saveReportEncounter(pathologyCase);
		}
		else if (!pathologyCase.getReportEncounterId().equals(report.getEncounterId())) {
			throw new IllegalStateException("Case " + pathologyCase.getAccessionNumber() + " already has a pathology report");
		}
		List<TestOrder> orders = getActiveOrders(request);
		for (PathologyStatus step : getStepsToRecord(stepsWithResults)) {
			if (step == PathologyStatus.SIGNED_OUT && !hasPrivilege(PathologyConfig.PRIVILEGE_SIGN_OUT)) {
				continue;
			}
			if (pathologyCase.getStatus().canMoveTo(step)) {
				pathologyCase.setStatus(step);
				caseRepository.saveCase(pathologyCase);
				updateOrders(orders, pathologyCase, null);
			}
		}
		return pathologyCase;
	}

	/**
	 * @return the steps to record for the results of a report, in the order of the lab's work
	 */
	List<PathologyStatus> getStepsToRecord(Set<PathologyStatus> stepsWithResults) {
		Set<PathologyStatus> steps = EnumSet.noneOf(PathologyStatus.class);
		for (PathologyStatus step : stepsWithResults) {
			if (step.isRecordedFromReport()) {
				steps.add(step);
			}
		}
		if (steps.contains(PathologyStatus.ADDITIONAL_STAINS) || steps.contains(PathologyStatus.SENT_FOR_REVIEW)
				|| steps.contains(PathologyStatus.SIGNED_OUT)) {
			steps.add(PathologyStatus.PROCESSED);
		}
		return new ArrayList<PathologyStatus>(steps);
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
		if (!hasPrivilege(privilege)) {
			throw new APIAuthenticationException("You do not have permission to do this: " + privilege);
		}
	}

	protected boolean hasPrivilege(String privilege) {
		return Context.hasPrivilege(privilege);
	}
}
