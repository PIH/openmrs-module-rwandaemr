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

<div id="batch-payment-view-dialog" class="dialog" style="display: none">
    <div class="dialog-header">
        <i class="icon-check-in"></i>
        <h3 id="batch_payment_request_title">
            ${ ui.message("Batch Payment (Irembo Pay)") }
        </h3>
    </div>
    <div class="dialog-content">
        <div id="batch-payment-data"></div>
        <button type="button" id="irembo-pay-batch-confirm-btn" class="confirm right">${ ui.message("rwandaemr.hie.done") }<i class="icon-spinner icon-spin icon-2x" style="display: none; margin-left: 10px;"></i></button>
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
        def totalCeiledRwf = 0.0d
        bills.each { b ->
            if (b?.getAmount() != null) {
                totalCeiledRwf += Math.ceil(b.getAmount().toDouble())
            }
        }
        def totalCeiledFormatted = String.format("%.2f", totalCeiledRwf)
        def payButtonLabel = ui.message("rwandaemr.billing.payTotalRwf", totalCeiledFormatted)
        def momoAlertText = ui.message("rwandaemr.billing.momoBalanceAlert", totalCeiledFormatted + " RWF")
        def unpaidBillIds = bills.collect { it.getPatientBillId() }.findAll { it != null }
        def unpaidBillIdsCsv = unpaidBillIds.join(",")
        def hasAnyInvoiceNumber = bills.any { b ->
            b?.getInvoiceNumber() != null && !b.getInvoiceNumber().toString().trim().isEmpty()
        }
        def defaultBatchPhone = bills.findResult { b ->
            def p = b?.getPhoneNumber()
            (p != null && !p.toString().trim().isEmpty()) ? p.toString().trim() : null
        } ?: ""
        def batchCtx = (ui.contextPath() ?: "").trim()
        if (batchCtx && !batchCtx.startsWith("/")) {
            batchCtx = "/" + batchCtx
        }
        def batchInitRestPath = batchCtx + "/ws/rest/v1/rwandaemr/irembopay/init-batch"
        if(bills.size() > 0) {
            %>
            <div class="irembo-pay-total-wrap">
                <% if (!hasAnyInvoiceNumber) { %>
                <a id="open_batch_payment_pop"
                   class="irembo-pay-total-btn"
                   href="javascript:void(0);"
                   role="button"
                   data-bill-ids="${ ui.encodeHtmlAttribute(unpaidBillIdsCsv) }"
                   data-default-phone="${ ui.encodeHtmlAttribute(defaultBatchPhone) }"
                   data-batch-url="${ ui.encodeHtmlAttribute(batchInitRestPath) }"
                   data-pay-label="${ ui.encodeHtmlAttribute(payButtonLabel) }"
                   data-momo-alert="${ ui.encodeHtmlAttribute(momoAlertText) }"
                   data-batch-invoice-summary="${ ui.encodeHtmlAttribute(ui.message('rwandaemr.billing.batchInvoiceCount', unpaidBillIds.size().toString())) }"
                   title="${ ui.encodeHtmlAttribute(ui.message('rwandaemr.billing.openBatchPayment')) }">
                    ${ payButtonLabel }
                </a>
                <% } else { %>
                <span class="irembo-pay-total-btn disabled"
                      title="Batch payment is unavailable once an invoice number exists.">
                    ${ payButtonLabel }
                </span>
                <% } %>
            </div>
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
    /** Root-absolute path (e.g. /openmrs/ws/...) or full URL — avoids 404 when contextPath lacks a leading slash. */
    function iremboAbsApiUrl(pathOrUrl) {
        var u = String(pathOrUrl || "").trim();
        if (!u) {
            return u;
        }
        var uLower = u.toLowerCase();
        if (uLower.indexOf("http://") === 0 || uLower.indexOf("https://") === 0) {
            return u;
        }
        if (u.charAt(0) === "/") {
            return (typeof window.location !== "undefined" && window.location.origin)
                ? (window.location.origin + u) : u;
        }
        var origin = (typeof window.location !== "undefined" && window.location.origin) ? window.location.origin : "";
        var rest = u;
        while (rest.length > 0 && rest.charAt(0) === "/") {
            rest = rest.substring(1);
        }
        return origin + "/" + rest;
    }

    var batchPaymentViewDialog = null;
    function closeIremboBatchPaymentDialog() {
        try {
            if (batchPaymentViewDialog && typeof batchPaymentViewDialog.close === "function") {
                batchPaymentViewDialog.close();
            }
        } catch (ignoreClose) {}
        jq("#batch-payment-view-dialog").hide();
    }
    function showBatchPaymentViewDialog() {
        if (batchPaymentViewDialog == null && typeof emr !== "undefined" && typeof emr.setupConfirmationDialog === "function") {
            batchPaymentViewDialog = emr.setupConfirmationDialog({
                selector: "#batch-payment-view-dialog",
                actions: {
                    confirm: function(){},
                    cancel: function(){ batchPaymentViewDialog.close(); }
                }
            });
            batchPaymentViewDialog.close();
        }
        if (batchPaymentViewDialog) {
            batchPaymentViewDialog.show();
        } else {
            jq("#batch-payment-view-dialog").show();
        }
    }

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

    jq(document).on("click", "#open_batch_payment_pop", function (e) {
        e.preventDefault();
        var clicked = jq(this);
        var billIdsCsv = String(clicked.attr("data-bill-ids") || "").trim();
        var endpoint = String(clicked.attr("data-batch-url") || "").trim();
        if (!billIdsCsv || !endpoint) {
            return;
        }
        var billIds = billIdsCsv.split(",").filter(function(v) { return String(v).trim().length > 0; });
        var defaultPhone = String(clicked.attr("data-default-phone") || "").trim();
        var payLabel = String(clicked.attr("data-pay-label") || "").trim();
        var momoAlert = String(clicked.attr("data-momo-alert") || "").trim();
        jq("#batch_payment_request_title").html("Batch Payment (Irembo Pay)");
        var phoneLabel = "${ ui.encodeJavaScript(ui.message('rwandaemr.billing.phoneNumber')) }";
        var invoiceCountMsg = String(clicked.attr("data-batch-invoice-summary") || "").trim();
        var batchRoot = jq("<div class='irembo-batch-payment-inner'/>");
        var topRow = jq("<div class='irembo-batch-dialog-top'/>");
        topRow.append(jq("<span/>").css("flex", "1"));
        if (payLabel) {
            topRow.append(jq("<span class='irembo-batch-pay-pill'/>").text(payLabel));
        }
        batchRoot.append(topRow);
        if (momoAlert) {
            batchRoot.append(jq("<div class='irembo-momo-alert'/>").text(momoAlert));
        }
        batchRoot.append(jq("<p class='irembo-batch-invoice-summary'/>").text(invoiceCountMsg));
        var phoneWrap = jq("<div style='margin-top:4px;'/>");
        phoneWrap.append(jq("<label for='irembo-batch-phone'/>").attr("style", "display:block;font-weight:600;margin-bottom:4px;color:#2c3e66;").text(phoneLabel));
        phoneWrap.append(jq("<input type='tel' id='irembo-batch-phone' autocomplete='tel'/>").attr("style", "width:100%;max-width:320px;padding:6px 8px;border:1px solid #ccc;border-radius:3px;"));
        batchRoot.append(phoneWrap);
        batchRoot.append(jq("<p id='irembo-batch-phone-error'/>").attr("style", "display:none;color:red;margin-top:8px;"));
        jq("#batch-payment-data").empty().append(batchRoot);
        jq("#irembo-batch-phone").val(defaultPhone);
        showBatchPaymentViewDialog();
    });

    jq(document).on("click", "#irembo-pay-batch-confirm-btn", function (e) {
        e.preventDefault();
        var clicked = jq("#open_batch_payment_pop");
        var billIdsCsv = String(clicked.attr("data-bill-ids") || "").trim();
        var endpoint = String(clicked.attr("data-batch-url") || "").trim();
        if (!billIdsCsv || !endpoint) {
            jq("#batch-payment-data").append("<p style='color:red;'>Missing batch payment parameters.</p>");
            return;
        }
        var phone = String(jq("#irembo-batch-phone").val() || "").trim();
        var errEl = jq("#irembo-batch-phone-error");
        if (!phone) {
            errEl.text("${ ui.encodeJavaScript(ui.message('rwandaemr.billing.phoneRequired')) }").show();
            return;
        }
        errEl.hide().text("");
        var btn = jq(this);
        btn.prop("disabled", true);
        btn.find("i.icon-spinner").show();
        jq.ajax({
            url: iremboAbsApiUrl(endpoint),
            type: "POST",
            dataType: "json",
            data: { billIds: billIdsCsv, phoneNumber: phone }
        }).done(function(data) {
            var status = data && data.status;
            var msg = (data && data.message) ? data.message : "Batch request submitted.";
            if (status === "success" || status === "partial") {
                if (typeof emr !== "undefined" && typeof emr.successMessage === "function") {
                    emr.successMessage(msg);
                }
                closeIremboBatchPaymentDialog();
                window.location.reload();
                return;
            }
            var okColor = "#8a0000";
            jq("#batch-payment-data").append("<p style='margin-top:10px;color:" + okColor + ";'>" + msg + "</p>");
            if (typeof emr !== "undefined" && typeof emr.errorMessage === "function") {
                emr.errorMessage(msg);
            }
            if (data && data.failed && data.failed.length) {
                var ul = jq("<ul style='margin-top:8px;color:#8a0000;padding-left:18px;'></ul>");
                jq.each(data.failed, function(i, f) {
                    ul.append(jq("<li/>").text("Bill " + f.billId + ": " + (f.message || "")));
                });
                jq("#batch-payment-data").append(ul);
            }
        }).fail(function(xhr) {
            var msg = "Failed to submit batch request";
            try {
                if (xhr && xhr.responseJSON && xhr.responseJSON.message) {
                    msg = xhr.responseJSON.message;
                }
            } catch (ignored) {}
            if (typeof emr !== "undefined" && typeof emr.errorMessage === "function") {
                emr.errorMessage(msg);
            }
            jq("#batch-payment-data").append("<p style='margin-top:10px;color:red;'>" + msg + "</p>");
        }).always(function() {
            btn.prop("disabled", false);
            btn.find("i.icon-spinner").hide();
        });
    });

    (function() {
        var waitingEls = jq(".irembo-waiting-payment");
        if (waitingEls.length === 0) return;
        var contextPath = jq("meta[name='openmrs-context-path']").attr("content") || "";
        if (contextPath && contextPath.charAt(0) !== "/") {
            contextPath = "/" + contextPath;
        }
        if (!contextPath && typeof window.location !== "undefined") {
            var path = window.location.pathname || "";
            var openmrsIdx = path.indexOf("/openmrs");
            contextPath = openmrsIdx >= 0 ? path.substring(0, openmrsIdx + 8) : "";
        }
        var statusUrlBase = iremboAbsApiUrl(contextPath + "/ws/rest/v1/rwandaemr/irembopay/status");

        var checkCount = 0;
        function checkStatus() {
            checkCount++;
            var stillWaiting = jq(".irembo-waiting-payment");
            if (stillWaiting.length === 0) return;
            stillWaiting.each(function() {
                var MAX_CHECKS_PER_BILL = 10;
                var MAX_RETRY_COUNT = 40;
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
                                var fullT = el.text();
                                var w = fullT.indexOf("Waiting");
                                el.text(w >= 0 ? fullT.substring(0, w) + "Paid" : fullT + " Paid");
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
                setTimeout(checkStatus, 4000);
            }
        }

        jq(document).ready(function() {
            setTimeout(checkStatus, 10000);
        });
    })();
</script>
