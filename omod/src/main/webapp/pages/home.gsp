<%
    ui.decorateWith("appui", "standardEmrPage", [ title: "Welcome to eBuzima" ])
    ui.includeCss("rwandaemr", "home.css")

    def htmlSafeId = { extension ->
        "${ extension.id.replace(".", "-") }-${ extension.id.replace(".", "-") }-extension"
    }
%>

<script type="text/javascript">
    jq(function() {
        jq('#patient-search').focus();
    });
</script>

<div id="home-container">

    <% if (sessionContext.currentUser.hasPrivilege("App: coreapps.findPatient")) { %>

    ${ ui.message("rwandaemr.searchPatientHeading") }
    ${ ui.includeFragment("coreapps", "patientsearch/patientSearchWidget", [
            showLastViewedPatients: 'false',
            afterSelectedUrl: '/coreapps/clinicianfacing/patient.page?patientId={{patientId}}'
    ])}
    <% } %>


    <div  class="col-12 col-sm-12 col-md-12 col-lg-12 homeList" id="apps">
            <% extensions.each { ext -> %>
                <a id="${ htmlSafeId(ext) }" href="/${ contextPath }/${ ext.url }" class="btn btn-default btn-lg button app big align-self-center" type="button">
                    <% if (ext.icon) { %>
                    <i class="${ ext.icon }"></i>
                    <% } %>
                    ${ ui.message(ext.label) }
                </a>
            <% } %>
    </div>

</div>