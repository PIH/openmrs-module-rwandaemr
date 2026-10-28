<%
    ui.decorateWith("appui", "standardEmrPage")
%>

${ ui.includeFragment("coreapps", "patientHeader", [ patient: patient.patient ]) }

<script type="text/javascript">
    var breadcrumbs = [
        { icon: "icon-home", link: '/' + OPENMRS_CONTEXT_PATH + '/index.htm' },
        { label: '${ui.encodeJavaScript(ui.encodeHtmlContent(ui.format(patient.patient)))}', link: '${ui.pageLink("coreapps", "clinicianfacing/patient", ["patientId": patient.id])}' },
        { label: "Pathology Orders" }
    ];

    jq(document).ready(function() {
        jq("#return-button").click(function(event) {
            document.location.href = '${ui.pageLink("coreapps", "clinicianfacing/patient", ["patientId": patient.id])}';
        });
        jq("#add-button").click(function(event) {
            document.location.href = '${ui.pageLink("htmlformentryui", "htmlform/enterHtmlFormWithStandardUi", [
                "patientId": patient.id,
                "definitionUiResource": requestForm,
                "returnUrl": ui.pageLink("rwandaemr", "patient/pathologyOrders", ["patientId": patient.id])
            ])}';
        });
    });
</script>

<style>
    .po-chip { display: inline-block; padding: 2px 8px; border-radius: 12px; font-weight: bold; font-size: 11.5px; white-space: nowrap; color: #455a64; background: #eceff1; }
    .po-SIGNED_OUT { color: #1b5e20; background: #dff0d8; }
    .po-RECEIVED, .po-GROSSED, .po-PROCESSED, .po-ADDITIONAL_STAINS { color: #0b5394; background: #d9edf7; }
    .po-REJECTED, .po-CANCELLED { color: #9f1d1d; background: #f8d7da; }
    .po-ORDERED { color: #8a6d3b; background: #fcf8e3; }
    .po-urgent { color: #fff; background: #e53935; font-size: 10.5px; padding: 1px 6px; margin-right: 4px; }
    .po-muted { color: #78909c; font-size: 12px; }
</style>

<h3>Pathology Orders</h3>

<div style="width:100%; text-align: right; padding-bottom: 5px;">
    <button id="add-button">${ui.message("rwandaemr.drugAdministrations.add")}</button>
</div>

<table id="pathology-order-table">
    <thead>
    <tr>
        <th>Requested</th>
        <th>Accession Number</th>
        <th>Tests</th>
        <th>Requested By</th>
        <th>Payment</th>
        <th>Status</th>
        <th>Request Form</th>
    </tr>
    </thead>
    <tbody>
    <% if (requests.size() == 0) { %>
    <tr>
        <td colspan="7">${ ui.message("emr.none") }</td>
    </tr>
    <% } %>
    <% requests.each { request ->
        def status = request.cancelled ? "CANCELLED" : request.status.name() %>
    <tr>
        <td>
            ${ ui.format(request.timeRequested) }
        </td>
        <td>
            ${ ui.encodeHtmlContent(request.accessionNumber ?: "") }
        </td>
        <td>
            <% if (request.urgent) { %><span class="po-chip po-urgent">URGENT</span><% } %>
            ${ ui.encodeHtmlContent(request.tests.join(", ")) }
            <div class="po-muted">${ ui.encodeHtmlContent(request.orderNumbers.join(", ")) }</div>
        </td>
        <td>
            ${ ui.encodeHtmlContent(request.requestingPhysician ?: "") }
        </td>
        <td>
            ${ ui.encodeHtmlContent(request.paymentStatus ? request.paymentStatus.label : "") }
        </td>
        <td>
            <span class="po-chip po-${ status }">${ ui.encodeHtmlContent(request.statusLabel) }</span>
            <% if (request.rejectionReason) { %>
                <div class="po-muted">${ ui.encodeHtmlContent(request.rejectionReason) }</div>
            <% } %>
        </td>
        <td>
            <a href="${ ui.pageLink("htmlformentryui", "htmlform/viewEncounterWithHtmlForm", [
                    "patientId": patient.patient.uuid,
                    "encounter": request.encounterUuid,
                    "returnUrl": ui.pageLink("rwandaemr", "patient/pathologyOrders", ["patientId": patient.id])]) }">View</a>
        </td>
    </tr>
    <% } %>
    </tbody>
</table>

<br/>
<div>
    <input id="return-button" type="button" class="cancel" value="${ ui.message("rwandaemr.encounterList.return") }"/>
    <% if (canSeeQueue) { %>
        <a href="${ ui.pageLink("rwandaemr", "pathology/pathologyQueue") }" style="margin-left: 12px;">Pathology Queue &rarr;</a>
    <% } %>
</div>
