<%
    def patientId = patient?.patient?.patientId
%>
<div class="info-section">
    <div class="info-header">
        <i class="fas fa-fw fa-procedures"></i>
        <h3>${ ui.message("rwandaemr.anesthesiaRecord.label", "ANESTHESIA RECORD").toUpperCase() }</h3>
        <% if (patientId) { %>
        <a href="${ ui.pageLink("rwandaemr", "patient/anesthesiaRecord", ["patientId": patientId]) }" class="right">
            <i class="icon-share-alt edit-action" title="${ ui.message("rwandaemr.anesthesiaRecord.viewFull", "View full anesthesia record") }"></i>
        </a>
        <% } %>
    </div>
    <div class="info-body">
        <% if (encounterCount == 0) { %>
            <p style="color: #888; font-style: italic;">${ ui.message("rwandaemr.anesthesiaRecord.noObservations", "No anesthesia observations recorded yet.") }</p>
        <% } else { %>
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px;">
                <span style="font-size: 0.85em; color: #555;">
                    <i class="icon-time"></i>
                    ${ ui.message("rwandaemr.anesthesiaRecord.observations", "Observations") }: <strong>${ encounterCount }</strong>
                    <% if (latestEncounter) { %>
                        &nbsp;-&nbsp;${ ui.message("rwandaemr.anesthesiaRecord.lastAt", "Last") }:
                        <strong>${ ui.formatDatePretty(latestEncounter.encounterDatetime) }</strong>
                    <% } %>
                </span>
            </div>

            <% if (!latestValues.isEmpty()) { %>
            <table style="width: 100%; font-size: 0.85em; border-collapse: collapse;">
                <% latestValues.each { label, value -> %>
                <tr style="border-bottom: 1px solid #eee;">
                    <td style="padding: 2px 4px; color: #555;">${ label }</td>
                    <td style="padding: 2px 4px; font-weight: bold; text-align: right;">${ value }</td>
                </tr>
                <% } %>
            </table>
            <% } %>
        <% } %>

        <% if (patientId) { %>
        <div style="margin-top: 8px; text-align: right;">
            <a href="${ ui.pageLink("rwandaemr", "patient/anesthesiaRecord", ["patientId": patientId]) }"
               style="font-size: 0.8em;">
                ${ ui.message("rwandaemr.anesthesiaRecord.viewCharts", "View charts &amp; record") } &rarr;
            </a>
        </div>
        <% } %>
    </div>
</div>
