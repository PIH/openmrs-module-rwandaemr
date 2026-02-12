<%@ include file="/WEB-INF/template/include.jsp"%>
<%@ include file="/WEB-INF/template/headerMinimal.jsp" %>

<!-- Require the same privilege defined in the mohdataflowtab extension -->
<openmrs:require privilege="View HMIS Data Flow" otherwise="/index.htm" redirect="/module/rwandaemr/mohdataflowpatient.htm"/>

<!-- We deliberately do not use the patientId attribute of the portlet tag to avoid loading unnecessary data -->
<openmrs:portlet url="mohdataflowpatient" id="mohdataflowpatient" moduleId="mohdataflowmodule" />

<%@ include file="/WEB-INF/template/footerMinimal.jsp" %>