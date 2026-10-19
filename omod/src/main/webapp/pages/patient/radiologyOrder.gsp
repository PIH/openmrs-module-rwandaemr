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
    .ro-cancelled { color: #9f1d1d; font-weight: bold; }
    .ro-draft { color: #0b5394; }
    #order-report .radiology-report { line-height: 1.5; }
    #order-report p { margin: 0 0 8px; }
    #order-report h3 { font-size: 17px; margin: 10px 0 6px; }
    #order-report h4 { font-size: 15px; margin: 8px 0 4px; }
    #order-report ul, #order-report ol { margin: 0 0 8px 22px; }
    #order-report .ra-center { text-align: center; }
    #order-report .ra-right { text-align: right; }
    #order-report .ra-justify { text-align: justify; }
    .ro-warning { color: #8a6d3b; }
    #cancel-form { display: none; border: 1px solid #f0c2c2; background: #fdf3f3; border-radius: 4px; padding: 10px 12px; margin-top: 8px; max-width: 640px; }
    #cancel-form label::after { content: ''; }
    #cancel-reason { width: 100%; box-sizing: border-box; min-height: 70px; }
    #cancel-error { display: none; color: #9f1d1d; font-weight: bold; margin: 4px 0; }
    table.ro-messages { width: 100%; font-size: 13px; }
    table.ro-messages td, table.ro-messages th { vertical-align: top; }
    table.ro-messages pre { white-space: pre-wrap; word-break: break-all; font-size: 12px; margin: 4px 0; max-width: 900px; }
    .ro-msg-ok { color: #1b5e20; font-weight: bold; }
    .ro-msg-bad { color: #9f1d1d; font-weight: bold; }
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
        jq("#cancel-button").click(function() {
            jq(this).hide();
            jq("#cancel-form").show();
            jq("#cancel-reason").focus();
        });
        jq("#cancel-back").click(function() {
            jq("#cancel-form").hide();
            jq("#cancel-button").show();
        });
        jq("#cancel-form").submit(function(e) {
            if (jq.trim(jq("#cancel-reason").val()) === '') {
                e.preventDefault();
                jq("#cancel-error").text('Please give the reason for cancelling the order.').show();
                return false;
            }
            if (!confirm('Cancel order ${ ui.encodeJavaScript(order.orderNumber) }? The cancellation will be sent to PACS.')) {
                e.preventDefault();
                return false;
            }
            jq("#cancel-confirm").prop('disabled', true).val('Cancelling...');
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
    <p>
        <label for="order-status">Status</label>
        <span id="order-status" class="field-value">
            <% if (order.dateStopped || order.voided) { %>
                <span class="ro-cancelled">Cancelled</span>
                ${ order.dateStopped ? "on " + ui.format(order.dateStopped) : "" }
                <% if (cancellation) { %>
                    ${ cancellation.orderer ? "by " + ui.encodeHtmlContent(ui.format(cancellation.orderer)) : "" }
                    <% if (cancellation.orderReasonNonCoded) { %>
                        &middot; Reason: ${ ui.encodeHtmlContent(cancellation.orderReasonNonCoded) }
                    <% } %>
                <% } %>
                <br/>
                <% if (cancellationSent) { %>
                    <span>The cancellation was sent to PACS.</span>
                <% } else if (order.accessionNumber) { %>
                    <span class="ro-warning">PACS has not confirmed the cancellation yet. Use "Send cancellation to PACS" below to send it again.</span>
                <% } %>
            <% } else { %>
                Active
            <% } %>
        </span>
    </p>
    <% if (canCancel) { %>
        <p>
            <input id="cancel-button" type="button" class="cancel" value="Cancel order"/>
        </p>
        <form id="cancel-form" method="post" action="${ ui.pageLink("rwandaemr", "patient/radiologyOrder") }">
            <input type="hidden" name="orderId" value="${ order.orderId }"/>
            <input type="hidden" name="action" value="cancel"/>
            <p><label for="cancel-reason">Reason for cancelling</label></p>
            <textarea id="cancel-reason" name="cancelReason" maxlength="255"></textarea>
            <div id="cancel-error"></div>
            <p>
                <input id="cancel-confirm" type="submit" class="button confirm" value="Cancel order"/>
                <input id="cancel-back" type="button" class="cancel" value="Keep order"/>
            </p>
        </form>
    <% } %>
</fieldset>

<fieldset>
    <legend>Study Details</legend>
    <p>
        <label for="order-images">Images</label>
        <span id="order-images" class="field-value">
            <% if (imageUrl) { %>
            <a href="${ ui.encodeHtmlAttribute(imageUrl) }" target="_blank" rel="noopener noreferrer" referrerpolicy="no-referrer">View</a>
            <% } else if (studyDone) { %>
            <span class="ro-warning">Study done &ndash; no image link received from PACS yet</span>
            <% } %>
        </span>
    </p>
</fieldset>

<fieldset>
    <legend>Report Details</legend>
    <p>
        <% if (canEnterReport) { %>
            <a class="button confirm" href="${ ui.pageLink("rwandaemr", "patient/radiologyReport", ["orderId": order.id]) }">
                ${ reportText ? "Correct report" : (draft ? "Continue draft" : "Enter report") }
            </a>
        <% } %>
        <% if (reportText) { %>
            <a class="button" target="_blank" rel="noopener" href="${ ui.pageLink("rwandaemr", "patient/radiologyReportPrint", ["orderId": order.id]) }">Print report</a>
        <% } %>
        <% if (draft && !reportText) { %>
            <span class="ro-draft">Draft saved ${ draft.lastSaved ? "on " + ui.format(draft.lastSaved) : "" } &middot; only radiologists can see it until it is saved as final.</span>
        <% } %>
        <% if (reportWaitingForImages) { %>
            <span style="color: #8a6d3b;">The report can be entered once the images are received from PACS.</span>
        <% } %>
    </p>
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
        <div id="order-report" class="field-value">${ reportHtml ?: "" }</div>
    </p>
</fieldset>

<fieldset>
    <legend>Messages with PACS</legend>
    <% if (!pacsMessages) { %>
        <p class="ro-warning">No messages with PACS have been recorded for this order.</p>
    <% } else { %>
        <table class="ro-messages">
            <thead>
            <tr>
                <th>Time</th>
                <th>Direction</th>
                <th>Message</th>
                <th>Order numbers</th>
                <th>Image link</th>
                <th>Outcome</th>
            </tr>
            </thead>
            <tbody>
            <% pacsMessages.each { m -> %>
            <tr>
                <td>${ ui.format(m.dateCreated) }</td>
                <td>${ m.direction == "IN" ? "From PACS" : "To PACS" }</td>
                <td>
                    ${ ui.encodeHtmlContent(m.messageType ?: "") }
                    ${ m.orderControl ? "(" + ui.encodeHtmlContent(m.orderControl == "CA" ? "cancel" : (m.orderControl == "NW" ? "new" : m.orderControl)) + ")" : "" }
                    ${ m.resultStatus ? "status " + ui.encodeHtmlContent(m.resultStatus) : "" }
                </td>
                <td>${ ui.encodeHtmlContent(m.orderNumbers ?: m.accessionNumber ?: "") }</td>
                <td>${ m.direction == "IN" ? (m.hasImageLink ? "Yes" : "<span class='ro-msg-bad'>No</span>") : "" }</td>
                <td>
                    <span class="${ m.status == 'PROCESSED' || m.status == 'ACCEPTED' ? 'ro-msg-ok' : 'ro-msg-bad' }">${ ui.encodeHtmlContent(m.status) }</span>
                    <% if (m.errorMessage) { %><div>${ ui.encodeHtmlContent(m.errorMessage) }</div><% } %>
                    <details>
                        <summary>Show message</summary>
                        <pre>${ ui.encodeHtmlContent(m.message ?: "") }</pre>
                        <% if (m.ackMessage) { %>
                            <strong>Answer</strong>
                            <pre>${ ui.encodeHtmlContent(m.ackMessage) }</pre>
                        <% } %>
                    </details>
                </td>
            </tr>
            <% } %>
            </tbody>
        </table>
    <% } %>
</fieldset>

<hr/>

<p>
    <a href="javascript:downloadORMO01('${order.uuid}')">
        Download ORM^OO1
    </a>
    <% def cancelled = order.dateStopped || order.voided
       if (!imageUrl && !reportDate && !cancellationSent && !(cancelled && !order.accessionNumber)) { %>
        <br/>
        <a href="javascript:sendORMO01('${order.uuid}')">
            ${ cancelled ? "Send cancellation to PACS" : "Send ORM^OO1 to PACS" }
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