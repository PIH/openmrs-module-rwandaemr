package org.openmrs.module.rwandaemr.htmlformentry;

import org.apache.commons.lang.StringUtils;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.api.context.Context;
import org.openmrs.module.htmlformentry.CustomFormSubmissionAction;
import org.openmrs.module.htmlformentry.FormEntryContext;
import org.openmrs.module.htmlformentry.FormEntrySession;
import org.openmrs.module.rwandaemr.pathology.PathologyCaseService;
import org.openmrs.module.rwandaemr.pathology.PathologyStatus;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Once HTML Form Entry has saved a pathology report, moves its case to the steps the report has results for
 */
public class PathologyReportSubmissionAction implements CustomFormSubmissionAction {

	private final Integer requestEncounterId;

	/**
	 * The questions of the report whose answers show that each step was done
	 */
	private final Map<PathologyStatus, Set<Concept>> resultsOfSteps;

	public PathologyReportSubmissionAction(Integer requestEncounterId, Map<PathologyStatus, Set<Concept>> resultsOfSteps) {
		this.requestEncounterId = requestEncounterId;
		this.resultsOfSteps = resultsOfSteps;
	}

	@Override
	public void applyAction(FormEntrySession session) {
		FormEntryContext.Mode mode = session.getContext().getMode();
		if (mode != FormEntryContext.Mode.ENTER && mode != FormEntryContext.Mode.EDIT) {
			return;
		}
		Encounter report = session.getEncounter();
		if (report == null) {
			throw new IllegalStateException("Unable to record the pathology report without an encounter");
		}
		Encounter request = Context.getEncounterService().getEncounter(requestEncounterId);
		if (request == null) {
			throw new IllegalStateException("Pathology request " + requestEncounterId + " not found");
		}
		getPathologyCaseService().recordReport(request, report, getStepsWithResults(report.getAllObs(false)));
	}

	/**
	 * @return the steps that the given observations of the report have results for
	 */
	Set<PathologyStatus> getStepsWithResults(Collection<Obs> observations) {
		Set<PathologyStatus> ret = EnumSet.noneOf(PathologyStatus.class);
		for (Obs obs : observations) {
			if (obs.getVoided() || obs.isObsGrouping() || !hasValue(obs)) {
				continue;
			}
			for (Map.Entry<PathologyStatus, Set<Concept>> step : resultsOfSteps.entrySet()) {
				if (step.getValue().contains(obs.getConcept())) {
					ret.add(step.getKey());
				}
			}
		}
		return ret;
	}

	private boolean hasValue(Obs obs) {
		return obs.getValueCoded() != null || obs.getValueNumeric() != null || obs.getValueDatetime() != null
				|| obs.getValueDrug() != null || StringUtils.isNotBlank(obs.getValueText())
				|| StringUtils.isNotBlank(obs.getValueComplex());
	}

	protected PathologyCaseService getPathologyCaseService() {
		return Context.getRegisteredComponents(PathologyCaseService.class).get(0);
	}
}
