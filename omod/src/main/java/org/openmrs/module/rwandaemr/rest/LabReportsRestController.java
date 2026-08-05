package org.openmrs.module.rwandaemr.rest;

import org.openmrs.module.rwandaemr.labreports.AggregateLabReportRow;
import org.openmrs.module.rwandaemr.labreports.LabReportSearchCriteria;
import org.openmrs.module.rwandaemr.labreports.LabReportService;
import org.openmrs.module.rwandaemr.labreports.LabTurnaroundTimeRow;
import org.openmrs.module.rwandaemr.labreports.LabTurnaroundTimeSummary;
import org.openmrs.module.webservices.rest.web.RestUtil;
import org.openmrs.module.webservices.rest.web.response.ResponseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/rest/v1/rwandaemr/labReports")
public class LabReportsRestController {

	private static final SimpleDateFormat DISPLAY_DATE_FORMAT = new SimpleDateFormat("dd/MM/yyyy");

	private static final SimpleDateFormat ISO_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

	private static final SimpleDateFormat OUTPUT_DATE_FORMAT = new SimpleDateFormat("dd/MM/yyyy HH:mm");

	@Autowired
	private LabReportService labReportService;

	@RequestMapping(value = "/aggregate", method = RequestMethod.GET)
	@ResponseBody
	public Object getAggregateReport(HttpServletRequest request, HttpServletResponse response,
	                                 @RequestParam(value = "startDate") String startDate,
	                                 @RequestParam(value = "endDate") String endDate,
	                                 @RequestParam(value = "locationId", required = false) Integer locationId,
	                                 @RequestParam(value = "categoryConceptId", required = false) Integer categoryConceptId,
	                                 @RequestParam(value = "testConceptId", required = false) Integer testConceptId,
	                                 @RequestParam(value = "resultType", required = false) String resultType) throws ResponseException {
		try {
			LabReportSearchCriteria criteria = criteria(startDate, endDate, locationId, categoryConceptId, testConceptId, resultType, null);
			Map<String, Object> ret = new LinkedHashMap<String, Object>();
			ret.put("results", aggregateRows(labReportService.getAggregateLabReport(criteria)));
			return ret;
		}
		catch (Exception e) {
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			return RestUtil.wrapErrorResponse(e, e.getMessage());
		}
	}

	@RequestMapping(value = "/aggregate/export", method = RequestMethod.GET)
	public void exportAggregateReport(HttpServletResponse response,
	                                  @RequestParam(value = "startDate") String startDate,
	                                  @RequestParam(value = "endDate") String endDate,
	                                  @RequestParam(value = "locationId", required = false) Integer locationId,
	                                  @RequestParam(value = "categoryConceptId", required = false) Integer categoryConceptId,
	                                  @RequestParam(value = "testConceptId", required = false) Integer testConceptId,
	                                  @RequestParam(value = "resultType", required = false) String resultType) throws IOException {
		try {
			LabReportSearchCriteria criteria = criteria(startDate, endDate, locationId, categoryConceptId, testConceptId, resultType, null);
			response.setContentType("text/csv");
			response.setHeader("Content-Disposition", "attachment; filename=\"lab-aggregate-report.csv\"");
			response.getWriter().println("Category,Test,Positive,Negative,Total");
			for (AggregateLabReportRow row : labReportService.getAggregateLabReport(criteria)) {
				response.getWriter().println(csv(row.getCategory()) + "," + csv(row.getTest()) + "," +
						value(row.getPositiveCount()) + "," + value(row.getNegativeCount()) + "," + value(row.getTotalResultedCount()));
			}
		}
		catch (Exception e) {
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			response.getWriter().println(e.getMessage());
		}
	}

	@RequestMapping(value = "/tat/details", method = RequestMethod.GET)
	@ResponseBody
	public Object getTatDetails(HttpServletRequest request, HttpServletResponse response,
	                            @RequestParam(value = "startDate") String startDate,
	                            @RequestParam(value = "endDate") String endDate,
	                            @RequestParam(value = "locationId", required = false) Integer locationId,
	                            @RequestParam(value = "categoryConceptId", required = false) Integer categoryConceptId,
	                            @RequestParam(value = "testConceptId", required = false) Integer testConceptId,
	                            @RequestParam(value = "targetHours", required = false) Double targetHours) throws ResponseException {
		try {
			LabReportSearchCriteria criteria = criteria(startDate, endDate, locationId, categoryConceptId, testConceptId, null, targetHours);
			Map<String, Object> ret = new LinkedHashMap<String, Object>();
			ret.put("results", tatRows(labReportService.getTurnaroundTimeRows(criteria)));
			return ret;
		}
		catch (Exception e) {
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			return RestUtil.wrapErrorResponse(e, e.getMessage());
		}
	}

	@RequestMapping(value = "/tat/summary", method = RequestMethod.GET)
	@ResponseBody
	public Object getTatSummary(HttpServletRequest request, HttpServletResponse response,
	                            @RequestParam(value = "startDate") String startDate,
	                            @RequestParam(value = "endDate") String endDate,
	                            @RequestParam(value = "locationId", required = false) Integer locationId,
	                            @RequestParam(value = "categoryConceptId", required = false) Integer categoryConceptId,
	                            @RequestParam(value = "testConceptId", required = false) Integer testConceptId,
	                            @RequestParam(value = "targetHours", required = false) Double targetHours) throws ResponseException {
		try {
			LabReportSearchCriteria criteria = criteria(startDate, endDate, locationId, categoryConceptId, testConceptId, null, targetHours);
			return summary(labReportService.getTurnaroundTimeSummary(criteria));
		}
		catch (Exception e) {
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			return RestUtil.wrapErrorResponse(e, e.getMessage());
		}
	}

	private LabReportSearchCriteria criteria(String startDate, String endDate, Integer locationId, Integer categoryConceptId,
	                                         Integer testConceptId, String resultType, Double targetHours) throws Exception {
		LabReportSearchCriteria criteria = new LabReportSearchCriteria();
		criteria.setStartDate(parseDate(startDate));
		criteria.setEndDate(parseDate(endDate));
		criteria.setLocationId(locationId);
		criteria.setCategoryConceptId(categoryConceptId);
		criteria.setTestConceptId(testConceptId);
		criteria.setResultType(resultType);
		criteria.setTargetHours(targetHours);
		return criteria;
	}

	private Date parseDate(String date) throws Exception {
		try {
			return DISPLAY_DATE_FORMAT.parse(date);
		}
		catch (ParseException e) {
			return ISO_DATE_FORMAT.parse(date);
		}
	}

	private List<Map<String, Object>> aggregateRows(List<AggregateLabReportRow> rows) {
		List<Map<String, Object>> ret = new ArrayList<Map<String, Object>>();
		for (AggregateLabReportRow row : rows) {
			Map<String, Object> map = new LinkedHashMap<String, Object>();
			map.put("categoryConceptId", row.getCategoryConceptId());
			map.put("category", row.getCategory());
			map.put("testConceptId", row.getTestConceptId());
			map.put("test", row.getTest());
			map.put("positiveCount", row.getPositiveCount());
			map.put("negativeCount", row.getNegativeCount());
			map.put("totalResultedCount", row.getTotalResultedCount());
			ret.add(map);
		}
		return ret;
	}

	private List<Map<String, Object>> tatRows(List<LabTurnaroundTimeRow> rows) {
		List<Map<String, Object>> ret = new ArrayList<Map<String, Object>>();
		for (LabTurnaroundTimeRow row : rows) {
			Map<String, Object> map = new LinkedHashMap<String, Object>();
			map.put("orderId", row.getOrderId());
			map.put("orderUuid", row.getOrderUuid());
			map.put("test", row.getTest());
			map.put("location", row.getLocation());
			map.put("urgency", row.getUrgency());
			map.put("fulfillerStatus", row.getFulfillerStatus());
			map.put("orderedAt", format(row.getOrderedAt()));
			map.put("specimenReceivedAt", format(row.getSpecimenReceivedAt()));
			map.put("resultedAt", format(row.getResultedAt()));
			map.put("orderToReceivedHours", row.getOrderToReceivedHours());
			map.put("receivedToResultHours", row.getReceivedToResultHours());
			map.put("orderToResultHours", row.getOrderToResultHours());
			ret.add(map);
		}
		return ret;
	}

	private Map<String, Object> summary(LabTurnaroundTimeSummary summary) {
		Map<String, Object> ret = new LinkedHashMap<String, Object>();
		ret.put("totalOrders", summary.getTotalOrders());
		ret.put("completedOrders", summary.getCompletedOrders());
		ret.put("delayedOrders", summary.getDelayedOrders());
		ret.put("averageHours", summary.getAverageHours());
		ret.put("medianHours", summary.getMedianHours());
		ret.put("percentile90Hours", summary.getPercentile90Hours());
		ret.put("percentWithinTarget", summary.getPercentWithinTarget());
		return ret;
	}

	private String format(Date date) {
		return date == null ? null : OUTPUT_DATE_FORMAT.format(date);
	}

	private String value(Object value) {
		return value == null ? "" : value.toString();
	}

	private String csv(String value) {
		if (value == null) {
			return "";
		}
		return "\"" + value.replace("\"", "\"\"") + "\"";
	}
}
