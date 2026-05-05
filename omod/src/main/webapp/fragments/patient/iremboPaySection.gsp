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
        <div style="margin-top: 6px; font-size: 13px; color: #00473f;">
            Dial *182*3*7*Invoice Number# and follow the prompts
        </div>

    </div>
    <div class="info-body">
        <g:if test="${error}">
            <span style="color: red;">${error}</span>
        </g:if>
        <%
        if(bills.size() > 0) {
            %>
            <div class="irembo-bills-table-wrapper">
                <table id="bills-list-table" class="irembo-bills-datatable">
                    <thead>
                        <tr>
                            <th>${ ui.message("Date") }</th>
                            <th>${ ui.message("Service") }</th>
                            <th>${ ui.message("Amount") }</th>
                            <th>${ ui.message("Invoice Number") }</th>
                        </tr>
                    </thead>
                    <tbody>
                        <% bills.each { bill ->
                            def pageLink
                            def retryCount = bill.getRetryCount() != null ? bill.getRetryCount() : 0
                            %>
                            <tr id="bill-${ bill.getPatientBillId() }" class="bill-row irembo-bill-row${pageLink ? ' pointer' :''}" data-href="#">
                                <td>
                                    ${ ui.format(bill.getBillDate()) }
                                </td>
                                <td>
                                    ${ ui.format(bill.getDepartment()) }
                                </td>
                                <td>
                                    ${ ui.format(bill.getAmount()) }
                                </td>
                                <td>
                                    ${ ui.format(bill.getInvoiceNumber()) }
                                    <% if (!bill.getInvoiceNumber() || bill.getInvoiceNumber().trim().isEmpty()) { %>
                                    <a class="open_payment_request_pop" data-bill_amount="${ ui.format(bill.getAmount()) }" data-bill_id="${ ui.format(bill.getPatientBillId()) }" data-invoice_number="${ ui.format(bill.getInvoiceNumber()) }" data-url='${ui.pageLink("rwandaemr", "patient/iremboPayStatusSection")}?billId=${bill.getPatientBillId()}&phoneNumber=${bill.getPhoneNumber()}' title="Create Payment Request" href="javascript:void(0);"><i class="icon-share-alt right"></i></a>
                                    <% } else { %>
                                    <span class="irembo-waiting-payment" data-bill_amount="${ ui.format(bill.getAmount()) }" data-bill_id="${ bill.getPatientBillId() }" data-invoice_number="${ ui.format(bill.getInvoiceNumber()) }" data-url='${ui.pageLink("rwandaemr", "patient/iremboPayStatusSection")}?billId=${bill.getPatientBillId()}&phoneNumber=${bill.getPhoneNumber()}' data-invoice-number="${ ui.format(bill.getInvoiceNumber()) }" data-bill-id="${ bill.getPatientBillId() }" data-phone-number="${ bill.getPhoneNumber() ?: '' }" data-check-count="0" data-retry-count="${ retryCount }" title="${ ui.message('rwandaemr.billing.waitingPayment') }"><i class="icon-spinner icon-spin"></i> ${ ui.message('rwandaemr.billing.waitingPayment') }</span>
                                    <% } %>
                                </td>
                            </tr>
                            <%
                        }
                        %>
                    </tbody>
                </table>
                <div id="irembo-bills-pagination" class="irembo-bills-pagination" style="margin-top: 8px; display: none;">
                    <span class="irembo-bills-page-info"></span>
                    <button type="button" class="irembo-bills-prev" style="margin-left: 10px;">${ ui.message("uicommons.dataTable.previous") }</button>
                    <button type="button" class="irembo-bills-next">${ ui.message("uicommons.dataTable.next") }</button>
                </div>
            </div>
            <%
        }
        %>
    </div>
</div>

<script type="text/javascript">
    jq(document).ready(function() {
        var ROWS_PER_PAGE = 5;
        var rows = jq("#bills-list-table tbody tr.irembo-bill-row");
        var total = rows.length;
        if (total === 0) return;

        var totalPages = Math.ceil(total / ROWS_PER_PAGE);
        var pagination = jq("#irembo-bills-pagination");
        var pageInfo = pagination.find(".irembo-bills-page-info");
        var prevBtn = pagination.find(".irembo-bills-prev");
        var nextBtn = pagination.find(".irembo-bills-next");
        var currentPage = 1;

        function showPage(page) {
            currentPage = Math.max(1, Math.min(page, totalPages));
            rows.hide();
            var visibleRows = rows.slice((currentPage - 1) * ROWS_PER_PAGE, currentPage * ROWS_PER_PAGE);
            visibleRows.show();
            var visibleCount = visibleRows.length;
            pageInfo.text(visibleCount + " of " + total + " · Page " + currentPage + " of " + totalPages);
            prevBtn.toggle(totalPages > 1 && currentPage > 1);
            nextBtn.toggle(totalPages > 1 && currentPage < totalPages);
        }

        if (totalPages > 1) {
            pagination.show();
            showPage(1);
            prevBtn.on("click", function() {
                if (currentPage > 1) showPage(currentPage - 1);
            });
            nextBtn.on("click", function() {
                if (currentPage < totalPages) showPage(currentPage + 1);
            });
        } else {
            pagination.hide();
        }
    });

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
                var MAX_CHECKS_PER_BILL = 5;
                var MAX_RETRY_COUNT = 20;
                var el = jq(this);
                var invoiceNumber = el.attr("data-invoice-number");
                if (!invoiceNumber) return;
                var currentRetryCount = parseInt(el.attr("data-retry-count") || "0");

                // If DB retryCount already reached threshold, do not call status endpoint anymore.
                if (currentRetryCount >= MAX_RETRY_COUNT) {
                    var thresholdIcon = el.find("i.icon-spinner");
                    if (thresholdIcon.length > 0) {
                        thresholdIcon.removeClass("icon-spinner icon-spin").addClass("icon-remove").css("color", "red");
                    }
                    el.contents().filter(function() { return this.nodeType === 3; }).remove();
                    el.append(" Not paid ");
                    if (el.find(".irembo-retry-btn").length === 0) {
                        var thresholdRetryBtn = jq('<a class="irembo-retry-btn open_payment_request_pop" href="javascript:void(0);" title="Retry Payment Request" style="margin-left: 10px; cursor: pointer;"><i class="icon-refresh"></i> Retry</a>');
                        thresholdRetryBtn.attr("data-bill_amount", el.attr("data-bill_amount"));
                        thresholdRetryBtn.attr("data-bill_id", el.attr("data-bill-id") || el.attr("data-bill_id"));
                        thresholdRetryBtn.attr("data-invoice_number", el.attr("data-invoice_number") || el.attr("data-invoice-number"));
                        thresholdRetryBtn.attr("data-url", el.attr("data-url"));
                        el.append(thresholdRetryBtn);
                    }
                    el.removeClass("irembo-waiting-payment");
                    return;
                }

                var currentCheckCount = parseInt(el.attr("data-check-count") || "0");
                var nextCheckCount = currentCheckCount + 1;
                el.attr("data-check-count", nextCheckCount);

                // Stop polling this bill after the configured number of checks.
                if (nextCheckCount > MAX_CHECKS_PER_BILL) {
                    el.attr("data-check-count", MAX_CHECKS_PER_BILL);
                    var timeoutIcon = el.find("i.icon-spinner");
                    if (timeoutIcon.length > 0) {
                        timeoutIcon.removeClass("icon-spinner icon-spin").addClass("icon-remove").css("color", "red");
                    }
                    el.contents().filter(function() { return this.nodeType === 3; }).remove();
                    el.append(" Not paid ");
                    el.removeClass("irembo-waiting-payment");
                    return;
                }

                jq.ajax({
                    url: statusUrlBase + "?forceUpdate=true&invoiceNumber=" + encodeURIComponent(invoiceNumber),
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
            setTimeout(checkStatus, 15000);
        });
    })();
</script>
