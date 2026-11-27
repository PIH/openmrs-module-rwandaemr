package org.openmrs.module.rwandaemr.htmlformentry;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.api.APIException;
import org.openmrs.api.context.Context;
import org.openmrs.module.htmlformentry.CustomFormSubmissionAction;
import org.openmrs.module.htmlformentry.FormEntrySession;
import org.openmrs.module.rwandaemr.integration.insurance.MmiPatientReceptionContextHolder;
import org.openmrs.module.rwandaemr.integration.insurance.MmiPatientReceptionOrchestratorService;
import org.openmrs.module.rwandaemr.integration.insurance.MmiPatientReceptionResult;
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
					// The OTP the patient received from /eligibility-check; MMI checks it when the reception opens
					otpCode = request.getParameter("mmiOtpCode");
				}
			}
			MmiPatientReceptionContextHolder.setSubmittedOtpCode(otpCode);
			MmiPatientReceptionOrchestratorService service =
					Context.getRegisteredComponents(MmiPatientReceptionOrchestratorService.class).get(0);
			MmiPatientReceptionResult result = service.createMmiReceptionForRegistration(session);
			if (result != null && result.isReceptionRequired() && !result.isSuccessful()) {
				throw new APIException(StringUtils.defaultIfEmpty(result.getMessage(),
						"Cannot start this visit because no MMI reception number was generated today."));
			}
		}
		catch (APIException e) {
			throw e;
		}
		catch (Exception e) {
			// Keep non-MMI registration resilient if the reception helper cannot run.
			log.error("Unable to orchestrate MMI patient reception from registration form submission", e);
		}
		finally {
			MmiPatientReceptionContextHolder.clear();
		}
	}
}
