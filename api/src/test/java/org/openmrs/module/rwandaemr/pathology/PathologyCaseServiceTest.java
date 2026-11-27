package org.openmrs.module.rwandaemr.pathology;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.Encounter;
import org.openmrs.Order;
import org.openmrs.OrderType;
import org.openmrs.TestOrder;
import org.openmrs.api.APIAuthenticationException;
import org.openmrs.api.OrderService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PathologyCaseServiceTest {

	private OrderType pathologyOrderType;
	private PathologyAccessionNumberGenerator accessionNumberGenerator;
	private InMemoryCaseRepository caseRepository;
	private Set<String> privileges;
	private List<UpdatedOrder> updatedOrders;
	private PathologyCaseService service;
	private Encounter encounter;
	private Encounter report;
	private TestOrder biopsy;
	private TestOrder boneMarrow;

	@BeforeEach
	public void setUp() {
		pathologyOrderType = new OrderType(5);
		PathologyConfig pathologyConfig = mock(PathologyConfig.class);
		when(pathologyConfig.getPathologyOrderType()).thenReturn(pathologyOrderType);
		accessionNumberGenerator = mock(PathologyAccessionNumberGenerator.class);
		when(accessionNumberGenerator.next()).thenReturn("PT26-0001", "PT26-0002");
		caseRepository = new InMemoryCaseRepository();
		privileges = new HashSet<String>();
		privileges.add(PathologyConfig.PRIVILEGE_PROCESS_SPECIMENS);
		updatedOrders = new ArrayList<UpdatedOrder>();
		service = new PathologyCaseService(mock(OrderService.class), pathologyConfig, caseRepository,
				accessionNumberGenerator) {
			@Override
			protected void updateOrder(TestOrder order, Order.FulfillerStatus fulfillerStatus, String fulfillerComment,
									   String accessionNumber) {
				updatedOrders.add(new UpdatedOrder(order, fulfillerStatus, fulfillerComment, accessionNumber));
				order.setFulfillerStatus(fulfillerStatus);
				order.setAccessionNumber(accessionNumber);
			}

			@Override
			protected boolean hasPrivilege(String privilege) {
				return privileges.contains(privilege);
			}
		};

		encounter = new Encounter(10);
		report = new Encounter(20);
		biopsy = addOrder(encounter, 1, pathologyOrderType);
		boneMarrow = addOrder(encounter, 2, pathologyOrderType);
	}

	@Test
	public void shouldOnlyTreatActivePathologyOrdersAsTheTestsOfTheRequest() {
		addOrder(encounter, 3, new OrderType(6));
		TestOrder voided = addOrder(encounter, 4, pathologyOrderType);
		voided.setVoided(true);
		TestOrder discontinuation = addOrder(encounter, 5, pathologyOrderType);
		discontinuation.setAction(Order.Action.DISCONTINUE);

		assertThat(service.getActiveOrders(encounter), hasSize(2));
		assertThat(service.getActiveOrders(encounter).contains(biopsy), equalTo(true));
		assertThat(service.getActiveOrders(encounter).contains(boneMarrow), equalTo(true));
	}

	@Test
	public void shouldBeOrderedUntilTheLabReceivesTheSpecimen() {
		assertThat(service.getCase(encounter), nullValue());
		assertThat(service.getStatus(encounter), equalTo(PathologyStatus.ORDERED));
	}

	@Test
	public void shouldGiveTheAccessionNumberToEachTestWhenTheSpecimenIsReceived() {
		PathologyCase pathologyCase = service.receive(encounter);

		assertThat(pathologyCase.getAccessionNumber(), equalTo("PT26-0001"));
		assertThat(pathologyCase.getStatus(), equalTo(PathologyStatus.RECEIVED));
		assertThat(service.getStatus(encounter), equalTo(PathologyStatus.RECEIVED));
		assertThat(updatedOrders, hasSize(2));
		for (UpdatedOrder updated : updatedOrders) {
			assertThat(updated.fulfillerStatus, equalTo(Order.FulfillerStatus.RECEIVED));
			assertThat(updated.accessionNumber, equalTo("PT26-0001"));
		}
		assertThat(caseRepository.history, contains(PathologyStatus.RECEIVED));
	}

	@Test
	public void shouldNotReceiveASpecimenTwice() {
		service.receive(encounter);

		IllegalStateException e = assertThrows(IllegalStateException.class, () -> service.receive(encounter));
		assertThat(e.getMessage(), containsString("received"));
		verify(accessionNumberGenerator, times(1)).next();
	}

	@Test
	public void shouldNotReceiveARequestWithNoTestsOrdered() {
		Encounter empty = new Encounter(11);

		IllegalStateException e = assertThrows(IllegalStateException.class, () -> service.receive(empty));
		assertThat(e.getMessage(), containsString("no pathology tests"));
		verify(accessionNumberGenerator, never()).next();
	}

	@Test
	public void shouldRequireThePrivilegeToProcessSpecimens() {
		privileges.clear();

		assertThrows(APIAuthenticationException.class, () -> service.receive(encounter));
		assertThrows(APIAuthenticationException.class, () -> service.reject(encounter, "Not labelled"));
		assertThat(service.getStatus(encounter), equalTo(PathologyStatus.ORDERED));
	}

	@Test
	public void shouldMoveThroughTheLabAsResultsAreSavedOnTheReport() {
		service.receive(encounter);
		service.recordReport(encounter, report, steps(PathologyStatus.GROSSED));
		service.recordReport(encounter, report, steps(PathologyStatus.GROSSED, PathologyStatus.PROCESSED));
		PathologyCase pathologyCase = service.recordReport(encounter, report,
				steps(PathologyStatus.GROSSED, PathologyStatus.PROCESSED, PathologyStatus.ADDITIONAL_STAINS));

		assertThat(pathologyCase.getStatus(), equalTo(PathologyStatus.ADDITIONAL_STAINS));
		assertThat(pathologyCase.getAccessionNumber(), equalTo("PT26-0001"));
		assertThat(pathologyCase.getReportEncounterId(), equalTo(20));
		assertThat(biopsy.getFulfillerStatus(), equalTo(Order.FulfillerStatus.IN_PROGRESS));
		assertThat(biopsy.getAccessionNumber(), equalTo("PT26-0001"));
		assertThat(caseRepository.history, contains(PathologyStatus.RECEIVED, PathologyStatus.GROSSED,
				PathologyStatus.PROCESSED, PathologyStatus.ADDITIONAL_STAINS));
	}

	@Test
	public void shouldRecordEachStepTheReportHasResultsForWhenSavedOnce() {
		service.receive(encounter);
		privileges.add(PathologyConfig.PRIVILEGE_SIGN_OUT);

		PathologyCase pathologyCase = service.recordReport(encounter, report,
				steps(PathologyStatus.GROSSED, PathologyStatus.PROCESSED, PathologyStatus.SIGNED_OUT));

		assertThat(pathologyCase.getStatus(), equalTo(PathologyStatus.SIGNED_OUT));
		assertThat(biopsy.getFulfillerStatus(), equalTo(Order.FulfillerStatus.COMPLETED));
		assertThat(caseRepository.history, contains(PathologyStatus.RECEIVED, PathologyStatus.GROSSED,
				PathologyStatus.PROCESSED, PathologyStatus.SIGNED_OUT));
	}

	@Test
	public void shouldSendTheReportForReviewThenLetAPathologistSignItOut() {
		service.receive(encounter);

		PathologyCase pathologyCase = service.recordReport(encounter, report,
				steps(PathologyStatus.PROCESSED, PathologyStatus.SENT_FOR_REVIEW, PathologyStatus.SIGNED_OUT));
		assertThat(pathologyCase.getStatus(), equalTo(PathologyStatus.SENT_FOR_REVIEW));
		assertThat(pathologyCase.getStatus().isInLab(), equalTo(true));
		assertThat(biopsy.getFulfillerStatus(), equalTo(Order.FulfillerStatus.IN_PROGRESS));

		privileges.add(PathologyConfig.PRIVILEGE_SIGN_OUT);
		pathologyCase = service.recordReport(encounter, report,
				steps(PathologyStatus.PROCESSED, PathologyStatus.SENT_FOR_REVIEW, PathologyStatus.SIGNED_OUT));
		assertThat(pathologyCase.getStatus(), equalTo(PathologyStatus.SIGNED_OUT));
		assertThat(caseRepository.history, contains(PathologyStatus.RECEIVED, PathologyStatus.PROCESSED,
				PathologyStatus.SENT_FOR_REVIEW, PathologyStatus.SIGNED_OUT));
	}

	@Test
	public void shouldAllowGrossingToBeSkipped() {
		service.receive(encounter);

		assertThat(service.recordReport(encounter, report, steps(PathologyStatus.PROCESSED)).getStatus(),
				equalTo(PathologyStatus.PROCESSED));
	}

	@Test
	public void shouldRecordProcessingForAStainOrDiagnosisReportedWithoutIt() {
		service.receive(encounter);

		service.recordReport(encounter, report, steps(PathologyStatus.ADDITIONAL_STAINS));

		assertThat(caseRepository.history, contains(PathologyStatus.RECEIVED, PathologyStatus.PROCESSED,
				PathologyStatus.ADDITIONAL_STAINS));
	}

	@Test
	public void shouldNotMoveACaseBackWhenResultsAreRemovedFromTheReport() {
		service.receive(encounter);
		service.recordReport(encounter, report, steps(PathologyStatus.GROSSED, PathologyStatus.PROCESSED));

		PathologyCase pathologyCase = service.recordReport(encounter, report, steps(PathologyStatus.GROSSED));

		assertThat(pathologyCase.getStatus(), equalTo(PathologyStatus.PROCESSED));
		assertThat(caseRepository.history, contains(PathologyStatus.RECEIVED, PathologyStatus.GROSSED,
				PathologyStatus.PROCESSED));
	}

	@Test
	public void shouldNotRecordAReportForASpecimenNotReceived() {
		assertThrows(IllegalStateException.class,
				() -> service.recordReport(encounter, report, steps(PathologyStatus.GROSSED)));
		service.reject(encounter, "Not labelled");
		assertThrows(IllegalStateException.class,
				() -> service.recordReport(encounter, report, steps(PathologyStatus.GROSSED)));
	}

	@Test
	public void shouldOnlyLetAPathologistSignOut() {
		service.receive(encounter);

		PathologyCase pathologyCase = service.recordReport(encounter, report,
				steps(PathologyStatus.PROCESSED, PathologyStatus.SIGNED_OUT));
		assertThat(pathologyCase.getStatus(), equalTo(PathologyStatus.PROCESSED));

		privileges.clear();
		privileges.add(PathologyConfig.PRIVILEGE_SIGN_OUT);
		pathologyCase = service.recordReport(encounter, report, steps(PathologyStatus.PROCESSED, PathologyStatus.SIGNED_OUT));
		assertThat(pathologyCase.getStatus(), equalTo(PathologyStatus.SIGNED_OUT));
	}

	@Test
	public void shouldRequireThePrivilegeOfTheLabOrOfAPathologistToRecordAReport() {
		service.receive(encounter);
		privileges.clear();

		assertThrows(APIAuthenticationException.class,
				() -> service.recordReport(encounter, report, steps(PathologyStatus.GROSSED)));
		assertThat(service.getStatus(encounter), equalTo(PathologyStatus.RECEIVED));
	}

	@Test
	public void shouldKeepOneReportForEachCase() {
		service.receive(encounter);
		service.recordReport(encounter, report, steps(PathologyStatus.GROSSED));

		IllegalStateException e = assertThrows(IllegalStateException.class,
				() -> service.recordReport(encounter, new Encounter(21), steps(PathologyStatus.PROCESSED)));
		assertThat(e.getMessage(), containsString("already has a pathology report"));
		assertThat(service.getStatus(encounter), equalTo(PathologyStatus.GROSSED));
	}

	@Test
	public void shouldGiveTestsAddedAfterReceptionTheAccessionNumber() {
		service.receive(encounter);
		TestOrder added = addOrder(encounter, 3, pathologyOrderType);

		service.recordReport(encounter, report, steps(PathologyStatus.GROSSED));

		assertThat(added.getAccessionNumber(), equalTo("PT26-0001"));
		assertThat(added.getFulfillerStatus(), equalTo(Order.FulfillerStatus.IN_PROGRESS));
	}

	@Test
	public void shouldRejectASpecimenAsItArrivesWithoutUsingANumber() {
		PathologyCase pathologyCase = service.reject(encounter, "  Not labelled ");

		assertThat(pathologyCase.getStatus(), equalTo(PathologyStatus.REJECTED));
		assertThat(pathologyCase.getRejectionReason(), equalTo("Not labelled"));
		assertThat(pathologyCase.getAccessionNumber(), nullValue());
		verify(accessionNumberGenerator, never()).next();
		assertThat(updatedOrders, hasSize(2));
		for (UpdatedOrder updated : updatedOrders) {
			assertThat(updated.fulfillerStatus, equalTo(Order.FulfillerStatus.DECLINED));
			assertThat(updated.fulfillerComment, equalTo("Specimen rejected: Not labelled"));
		}
	}

	@Test
	public void shouldRejectAReceivedSpecimenKeepingItsNumber() {
		service.receive(encounter);

		PathologyCase pathologyCase = service.reject(encounter, "Wrong fixative");

		assertThat(pathologyCase.getStatus(), equalTo(PathologyStatus.REJECTED));
		assertThat(pathologyCase.getAccessionNumber(), equalTo("PT26-0001"));
		assertThat(biopsy.getFulfillerStatus(), equalTo(Order.FulfillerStatus.DECLINED));
	}

	@Test
	public void shouldRequireAReasonToRejectASpecimen() {
		assertThrows(IllegalArgumentException.class, () -> service.reject(encounter, " "));
		assertThrows(IllegalArgumentException.class, () -> service.reject(encounter, new String(new char[256]).replace('\0', 'x')));
		assertThat(service.getStatus(encounter), equalTo(PathologyStatus.ORDERED));
	}

	@Test
	public void shouldNotRejectASpecimenOnceWorkHasStarted() {
		service.receive(encounter);
		service.recordReport(encounter, report, steps(PathologyStatus.GROSSED));

		assertThrows(IllegalStateException.class, () -> service.reject(encounter, "Too late"));
		assertThat(service.getReasonCannotMoveTo(encounter, PathologyStatus.REJECTED), notNullValue());
	}

	private Set<PathologyStatus> steps(PathologyStatus... steps) {
		return new HashSet<PathologyStatus>(Arrays.asList(steps));
	}

	private TestOrder addOrder(Encounter encounter, int orderId, OrderType orderType) {
		TestOrder order = new TestOrder();
		order.setOrderId(orderId);
		order.setOrderType(orderType);
		order.setAction(Order.Action.NEW);
		order.setDateActivated(new Date(System.currentTimeMillis() - 60000));
		encounter.addOrder(order);
		return order;
	}

	private static class UpdatedOrder {

		final TestOrder order;
		final Order.FulfillerStatus fulfillerStatus;
		final String fulfillerComment;
		final String accessionNumber;

		UpdatedOrder(TestOrder order, Order.FulfillerStatus fulfillerStatus, String fulfillerComment, String accessionNumber) {
			this.order = order;
			this.fulfillerStatus = fulfillerStatus;
			this.fulfillerComment = fulfillerComment;
			this.accessionNumber = accessionNumber;
		}
	}

	private static class InMemoryCaseRepository extends PathologyCaseRepository {

		final Map<Integer, PathologyCase> cases = new HashMap<Integer, PathologyCase>();
		final List<PathologyStatus> history = new ArrayList<PathologyStatus>();

		InMemoryCaseRepository() {
			super(null);
		}

		@Override
		public PathologyCase getCase(Integer encounterId) {
			PathologyCase stored = cases.get(encounterId);
			return stored == null ? null : copy(stored);
		}

		@Override
		public Map<Integer, PathologyCase> getCases(Collection<Integer> encounterIds) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void saveCase(PathologyCase pathologyCase) {
			if (pathologyCase.getPathologyCaseId() == null) {
				pathologyCase.setPathologyCaseId(cases.size() + 1);
			}
			cases.put(pathologyCase.getEncounterId(), copy(pathologyCase));
			history.add(pathologyCase.getStatus());
		}

		@Override
		public void saveReportEncounter(PathologyCase pathologyCase) {
			cases.get(pathologyCase.getEncounterId()).setReportEncounterId(pathologyCase.getReportEncounterId());
		}

		private PathologyCase copy(PathologyCase pathologyCase) {
			PathologyCase ret = new PathologyCase();
			ret.setPathologyCaseId(pathologyCase.getPathologyCaseId());
			ret.setEncounterId(pathologyCase.getEncounterId());
			ret.setAccessionNumber(pathologyCase.getAccessionNumber());
			ret.setStatus(pathologyCase.getStatus());
			ret.setRejectionReason(pathologyCase.getRejectionReason());
			ret.setReportEncounterId(pathologyCase.getReportEncounterId());
			return ret;
		}
	}
}
