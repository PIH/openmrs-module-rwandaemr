package org.openmrs.module.rwandaemr.page.controller.pathology;

import org.junit.jupiter.api.Test;
import org.openmrs.module.rwandaemr.billing.PaymentStatus;
import org.openmrs.module.rwandaemr.pathology.PathologyStatus;
import org.openmrs.module.rwandaemr.pathology.queue.PathologyQueueRow;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

public class PathologyQueueRequestTest {

	@Test
	public void shouldShowTheLastWeekAndCasesStillInTheLabByDefault() {
		PathologyQueueRequest request = new PathologyQueueRequest(null, null, null, null, null, null, null);

		assertThat(request.getEndDate(), equalTo(LocalDate.now().toString()));
		assertThat(request.getStartDate(), equalTo(LocalDate.now().minusDays(PathologyQueueRequest.DEFAULT_DAYS).toString()));
		assertThat(request.isIncludeCasesInLab(), equalTo(true));
		assertThat(request.isValid(), equalTo(true));
		assertThat(new PathologyQueueRequest(null, null, null, null, null, null, "NO").isIncludeCasesInLab(), equalTo(false));
	}

	@Test
	public void shouldRejectInvalidDateRanges() {
		assertThat(new PathologyQueueRequest("2026-10-20", "2026-10-01", null, null, null, null, null).getError(),
				containsString("on or after"));
		assertThat(new PathologyQueueRequest("2026-01-01", "2026-12-31", null, null, null, null, null).getError(),
				containsString("at most"));
		assertThat(new PathologyQueueRequest("yesterday", null, null, null, null, null, null).getError(),
				containsString("valid dates"));
	}

	@Test
	public void shouldFilterByStatusPaymentTestAndText() {
		PathologyQueueRow received = row("PAT2026-0001", "Aline Uwase", PathologyStatus.RECEIVED, false,
				PaymentStatus.PAID, "Examination of a biopsy");
		PathologyQueueRow cancelled = row(null, "Jean Mugabo", PathologyStatus.ORDERED, true,
				PaymentStatus.NOT_PAID, "Bone marrow aspirate and/or biopsy");
		List<PathologyQueueRow> rows = Arrays.asList(received, cancelled);

		assertThat(filter(rows, "RECEIVED", null, null, null), contains(received));
		assertThat(filter(rows, "CANCELLED", null, null, null), contains(cancelled));
		assertThat(filter(rows, "ORDERED", null, null, null).isEmpty(), equalTo(true));
		assertThat(filter(rows, null, "NOT_PAID", null, null), contains(cancelled));
		assertThat(filter(rows, null, null, "Examination of a biopsy", null), contains(received));
		assertThat(filter(rows, null, null, null, "pat2026-0001"), contains(received));
		assertThat(filter(rows, null, null, null, "mugabo"), contains(cancelled));
	}

	private List<PathologyQueueRow> filter(List<PathologyQueueRow> rows, String status, String payment, String test,
										   String query) {
		return new PathologyQueueRequest(null, null, status, payment, test, query, null).filter(rows);
	}

	private PathologyQueueRow row(String accessionNumber, String patientName, PathologyStatus status, boolean cancelled,
								  PaymentStatus paymentStatus, String test) {
		PathologyQueueRow row = new PathologyQueueRow();
		row.setAccessionNumber(accessionNumber);
		row.setPatientName(patientName);
		row.setStatus(status);
		row.setCancelled(cancelled);
		row.setPaymentStatus(paymentStatus);
		row.getTests().add(test);
		return row;
	}
}
