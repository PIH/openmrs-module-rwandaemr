package org.openmrs.module.rwandaemr.billing;

import org.junit.jupiter.api.Test;
import org.openmrs.Encounter;
import org.openmrs.Visit;
import org.openmrs.module.mohbilling.model.Admission;
import org.openmrs.module.mohbilling.model.GlobalBill;

import java.text.SimpleDateFormat;
import java.util.Date;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

public class BillJourneyScopeTest {

	private static Date date(String value) throws Exception {
		return new SimpleDateFormat("yyyy-MM-dd HH:mm").parse(value);
	}

	private static GlobalBill bill(Visit visit, String admitted, String closed) throws Exception {
		Admission admission = new Admission();
		admission.setAdmissionDate(date(admitted));
		admission.setVisit(visit);
		GlobalBill bill = new GlobalBill();
		bill.setAdmission(admission);
		bill.setClosingDate(date(closed));
		return bill;
	}

	private static Visit visit(int id, String start, String stop) throws Exception {
		Visit visit = new Visit(id);
		visit.setStartDatetime(date(start));
		visit.setStopDatetime(stop == null ? null : date(stop));
		return visit;
	}

	private static Encounter encounter(Visit visit, String datetime) throws Exception {
		Encounter encounter = new Encounter();
		encounter.setVisit(visit);
		encounter.setEncounterDatetime(date(datetime));
		return encounter;
	}

	@Test
	public void shouldOnlyIncludeTheEncountersOfTheVisitOfTheBill() throws Exception {
		Visit billVisit = visit(18, "2026-11-18 07:30", "2026-11-18 16:00");
		Visit earlierVisit = visit(11, "2026-11-11 08:00", "2026-11-11 15:00");
		BillJourneyScope scope = new BillJourneyScope(bill(billVisit, "2026-11-11 09:00", "2026-11-18 16:30"));

		assertThat(scope.contains(encounter(billVisit, "2026-11-18 08:00")), is(true));
		assertThat(scope.contains(encounter(earlierVisit, "2026-11-11 10:00")), is(false));
	}

	@Test
	public void shouldIncludeEncountersWithoutAVisitDuringTheVisit() throws Exception {
		Visit billVisit = visit(18, "2026-11-18 07:30", "2026-11-18 16:00");
		BillJourneyScope scope = new BillJourneyScope(bill(billVisit, "2026-11-18 09:00", "2026-11-18 16:30"));

		assertThat(scope.contains(encounter(null, "2026-11-18 12:00")), is(true));
		assertThat(scope.contains(encounter(null, "2026-11-11 12:00")), is(false));
	}

	@Test
	public void shouldUseTheDaysOfTheAdmissionWhenTheBillHasNoVisit() throws Exception {
		BillJourneyScope scope = new BillJourneyScope(bill(null, "2026-11-18 09:00", "2026-11-18 16:30"));

		assertThat(scope.contains(encounter(null, "2026-11-18 07:00")), is(true));
		assertThat(scope.contains(encounter(null, "2026-11-17 23:00")), is(false));
	}

	@Test
	public void shouldLimitTheJourneyQueryToTheVisit() throws Exception {
		Visit billVisit = visit(18, "2026-11-18 07:30", null);
		String where = new BillJourneyScope(bill(billVisit, "2026-11-18 09:00", "2026-11-18 16:30")).toSqlWhere(5);

		assertThat(where, containsString("e.patient_id = 5"));
		assertThat(where, containsString("e.visit_id = 18"));
		assertThat(where, containsString("e.visit_id is null"));
		assertThat(where, not(containsString("2026-11-11")));
	}
}
