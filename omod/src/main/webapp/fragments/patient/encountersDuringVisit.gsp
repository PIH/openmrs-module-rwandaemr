<%
    def elementId = ui.randomId('encounters-during-visit-container') + "-"

    def definitionUiResource = config.definitionUiResource;
    def returnUrl = config.returnUrl;

    def editIcon = config.editIcon ?: "icon-pencil"
    def editProvider = config.editProvider ?: "htmlformentryui"
    def editFragment = config.editFragment ?: "htmlform/editHtmlFormWithStandardUi"
    def editParams = config.editParams ?: [patientId: patient.id, encounterId: "{encounterId}", definitionUiResource: definitionUiResource, returnUrl: returnUrl ]

    def creatable = config.creatable
    def createIcon = config.createIcon ?: "icon-plus"
    def createProvider = config.createProvider ?: "htmlformentryui"
    def createFragment = config.createFragment ?: "htmlform/enterHtmlFormWithStandardUi"
    def createParams = config.createParams ?: [patientId: patient.id, visitId: visit?.id, createVisit: "true", definitionUiResource: definitionUiResource, returnUrl: returnUrl ]
    def encCreateParams = [:]
    createParams.each{p ->
        def val = p.value;
        if (val != null) {
            if (val instanceof String) {
                val = val.replace("{patientId}", patient.id.toString()).replace("{visitId}", (visit?.id ? visit.id.toString() : ""))
            }
            encCreateParams.put(p.key, val);
        }
    }
    def noEncountersMessage = config.noEncountersMessage ?: "coreapps.clinicianfacing.noneRecorded"
%>

<div id="${ app.id ? app.id.replaceAll('\\.','-') : '' }" class="info-section encounters-during-visit">
    <div class="info-header">
        <i class="${ app.icon }"></i>
        <h3>${ ui.message(app.label).toUpperCase() }</h3>
        <% if (creatable && definitionUiResource != null && encounters.isEmpty()) { %>
            <i class="${createIcon} edit-action right" title="${ ui.message("coreapps.add") }"
               onclick="location.href='${ui.pageLink(createProvider, createFragment, encCreateParams)}';">
            </i>
        <% } %>
    </div>
    <div class="info-body">
        <% if (encounters.isEmpty()) { %>
            ${ ui.message(noEncountersMessage) }
        <% } else { %>

            <% encounters.eachWithIndex { encounter, encounterIndex ->
                def currentId = elementId + "-" + encounter.id
                def encEditParams = [:]
                editParams.each{p ->
                    def val = p.value;
                    if (val != null) {
                        if (val instanceof String) {
                            val = val.replace("{patientId}", patient.id.toString()).replace("{encounterId}", encounter.id.toString()).replace("{visitId}", encounter.visit?.id?.toString());
                        }
                        encEditParams.put(p.key, val);
                    }
                }
            %>
                <div class="encounter-container row col-12">
                    <% if (encounterIndex > 0) { %><hr/><% } %>
                    <div id="${currentId}" class="in col-11">
                        ${ ui.message("uicommons.loading.placeholder") }
                    </div>
                    <div class="col-1 info-header right" style="border-bottom: none;">
                        <i class="${editIcon} edit-action" title="${ ui.message("coreapps.edit") }"
                           onclick="location.href='${ui.pageLink(editProvider, editFragment, encEditParams)}';">
                        </i>
                    </div>
                    <script type="text/javascript">
                        emr.getFragmentActionWithCallback("htmlformentryui", "htmlform/viewEncounterWithHtmlForm", "getAsHtml",
                            { encounterId: ${encounter.id}, definitionUiResource: '${ definitionUiResource }' },
                            function(result) {
                                jq('#${currentId}').html(result.html);
                                <% if (config.classToHide) { %>
                                    jq('#${currentId} .${config.classToHide}').hide();
                                <% } %>
                                <% if (config.classToShow) { %>
                                    jq('#${currentId} .${config.classToShow}').show();
                                <% } %>
                            });
                    </script>
                </div>
            <% } %>
        <% } %>
    </div>
</div>