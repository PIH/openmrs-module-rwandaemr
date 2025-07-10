<%
    ui.decorateWith("appui", "standardEmrPage")
%>

${ ui.includeFragment("coreapps", "patientHeader", [ patient: patient.patient ]) }

<script type="text/javascript">
    var breadcrumbs = [
        { icon: "icon-home", link: '/' + OPENMRS_CONTEXT_PATH + '/index.htm' },
        { label: '${ui.encodeJavaScript(ui.encodeHtmlContent(ui.format(patient.patient)))}', link: '${ui.pageLink("coreapps", "clinicianfacing/patient", ["patientId": patient.id])}' },
        { label: "Radiology Orders" }
    ];

    jq(document).ready(function() {
        jq("#return-button").click(function(event) {
            document.location.href = '${ui.pageLink("coreapps", "clinicianfacing/patient", ["patientId": patient.id])}';
        });
        jq("#add-button").click(function(event) {
            document.location.href = '${ui.pageLink("htmlformentryui", "htmlform/enterHtmlFormWithStandardUi", [
                "patientId": patient.id,
                "definitionUiResource": "file:configuration/htmlforms/radiology-order.xml",
                "returnUrl": ui.pageLink("rwandaemr", "patient/radiologyOrders", ["patientId": patient.id])
            ])}';
        });
    });
</script>

<h3>Radiology Orders</h3>

<div style="width:100%; text-align: right; padding-bottom: 5px;">
    <button id="add-button">${ui.message("rwandaemr.drugAdministrations.add")}</button>
</div>

<table id="radiology-order-table">
    <thead>
    <tr>
        <th>Order Date</th>
        <th>Order Number</th>
        <th>Study</th>
        <th>Images</th>
        <th>Report Date</th>
        <th>Report</th>
        <th>Status</th>
    </tr>
    </thead>
    <tbody>
    <% if (orders.size() == 0) { %>
    <tr>
        <td colspan="5">${ ui.message("emr.none") }</td>
    </tr>
    <% } %>
    <% orders.each { order ->
        def result = results.get(order) %>
    <tr>
        <td>
            ${ ui.format(order.dateActivated) }
        </td>
        <td>
            <a href="${ui.pageLink("rwandaemr", "patient/radiologyOrder", ["orderId": order.id])}">
                ${ ui.format(order.orderNumber) }
            </a>
        </td>
        <td>
            ${ ui.format(order.concept) }
        </td>
        <td>
            <% def imageUrl = result.get("imageUrl")
            if (imageUrl) { %>
                <a href="${ imageUrl }" target="_blank">View</a>
            <% } %>
        </td>
        <td>
            ${ ui.format(result.get("reportDate")) }
        </td>
        <td>
            <% def reportText = result.get("reportText")
            if (reportText) { %>
                <a href="${ui.pageLink("rwandaemr", "patient/radiologyOrder", ["orderId": order.id])}">View</a>
            <% } %>
        </td>
        <td>
            ${ ui.format(result.get("reportStatus")) }
        </td>
    </tr>
    <% } %>
    </tbody>
</table>

<br/>
<a href="javascript:downloadADTA08('${patient.patient.uuid}')">
    Download ADT^A08
</a>
<br/><br/>
<div>
    <input id="return-button" type="button" class="cancel" value="${ ui.message("rwandaemr.encounterList.return") }"/>
</div>