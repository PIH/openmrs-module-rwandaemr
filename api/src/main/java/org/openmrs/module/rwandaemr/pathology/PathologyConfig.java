package org.openmrs.module.rwandaemr.pathology;

import org.apache.commons.lang.StringUtils;
import org.openmrs.Concept;
import org.openmrs.OrderType;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.ConceptService;
import org.openmrs.api.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Configuration of the pathology lab: the order type pathology tests are ordered with, and the tests that can be ordered
 */
@Component
public class PathologyConfig {

	public static final String PATHOLOGY_ORDER_TYPE_UUID = "fe7aad7e-c224-4f56-878e-7d7309a6d3c3";

	public static final String GP_ORDERABLES = "rwandaemr.pathology.orderables";

	/**
	 * The phone number of the lab, printed on the pathology report
	 */
	public static final String GP_LAB_PHONE_NUMBER = "rwandaemr.pathology.labPhoneNumber";

	/**
	 * The logo printed on the pathology report: an image of this module (such as images/butaro-logo.png) or a URL
	 */
	public static final String GP_REPORT_LOGO = "rwandaemr.pathology.reportLogo";

	/**
	 * The pathology request form, opened from the patient's pathology orders page
	 */
	public static final String REQUEST_FORM_RESOURCE = "file:configuration/htmlforms/oncology---pathology-specimen-submission.xml";

	/**
	 * The pathology report (Pathology Report for Pathology System), opened from the queue.  Saving its results moves the
	 * case through the lab.
	 */
	public static final String REPORT_FORM_UUID = "17c48a32-d7d3-4b4e-bb2e-9b730e030070";

	public static final String PRIVILEGE_PATIENT_ORDERS = "Task: rwandaemr.pathologyorders";
	public static final String PRIVILEGE_VIEW_QUEUE = "Task: rwandaemr.pathologyQueue";
	public static final String PRIVILEGE_PROCESS_SPECIMENS = "Task: rwandaemr.pathology.processSpecimens";
	public static final String PRIVILEGE_SIGN_OUT = "Task: rwandaemr.pathology.signOut";

	// Questions of the pathology request form shown on the queue
	public static final String FACILITY_UUID = "3c9331e3-d02c-4b7d-840e-59e2d8ab7dff";
	public static final String HOSPITAL_OF_ORIGIN_UUID = "e94e52a3-2d21-4809-9310-797b36877f38";
	public static final String HOSPITAL_OF_ORIGIN_NON_CODED_UUID = "9fe00f19-5a3d-4b92-b457-dcf11519c431";
	public static final String SPECIMEN_UUID = "c475a604-250d-44f1-bd31-475b98cd5411";
	public static final String SPECIMEN_NON_CODED_UUID = "14289de0-b5a9-44ca-9bb6-b90cb153ee55";
	public static final String OTHER_TESTS_REQUESTED_UUID = "3ce1654a-26fe-102b-80cb-0017a47871b2";
	public static final String SURGERY_DATE_UUID = "e0436a5b-dc19-4cb9-ac1b-acc005272c08";

	/**
	 * The clinical history the request form records, which the pathology report starts from, labelled as on the form
	 */
	public static final Map<String, String> CLINICAL_INFORMATION_QUESTIONS;
	static {
		Map<String, String> questions = new LinkedHashMap<String, String>();
		questions.put("226ed7ad-b776-4b99-966d-fd818d3302c2", "Diagnosis");
		questions.put("bb42692c-7721-42fd-8d6d-6583585c2ce1", "Physical exam findings");
		questions.put("5523d50e-9532-440e-bff0-5697cf61e5fc", "Labs");
		questions.put("3866b7eb-4d99-4bc0-a817-f7a644d444f2", "Radiology");
		questions.put("9e74aa34-fc7e-4705-85d2-875818d2395d", "Operative findings");
		CLINICAL_INFORMATION_QUESTIONS = Collections.unmodifiableMap(questions);
	}

	/**
	 * The request form records up to four specimens, A to D, each in a group of its own
	 */
	public static final Map<String, String> SPECIMEN_GROUP_LETTERS;
	static {
		Map<String, String> letters = new HashMap<String, String>();
		letters.put("a967b0ce-1f05-473e-9d6e-c38308b23e25", "A");
		letters.put("ec714e68-d257-4319-aae7-76ae2ceb1f2c", "B");
		letters.put("1ce6c679-c895-4007-815a-b778e462f526", "C");
		letters.put("057c58a8-2623-4374-a288-c0d62d5b4318", "D");
		SPECIMEN_GROUP_LETTERS = Collections.unmodifiableMap(letters);
	}

	private final AdministrationService administrationService;
	private final ConceptService conceptService;
	private final OrderService orderService;

	public PathologyConfig(@Autowired @Qualifier("adminService") AdministrationService administrationService,
						   @Autowired ConceptService conceptService,
						   @Autowired OrderService orderService) {
		this.administrationService = administrationService;
		this.conceptService = conceptService;
		this.orderService = orderService;
	}

	/**
	 * @return the order type of pathology orders, or null if it is not installed
	 */
	public OrderType getPathologyOrderType() {
		return orderService.getOrderTypeByUuid(PATHOLOGY_ORDER_TYPE_UUID);
	}

	/**
	 * @return the concept set of the pathology tests that can be ordered, given by uuid, mapping or id, or null if none is
	 * configured
	 */
	public Concept getOrderablesConceptSet() {
		String ref = StringUtils.trimToNull(administrationService.getGlobalProperty(GP_ORDERABLES));
		if (ref == null) {
			return null;
		}
		Concept concept = conceptService.getConceptByReference(ref);
		if (concept == null && StringUtils.isNumeric(ref)) {
			concept = conceptService.getConcept(Integer.valueOf(ref));
		}
		return concept;
	}

	/**
	 * @return the phone number of the lab printed on the report, or null if none is configured
	 */
	public String getLabPhoneNumber() {
		return StringUtils.trimToNull(administrationService.getGlobalProperty(GP_LAB_PHONE_NUMBER));
	}

	/**
	 * @return the logo printed on the report, as an image of this module or a URL, or null to print none
	 */
	public String getReportLogo() {
		return StringUtils.trimToNull(administrationService.getGlobalProperty(GP_REPORT_LOGO));
	}

	/**
	 * @return the facility entered on the request form, which the queue shows as the hospital of origin
	 */
	public Concept getFacility() {
		return conceptService.getConceptByUuid(FACILITY_UUID);
	}

	public Concept getHospitalOfOrigin() {
		return conceptService.getConceptByUuid(HOSPITAL_OF_ORIGIN_UUID);
	}

	public Concept getHospitalOfOriginNonCoded() {
		return conceptService.getConceptByUuid(HOSPITAL_OF_ORIGIN_NON_CODED_UUID);
	}

	public Concept getSpecimen() {
		return conceptService.getConceptByUuid(SPECIMEN_UUID);
	}

	public Concept getSpecimenNonCoded() {
		return conceptService.getConceptByUuid(SPECIMEN_NON_CODED_UUID);
	}

	public Concept getOtherTestsRequested() {
		return conceptService.getConceptByUuid(OTHER_TESTS_REQUESTED_UUID);
	}

	/**
	 * @return the pathology tests that can be ordered, in the order of the concept set
	 */
	public Set<Concept> getOrderables() {
		Concept conceptSet = getOrderablesConceptSet();
		if (conceptSet == null) {
			return Collections.emptySet();
		}
		return new LinkedHashSet<Concept>(conceptSet.getSetMembers());
	}
}
