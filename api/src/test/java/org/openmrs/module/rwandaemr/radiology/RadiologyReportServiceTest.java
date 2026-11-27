package org.openmrs.module.rwandaemr.radiology;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.Concept;
import org.openmrs.ConceptName;
import org.openmrs.Encounter;
import org.openmrs.Form;
import org.openmrs.Obs;
import org.openmrs.Order;
import org.openmrs.Provider;
import org.openmrs.api.ProviderService;

import java.util.Locale;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RadiologyReportServiceTest extends BaseHL7TranslatorTest {

	private RadiologyReportService service;
	private RadiologyReportDraftRepository draftRepository;
	private Provider radiologist;

	@BeforeEach
	public void setUpService() {
		draftRepository = mock(RadiologyReportDraftRepository.class);
		radiologist = new Provider(7);
		radiologist.setIdentifier("RAD-7");
		service = new RadiologyReportService(radiologyConfig, rwandaEmrService, mock(ProviderService.class), oruToObsTranslator,
				draftRepository) {
			@Override
			public Provider getCurrentProvider() {
				return radiologist;
			}
		};
		testOrder.setOrderId(5);
		// X-rays are configured here as a modality whose reports are entered in OpenMRS
		when(administrationService.getGlobalProperty(RadiologyConfig.GP_MANUAL_REPORT_MODALITIES, "US")).thenReturn("CR");
	}

	@Test
	public void shouldOnlyAllowAReportOnceTheImagesAreReceivedFromPacs() {
		assertThat(service.requiresManualReport(testOrder), equalTo(true));
		assertThat(service.canEnterReport(testOrder), equalTo(false));
		assertThat(service.getReasonReportCannotBeEntered(testOrder), containsString("images"));

		Obs study = new Obs();
		study.setConcept(radiologyStudyConstruct);
		rwandaEmrService.addObsForOrder(testOrder, study);

		assertThat(service.canEnterReport(testOrder), equalTo(true));
		assertThat(service.getReasonReportCannotBeEntered(testOrder), nullValue());
	}

	@Test
	public void shouldNotAllowAReportForModalitiesReportedThroughPacs() {
		when(administrationService.getGlobalProperty(RadiologyConfig.GP_MANUAL_REPORT_MODALITIES, "US")).thenReturn("US");
		Obs study = new Obs();
		study.setConcept(radiologyStudyConstruct);
		rwandaEmrService.addObsForOrder(testOrder, study);

		assertThat(service.requiresManualReport(testOrder), equalTo(false));
		assertThat(service.canEnterReport(testOrder), equalTo(false));
		assertThat(service.getReasonReportCannotBeEntered(testOrder), containsString("come from PACS"));
	}

	@Test
	public void shouldMakeTheViewerLinkFromTheAccessionNumber() {
		when(administrationService.getGlobalProperty(RadiologyConfig.GP_IMAGE_VIEWER_URL_TEMPLATE)).thenReturn(
				" https://pacs.example/PACSAPI/Launch_Viewer?AccessionNumber={accessionNumber} ");

		assertThat(radiologyConfig.getImageViewerUrl("ORD-692888"),
				equalTo("https://pacs.example/PACSAPI/Launch_Viewer?AccessionNumber=ORD-692888"));
		assertThat(radiologyConfig.getImageViewerUrl("A&B 1"),
				equalTo("https://pacs.example/PACSAPI/Launch_Viewer?AccessionNumber=A%26B+1"));
		assertThat(radiologyConfig.getImageViewerUrl(null), nullValue());

		// Saved through the admin pages, which escape & as &amp;
		when(administrationService.getGlobalProperty(RadiologyConfig.GP_IMAGE_VIEWER_URL_TEMPLATE)).thenReturn(
				"https://pacs.example/Launch_Viewer?Username=u&amp;Password=p&amp;AccessionNumber={accessionNumber}");
		assertThat(radiologyConfig.getImageViewerUrl("ORD-1"),
				equalTo("https://pacs.example/Launch_Viewer?Username=u&Password=p&AccessionNumber=ORD-1"));
		when(administrationService.getGlobalProperty(RadiologyConfig.GP_IMAGE_VIEWER_URL_TEMPLATE)).thenReturn(
				" https://pacs.example/PACSAPI/Launch_Viewer?AccessionNumber={accessionNumber} ");

		testOrder.setAccessionNumber("ORD-764591");
		assertThat(service.getImageUrl(testOrder),
				equalTo("https://pacs.example/PACSAPI/Launch_Viewer?AccessionNumber=ORD-764591"));

		// Without a placeholder, or not configured, no link is made
		when(administrationService.getGlobalProperty(RadiologyConfig.GP_IMAGE_VIEWER_URL_TEMPLATE)).thenReturn("https://pacs.example/viewer");
		assertThat(radiologyConfig.getImageViewerUrl("ORD-692888"), nullValue());
		when(administrationService.getGlobalProperty(RadiologyConfig.GP_IMAGE_VIEWER_URL_TEMPLATE)).thenReturn(null);
		assertThat(service.getImageUrl(testOrder), nullValue());
	}

	@Test
	public void shouldSaveADraftThatIsNotPartOfThePatientRecord() {
		addImages();

		service.saveDraft(testOrder, "<p><b>Liver</b> normal</p>");

		verify(draftRepository).saveDraft(eq(5), eq(RadiologyReportText.FORMATTED_START + "<p><b>Liver</b> normal</p>"
				+ RadiologyReportText.FORMATTED_END), eq(7));
		// Nothing is saved to the patient's record
		assertThat(rwandaEmrService.getEncounters() == null || rwandaEmrService.getEncounters().isEmpty(), equalTo(true));
		assertThat(service.getReport(testOrder), nullValue());
	}

	@Test
	public void shouldSaveTheFinalReportOnTheTestAndRemoveItsDraft() {
		addImages();

		service.saveReport(testOrder, "<p>Normal <script>alert(1)</script>study</p>");
		linkSavedReport();

		RadiologyReportService.RadiologyReport report = service.getReport(testOrder);
		assertThat(report.getText(), equalTo(RadiologyReportText.FORMATTED_START + "<p>Normal study</p>"
				+ RadiologyReportText.FORMATTED_END));
		verify(draftRepository).deleteDraft(5);
	}

	@Test
	public void shouldNotTakeAFinalReportBackToADraft() {
		addImages();
		service.saveReport(testOrder, "<p>Normal study</p>");
		linkSavedReport();

		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
				() -> service.saveDraft(testOrder, "<p>Changed</p>"));
		assertThat(e.getMessage(), containsString("already final"));
		verify(draftRepository, never()).saveDraft(any(), any(), any());
	}

	@Test
	public void shouldRefuseAnEmptyReport() {
		addImages();
		assertThrows(IllegalArgumentException.class, () -> service.saveDraft(testOrder, "<p><br></p>"));
		assertThrows(IllegalArgumentException.class, () -> service.saveReport(testOrder, " "));
	}

	/**
	 * The test service does not look up saved obs by order, so the saved report is linked to its order here
	 */
	private void linkSavedReport() {
		for (org.openmrs.Encounter e : rwandaEmrService.getEncounters()) {
			for (Obs report : e.getObsAtTopLevel(false)) {
				rwandaEmrService.addObsForOrder(testOrder, report);
			}
		}
	}

	private void addImages() {
		Obs study = new Obs();
		study.setConcept(radiologyStudyConstruct);
		rwandaEmrService.addObsForOrder(testOrder, study);
	}

	@Test
	public void shouldRefuseToSaveAReportBeforeTheImagesArrive() {
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
				() -> service.saveReport(testOrder, "Normal study."));
		assertThat(e.getMessage(), containsString("have not been received from PACS"));
	}

	private static final String FORM_UUID = "cfca9ba7-a815-55df-b904-c7673ef15237";

	private static final String FORM_XML = "<htmlform><div><h2>Diagnostic Breast Ultrasound Report Form</h2>" +
			"<fieldset><legend>IV. Assessment and Management</legend>" +
			"<div class=\"row\"><span class=\"lbl\">BI-RADS Category</span>" +
			"<obs conceptId=\"birads\" answerConceptIds=\"b2,b4\" answerLabels=\"2 - Benign,4 - Suspicious\" style=\"radio\"/></div>" +
			"</fieldset></div></htmlform>";

	/**
	 * A service whose report form is installed, and which keeps the obs it saves on the order as the database would
	 */
	private RadiologyReportService formService(Form form) {
		return new RadiologyReportService(radiologyConfig, rwandaEmrService, mock(ProviderService.class), oruToObsTranslator,
				draftRepository) {
			@Override
			public Provider getCurrentProvider() {
				return radiologist;
			}

			@Override
			protected Form getFormByUuid(String uuid) {
				return form != null && uuid.equals(form.getUuid()) ? form : null;
			}

			@Override
			protected void saveObs(Obs obs) {
				rwandaEmrService.addObsForOrder(obs.getOrder(), obs);
			}
		};
	}

	private void configureReportForm() {
		when(administrationService.getGlobalProperty(RadiologyConfig.GP_REPORT_FORMS, "")).thenReturn(
				"CIEL:999:other-form, " + testOrder.getConcept().getUuid() + ":" + FORM_UUID);
		when(conceptService.getConceptByReference(testOrder.getConcept().getUuid())).thenReturn(testOrder.getConcept());
	}

	private Encounter formEncounter(Form form, Concept birads, Concept answer) {
		Encounter encounter = new Encounter(900);
		encounter.setPatient(testOrder.getPatient());
		encounter.setForm(form);
		encounter.setEncounterDatetime(new java.util.Date());
		Obs obs = new Obs();
		obs.setConcept(birads);
		obs.setValueCoded(answer);
		encounter.addObs(obs);
		return encounter;
	}

	private Concept concept(String uuid, String name) {
		Concept concept = new Concept();
		concept.setUuid(uuid);
		concept.setFullySpecifiedName(new ConceptName(name, Locale.ENGLISH));
		return concept;
	}

	@Test
	public void shouldReportATestWithItsConfiguredFormOnlyIfTheFormIsInstalled() {
		configureReportForm();
		Form form = new Form();
		form.setUuid(FORM_UUID);

		assertThat(formService(form).getReportFormUuid(testOrder), equalTo(FORM_UUID));
		assertThat(formService(form).getReportForm(testOrder), equalTo(form));
		// Not installed, or retired: the report editor is used
		assertThat(formService(null).getReportForm(testOrder), nullValue());
		form.setRetired(true);
		assertThat(formService(form).getReportForm(testOrder), nullValue());
		// A modality reported through PACS has no report form
		when(administrationService.getGlobalProperty(RadiologyConfig.GP_MANUAL_REPORT_MODALITIES, "US")).thenReturn("US");
		assertThat(formService(form).getReportFormUuid(testOrder), nullValue());
	}

	@Test
	public void shouldRecordAFormAsTheFinalReportOfItsTestAndLinkThem() {
		configureReportForm();
		Form form = new Form();
		form.setUuid(FORM_UUID);
		RadiologyReportService formService = formService(form);
		addImages();
		Concept birads = concept("birads", "BI-RADS");
		Encounter encounter = formEncounter(form, birads, concept("b4", "Category 4"));

		formService.saveFormReport(testOrder, encounter, FORM_XML);
		linkSavedReport();

		assertThat(formService.getReportedOrder(encounter), equalTo((Order) testOrder));
		assertThat(formService.getReportFormEncounter(testOrder), equalTo(encounter));
		RadiologyReportService.RadiologyReport report = formService.getReport(testOrder);
		assertThat(report.getText(), equalTo(RadiologyReportText.FORMATTED_START +
				"<h3>Diagnostic Breast Ultrasound Report Form</h3><h4>IV. Assessment and Management</h4>" +
				"<p><b>BI-RADS Category:</b> 4 - Suspicious</p>" + RadiologyReportText.FORMATTED_END));
		verify(draftRepository).deleteDraft(5);

		// Saving the form again corrects the report, and keeps the one link to the test
		encounter.getObsAtTopLevel(false).stream().filter(o -> birads.equals(o.getConcept())).findFirst().get()
				.setValueCoded(concept("b2", "Category 2"));
		formService.saveFormReport(testOrder, encounter, FORM_XML);
		assertThat(formService.getReport(testOrder).getText(), containsString("2 - Benign"));
		assertThat(encounter.getObsAtTopLevel(false).stream()
				.filter(o -> radiologyAccessionNumber.equals(o.getConcept())).count(), equalTo(1L));
	}

	@Test
	public void shouldNotRecordAFormAsTheReportOfAnotherTestThanItsOwn() throws Exception {
		configureReportForm();
		Form form = new Form();
		form.setUuid(FORM_UUID);
		RadiologyReportService formService = formService(form);
		addImages();
		Encounter encounter = formEncounter(form, concept("birads", "BI-RADS"), concept("b4", "Category 4"));
		formService.saveFormReport(testOrder, encounter, FORM_XML);

		org.openmrs.TestOrder otherOrder = new org.openmrs.TestOrder();
		otherOrder.setOrderId(6);
		org.apache.commons.lang3.reflect.FieldUtils.writeField(otherOrder, "orderNumber", "ORD-6", true);
		otherOrder.setConcept(testOrder.getConcept());
		otherOrder.setPatient(testOrder.getPatient());
		Obs study = new Obs();
		study.setConcept(radiologyStudyConstruct);
		rwandaEmrService.addObsForOrder(otherOrder, study);

		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
				() -> formService.saveFormReport(otherOrder, encounter, FORM_XML));
		assertThat(e.getMessage(), containsString("another test"));
		assertThat(formService.getReport(otherOrder), nullValue());
	}

	@Test
	public void shouldNotRecordAFormReportBeforeTheImagesArrive() {
		configureReportForm();
		Form form = new Form();
		form.setUuid(FORM_UUID);
		Encounter encounter = formEncounter(form, concept("birads", "BI-RADS"), concept("b4", "Category 4"));

		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
				() -> formService(form).saveFormReport(testOrder, encounter, FORM_XML));
		assertThat(e.getMessage(), containsString("have not been received from PACS"));
		assertThat(encounter.getObsAtTopLevel(false).stream()
				.anyMatch(o -> radiologyAccessionNumber.equals(o.getConcept())), equalTo(false));
	}
}
