<%
    ui.decorateWith("appui", "standardEmrPage")
%>
<style>
    body {
        max-width: unset;
    }
    .frame-content {
        width: 100%;
        height: 2000px;
        overflow: hidden;
        border: none;
    }
</style>

<h3>Primary Care Registration</h3>

<iframe class="frame-content" scrolling="no" src="/${ contextPath }/module/rwandaemr/manualBilling.htm?patientId=${ patient.patientId }"></iframe>
