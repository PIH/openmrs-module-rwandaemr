package org.openmrs.module.rwandaemr.radiology;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.Obs;
import org.openmrs.Provider;
import org.openmrs.api.ProviderService;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
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
}
