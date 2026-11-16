<%
    ui.decorateWith("appui", "standardEmrPage")
%>

<script type="text/javascript">
    var breadcrumbs = [
        { icon: "icon-home", link: '/' + OPENMRS_CONTEXT_PATH + '/index.htm' },
        { label: "${ ui.message("coreapps.app.system.administration.label") }", link: "${ ui.pageLink("coreapps", "systemadministration/systemAdministration") }" },
        { label: "HIE Queue Monitor", link: "${ ui.pageLink("rwandaemr", "admin/hieQueueMonitor") }" }
    ];

    jq(document).ready(function() {
        jq(".delete-queue-item").submit(function() {
            return confirm("Delete this queue item?");
        });
    });
</script>

<style>
    .hie-monitor-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 16px;
        margin-bottom: 16px;
    }

    .hie-monitor-header h3 {
        margin: 0;
    }

    .hie-inline-form {
        display: inline-block;
        margin: 0 4px 4px 0;
    }

    .hie-summary-grid {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
        gap: 12px;
        margin-bottom: 18px;
    }

    .hie-summary {
        border: 1px solid #ddd;
        border-radius: 4px;
        padding: 12px;
        background: #fff;
    }

    .hie-summary-title {
        font-weight: bold;
        margin-bottom: 10px;
    }

    .hie-summary-counts {
        display: grid;
        grid-template-columns: repeat(5, minmax(40px, 1fr));
        gap: 8px;
        margin-bottom: 10px;
    }

    .hie-count-label {
        color: #666;
        font-size: 0.85em;
    }

    .hie-count-value {
        display: block;
        font-size: 1.25em;
        font-weight: bold;
    }

    .hie-filters {
        display: flex;
        flex-wrap: wrap;
        align-items: end;
        gap: 10px;
        margin: 0 0 16px 0;
    }

    .hie-filter-field label {
        display: block;
        font-weight: bold;
        margin-bottom: 3px;
    }

    .hie-filter-field input,
    .hie-filter-field select {
        min-width: 180px;
    }

    .hie-queue-table {
        width: 100%;
        table-layout: fixed;
    }

    .hie-queue-table th,
    .hie-queue-table td {
        vertical-align: top;
        overflow-wrap: anywhere;
    }

    .hie-queue-table th:nth-child(1) {
        width: 13%;
    }

    .hie-queue-table th:nth-child(2) {
        width: 8%;
    }

    .hie-queue-table th:nth-child(3) {
        width: 20%;
    }

    .hie-queue-table th:nth-child(4) {
        width: 9%;
    }

    .hie-queue-table th:nth-child(5) {
        width: 8%;
    }

    .hie-queue-table th:nth-child(6) {
        width: 12%;
    }

    .hie-queue-table th:nth-child(7) {
        width: 18%;
    }

    .hie-queue-table th:nth-child(8) {
        width: 12%;
    }

    .hie-status {
        border-radius: 3px;
        color: #fff;
        display: inline-block;
        font-size: 0.85em;
        font-weight: bold;
        padding: 2px 7px;
        text-transform: uppercase;
    }

    .hie-status-pending {
        background: #3478a8;
    }

    .hie-status-failed {
        background: #b35a00;
    }

    .hie-status-exhausted,
    .hie-status-unreadable {
        background: #9d1d20;
    }

    .hie-response-preview {
        background: #f7f7f7;
        border: 1px solid #e0e0e0;
        border-radius: 3px;
        max-height: 95px;
        overflow: auto;
        padding: 6px;
        white-space: pre-wrap;
    }

    .hie-muted {
        color: #666;
    }

    .hie-empty {
        border: 1px solid #ddd;
        padding: 18px;
        background: #fff;
    }
</style>

<% if (!authorized) { %>
    <div class="note-container">
        <div class="note warning">You do not have permission to view this page.</div>
    </div>
<% } else { %>
    <div class="hie-monitor-header">
        <h3>HIE Queue Monitor</h3>
        <form class="hie-inline-form" method="post" action="${ ui.pageLink("rwandaemr", "admin/hieQueueMonitor") }">
            <input type="hidden" name="action" value="process" />
            <input type="hidden" name="queueType" value="all" />
            <input type="hidden" name="filterQueue" value="${ ui.encodeHtmlContent(selectedQueue) }" />
            <input type="hidden" name="filterStatus" value="${ ui.encodeHtmlContent(selectedStatus) }" />
            <input type="hidden" name="filterQ" value="${ ui.encodeHtmlContent(search) }" />
            <button type="submit" class="button confirm">
                <i class="icon-play"></i>
                Process All Queues
            </button>
        </form>
    </div>

    <div class="hie-summary-grid">
        <% dashboard.summaries.each { summary -> %>
            <div class="hie-summary">
                <div class="hie-summary-title">${ ui.encodeHtmlContent(summary.definition.displayName) }</div>
                <div class="hie-summary-counts">
                    <div>
                        <span class="hie-count-label">Total</span>
                        <span class="hie-count-value">${ summary.total }</span>
                    </div>
                    <div>
                        <span class="hie-count-label">Pending</span>
                        <span class="hie-count-value">${ summary.pending }</span>
                    </div>
                    <div>
                        <span class="hie-count-label">Failed</span>
                        <span class="hie-count-value">${ summary.failed }</span>
                    </div>
                    <div>
                        <span class="hie-count-label">Exhausted</span>
                        <span class="hie-count-value">${ summary.exhausted }</span>
                    </div>
                    <div>
                        <span class="hie-count-label">Unreadable</span>
                        <span class="hie-count-value">${ summary.unreadable }</span>
                    </div>
                </div>
                <form class="hie-inline-form" method="post" action="${ ui.pageLink("rwandaemr", "admin/hieQueueMonitor") }">
                    <input type="hidden" name="action" value="process" />
                    <input type="hidden" name="queueType" value="${ ui.encodeHtmlContent(summary.definition.type) }" />
                    <input type="hidden" name="filterQueue" value="${ ui.encodeHtmlContent(selectedQueue) }" />
                    <input type="hidden" name="filterStatus" value="${ ui.encodeHtmlContent(selectedStatus) }" />
                    <input type="hidden" name="filterQ" value="${ ui.encodeHtmlContent(search) }" />
                    <button type="submit" class="button">
                        <i class="icon-play"></i>
                        Process
                    </button>
                </form>
            </div>
        <% } %>
    </div>

    <form class="hie-filters" method="get" action="${ ui.pageLink("rwandaemr", "admin/hieQueueMonitor") }">
        <div class="hie-filter-field">
            <label for="hie-filter-queue">Queue</label>
            <select id="hie-filter-queue" name="queue">
                <option value="all" ${ selectedQueue == "all" ? "selected=\"selected\"" : "" }>All queues</option>
                <% queueDefinitions.each { queueDefinition -> %>
                    <option value="${ ui.encodeHtmlContent(queueDefinition.type) }" ${ selectedQueue == queueDefinition.type ? "selected=\"selected\"" : "" }>
                        ${ ui.encodeHtmlContent(queueDefinition.displayName) }
                    </option>
                <% } %>
            </select>
        </div>
        <div class="hie-filter-field">
            <label for="hie-filter-status">Status</label>
            <select id="hie-filter-status" name="status">
                <% statusOptions.each { statusOption -> %>
                    <option value="${ ui.encodeHtmlContent(statusOption) }" ${ selectedStatus == statusOption ? "selected=\"selected\"" : "" }>
                        ${ ui.encodeHtmlContent(statusOption.capitalize()) }
                    </option>
                <% } %>
            </select>
        </div>
        <div class="hie-filter-field">
            <label for="hie-filter-q">Search</label>
            <input id="hie-filter-q" type="text" name="q" value="${ ui.encodeHtmlContent(search) }" />
        </div>
        <button type="submit" class="button">
            <i class="icon-filter"></i>
            Filter
        </button>
        <a class="button" href="${ ui.pageLink("rwandaemr", "admin/hieQueueMonitor") }">
            <i class="icon-refresh"></i>
            Reset
        </a>
    </form>

    <% if (dashboard.items.isEmpty()) { %>
        <div class="hie-empty">No queue items match the selected filters.</div>
    <% } else { %>
        <table class="hie-queue-table">
            <thead>
                <tr>
                    <th>Queue</th>
                    <th>Status</th>
                    <th>UUID</th>
                    <th>Event</th>
                    <th>Attempts</th>
                    <th>Latest Attempt</th>
                    <th>Response / File</th>
                    <th>Actions</th>
                </tr>
            </thead>
            <tbody>
                <% dashboard.items.each { item -> %>
                    <tr>
                        <td>${ ui.encodeHtmlContent(item.definition.displayName) }</td>
                        <td>
                            <span class="hie-status hie-status-${ ui.encodeHtmlContent(item.status) }">
                                ${ ui.encodeHtmlContent(item.status) }
                            </span>
                        </td>
                        <td>
                            ${ ui.encodeHtmlContent(item.entityUuid ?: "") }
                            <div class="hie-muted">${ ui.encodeHtmlContent(item.eventDatetimeDisplay ?: "") }</div>
                        </td>
                        <td>${ ui.encodeHtmlContent(item.eventType ?: "") }</td>
                        <td>${ item.attemptCount }</td>
                        <td>
                            ${ ui.encodeHtmlContent(item.latestAttemptDatetimeDisplay ?: "") }
                            <div class="hie-muted">Modified: ${ ui.encodeHtmlContent(item.lastModifiedDatetimeDisplay ?: "") }</div>
                        </td>
                        <td>
                            <% if (item.latestAttemptResponsePreview) { %>
                                <pre class="hie-response-preview">${ ui.encodeHtmlContent(item.latestAttemptResponsePreview) }</pre>
                            <% } %>
                            <div class="hie-muted">
                                ${ ui.encodeHtmlContent(item.fileName) } (${ item.fileSize } bytes)
                            </div>
                        </td>
                        <td>
                            <% if (item.status != "unreadable") { %>
                                <form class="hie-inline-form" method="post" action="${ ui.pageLink("rwandaemr", "admin/hieQueueMonitor") }">
                                    <input type="hidden" name="action" value="retry" />
                                    <input type="hidden" name="queueType" value="${ ui.encodeHtmlContent(item.definition.type) }" />
                                    <input type="hidden" name="fileName" value="${ ui.encodeHtmlContent(item.fileName) }" />
                                    <input type="hidden" name="filterQueue" value="${ ui.encodeHtmlContent(selectedQueue) }" />
                                    <input type="hidden" name="filterStatus" value="${ ui.encodeHtmlContent(selectedStatus) }" />
                                    <input type="hidden" name="filterQ" value="${ ui.encodeHtmlContent(search) }" />
                                    <button type="submit" class="button">
                                        <i class="icon-repeat"></i>
                                        Retry
                                    </button>
                                </form>
                            <% } %>
                            <form class="hie-inline-form delete-queue-item" method="post" action="${ ui.pageLink("rwandaemr", "admin/hieQueueMonitor") }">
                                <input type="hidden" name="action" value="delete" />
                                <input type="hidden" name="queueType" value="${ ui.encodeHtmlContent(item.definition.type) }" />
                                <input type="hidden" name="fileName" value="${ ui.encodeHtmlContent(item.fileName) }" />
                                <input type="hidden" name="filterQueue" value="${ ui.encodeHtmlContent(selectedQueue) }" />
                                <input type="hidden" name="filterStatus" value="${ ui.encodeHtmlContent(selectedStatus) }" />
                                <input type="hidden" name="filterQ" value="${ ui.encodeHtmlContent(search) }" />
                                <button type="submit" class="button cancel">
                                    <i class="icon-trash"></i>
                                    Delete
                                </button>
                            </form>
                        </td>
                    </tr>
                <% } %>
            </tbody>
        </table>
    <% } %>
<% } %>
