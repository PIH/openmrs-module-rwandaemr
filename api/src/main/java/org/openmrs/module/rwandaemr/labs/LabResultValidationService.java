package org.openmrs.module.rwandaemr.labs;

import org.apache.commons.lang.StringUtils;
import org.hibernate.Criteria;
import org.openmrs.Concept;
import org.openmrs.Obs;
import org.openmrs.Order;
import org.openmrs.User;
import org.openmrs.api.APIException;
import org.openmrs.api.OrderService;
import org.openmrs.api.context.Context;
import org.openmrs.api.db.hibernate.DbSessionFactory;
import org.openmrs.module.pihapps.orders.OrderFulfillmentStatus;
import org.openmrs.module.rwandaemr.labs.LabResultValidationRepository.Action;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.hibernate.criterion.Restrictions.eq;

/**
 * Validation of lab results by a second user.  When pihapps.labs.requireResultValidation is true, results that are
 * entered leave their order IN_PROGRESS and awaiting validation.  A validator, who must not have entered or changed any
 * of the order's results, either completes the order or returns it to the lab for correction.
 */
@Component
public class LabResultValidationService {

	public static final String PRIVILEGE_VALIDATE_RESULTS = "Task: rwandaemr.labs.validateResults";

	/**
	 * The fulfiller comment of an order returned for correction starts with this, followed by the reason
	 */
	public static final String RETURNED_FOR_CORRECTION = "Returned for correction";

	public static final String RESULTS_VALIDATED = "Results validated";

	private static final int MAX_FULFILLER_COMMENT_LENGTH = 1024;

	private final DbSessionFactory dbSessionFactory;
	private final OrderService orderService;
	private final LabResultValidationRepository repository;
	private final AntibiogramConfig antibiogramConfig;

	public LabResultValidationService(@Autowired DbSessionFactory dbSessionFactory,
									  @Autowired OrderService orderService,
									  @Autowired LabResultValidationRepository repository,
									  @Autowired AntibiogramConfig antibiogramConfig) {
		this.dbSessionFactory = dbSessionFactory;
		this.orderService = orderService;
		this.repository = repository;
		this.antibiogramConfig = antibiogramConfig;
	}

	public static boolean isAwaitingValidation(Order order) {
		return order.getFulfillerStatus() == Order.FulfillerStatus.IN_PROGRESS
				&& OrderFulfillmentStatus.RESULTS_AWAITING_VALIDATION.equals(order.getFulfillerComment());
	}

	/**
	 * @return the users who entered, changed or removed any result of the order.  Results are the observations linked
	 * to the order whose concept is the ordered test or one of its members, or part of its antibiogram, so specimen
	 * details are not counted.  The user who last saved the encounter of the results counts too, as results recorded by
	 * a lab machine are created by the machine's user and only submitted by the lab technician.
	 */
	@Transactional(readOnly = true)
	@SuppressWarnings("unchecked")
	public Set<User> getResultEditors(Order order) {
		Set<Concept> resultConcepts = new HashSet<Concept>();
		addWithSetMembers(order.getConcept(), resultConcepts);
		resultConcepts.addAll(antibiogramConfig.getAntibiogramConcepts());
		Criteria c = dbSessionFactory.getHibernateSessionFactory().getCurrentSession().createCriteria(Obs.class);
		c.add(eq("order", order)); // voided results too: whoever removed or replaced a value changed the results
		Set<User> ret = new LinkedHashSet<User>();
		for (Obs obs : (List<Obs>) c.list()) {
			if (resultConcepts.contains(obs.getConcept())) {
				ret.add(obs.getCreator());
				if (obs.getVoidedBy() != null) {
					ret.add(obs.getVoidedBy());
				}
				// Whoever saved the results last, eg submitting results that a lab machine recorded, entered them too
				if (obs.getEncounter() != null && obs.getEncounter().getChangedBy() != null) {
					ret.add(obs.getEncounter().getChangedBy());
				}
			}
		}
		return ret;
	}

	/**
	 * @return whether the user is allowed to validate the results of the order: the four-eyes rule
	 */
	@Transactional(readOnly = true)
	public boolean mayValidate(Order order, User user) {
		return isAwaitingValidation(order) && !getResultEditors(order).contains(user);
	}

	/**
	 * Completes each order, after checking that it awaits validation and that the current user did not enter or change
	 * its results.  Nothing is saved if any order cannot be validated.
	 */
	@Transactional
	public void validate(List<Order> orders) {
		Context.requirePrivilege(PRIVILEGE_VALIDATE_RESULTS);
		User user = Context.getAuthenticatedUser();
		List<Set<User>> editors = new ArrayList<Set<User>>();
		for (Order order : orders) {
			if (!isAwaitingValidation(order)) {
				throw new APIException("The results of order " + order.getOrderNumber() + " are not awaiting validation");
			}
			Set<User> orderEditors = getResultEditors(order);
			if (orderEditors.contains(user)) {
				throw new APIException("You entered or changed the results of order " + order.getOrderNumber()
						+ ", so another user must validate them");
			}
			editors.add(orderEditors);
		}
		for (int i = 0; i < orders.size(); i++) {
			Order order = orders.get(i);
			orderService.updateOrderFulfillerStatus(order, Order.FulfillerStatus.COMPLETED, RESULTS_VALIDATED);
			repository.saveDecision(order, Action.VALIDATED, null, userIds(editors.get(i)), user);
		}
	}

	/**
	 * Returns each order to the lab to correct its results.  The order stays in progress and no longer awaits
	 * validation; its fulfiller comment gives the reason.
	 */
	@Transactional
	public void returnForCorrection(List<Order> orders, String reason) {
		Context.requirePrivilege(PRIVILEGE_VALIDATE_RESULTS);
		if (StringUtils.isBlank(reason)) {
			throw new APIException("A reason is required to return results for correction");
		}
		for (Order order : orders) {
			if (!isAwaitingValidation(order)) {
				throw new APIException("The results of order " + order.getOrderNumber() + " are not awaiting validation");
			}
		}
		User user = Context.getAuthenticatedUser();
		String comment = StringUtils.abbreviate(RETURNED_FOR_CORRECTION + ": " + reason.trim(), MAX_FULFILLER_COMMENT_LENGTH);
		for (Order order : orders) {
			String editors = userIds(getResultEditors(order));
			orderService.updateOrderFulfillerStatus(order, Order.FulfillerStatus.IN_PROGRESS, comment);
			repository.saveDecision(order, Action.RETURNED, reason.trim(), editors, user);
		}
	}

	private void addWithSetMembers(Concept concept, Set<Concept> concepts) {
		if (concept != null && concepts.add(concept) && concept.getSetMembers() != null) {
			for (Concept member : concept.getSetMembers()) {
				addWithSetMembers(member, concepts);
			}
		}
	}

	private String userIds(Set<User> users) {
		List<String> ids = new ArrayList<String>();
		for (User user : users) {
			ids.add(String.valueOf(user.getUserId()));
		}
		return StringUtils.abbreviate(StringUtils.join(ids, ","), 255);
	}
}
