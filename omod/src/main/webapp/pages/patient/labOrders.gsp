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
        overflow: auto;
        border: none;
    }
</style>

${ ui.includeFragment("coreapps", "patientHeader", [ patient: patient ]) }

<h3>Laboratory Order</h3>

<iframe class="frame-content" scrolling="yes" src="/${ contextPath }/module/rwandaemr/labOrders.htm?patientId=${ patient.patientId }"></iframe>
