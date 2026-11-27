package org.openmrs.module.rwandaemr.billing;

import org.junit.jupiter.api.Test;
import org.openmrs.Visit;
import org.openmrs.module.mohbilling.model.Admission;
import org.openmrs.module.mohbilling.model.GlobalBill;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

public class VisitTransferLookupTest {

	private static final Date START = new Date(1_000_000_000L);
	private static final Date END = new Date(1_100_000_000L);

	/**
	 * Answers each query by the table it reads
	 */
	private static class FakeSql implements VisitTransferLookup.SqlRunner {
		List<Object[]> tables = new ArrayList<Object[]>(Arrays.asList(new Object[] { "transfers" },
				new Object[] { "transfer_referral_feedback" }, new Object[] { "transfer_receiving_facilities" }));
		List<Object[]> obs = new ArrayList<Object[]>();
		List<Object[]> localCopies = new ArrayList<Object[]>();
		List<Object[]> feedback = new ArrayList<Object[]>();
		List<Object[]> sent = new ArrayList<Object[]>();
		List<String> queries = new ArrayList<String>();

		@Override
		public List<Object[]> list(String sql, Map<String, Object> parameters) {
			queries.add(sql);
			if (sql.contains("information_schema")) {
				return tables;
			}
			if (sql.contains("from obs o")) {
				return obs;
			}
			if (sql.contains("transfer_referral_feedback f")) {
				return feedback;
			}
			if (sql.contains("t.hie_transfer_id in (:ids)")) {
				return localCopies;
			}
			return sent;
		}
	}

	private static BillJourneyScope scope() {
		Visit visit = new Visit(55);
		visit.setStartDatetime(START);
		visit.setStopDatetime(END);
		Admission admission = new Admission();
		admission.setVisit(visit);
		GlobalBill globalBill = new GlobalBill();
		globalBill.setAdmission(admission);
		return new BillJourneyScope(globalBill);
	}

	@Test
	public void shouldFindTheTransferRecordedOnTheVisitWithItsLocalCopyAndFeedback() {
		FakeSql sql = new FakeSql();
		sql.obs.add(new Object[] { "TR-1", START, "clerk" });
		sql.obs.add(new Object[] { " tr-1 ", END, "nurse" });
		sql.localCopies.add(new Object[] { "TR-1", "Health centre A", "OPD", START, "Routine", "GENERAL", "Fever", "Malaria", "local-uuid" });
		sql.feedback.add(new Object[] { "TR-1", 1, END });
		sql.sent.add(new Object[] { "TR-1", START, "clerk", "Hospital B", "Surgery", "Routine", "GENERAL", "NONE", null, 0 });
		sql.sent.add(new Object[] { null, END, "doctor", "Hospital B", "Surgery", "Emergency", "MATERNITY", "APPROVED", END, 1, "sent-uuid" });

		VisitTransfers transfers = new VisitTransferLookup(sql, 7).getTransfers(12, scope());

		assertThat(transfers.getReceived(), hasSize(1));
		VisitTransfers.Received received = transfers.getReceived().get(0);
		assertThat(received.getHieTransferId(), equalTo("TR-1"));
		assertThat(received.getRecordedBy(), equalTo("clerk"));
		assertThat(received.isLocalCopy(), is(true));
		assertThat(received.getSendingFacility(), equalTo("Health centre A"));
		assertThat(received.isCounterReferralCompleted(), is(true));
		assertThat(received.getLocalUuid(), equalTo("local-uuid"));
		// The local copy of the received transfer is not a transfer sent from here
		assertThat(transfers.getSent(), hasSize(1));
		VisitTransfers.Sent sent = transfers.getSent().get(0);
		assertThat(sent.getApprovalLabel(), equalTo("Approved"));
		assertThat(sent.isHieSent(), is(true));
		assertThat(sent.getUuid(), equalTo("sent-uuid"));
		assertThat(VisitTransfers.formKindLabel(sent.getFormKind()), equalTo("Maternity"));
		assertThat(transfers.isEmpty(), is(false));
		assertThat(sql.queries.get(1), containsString("e.visit_id = 55"));
	}

	@Test
	public void shouldBeEmptyWithoutAnyTransfer() {
		VisitTransfers transfers = new VisitTransferLookup(new FakeSql(), 7).getTransfers(12, scope());

		assertThat(transfers.isEmpty(), is(true));
	}

	@Test
	public void shouldShowAReceivedTransferWithoutALocalCopy() {
		FakeSql sql = new FakeSql();
		sql.obs.add(new Object[] { "TR-9", START, "clerk" });

		VisitTransfers transfers = new VisitTransferLookup(sql, 7).getTransfers(12, scope());

		assertThat(transfers.getReceived().get(0).isLocalCopy(), is(false));
		assertThat(transfers.getReceived().get(0).isCounterReferralCompleted(), is(false));
	}

	@Test
	public void shouldShowNothingWithoutTheTransferApp() {
		FakeSql sql = new FakeSql();
		sql.tables = Collections.emptyList();

		assertThat(new VisitTransferLookup(sql, 7).getTransfers(12, scope()), nullValue());
	}
}
