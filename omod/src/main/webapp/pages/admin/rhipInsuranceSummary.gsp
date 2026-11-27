<%
    ui.decorateWith("appui", "standardEmrPage")
    def openmrsContextPath = contextPath ? (contextPath.startsWith("/") ? contextPath : "/" + contextPath) : ""
    def moneyFormat = new java.text.DecimalFormat("#,##0.##")
    def money = { value -> value == null ? "" : moneyFormat.format(value) }
    def enc = { value -> java.net.URLEncoder.encode(value == null ? "" : value.toString(), "UTF-8") }
    def periodQuery = "startDate=" + enc(startDate) + "&endDate=" + enc(endDate)
    def voucherLink = { insurance -> ui.pageLink("rwandaemr", "admin/rhipVoucherSubmissions") + "?dateType=ADMISSION&status=ALL&dischargeStartDate=" + enc(startDate) + "&dischargeEndDate=" + enc(endDate) + "&insuranceId=" + enc(insurance) }
    def reportLink = { insurance -> openmrsContextPath + "/module/mohbilling/billReport.form?formStatus=submit&startDate=" + enc(billReportStartDate) + "&endDate=" + enc(billReportEndDate) + (insurance ? "&insuranceFilter=" + enc(insurance.toString().startsWith("category:") ? insurance : "id:" + insurance) : "") }
    def total = summary.total
%>

<script type="text/javascript">
    var breadcrumbs = [
        { icon: "icon-home", link: '${ openmrsContextPath }/index.htm' },
        { label: "RHIP Insurance Summary", link: "${ ui.pageLink("rwandaemr", "admin/rhipInsuranceSummary") }" }
    ];
</script>

<style>
    /* The tables have many columns: use the screen's width rather than the decorator's narrow column */
    #body-wrapper, #content {
        max-width: none !important;
        width: auto !important;
    }
    .ris-page { max-width: 1800px; margin: 0 auto; padding: 0 16px; }
    .ris-header { display: flex; flex-wrap: wrap; align-items: flex-end; justify-content: space-between; gap: 12px; margin: 8px 0 16px; }
    .ris-title { margin: 0; font-size: 28px; line-height: 1.15; color: #263238; }
    .ris-subtitle { margin: 5px 0 0; color: #607d8b; font-size: 14px; }
    .ris-actions { display: flex; flex-wrap: wrap; gap: 8px; }
    .ris-filters { display: flex; flex-wrap: wrap; gap: 12px; align-items: flex-end; border: 1px solid #d9e2e7; background: #ffffff; padding: 14px; margin-bottom: 14px; border-radius: 4px; }
    .ris-field label { display: block; margin-bottom: 4px; font-size: 12px; font-weight: bold; color: #455a64; text-transform: uppercase; }
    .ris-field input, .ris-field select { min-width: 170px; box-sizing: border-box; min-height: 34px; border: 1px solid #b0bec5; border-radius: 3px; padding: 6px 8px; background: #ffffff; }
    .ris-cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(190px, 1fr)); gap: 12px; margin-bottom: 14px; }
    .ris-card { border: 1px solid #d9e2e7; background: #ffffff; border-radius: 4px; padding: 12px 16px; }
    .ris-card span { display: block; font-size: 12px; font-weight: bold; color: #455a64; text-transform: uppercase; }
    .ris-card strong { display: block; font-size: 24px; color: #263238; margin: 4px 0 2px; font-weight: normal; }
    .ris-card small { color: #78909c; }
    .ris-card-rejected strong { color: #b23b3b; }
    .ris-table-wrap { border: 1px solid #d9e2e7; background: #ffffff; border-radius: 4px; overflow-x: auto; }
    table.ris-table { width: 100%; border-collapse: collapse; min-width: 1100px; }
    .ris-table th { background: #f3f6f8; color: #455a64; font-size: 12px; text-transform: uppercase; border-bottom: 1px solid #d9e2e7; padding: 10px 8px; text-align: left; }
    .ris-table td { border-bottom: 1px solid #edf2f5; padding: 10px 8px; vertical-align: top; color: #263238; }
    .ris-table tfoot td { background: #f3f6f8; font-weight: bold; border-top: 2px solid #90a4ae; }
    .ris-number { text-align: right; white-space: nowrap; }
    .ris-muted { color: #78909c; font-size: 12px; }
    .ris-bar { display: flex; height: 10px; background: #edf2f5; overflow: hidden; min-width: 160px; }
    .ris-bar-closed { background: #2e7d32; }
    .ris-bar-review { background: #f9a825; }
    .ris-match { color: #2e7d32; white-space: nowrap; }
    .ris-differs { color: #8a5300; font-weight: bold; white-space: nowrap; }
    .ris-icon-link { display: inline-flex; width: 32px; height: 32px; align-items: center; justify-content: center; }
    .ris-notes { margin-top: 12px; color: #607d8b; font-size: 13px; }
    .ris-notes p { margin: 0 0 4px; }
    .ris-empty { border: 1px solid #d9e2e7; background: #ffffff; padding: 24px; text-align: center; color: #607d8b; border-radius: 4px; }
</style>

<div class="ris-page">
    <div class="ris-header">
        <div>
            <h1 class="ris-title">RHIP Insurance Summary</h1>
            <p class="ris-subtitle">Closed global bills admitted from ${ startDate } to ${ endDate }, per insurance, with the same amounts as the bill report.</p>
        </div>
        <div class="ris-actions">
            <a class="button" href="${ openmrsContextPath }/ws/rest/v1/rwandaemr/rhipInsuranceSummary/export?${ periodQuery }&insuranceId=${ enc(insuranceId) }">
                <i class="icon-download-alt"></i> Export to Excel
            </a>
            <% if (!summary.limited) { %>
                <a class="button" href="${ reportLink(insuranceId) }"><i class="icon-file"></i> Open bill report</a>
            <% } %>
        </div>
    </div>

    <form method="get" action="${ ui.pageLink("rwandaemr", "admin/rhipInsuranceSummary") }" class="ris-filters">
        <div class="ris-field">
            <label for="startDate">Admitted from</label>
            <input id="startDate" type="date" name="startDate" value="${ startDate }"/>
        </div>
        <div class="ris-field">
            <label for="endDate">Admitted to</label>
            <input id="endDate" type="date" name="endDate" value="${ endDate }"/>
        </div>
        <div class="ris-field">
            <label for="insuranceId">Insurance</label>
            <select id="insuranceId" name="insuranceId">
                <option value="" ${ !insuranceId ? "selected" : "" }>${ summary.limited ? "All my insurances" : "All" }</option>
                <% summary.insuranceOptions.each { option -> %>
                    <option value="${ ui.encodeHtmlAttribute(option.value) }" ${ insuranceId == option.value ? "selected" : "" }>${ ui.encodeHtmlContent(option.label) }</option>
                <% } %>
            </select>
        </div>
        <input type="submit" class="confirm" value="Show"/>
    </form>

    <% if (summary.limited) { %>
        <div class="note-container">
            <div class="note warning">
                <div class="text"><p>You only see the insurances linked to your account.</p></div>
            </div>
        </div>
    <% } %>

    <div class="ris-cards">
        <div class="ris-card"><span>Vouchers</span><strong>${ total.vouchers }</strong><small>${ summary.rows.size() } insurance(s)</small></div>
        <div class="ris-card"><span>Total amount (100%)</span><strong>${ money(total.total) }</strong><small>RWF</small></div>
        <div class="ris-card"><span>Patient due</span><strong>${ money(total.patientDue) }</strong><small>Co-payment</small></div>
        <div class="ris-card"><span>Insurance due</span><strong>${ money(total.insuranceDue) }</strong><small>Total minus co-payment</small></div>
        <div class="ris-card ris-card-rejected"><span>Rejected in verification</span><strong>${ money(total.rejected) }</strong><small>Not deducted from the vouchers</small></div>
    </div>

    <% if (!summary.rows) { %>
        <div class="ris-empty">No closed global bill was admitted in this period${ insuranceId ? " for this insurance" : "" }.</div>
    <% } else { %>
        <div class="ris-table-wrap">
            <table class="ris-table">
                <thead>
                <tr>
                    <th>Insurance</th>
                    <th class="ris-number">Vouchers</th>
                    <th class="ris-number">Total amount</th>
                    <th class="ris-number">Patient due</th>
                    <th class="ris-number">Insurance due</th>
                    <th>${ summary.limited ? "Waiting for you" : "Verification" }</th>
                    <th class="ris-number">Rejected</th>
                    <% if (summary.billReportAvailable) { %><th>Bill report</th><% } %>
                    <th>Actions</th>
                </tr>
                </thead>
                <tbody>
                <% summary.rows.each { row -> %>
                    <tr>
                        <td>
                            <a href="${ voucherLink(row.filterValue) }" target="_blank" rel="noopener" title="Opens the vouchers of this insurance in a new tab"><strong>${ ui.format(row.name) }</strong></a>
                            <div class="ris-muted">${ ui.format(row.category ?: "") }</div>
                        </td>
                        <td class="ris-number">
                            ${ row.vouchers }
                            <div class="ris-muted">${ row.confirmed } confirmed · ${ row.sent } sent<% if (row.failed) { %> · ${ row.failed } failed<% } %> · ${ row.notSent } not sent</div>
                        </td>
                        <td class="ris-number">${ money(row.total) }</td>
                        <td class="ris-number">${ money(row.patientDue) }</td>
                        <td class="ris-number">${ money(row.insuranceDue) }</td>
                        <td>
                            <% if (!row.verified) { %>
                                <span class="ris-muted">Not verified</span>
                            <% } else if (summary.limited) { %>
                                <strong>${ row.awaitingInsurerReview }</strong> to review
                                <div class="ris-muted">${ row.awaitingInsurerFinalDecision } final decision(s)</div>
                            <% } else { %>
                                <div class="ris-bar" role="img" aria-label="${ row.verificationClosed } closed, ${ row.verificationInReview } in review, ${ row.verificationNotStarted } not started">
                                    <div class="ris-bar-closed" style="width: ${ row.verificationClosedPercent }%"></div>
                                    <div class="ris-bar-review" style="width: ${ row.verificationInReviewPercent }%"></div>
                                </div>
                                <div class="ris-muted">${ row.verificationClosed } closed · ${ row.verificationInReview } in review · ${ row.verificationNotStarted } not started</div>
                            <% } %>
                        </td>
                        <td class="ris-number">${ row.verified ? money(row.rejected) : '<span class="ris-muted">-</span>' }</td>
                        <% if (summary.billReportAvailable) { %>
                            <td>
                                <% if (row.matchingBillReport) { %>
                                    <span class="ris-match"><i class="icon-ok"></i> Matches</span>
                                <% } else { %>
                                    <span class="ris-differs"><i class="icon-warning-sign"></i> Differs by ${ money(row.billReportDifference.abs()) }</span>
                                    <div class="ris-muted">Bill report: ${ money(row.billReportTotal) }</div>
                                <% } %>
                            </td>
                        <% } %>
                        <td style="white-space: nowrap;">
                            <a class="ris-icon-link" target="_blank" rel="noopener" href="${ voucherLink(row.filterValue) }" title="Open the vouchers of this insurance" aria-label="Open the vouchers of ${ ui.encodeHtmlAttribute(row.name ?: "") }"><i class="icon-list"></i></a>
                            <% if (!summary.limited) { %>
                                <a class="ris-icon-link" target="_blank" rel="noopener" href="${ reportLink(row.filterValue) }" title="Open the bill report for this insurance" aria-label="Open the bill report for ${ ui.encodeHtmlAttribute(row.name ?: "") }"><i class="icon-file"></i></a>
                            <% } %>
                        </td>
                    </tr>
                <% } %>
                </tbody>
                <tfoot>
                <tr>
                    <td>${ summary.limited ? "Total, your insurances" : "Total" }</td>
                    <td class="ris-number">${ total.vouchers }</td>
                    <td class="ris-number">${ money(total.total) }</td>
                    <td class="ris-number">${ money(total.patientDue) }</td>
                    <td class="ris-number">${ money(total.insuranceDue) }</td>
                    <td>
                        <% if (summary.limited) { %>
                            ${ total.awaitingInsurerReview } to review · ${ total.awaitingInsurerFinalDecision } final
                        <% } else { %>
                            ${ total.verificationClosed } closed · ${ total.verificationInReview } in review · ${ total.verificationNotStarted } not started
                        <% } %>
                    </td>
                    <td class="ris-number">${ money(total.rejected) }</td>
                    <% if (summary.billReportAvailable) { %>
                        <td>${ summary.billReportDifferences == 0 ? "All match" : summary.billReportDifferences + " difference(s)" }</td>
                    <% } %>
                    <td></td>
                </tr>
                </tfoot>
            </table>
        </div>

        <% if (summary.billReportAvailable && summary.billReportDifferences > 0) { %>
            <div class="note-container" style="margin-top: 12px;">
                <div class="note warning">
                    <div class="text">
                        <p>${ summary.billReportDifferences } insurance(s) differ from the bill report. A bill changed after the bill report data
                        was last refreshed, or a bill the report counts as discharged that is not closed, makes a difference. Open the vouchers and
                        the bill report of that insurance to compare.</p>
                    </div>
                </div>
            </div>
        <% } %>
    <% } %>

    <div class="ris-notes">
        <p><strong>MUTUELLE</strong> adds up every insurance of the MUTUELLE category, as the bill report does.</p>
        <p><strong>Vouchers</strong> are the global bills admitted in the period (admission date, as in the bill report) that are closed. Bills still open are not counted.</p>
        <p><strong>Same figures as the bill report:</strong> Total amount = "TOTAL invoice (100%)", Patient due = "Co-Payment", Insurance due = "RSSB payment (Total - Copayment)", computed item by item with each insurance's current rate.</p>
        <p><strong>Rejected</strong> is the total of the items whose rejection is final. A rejected item stays in the voucher amount.</p>
        <p><strong>Not verified:</strong> only the bills of the insurances set in the global property rwandaemr.billVerification.insurances (the MUTUELLE insurances by default) go through verification. The totals of the verification columns count those bills only.</p>
        <p>Clicking an insurance opens the RHIP voucher page for that insurance, filtered on the same admission dates.</p>
    </div>
</div>
