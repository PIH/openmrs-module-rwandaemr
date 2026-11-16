package org.openmrs.module.rwandaemr.fragment.controller.patient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.text.SimpleDateFormat;
import java.util.Collections;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;

import org.openmrs.Encounter;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.rwandaemr.integration.HttpUtils;
import org.openmrs.module.emrapi.patient.PatientDomainWrapper;
import org.openmrs.module.rwandaemr.RwandaEmrConfig;
import org.openmrs.module.rwandaemr.integration.IntegrationConfig;
import org.openmrs.module.rwandaemr.integration.ShrEncounter;
import org.openmrs.module.rwandaemr.integration.ShrEncounterProvider;
import org.openmrs.module.rwandaemr.integration.ShrEncounterTranslator;
import org.openmrs.module.rwandaemr.integration.ShrVisit;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.annotation.InjectBeans;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.fragment.FragmentConfiguration;
import org.openmrs.ui.framework.fragment.action.FragmentActionResult;
import org.openmrs.ui.framework.fragment.FragmentModel;
import org.openmrs.ui.framework.fragment.action.ObjectResult;
import org.openmrs.ui.framework.page.PageModel;
import org.springframework.web.bind.annotation.RequestParam;

public class HieEncountersSectionFragmentController {

    protected final Log log = LogFactory.getLog(HieEncountersSectionFragmentController.class);
    private static final SimpleDateFormat ENCOUNTER_DATETIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

	public void controller(FragmentConfiguration config,
    PageModel pageModel,
    FragmentModel model,
    UiUtils ui,
    UiSessionContext sessionContext,
    @InjectBeans PatientDomainWrapper patientWrapper,
    @SpringBean IntegrationConfig integrationConfig,
    @SpringBean RwandaEmrConfig rwandaEmrConfig
    ) {
        config.require("patient");
        Object patient = config.get("patient");

        if(patient instanceof Patient){
            patientWrapper.setPatient((Patient) patient);
            config.addAttribute("patient", patientWrapper);
        } else if(patient instanceof PatientDomainWrapper) {
            patientWrapper = (PatientDomainWrapper) patient;
        }
        model.addAttribute("error", "");
        model.addAttribute("upid", "");
        try {
            if (!integrationConfig.isHieEnabled()) {
                model.addAttribute("error", "Hie is not enabled on this server");
                return;
            }
            PatientIdentifier upidPatientIdentifier = patientWrapper.getPatient().getPatientIdentifier(rwandaEmrConfig.getUPID());
            if (upidPatientIdentifier == null || upidPatientIdentifier.getIdentifier() == null) {
                model.addAttribute("error", "No UPID found");
                return;
            }
            model.addAttribute("upid", upidPatientIdentifier.getIdentifier());
        } catch (Exception e) {
            model.addAttribute("error", "Error preparing HIE history: " + e.getClass().getSimpleName());
        }
    }

    public FragmentActionResult loadPastHistory(
            @RequestParam("upid") String upid,
            @SpringBean IntegrationConfig integrationConfig,
            @SpringBean ShrEncounterProvider shrEncounterProvider,
            @SpringBean ShrEncounterTranslator shrEncounterTranslator
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("message", "");
        result.put("visits", new ArrayList<>());
        try {
            if (!integrationConfig.isHieEnabled()) {
                result.put("message", "Hie is not enabled on this server");
                return new ObjectResult(result);
            }
            if (upid == null || upid.trim().isEmpty()) {
                result.put("message", "No UPID found");
                return new ObjectResult(result);
            }

            List<ShrEncounter> shrEncounters = shrEncounterProvider.fetchEncounterFromShr(upid.trim());
            List<ShrVisit> visits = new ArrayList<>();
            List<String> visitedLocation = new ArrayList<>();
            List<Map<String, Object>> visitPayload = new ArrayList<>();

            if (shrEncounters != null && !shrEncounters.isEmpty()) {
                for (ShrEncounter shrEncounter: shrEncounters) {
                    Encounter myEncounter = shrEncounterTranslator.toEncounter(shrEncounter);
                    String locationName = myEncounter.getLocation() != null ? myEncounter.getLocation().getName() : "Unspecified";
                    int locationIndex = visitedLocation.indexOf(locationName);
                    if (locationIndex == -1) {
                        ShrVisit shrVisit = new ShrVisit();
                        shrVisit.setLocation(locationName);
                        shrVisit.clearEncounters();
                        shrVisit.addEncounter(myEncounter);
                        visits.add(shrVisit);
                        visitedLocation.add(locationName);
                    } else {
                        visits.get(locationIndex).addEncounter(myEncounter);
                    }
                }
            }

            for (ShrVisit visit : visits) {
                Map<String, Object> visitMap = new LinkedHashMap<>();
                visitMap.put("location", visit.getLocation());
                List<Map<String, Object>> encounterPayload = new ArrayList<>();
                if (visit.getEncounters() != null) {
                    for (Encounter e : visit.getEncounters()) {
                        Map<String, Object> encounterMap = new LinkedHashMap<>();
                        encounterMap.put("uuid", e.getUuid());
                        encounterMap.put("encounterDatetime",
                                e.getEncounterDatetime() != null ? ENCOUNTER_DATETIME_FORMAT.format(e.getEncounterDatetime()) : "");
                        encounterMap.put("encounterType", e.getVoidReason());
                        encounterMap.put("location", e.getLocation() != null ? e.getLocation().getName() : "");
                        encounterPayload.add(encounterMap);
                    }
                }
                visitMap.put("encounters", encounterPayload);
                visitPayload.add(visitMap);
            }

            result.put("success", true);
            result.put("visits", visitPayload);
            return new ObjectResult(result);
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            if (errorMsg == null || errorMsg.trim().isEmpty()) {
                errorMsg = "Error retrieving HIE encounters: " + e.getClass().getSimpleName();
            }
            result.put("message", errorMsg);
            return new ObjectResult(result);
        }
    }

    public FragmentActionResult loadIps(
            @RequestParam("upid") String upid,
            @SpringBean IntegrationConfig integrationConfig
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("message", "");
        result.put("tabs", new ArrayList<>());
        result.put("meta", new LinkedHashMap<>());
        try {
            if (!integrationConfig.isHieEnabled()) {
                result.put("message", "Hie is not enabled on this server");
                return new ObjectResult(result);
            }
            if (upid == null || upid.trim().isEmpty()) {
                result.put("message", "No UPID found");
                return new ObjectResult(result);
            }

            String cleanUpid = upid.trim();
            String url = integrationConfig.getHieEndpointUrl("/shr/Bundle/$ips", "patient", cleanUpid, "idType", "UPI");
            if (url == null || url.trim().isEmpty()) {
                result.put("message", "HIE URL is not configured");
                return new ObjectResult(result);
            }

            try (CloseableHttpClient httpClient = HttpUtils.getHieClient()) {
                if (httpClient == null) {
                    result.put("message", "HIE credentials are not configured");
                    return new ObjectResult(result);
                }
                HttpGet httpGet = new HttpGet(url);
                try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                    int statusCode = response.getStatusLine().getStatusCode();
                    HttpEntity entity = response.getEntity();
                    String payload = entity != null ? EntityUtils.toString(entity) : "";
                    if (statusCode != 200) {
                        result.put("message", "HIE IPS request failed with status " + statusCode);
                        return new ObjectResult(result);
                    }
                    return new ObjectResult(parseIpsPayload(payload));
                }
            }
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            if (errorMsg == null || errorMsg.trim().isEmpty()) {
                errorMsg = "Error retrieving IPS: " + e.getClass().getSimpleName();
            }
            result.put("message", errorMsg);
            return new ObjectResult(result);
        }
    }

    private Map<String, Object> parseIpsPayload(String payload) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("message", "");
        List<Map<String, Object>> tabs = new ArrayList<>();
        Map<String, Object> meta = new LinkedHashMap<>();
        result.put("tabs", tabs);
        result.put("meta", meta);

        if (payload == null || payload.trim().isEmpty()) {
            result.put("message", "Empty IPS response");
            return result;
        }

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(payload);
        if (root == null || !"Bundle".equals(text(root.get("resourceType")))) {
            result.put("message", "Unexpected IPS response format");
            return result;
        }

        meta.put("bundleId", text(root.get("id")));
        meta.put("timestamp", text(root.get("timestamp")));

        JsonNode entries = root.get("entry");
        if (entries == null || !entries.isArray()) {
            result.put("success", true);
            return result;
        }

        List<Map<String, Object>> patientItems = new ArrayList<>();
        List<Map<String, Object>> conditionItems = new ArrayList<>();
        List<Map<String, Object>> medicationItems = new ArrayList<>();
        List<Map<String, Object>> allergyItems = new ArrayList<>();
        List<Map<String, Object>> immunizationItems = new ArrayList<>();
        List<Map<String, Object>> procedureItems = new ArrayList<>();
        List<Map<String, Object>> encounterItems = new ArrayList<>();
        List<Map<String, Object>> otherItems = new ArrayList<>();
        List<Map<String, Object>> unlinkedObservationItems = new ArrayList<>();
        Map<String, List<Map<String, Object>>> observationsByEncounterRef = new LinkedHashMap<>();
        Map<String, Map<String, Object>> encounterByRef = new LinkedHashMap<>();

        for (JsonNode entry : entries) {
            JsonNode resource = entry.get("resource");
            if (resource == null || resource.isNull()) {
                continue;
            }
            String type = text(resource.get("resourceType"));
            if ("Patient".equals(type)) {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("name", firstGivenFamily(resource.get("name")));
                p.put("gender", text(resource.get("gender")));
                p.put("birthDate", text(resource.get("birthDate")));
                p.put("upi", identifierBySystem(resource.get("identifier"), "UPI"));
                p.put("nid", identifierBySystem(resource.get("identifier"), "NID"));
                p.put("phone", firstTelecom(resource.get("telecom"), "phone"));
                p.put("address", firstAddress(resource.get("address")));
                patientItems.add(p);
            } else if ("Composition".equals(type)) {
                if (text(root.get("id")).length() > 0) {
                    meta.put("bundleId", text(root.get("id")));
                }
                if (text(root.get("timestamp")).length() > 0) {
                    meta.put("timestamp", text(root.get("timestamp")));
                }
                if (text(resource.get("title")).length() > 0) {
                    meta.put("title", text(resource.get("title")));
                }
                if (text(resource.get("date")).length() > 0) {
                    meta.put("documentDate", text(resource.get("date")));
                }
                if (firstCodingDisplay(resource.get("type")).length() > 0) {
                    meta.put("documentType", firstCodingDisplay(resource.get("type")));
                }
            } else if ("Condition".equals(type)) {
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("name", firstCodingDisplay(resource.get("code")));
                c.put("status", text(resource.get("clinicalStatus")));
                c.put("recordedDate", text(resource.get("recordedDate")));
                conditionItems.add(c);
            } else if ("MedicationRequest".equals(type) || "MedicationStatement".equals(type)) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("resourceType", type);
                m.put("medication", firstCodingDisplay(resource.get("medicationCodeableConcept")));
                m.put("status", text(resource.get("status")));
                medicationItems.add(m);
            } else if ("AllergyIntolerance".equals(type)) {
                Map<String, Object> a = new LinkedHashMap<>();
                a.put("allergy", firstCodingDisplay(resource.get("code")));
                a.put("criticality", text(resource.get("criticality")));
                a.put("clinicalStatus", firstCodingDisplay(resource.get("clinicalStatus")));
                allergyItems.add(a);
            } else if ("Immunization".equals(type)) {
                Map<String, Object> i = new LinkedHashMap<>();
                i.put("vaccine", firstCodingDisplay(resource.get("vaccineCode")));
                i.put("date", text(resource.get("occurrenceDateTime")));
                i.put("status", text(resource.get("status")));
                immunizationItems.add(i);
            } else if ("Procedure".equals(type)) {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("procedure", firstCodingDisplay(resource.get("code")));
                p.put("status", text(resource.get("status")));
                p.put("performed", text(resource.get("performedDateTime")));
                procedureItems.add(p);
            } else if ("Observation".equals(type)) {
                Map<String, Object> o = new LinkedHashMap<>();
                o.put("category", firstCategoryDisplay(resource.get("category")));
                o.put("code", firstCodingDisplay(resource.get("code")));
                o.put("value", firstValue(resource));
                o.put("effective", text(resource.get("effectiveDateTime")));
                String encounterRef = text(resource.path("encounter").get("reference"));
                if (encounterRef.length() > 0) {
                    if (!observationsByEncounterRef.containsKey(encounterRef)) {
                        observationsByEncounterRef.put(encounterRef, new ArrayList<Map<String, Object>>());
                    }
                    observationsByEncounterRef.get(encounterRef).add(o);
                } else {
                    unlinkedObservationItems.add(o);
                }
            } else if ("Encounter".equals(type)) {
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("status", text(resource.get("status")));
                e.put("type", firstArrayCodingDisplay(resource.get("type")));
                e.put("serviceType", firstCodingDisplay(resource.get("serviceType")));
                e.put("periodStart", text(resource.path("period").get("start")));
                e.put("periodEnd", text(resource.path("period").get("end")));
                e.put("location", firstEncounterLocation(resource.get("location")));
                e.put("observations", new ArrayList<Map<String, Object>>());
                encounterItems.add(e);
                String encounterId = text(resource.get("id"));
                if (encounterId.length() > 0) {
                    encounterByRef.put("Encounter/" + encounterId, e);
                }
            } else {
                Map<String, Object> other = new LinkedHashMap<>();
                other.put("resourceType", type);
                otherItems.add(other);
            }
        }

        for (String encounterRef : observationsByEncounterRef.keySet()) {
            Map<String, Object> encounter = encounterByRef.get(encounterRef);
            if (encounter != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> encounterObservations = (List<Map<String, Object>>) encounter.get("observations");
                encounterObservations.addAll(observationsByEncounterRef.get(encounterRef));
            } else {
                unlinkedObservationItems.addAll(observationsByEncounterRef.get(encounterRef));
            }
        }

        tabs.add(tab("Patient", patientItems));
        tabs.add(tab("Conditions", conditionItems));
        tabs.add(tab("Medications", medicationItems));
        tabs.add(tab("Allergies", allergyItems));
        tabs.add(tab("Immunizations", immunizationItems));
        tabs.add(tab("Procedures", procedureItems));
        tabs.add(tab("Encounters", encounterItems));
        if (!unlinkedObservationItems.isEmpty()) {
            tabs.add(tab("Other Observations", unlinkedObservationItems));
        }
        if (!otherItems.isEmpty()) {
            tabs.add(tab("Other", otherItems));
        }

        result.put("success", true);
        return result;
    }

    private Map<String, Object> tab(String title, List<Map<String, Object>> items) {
        Map<String, Object> tab = new LinkedHashMap<>();
        tab.put("title", title);
        tab.put("items", items == null ? Collections.emptyList() : items);
        return tab;
    }

    private String text(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        return node.asText();
    }

    private String firstGivenFamily(JsonNode names) {
        if (names == null || !names.isArray() || names.size() == 0) {
            return "";
        }
        JsonNode first = names.get(0);
        String family = text(first.get("family"));
        JsonNode given = first.get("given");
        String givenName = "";
        if (given != null && given.isArray() && given.size() > 0) {
            givenName = text(given.get(0));
        }
        return (givenName + " " + family).trim();
    }

    private String identifierBySystem(JsonNode identifiers, String system) {
        if (identifiers == null || !identifiers.isArray()) {
            return "";
        }
        for (JsonNode id : identifiers) {
            if (system.equalsIgnoreCase(text(id.get("system")))) {
                return text(id.get("value"));
            }
        }
        return "";
    }

    private String firstTelecom(JsonNode telecom, String type) {
        if (telecom == null || !telecom.isArray()) {
            return "";
        }
        for (JsonNode item : telecom) {
            if (type.equalsIgnoreCase(text(item.get("system")))) {
                return text(item.get("value"));
            }
        }
        return "";
    }

    private String firstAddress(JsonNode address) {
        if (address == null || !address.isArray() || address.size() == 0) {
            return "";
        }
        JsonNode a = address.get(0);
        String line = "";
        JsonNode lines = a.get("line");
        if (lines != null && lines.isArray() && lines.size() > 0) {
            line = text(lines.get(0));
        }
        String city = text(a.get("city"));
        String district = text(a.get("district"));
        String country = text(a.get("country"));
        StringBuilder sb = new StringBuilder();
        if (!line.isEmpty()) sb.append(line);
        if (!city.isEmpty()) sb.append(sb.length() > 0 ? ", " : "").append(city);
        if (!district.isEmpty()) sb.append(sb.length() > 0 ? ", " : "").append(district);
        if (!country.isEmpty()) sb.append(sb.length() > 0 ? ", " : "").append(country);
        return sb.toString();
    }

    private String firstCodingDisplay(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        JsonNode coding = node.get("coding");
        if (coding != null && coding.isArray() && coding.size() > 0) {
            JsonNode c = coding.get(0);
            String display = text(c.get("display"));
            if (!display.isEmpty()) {
                return display;
            }
            return text(c.get("code"));
        }
        return text(node.get("text"));
    }

    private String firstArrayCodingDisplay(JsonNode arrNode) {
        if (arrNode == null || !arrNode.isArray() || arrNode.size() == 0) {
            return "";
        }
        return firstCodingDisplay(arrNode.get(0));
    }

    private String firstCategoryDisplay(JsonNode categoryNode) {
        return firstArrayCodingDisplay(categoryNode);
    }

    private String firstValue(JsonNode resource) {
        String cc = firstCodingDisplay(resource.get("valueCodeableConcept"));
        if (!cc.isEmpty()) {
            return cc;
        }
        String vs = text(resource.get("valueString"));
        if (!vs.isEmpty()) {
            return vs;
        }
        String vn = text(resource.get("valueQuantity"));
        if (!vn.isEmpty()) {
            return vn;
        }
        return "";
    }

    private String firstEncounterLocation(JsonNode locationNode) {
        if (locationNode == null || !locationNode.isArray() || locationNode.size() == 0) {
            return "";
        }
        JsonNode first = locationNode.get(0);
        return text(first.path("location").get("display"));
    }
}
