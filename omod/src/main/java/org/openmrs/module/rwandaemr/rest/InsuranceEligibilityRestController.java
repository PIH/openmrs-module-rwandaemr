package org.openmrs.module.rwandaemr.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Patient;
import org.openmrs.Visit;
import org.openmrs.api.context.Context;
import org.openmrs.module.rwandaemr.integration.IntegrationConfig;
import org.openmrs.module.rwandaemr.integration.IntegrationResponse;
import org.openmrs.module.rwandaemr.integration.insurance.InsuranceEligibilityProvider;
import org.openmrs.module.rwandaemr.integration.insurance.InsuranceIntegrationConfig;
import org.openmrs.module.rwandaemr.integration.insurance.MmiPatientReceptionLogEntry;
import org.openmrs.module.rwandaemr.integration.insurance.MmiPatientReceptionLogRepository;
import org.openmrs.module.rwandaemr.integration.insurance.MmiPendingReception;
import org.openmrs.module.rwandaemr.integration.insurance.MmiReceptionData;
import org.openmrs.module.rwandaemr.integration.insurance.MmiReceptionResponse;
import org.openmrs.module.rwandaemr.integration.insurance.MmiReceptionStore;
import org.openmrs.module.webservices.rest.web.response.ResponseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class InsuranceEligibilityRestController {

    protected final Log log = LogFactory.getLog(getClass());

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    InsuranceEligibilityProvider insuranceEligibilityProvider;

    @Autowired
    IntegrationConfig integrationConfig;

    @Autowired
    MmiReceptionStore mmiReceptionStore;

    @Autowired
    InsuranceIntegrationConfig insuranceIntegrationConfig;

    private static final String MISSING_FOSA_ID_MESSAGE = "The facility FOSA ID for RHIP is not set. Ask the system "
            + "administrator to set the global property " + InsuranceIntegrationConfig.FACILITY_FOSA_ID + ".";

    @Autowired
    MmiPatientReceptionLogRepository mmiPatientReceptionLogRepository;

    @RequestMapping(value = "/rest/v1/rwandaemr/insurance/eligibility", method = RequestMethod.GET)
    @ResponseBody
    public Object checkInsuranceEligibility(HttpServletRequest request, HttpServletResponse response) throws ResponseException {
        String type = request.getParameter("type");
        String identifier = request.getParameter("identifier");
        String fosaid = rhipFosaId();
        if (fosaid == null) {
            return missingFosaIdResponse();
        }
        boolean sendOtp = Boolean.parseBoolean(request.getParameter("sendOTP"));
        boolean isMainInsurer = Boolean.parseBoolean(request.getParameter("isMainInsurer"));
        String otpTo = request.getParameter("otpTo");
        IntegrationResponse ret = insuranceEligibilityProvider.checkEligibility(type, identifier, fosaid, sendOtp,
                isMainInsurer, otpTo);
        return ret;
    }

    @RequestMapping(value = "/rest/v1/rwandaemr/insurance/eligibility/mmi/reception", method = RequestMethod.POST)
    @ResponseBody
    public Object createMmiReception(@RequestBody MmiReceptionRequest requestBody) throws ResponseException {
        String identifier = requestBody.getIdentifier();
        String otpCode = requestBody.getOtpCode();
        String fosaid = requestBody.getFosaid();
        Integer patientId = requestBody.getPatientId();
        String patientType = requestBody.getPatientType();
        log.info("MMI reception requested for identifier=" + identifier + ", patientId=" + patientId
                + ", patientType=" + patientType);
        Map<String, Object> response = new LinkedHashMap<>();
        try {
            fosaid = rhipFosaId();
            if (fosaid == null) {
                response.put("success", false);
                response.put("message", MISSING_FOSA_ID_MESSAGE);
                return response;
            }

            if (StringUtils.isBlank(patientType)) {
                log.warn("MMI reception failed: patient type is required");
                response.put("success", false);
                response.put("message", "Patient type is required");
                return response;
            }
            if (!StringUtils.isNumeric(patientType.trim())) {
                log.warn("MMI reception failed: patient type must be numeric");
                response.put("success", false);
                response.put("message", "Patient type must be numeric");
                return response;
            }
            if (StringUtils.isBlank(otpCode)) {
                log.warn("MMI reception failed: OTP code is required");
                response.put("success", false);
                response.put("message", "OTP code is required");
                return response;
            }

            IntegrationResponse receptionResponse = insuranceEligibilityProvider.createReception("MMI", identifier,
                    fosaid, patientType.trim(), otpCode, true);
            response.put("reception", receptionResponse.getResponseEntity());
            boolean success = receptionResponse.getResponseEntity() instanceof MmiReceptionResponse &&
                    ((MmiReceptionResponse) receptionResponse.getResponseEntity()).isSuccess();
            // On failure, data can still carry a reception number: the one MMI says is still open
            String returnedReceptionNumber = getReceptionNumber(receptionResponse);
            String receptionNumber = success ? returnedReceptionNumber : null;
            if (StringUtils.isNotBlank(receptionNumber) && patientId != null) {
                mmiReceptionStore.storeReceptionNumber(Context.getAuthenticatedUser().getUserId(), patientId, receptionNumber);
            }
            String message = receptionResponse.getResponseEntity() instanceof MmiReceptionResponse ?
                    ((MmiReceptionResponse) receptionResponse.getResponseEntity()).getMessage() :
                    StringUtils.defaultIfEmpty(receptionResponse.getErrorMessage(), "Reception response missing");
            if (MmiPendingReception.isPendingReception(receptionResponse.getResponseCode(), success, returnedReceptionNumber)) {
                message = MmiPendingReception.describe(returnedReceptionNumber,
                        mmiPatientReceptionLogRepository.getSuccessfulReception(returnedReceptionNumber));
                response.put("pendingReceptionNumber", returnedReceptionNumber);
            }
            persistMmiReceptionLog(patientId, identifier, fosaid, patientType, receptionResponse, success, message);
            response.put("receptionNumber", receptionNumber);
            response.put("success", success);
            response.put("message", message);
            if (StringUtils.isNotBlank(receptionResponse.getErrorMessage())) {
                log.warn("MMI reception error=" + receptionResponse.getErrorMessage());
            }
            log.info("MMI reception result success=" + success + ", receptionNumber=" + receptionNumber);
            return response;
        } catch (Exception e) {
            log.error("MMI reception failed with exception", e);
            response.put("success", false);
            response.put("message", "MMI reception failed");
            return response;
        }
    }

    /**
     * Switches the patient's open MMI reception to another patient type (RHIP /patient-reception/change-type),
     * which changes the MMI / patient cost split for the rest of the visit
     */
    @RequestMapping(value = "/rest/v1/rwandaemr/insurance/eligibility/mmi/reception/change-type", method = RequestMethod.POST)
    @ResponseBody
    public Object changeMmiReceptionPatientType(@RequestBody MmiReceptionChangeTypeRequest requestBody) throws ResponseException {
        Map<String, Object> response = new LinkedHashMap<>();
        String receptionNumber = StringUtils.trimToNull(requestBody.getReceptionNumber());
        String patientType = StringUtils.trimToNull(requestBody.getPatientType());
        if (receptionNumber == null) {
            response.put("success", false);
            response.put("message", "MMI reception number is required");
            return response;
        }
        if (patientType == null || !StringUtils.isNumeric(patientType)) {
            response.put("success", false);
            response.put("message", "Choose the new MMI patient type");
            return response;
        }
        try {
            String fosaid = rhipFosaId();
            if (fosaid == null) {
                response.put("success", false);
                response.put("message", MISSING_FOSA_ID_MESSAGE);
                return response;
            }
            log.info("MMI reception type change requested for reception=" + receptionNumber + ", patientType=" + patientType);
            IntegrationResponse changeResponse = insuranceEligibilityProvider.changeReceptionPatientType("MMI", fosaid,
                    receptionNumber, Integer.valueOf(patientType));
            JsonNode body = changeResponse.getResponseEntity() instanceof JsonNode
                    ? (JsonNode) changeResponse.getResponseEntity() : null;
            boolean success = body != null && body.path("success").asBoolean(false);
            if (success) {
                mmiPatientReceptionLogRepository.updatePatientType(receptionNumber, patientType);
                response.put("patientTypeNo", body.path("patientTypeNo").isMissingNode() ? null : body.path("patientTypeNo").asText());
                response.put("visitType", body.path("visitType").isMissingNode() ? null : body.path("visitType").asText());
                response.put("coverage", body.get("coverage"));
            }
            String message = body != null && StringUtils.isNotBlank(body.path("message").asText(null))
                    ? body.path("message").asText()
                    : StringUtils.defaultIfEmpty(changeResponse.getErrorMessage(),
                    success ? "Reception type changed" : "Unable to change the MMI patient type");
            response.put("success", success);
            response.put("message", message);
            log.info("MMI reception type change result success=" + success + ", reception=" + receptionNumber);
            return response;
        }
        catch (Exception e) {
            log.error("MMI reception type change failed with exception", e);
            response.put("success", false);
            response.put("message", "Unable to change the MMI patient type");
            return response;
        }
    }

    @RequestMapping(value = "/rest/v1/rwandaemr/insurance/eligibility/mmi/patient-types", method = RequestMethod.GET)
    @ResponseBody
    public Object getMmiPatientTypes(HttpServletRequest request) throws ResponseException {
        String fosaid = rhipFosaId();
        if (fosaid == null) {
            return missingFosaIdResponse();
        }
        IntegrationResponse response = insuranceEligibilityProvider.getPatientTypes("mmi", fosaid);
        return response;
    }

    /**
     * @return the facility FOSA ID sent to RHIP: always the one global property shared by all RHIP calls
     */
    private String rhipFosaId() {
        return insuranceIntegrationConfig.getFacilityFosaId();
    }

    private IntegrationResponse missingFosaIdResponse() {
        IntegrationResponse ret = new IntegrationResponse();
        ret.setEnabled(true);
        ret.setErrorMessage(MISSING_FOSA_ID_MESSAGE);
        Map<String, Object> entity = new LinkedHashMap<>();
        entity.put("success", false);
        entity.put("message", MISSING_FOSA_ID_MESSAGE);
        ret.setResponseEntity(entity);
        return ret;
    }

    private String getReceptionNumber(IntegrationResponse receptionResponse) {
        if (receptionResponse.getResponseEntity() instanceof MmiReceptionResponse) {
            MmiReceptionResponse response = (MmiReceptionResponse) receptionResponse.getResponseEntity();
            MmiReceptionData data = response.getData();
            return data == null ? null : data.getReceptionNumber();
        }
        return null;
    }

    private void persistMmiReceptionLog(Integer patientId, String identifier, String fosaid, String patientType,
                                        IntegrationResponse receptionResponse, boolean success, String failureMessage) {
        try {
            Patient patient = patientId == null ? null : Context.getPatientService().getPatient(patientId);
            Visit visit = patient == null ? null : getActiveVisit(patient);
            MmiReceptionResponse responseEntity = receptionResponse.getResponseEntity() instanceof MmiReceptionResponse
                    ? (MmiReceptionResponse) receptionResponse.getResponseEntity()
                    : null;
            MmiReceptionData data = responseEntity == null ? null : responseEntity.getData();

            Map<String, Object> requestPayload = new LinkedHashMap<>();
            requestPayload.put("insuranceType", "MMI");
            requestPayload.put("patientIdentifier", identifier);
            requestPayload.put("facilityFosaId", fosaid);
            requestPayload.put("patientType", patientType);
            requestPayload.put("prescriptionRequired", true);

            MmiPatientReceptionLogEntry entry = new MmiPatientReceptionLogEntry();
            entry.setPatientId(patientId);
            entry.setVisitId(visit == null ? null : visit.getVisitId());
            entry.setPatientIdentifier(identifier);
            entry.setFacilityFosaId(fosaid);
            entry.setPatientType(patientType);
            entry.setPrescriptionRequired(true);
            entry.setRequestPayload(OBJECT_MAPPER.writeValueAsString(requestPayload));
            entry.setResponsePayload(responseEntity == null ? null : OBJECT_MAPPER.writeValueAsString(responseEntity));
            entry.setResponseCode(receptionResponse.getResponseCode());
            entry.setStatus(success ? "SUCCESS" : "FAILED");
            entry.setReceptionNumber(data == null ? null : data.getReceptionNumber());
            entry.setBpCode(data == null ? null : data.getBpCode());
            entry.setReceptionStatus(data == null ? null : data.getStatus());
            entry.setErrorMessage(success ? null : StringUtils.defaultIfEmpty(failureMessage,
                    resolveReceptionErrorMessage(receptionResponse, responseEntity)));
            entry.setCreator(Context.getAuthenticatedUser() == null ? null : Context.getAuthenticatedUser().getUserId());
            entry.setDateCreated(new Date());
            mmiPatientReceptionLogRepository.saveLogEntry(entry);
        }
        catch (Exception e) {
            log.warn("Unable to persist MMI reception log", e);
        }
    }

    private Visit getActiveVisit(Patient patient) {
        List<Visit> activeVisits = Context.getVisitService().getActiveVisitsByPatient(patient);
        return activeVisits == null || activeVisits.isEmpty() ? null : activeVisits.get(0);
    }

    private String resolveReceptionErrorMessage(IntegrationResponse response, MmiReceptionResponse responseEntity) {
        if (response == null) {
            return "No response returned from MMI reception endpoint";
        }
		if (StringUtils.isNotBlank(response.getErrorMessage())) {
			return response.getErrorMessage();
		}
		if (responseEntity != null && !responseEntity.isSuccess() && StringUtils.isNotBlank(responseEntity.getMessage())) {
			return responseEntity.getMessage();
		}
		return null;
	}

    public static class MmiReceptionChangeTypeRequest {

        private String receptionNumber;
        private String patientType;

        public String getReceptionNumber() {
            return receptionNumber;
        }

        public void setReceptionNumber(String receptionNumber) {
            this.receptionNumber = receptionNumber;
        }

        public String getPatientType() {
            return patientType;
        }

        public void setPatientType(String patientType) {
            this.patientType = patientType;
        }
    }

    public static class MmiReceptionRequest {

        private String identifier;
        private String otpCode;
        private String fosaid;
        private Integer patientId;
        private String patientType;

        public String getIdentifier() {
            return identifier;
        }

        public void setIdentifier(String identifier) {
            this.identifier = identifier;
        }

        public String getOtpCode() {
            return otpCode;
        }

        public void setOtpCode(String otpCode) {
            this.otpCode = otpCode;
        }

        public String getFosaid() {
            return fosaid;
        }

        public void setFosaid(String fosaid) {
            this.fosaid = fosaid;
        }

        public Integer getPatientId() {
            return patientId;
        }

        public void setPatientId(Integer patientId) {
            this.patientId = patientId;
        }

        public String getPatientType() {
            return patientType;
        }

        public void setPatientType(String patientType) {
            this.patientType = patientType;
        }
    }
}
