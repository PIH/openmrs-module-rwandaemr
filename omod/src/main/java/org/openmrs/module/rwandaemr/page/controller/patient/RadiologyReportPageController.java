package org.openmrs.module.rwandaemr.page.controller.patient;

import org.openmrs.Order;
import org.openmrs.api.context.Context;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.emrapi.patient.PatientDomainWrapper;
import org.openmrs.module.rwandaemr.radiology.RadiologyReportDraftRepository;
import org.openmrs.module.rwandaemr.radiology.RadiologyReportService;
import org.openmrs.module.rwandaemr.radiology.RadiologyReportText;
import org.openmrs.module.uicommons.UiCommonsConstants;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.annotation.InjectBeans;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.PageModel;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lets a radiologist enter or correct the report of a radiology test whose modality does not send reports through PACS.
 * Each test has its own report, even when several tests were done as one PACS study.  A report can be saved as a draft,
 * which only radiologists see, and is visible to clinicians once saved as final.
 */
public class RadiologyReportPageController {

	public static final String ACTION_DRAFT = "draft";
	public static final String ACTION_FINAL = "final";
	public static final String ACTION_DISCARD_DRAFT = "discardDraft";

	public String get(PageModel model, UiUtils ui, UiSessionContext sessionContext,
					  @InjectBeans PatientDomainWrapper patientDomainWrapper,
					  @RequestParam(value = "orderId") Order order,
					  @RequestParam(value = "returnUrl", required = false) String returnUrl,
					  @SpringBean RadiologyReportService radiologyReportService) {

		String error = checkAccess(order, radiologyReportService);
		if (error != null) {
			sessionContext.getSession().setAttribute(UiCommonsConstants.SESSION_ATTRIBUTE_ERROR_MESSAGE, error);
			return "redirect:" + orderPage(ui, order);
		}
		patientDomainWrapper.setPatient(order.getPatient());
		model.addAttribute("patient", patientDomainWrapper);
		model.addAttribute("order", order);

		// The other tests done in the same PACS study share its images, but each has its own report
		List<Order> otherTestsInStudy = new ArrayList<Order>(radiologyReportService.getStudyOrders(order));
		otherTestsInStudy.remove(order);
		model.addAttribute("otherTestsInStudy", otherTestsInStudy);

		RadiologyReportService.RadiologyReport report = radiologyReportService.getReport(order);
		RadiologyReportDraftRepository.Draft draft = radiologyReportService.getDraft(order);
		model.addAttribute("report", report);
		model.addAttribute("draft", draft);
		model.addAttribute("draftSavedBy", draft == null || draft.getLastSavedBy() == null ? null :
				getUserName(draft.getLastSavedBy()));
		String text = draft != null ? draft.getText() : (report != null ? report.getText() : null);
		model.addAttribute("editorHtml", RadiologyReportText.toEditorHtml(text));
		model.addAttribute("radiologist", radiologyReportService.getCurrentProvider());
		model.addAttribute("imageUrl", safeImageUrl(radiologyReportService.getImageUrl(order)));
		model.addAttribute("returnUrl", safeReturnUrl(returnUrl, ui, order));
		return null;
	}

	public String post(UiUtils ui, UiSessionContext sessionContext,
					   @RequestParam(value = "orderId") Order order,
					   @RequestParam(value = "action", required = false) String action,
					   @RequestParam(value = "reportText", required = false) String reportText,
					   @RequestParam(value = "returnUrl", required = false) String returnUrl,
					   @SpringBean RadiologyReportService radiologyReportService) {

		String error = checkAccess(order, radiologyReportService);
		if (error == null) {
			try {
				String message;
				boolean stayOnPage = false;
				if (ACTION_DRAFT.equals(action)) {
					radiologyReportService.saveDraft(order, reportText);
					message = "Draft saved. Clinicians will see the report once it is saved as final.";
					stayOnPage = true;
				}
				else if (ACTION_DISCARD_DRAFT.equals(action)) {
					radiologyReportService.discardDraft(order);
					message = "Draft discarded";
					stayOnPage = true;
				}
				else {
					radiologyReportService.saveReport(order, reportText);
					message = "Report saved as final";
				}
				sessionContext.getSession().setAttribute(UiCommonsConstants.SESSION_ATTRIBUTE_INFO_MESSAGE, message);
				sessionContext.getSession().setAttribute(UiCommonsConstants.SESSION_ATTRIBUTE_TOAST_MESSAGE, "true");
				if (!stayOnPage) {
					return "redirect:" + safeReturnUrl(returnUrl, ui, order);
				}
			}
			catch (RuntimeException e) {
				error = e.getMessage();
			}
		}
		if (error != null) {
			sessionContext.getSession().setAttribute(UiCommonsConstants.SESSION_ATTRIBUTE_ERROR_MESSAGE, error);
		}
		Map<String, Object> params = new HashMap<String, Object>();
		params.put("orderId", order.getOrderId());
		String reportPage = ui.pageLink("rwandaemr", "patient/radiologyReport", params);
		if (returnUrl != null) {
			try {
				reportPage += (reportPage.contains("?") ? "&" : "?") + "returnUrl="
						+ URLEncoder.encode(safeReturnUrl(returnUrl, ui, order), "UTF-8");
			}
			catch (UnsupportedEncodingException e) {
				throw new IllegalStateException(e);
			}
		}
		return "redirect:" + reportPage;
	}

	private String checkAccess(Order order, RadiologyReportService radiologyReportService) {
		if (!Context.hasPrivilege(RadiologyReportService.PRIVILEGE_ENTER_REPORT)) {
			return "You do not have permission to enter radiology reports.";
		}
		return radiologyReportService.getReasonReportCannotBeEntered(order);
	}

	private String getUserName(Integer userId) {
		org.openmrs.User user = Context.getUserService().getUser(userId);
		if (user == null) {
			return null;
		}
		return user.getPersonName() != null ? user.getPersonName().getFullName() : user.getUsername();
	}

	/**
	 * @return the link only if it is a web address, so a link can never run script in the page
	 */
	private String safeImageUrl(String url) {
		if (url == null) {
			return null;
		}
		String lower = url.trim().toLowerCase();
		return lower.startsWith("http://") || lower.startsWith("https://") ? url.trim() : null;
	}

	/**
	 * Only return to pages within OpenMRS, never to another site
	 */
	private String safeReturnUrl(String returnUrl, UiUtils ui, Order order) {
		if (returnUrl != null && returnUrl.startsWith("/") && !returnUrl.startsWith("//")) {
			return returnUrl;
		}
		return orderPage(ui, order);
	}

	private String orderPage(UiUtils ui, Order order) {
		Map<String, Object> params = new HashMap<String, Object>();
		params.put("orderId", order.getOrderId());
		return ui.pageLink("rwandaemr", "patient/radiologyOrder", params);
	}
}
