<%
    ui.decorateWith("appui", "standardEmrPage")
    def configurationIsValid = !locationTagUtil.isLocationSetupRequired()
    def validVisitLocations = locationTagUtil.getValidVisitLocations()
    def validLoginLocations = locationTagUtil.getValidLoginLocations()
    def locationsWithChildren = []
    allLocations.each{l ->
        if (l.childLocations != null && !l.childLocations.isEmpty()) {
            locationsWithChildren.add(l)
        }
    }
%>

<script type="text/javascript" xmlns="http://www.w3.org/1999/html" xmlns="http://www.w3.org/1999/html">
    var breadcrumbs = [
        { icon: "icon-home", link: '/' + OPENMRS_CONTEXT_PATH + '/index.htm' },
        { label: "${ ui.message("coreapps.app.system.administration.label") }", link: "${ ui.pageLink("coreapps", "systemadministration/systemAdministration") }" },
        { label: "${ ui.message("rwandaemr.admin.configureLoginLocations") }", link: "${ ui.pageLink("rwandaemr", "admin/configureLoginLocations") }" }
    ];
</script>

<style>
    #login-location-instructions {
        font-size: smaller;
    }
    #login-location-instructions p {
        padding: 10px;
    }
    #login-location-form {
        padding: 20px;
    }
    .system-type-option-description {
        display: none;
        padding-left: 20px;
        color: blue;
    }
    .system-type-section {
        display: none;
        padding: 20px;
    }
    .multi-department-login-location-section {
        display: none;
        padding: 20px;
    }
    .login-location-checkboxes input[type=checkbox] {
        float: unset;
    }
</style>

<script type="text/javascript">
    jq(document).ready(function() {

        function setupInitialValues() {
            <% if (configurationIsValid && validVisitLocations.size() == 1) { %>
                <% if (systemType == locationTagUtil.SINGLE_LOCATION) { %>
                    jq("#singleLocationWidget-field").val('${validVisitLocations.get(0).id}');
                <% } else if (systemType == locationTagUtil.MULTI_DEPARTMENT) { %>
                    let visitLocationWidget = jq("#multiDepartmentVisitLocationWidget");
                    jq(visitLocationWidget).val('${validVisitLocations.get(0).id}');
                    jq(visitLocationWidget).change();
                <% } %>
            <% } %>
            jq("input[name='systemType'][value='${systemType}']").click();
        }

        jq("input[name='systemType']").click(function() {
            let value = jq(this).val();
            jq(".system-type-section").hide();
            jq("#system-type-section-" + value).show();
        });

        jq("#multiDepartmentVisitLocationWidget").change(function() {
            let value = jq(this).val();
            jq(".multi-department-login-location-section").hide();
            jq("#multi-department-login-location-section-" + value).show();
        });

        setupInitialValues();
    });
</script>

<h3>${ui.message("rwandaemr.admin.configureLoginLocations")}</h3>

<div class="note-container">
    <% if (configurationIsValid) { %>
        <div class="note" style="width: 100%;">
            <div class="text">
                <i class="fas fa-fw fa-check-circle" style="vertical-align: middle;"></i>
                Login Locations are currently valid.  You may change the configuration via the form below.
            </div>
        </div>
    <% } else { %>
        <div class="note warning" style="width: 100%;">
            <div class="text">
                <i class="fas fa-fw fa-exclamation-circle" style="vertical-align: middle;"></i>
                Login Locations are not properly configured in this system, please complete the form below to ensure a proper setup.
            </div>
        </div>
    <% } %>
</div>

<div id="login-location-instructions">
    <p>
        <b>Login Locations:</b>:  When users authenticate into the system, their session must be associated with a Login Location.
        This location is used determine what functionality and workflows should be available, and is the default location
        associated with any <b>encounters</b> and other data that this user records during this session.
        A user can change their login location at any point in time by clicking on the name of their currently
        selected location in the page header.  Specific locations are designated as <em>Login Locations</em> by associating them
        with the <em>Login Location</em> tag.
    </p>
    <p>
        <b>Visit Locations:</b>:  Each login location must be associated with exactly one visit location. Visit locations are used to
        determine what location to assign to any <b>visits</b> that are created during the user session.  In order to do this,
        each location that is tagged as a "Login Location" must itself be tagged as a "Visit Location", or must be a descendent of
        a location tagged as a Visit Location.
    </p>
</div>

<form id="login-location-form" method="post" action="${ui.pageLink("rwandaemr", "admin/configureLoginLocations")}">
    <div id="choose-system-type"></div>
    <b>Please choose the option that best reflects this system:</b>
    <div class="system-type-option">
        <input name="systemType" value="${locationTagUtil.SINGLE_LOCATION}" type="radio" ${locationTagUtil.SINGLE_LOCATION == systemType ? "checked" : ""}/>
        Single health center or small facility, all encounters and visits are associated with the overall facility location.
        <a href="#" onclick="jq('#single-visit-single-login-description').toggle()">More info</a>
        <p id="single-visit-single-login-description" class="system-type-option-description">
            Health centers and similar smaller facilities will typically configure a location representing the overall facility
            as the only Visit Location, and will also tag this location as the only Login Location.  This will eliminate the need
            for users to choose their location after login, and will automatically assign all user sessions to this single login location.
            All encounters, visits, and other data will be associated with this overall facility location.
            and will configure this same location as the Visit Location.
        </p>
    </div>
    <div class="system-type-option">
        <input name="systemType" value="${locationTagUtil.MULTI_DEPARTMENT}" type="radio" ${locationTagUtil.MULTI_DEPARTMENT == systemType ? "checked" : ""}/>
        Single hospital or multi-department facility, all visits at a single facility, with encounters at different departments within that facility
        <a href="#" onclick="jq('#single-visit-multiple-login-description').toggle()">More info</a>
        <p id="single-visit-multiple-login-description" class="system-type-option-description">
            Hospitals and similar larger facilities will typically configure a location representing the overall facility
            as the only Visit Location, but will then create locations representing each department/service that have this
            Visit Location as their parent location.  They will tag the department/service locations as login locations, but
            the overall facility visit location will not be tagged as a login location.
            This will enable encounters to be associated with individual department locations, and for multiple encounters
            at different departments within the facility to be associated with the same Visit at the parent visit location.
        </p>
    </div>
    <div class="system-type-option">
        <input name="systemType" value="${locationTagUtil.MULTI_FACILITY}" type="radio" ${locationTagUtil.MULTI_FACILITY == systemType ? "checked" : ""}/>
        System serving multiple facilities.  Visits and encounters will be created at different facilities.
        <a href="#" onclick="jq('#multiple-visit-multiple-description').toggle()">More info</a>
        <p id="multiple-visit-multiple-description" class="system-type-option-description">
            Systems that are set up to serve multiple distinct facilities will typically configure a location representing each
            of the supported facilities as a "Visit Location".  It would then configure the Login Locations associated with each
            configured Visit Location based on one of the two preferred setups above.  All Login Locations must be associated
            with only a single ancestor Visit Location.
        </p>
    </div>

    <div class="system-type-section" id="system-type-section-${locationTagUtil.SINGLE_LOCATION}">
        ${ui.includeFragment("rwandaemr", "field/location", [
                "id": "singleLocationWidget",
                "formFieldName": "singleLocation",
                "label": "Single Visit and Login Location"
        ])}
        <input type="submit" />
    </div>

    <div class="system-type-section" id="system-type-section-${locationTagUtil.MULTI_DEPARTMENT}">
        <p>
            <label for="multiDepartmentVisitLocationWidget">Facility Location to associate with all visits</label>
            <select id="multiDepartmentVisitLocationWidget" name="multiDepartmentVisitLocation">
                <option value=""></option>
                <% locationsWithChildren.each{l -> %>
                    <option value="${l.id}">${l.name}</option>
                <% } %>
            </select>
        </p>
        <% locationsWithChildren.each{ visitLoc -> %>
            <div class="multi-department-login-location-section" id="multi-department-login-location-section-${visitLoc.id}">
                <p>Please select the departments/services within ${visitLoc.name} which users will log into and create encounters</p>
                <% visitLoc.childLocations.each{ loginLoc ->
                    def selected = systemType == locationTagUtil.MULTI_DEPARTMENT && configurationIsValid && validLoginLocations.contains(loginLoc) %>
                    <input type="checkbox" name="multiDepartmentLoginLocations" value="${loginLoc.id}" ${selected ? "checked": ""}>
                    ${loginLoc.name}
                    <br/>
                <% } %>
                <input type="submit" />
            </div>
        <% } %>
    </div>

    <div class="system-type-section" id="system-type-section-${locationTagUtil.MULTI_FACILITY}">
        <p>
            Please choose the locations that should be denoted as Visit Locations and Login Locations
        </p>
        <% rootLocations.each{ l -> %>
            ${ui.includeFragment("rwandaemr", "field/admin/loginLocationCheckboxes", [
                    "location": l,
                    "padding": 10,
                    "visitLocationFormFieldName": "multiFacilityVisitLocations",
                    "loginLocationFormFieldName": "multiFacilityLoginLocations",
                    "initialVisitLocations": validVisitLocations,
                    "initialLoginLocations": validLoginLocations
            ])}
        <% } %>
        <input type="submit" />

    </div>
</form>
