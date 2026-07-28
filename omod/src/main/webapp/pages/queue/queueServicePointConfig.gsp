<%
    ui.decorateWith("appui", "standardEmrPage")
%>

<script type="text/javascript">
    var breadcrumbs = [
        { icon: "icon-home", link: '/' + OPENMRS_CONTEXT_PATH + '/index.htm' },
        { label: "Queue Service Point Mappings", link: "${ ui.pageLink("rwandaemr", "queue/queueServicePointConfig") }" }
    ];
</script>

<style type="text/css">
    .queue-config-form {
        align-items: flex-end;
        border-bottom: 1px solid #d5dddd;
        border-top: 1px solid #d5dddd;
        display: flex;
        flex-wrap: wrap;
        gap: 12px;
        margin: 16px 0 22px;
        padding: 14px 10px;
    }

    .queue-config-field {
        flex: 1 1 240px;
        max-width: 360px;
        min-width: 0;
    }

    .queue-config-field label {
        display: block;
        font-weight: bold;
        margin-bottom: 5px;
    }

    .queue-config-field select {
        box-sizing: border-box;
        min-height: 36px;
        width: 100%;
    }
</style>

<% if (!authorized) { %>
    <div class="note-container"><div class="note warning">You do not have permission to configure queue mappings.</div></div>
<% } else { %>
    <h3>Queue Service Point Mappings</h3>
    <p>Service points are OpenMRS locations tagged as Login Location.</p>

    <% if (serviceRequestedConceptError) { %>
        <div class="note-container">
            <div class="note warning">${ ui.encodeHtmlContent(serviceRequestedConceptError) }</div>
        </div>
    <% } %>

    <form class="queue-config-form" method="post"
          action="${ ui.pageLink("rwandaemr", "queue/queueServicePointConfig") }">
        <div class="queue-config-field">
            <label for="queue-service-requested">Service Requested</label>
            <select id="queue-service-requested" name="conceptUuid" required="required"
                    ${ serviceRequestedOptions.isEmpty() ? "disabled=\"disabled\"" : "" }>
                <option value="">Select service requested</option>
                <% serviceRequestedOptions.each { concept ->
                    def conceptLabel = concept.name?.name ?: concept.uuid
                %>
                    <option value="${ ui.escapeAttribute(concept.uuid) }">${ ui.encodeHtmlContent(conceptLabel) }</option>
                <% } %>
            </select>
        </div>
        <div class="queue-config-field">
            <label for="queue-service-point">Login Location service point</label>
            <select id="queue-service-point" name="servicePointId" required="required">
                <option value="">Select login location</option>
                <% servicePoints.each { sp -> %>
                    <option value="${ sp.id }">${ ui.encodeHtmlContent(sp.name) }</option>
                <% } %>
            </select>
        </div>
        <button type="submit" class="button confirm"
                ${ serviceRequestedOptions.isEmpty() ? "disabled=\"disabled\"" : "" }>Add Mapping</button>
    </form>

    <h4>Existing mappings</h4>
    <table>
        <thead><tr><th>Service Requested concept</th><th>Login Location service point</th><th>Active</th></tr></thead>
        <tbody>
            <% conceptMaps.each { map -> %>
                <tr>
                    <td>${ ui.encodeHtmlContent(map.serviceRequestedConcept?.name?.name ?: map.serviceRequestedConcept?.uuid ?: "") }</td>
                    <td>${ ui.encodeHtmlContent(map.servicePoint?.name ?: "") }</td>
                    <td>${ map.active ? "Yes" : "No" }</td>
                </tr>
            <% } %>
        </tbody>
    </table>
<% } %>
