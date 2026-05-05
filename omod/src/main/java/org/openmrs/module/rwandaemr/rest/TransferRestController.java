package org.openmrs.module.rwandaemr.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.openmrs.module.rwandaemr.integration.HttpUtils;
import org.openmrs.module.rwandaemr.integration.IntegrationConfig;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.module.webservices.rest.web.response.IllegalRequestException;
import org.openmrs.module.webservices.rest.web.response.ResponseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API endpoint for patient transfer information.
 */
@Controller
public class TransferRestController {

    protected final Log log = LogFactory.getLog(getClass());

    @Autowired
    IntegrationConfig integrationConfig;

    @RequestMapping(value = "/rest/v1/rwandaemr/transfer", method = RequestMethod.GET)
    @ResponseBody
    public Object getTransfer(HttpServletRequest request, HttpServletResponse response) throws ResponseException {
        String upid = request.getParameter("upid");

        if (upid == null || upid.trim().isEmpty()) {
            log.warn("Transfer REST API called without UPID parameter");
            throw new IllegalRequestException(
                    "UPID parameter is required. Usage: /rest/v1/rwandaemr/transfer?upid=<patient-upid>[&activeOnly=true]");
        }

        upid = upid.trim();
        String transferId = request.getParameter("transferId");
        if (transferId != null) {
            transferId = transferId.trim();
            if (transferId.isEmpty()) {
                transferId = null;
            }
        }

        String activeOnlyParam = request.getParameter("activeOnly");
        boolean activeOnly = false;
        if (activeOnlyParam != null && !activeOnlyParam.trim().isEmpty()) {
            activeOnly = Boolean.parseBoolean(activeOnlyParam.trim());
        }

        String fromDate = null;
        String endDate = null;
        if (activeOnly) {
            LocalDate firstDayOfMonth = LocalDate.now().withDayOfMonth(1);
            fromDate = firstDayOfMonth.format(DateTimeFormatter.ISO_LOCAL_DATE);
            endDate = LocalDate.now().plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
            log.info("activeOnly is true, setting fromDate to first day of current month: " + fromDate + ", endDate to next day: " + endDate);
        }

        log.info("Transfer REST API endpoint called for UPID: " + upid + ", activeOnly: " + activeOnly +
                (fromDate != null ? ", fromDate: " + fromDate + ", endDate: " + endDate : ""));

        SimpleObject transferResponse = new SimpleObject();

        if (!integrationConfig.isHieEnabled()) {
            log.warn("HIE is not enabled, returning sample data");
            transferResponse.put("status", "error");
            transferResponse.put("message", "HIE is not enabled");
            List<SimpleObject> sampleList = new ArrayList<SimpleObject>();
            transferResponse.put("data", sampleList);
            return transferResponse;
        }

        try {
            List<SimpleObject> transferList = fetchTransfersFromHie(upid, fromDate, endDate);
            if (transferId != null) {
                List<SimpleObject> filtered = new ArrayList<SimpleObject>();
                for (SimpleObject item : transferList) {
                    Object idObj = item.get("id");
                    if (idObj != null && transferId.equals(String.valueOf(idObj))) {
                        filtered.add(item);
                        break;
                    }
                }
                transferList = filtered;
            }
            log.info("Successfully retrieved transfer data from HIE for UPID: " + upid);

            transferResponse.put("status", "success");
            transferResponse.put("data", transferList);
            return transferResponse;
        } catch (Exception e) {
            log.error("Error fetching transfers from HIE for UPID: " + upid, e);

            transferResponse.put("status", "error");
            transferResponse.put("message", e.getMessage() != null ? e.getMessage() : "Failed to fetch transfers from HIE");
            transferResponse.put("data", new ArrayList<SimpleObject>());
            return transferResponse;
        }
    }

    private List<SimpleObject> parseTransferResponse(String jsonData) throws Exception {
        if (jsonData == null || jsonData.trim().isEmpty()) {
            log.warn("Empty response from HIE");
            return new ArrayList<SimpleObject>();
        }

        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode = mapper.readTree(jsonData);

        if (!rootNode.has("resourceType") || !"Parameters".equals(rootNode.get("resourceType").asText())) {
            log.warn("Unexpected response format from HIE");
            return new ArrayList<SimpleObject>();
        }

        JsonNode parameters = rootNode.get("parameter");
        if (parameters == null || !parameters.isArray()) {
            log.warn("No parameters found in response");
            return new ArrayList<SimpleObject>();
        }

        JsonNode bundleNode = null;
        for (JsonNode param : parameters) {
            if ("bundle".equals(param.get("name").asText())) {
                bundleNode = param.get("resource");
                break;
            }
        }

        if (bundleNode == null) {
            log.warn("No bundle found in parameters");
            return new ArrayList<SimpleObject>();
        }

        JsonNode entries = bundleNode.get("entry");
        if (entries == null || !entries.isArray() || entries.size() == 0) {
            log.info("No transfer entries found in bundle");
            return new ArrayList<SimpleObject>();
        }

        List<SimpleObject> transferList = new ArrayList<SimpleObject>();

        for (JsonNode entry : entries) {
            JsonNode resource = entry.get("resource");
            if (resource == null) {
                continue;
            }

            SimpleObject transfer = new SimpleObject();
            initializeTransferFormPlaceholders(transfer);

            if (resource.has("id") && !resource.get("id").isNull()) {
                transfer.put("id", resource.get("id").asText());
            } else {
                transfer.put("id", "");
            }

            if (resource.has("status") && !resource.get("status").isNull()) {
                transfer.put("status", resource.get("status").asText());
            } else {
                transfer.put("status", "unknown");
            }

            if (resource.has("subject")) {
                JsonNode subject = resource.get("subject");
                if (subject.has("identifier") && !subject.get("identifier").isNull() &&
                        subject.get("identifier").has("value") && !subject.get("identifier").get("value").isNull()) {
                    transfer.put("subject", subject.get("identifier").get("value").asText());
                } else {
                    transfer.put("subject", "");
                }
            } else {
                transfer.put("subject", "");
            }

            if (resource.has("period") && !resource.get("period").isNull()) {
                JsonNode period = resource.get("period");
                if (period.has("start") && !period.get("start").isNull()) {
                    String startDateTime = period.get("start").asText();
                    if (startDateTime != null && !startDateTime.trim().isEmpty()) {
                        if (startDateTime.contains("T")) {
                            transfer.put("date", startDateTime.substring(0, startDateTime.indexOf("T")));
                        } else if (startDateTime.length() >= 10) {
                            transfer.put("date", startDateTime.substring(0, 10));
                        } else {
                            transfer.put("date", startDateTime);
                        }
                    } else {
                        transfer.put("date", "");
                    }
                } else {
                    transfer.put("date", "");
                }
            } else {
                transfer.put("date", "");
            }

            if (resource.has("hospitalization")) {
                JsonNode hospitalization = resource.get("hospitalization");

                if (hospitalization.has("origin") && !hospitalization.get("origin").isNull() &&
                        hospitalization.get("origin").has("display") && !hospitalization.get("origin").get("display").isNull()) {
                    transfer.put("origin", hospitalization.get("origin").get("display").asText());
                } else {
                    transfer.put("origin", "");
                }

                if (hospitalization.has("destination") && !hospitalization.get("destination").isNull() &&
                        hospitalization.get("destination").has("display") && !hospitalization.get("destination").get("display").isNull()) {
                    transfer.put("destination", hospitalization.get("destination").get("display").asText());
                } else {
                    transfer.put("destination", "");
                }

                if (hospitalization.has("admitSource")) {
                    JsonNode admitSource = hospitalization.get("admitSource");
                    if (admitSource.has("coding") && admitSource.get("coding").isArray() && admitSource.get("coding").size() > 0) {
                        JsonNode firstCoding = admitSource.get("coding").get(0);
                        if (firstCoding.has("display") && !firstCoding.get("display").isNull()) {
                            transfer.put("admitSource", firstCoding.get("display").asText());
                        } else {
                            transfer.put("admitSource", "");
                        }
                    } else {
                        transfer.put("admitSource", "");
                    }
                } else {
                    transfer.put("admitSource", "");
                }
            } else {
                transfer.put("origin", "");
                transfer.put("destination", "");
                transfer.put("admitSource", "");
            }

            mapTransferFormFields(resource, transfer);

            transferList.add(transfer);
        }

        return transferList;
    }

    /**
     * Initializes placeholders aligned with the transfer form so fields can be
     * populated incrementally without breaking clients expecting these keys.
     */
    private void initializeTransferFormPlaceholders(SimpleObject transfer) {
        transfer.put("province", "");
        transfer.put("district", "");
        transfer.put("hospitalName", "");
        transfer.put("referringFacilityName", "");
        transfer.put("referringUnit", "");
        transfer.put("receivingClinicianPhone", "");

        transfer.put("clientName", "");
        transfer.put("serialNumberOrEmrId", "");
        transfer.put("ageDob", "");
        transfer.put("sex", "");
        transfer.put("caregiverName", "");
        transfer.put("telephone", "");
        transfer.put("patientDistrict", "");
        transfer.put("patientSector", "");
        transfer.put("patientCell", "");
        transfer.put("patientVillage", "");

        transfer.put("admissionDatetime", "");
        transfer.put("transferDecisionDatetime", "");
        transfer.put("receivingFacility", "");
        transfer.put("receivingService", "");
        transfer.put("callingTime", "");
        transfer.put("staffContactedAtReceivingFacility", "");
        transfer.put("staffContactPhone", "");

        transfer.put("transferType", "");
        transfer.put("isEmergency", "");
        transfer.put("isNonEmergency", "");
        transfer.put("isFollowUp", "");
        transfer.put("ambulanceCalledTime", "");
        transfer.put("departureTime", "");

        transfer.put("reasonForTransfer", "");
        transfer.put("significantFindings", "");
        transfer.put("clinicalPresentation", "");
        transfer.put("disabilityType", "");

        transfer.put("temperature", "");
        transfer.put("spo2", "");
        transfer.put("respiratoryRate", "");
        transfer.put("pulse", "");
        transfer.put("bloodPressure", "");
        transfer.put("weight", "");
        transfer.put("height", "");
        transfer.put("muac", "");
        transfer.put("laboratory", "");
        transfer.put("others", "");
        transfer.put("diagnosis", "");
        transfer.put("proceduresAndTreatments", "");

        transfer.put("transportType", "");
        transfer.put("isAmbulanceTransport", "");
        transfer.put("otherTransportType", "");
        transfer.put("isNaTransport", "");
        transfer.put("healthInsurance", "");
        transfer.put("isCbhiInsurance", "");
        transfer.put("isRssbInsurance", "");
        transfer.put("isMmiInsurance", "");
        transfer.put("otherInsurance", "");
        transfer.put("isNoInsurance", "");

        transfer.put("referringProviderName", "");
        transfer.put("referringProviderQualification", "");
        transfer.put("formDate", "");
        transfer.put("formTime", "");
        transfer.put("providerPhone", "");
        transfer.put("signatureAndStamp", "");
    }

    private void mapTransferFormFields(JsonNode resource, SimpleObject transfer) {
        JsonNode subject = resource.get("subject");
        if (subject != null) {
            transfer.put("clientName", textOrDefault(subject.get("display"), ""));
            if (subject.has("identifier") && subject.get("identifier").has("value")) {
                transfer.put("serialNumberOrEmrId", textOrDefault(subject.get("identifier").get("value"), ""));
            }
        }

        // Demographics
        transfer.put("clientName", firstNonBlank(
                extractNestedExtensionValue(resource, "http://example.org/fhir/StructureDefinition/patient-demographics", "name"),
                (String) transfer.get("clientName")));
        transfer.put("serialNumberOrEmrId", firstNonBlank(
                extractNestedExtensionValue(resource, "http://example.org/fhir/StructureDefinition/patient-demographics", "serial-number"),
                (String) transfer.get("serialNumberOrEmrId")));
        transfer.put("ageDob", extractNestedExtensionValue(resource, "http://example.org/fhir/StructureDefinition/patient-demographics", "dob"));
        transfer.put("sex", extractNestedExtensionValue(resource, "http://example.org/fhir/StructureDefinition/patient-demographics", "gender"));

        // Caregiver
        transfer.put("caregiverName", extractNestedExtensionValue(resource, "http://example.org/fhir/StructureDefinition/caregiver-info", "name"));
        String caregiverPhone = extractNestedExtensionValue(resource, "http://example.org/fhir/StructureDefinition/caregiver-info", "phone");
        transfer.put("telephone", caregiverPhone);
        transfer.put("providerPhone", caregiverPhone);

        // Address
        transfer.put("province", stripCodePrefix(extractNestedExtensionValue(resource, "http://example.org/fhir/StructureDefinition/patient-address", "province")));
        transfer.put("district", stripCodePrefix(extractNestedExtensionValue(resource, "http://example.org/fhir/StructureDefinition/patient-address", "district")));
        transfer.put("patientDistrict", transfer.get("district"));
        transfer.put("patientSector", stripCodePrefix(extractNestedExtensionValue(resource, "http://example.org/fhir/StructureDefinition/patient-address", "sector")));
        transfer.put("patientCell", stripCodePrefix(extractNestedExtensionValue(resource, "http://example.org/fhir/StructureDefinition/patient-address", "cell")));
        transfer.put("patientVillage", stripCodePrefix(extractNestedExtensionValue(resource, "http://example.org/fhir/StructureDefinition/patient-address", "village")));

        // Timeline and transfer type
        String periodStart = textOrDefault(resource.path("period").path("start"), "");
        String periodEnd = textOrDefault(resource.path("period").path("end"), "");
        transfer.put("admissionDatetime", firstNonBlank(
                extractNestedExtensionValue(resource, "http://example.org/fhir/StructureDefinition/transfer-flags", "admission-date"),
                periodStart));
        transfer.put("transferDecisionDatetime", firstNonBlank(periodEnd, periodStart));
        transfer.put("departureTime", firstNonBlank(
                extractExtensionDateTime(resource, "http://example.org/fhir/StructureDefinition/departure-time"),
                periodEnd));
        transfer.put("ambulanceCalledTime", extractExtensionDateTime(resource, "http://example.org/fhir/StructureDefinition/ambulance-call-time"));
        transfer.put("callingTime", transfer.get("ambulanceCalledTime"));

        String transferType = extractExtensionDisplay(resource, "http://example.org/fhir/StructureDefinition/transfer-type");
        transfer.put("transferType", transferType);
        String transferTypeLower = transferType.toLowerCase();
        transfer.put("isEmergency", String.valueOf(transferTypeLower.contains("emergency") && !transferTypeLower.contains("non")));
        transfer.put("isNonEmergency", String.valueOf(transferTypeLower.contains("non-emergency") || transferTypeLower.contains("non emergency")));
        transfer.put("isFollowUp", String.valueOf(transferTypeLower.contains("follow")));

        // Facilities and services
        transfer.put("receivingService", extractExtensionValue(resource, "http://example.org/fhir/StructureDefinition/receiving-department"));
        transfer.put("referringUnit", extractExtensionValue(resource, "http://example.org/fhir/StructureDefinition/referring-department"));
        transfer.put("reasonForTransfer", firstNonBlank(
                extractNestedExtensionValue(resource, "http://example.org/fhir/StructureDefinition/clinical-presentation", "immediate-condition"),
                textOrDefault(resource.path("reasonCode").path(0).path("text"), "")));
        transfer.put("clinicalPresentation", firstNonBlank(
                extractNestedExtensionValue(resource, "http://example.org/fhir/StructureDefinition/clinical-presentation", "presentation"),
                textOrDefault(resource.path("diagnosis").path(0).path("condition").path("display"), "")));
        transfer.put("diagnosis", textOrDefault(resource.path("diagnosis").path(0).path("condition").path("display"), ""));

        JsonNode hospitalization = resource.get("hospitalization");
        if (hospitalization != null) {
            transfer.put("referringFacilityName", textOrDefault(hospitalization.path("origin").path("display"), ""));
            transfer.put("hospitalName", transfer.get("referringFacilityName"));
            transfer.put("receivingFacility", firstNonBlank(
                    textOrDefault(hospitalization.path("destination").path("display"), ""),
                    textOrDefault(resource.path("location").path(0).path("location").path("display"), "")));
        }

        // Practitioner info
        transfer.put("referringProviderName", textOrDefault(resource.path("participant").path(0).path("individual").path("display"), ""));
        transfer.put("referringProviderQualification", extractNestedExtensionValue(resource, "http://example.org/fhir/StructureDefinition/practitioner-info", "qualification"));

        // Transport
        String transportType = extractExtensionDisplay(resource, "http://example.org/fhir/StructureDefinition/transport-type");
        transfer.put("transportType", transportType);
        String transportTypeLower = transportType.toLowerCase();
        transfer.put("isAmbulanceTransport", String.valueOf(transportTypeLower.contains("ambulance")));
        transfer.put("otherTransportType", transportTypeLower.contains("ambulance") ? "" : transportType);

        // Insurance
        String insurance = extractExtensionDisplay(resource, "http://example.org/fhir/StructureDefinition/insurance-type");
        transfer.put("healthInsurance", insurance);
        String insuranceLower = insurance.toLowerCase();
        transfer.put("isCbhiInsurance", String.valueOf(insuranceLower.contains("cbhi") || insuranceLower.contains("mutuelle")));
        transfer.put("isRssbInsurance", String.valueOf(insuranceLower.contains("rssb")));
        transfer.put("isMmiInsurance", String.valueOf(insuranceLower.contains("mmi")));
        transfer.put("otherInsurance", (insuranceLower.contains("cbhi") || insuranceLower.contains("mutuelle")
                || insuranceLower.contains("rssb") || insuranceLower.contains("mmi")) ? "" : insurance);
        transfer.put("isNoInsurance", String.valueOf(insurance.trim().isEmpty()));

        // Vitals/labs
        transfer.put("laboratory", extractExtensionValue(resource, "http://example.org/fhir/StructureDefinition/lab-results"));
        String vitals = extractExtensionValue(resource, "http://example.org/fhir/StructureDefinition/vital-signs");
        transfer.put("significantFindings", vitals);
        parseVitalSignsIntoTransfer(vitals, transfer);

        // Fallback/override from structured extended-vitals when provided
        String extWeight = extractNestedExtensionValue(resource, "http://example.org/fhir/StructureDefinition/extended-vitals", "weight");
        String extHeight = extractNestedExtensionValue(resource, "http://example.org/fhir/StructureDefinition/extended-vitals", "height");
        if (extWeight != null && extWeight.trim().length() > 0) {
            transfer.put("weight", extWeight.trim());
        }
        if (extHeight != null && extHeight.trim().length() > 0) {
            transfer.put("height", extHeight.trim());
        }
    }

    private String extractExtensionValue(JsonNode resource, String extensionUrl) {
        JsonNode extensions = resource.get("extension");
        if (extensions == null || !extensions.isArray()) {
            return "";
        }
        for (JsonNode ext : extensions) {
            if (extensionUrl.equals(textOrDefault(ext.get("url"), ""))) {
                if (ext.has("valueString")) {
                    return textOrDefault(ext.get("valueString"), "");
                }
                if (ext.has("valueDateTime")) {
                    return textOrDefault(ext.get("valueDateTime"), "");
                }
                if (ext.has("valueCodeableConcept")) {
                    JsonNode cc = ext.get("valueCodeableConcept");
                    String display = textOrDefault(cc.path("coding").path(0).path("display"), "");
                    if (!display.trim().isEmpty()) {
                        return display;
                    }
                    return textOrDefault(cc.path("text"), "");
                }
            }
        }
        return "";
    }

    private String extractExtensionDisplay(JsonNode resource, String extensionUrl) {
        return extractExtensionValue(resource, extensionUrl);
    }

    private String extractExtensionDateTime(JsonNode resource, String extensionUrl) {
        return extractExtensionValue(resource, extensionUrl);
    }

    private String extractNestedExtensionValue(JsonNode resource, String parentUrl, String childUrl) {
        JsonNode extensions = resource.get("extension");
        if (extensions == null || !extensions.isArray()) {
            return "";
        }
        for (JsonNode ext : extensions) {
            if (parentUrl.equals(textOrDefault(ext.get("url"), "")) && ext.has("extension") && ext.get("extension").isArray()) {
                for (JsonNode nested : ext.get("extension")) {
                    if (childUrl.equals(textOrDefault(nested.get("url"), "")) && nested.has("valueString")) {
                        return textOrDefault(nested.get("valueString"), "");
                    }
                }
            }
        }
        return "";
    }

    private String stripCodePrefix(String value) {
        if (value == null) {
            return "";
        }
        int idx = value.indexOf('#');
        return idx >= 0 && idx + 1 < value.length() ? value.substring(idx + 1).trim() : value.trim();
    }

    private String textOrDefault(JsonNode node, String defaultValue) {
        if (node == null || node.isNull()) {
            return defaultValue;
        }
        String value = node.asText();
        return value == null ? defaultValue : sanitizeFrontendText(value);
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && first.trim().length() > 0) {
            return first;
        }
        return second == null ? "" : second;
    }

    /**
     * Normalizes HIE text values for frontend rendering by removing HTML tags and
     * common attribute remnants, then collapsing whitespace.
     */
    private String sanitizeFrontendText(String input) {
        if (input == null) {
            return "";
        }
        String text = input;

        // Decode a few common entities first so tags can be removed consistently.
        text = text.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&amp;", "&")
                .replace("&nbsp;", " ");

        // Remove HTML tags.
        text = text.replaceAll("(?is)<[^>]*>", " ");

        // Remove leftover HTML-like attribute fragments such as class="...">.
        text = text.replaceAll("(?i)\\b[a-zA-Z_:][-a-zA-Z0-9_:.]*\\s*=\\s*\"[^\"]*\"\\s*>?", " ");
        text = text.replaceAll("(?i)\\b[a-zA-Z_:][-a-zA-Z0-9_:.]*\\s*=\\s*'[^']*'\\s*>?", " ");

        // Normalize spaces.
        text = text.replaceAll("\\s+", " ").trim();
        text = normalizeIsoDateTimeForFrontend(text);
        return text;
    }

    /**
     * Converts ISO datetime values from HIE to frontend-friendly format:
     * - removes timezone suffix (+02:00 or Z)
     * - replaces "T" with a space
     * Non-datetime strings are returned unchanged.
     */
    private String normalizeIsoDateTimeForFrontend(String value) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        if (text.matches("^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:[+-][0-9]{2}:[0-9]{2}|Z)?$")) {
            text = text.replaceAll("([T ][0-9]{2}:[0-9]{2}:[0-9]{2})(?:[+-][0-9]{2}:[0-9]{2}|Z)$", "$1");
            text = text.replace("T", " ");
        }
        return text;
    }

    private void parseVitalSignsIntoTransfer(String vitals, SimpleObject transfer) {
        if (vitals == null || vitals.trim().isEmpty()) {
            return;
        }
        String[] segments = vitals.split(",");
        for (String rawSegment : segments) {
            if (rawSegment == null) {
                continue;
            }
            String segment = rawSegment.trim();
            int idx = segment.indexOf(':');
            if (idx <= 0) {
                continue;
            }

            String key = segment.substring(0, idx).trim().toLowerCase();
            String value = segment.substring(idx + 1).trim();
            if (value.endsWith("%")) {
                value = value.substring(0, value.length() - 1).trim();
            }

            if ("t".equals(key) || "temp".equals(key) || "temperature".equals(key)) {
                transfer.put("temperature", value);
            } else if ("spo2".equals(key) || "sp02".equals(key) || "o2sat".equals(key)) {
                transfer.put("spo2", value);
            } else if ("rr".equals(key) || "respiratory rate".equals(key) || "respiratoryrate".equals(key)) {
                transfer.put("respiratoryRate", value);
            } else if ("pulse".equals(key) || "pr".equals(key) || "heart rate".equals(key) || "heartrate".equals(key)) {
                transfer.put("pulse", value);
            } else if ("bp".equals(key) || "blood pressure".equals(key) || "bloodpressure".equals(key)) {
                transfer.put("bloodPressure", value);
            } else if ("weight".equals(key)) {
                transfer.put("weight", value);
            } else if ("height".equals(key)) {
                transfer.put("height", value);
            } else if ("muac".equals(key)) {
                transfer.put("muac", value);
            }
        }
    }

    private List<SimpleObject> fetchTransfersFromHie(String upid, String fromDate, String endDate) throws Exception {
        try (CloseableHttpClient httpClient = HttpUtils.getHieClient()) {
            if (httpClient == null) {
                throw new IllegalStateException("HIE client could not be created. Check HIE credentials configuration.");
            }

            String url;
            if (fromDate != null && endDate != null) {
                url = integrationConfig.getHieEndpointUrl("/shr/Encounter/$list-transfers", "patient", upid, "fromDate", fromDate, "endDate", endDate);
            } else {
                url = integrationConfig.getHieEndpointUrl("/shr/Encounter/$list-transfers", "patient", upid);
            }

            log.info("Requesting transfers from HIE: " + url);
            HttpGet httpGet = new HttpGet(url);

            try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                int statusCode = response.getStatusLine().getStatusCode();
                HttpEntity httpEntity = response.getEntity();
                String data = "";
                try {
                    data = EntityUtils.toString(httpEntity);
                } catch (Exception e) {
                    log.warn("Could not read response body: " + e.getMessage());
                }

                log.info("HIE transfer request response status: " + statusCode);

                if (data != null && !data.trim().isEmpty()) {
                    try {
                        if (data.contains("\"resourceType\":\"OperationOutcome\"")) {
                            String errorMessage = "HIE returned OperationOutcome error";
                            if (data.length() < 500) {
                                errorMessage += ". Response: " + data;
                            }
                            throw new IllegalStateException(errorMessage);
                        }

                        if (statusCode != 200) {
                            String errorMessage = "HIE responded with status " + statusCode;
                            if (data.length() < 500) {
                                errorMessage += ". Response: " + data;
                            }
                            throw new IllegalStateException(errorMessage);
                        }
                    } catch (IllegalStateException ise) {
                        throw ise;
                    } catch (Exception parseException) {
                        log.debug("Failed to parse response: " + parseException.getMessage());
                    }
                }

                return parseTransferResponse(data);
            }
        }
    }
}
