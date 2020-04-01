package org.openmrs.module.rwandaemr.migration;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.openmrs.Concept;
import org.openmrs.ConceptName;
import org.openmrs.ConceptSet;
import org.openmrs.DrugOrder;
import org.openmrs.GlobalProperty;
import org.openmrs.api.ConceptService;
import org.openmrs.api.OrderService;
import org.openmrs.api.context.Context;
import org.openmrs.util.OpenmrsUtil;

/**
 * TODO: This was copied straight over from https://github.com/rwanda-rbc-emr/openmrs-module-mohbeforeoneelevenupgrade
 * TODO: As a reference.
 * This creates concepts if there are none for all dose units and frequencies currently existing in
 * the Database and then adds them into a settings file as described at: <a
 * href="https://wiki.openmrs.org/x/OALpAw">Prepare for Upgrading From a Pre-1.10 to 1.10 or Later
 * Version</a> <br />
 * TODO: Actually all orders require a route, must this be provided for each existing drug order?
 */
public class OneTenOrderDoseUnitsAndFrequencies {
	
	private static String ORDER_ENTRY_UPGRADE_SETTINGS_FILE_PATH = OpenmrsUtil.getApplicationDataDirectory()
	        + File.separator + "order_entry_upgrade_settings.txt";
	
	private String DOSE_UNITS_SET_UUID = "87560c3e-8fdd-44c3-9bf8-3be9c6d7c241";
	
	private String FREQUENCIES_SET_UUID = "f73e5638-859d-4fcb-80da-82a68c90d4b5";
	
	private String DRUG_ROUTES_SET_UUID = "969ee822-8e85-41f0-b636-dcbac1592e7a";
	
	private String DRUG_ROUTES_SET_NAME = "SAMPLE DRUG ROUTES";
	
	private static String DOSE_UNIT_SET_NAME = "DOSING UNITS";
	
	private static String FREQUENCIES_SET_NAME = "DRUG ORDER FREQUENCIES";
	
	private ConceptService conceptService = Context.getConceptService();
	
	private String ORDER_ENTRY_UPGRADE_SETTINGS_FILE_CONTENT = "";
	
	private boolean ORDER_ENTRY_UPGRADE_SETTINGS_FILE_WRITTEN = false;
	
	private OrderService orderService = Context.getOrderService();
	
	/**
	 * @should create concepts for each existing text doseunits and frequencies that match
	 * @should create a proper ORDER_ENTRY_UPGRADE_SETTINGS_FILE_PATH mappings file
	 */
	public boolean runPrepareToUpgradeOrderDoseUnitsAndFrequencies() {
		String gpAlreadyRunValue = Context.getAdministrationService()
		        .getGlobalProperty("mohbeforeoneelevenupgrade.executed");
		boolean alreadyRun = StringUtils.isNotBlank(gpAlreadyRunValue) ? (Context.getAdministrationService()
		        .getGlobalProperty("mohbeforeoneelevenupgrade.executed").equals("true") ? true : false) : false;
		if (!alreadyRun) {
			Concept doseUnitsSetConcept = null;
			String doseUnitsSetConceptUuid = Context.getAdministrationService().getGlobalProperty(
			    "order.drugDosingUnitsConceptUuid");
			List<DrugOrder> allDrugOrders = orderService.getDrugOrders();
			List<String> doseUnits = new ArrayList<String>();
			List<String> frequencies = new ArrayList<String>();
			Concept frequenciesSetConcept = conceptService.getConceptByUuid(FREQUENCIES_SET_UUID);
			if (StringUtils.isBlank(doseUnitsSetConceptUuid)) {
				doseUnitsSetConcept = createConcept(DOSE_UNIT_SET_NAME, DOSE_UNITS_SET_UUID, true);
				updateOrSaveNewGlobalProperty("order.drugDosingUnitsConceptUuid", DOSE_UNITS_SET_UUID);
			} else {
				DOSE_UNITS_SET_UUID = doseUnitsSetConceptUuid;
				doseUnitsSetConcept = conceptService.getConceptByUuid(DOSE_UNITS_SET_UUID);
				if (doseUnitsSetConcept == null) {
					DOSE_UNITS_SET_UUID = doseUnitsSetConceptUuid;
					doseUnitsSetConcept = createConcept(DOSE_UNIT_SET_NAME, DOSE_UNITS_SET_UUID, true);
				}
			}
			for (DrugOrder dOrder : allDrugOrders) {
				if (dOrder != null) {
					addStringToStringsList(doseUnits, dOrder.getUnits());
					addStringToStringsList(frequencies, dOrder.getFrequency());
				}
			}
			for (String mem : doseUnits) {
				Concept member = createConcept(mem, null, false);
				
				// TODO what's the most appropriate default value for sort
				// weight
				// instead of 0.0?
				doseUnitsSetConcept = addConceptSetMember(doseUnitsSetConcept, member, 0.0);
				addDoseUnitOrFrequencyEntryToSettingFileContent(mem, member.getConceptId());
			}
			if (frequenciesSetConcept == null) {
				frequenciesSetConcept = createConcept(FREQUENCIES_SET_NAME, FREQUENCIES_SET_UUID, true);
			}
			for (String mem : frequencies) {
				Concept member = createConcept(mem, null, false);
				
				frequenciesSetConcept = addConceptSetMember(frequenciesSetConcept, member, 0.0);
				addDoseUnitOrFrequencyEntryToSettingFileContent(mem, member.getConceptId());
			}
			createOrderEntryUpgradeFileAndWriteItsContent();
			createSampleStartingDrugRoutes();
			if (ORDER_ENTRY_UPGRADE_SETTINGS_FILE_WRITTEN) {
				updateOrSaveNewGlobalProperty("mohbeforeoneelevenupgrade.executed", "true");
				return true;
			} else {
				return false;
			}
		} else
			return true;
	}
	
	/**
	 * @should create and return created concept
	 * @param name
	 * @param uuid
	 * @param isSet
	 * @return
	 */
	public Concept createConcept(String name, String uuid, boolean isSet) {
		Concept concept = new Concept();
		ConceptName cn = new ConceptName(name, Context.getLocale());
		
		cn.setDateCreated(new Date());
		cn.setCreator(Context.getAuthenticatedUser());
		concept.addName(cn);
		if (StringUtils.isNotBlank(uuid)) {
			if (conceptService.getConceptByUuid(uuid) != null) {
				concept = conceptService.getConceptByUuid(uuid);
			} else {
				concept.setUuid(uuid);
			}
		}
		concept.setSet(isSet);
		concept.setDatatype(conceptService.getConceptDatatypeByName("Text"));
		concept.setConceptClass(conceptService.getConceptClassByName("Drug"));
		concept.setCreator(Context.getAuthenticatedUser());
		concept.setRetired(false);
		
		return conceptService.saveConcept(concept);
	}
	
	/**
	 * @should work as expected
	 * @param propertyName
	 * @param propertyValue
	 * @return
	 */
	public GlobalProperty updateOrSaveNewGlobalProperty(String propertyName, String propertyValue) {
		GlobalProperty gp = Context.getAdministrationService().getGlobalPropertyObject(propertyName);
		
		if (gp == null) {
			gp = new GlobalProperty();
			
			gp.setProperty(propertyName);
			gp = Context.getAdministrationService().saveGlobalProperty(gp);
		}
		gp.setPropertyValue(propertyValue);
		
		return Context.getAdministrationService().saveGlobalProperty(gp);
	}
	
	/**
	 * @param sortWeight
	 * @should add a concept set member to an existing concept set
	 * @param set, the concept set to add member to
	 * @param member, the concept member to add to set
	 * @return set, conceptSet is actually a member, its concept property refers to the member where
	 *         as its conceptSet property refers to the set where it belongs
	 */
	public Concept addConceptSetMember(Concept set, Concept member, Double sortWeight) {
		// get all concepts in a
		// set#conceptService.getConceptsByConceptSet(concept);
		if (set != null && member != null && sortWeight != null) {
			ConceptSet setMember = new ConceptSet(member, sortWeight);
			setMember.setCreator(Context.getAuthenticatedUser());
			setMember.setDateCreated(new Date());
			set.getConceptSets().add(setMember);
			set.setConceptSets(set.getConceptSets());
			conceptService.saveConcept(set);
			
			return conceptService.getConcept(set.getId());
		} else
			return null;
	}
	
	public void addStringToStringsList(List<String> list, String string) {
		boolean exists = false;
		
		if (list != null && StringUtils.isNotBlank(string)) {
			for (String str : list) {
				if (StringUtils.isNotBlank(str) && str.equals(string)) {
					exists = true;
					break;
				}
			}
			if (!exists) {
				list.add(string);
			}
		}
	}
	
	/**
	 * @should only add an entry when it needs to
	 * @return ORDER_ENTRY_UPGRADE_SETTINGS_FILE_CONTENT
	 */
	public String addDoseUnitOrFrequencyEntryToSettingFileContent(String doseUnitOrFreqName, Integer conceptId) {
		// ORDER_ENTRY_UPGRADE_SETTINGS_FILE_CONTENT
		if (StringUtils.isNotBlank(doseUnitOrFreqName) && conceptId != null) {
			doseUnitOrFreqName = doseUnitOrFreqName.replace(" ", "\\ ");
			ORDER_ENTRY_UPGRADE_SETTINGS_FILE_CONTENT += doseUnitOrFreqName + "=" + conceptId + "\n";
		}
		
		return ORDER_ENTRY_UPGRADE_SETTINGS_FILE_CONTENT;
	}
	
	private void createOrderEntryUpgradeFileAndWriteItsContent() {
		if (StringUtils.isNotBlank(ORDER_ENTRY_UPGRADE_SETTINGS_FILE_CONTENT)) {
			FileOutputStream fop = null;
			File file;
			
			ORDER_ENTRY_UPGRADE_SETTINGS_FILE_CONTENT = removeLastOccurencyOf(ORDER_ENTRY_UPGRADE_SETTINGS_FILE_CONTENT,
			    "\n");
			try {
				file = new File(ORDER_ENTRY_UPGRADE_SETTINGS_FILE_PATH);
				fop = new FileOutputStream(file);
				
				// if file doesnt exists, then create it
				if (!file.exists()) {
					file.createNewFile();
				}
				
				// get the content in bytes
				byte[] contentInBytes = ORDER_ENTRY_UPGRADE_SETTINGS_FILE_CONTENT.getBytes();
				
				fop.write(contentInBytes);
				fop.flush();
				fop.close();
				
				System.out.println("Done writing ORDER_ENTRY_UPGRADE_SETTINGS_FILE");
				ORDER_ENTRY_UPGRADE_SETTINGS_FILE_WRITTEN = true;
			}
			catch (IOException e) {
				e.printStackTrace();
			}
			finally {
				try {
					if (fop != null) {
						fop.close();
					}
				}
				catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	public String removeLastOccurencyOf(String string, String stringToToRemoved) {
		int ind = string.lastIndexOf(stringToToRemoved);
		return new StringBuilder(string).replace(ind, ind + 1, "").toString();
	}
	
	@SuppressWarnings("unused")
	//memberN
	public void createSampleStartingDrugRoutes() {
		GlobalProperty gp = Context.getAdministrationService().getGlobalPropertyObject("order.drugRoutesConceptUuid");
		
		if (gp == null) {
			gp = updateOrSaveNewGlobalProperty("order.drugRoutesConceptUuid", "");
		}
		
		if (StringUtils.isBlank(gp.getPropertyValue())) {
			Concept drugRoutesSetConcept = createConcept(DRUG_ROUTES_SET_NAME, DRUG_ROUTES_SET_UUID, true);
			Concept member1 = addConceptSetMember(drugRoutesSetConcept, createConcept("ORAL", null, false), 0.0);
			Concept member2 = addConceptSetMember(drugRoutesSetConcept, createConcept("SUBLINGUAL", null, false), 0.0);
			Concept member3 = addConceptSetMember(drugRoutesSetConcept, createConcept("PER RECTUM", null, false), 0.0);
			Concept member4 = addConceptSetMember(drugRoutesSetConcept, createConcept("INTRAVENOUS", null, false), 0.0);
			Concept member5 = addConceptSetMember(drugRoutesSetConcept, createConcept("INTRAOSSEOUS", null, false), 0.0);
			Concept member6 = addConceptSetMember(drugRoutesSetConcept,
			    createConcept("ENDOTRACHEAL INHALATION", null, false), 0.0);
			Concept member7 = addConceptSetMember(drugRoutesSetConcept, createConcept("INTRAMUSCULAR", null, false), 0.0);
			Concept member8 = addConceptSetMember(drugRoutesSetConcept, createConcept("RECTAL", null, false), 0.0);
			Concept member9 = addConceptSetMember(drugRoutesSetConcept, createConcept("INGESTION", null, false), 0.0);
			Concept member10 = addConceptSetMember(drugRoutesSetConcept, createConcept("PARENTERAL", null, false), 0.0);
			Concept member11 = addConceptSetMember(drugRoutesSetConcept, createConcept("TOPICAL", null, false), 0.0);
			Concept member12 = addConceptSetMember(drugRoutesSetConcept, createConcept("UNASSIGNED", null, false), 0.0);
			Concept member13 = addConceptSetMember(drugRoutesSetConcept, createConcept("UNKNOWN", null, false), 0.0);
			Concept member14 = addConceptSetMember(drugRoutesSetConcept, createConcept("VAGINAL", null, false), 0.0);
			Concept member15 = addConceptSetMember(drugRoutesSetConcept, createConcept("SOFT TISSUE", null, false), 0.0);
			Concept member16 = addConceptSetMember(drugRoutesSetConcept,
			    createConcept("RESPIRATORY (INHALATION)", null, false), 0.0);
			Concept member17 = addConceptSetMember(drugRoutesSetConcept, createConcept("PERINEURAL", null, false), 0.0);
			Concept member18 = addConceptSetMember(drugRoutesSetConcept, createConcept("NASAL", null, false), 0.0);
			Concept member19 = addConceptSetMember(drugRoutesSetConcept, createConcept("INTERSTITIAL", null, false), 0.0);
			Concept member20 = addConceptSetMember(drugRoutesSetConcept, createConcept("HEMODIALYSIS", null, false), 0.0);
			Concept member21 = addConceptSetMember(drugRoutesSetConcept, createConcept("DENTAL", null, false), 0.0);
			Concept member22 = addConceptSetMember(drugRoutesSetConcept, createConcept("ENDOCERVICAL", null, false), 0.0);
			Concept member23 = addConceptSetMember(drugRoutesSetConcept, createConcept("ENTERAL", null, false), 0.0);
			Concept member24 = addConceptSetMember(drugRoutesSetConcept, createConcept("INFILTRATION", null, false), 0.0);
			Concept member25 = addConceptSetMember(drugRoutesSetConcept, createConcept("OPHTHALMIC", null, false), 0.0);
			Concept member26 = addConceptSetMember(drugRoutesSetConcept, createConcept("PERIARTICULAR", null, false), 0.0);
			Concept member27 = addConceptSetMember(drugRoutesSetConcept, createConcept("SUBCUTANEOUS", null, false), 0.0);
			
			updateOrSaveNewGlobalProperty("order.drugRoutesConceptUuid", DRUG_ROUTES_SET_UUID);
		}
	}
}
