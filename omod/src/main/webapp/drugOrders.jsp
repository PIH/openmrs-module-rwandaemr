<%@ include file="/WEB-INF/template/include.jsp"%>
<%@ include file="/WEB-INF/template/headerMinimal.jsp" %>

<openmrs:htmlInclude file="/scripts/jquery/dataTables/css/dataTables.css" />
<openmrs:htmlInclude file="/moduleResources/laboratorymanagement/scripts/jquery.dataTables1.js" />

<openmrs:htmlInclude file="/scripts/jquery-ui/js/jquery-ui-1.7.2.custom.min.js" />
<link href="<openmrs:contextPath/>/scripts/jquery-ui/css/<spring:theme code='jqueryui.theme.name' />/jquery-ui.custom.css" type="text/css" rel="stylesheet" />
<openmrs:htmlInclude file="/scripts/calendar/calendar.js" />

<!-- Require the same privilege defined in the DrugOrderDashboardTab extension -->
<openmrs:require privilege="Patient Dashboard - View Drug Order Section" otherwise="/index.htm" redirect="/module/rwandaemr/drugOrders.htm"/>

<!-- We deliberately do not use the patientId attribute of the portlet tag to avoid loading unnecessary data -->
<openmrs:portlet url="drugOrderPortlet" id="DrugOrderTabId" moduleId="pharmacymanagement" patientId="${patient.patientId}" parameters="returnUrl=${pageContext.request.contextPath}/module/rwandaemr/drugOrders.htm?patientId={patientId}"/>

<%@ include file="/WEB-INF/template/footerMinimal.jsp" %>