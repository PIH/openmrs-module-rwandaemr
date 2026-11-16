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
	 * The pathology request form, opened from the patient's pathology orders page
	 */
	public static final String REQUEST_FORM_RESOURCE = "file:configuration/htmlforms/oncology---pathology-specimen-submission.xml";

	public static final String PRIVILEGE_PATIENT_ORDERS = "Task: rwandaemr.pathologyorders";
	public static final String PRIVILEGE_VIEW_QUEUE = "Task: rwandaemr.pathologyQueue";
	public static final String PRIVILEGE_PROCESS_SPECIMENS = "Task: rwandaemr.pathology.processSpecimens";
	public static final String PRIVILEGE_SIGN_OUT = "Task: rwandaemr.pathology.signOut";

	// Questions of the pathology request form shown on the queue
	public static final String HOSPITAL_OF_ORIGIN_UUID = "e94e52a3-2d21-4809-9310-797b36877f38";
	public static final String HOSPITAL_OF_ORIGIN_NON_CODED_UUID = "9fe00f19-5a3d-4b92-b457-dcf11519c431";
	public static final String SPECIMEN_UUID = "c475a604-250d-44f1-bd31-475b98cd5411";
	public static final String SPECIMEN_NON_CODED_UUID = "14289de0-b5a9-44ca-9bb6-b90cb153ee55";
	public static final String OTHER_TESTS_REQUESTED_UUID = "3ce1654a-26fe-102b-80cb-0017a47871b2";

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
