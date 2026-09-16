<%
    def patientId = patient?.patient?.patientId
    def labourCareGuideUrl = patientId ? ui.pageLink('rwandaemr', 'patient/labourCareGuide', ['patientId': patientId]) : null
    def title = ui.message('rwandaemr.labourCareGuide.label', 'LABOUR CARE GUIDE').toUpperCase()
    def viewFullTitle = ui.message('rwandaemr.labourCareGuide.viewFull', 'View full Labour Care Guide')
    def noObservationsMessage = ui.message('rwandaemr.labourCareGuide.noObservations', 'No Labour Care Guide observations recorded yet.')
    def observationsLabel = ui.message('rwandaemr.labourCareGuide.observations', 'Observations')
    def lastLabel = ui.message('rwandaemr.labourCareGuide.lastAt', 'Last')
    def viewGuideLabel = ui.message('rwandaemr.labourCareGuide.viewGuide', 'View Labour Care Guide')
%>
<div class="info-section">
    <div class="info-header">
        <i class="fas fa-fw fa-heartbeat"></i>
        <h3>${ title }</h3>
        <% if (labourCareGuideUrl) { %>
        <a href="${ labourCareGuideUrl }" class="right">
            <i class="icon-share-alt edit-action" title="${ viewFullTitle }"></i>
        </a>
        <% } %>
    </div>
    <div class="info-body">
        <% if (encounterCount == 0) { %>
            <p style="color: #888; font-style: italic;">${ noObservationsMessage }</p>
        <% } else { %>
            <div style="font-size: 0.85em; color: #555; margin-bottom: 8px;">
                <i class="icon-time"></i>
                ${ observationsLabel }: <strong>${ encounterCount }</strong>
                <% if (latestEncounter) { %>
                    &nbsp;-&nbsp;${ lastLabel }:
                    <strong>${ ui.formatDatePretty(latestEncounter.encounterDatetime) }</strong>
                <% } %>
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

        <% if (labourCareGuideUrl) { %>
        <div style="margin-top: 8px; text-align: right;">
            <a href="${ labourCareGuideUrl }" style="font-size: 0.8em;">
                ${ viewGuideLabel } &rarr;
            </a>
        </div>
        <% } %>
    </div>
</div>
