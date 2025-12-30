<%
    ui.decorateWith("appui", "standardEmrPage")
    def visitAndLoginLocations = locationTagUtil.getValidVisitAndLoginLocations()
    def visitLocations = visitAndLoginLocations.keySet()
%>

<script type="text/javascript" xmlns="http://www.w3.org/1999/html" xmlns="http://www.w3.org/1999/html">
    var breadcrumbs = [
        { icon: "icon-home", link: '/' + OPENMRS_CONTEXT_PATH + '/index.htm' },
        { label: "${ ui.message("rwandaemr.login.chooseLocation.title") }" }
    ];
</script>

<style>
    #visit-location-section {
        padding-bottom: 20px;
    }
    .login-location-section {
        display: none;
    }
    ul.select li.visit-location-selected {
        background-color: #007FFF;
        color: white;
    }
</style>

<script type="text/javascript">
    jq(document).ready(function() {

        function showLoginLocationSection(visitLocationId) {
            console.log('Visit Location selected ' + visitLocationId);
            jq("#login-location-section").hide();
            jq(".login-location-item").hide();
            let loginLocationElements = jq(".login-location-item-"+visitLocationId);
            if (loginLocationElements.length === 1) {
                jq(loginLocationElements[0]).click();
            }
            else {
                console.log('Showing ' + loginLocationElements.size() + " login locations");
                jq(loginLocationElements).show();
                jq("#login-location-section").show();
            }
        }

        <% if (visitLocations.size() == 1) { %>
            showLoginLocationSection('${visitLocations.iterator().next().id}');
        <% } %>


        jq(".visit-location-select .location-list-item").click(function() {
            let id = jq(this).attr('value');
            jq(".visit-location-select li").removeClass('visit-location-selected');
            jq(this).addClass('visit-location-selected');
            showLoginLocationSection(id);
        });

        jq(".login-location-select .location-list-item").click(function() {
            let id = jq(this).attr('value');
            console.log('Login Location selected ' + id);
            jq("#session-location-input").val(id);
            <% if (!locationTagUtil.isLocationSetupRequired()) { %>
                jq("#login-location-form").submit();
            <% } %>
        });
    });
</script>

<% if (locationTagUtil.isLocationSetupRequired()) { %>
    <style>
    .setup-location-tag-link {
        color: blue;
        text-decoration: underline;
    }
    </style>
    <div class="note-container">
        <div class="note warning" style="width: 100%;">
            <div class="text">
                <i class="fas fa-fw fa-exclamation-circle" style="vertical-align: middle;"></i>
                <% if (sessionContext.currentUser.hasPrivilege("App: coreapps.systemAdministration")) { %>
                    <a class="setup-location-tag-link" href="${ ui.pageLink("rwandaemr", "admin/configureLoginLocations") }">
                        ${ ui.message("rwandaemr.login.warning.invalidLoginLocations") }
                    </a>
                <% } else { %>
                    ${ ui.message("rwandaemr.login.warning.invalidLoginLocations") }
                <% } %>
            </div>
        </div>
    </div>
<% } %>

<form id="login-location-form" method="post">
    <!-- only show visit location selector if there are multiple locations to choose from -->
    <% if (visitLocations.size() > 1) { %>
        <div class="clear" id="visit-location-section">
            <label>
                ${ ui.message("rwandaemr.login.chooseVisitLocation.title") }:
            </label>
            <ul class="select visit-location-select">
                <% visitLocations.each { visitLocation -> %>
                    <li id="visit-location-select-item-${visitLocation.id}" class="location-list-item" value="${visitLocation.id}">${ui.format(visitLocation)}</li>
                <% } %>
            </ul>
        </div>
    <% } %>

    <% if (visitLocations.size() == 1) { %>
        <h3>${ ui.format(visitLocations.iterator().next()) }</h3>
    <% } %>
    <div class="clear login-location-section" id="login-location-section">
        <label>
            ${ ui.message("rwandaemr.login.chooseLoginLocation.title") }:
        </label>
        <ul id="login-location-select" class="select login-location-select">
            <% visitLocations.each { visitLocation ->
                def loginLocations = visitAndLoginLocations.get(visitLocation)
                loginLocations.each { loginLocation -> %>
                    <li class="location-list-item login-location-item login-location-item-${visitLocation.id}" value="${loginLocation.id}">${ui.format(loginLocation)}</li>
                <% } %>
            <% } %>
        </ul>
    </div>

    <input id="session-location-input" type="hidden" name="sessionLocation" />
</form>