package org.openmrs.module.rwandaemr.labreports;

import org.openmrs.api.OpenmrsService;

import java.util.List;

public interface LabReportService extends OpenmrsService {

	List<AggregateLabReportRow> getAggregateLabReport(LabReportSearchCriteria criteria);

	List<LabTurnaroundTimeRow> getTurnaroundTimeRows(LabReportSearchCriteria criteria);

	LabTurnaroundTimeSummary getTurnaroundTimeSummary(LabReportSearchCriteria criteria);
}
