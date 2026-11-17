<%
    ui.decorateWith("appui", "standardEmrPage")
    def openmrsContextPath = contextPath ? (contextPath.startsWith("/") ? contextPath : "/" + contextPath) : ""
    def dateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    def displayType = { type ->
        type == "PAYMENT_CLEARANCE" ? "Payment clearance" : "Lab result"
    }
    def chipClass = { value ->
        def normalized = value ? value.toString().replaceAll("[^A-Za-z0-9_-]", "_") : "UNKNOWN"
        "sms-chip-" + normalized
    }
%>

<script type="text/javascript">
    var breadcrumbs = [
        { icon: "icon-home", link: '${ openmrsContextPath }/index.htm' },
        { label: "${ ui.message("coreapps.app.system.administration.label") }", link: "${ ui.pageLink("coreapps", "systemadministration/systemAdministration") }" },
        { label: "SMS Logs", link: "${ ui.pageLink("rwandaemr", "admin/smsLogs") }" }
    ];
</script>

<style>
    .sms-log-page {
        max-width: 1540px;
        margin: 0 auto;
    }
    .sms-log-header {
        display: flex;
        align-items: flex-end;
        justify-content: space-between;
        gap: 16px;
        margin: 8px 0 18px;
    }
    .sms-log-title {
        margin: 0;
        font-size: 28px;
        line-height: 1.15;
        color: #263238;
    }
    .sms-log-subtitle {
        margin: 5px 0 0;
        color: #607d8b;
        font-size: 14px;
    }
    .sms-log-count {
        min-width: 170px;
        border: 1px solid #d9e2e7;
        background: #ffffff;
        padding: 12px 16px;
        text-align: right;
        border-radius: 4px;
    }
    .sms-log-count strong {
        display: block;
        font-size: 24px;
        color: #263238;
    }
    .sms-log-filter-panel {
        border: 1px solid #d9e2e7;
        background: #ffffff;
        padding: 14px;
        margin-bottom: 14px;
        border-radius: 4px;
    }
    .sms-log-filter-grid {
        display: grid;
        grid-template-columns: 150px 150px 190px 150px 130px minmax(220px, 1fr) 150px 140px;
        gap: 10px;
        align-items: end;
    }
    .sms-log-field label {
        display: block;
        margin-bottom: 4px;
        font-size: 12px;
        font-weight: bold;
        color: #455a64;
        text-transform: uppercase;
    }
    .sms-log-field input,
    .sms-log-field select {
        width: 100%;
        box-sizing: border-box;
        min-height: 34px;
        border: 1px solid #b0bec5;
        border-radius: 3px;
        padding: 6px 8px;
        background-color: #ffffff;
    }
    .sms-log-actions {
        display: flex;
        gap: 8px;
    }
    .sms-log-actions input,
    .sms-log-actions a {
        min-height: 34px;
        box-sizing: border-box;
        white-space: nowrap;
    }
    .sms-log-pagination {
        display: flex;
        align-items: center;
        justify-content: flex-end;
        gap: 12px;
        margin: 10px 0;
        color: #455a64;
    }
    .sms-log-table-wrap {
        border: 1px solid #d9e2e7;
        background: #ffffff;
        border-radius: 4px;
        overflow-x: auto;
    }
    table.sms-log-table {
        width: 100%;
        border-collapse: collapse;
        min-width: 1420px;
    }
    .sms-log-table th {
        background: #f3f6f8;
        color: #455a64;
        font-size: 12px;
        text-transform: uppercase;
        border-bottom: 1px solid #d9e2e7;
        padding: 10px 8px;
        text-align: left;
    }
    .sms-log-table td {
        border-bottom: 1px solid #edf2f5;
        padding: 10px 8px;
        vertical-align: top;
        color: #263238;
        overflow-wrap: anywhere;
    }
    .sms-log-table tr:hover td {
        background: #f7fbfc;
    }
    .sms-chip {
        display: inline-block;
        min-width: 72px;
        padding: 4px 8px;
        border-radius: 12px;
        text-align: center;
        font-weight: bold;
        font-size: 12px;
        color: #455a64;
        background: #eceff1;
    }
    .sms-chip-QUEUED,
    .sms-chip-ACCEPTD {
        color: #0b5394;
        background: #d9edf7;
    }
    .sms-chip-DELIVRD,
    .sms-chip-SENT {
        color: #1b5e20;
        background: #dff0d8;
    }
    .sms-chip-FAILED,
    .sms-chip-UNDELIV,
    .sms-chip-REJECTD,
    .sms-chip-EXPIRED {
        color: #9f1d1d;
        background: #f8d7da;
    }
    .sms-log-textarea {
        width: 100%;
        min-height: 82px;
        box-sizing: border-box;
        resize: vertical;
        border: 1px solid #d9e2e7;
        background: #fbfdfe;
        font-family: monospace;
        font-size: 12px;
        color: #263238;
    }
    .sms-log-muted {
        color: #78909c;
    }
    .sms-log-empty {
        border: 1px solid #d9e2e7;
        background: #ffffff;
        padding: 18px;
        color: #455a64;
    }
    @media (max-width: 1200px) {
        .sms-log-filter-grid {
            grid-template-columns: repeat(2, minmax(160px, 1fr));
        }
    }
</style>

<div class="sms-log-page">
    <div class="sms-log-header">
        <div>
            <h3 class="sms-log-title">SMS Logs</h3>
            <p class="sms-log-subtitle">Lab result and payment clearance messages</p>
        </div>
        <div class="sms-log-count">
            <span>Total matching rows</span>
            <strong>${ totalCount }</strong>
        </div>
    </div>

    <form method="get" action="${ ui.pageLink("rwandaemr", "admin/smsLogs") }" class="sms-log-filter-panel">
        <div class="sms-log-filter-grid">
            <div class="sms-log-field">
                <label for="startDate">Start date</label>
                <input id="startDate" type="text" name="startDate" value="${ ui.encodeHtmlAttribute(startDate) }" placeholder="yyyy-MM-dd" />
            </div>
            <div class="sms-log-field">
                <label for="endDate">End date</label>
                <input id="endDate" type="text" name="endDate" value="${ ui.encodeHtmlAttribute(endDate) }" placeholder="yyyy-MM-dd" />
            </div>
            <div class="sms-log-field">
                <label for="sourceType">SMS type</label>
                <select id="sourceType" name="sourceType">
                    <option value="ALL" ${ sourceType == "ALL" ? "selected=\"selected\"" : "" }>All</option>
                    <option value="LAB_RESULT" ${ sourceType == "LAB_RESULT" ? "selected=\"selected\"" : "" }>Lab result</option>
                    <option value="PAYMENT_CLEARANCE" ${ sourceType == "PAYMENT_CLEARANCE" ? "selected=\"selected\"" : "" }>Payment clearance</option>
                </select>
            </div>
            <div class="sms-log-field">
                <label for="status">Status</label>
                <select id="status" name="status">
                    <option value="ALL" ${ status == "ALL" ? "selected=\"selected\"" : "" }>All</option>
                    <option value="QUEUED" ${ status == "QUEUED" ? "selected=\"selected\"" : "" }>QUEUED</option>
                    <option value="DELIVRD" ${ status == "DELIVRD" ? "selected=\"selected\"" : "" }>DELIVRD</option>
                    <option value="FAILED" ${ status == "FAILED" ? "selected=\"selected\"" : "" }>FAILED</option>
                    <option value="UNDELIV" ${ status == "UNDELIV" ? "selected=\"selected\"" : "" }>UNDELIV</option>
                    <option value="REJECTD" ${ status == "REJECTD" ? "selected=\"selected\"" : "" }>REJECTD</option>
                    <option value="EXPIRED" ${ status == "EXPIRED" ? "selected=\"selected\"" : "" }>EXPIRED</option>
                    <option value="UNKNOWN" ${ status == "UNKNOWN" ? "selected=\"selected\"" : "" }>UNKNOWN</option>
                </select>
            </div>
            <div class="sms-log-field">
                <label for="responseCode">HTTP</label>
                <input id="responseCode" type="text" name="responseCode" value="${ ui.encodeHtmlAttribute(responseCode) }" />
            </div>
            <div class="sms-log-field">
                <label for="query">Text</label>
                <input id="query" type="text" name="query" value="${ ui.encodeHtmlAttribute(query) }" />
            </div>
            <div class="sms-log-field">
                <label for="pageSize">Page size</label>
                <select id="pageSize" name="pageSize">
                    <option value="25" ${ pageSize == 25 ? "selected=\"selected\"" : "" }>25</option>
                    <option value="50" ${ pageSize == 50 ? "selected=\"selected\"" : "" }>50</option>
                    <option value="100" ${ pageSize == 100 ? "selected=\"selected\"" : "" }>100</option>
                    <option value="200" ${ pageSize == 200 ? "selected=\"selected\"" : "" }>200</option>
                </select>
            </div>
            <div class="sms-log-actions">
                <input type="submit" class="button confirm" value="Filter" />
                <a class="button" href="${ ui.pageLink("rwandaemr", "admin/smsLogs") }">Clear</a>
            </div>
        </div>
    </form>

    <div class="sms-log-pagination">
        <% if (hasPreviousPage) { %>
            <a href="${ ui.pageLink("rwandaemr", "admin/smsLogs") }?page=${ previousPage }&${ filterQueryString }">Previous</a>
        <% } else { %>
            <span class="sms-log-muted">Previous</span>
        <% } %>
        <span>Page ${ page } of ${ totalPages }</span>
        <% if (hasNextPage) { %>
            <a href="${ ui.pageLink("rwandaemr", "admin/smsLogs") }?page=${ nextPage }&${ filterQueryString }">Next</a>
        <% } else { %>
            <span class="sms-log-muted">Next</span>
        <% } %>
    </div>

    <% if (!logs) { %>
        <div class="sms-log-empty">No SMS logs found.</div>
    <% } else { %>
        <div class="sms-log-table-wrap">
            <table class="sms-log-table">
                <thead>
                    <tr>
                        <th>Date</th>
                        <th>Type</th>
                        <th>Reference</th>
                        <th>Patient</th>
                        <th>Phone</th>
                        <th>Status</th>
                        <th>DLR</th>
                        <th>HTTP</th>
                        <th>Provider ID</th>
                        <th>Message</th>
                        <th>Gateway response</th>
                        <th>Error</th>
                    </tr>
                </thead>
                <tbody>
                    <% logs.each { log -> %>
                        <tr>
                            <td>
                                ${ log.dateCreated ? dateFormat.format(log.dateCreated) : "" }
                                <% if (log.dateChanged) { %>
                                    <div class="sms-log-muted">Updated ${ dateFormat.format(log.dateChanged) }</div>
                                <% } %>
                            </td>
                            <td>${ displayType(log.sourceType) }</td>
                            <td>${ ui.encodeHtmlContent(log.reference) }</td>
                            <td>
                                <% if (log.patientIdentifier) { %>
                                    ${ ui.encodeHtmlContent(log.patientIdentifier) }
                                <% } else if (log.patientId) { %>
                                    #${ log.patientId }
                                <% } %>
                            </td>
                            <td>${ ui.encodeHtmlContent(log.phoneNumber) }</td>
                            <td><span class="sms-chip ${ chipClass(log.status) }">${ ui.encodeHtmlContent(log.status ?: "UNKNOWN") }</span></td>
                            <td>
                                <span class="sms-chip ${ chipClass(log.dlrStatus) }">${ ui.encodeHtmlContent(log.dlrStatus ?: "PENDING") }</span>
                                <% if (log.dlrLevel) { %>
                                    <div class="sms-log-muted">Level ${ ui.encodeHtmlContent(log.dlrLevel) }</div>
                                <% } %>
                            </td>
                            <td>${ log.responseCode == null ? "" : log.responseCode }</td>
                            <td>${ ui.encodeHtmlContent(log.providerMessageId) }</td>
                            <td><textarea class="sms-log-textarea" readonly="readonly">${ ui.encodeHtmlContent(log.messageContent) }</textarea></td>
                            <td>
                                <textarea class="sms-log-textarea" readonly="readonly">${ ui.encodeHtmlContent(log.responseBody) }</textarea>
                                <% if (log.requestPayload) { %>
                                    <textarea class="sms-log-textarea" readonly="readonly">${ ui.encodeHtmlContent(log.requestPayload) }</textarea>
                                <% } %>
                            </td>
                            <td><textarea class="sms-log-textarea" readonly="readonly">${ ui.encodeHtmlContent(log.errorMessage) }</textarea></td>
                        </tr>
                    <% } %>
                </tbody>
            </table>
        </div>
    <% } %>

    <div class="sms-log-pagination">
        <% if (hasPreviousPage) { %>
            <a href="${ ui.pageLink("rwandaemr", "admin/smsLogs") }?page=${ previousPage }&${ filterQueryString }">Previous</a>
        <% } else { %>
            <span class="sms-log-muted">Previous</span>
        <% } %>
        <span>Page ${ page } of ${ totalPages }</span>
        <% if (hasNextPage) { %>
            <a href="${ ui.pageLink("rwandaemr", "admin/smsLogs") }?page=${ nextPage }&${ filterQueryString }">Next</a>
        <% } else { %>
            <span class="sms-log-muted">Next</span>
        <% } %>
    </div>
</div>
