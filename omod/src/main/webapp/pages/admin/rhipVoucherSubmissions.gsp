<%
    ui.decorateWith("appui", "standardEmrPage")
    def openmrsContextPath = contextPath ? (contextPath.startsWith("/") ? contextPath : "/" + contextPath) : ""
%>

<script type="text/javascript">
    var breadcrumbs = [
        { icon: "icon-home", link: '${ openmrsContextPath }/index.htm' },
        { label: "${ ui.message("coreapps.app.system.administration.label") }", link: "${ ui.pageLink("coreapps", "systemadministration/systemAdministration") }" },
        { label: "RHIP Voucher Submission", link: "${ ui.pageLink("rwandaemr", "admin/rhipVoucherSubmissions") }" }
    ];
</script>

<style>
    .rhip-page {
        max-width: 1540px;
        margin: 0 auto;
    }
    .rhip-page-header {
        display: flex;
        align-items: flex-end;
        justify-content: space-between;
        gap: 16px;
        margin: 8px 0 18px;
    }
    .rhip-page-title {
        margin: 0;
        font-size: 28px;
        line-height: 1.15;
        color: #263238;
    }
    .rhip-page-subtitle {
        margin: 5px 0 0;
        color: #607d8b;
        font-size: 14px;
    }
    .rhip-count-card {
        min-width: 170px;
        border: 1px solid #d9e2e7;
        background: #ffffff;
        padding: 12px 16px;
        text-align: right;
        border-radius: 4px;
    }
    .rhip-count-card strong {
        display: block;
        font-size: 24px;
        color: #263238;
    }
    .rhip-filter-panel {
        border: 1px solid #d9e2e7;
        background: #ffffff;
        padding: 14px;
        margin-bottom: 14px;
        border-radius: 4px;
    }
    .rhip-filter-grid {
        display: grid;
        grid-template-columns: 150px 150px 180px 180px minmax(260px, 1fr) 150px 140px;
        gap: 10px;
        align-items: end;
    }
    .rhip-field label {
        display: block;
        margin-bottom: 4px;
        font-size: 12px;
        font-weight: bold;
        color: #455a64;
        text-transform: uppercase;
    }
    .rhip-field input,
    .rhip-field select {
        width: 100%;
        box-sizing: border-box;
        min-height: 34px;
        border: 1px solid #b0bec5;
        border-radius: 3px;
        padding: 6px 8px;
        background-color: #ffffff;
    }
    .rhip-filter-actions {
        display: flex;
        gap: 8px;
    }
    .rhip-filter-actions input,
    .rhip-filter-actions a {
        min-height: 34px;
        box-sizing: border-box;
        white-space: nowrap;
    }
    .rhip-table-wrap {
        border: 1px solid #d9e2e7;
        background: #ffffff;
        border-radius: 4px;
        overflow-x: auto;
    }
    table.rhip-table {
        width: 100%;
        border-collapse: collapse;
        min-width: 1280px;
    }
    .rhip-table th {
        background: #f3f6f8;
        color: #455a64;
        font-size: 12px;
        text-transform: uppercase;
        border-bottom: 1px solid #d9e2e7;
        padding: 10px 8px;
        text-align: left;
    }
    .rhip-table td {
        border-bottom: 1px solid #edf2f5;
        padding: 10px 8px;
        vertical-align: top;
        color: #263238;
    }
    .rhip-table tr.rhip-row:hover td {
        background: #f7fbfc;
        cursor: pointer;
    }
    .rhip-number {
        text-align: right;
        white-space: nowrap;
    }
    .rhip-muted {
        color: #78909c;
    }
    .rhip-chip {
        display: inline-block;
        min-width: 74px;
        padding: 4px 8px;
        border-radius: 12px;
        text-align: center;
        font-weight: bold;
        font-size: 12px;
    }
    .rhip-chip-NOT_SENT {
        color: #455a64;
        background: #eceff1;
    }
    .rhip-chip-PROCESSING {
        color: #795548;
        background: #fff3cd;
    }
    .rhip-chip-SENT {
        color: #1b5e20;
        background: #dff0d8;
    }
    .rhip-chip-FAILED {
        color: #9f1d1d;
        background: #f8d7da;
    }
    .rhip-actions {
        white-space: nowrap;
    }
    .rhip-actions form {
        display: inline-block;
        margin: 0 6px 6px 0;
    }
    .rhip-actions input[type=submit] {
        min-height: 30px;
        border-radius: 3px;
    }
    .rhip-actions .primary {
        background: #007fff;
        color: #ffffff;
        border: 1px solid #0066cc;
    }
    .rhip-actions .retry {
        background: #ff9800;
        color: #ffffff;
        border: 1px solid #e08600;
    }
    .rhip-open-link {
        display: inline-block;
        padding: 6px 8px;
        border: 1px solid #b0bec5;
        border-radius: 3px;
        background: #ffffff;
        text-decoration: none;
    }
    .rhip-detail-row {
        display: none;
    }
    .rhip-detail-row td {
        background: #fbfdfe;
        padding: 16px;
    }
    .rhip-detail-grid {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 14px;
    }
    .rhip-detail-card {
        border: 1px solid #d9e2e7;
        background: #ffffff;
        border-radius: 4px;
        padding: 12px;
    }
    .rhip-detail-card h4 {
        margin: 0 0 10px;
        color: #263238;
    }
    .rhip-kv {
        display: grid;
        grid-template-columns: 150px 1fr;
        gap: 6px 10px;
        font-size: 13px;
    }
    .rhip-kv label {
        color: #607d8b;
        font-weight: bold;
    }
    .rhip-mini-table {
        width: 100%;
        border-collapse: collapse;
    }
    .rhip-mini-table th,
    .rhip-mini-table td {
        padding: 7px;
        border-bottom: 1px solid #edf2f5;
        font-size: 12px;
    }
    .rhip-pagination {
        display: flex;
        align-items: center;
        gap: 12px;
        margin: 14px 0;
    }
    .rhip-empty {
        padding: 28px;
        text-align: center;
        color: #607d8b;
    }
    @media (max-width: 1100px) {
        .rhip-filter-grid {
            grid-template-columns: 1fr 1fr;
        }
        .rhip-detail-grid {
            grid-template-columns: 1fr;
        }
    }
</style>

<script type="text/javascript">
    function toggleRhipDetails(id) {
        var row = jq("#rhip-detail-" + id);
        row.toggle();
    }

    function submitRhipVoucher(form) {
        if (!confirm("Are you sure you want to send the RHIP voucher for this global bill?")) {
            return false;
        }
        jq(form).find("input[type=submit]").prop("disabled", true).val("Processing...");
        return true;
    }
</script>

<div class="rhip-page">
    <div class="rhip-page-header">
        <div>
            <h1 class="rhip-page-title">RHIP Voucher Submission</h1>
            <p class="rhip-page-subtitle">Discharged global bills ready for voucher submission and retry follow-up.</p>
        </div>
        <div class="rhip-count-card">
            <strong>${ totalCount }</strong>
            <span class="rhip-muted">matching bills</span>
        </div>
    </div>

    <form method="get" action="${ ui.pageLink("rwandaemr", "admin/rhipVoucherSubmissions") }" class="rhip-filter-panel">
        <div class="rhip-filter-grid">
            <div class="rhip-field">
                <label for="dischargeStartDate">Discharge From</label>
                <input id="dischargeStartDate" type="date" name="dischargeStartDate" value="${ dischargeStartDate }"/>
            </div>
            <div class="rhip-field">
                <label for="dischargeEndDate">Discharge To</label>
                <input id="dischargeEndDate" type="date" name="dischargeEndDate" value="${ dischargeEndDate }"/>
            </div>
            <div class="rhip-field">
                <label for="status">Submission Status</label>
                <select id="status" name="status">
                    <option value="NOT_SENT" ${ status == "NOT_SENT" ? "selected" : "" }>Not Sent</option>
                    <option value="FAILED" ${ status == "FAILED" ? "selected" : "" }>Failed</option>
                    <option value="SENT" ${ status == "SENT" ? "selected" : "" }>Sent</option>
                    <option value="ALL" ${ status == "ALL" ? "selected" : "" }>All</option>
                </select>
            </div>
            <div class="rhip-field">
                <label for="sortBy">Sort By</label>
                <select id="sortBy" name="sortBy">
                    <option value="dischargeDate" ${ sortBy == "dischargeDate" ? "selected" : "" }>Discharge date</option>
                    <option value="patientName" ${ sortBy == "patientName" ? "selected" : "" }>Patient name</option>
                    <option value="globalBillNumber" ${ sortBy == "globalBillNumber" ? "selected" : "" }>Global bill number</option>
                    <option value="totalAmount" ${ sortBy == "totalAmount" ? "selected" : "" }>Total amount</option>
                    <option value="status" ${ sortBy == "status" ? "selected" : "" }>RHIP status</option>
                </select>
            </div>
            <div class="rhip-field">
                <label for="query">Search</label>
                <input id="query" type="search" name="query" value="${ ui.encodeHtmlAttribute(query) }" placeholder="Bill number, patient, identifier, card number"/>
            </div>
            <div class="rhip-field">
                <label for="sortDirection">Direction</label>
                <select id="sortDirection" name="sortDirection">
                    <option value="desc" ${ sortDirection == "desc" ? "selected" : "" }>Descending</option>
                    <option value="asc" ${ sortDirection == "asc" ? "selected" : "" }>Ascending</option>
                </select>
            </div>
            <div class="rhip-field">
                <label for="pageSize">Page Size</label>
                <select id="pageSize" name="pageSize">
                    <option value="25" ${ pageSize == 25 ? "selected" : "" }>25</option>
                    <option value="50" ${ pageSize == 50 ? "selected" : "" }>50</option>
                    <option value="100" ${ pageSize == 100 ? "selected" : "" }>100</option>
                </select>
            </div>
        </div>
        <div class="rhip-filter-actions" style="margin-top: 12px;">
            <input type="submit" value="Apply Filters"/>
            <a class="button" href="${ ui.pageLink("rwandaemr", "admin/rhipVoucherSubmissions") }">Reset</a>
        </div>
    </form>

    <div class="rhip-pagination">
        <% if (hasPreviousPage) { %>
            <a class="button" href="${ ui.pageLink("rwandaemr", "admin/rhipVoucherSubmissions") }?page=${ previousPage }&${ filterQueryString }">Previous</a>
        <% } else { %>
            <span class="rhip-muted">Previous</span>
        <% } %>
        <strong>Page ${ page } of ${ totalPages }</strong>
        <% if (hasNextPage) { %>
            <a class="button" href="${ ui.pageLink("rwandaemr", "admin/rhipVoucherSubmissions") }?page=${ nextPage }&${ filterQueryString }">Next</a>
        <% } else { %>
            <span class="rhip-muted">Next</span>
        <% } %>
    </div>

    <div class="rhip-table-wrap">
        <% if (!rows) { %>
            <div class="rhip-empty">No global bills match the selected filters.</div>
        <% } else { %>
            <table class="rhip-table">
                <thead>
                <tr>
                    <th>Global Bill</th>
                    <th>Patient</th>
                    <th>Insurance</th>
                    <th>Admission</th>
                    <th>Discharge</th>
                    <th>Total</th>
                    <th>Status</th>
                    <th>Last Attempt</th>
                    <th>Message</th>
                    <th>Actions</th>
                </tr>
                </thead>
                <tbody>
                <% rows.each { row ->
                    def globalBill = row.globalBill
                    def globalBillId = globalBill.globalBillId
                    def histories = historyByGlobalBillId[globalBillId] ?: []
                    def consommations = consommationsByGlobalBillId[globalBillId] ?: []
                %>
                    <tr class="rhip-row" onclick="toggleRhipDetails('${ globalBillId }')">
                        <td>
                            <strong>${ ui.format(globalBill.billIdentifier) }</strong>
                            <div class="rhip-muted">ID ${ globalBillId }</div>
                        </td>
                        <td>
                            <strong>${ ui.format(row.patientName) }</strong>
                            <div class="rhip-muted">${ ui.format(row.patientIdentifier) }</div>
                        </td>
                        <td>
                            ${ ui.format(row.insuranceName) }
                            <div class="rhip-muted">${ ui.format(row.insuranceCardNumber) }</div>
                        </td>
                        <td>${ ui.formatDatePretty(globalBill.admission?.admissionDate) }</td>
                        <td>${ ui.formatDatePretty(globalBill.closingDate) }</td>
                        <td class="rhip-number">${ ui.format(globalBill.globalAmount) }</td>
                        <td>
                            <span class="rhip-chip rhip-chip-${ row.effectiveStatus }">${ row.displayStatus }</span>
                            <% if (row.voucherReference) { %>
                                <div class="rhip-muted">Ref: ${ ui.format(row.voucherReference) }</div>
                            <% } %>
                        </td>
                        <td>${ row.lastSubmissionDate ? ui.formatDatePretty(row.lastSubmissionDate) : "" }</td>
                        <td style="max-width: 260px;">${ ui.format(row.submissionMessage) }</td>
                        <td class="rhip-actions" onclick="event.stopPropagation();">
                            <% if (row.effectiveStatus == "NOT_SENT" && canSend) { %>
                                <form method="post" action="${ ui.pageLink("rwandaemr", "admin/rhipVoucherSubmissions") }" onsubmit="return submitRhipVoucher(this);">
                                    <input type="hidden" name="action" value="send"/>
                                    <input type="hidden" name="globalBillId" value="${ globalBillId }"/>
                                    <input type="hidden" name="dischargeStartDate" value="${ dischargeStartDate }"/>
                                    <input type="hidden" name="dischargeEndDate" value="${ dischargeEndDate }"/>
                                    <input type="hidden" name="status" value="${ status }"/>
                                    <input type="hidden" name="query" value="${ ui.encodeHtmlAttribute(query) }"/>
                                    <input type="hidden" name="sortBy" value="${ sortBy }"/>
                                    <input type="hidden" name="sortDirection" value="${ sortDirection }"/>
                                    <input type="hidden" name="page" value="${ page }"/>
                                    <input type="hidden" name="pageSize" value="${ pageSize }"/>
                                    <input class="primary" type="submit" value="Send RHIP Voucher"/>
                                </form>
                            <% } %>
                            <% if (row.effectiveStatus == "FAILED" && canRetry) { %>
                                <form method="post" action="${ ui.pageLink("rwandaemr", "admin/rhipVoucherSubmissions") }" onsubmit="return submitRhipVoucher(this);">
                                    <input type="hidden" name="action" value="retry"/>
                                    <input type="hidden" name="globalBillId" value="${ globalBillId }"/>
                                    <input type="hidden" name="dischargeStartDate" value="${ dischargeStartDate }"/>
                                    <input type="hidden" name="dischargeEndDate" value="${ dischargeEndDate }"/>
                                    <input type="hidden" name="status" value="${ status }"/>
                                    <input type="hidden" name="query" value="${ ui.encodeHtmlAttribute(query) }"/>
                                    <input type="hidden" name="sortBy" value="${ sortBy }"/>
                                    <input type="hidden" name="sortDirection" value="${ sortDirection }"/>
                                    <input type="hidden" name="page" value="${ page }"/>
                                    <input type="hidden" name="pageSize" value="${ pageSize }"/>
                                    <input class="retry" type="submit" value="Retry RHIP Voucher"/>
                                </form>
                            <% } %>
                            <a class="rhip-open-link" href="${ openmrsContextPath }/module/mohbilling/viewGlobalBill.form?globalBillId=${ globalBillId }">Open Global Bill</a>
                        </td>
                    </tr>
                    <tr id="rhip-detail-${ globalBillId }" class="rhip-detail-row">
                        <td colspan="10">
                            <div class="rhip-detail-grid">
                                <div class="rhip-detail-card">
                                    <h4>Global Bill Summary</h4>
                                    <div class="rhip-kv">
                                        <label>Patient</label><span>${ ui.format(row.patientName) } (${ ui.format(row.patientIdentifier) })</span>
                                        <label>Admission</label><span>${ ui.formatDatePretty(globalBill.admission?.admissionDate) }</span>
                                        <label>Discharge</label><span>${ ui.formatDatePretty(globalBill.closingDate) }</span>
                                        <label>Insurance</label><span>${ ui.format(row.insuranceName) } / ${ ui.format(row.insuranceCardNumber) }</span>
                                        <label>Total amount</label><span>${ ui.format(globalBill.globalAmount) }</span>
                                        <label>Patient contribution</label><span>${ ui.format(row.patientContribution) }</span>
                                        <label>Insurance contribution</label><span>${ ui.format(row.insuranceContribution) }</span>
                                        <label>Diagnoses</label><span>${ ui.format(globalBill.admission?.diseaseType) }</span>
                                    </div>
                                </div>

                                <div class="rhip-detail-card">
                                    <h4>Submission History</h4>
                                    <% if (!canViewHistory) { %>
                                        <div class="rhip-muted">You do not have permission to view submission history.</div>
                                    <% } else if (!histories) { %>
                                        <div class="rhip-muted">No RHIP submission attempts recorded.</div>
                                    <% } else { %>
                                        <table class="rhip-mini-table">
                                            <thead>
                                            <tr>
                                                <th>Attempt</th>
                                                <th>Status</th>
                                                <th>Date</th>
                                                <th>HTTP</th>
                                                <th>Reference</th>
                                            </tr>
                                            </thead>
                                            <tbody>
                                            <% histories.each { history -> %>
                                                <tr>
                                                    <td>${ history.attemptNumber }</td>
                                                    <td>${ ui.format(history.status) }</td>
                                                    <td>${ ui.formatDatePretty(history.dateSubmitted) }</td>
                                                    <td>${ history.responseCode ?: "" }</td>
                                                    <td>${ ui.format(history.voucherReferenceNumber ?: history.voucherCode ?: "") }</td>
                                                </tr>
                                            <% } %>
                                            </tbody>
                                        </table>
                                    <% } %>
                                </div>
                            </div>

                            <div class="rhip-detail-card" style="margin-top: 14px;">
                                <h4>Billable Services, Drugs and Consumables</h4>
                                <table class="rhip-mini-table">
                                    <thead>
                                    <tr>
                                        <th>Item</th>
                                        <th>Date</th>
                                        <th class="rhip-number">Quantity</th>
                                        <th class="rhip-number">Unit Price</th>
                                        <th class="rhip-number">Amount</th>
                                    </tr>
                                    </thead>
                                    <tbody>
                                    <% consommations.each { consommation ->
                                        consommation.billItems?.each { item ->
                                            if (!item.voided) {
                                                def itemName = item.service?.facilityServicePrice?.name ?: item.serviceOther ?: item.serviceOtherDescription ?: item.hopService?.name
                                    %>
                                        <tr>
                                            <td>${ ui.format(itemName) }</td>
                                            <td>${ ui.formatDatePretty(item.serviceDate) }</td>
                                            <td class="rhip-number">${ ui.format(item.quantity) }</td>
                                            <td class="rhip-number">${ ui.format(item.unitPrice) }</td>
                                            <td class="rhip-number">${ ui.format(item.amount) }</td>
                                        </tr>
                                    <%      }
                                        }
                                    } %>
                                    </tbody>
                                </table>
                            </div>
                        </td>
                    </tr>
                <% } %>
                </tbody>
            </table>
        <% } %>
    </div>

    <div class="rhip-pagination">
        <% if (hasPreviousPage) { %>
            <a class="button" href="${ ui.pageLink("rwandaemr", "admin/rhipVoucherSubmissions") }?page=${ previousPage }&${ filterQueryString }">Previous</a>
        <% } else { %>
            <span class="rhip-muted">Previous</span>
        <% } %>
        <strong>Page ${ page } of ${ totalPages }</strong>
        <% if (hasNextPage) { %>
            <a class="button" href="${ ui.pageLink("rwandaemr", "admin/rhipVoucherSubmissions") }?page=${ nextPage }&${ filterQueryString }">Next</a>
        <% } else { %>
            <span class="rhip-muted">Next</span>
        <% } %>
    </div>
</div>
