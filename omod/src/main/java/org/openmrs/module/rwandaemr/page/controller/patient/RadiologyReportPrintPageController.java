package org.openmrs.module.rwandaemr.page.controller.patient;

import org.openmrs.Order;
import org.openmrs.PatientIdentifier;
import org.openmrs.TestOrder;
import org.openmrs.api.context.Context;
import org.openmrs.module.rwandaemr.RwandaEmrConfig;
import org.openmrs.module.rwandaemr.radiology.OrderToORMO01Translator;
import org.openmrs.module.rwandaemr.radiology.RadiologyReportService;
import org.openmrs.module.rwandaemr.radiology.RadiologyReportText;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.PageModel;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collections;

/**
 * Printable radiology report, for the patient or the requesting ward
 */
public class RadiologyReportPrintPageController {

	public void get(PageModel model,
					@RequestParam(value = "orderId") Order order,
					@SpringBean RadiologyReportService radiologyReportService,
					@SpringBean OrderToORMO01Translator orderTranslator,
					@SpringBean RwandaEmrConfig rwandaEmrConfig) {

		boolean authorized = Context.hasPrivilege(RadiologyReportService.PRIVILEGE_ENTER_REPORT)
				|| Context.hasPrivilege("Task: rwandaemr.radiologyorders")
				|| Context.hasPrivilege("Task: rwandaemr.radiologyQueue");
		model.addAttribute("authorized", authorized);
		if (!authorized) {
			return;
		}
		model.addAttribute("order", order);
		model.addAttribute("patient", order.getPatient());
		// Each test has its own report, even when several tests were done as one PACS study
		model.addAttribute("studyOrders", Collections.singletonList(order));
		RadiologyReportService.RadiologyReport report = radiologyReportService.getReport(order);
		model.addAttribute("report", report);
		model.addAttribute("reportHtml", report == null ? null : RadiologyReportText.toHtml(report.getText()));
		model.addAttribute("facility", order instanceof TestOrder && order.getEncounter() != null ?
				orderTranslator.getOrderLocation((TestOrder) order).getName() : null);
		PatientIdentifier identifier = rwandaEmrConfig.getPrimaryCareIdentifierType() == null ? null :
				order.getPatient().getPatientIdentifier(rwandaEmrConfig.getPrimaryCareIdentifierType());
		if (identifier == null) {
			identifier = order.getPatient().getPatientIdentifier();
		}
		model.addAttribute("patientIdentifier", identifier == null ? null : identifier.getIdentifier());
	}
}
