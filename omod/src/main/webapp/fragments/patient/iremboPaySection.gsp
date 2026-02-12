<%
ui.includeJavascript("rwandaemr", "custom/hie.js")
ui.includeCss("rwandaemr", "hie/hie.css")
%>

<div id="payment-view-dialog" class="dialog" style="display: none">
    <div class="dialog-header">
        <i class="icon-check-in"></i>
        <h3 id="payment_request_title">
            ${ ui.message("Pay using Irembo Pay") }
        </h3>
    </div>
    <div class="dialog-content">
        <div id="payment-data"></div>
        <button type="button" id="irembo-pay-confirm-btn" class="confirm right">${ ui.message("rwandaemr.hie.done") }<i class="icon-spinner icon-spin icon-2x" style="display: none; margin-left: 10px;"></i></button>
        <button type="button" class="cancel">${ ui.message("coreapps.cancel") }</button>
    </div>
</div>
<div class="info-section">
    <div class="info-header">
        <i class="icon-calendar"></i>
        <h3>${ ui.message(config.label ? config.label : "Billing Information").toUpperCase() }</h3>
        
    </div>
    <div class="info-body">
        <g:if test="${error}">
            <span style="color: red;">${error}</span>
        </g:if>
        <%
        if(bills.size() > 0) {
            %>
            <table id="bills-list-table">
                <thead>
                    <tr>
                        <th>${ ui.message("Service") }</th>
                        <th>${ ui.message("Amount") }</th>
                        <th>${ ui.message("Invoice Number") }</th>
                    </tr>
                </thead>
                <tbody>
                    <% bills.each { bill ->
                        def pageLink
                        %>
                        <tr id="bill-${ bill.getPatientBillId() }" class="bill-row${pageLink ? ' pointer' :''}" data-href="#">
                            <td>
                                ${ ui.format(bill.getDepartment()) }
                            </td>
                            <td>
                                ${ ui.format(bill.getAmount()) }
                            </td>
                            <td>
                                ${ ui.format(bill.getInvoiceNumber()) }
                                <% if (!bill.getInvoiceNumber() || bill.getInvoiceNumber().trim().isEmpty()) { %>
                                <a class="open_payment_request_pop" data-bill_amount="${ ui.format(bill.getAmount()) }" data-bill_id="${ ui.format(bill.getPatientBillId()) }" data-invoice_number="${ ui.format(bill.getInvoiceNumber()) }" data-url='${ui.pageLink("rwandaemr", "patient/iremboPayStatusSection")}?billId=${bill.getPatientBillId()}&phoneNumber=${bill.getPhoneNumber()}'" title="Create Payment Request" href="javascript:hie.showPaymentViewDialog('${bill.getPatientBillId()}')"><i class="icon-share-alt right"></i></a>
                                <% } else { %>
                                <span class="irembo-waiting-payment" data-bill_amount="${ ui.format(bill.getAmount()) }" data-bill_id="${ bill.getPatientBillId() }" data-invoice_number="${ ui.format(bill.getInvoiceNumber()) }" data-url='${ui.pageLink("rwandaemr", "patient/iremboPayStatusSection")}?billId=${bill.getPatientBillId()}&phoneNumber=${bill.getPhoneNumber()}'" data-invoice-number="${ ui.format(bill.getInvoiceNumber()) }" data-bill-id="${ bill.getPatientBillId() }" data-phone-number="${ bill.getPhoneNumber() ?: '' }" data-check-count="0" title="${ ui.message('rwandaemr.billing.waitingPayment') }"><i class="icon-spinner icon-spin"></i> ${ ui.message('rwandaemr.billing.waitingPayment') }</span>
                                <% } %>
                            </td>
                        </tr>
                        <% 
                    }
                    %>
                </tbody>
            </table>
            <%
        }
        %>
    </div>
</div>

<script type="text/javascript">
    jq(document).on("click", ".open_payment_request_pop", function (e) {
        e.preventDefault();
        var clicked = jq(this);
        var url = clicked.attr("data-url");
        if (url) {
            var fullUrl = window.location.origin + url;
            var title = clicked.data("encounter_type");
            if (!title) {
                title = "${ ui.message('Pay using Irembo Pay') }";
            }
            jq("#payment_request_title").html(title);
            jq("#payment-data").load(fullUrl, function (response, status, xhr) {
                if (status == "error") {
                    jq("#payment-data").html("<p>Error Irembo Pay Parameters.</p>");
                }
            });
        }
        var billId = clicked.attr("data-bill_id");
        if (billId && typeof hie !== "undefined" && typeof hie.showPaymentViewDialog === "function") {
            hie.showPaymentViewDialog(billId);
        }
    });

    (function() {
        var waitingEls = jq(".irembo-waiting-payment");
        if (waitingEls.length === 0) return;
        var contextPath = jq("meta[name='openmrs-context-path']").attr("content") || "";
        if (!contextPath && typeof window.location !== "undefined") {
            var path = window.location.pathname || "";
            var openmrsIdx = path.indexOf("/openmrs");
            contextPath = openmrsIdx >= 0 ? path.substring(0, openmrsIdx + 8) : "";
        }
        var statusUrlBase = contextPath + "/ws/rest/v1/rwandaemr/irembopay/status";

        var checkCount = 0;
        function checkStatus() {
            checkCount++;
            var stillWaiting = jq(".irembo-waiting-payment");
            if (stillWaiting.length === 0) return;
            stillWaiting.each(function() {
                var el = jq(this);
                var invoiceNumber = el.attr("data-invoice-number");
                if (!invoiceNumber) return;
                
                var currentCheckCount = parseInt(el.attr("data-check-count") || "0");
                el.attr("data-check-count", currentCheckCount + 1);
                
                if (currentCheckCount + 1 >= 3 && el.find(".irembo-retry-btn").length === 0) {
                    var billId = el.attr("data-bill-id") || el.attr("data-bill_id");
                    var billAmount = el.attr("data-bill_amount");
                    var invoiceNumber = el.attr("data-invoice_number") || el.attr("data-invoice-number");
                    var url = el.attr("data-url");
                    el.find("i.icon-spinner").remove();
                    el.text(el.text().replace(/Waiting.*/, "").trim());
                    var retryBtn = jq('<a class="irembo-retry-btn open_payment_request_pop" href="javascript:hie.showPaymentViewDialog(\\\'' + billId + '\\\')" title="Retry Payment Request" style="margin-left: 10px; cursor: pointer;"><i class="icon-refresh"></i> Retry</a>');
                    retryBtn.attr("data-bill_amount", billAmount);
                    retryBtn.attr("data-bill_id", billId);
                    retryBtn.attr("data-invoice_number", invoiceNumber);
                    retryBtn.attr("data-url", url);
                    el.append(retryBtn);
                    el.removeClass("irembo-waiting-payment");
                    return;
                }
                
                if (el.find(".irembo-retry-btn").length > 0) {
                    return;
                }
                
                jq.ajax({
                    url: statusUrlBase + "?invoiceNumber=" + encodeURIComponent(invoiceNumber),
                    type: "GET",
                    dataType: "json"
                }).done(function(data) {
                    if (data && data.found === true && data.paid === true) {
                        var spinnerIcon = el.find("i.icon-spinner");
                        if (spinnerIcon.length > 0) {
                            spinnerIcon.removeClass("icon-spinner icon-spin").addClass("icon-ok").css("color", "green");
                            var currentText = el.text().trim();
                            if (currentText.indexOf("Waiting") >= 0) {
                                el.text(el.text().replace(/Waiting.*/, "Paid"));
                            } else {
                                el.append(" Paid");
                            }
                            el.find(".irembo-retry-btn").remove();
                            el.removeClass("irembo-waiting-payment");
                        }
                    }
                });
            });
            var stillWaitingAfter = jq(".irembo-waiting-payment");
            if (stillWaitingAfter.length > 0) {
                setTimeout(checkStatus, 8000);
            }
        }

        jq(document).ready(function() {
            setTimeout(checkStatus, 5000);
        });
    })();
</script>