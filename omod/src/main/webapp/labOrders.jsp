<%@ include file="/WEB-INF/template/include.jsp"%>

<%@ include file="/WEB-INF/template/headerMinimal.jsp" %>

<!-- Require the same privilege defined in the LabOrderDashboardTab extension -->
<openmrs:require privilege="Patient Dashboard - View Laboratory Order" otherwise="/index.htm" redirect="/module/rwandaemr/labOrders.htm"/>

<!-- We deliberately do not use the patientId attribute of the portlet tag to avoid loading unnecessary data -->
<openmrs:portlet url="labOrderPortlet" id="LaboratoryTabId" moduleId="laboratorymanagement" />

<%@ include file="/WEB-INF/template/footerMinimal.jsp" %>