package org.openmrs.module.rwandaemr.radiology;

import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.openmrs.Concept;
import org.openmrs.EncounterProvider;
import org.openmrs.Obs;
import org.openmrs.Order;
import org.openmrs.Provider;
import org.openmrs.User;
import org.openmrs.api.ProviderService;
import org.openmrs.api.context.Context;
import org.openmrs.module.rwandaemr.RwandaEmrService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Reports entered in OpenMRS by a radiologist, for modalities such as ultrasound that do not send reports through PACS
 */
@Component
public class RadiologyReportService {

	public static final String PRIVILEGE_ENTER_REPORT = "Task: rwandaemr.radiologyReport.enter";

	private final RadiologyConfig radiologyConfig;
	private final RwandaEmrService rwandaEmrService;
	private final ProviderService providerService;
	private final ORUR01ToObsTranslator resultTranslator;
	private final RadiologyReportDraftRepository draftRepository;

	public RadiologyReportService(@Autowired RadiologyConfig radiologyConfig,
								  @Autowired @Qualifier("rwandaEmrService") RwandaEmrService rwandaEmrService,
								  @Autowired ProviderService providerService,
								  @Autowired ORUR01ToObsTranslator resultTranslator,
								  @Autowired RadiologyReportDraftRepository draftRepository) {
		this.radiologyConfig = radiologyConfig;
		this.rwandaEmrService = rwandaEmrService;
		this.providerService = providerService;
		this.resultTranslator = resultTranslator;
		this.draftRepository = draftRepository;
	}

	/**
	 * @return true if the order is for a modality whose reports are entered in OpenMRS
	 */
	public boolean requiresManualReport(Order order) {
		return order != null && radiologyConfig.isManualReportModality(radiologyConfig.getModality(order.getConcept()));
	}

	/**
	 * @return true if a report can be entered or corrected for the order now: it needs a manual report, is not cancelled,
	 * and its images have been received from PACS, as the radiologist reports on the images
	 */
	public boolean canEnterReport(Order order) {
		return requiresManualReport(order) && !BooleanUtils.isTrue(order.getVoided()) && order.getDateStopped() == null
				&& hasImagesFromPacs(order);
	}

	/**
	 * @return true if PACS has sent the study (images) for the order or another test of its study
	 */
	public boolean hasImagesFromPacs(Order order) {
		Concept studyConstruct = radiologyConfig.getRadiologyStudyConstruct();
		for (Order studyOrder : getStudyOrders(order)) {
			for (Obs obs : rwandaEmrService.getObsByOrder(studyOrder)) {
				if (obs.getConcept().equals(studyConstruct)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * @return the link to view the images of the order's study: the one PACS sent, or otherwise one made from the
	 * study's accession number, or null if neither is available
	 */
	public String getImageUrl(Order order) {
		Concept studyConstruct = radiologyConfig.getRadiologyStudyConstruct();
		Concept imagesAvailable = radiologyConfig.getRadiologyImagesAvailable();
		for (Order studyOrder : getStudyOrders(order)) {
			for (Obs obs : rwandaEmrService.getObsByOrder(studyOrder)) {
				if (obs.getConcept().equals(studyConstruct) && obs.getGroupMembers() != null) {
					for (Obs member : obs.getGroupMembers()) {
						if (!member.getVoided() && member.getConcept().equals(imagesAvailable)) {
							String url = HL7Utils.getViewerUrl(member.getComment());
							if (url != null) {
								return url;
							}
						}
					}
				}
			}
		}
		return radiologyConfig.getImageViewerUrl(order.getAccessionNumber());
	}

	/**
	 * @return why a report cannot be entered for the order, or null if it can
	 */
	public String getReasonReportCannotBeEntered(Order order) {
		if (!requiresManualReport(order)) {
			return "Reports for this study come from PACS and are not entered in OpenMRS.";
		}
		if (BooleanUtils.isTrue(order.getVoided()) || order.getDateStopped() != null) {
			return "This order was cancelled.";
		}
		if (!hasImagesFromPacs(order)) {
			return "The images for this study have not been received from PACS yet. The report can be entered once they arrive.";
		}
		return null;
	}

	/**
	 * @return the orders of the study the order is part of, which share the images.  Each test has its own report.
	 */
	public List<Order> getStudyOrders(Order order) {
		return resultTranslator.getStudyOrders(order, order.getAccessionNumber());
	}

	/**
	 * @return the report recorded for the order, entered manually or received from PACS, or null if there is none
	 */
	public RadiologyReport getReport(Order order) {
		Concept reportConstruct = radiologyConfig.getRadiologyReportConstruct();
		for (Obs obs : rwandaEmrService.getObsByOrder(order)) {
			if (!obs.getConcept().equals(reportConstruct)) {
				continue;
			}
			RadiologyReport report = new RadiologyReport();
			report.setText(getMember(obs, radiologyConfig.getRadiologyReportComments(), true));
			report.setStatus(getMember(obs, radiologyConfig.getRadiologyReportType(), false));
			report.setReportDate(obs.getEncounter() == null ? obs.getObsDatetime() : obs.getEncounter().getEncounterDatetime());
			if (obs.getEncounter() != null) {
				for (EncounterProvider ep : obs.getEncounter().getActiveEncounterProviders()) {
					if (ep.getEncounterRole() != null
							&& ep.getEncounterRole().equals(radiologyConfig.getPrincipalResultsInterpreterEncounterRole())) {
						report.setRadiologist(getProviderName(ep.getProvider()));
					}
				}
			}
			Date changed = obs.getDateCreated();
			User changedBy = obs.getCreator();
			report.setLastChanged(changed);
			report.setLastChangedBy(changedBy == null || changedBy.getPersonName() == null ? null :
					changedBy.getPersonName().getFullName());
			return report;
		}
		return null;
	}

	/**
	 * Records the report of this test as final, signed by the logged-in radiologist, and removes its draft.  Clinicians
	 * can see it from then on.  Each test of a study has its own report.  Correcting a final report keeps its report
	 * date, and is recorded as a correction.
	 * @param reportHtml the formatted report from the report editor
	 * @throws IllegalArgumentException if the report cannot be entered for the order or the text is empty or too long
	 * @throws IllegalStateException if the logged-in user is not linked to a provider
	 */
	public void saveReport(Order order, String reportHtml) {
		String reportText = prepareReport(order, reportHtml);
		Provider radiologist = getSigningProvider();
		RadiologyReport existing = getReport(order);
		Date reportDate = existing != null && existing.getReportDate() != null ? existing.getReportDate() : new Date();
		resultTranslator.saveManualReport(order, reportDate, radiologist, reportText);
		draftRepository.deleteDraft(order.getOrderId());
	}

	/**
	 * Saves the report of this test as a draft, which only radiologists can see, until it is saved as final.  A report
	 * that is already final can only be corrected, not taken back to a draft.
	 * @param reportHtml the formatted report from the report editor
	 * @throws IllegalArgumentException if a draft cannot be saved for the order or the text is empty or too long
	 * @throws IllegalStateException if the logged-in user is not linked to a provider
	 */
	public void saveDraft(Order order, String reportHtml) {
		String reportText = prepareReport(order, reportHtml);
		if (getReport(order) != null) {
			throw new IllegalArgumentException("This report is already final, so it cannot be saved as a draft. "
					+ "Save your changes as a correction instead.");
		}
		Provider radiologist = getSigningProvider();
		draftRepository.saveDraft(order.getOrderId(), reportText, radiologist.getProviderId());
	}

	/**
	 * @return the draft report of this test, or null if there is none
	 */
	public RadiologyReportDraftRepository.Draft getDraft(Order order) {
		return order == null || order.getOrderId() == null ? null : draftRepository.getDraft(order.getOrderId());
	}

	/**
	 * Throws away the draft report of this test
	 */
	public void discardDraft(Order order) {
		draftRepository.deleteDraft(order.getOrderId());
	}

	/**
	 * @return the report text to save: checked that it can be entered, and cleaned
	 */
	private String prepareReport(Order order, String reportHtml) {
		String reason = getReasonReportCannotBeEntered(order);
		if (reason != null) {
			throw new IllegalArgumentException(reason);
		}
		if (StringUtils.isBlank(reportHtml)) {
			throw new IllegalArgumentException("The report cannot be empty");
		}
		return RadiologyReportText.toStorage(reportHtml);
	}

	private Provider getSigningProvider() {
		Provider radiologist = getCurrentProvider();
		if (radiologist == null) {
			throw new IllegalStateException("Your user account is not linked to a provider, so the report cannot be signed");
		}
		return radiologist;
	}

	/**
	 * @return the provider account of the logged-in user, or null if there is none
	 */
	public Provider getCurrentProvider() {
		User user = Context.getAuthenticatedUser();
		if (user == null || user.getPerson() == null) {
			return null;
		}
		Collection<Provider> providers = providerService.getProvidersByPerson(user.getPerson(), false);
		return providers == null || providers.isEmpty() ? null : providers.iterator().next();
	}

	private String getMember(Obs group, Concept concept, boolean text) {
		if (group.getGroupMembers() != null && concept != null) {
			for (Obs member : group.getGroupMembers()) {
				if (!member.getVoided() && concept.equals(member.getConcept())) {
					if (text) {
						return member.getValueText();
					}
					Concept value = member.getValueCoded();
					if (value == null) {
						return null;
					}
					return value.getName(Locale.ENGLISH) != null ? value.getName(Locale.ENGLISH).getName() : value.getDisplayString();
				}
			}
		}
		return null;
	}

	private String getProviderName(Provider provider) {
		if (provider == null) {
			return null;
		}
		if (provider.getPerson() != null && provider.getPerson().getPersonName() != null) {
			return provider.getPerson().getPersonName().getFullName();
		}
		return provider.getName();
	}

	/**
	 * A radiology report recorded on an order
	 */
	public static class RadiologyReport {

		private String text;
		private String status;
		private Date reportDate;
		private String radiologist;
		private Date lastChanged;
		private String lastChangedBy;

		public String getText() {
			return text;
		}

		public void setText(String text) {
			this.text = text;
		}

		public String getStatus() {
			return status;
		}

		public void setStatus(String status) {
			this.status = status;
		}

		public Date getReportDate() {
			return reportDate;
		}

		public void setReportDate(Date reportDate) {
			this.reportDate = reportDate;
		}

		public String getRadiologist() {
			return radiologist;
		}

		public void setRadiologist(String radiologist) {
			this.radiologist = radiologist;
		}

		public Date getLastChanged() {
			return lastChanged;
		}

		public void setLastChanged(Date lastChanged) {
			this.lastChanged = lastChanged;
		}

		public String getLastChangedBy() {
			return lastChangedBy;
		}

		public void setLastChangedBy(String lastChangedBy) {
			this.lastChangedBy = lastChangedBy;
		}
	}
}
