<%
    def title = ui.message('rwandaemr.medicalPlan.label', 'MEDICAL PLAN').toUpperCase()
    def noPlanMessage = ui.message('rwandaemr.medicalPlan.noPlan', 'No medical plan recorded during the current visit.')
    def recordedByLabel = ui.message('rwandaemr.medicalPlan.recordedBy', 'Recorded by')
%>
<%/* The plan belongs to the active visit, so nothing is shown without one */%>
<% if (activeVisit) { %>
<div class="info-section">
    <div class="info-header">
        <i class="fas fa-fw fa-notes-medical"></i>
        <h3>${ title }</h3>
    </div>
    <div class="info-body">
        <% if (!latestPlan) { %>
            <p style="color: #888; font-style: italic;">${ noPlanMessage }</p>
        <% } else { %>
            <div style="font-size: 0.85em; color: #555; margin-bottom: 8px;">
                <i class="icon-time"></i>
                <strong>${ ui.formatDatetimePretty(latestPlan.obsDatetime) }</strong>
                <% if (latestPlan.encounter) { %>
                    &nbsp;-&nbsp;${ ui.encodeHtmlContent(ui.format(latestPlan.encounter.encounterType)) }
                <% } %>
                <br/>
                ${ recordedByLabel }: ${ ui.encodeHtmlContent(ui.format(latestPlan.creator)) }
            </div>
            <div style="white-space: pre-wrap; word-wrap: break-word;">${ ui.encodeHtmlContent(latestPlan.getValueAsString(ui.locale)) }</div>
        <% } %>
    </div>
</div>
<% } %>
