<%@ include file="/WEB-INF/template/include.jsp"%>

<%@ include file="/WEB-INF/template/headerMinimal.jsp" %>

<!-- Require the same privilege defined in the billtab extension -->
<openmrs:require privilege="Patient Dashboard - View Billing Section" otherwise="/index.htm" redirect="/module/rwandaemr/manualBilling.htm"/>

<!-- We deliberately do not use the patientId attribute of the portlet tag to avoid loading unnecessary data -->
<openmrs:portlet url="billingPortlet" id="billing" moduleId="mohbilling" />

<%@ include file="/WEB-INF/template/footerMinimal.jsp" %>