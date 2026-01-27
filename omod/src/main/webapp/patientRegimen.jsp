<%@ include file="/WEB-INF/template/include.jsp"%>
<%@ include file="/WEB-INF/template/headerMinimal.jsp" %>
<%@ include file="/WEB-INF/view/module/orderextension/include/include.jsp"%>


<!-- Require the same privilege defined in the LabOrderDashboardTab extension -->
<openmrs:require privilege="Patient Dashboard - View Regimen Section" otherwise="/index.htm" redirect="/module/rwandaemr/patientRegimen.htm"/>

<openmrs:portlet url="patientRegimen" id="patientRegimen" moduleId="orderextension" patientId="${param.patientId}"/>

<%@ include file="/WEB-INF/template/footerMinimal.jsp" %>