package org.openmrs.module.rwandaemr.rest;

import lombok.Data;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.module.rwandaemr.labnotification.LabNotificationResult;
import org.openmrs.module.rwandaemr.labnotification.LabNotificationService;
import org.openmrs.module.webservices.rest.web.response.ResponseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.LinkedHashMap;
import java.util.Map;

@Controller
public class LabNotificationRestController {

    protected final Log log = LogFactory.getLog(getClass());

    @Autowired
    private LabNotificationService labNotificationService;

    @RequestMapping(value = "/rest/v1/rwandaemr/lab/notify", method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> notifyPatient(@RequestBody LabNotificationRequest request) throws ResponseException {
        try {
            LabNotificationResult result = labNotificationService.notifyPatient(request.getOrderUuid(),
                    request.getPhoneNumber(), request.getMessage(), Boolean.TRUE.equals(request.getForce()));
            if (!result.isSuccess()) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(result);
            }
            return ResponseEntity.ok(result);
        }
        catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(error(e.getMessage()));
        }
        catch (IllegalStateException e) {
            HttpStatus status = isConfigurationError(e) ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.CONFLICT;
            return ResponseEntity.status(status).body(error(e.getMessage()));
        }
        catch (Exception e) {
            log.error("Error notifying patient of lab results", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error("Error notifying patient of lab results"));
        }
    }

    @RequestMapping(value = "/rest/v1/rwandaemr/lab/notify/dlr", method = RequestMethod.POST,
            produces = MediaType.TEXT_PLAIN_VALUE)
    @ResponseBody
    public String receiveDeliveryReport(@RequestParam(value = "id", required = false) String id,
                                        @RequestParam(value = "message_status", required = false) String messageStatus,
                                        @RequestParam(value = "level", required = false) String level) {
        try {
            labNotificationService.recordDeliveryReport(id, messageStatus, level);
        }
        catch (Exception e) {
            log.warn("Error processing lab notification delivery report", e);
        }
        return "ACK/Jasmin";
    }

    private boolean isConfigurationError(IllegalStateException e) {
        String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        return message.contains("not enabled") || message.contains("not fully configured");
    }

    private Map<String, Object> error(String message) {
        Map<String, Object> ret = new LinkedHashMap<>();
        ret.put("success", false);
        ret.put("message", message);
        return ret;
    }

    @Data
    public static class LabNotificationRequest {
        private String orderUuid;
        private String phoneNumber;
        private String message;
        private Boolean force;
    }
}
