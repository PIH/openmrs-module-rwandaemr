<%
    def patient = config.patient
%>
<div class="info-section">
    <div class="info-header">
        <i class="icon-calendar"></i>
        <h3>${ ui.message(config.label ? config.label : "rwandaemr.clinicianfacing.recentEncounters").toUpperCase() }</h3>
        <a href="${ui.pageLink("rwandaemr", "patient/encounterList", ["patientId": patient.patient.patientId])}" class="right">
            <i class="icon-share-alt edit-action" title="${ ui.message("rwandaemr.clinicianfacing.recentEncounters") }"></i>
        </a>
    </div>
    <div class="info-body">
        <% if (encounters.isEmpty()) { %>
        ${ui.message("coreapps.none")}
        <% } %>
        <ul>
            <% encounters.each { encounter ->
                def url = ui.pageLink("htmlformentryui", "htmlform/viewEncounterWithHtmlForm", [
                        "patientId"     : encounter.patient.uuid,
                        "encounter"   : encounter.uuid,
                        "returnProvider": "coreapps",
                        "returnPage"    : "clinicianfacing/patient"])
            %>
            <li class="clear">
                <% if (encounter.form) { %>
                    <a id="${encounter.id}" href="${url}" class="encounter-link">
                        <script type="text/javascript">
                            jq("#${encounter.id}.encounter-link").click(function () {
                                window.location.href = "${url}";
                            });
                        </script>
                        ${ ui.formatDatePretty(encounter.encounterDatetime) }
                    </a>
                <% } else { %>
                    ${ ui.formatDatePretty(encounter.encounterDatetime) }
                <% } %>
                <span id="encountertype-tag-${encounter.id}" class="tag" >
                    ${ ui.format(encounter.form ?: encounter.encounterType)}
                </span>
            </li>
            <% } %>
        </ul>
    </div>
</div>
