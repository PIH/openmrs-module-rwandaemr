package org.openmrs.module.rwandaemr.billing;

import org.openmrs.Encounter;
import org.openmrs.Visit;
import org.openmrs.module.mohbilling.model.Admission;
import org.openmrs.module.mohbilling.model.GlobalBill;
import org.openmrs.module.rwandaemr.billing.verification.BillVerificationService;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Which encounters of the patient belong to a bill, the same way the mohbilling bill report links a bill to clinical
 * data: through the visit of its admission ({@code moh_bill_admission.visit_id}).
 * <ul>
 *     <li>With a visit: the encounters of that visit, and those not put in any visit during the visit</li>
 *     <li>Without a visit: the encounters from the day of the admission to the day the bill was closed</li>
 * </ul>
 */
public class BillJourneyScope {

	private static final String SQL_DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

	private final Integer visitId;
	private final Date start;
	private final Date end;

	public BillJourneyScope(GlobalBill globalBill) {
		Admission admission = globalBill == null ? null : globalBill.getAdmission();
		Visit visit = admission == null ? null : admission.getVisit();
		if (visit != null && visit.getVisitId() != null) {
			visitId = visit.getVisitId();
			start = visit.getStartDatetime();
			// An open visit runs until the bill was closed
			end = visit.getStopDatetime() != null ? visit.getStopDatetime()
					: BillVerificationService.endOfDay(globalBill.getClosingDate());
		}
		else {
			visitId = null;
			// Whole days: registration, vitals and consultation happen before the cashier opens the admission
			start = admission == null ? null : BillVerificationService.startOfDay(admission.getAdmissionDate());
			end = globalBill == null ? null : BillVerificationService.endOfDay(globalBill.getClosingDate());
		}
	}

	/**
	 * @return the visit of the bill, or null if its admission is not linked to one
	 */
	public Integer getVisitId() {
		return visitId;
	}

	public Date getStart() {
		return start;
	}

	public Date getEnd() {
		return end;
	}

	/**
	 * @return true if the encounter, of the patient of the bill, belongs to the bill
	 */
	public boolean contains(Encounter encounter) {
		if (encounter == null) {
			return false;
		}
		if (visitId != null && encounter.getVisit() != null) {
			return visitId.equals(encounter.getVisit().getVisitId());
		}
		Date datetime = encounter.getEncounterDatetime();
		return datetime != null && (start == null || !datetime.before(start)) && (end == null || !datetime.after(end));
	}

	/**
	 * @return the condition on the encounter table, aliased {@code e}, for the encounters of the patient that belong
	 * to the bill.  Built from ids and dates only.
	 */
	public String toSqlWhere(Integer patientId) {
		StringBuilder where = new StringBuilder("e.patient_id = ").append(patientId.intValue());
		String period = period();
		if (visitId != null) {
			where.append(" and (e.visit_id = ").append(visitId.intValue());
			where.append(" or (e.visit_id is null").append(period).append("))");
		}
		else {
			where.append(period);
		}
		return where.toString();
	}

	private String period() {
		SimpleDateFormat format = new SimpleDateFormat(SQL_DATE_TIME_PATTERN);
		StringBuilder ret = new StringBuilder();
		if (start != null) {
			ret.append(" and e.encounter_datetime >= '").append(format.format(start)).append("'");
		}
		if (end != null) {
			ret.append(" and e.encounter_datetime <= '").append(format.format(end)).append("'");
		}
		return ret.toString();
	}
}
