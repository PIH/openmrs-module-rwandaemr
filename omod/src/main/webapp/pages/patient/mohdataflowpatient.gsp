<%
    ui.decorateWith("appui", "standardEmrPage")
%>
<style>
    body {
        max-width: unset;
    }
    .frame-content {
        width: 100%;
        height: 70vh;
        overflow: hidden;
        border: none;
    }
</style>

${ ui.includeFragment("coreapps", "patientHeader", [ patient: patient ]) }

<h3>Medical History</h3>

<iframe class="frame-content" scrolling="yes" src="/${ contextPath }/module/rwandaemr/mohdataflowpatient.htm?patientId=${ patient.patientId }"></iframe>