<%
    ui.decorateWith("appui", "standardEmrPage")
%>

<script type="text/javascript">
    var breadcrumbs = [
        { icon: "icon-home", link: '/' + OPENMRS_CONTEXT_PATH + '/index.htm' },
        { label: "Queue Display", link: "${ ui.pageLink("rwandaemr", "queue/queueDisplay") }" }
    ];
</script>

<style>
    .display-wrap { font-size: 26px; }
    .display-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
    .display-grid { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 18px; }
    .display-panel { border: 1px solid #ddd; padding: 18px; min-height: 260px; }
    .display-panel h2 { margin-top: 0; font-size: 34px; }
    .queue-number { font-size: 58px; font-weight: bold; }
</style>

<% if (!authorized) { %>
    <div class="note-container"><div class="note warning">You do not have permission to view queue entries.</div></div>
<% } else {
    def nowServing = calledEntries ? calledEntries[0] : null
%>
    <div class="display-wrap">
        <div class="display-header">
            <h1>${ ui.encodeHtmlContent(selectedServicePoint?.name ?: location?.name ?: "Queue Display") }</h1>
            <form method="get" action="${ ui.pageLink("rwandaemr", "queue/queueDisplay") }">
                <select name="servicePointId">
                    <option value="">Current login location</option>
                    <% servicePoints.each { sp -> %>
                        <option value="${ sp.id }" ${ selectedServicePoint?.id == sp.id ? "selected=\"selected\"" : "" }>${ ui.encodeHtmlContent(sp.name) }</option>
                    <% } %>
                </select>
                <button type="submit" class="button">Show</button>
            </form>
        </div>
        <div class="display-grid">
            <div class="display-panel">
                <h2>Now Serving</h2>
                <% if (nowServing) { %>
                    <div class="queue-number">${ ui.encodeHtmlContent(nowServing.queueNumber) }</div>
                    <div>${ ui.encodeHtmlContent(nowServing.servicePoint?.name ?: "") }</div>
                <% } else { %>
                    <div>No patient called</div>
                <% } %>
            </div>
            <div class="display-panel">
                <h2>Next Patients</h2>
                <% waitingEntries.take(5).each { entry -> %>
                    <div>${ ui.encodeHtmlContent(entry.queueNumber) } - ${ ui.encodeHtmlContent(entry.servicePoint?.name ?: "") }</div>
                <% } %>
            </div>
            <div class="display-panel">
                <h2>Waiting Patients</h2>
                <div class="queue-number">${ waitingEntries.size() }</div>
            </div>
        </div>
    </div>
<% } %>
