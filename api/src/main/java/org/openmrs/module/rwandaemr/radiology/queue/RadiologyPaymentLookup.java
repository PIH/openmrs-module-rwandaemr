package org.openmrs.module.rwandaemr.radiology.queue;

import org.hibernate.query.NativeQuery;
import org.openmrs.Order;
import org.openmrs.api.db.hibernate.DbSessionFactory;
import org.openmrs.module.rwandaemr.radiology.queue.RadiologyQueueRow.PaymentStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds the payment status of radiology orders from the billing module.  Bill items do not record the order they were
 * created for, so each order is matched to the bill item for the same patient and the same test whose consommation was
 * created closest to the time of the order, from shortly before it until a day after it.
 */
@Component
public class RadiologyPaymentLookup {

	/**
	 * Bill items are normally created as the order is saved; allow a small margin for clock differences
	 */
	static final long MATCH_BEFORE_ORDER_MS = 10 * 60 * 1000L;

	/**
	 * Allow for tests billed later by the cashier rather than when the order was saved
	 */
	static final long MATCH_AFTER_ORDER_MS = 24 * 60 * 60 * 1000L;

	private final DbSessionFactory dbSessionFactory;

	public RadiologyPaymentLookup(@Autowired DbSessionFactory dbSessionFactory) {
		this.dbSessionFactory = dbSessionFactory;
	}

	/**
	 * @return the payment of each of the given orders, by order id.  Orders with no matching bill item are not billed.
	 */
	@Transactional(readOnly = true)
	public Map<Integer, OrderPayment> getPayments(Collection<? extends Order> orders) {
		Map<Integer, OrderPayment> ret = new HashMap<Integer, OrderPayment>();
		if (orders == null || orders.isEmpty()) {
			return ret;
		}
		Set<Integer> patientIds = new HashSet<Integer>();
		Set<Integer> conceptIds = new HashSet<Integer>();
		Date from = null;
		Date to = null;
		for (Order order : orders) {
			patientIds.add(order.getPatient().getPatientId());
			conceptIds.add(order.getConcept().getConceptId());
			Date created = getOrderTime(order);
			from = (from == null || created.before(from)) ? created : from;
			to = (to == null || created.after(to)) ? created : to;
		}
		List<BillItem> items = findBillItems(patientIds, conceptIds,
				new Date(from.getTime() - MATCH_BEFORE_ORDER_MS), new Date(to.getTime() + MATCH_AFTER_ORDER_MS));
		return matchOrdersToBillItems(orders, items);
	}

	/**
	 * Matches each order to the closest bill item in time for the same patient and test.  Each bill item is used for at
	 * most one order.
	 */
	static Map<Integer, OrderPayment> matchOrdersToBillItems(Collection<? extends Order> orders, List<BillItem> items) {
		Map<Integer, OrderPayment> ret = new HashMap<Integer, OrderPayment>();
		Set<BillItem> used = new HashSet<BillItem>();
		List<Order> sortedOrders = new ArrayList<Order>(orders);
		sortedOrders.sort((a, b) -> getOrderTime(a).compareTo(getOrderTime(b)));
		for (Order order : sortedOrders) {
			long orderTime = getOrderTime(order).getTime();
			BillItem best = null;
			long bestDistance = Long.MAX_VALUE;
			for (BillItem item : items) {
				if (used.contains(item) || !item.patientId.equals(order.getPatient().getPatientId())
						|| !item.conceptId.equals(order.getConcept().getConceptId())) {
					continue;
				}
				long offset = item.createdDate.getTime() - orderTime;
				if (offset < -MATCH_BEFORE_ORDER_MS || offset > MATCH_AFTER_ORDER_MS) {
					continue;
				}
				long distance = Math.abs(offset);
				if (distance < bestDistance) {
					best = item;
					bestDistance = distance;
				}
			}
			if (best == null) {
				ret.put(order.getOrderId(), new OrderPayment(PaymentStatus.NOT_BILLED, null));
			}
			else {
				used.add(best);
				ret.put(order.getOrderId(), new OrderPayment(best.getStatus(), best.lastPaymentDate));
			}
		}
		return ret;
	}

	/**
	 * Explains the payment matching of one order, for troubleshooting.  Lists every bill item of the order's patient from
	 * three days before to three days after the order, for any test, and why it does or does not match.  Contains only
	 * ids, concept ids, times and statuses: no names, identifiers or amounts.
	 */
	@Transactional(readOnly = true)
	public Map<String, Object> explain(Order order) {
		Map<String, Object> ret = new java.util.LinkedHashMap<String, Object>();
		Date orderTime = getOrderTime(order);
		ret.put("orderId", order.getOrderId());
		ret.put("orderConceptId", order.getConcept().getConceptId());
		ret.put("orderDateCreated", String.valueOf(order.getDateCreated()));
		ret.put("orderDateActivated", String.valueOf(order.getDateActivated()));
		ret.put("matchWindow", "consommation created from " + (MATCH_BEFORE_ORDER_MS / 60000) + " minutes before to "
				+ (MATCH_AFTER_ORDER_MS / 3600000) + " hours after the order");
		long threeDays = 3 * 24 * 60 * 60 * 1000L;
		NativeQuery<?> query = dbSessionFactory.getHibernateSessionFactory().getCurrentSession().createSQLQuery(
				"select psb.patient_service_bill_id, fsp.facility_service_price_id, fsp.concept_id, c.created_date, " +
						"psb.created_date, psb.voided, c.voided, psb.billable_service_id, psb.is_paid, psb.paid_quantity, " +
						"psb.quantity, c.consommation_id " +
						"from moh_bill_patient_service_bill psb " +
						"join moh_bill_consommation c on c.consommation_id = psb.consommation_id " +
						"join moh_bill_beneficiary b on b.beneficiary_id = c.beneficiary_id " +
						"left join moh_bill_billable_service bs on bs.billable_service_id = psb.billable_service_id " +
						"left join moh_bill_facility_service_price fsp on fsp.facility_service_price_id = bs.facility_service_price_id " +
						"where b.patient_id = :patientId and c.created_date between :fromDate and :toDate " +
						"order by c.created_date");
		query.setParameter("patientId", order.getPatient().getPatientId());
		query.setParameter("fromDate", new Date(orderTime.getTime() - threeDays));
		query.setParameter("toDate", new Date(orderTime.getTime() + threeDays));
		List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
		for (Object row : query.list()) {
			Object[] v = (Object[]) row;
			Map<String, Object> item = new java.util.LinkedHashMap<String, Object>();
			item.put("billItemId", v[0]);
			item.put("consommationId", v[11]);
			item.put("billableServiceId", v[7]);
			item.put("facilityServicePriceId", v[1]);
			item.put("priceConceptId", v[2]);
			item.put("consommationCreated", String.valueOf(v[3]));
			item.put("itemCreatedDate", String.valueOf(v[4]));
			Date consommationCreated = (Date) v[3];
			Long offsetMinutes = consommationCreated == null ? null : (consommationCreated.getTime() - orderTime.getTime()) / 60000;
			item.put("minutesAfterOrder", offsetMinutes);
			item.put("itemVoided", v[5]);
			item.put("consommationVoided", v[6]);
			item.put("isPaid", v[8]);
			item.put("paidQuantity", v[9]);
			item.put("quantity", v[10]);
			List<String> reasons = new ArrayList<String>();
			if (v[1] == null) {
				reasons.add("bill item has no billable service / price");
			}
			else if (v[2] == null) {
				reasons.add("price has no concept");
			}
			else if (((Number) v[2]).intValue() != order.getConcept().getConceptId()) {
				reasons.add("price concept differs from the ordered test's concept");
			}
			if (isTrue(v[5])) {
				reasons.add("bill item voided");
			}
			if (isTrue(v[6])) {
				reasons.add("consommation voided");
			}
			if (offsetMinutes != null && (offsetMinutes * 60000 < -MATCH_BEFORE_ORDER_MS || offsetMinutes * 60000 > MATCH_AFTER_ORDER_MS)) {
				reasons.add("outside the time window");
			}
			item.put("matches", reasons.isEmpty());
			item.put("notMatchedBecause", reasons);
			items.add(item);
		}
		ret.put("billItemsOfPatientWithin3Days", items);
		ret.put("result", getPayments(java.util.Collections.singletonList(order)).get(order.getOrderId()).getStatus().name());
		return ret;
	}

	private static boolean isTrue(Object value) {
		return value != null && (value instanceof Boolean ? (Boolean) value : ((Number) value).intValue() != 0);
	}

	private static Date getOrderTime(Order order) {
		return order.getDateCreated() != null ? order.getDateCreated() : order.getDateActivated();
	}

	private List<BillItem> findBillItems(Set<Integer> patientIds, Set<Integer> conceptIds, Date from, Date to) {
		String paidItems = "from moh_bill_paid_service_bill pd join moh_bill_payment bp on bp.bill_payment_id = pd.bill_payment_id " +
				"where pd.patient_service_bill_id = psb.patient_service_bill_id and pd.voided = 0 and bp.voided = 0";
		// The bill item's own created_date is a DATE column without the time, so the time of the consommation it belongs
		// to is used instead: the billing module creates it in the same transaction as the order.
		// The cashier's payment is recorded both on the item (paid_quantity) and per payment (paid service bill).
		NativeQuery<?> query = dbSessionFactory.getHibernateSessionFactory().getCurrentSession().createSQLQuery(
				"select psb.patient_service_bill_id, b.patient_id, fsp.concept_id, c.created_date, psb.quantity, " +
						"psb.is_paid, pb.amount, " +
						"greatest(coalesce(psb.paid_quantity, 0), (select coalesce(sum(pd.paid_quantity), 0) " + paidItems + ")), " +
						"(select max(bp.date_received) " + paidItems + ") " +
						"from moh_bill_patient_service_bill psb " +
						"join moh_bill_consommation c on c.consommation_id = psb.consommation_id " +
						"join moh_bill_beneficiary b on b.beneficiary_id = c.beneficiary_id " +
						"join moh_bill_billable_service bs on bs.billable_service_id = psb.billable_service_id " +
						"join moh_bill_facility_service_price fsp on fsp.facility_service_price_id = bs.facility_service_price_id " +
						"left join moh_bill_patient_bill pb on pb.patient_bill_id = c.patient_bill_id " +
						"where psb.voided = 0 and c.voided = 0 and b.patient_id in (:patientIds) " +
						"and fsp.concept_id in (:conceptIds) and c.created_date between :fromDate and :toDate");
		query.setParameterList("patientIds", patientIds);
		query.setParameterList("conceptIds", conceptIds);
		query.setParameter("fromDate", from);
		query.setParameter("toDate", to);
		List<BillItem> ret = new ArrayList<BillItem>();
		for (Object row : query.list()) {
			Object[] values = (Object[]) row;
			BillItem item = new BillItem();
			item.patientId = ((Number) values[1]).intValue();
			item.conceptId = ((Number) values[2]).intValue();
			item.createdDate = (Date) values[3];
			item.quantity = toBigDecimal(values[4]);
			item.markedPaid = values[5] != null && (values[5] instanceof Boolean ? (Boolean) values[5] :
					((Number) values[5]).intValue() != 0);
			item.patientDue = toBigDecimal(values[6]);
			item.paidQuantity = toBigDecimal(values[7]);
			item.lastPaymentDate = (Date) values[8];
			ret.add(item);
		}
		return ret;
	}

	private static BigDecimal toBigDecimal(Object value) {
		if (value == null) {
			return null;
		}
		return value instanceof BigDecimal ? (BigDecimal) value : new BigDecimal(value.toString());
	}

	/**
	 * A test on a patient's bill and what has been paid for it
	 */
	static class BillItem {

		Integer patientId;
		Integer conceptId;
		Date createdDate;
		BigDecimal quantity;
		boolean markedPaid;
		/** What the patient owes on the bill this item is part of; nothing when insurance covers it all */
		BigDecimal patientDue;
		BigDecimal paidQuantity;
		Date lastPaymentDate;

		PaymentStatus getStatus() {
			if (patientDue != null && patientDue.signum() <= 0) {
				return PaymentStatus.PAID; // nothing for the patient to pay, as the billing module counts it
			}
			BigDecimal paid = paidQuantity == null ? BigDecimal.ZERO : paidQuantity;
			BigDecimal due = quantity == null ? BigDecimal.ONE : quantity;
			if (markedPaid || paid.compareTo(due) >= 0) {
				return PaymentStatus.PAID;
			}
			return paid.signum() > 0 ? PaymentStatus.PARTIALLY_PAID : PaymentStatus.NOT_PAID;
		}
	}

	/**
	 * The payment status of one order, and when it was last paid
	 */
	public static class OrderPayment {

		private final PaymentStatus status;
		private final Date lastPaymentDate;

		public OrderPayment(PaymentStatus status, Date lastPaymentDate) {
			this.status = status;
			this.lastPaymentDate = lastPaymentDate;
		}

		public PaymentStatus getStatus() {
			return status;
		}

		public Date getLastPaymentDate() {
			return lastPaymentDate;
		}
	}
}
