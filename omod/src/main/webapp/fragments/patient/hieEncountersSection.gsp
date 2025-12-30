<%
ui.includeJavascript("rwandaemr", "custom/hie.js")
ui.includeCss("rwandaemr", "hie/hie.css")
%>

<div id="quick-view-dialog" class="dialog" style="display: none">
    <div class="dialog-header">
        <i class="icon-check-in"></i>
        <h3 id="encounter_title">
            ${ ui.message("rwandaemr.hie.viewObs.title") }
        </h3>
    </div>
    <div class="dialog-content">
        <p class="dialog-instructions" id="encounter_header">
            ${ ui.message("rwandaemr.hie.viewObs.instructions") }
        </p>
        <div id="hie-observation-data"></div>
        <button id="" class="confirm right">${ ui.message("rwandaemr.hie.done") }<i class="icon-spinner icon-spin icon-2x" style="display: none; margin-left: 10px;"></i></button>
        <button class="cancel">${ ui.message("coreapps.cancel") }</button>
    </div>
</div>
<div class="info-section">
    <div class="info-header">
        <i class="icon-calendar"></i>
        <h3>${ ui.message(config.label ? config.label : "rwandaemr.clinicianfacing.recentHieEncounters").toUpperCase() }</h3>
        
    </div>
    <div class="info-body">
        <g:if test="${error}">
            <span style="color: red;">${error}</span>
        </g:if>
        
        <% if(visit_list.size() == 0) { %>
            <span style="color: red;">${ ui.message("emr.none") }</span>
        <% } %>
        <div class="accordion">
            <%
            visit_list.each { vle ->
                %>
                <div class="accordion-item">
                    <div class="accordion-header">${ ui.format(vle.getLocation()) }</div>
                    <div class="accordion-content">
                        <table id="encounter-list-table">
                            <thead>
                                <tr>
                                    <th>${ ui.message("rwandaemr.encounterList.encounterDatetime") }</th>
                                    <th>${ ui.message("rwandaemr.encounterList.encounterType") }</th>
                                    <th>${ ui.message("rwandaemr.encounterList.location") }</th>
                                    <th>${ ui.message("rwandaemr.hie.encounterLis.actions") }</th>
                                </tr>
                            </thead>
                            <tbody>
                                <% if (vle.getEncounters().size() == 0) { %>
                                    <tr>
                                        <td colspan="4">${ ui.format("emr.none") }</td>
                                    </tr>
                                <% } %>
                                <% vle.getEncounters().each { e ->
                                    def pageLink
                                    %>
                                    <tr id="encounter-${ e.uuid }" class="encounter-row${pageLink ? ' pointer' :''}" data-href="#">
                                        <td class="date-column">
                                            ${ ui.format(e.encounterDatetime) }
                                        </td>
                                        <td class="encounterTypeColumn${pageLink ? ' encounter-link' :''}">
                                            ${ ui.format(e.voidReason) }
                                        </td>
                                        <td class="formColumn${pageLink ? ' encounter-link' :''}">
                                            ${ ui.format(e.location) }
                                        </td>
                                        <td>
                                            <a class="open_encounter_pop" data-encounter_date="${ ui.format(e.encounterDatetime) }" data-encounter_type="${ ui.format(e.voidReason) }" data-encounter_location="${ ui.format(e.location) }" data-url='${ui.pageLink("rwandaemr", "patient/hieObservationsSection")}?encounterUuid=${e.uuid}' data-uuid="${e.uuid}" title="Preview Observation on this encounter" href="javascript:hie.showQuickViewDialog('${e.uuid}')"><i class="fas fa-eye"></i></a>
                                        </td>

                                    </tr>
                                <% } %>
                            </tbody>
                        </table>
                    </div>
                </div>
                <%
            }
            %>
        </div>
    </div>
</div>

<style>
.accordion-header {
  background: #00473f;
  color: white;
  padding: 10px;
  cursor: pointer;
}
.accordion-content {
  display: none;
  padding: 10px;
  border: 1px solid #00473f;
  border-top: none;
}
</style>

<script>
  jq(document).ready(function () {
    jq('.accordion-header').click(function () {
      const content = jq(this).next('.accordion-content');

      // Close all other contents
      jq('.accordion-content').not(content).slideUp();

      // Toggle current content
      content.slideToggle();
    });
  });
</script>

<script type="text/javascript">
    jq(".open_encounter_pop").click(function (e) {
        //e.preventDefault();

        var clicked = jq(this);
        //console.log(clicked.data("uuid"));
        var url = window.location.origin + clicked.data("url");
        jq("#encounter_title").html(clicked.data("encounter_type"));
        jq("p#encounter_header").html(clicked.data("encounter_location") + " Date: " + clicked.data("encounter_date"));
        jq("#hie-observation-data").load(url, function (response, status, xhr) {
            if (status == "error") {
                jq("#hie-observation-data").html("<p>Error loading observations.</p>");
            }
            //jq("#fragmentContainer").html(response);
        });

    });
</script>