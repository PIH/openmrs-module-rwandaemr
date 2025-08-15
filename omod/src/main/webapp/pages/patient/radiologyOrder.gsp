<%
    ui.decorateWith("appui", "standardEmrPage")
%>

${ ui.includeFragment("coreapps", "patientHeader", [ patient: patient.patient ]) }

<style>
    label {
        font-weight: bold;
        padding-right: 10px;
    }
    label::after {
        content: ': ';
    }
</style>

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
                    jq().toastmessage('showErrorToast', result.errorMessage);
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

    const sendORMO01 = function(orderUuid) {
        jq.ajax({
            type: "GET",
            url: openmrsContextPath + '/ws/rest/v1/rwandaemr/hl7/' + orderUuid + '/orm001?action=send',
            dataType: 'json',
            success: function(result) {
                if (result.errorMessage) {
                    jq().toastmessage('showErrorToast', result.errorMessage);
                }
                else {
                    jq().toastmessage('showSuccessToast', 'Message sent successfully')
                }
            }
        });
    }

    jq(document).ready(function() {
        jq("#return-button").click(function(event) {
            document.location.href = '${ui.pageLink("rwandaemr", "patient/radiologyOrders", ["patientId": patient.id])}';
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

<h3>Radiology Order</h3>

<fieldset>
    <legend>Order Details</legend>
    <p>
        <label for="order-date">Order Date</label>
        <span id="order-date" class="field-value">${ ui.format(order.dateActivated) }</span>
    </p>
    <p>
        <label for="order-number">Order Number</label>
        <span id="order-number" class="field-value">${ ui.format(order.orderNumber) }</span>
    </p>
    <p>
        <label for="order-modality">Modality</label>
        <span id="order-modality" class="field-value">${ ui.format(orderables.get(order.concept)) }</span>
    </p>
    <p>
        <label for="order-study">Study</label>
        <span id="order-study" class="field-value">${ ui.format(order.concept) }</span>
    </p>
</fieldset>

<fieldset>
    <legend>Study Details</legend>
    <p>
        <label for="order-images">Images</label>
        <span id="order-images" class="field-value">
            <% if (imageUrl) { %>
            <a href="${ imageUrl }" target="_blank">View</a>
            <% } %>
        </span>
    </p>
</fieldset>

<fieldset>
    <legend>Report Details</legend>
    <p>
        <label for="order-report-date">Report Date</label>
        <span id="order-report-date" class="field-value">${ ui.format(reportDate) }</span>
    </p>
    <p>
        <label for="order-report-status">Report Status</label>
        <span id="order-report-status" class="field-value">${ ui.format(reportStatus) }</span>
    </p>
    <p>
        <label for="order-report">Report</label>
        <br/>
        <pre id="order-report" class="field-value">${ ui.format(reportText) }</pre>
    </p>
</fieldset>

<hr/>

<p>
    <a href="javascript:downloadORMO01('${order.uuid}')">
        Download ORM^OO1
    </a>
    <% if (!imageUrl && !reportDate) { %>
        <br/>
        <a href="javascript:sendORMO01('${order.uuid}')">
            Send ORM^OO1 to PACS
        </a>
    <% } %>
    <br/>
    <a href="javascript:downloadADTA08('${patient.patient.uuid}')">
        Download ADT^A08
    </a>
</p>

<br/><br/>
<div>
    <input id="return-button" type="button" class="cancel" value="${ ui.message("rwandaemr.encounterList.return") }"/>
</div>