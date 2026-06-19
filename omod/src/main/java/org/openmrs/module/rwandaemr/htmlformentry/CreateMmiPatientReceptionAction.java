package org.openmrs.module.rwandaemr.htmlformentry;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Encounter;
import org.openmrs.api.context.Context;
import org.openmrs.module.htmlformentry.CustomFormSubmissionAction;
import org.openmrs.module.htmlformentry.FormEntrySession;
import org.openmrs.module.rwandaemr.integration.insurance.MmiPatientReceptionContextHolder;
import org.openmrs.module.rwandaemr.integration.insurance.MmiPatientReceptionOrchestratorService;
import org.openmrs.module.rwandaemr.session.MmiOtpSessionStore;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

public class CreateMmiPatientReceptionAction implements CustomFormSubmissionAction {

	protected Log log = LogFactory.getLog(getClass());

	@Override
	public void applyAction(FormEntrySession session) {
		try {
			String otpCode = null;
			RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
			if (requestAttributes instanceof ServletRequestAttributes) {
				HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
				if (request != null) {
					otpCode = request.getParameter("mmiOtpCode");
					if (StringUtils.isBlank(otpCode)) {
						Integer patientId = resolvePatientId(session);
						otpCode = MmiOtpSessionStore.getOtp(request, patientId, null);
					}
				}
			}
			MmiPatientReceptionContextHolder.setSubmittedOtpCode(otpCode);
			MmiPatientReceptionOrchestratorService service =
					Context.getRegisteredComponents(MmiPatientReceptionOrchestratorService.class).get(0);
			service.createMmiReceptionForRegistration(session);
		}
		catch (Exception e) {
			// Non-blocking by design: registration should complete even when MMI patient reception fails.
			log.error("Unable to orchestrate MMI patient reception from registration form submission", e);
		}
		finally {
			MmiPatientReceptionContextHolder.clear();
		}
	}

	private Integer resolvePatientId(FormEntrySession session) {
		if (session == null || session.getEncounter() == null) {
			return null;
		}
		Encounter encounter = session.getEncounter();
		return encounter.getPatient() == null ? null : encounter.getPatient().getPatientId();
	}
}
