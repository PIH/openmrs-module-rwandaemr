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
        overflow: scroll;
        border: none;
    }
</style>

${ ui.includeFragment("coreapps", "patientHeader", [ patient: patient ]) }

<h3>Drug Order</h3>

<iframe class="frame-content" scrolling="yes" src="/${ contextPath }/module/rwandaemr/drugOrders.htm?patientId=${ patient.patientId }"></iframe>
