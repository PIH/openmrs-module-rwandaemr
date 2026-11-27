package org.openmrs.module.rwandaemr.integration.insurance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.openmrs.module.rwandaemr.integration.IntegrationResponse;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MMI approvals (RHIP Unified Insurance Integration Guide: MMI, section 7)
 */
public class RhipApprovalTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private InsuranceEligibilityProvider provider;

	private RhipApprovalRequestLogRepository repository;

	private RhipApprovalRequestService requestService;

	private RhipApprovalStatusService statusService;

	@BeforeEach
	public void setUp() {
		provider = mock(InsuranceEligibilityProvider.class);
		when(provider.buildApprovalRequestPayload(any(), any(), any(), any(), any(), anyList(), any(), anyList()))
				.thenCallRealMethod();
		repository = mock(RhipApprovalRequestLogRepository.class);
		requestService = new RhipApprovalRequestService(provider, repository);
		statusService = new RhipApprovalStatusService(provider, repository);
		RhipApprovalStatusService.resetPause();
	}

	@AfterEach
	public void tearDown() {
		RhipApprovalStatusService.resetPause();
	}

	// Statuses

	@Test
	public void normalizeStatus_shouldReadMmisOwnWords() {
		assertThat(RhipApprovalDecision.normalizeStatus("Accepted"), equalTo("APPROVED"));
		assertThat(RhipApprovalDecision.normalizeStatus("approved"), equalTo("APPROVED"));
		assertThat(RhipApprovalDecision.normalizeStatus("Partially approved"), equalTo("PARTIALLY_APPROVED"));
		assertThat(RhipApprovalDecision.normalizeStatus("Declined"), equalTo("REJECTED"));
		assertThat(RhipApprovalDecision.normalizeStatus("created"), equalTo("PENDING"));
		assertThat(RhipApprovalDecision.normalizeStatus("edited"), equalTo("PENDING"));
		assertThat(RhipApprovalDecision.normalizeStatus("In review"), equalTo("PENDING"));
		assertThat(RhipApprovalDecision.normalizeStatus(" "), nullValue());
	}

	@Test
	public void lineStatus_shouldBePartialWhenMmiApprovesLessThanRequested() {
		assertThat(RhipApprovalDecision.lineStatus("approved", null, new BigDecimal("2"), new BigDecimal("1")),
				equalTo("PARTIALLY_APPROVED"));
		assertThat(RhipApprovalDecision.lineStatus("approved", null, new BigDecimal("2"), new BigDecimal("2")),
				equalTo("APPROVED"));
		assertThat(RhipApprovalDecision.lineStatus("approved", null, new BigDecimal("2"), BigDecimal.ZERO),
				equalTo("REJECTED"));
	}

	@Test
	public void lineStatus_shouldUseTheApprovalsStatusWhenTheLineHasNone() {
		assertThat(RhipApprovalDecision.lineStatus(null, "approved", BigDecimal.ONE, null), equalTo("APPROVED"));
		assertThat(RhipApprovalDecision.lineStatus(null, null, BigDecimal.ONE, null), equalTo("PENDING"));
	}

	@Test
	public void describeRequestAnswer_shouldListEachRefusedLine() throws Exception {
		JsonNode root = MAPPER.readTree("{\"success\":false,\"message\":\"Some procedures could not be requested; see details.\","
				+ "\"errors\":[{\"productCode\":\"RHIC-OPTH-072\",\"message\":\"Cataract surgery\","
				+ "\"reason\":\"Requested unit price 300,000 exceeds the maximum of 250,000 allowed for this procedure.\"}]}");
		assertThat(RhipApprovalDecision.describeRequestAnswer(root, null), equalTo(
				"Some procedures could not be requested; see details. RHIC-OPTH-072: Requested unit price 300,000 exceeds "
						+ "the maximum of 250,000 allowed for this procedure."));
	}

	// Requests

	@Test
	public void submit_shouldSendTheReceptionsOtherProceduresWithTheNewOne() throws Exception {
		RhipApprovalRequestLogEntry held = mmiLine(10, "RHIC-OPTH-072", "1", "PENDING");
		held.setApprovalCode("APR-1");
		when(repository.getActiveByVisitReference("REC-1")).thenReturn(new ArrayList<>(Collections.singletonList(held)));
		when(provider.requestApproval(any())).thenReturn(answer(200,
				"{\"success\":true,\"message\":\"Approval request submitted successfully\","
						+ "\"data\":{\"approvalCode\":\"APR-1\",\"status\":\"edited\"}}"));

		RhipApprovalRequestLogEntry line = mmiLine(null, "RHIC-ANES-001", "1.4", null);
		RhipApprovalRequestService.Result result = requestService.submit(line);

		assertThat(result.isSuccess(), equalTo(true));
		Map<String, Object> payload = sentPayload();
		assertThat(payload.get("insuranceType"), equalTo("mmi"));
		assertThat(payload.get("visitReferenceNumber"), equalTo("REC-1"));
		assertThat(payload.get("practitionerLicenseNumber"), equalTo("RW-MD-1"));
		assertThat(payload.get("comment"), equalTo("Needs a specialist"));
		assertThat(payload, not(hasKey("receptionNumber")));
		assertThat(payload, not(hasKey("licenseNumber")));
		assertThat(MAPPER.writeValueAsString(payload.get("procedures")),
				equalTo("[{\"code\":\"RHIC-OPTH-072\",\"quantity\":1},{\"code\":\"RHIC-ANES-001\",\"quantity\":1}]"));
		assertThat(line.getApprovalStatus(), equalTo("PENDING"));
		assertThat(line.getApprovalCode(), equalTo("APR-1"));
		verify(repository).save(line);
		verify(repository).updateRequestResult(eq(10), eq("PENDING"), eq("APR-1"), eq("REC-1"), eq(BigDecimal.ONE),
				any(), any(), any(), eq(200), any());
	}

	@Test
	public void submitAll_shouldSendAFormsProceduresInOneRequestWithTheReceptionsOthers() throws Exception {
		RhipApprovalRequestLogEntry held = mmiLine(10, "RHIC-OPTH-072", "1", "APPROVED");
		when(repository.getActiveByVisitReference("REC-1")).thenReturn(new ArrayList<>(Collections.singletonList(held)));
		when(provider.requestApproval(any())).thenReturn(answer(200,
				"{\"success\":true,\"data\":{\"approvalCode\":\"APR-1\",\"status\":\"edited\"}}"));
		RhipApprovalRequestLogEntry mri = mmiLine(null, "RHIC-MRSC-001", "1", null);
		RhipApprovalRequestLogEntry eyeAgain = mmiLine(null, "RHIC-OPTH-072", "2", null);
		RhipApprovalRequestLogEntry physio = mmiLine(null, "RHIC-PHYS-005", "3", null);

		RhipApprovalRequestService.Result result = requestService.submitAll(Arrays.asList(mri, eyeAgain, physio));

		assertThat(result.isSuccess(), equalTo(true));
		assertThat(MAPPER.writeValueAsString(sentPayload().get("procedures")), equalTo(
				"[{\"code\":\"RHIC-OPTH-072\",\"quantity\":2},{\"code\":\"RHIC-MRSC-001\",\"quantity\":1},"
						+ "{\"code\":\"RHIC-PHYS-005\",\"quantity\":3}]"));
		verify(repository).save(mri);
		verify(repository).save(physio);
		verify(repository, never()).save(eyeAgain);
		verify(repository).updateRequestResult(eq(10), eq("PENDING"), eq("APR-1"), eq("REC-1"), eq(new BigDecimal("2")),
				any(), any(), any(), eq(200), any());
	}

	@Test
	public void submit_shouldChangeTheQuantityOfAProcedureAlreadyOnTheApproval() throws Exception {
		RhipApprovalRequestLogEntry held = mmiLine(10, "RHIC-OPTH-072", "1", "APPROVED");
		held.setApprovalCode("APR-1");
		when(repository.getActiveByVisitReference("REC-1")).thenReturn(new ArrayList<>(Collections.singletonList(held)));
		when(provider.requestApproval(any())).thenReturn(answer(200,
				"{\"success\":true,\"data\":{\"approvalCode\":\"APR-1\",\"status\":\"edited\"}}"));

		RhipApprovalRequestService.Result result = requestService.submit(mmiLine(null, "RHIC-OPTH-072", "2", null));

		assertThat(result.isSuccess(), equalTo(true));
		assertThat(result.isEdited(), equalTo(true));
		assertThat(MAPPER.writeValueAsString(sentPayload().get("procedures")),
				equalTo("[{\"code\":\"RHIC-OPTH-072\",\"quantity\":2}]"));
		verify(repository, never()).save(any());
		verify(repository).updateRequestResult(eq(10), eq("PENDING"), eq("APR-1"), eq("REC-1"), eq(new BigDecimal("2")),
				any(), any(), any(), eq(200), any());
	}

	@Test
	public void submit_shouldKeepTheHeldLinesWhenMmiRefusesTheRequest() throws Exception {
		RhipApprovalRequestLogEntry held = mmiLine(10, "RHIC-OPTH-072", "1", "PENDING");
		when(repository.getActiveByVisitReference("REC-1")).thenReturn(new ArrayList<>(Collections.singletonList(held)));
		when(provider.requestApproval(any())).thenReturn(answer(400,
				"{\"success\":false,\"message\":\"Some procedures could not be requested; see details.\","
						+ "\"errors\":[{\"productCode\":\"RHIC-ANES-001\",\"reason\":\"Unknown procedure\"}],\"data\":null}"));

		RhipApprovalRequestLogEntry line = mmiLine(null, "RHIC-ANES-001", "1", null);
		RhipApprovalRequestService.Result result = requestService.submit(line);

		assertThat(result.isSuccess(), equalTo(false));
		assertThat(result.getMessage(), containsString("RHIC-ANES-001: Unknown procedure"));
		assertThat(line.getApprovalStatus(), equalTo("FAILED"));
		verify(repository).save(line);
		verify(repository, never()).updateRequestResult(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
	}

	@Test
	public void resend_shouldNotMarkALineMmiHoldsAsFailedWhenTheResendIsRefused() throws Exception {
		RhipApprovalRequestLogEntry held = mmiLine(10, "RHIC-OPTH-072", "1", "PENDING");
		when(repository.getById(10)).thenReturn(held);
		when(repository.getActiveByVisitReference("REC-1")).thenReturn(new ArrayList<>(Collections.singletonList(held)));
		when(provider.requestApproval(any())).thenReturn(answer(503, null));

		RhipApprovalRequestService.Result result = requestService.resend(10);

		assertThat(result.isSuccess(), equalTo(false));
		verify(repository, never()).updateRequestResult(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
	}

	@Test
	public void resend_shouldSendAFailedLineWithTheReceptionsOtherProcedures() throws Exception {
		RhipApprovalRequestLogEntry held = mmiLine(10, "RHIC-OPTH-072", "1", "APPROVED");
		RhipApprovalRequestLogEntry failed = mmiLine(11, "RHIC-ANES-001", "1", "FAILED");
		when(repository.getById(11)).thenReturn(failed);
		when(repository.getActiveByVisitReference("REC-1")).thenReturn(new ArrayList<>(Collections.singletonList(held)));
		when(provider.requestApproval(any())).thenReturn(answer(200,
				"{\"success\":true,\"data\":{\"approvalCode\":\"APR-1\",\"status\":\"edited\"}}"));

		assertThat(requestService.resend(11).isSuccess(), equalTo(true));

		assertThat(MAPPER.writeValueAsString(sentPayload().get("procedures")),
				equalTo("[{\"code\":\"RHIC-OPTH-072\",\"quantity\":1},{\"code\":\"RHIC-ANES-001\",\"quantity\":1}]"));
		verify(repository).updateRequestResult(eq(11), eq("PENDING"), eq("APR-1"), eq("REC-1"), any(), any(), any(),
				any(), any(), any());
		verify(repository).updateRequestResult(eq(10), eq("APPROVED"), eq("APR-1"), eq("REC-1"), any(), any(), any(),
				any(), any(), any());
	}

	@Test
	public void submit_shouldUseTheUnifiedFieldNamesForRama() throws Exception {
		when(provider.requestApproval(any())).thenReturn(answer(200,
				"{\"success\":true,\"data\":{\"approvalCode\":\"RAMA-9\",\"status\":\"created\"}}"));
		RhipApprovalRequestLogEntry line = mmiLine(null, "RHIC-1", "1", null);
		line.setInsuranceType("rama");
		line.setVisitReferenceNumber(null);
		line.setPatientIdentifier("1199");
		line.setDiagnosisIds("9B10.0");

		requestService.submit(line);

		Map<String, Object> payload = sentPayload();
		assertThat(payload.get("patientIdentifier"), equalTo("1199"));
		assertThat(payload.get("clinicalKnowledge"), equalTo("Needs a specialist"));
		assertThat(payload.get("diagnosisIds"), equalTo(Collections.singletonList("9B10.0")));
		assertThat(payload, not(hasKey("visitReferenceNumber")));
		assertThat(MAPPER.writeValueAsString(payload.get("procedures")), equalTo("[{\"code\":\"RHIC-1\",\"quantity\":1}]"));
	}

	// Status checks

	@Test
	public void updateApprovalStatus_shouldRecordMmisDecisionOnEachLine() throws Exception {
		RhipApprovalRequestLogEntry cataract = mmiLine(10, "RHIC-OPTH-072", "2", "PENDING");
		RhipApprovalRequestLogEntry anaesthesia = mmiLine(11, "RHIC-ANES-001", "1", "PENDING");
		RhipApprovalRequestLogEntry neverHeld = mmiLine(12, "RHIC-OTHER", "1", "FAILED");
		for (RhipApprovalRequestLogEntry line : Arrays.asList(cataract, anaesthesia, neverHeld)) {
			line.setApprovalCode("APR-1");
		}
		when(repository.getByApprovalCode("APR-1")).thenReturn(Arrays.asList(cataract, anaesthesia, neverHeld));
		when(provider.checkApprovalStatus(any(), eq("APR-1"), any(), any())).thenReturn(answer(200,
				"{\"success\":true,\"data\":{\"approvalCode\":\"APR-1\",\"status\":\"approved\",\"decidedBy\":\"MMI Medical Officer\","
						+ "\"decidedAt\":\"2026-11-24\",\"procedures\":["
						+ "{\"code\":\"RHIC-OPTH-072\",\"requestedQuantity\":2,\"approvedQuantity\":1,\"approvedUnitPrice\":250000,"
						+ "\"status\":\"approved\",\"reason\":\"One eye only\",\"billed\":false},"
						+ "{\"code\":\"RHIC-ANES-001\",\"requestedQuantity\":1,\"approvedQuantity\":0,\"status\":\"rejected\","
						+ "\"reason\":\"Not needed\",\"billed\":false}]}}"));

		RhipApprovalStatusService.ApprovalStatusUpdate update = statusService.updateApprovalStatus(cataract, true);

		assertThat(update.getNewStatus(), equalTo("PARTIALLY_APPROVED"));
		verify(repository).updateDecision(eq(10), eq("PARTIALLY_APPROVED"), eq(BigDecimal.ONE), eq(new BigDecimal("250000")),
				eq("One eye only"), eq(false), eq("MMI Medical Officer"), eq("2026-11-24"), any(), any(), eq(200), any(), eq(true));
		verify(repository).updateDecision(eq(11), eq("REJECTED"), eq(BigDecimal.ZERO), isNull(), eq("Not needed"), eq(false),
				any(), any(), any(), any(), eq(200), any(), eq(true));
		verify(repository, never()).updateDecision(eq(12), any(), any(), any(), any(), any(), any(), any(), any(), any(),
				any(), any(), anyBoolean());
	}

	@Test
	public void updateApprovalStatus_shouldStopCheckingForTheTimeRhipAsksAfterA429() throws Exception {
		RhipApprovalRequestLogEntry line = mmiLine(10, "RHIC-OPTH-072", "1", "PENDING");
		line.setApprovalCode("APR-1");
		IntegrationResponse tooFast = answer(429, "{\"success\":false,\"message\":\"Too many requests\"}");
		tooFast.setRetryAfter("120");
		when(provider.checkApprovalStatus(any(), anyString(), any(), any())).thenReturn(tooFast);
		when(repository.getByApprovalCode("APR-1")).thenReturn(Collections.singletonList(line));

		statusService.updateApprovalStatus(line, true);

		assertThat(RhipApprovalStatusService.isPaused(), equalTo(true));
		verify(repository).updateStatusCheckFailure(eq(10), eq("Too many requests"), eq(429), eq(true));
		when(repository.getPendingApprovalsRequestedSince(any(), any(Integer.class))).thenReturn(Collections.singletonList(line));
		assertThat(statusService.updatePendingApprovalStatuses(25), equalTo(0));
	}

	@Test
	public void isDue_shouldCheckEveryFiveMinutesInTheFirstHourThenEveryThirtyMinutes() {
		long now = System.currentTimeMillis();
		RhipApprovalRequestLogEntry recent = mmiLine(10, "RHIC-1", "1", "PENDING");
		recent.setDateCreated(new Date(now - 20 * 60 * 1000));
		recent.setLastStatusCheck(new Date(now - 6 * 60 * 1000));
		assertThat(RhipApprovalStatusService.isDue(recent, now), equalTo(true));

		RhipApprovalRequestLogEntry older = mmiLine(11, "RHIC-1", "1", "PENDING");
		older.setDateCreated(new Date(now - 5 * 60 * 60 * 1000));
		older.setLastStatusCheck(new Date(now - 6 * 60 * 1000));
		assertThat(RhipApprovalStatusService.isDue(older, now), equalTo(false));
		older.setLastStatusCheck(new Date(now - 31 * 60 * 1000));
		assertThat(RhipApprovalStatusService.isDue(older, now), equalTo(true));
	}

	private RhipApprovalRequestLogEntry mmiLine(Integer id, String code, String quantity, String status) {
		RhipApprovalRequestLogEntry line = new RhipApprovalRequestLogEntry();
		line.setId(id);
		line.setPatientId(7);
		line.setInsuranceType("mmi");
		line.setFacilityFosaId("3");
		line.setVisitReferenceNumber("REC-1");
		line.setProductCode(code);
		line.setRequestedQuantity(new BigDecimal(quantity));
		line.setPractitionerLicenseNumber("RW-MD-1");
		line.setClinicalKnowledge("Needs a specialist");
		line.setApprovalStatus(status);
		line.setDateCreated(new Date());
		return line;
	}

	private IntegrationResponse answer(int code, String body) throws Exception {
		IntegrationResponse response = new IntegrationResponse();
		response.setEnabled(true);
		response.setEndpointAccessible(true);
		response.setResponseCode(code);
		if (body != null) {
			response.setResponseEntity(MAPPER.readTree(body));
		}
		return response;
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> sentPayload() {
		ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
		verify(provider).requestApproval(captor.capture());
		return captor.getValue();
	}
}
