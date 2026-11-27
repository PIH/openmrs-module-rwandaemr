package org.openmrs.module.rwandaemr.billing.verification;

import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class LabTestProgressTest {

	@Test
	public void shouldBeOrderedUntilTheLabReceivesTheSpecimen() {
		assertThat(new LabTestProgress().getStatus(), equalTo(LabTestProgress.Status.ORDERED));
	}

	@Test
	public void shouldBeReceivedOnceTheLabRecordsTheSpecimen() {
		LabTestProgress test = new LabTestProgress();
		test.setSpecimenRecordedAt(new Date());

		assertThat(test.getStatus(), equalTo(LabTestProgress.Status.RECEIVED));
	}

	@Test
	public void shouldBeReceivedWhenTheOrderIsInProgress() {
		LabTestProgress test = new LabTestProgress();
		test.setFulfillerStatus("IN_PROGRESS");

		assertThat(test.getStatus(), equalTo(LabTestProgress.Status.RECEIVED));
	}

	@Test
	public void shouldBeCompletedOnceAResultIsEnteredEvenWithoutTheSpecimenReceivedDate() {
		LabTestProgress test = new LabTestProgress();
		test.setResultsEnteredAt(new Date());

		assertThat(test.getStatus(), equalTo(LabTestProgress.Status.COMPLETED));
	}

	@Test
	public void shouldBeNotPerformedWhenTheLabGivesAReason() {
		LabTestProgress test = new LabTestProgress();
		test.setSpecimenRecordedAt(new Date());
		test.setNotPerformedReason("Specimen haemolysed");

		assertThat(test.getStatus(), equalTo(LabTestProgress.Status.NOT_PERFORMED));
	}

	@Test
	public void shouldBeCancelledWhenTheOrderWasStoppedBeforeAnyResult() {
		LabTestProgress test = new LabTestProgress();
		test.setCancelled(true);

		assertThat(test.getStatus(), equalTo(LabTestProgress.Status.CANCELLED));
	}

	@Test
	public void shouldStayCompletedWhenAnOrderWithAResultIsStopped() {
		LabTestProgress test = new LabTestProgress();
		test.setCancelled(true);
		test.setFulfillerStatus("COMPLETED");

		assertThat(test.getStatus(), equalTo(LabTestProgress.Status.COMPLETED));
	}
}
