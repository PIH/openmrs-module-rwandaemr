<%
    ui.decorateWith("appui", "standardEmrPage")
%>
<style>
    body {
        max-width: 1000px;
    }
    .frame-content {
        width: 100%;
        height: 70vh;
        overflow: auto;
        border: none;
    }
</style>

${ ui.includeFragment("coreapps", "patientHeader", [ patient: patient ]) }

<h3>Regimens</h3>

<iframe class="frame-content" scrolling="yes" src="/${ contextPath }/module/rwandaemr/patientRegimen.htm?patientId=${ patient.patientId }"></iframe>