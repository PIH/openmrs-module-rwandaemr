package org.openmrs.module.rwandaemr.rest;

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
import org.openmrs.module.rwandaemr.integration.insurance.MmiPatientReceptionLogEntry;
import org.openmrs.module.rwandaemr.integration.insurance.MmiPatientReceptionLogRepository;
import org.openmrs.module.rwandaemr.integration.insurance.MmiOtpVerificationResponse;
import org.openmrs.module.rwandaemr.integration.insurance.MmiReceptionData;
import org.openmrs.module.rwandaemr.integration.insurance.MmiReceptionResponse;
import org.openmrs.module.rwandaemr.integration.insurance.MmiReceptionStore;
import org.openmrs.module.rwandaemr.session.MmiOtpSessionStore;
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
    MmiPatientReceptionLogRepository mmiPatientReceptionLogRepository;

    @RequestMapping(value = "/rest/v1/rwandaemr/insurance/eligibility", method = RequestMethod.GET)
    @ResponseBody
    public Object checkInsuranceEligibility(HttpServletRequest request, HttpServletResponse response) throws ResponseException {
        String type = request.getParameter("type");
        String identifier = request.getParameter("identifier");
        String fosaid = request.getParameter("fosaid");
        if (StringUtils.isBlank(fosaid)) {
            fosaid = integrationConfig.getFosaId(Context.getUserContext().getLocation());
        }
        boolean sendOtp = Boolean.parseBoolean(request.getParameter("sendOTP"));
        boolean isMainInsurer = Boolean.parseBoolean(request.getParameter("isMainInsurer"));
        IntegrationResponse ret = insuranceEligibilityProvider.checkEligibility(type, identifier, fosaid, sendOtp,
                isMainInsurer);
        return ret;
    }

    @RequestMapping(value = "/rest/v1/rwandaemr/insurance/eligibility/verify-otp", method = RequestMethod.POST)
    @ResponseBody
    public Object verifyInsuranceOtp(@RequestBody OtpVerificationRequest requestBody,
                                     HttpServletRequest request) throws ResponseException {
        String type = requestBody.getInsuranceType();
        String identifier = requestBody.getIdentifier();
        String otpCode = requestBody.getOtpCode();
        String fosaid = requestBody.getFosaid();
        log.info("MMI OTP verify requested for identifier=" + identifier);
        if (StringUtils.isBlank(fosaid)) {
            fosaid = integrationConfig.getFosaId(Context.getUserContext().getLocation());
        }
        IntegrationResponse ret = insuranceEligibilityProvider.verifyOtp(type, identifier, otpCode, fosaid);
        if (ret != null && ret.getResponseEntity() instanceof MmiOtpVerificationResponse) {
            MmiOtpVerificationResponse responseEntity = (MmiOtpVerificationResponse) ret.getResponseEntity();
            log.info("MMI OTP verify result success=" + responseEntity.isSuccess());
            if (responseEntity.isSuccess()) {
                MmiOtpSessionStore.rememberOtp(request, requestBody.getPatientId(), identifier, otpCode);
            }
        } else if (ret != null && StringUtils.isNotBlank(ret.getErrorMessage())) {
            log.warn("MMI OTP verify error=" + ret.getErrorMessage());
        }
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
            if (StringUtils.isBlank(fosaid)) {
                fosaid = integrationConfig.getFosaId(Context.getUserContext().getLocation());
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
            String receptionNumber = getReceptionNumber(receptionResponse);
            if (StringUtils.isNotBlank(receptionNumber) && patientId != null) {
                mmiReceptionStore.storeReceptionNumber(Context.getAuthenticatedUser().getUserId(), patientId, receptionNumber);
            }
            boolean success = receptionResponse.getResponseEntity() instanceof MmiReceptionResponse &&
                    ((MmiReceptionResponse) receptionResponse.getResponseEntity()).isSuccess();
            persistMmiReceptionLog(patientId, identifier, fosaid, patientType, receptionResponse, success);
            response.put("receptionNumber", receptionNumber);
            response.put("success", success);
            response.put("message", receptionResponse.getResponseEntity() instanceof MmiReceptionResponse ?
                    ((MmiReceptionResponse) receptionResponse.getResponseEntity()).getMessage() :
                    "Reception response missing");
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

    @RequestMapping(value = "/rest/v1/rwandaemr/insurance/eligibility/mmi/patient-types", method = RequestMethod.GET)
    @ResponseBody
    public Object getMmiPatientTypes(HttpServletRequest request) throws ResponseException {
        String fosaid = request.getParameter("fosaid");
        if (StringUtils.isBlank(fosaid)) {
            fosaid = integrationConfig.getFosaId(Context.getUserContext().getLocation());
        }
        IntegrationResponse response = insuranceEligibilityProvider.getPatientTypes("mmi", fosaid);
        return response;
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
                                        IntegrationResponse receptionResponse, boolean success) {
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
            entry.setErrorMessage(resolveReceptionErrorMessage(receptionResponse, responseEntity));
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

    public static class OtpVerificationRequest {

        private String insuranceType;
        private String identifier;
        private String otpCode;
        private String fosaid;
        private Integer patientId;
        private String patientType;

        public String getInsuranceType() {
            return insuranceType;
        }

        public void setInsuranceType(String insuranceType) {
            this.insuranceType = insuranceType;
        }

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
