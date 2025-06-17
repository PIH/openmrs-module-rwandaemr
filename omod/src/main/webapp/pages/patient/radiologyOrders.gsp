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
    const downloadADTA08 = function(patientUuid) {
        jq.ajax({
            type: "GET",
            url: openmrsContextPath + '/ws/rest/v1/rwandaemr/hl7/' + patientUuid + '/adta08',
            dataType: 'json',
            success: function(result) {
                if (result.errorMessage) {
                    alert(result.errorMessage);
                }
                else {
                    const blob = new Blob([result.hl7Message], {type: "text/plain"});
                    const url = window.URL.createObjectURL(blob);
                    const a = document.createElement('a');
                    a.style.display = 'none';
                    a.href = url;
                    a.download = "adtA08-" + patientUuid + ".txt";
                    document.body.appendChild(a);
                    a.click();
                    window.URL.revokeObjectURL(url);
                    document.body.removeChild(a);
                }
            }
        });
    };

    const downloadORMO01 = function(orderUuid) {
        jq.ajax({
            type: "GET",
            url: openmrsContextPath + '/ws/rest/v1/rwandaemr/hl7/' + orderUuid + '/orm001',
            dataType: 'json',
            success: function(result) {
                if (result.errorMessage) {
                    alert(result.errorMessage);
                }
                else {
                    const blob = new Blob([result.hl7Message], {type: "text/plain"});
                    const url = window.URL.createObjectURL(blob);
                    const a = document.createElement('a');
                    a.style.display = 'none';
                    a.href = url;
                    a.download = "orm001-" + orderUuid + ".txt";
                    document.body.appendChild(a);
                    a.click();
                    window.URL.revokeObjectURL(url);
                    document.body.removeChild(a);
                }
            }
        });
    };

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
        <th>Date Activated</th>
        <th>Order Number</th>
        <th>Modality</th>
        <th>Study</th>
        <th>Actions</th>
    </tr>
    </thead>
    <tbody>
    <% if (orders.size() == 0) { %>
    <tr>
        <td colspan="5">${ ui.message("emr.none") }</td>
    </tr>
    <% } %>
    <% orders.each { order -> %>
    <tr>
        <td>
            ${ ui.format(order.dateActivated) }
        </td>
        <td>
            ${ ui.format(order.orderNumber) }
        </td>
        <td>
            ${ ui.format(orderables.get(order.concept)) }
        </td>
        <td>
            ${ ui.format(order.concept) }
        </td>
        <td>
            <a href="javascript:downloadORMO01('${order.uuid}')">
                Download ORM^OO1
            </a>
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