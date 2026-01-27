<%
    ui.decorateWith("appui", "standardEmrPage")
%>

<script type="text/javascript">
    var breadcrumbs = [
        { icon: "icon-home", link: '/' + OPENMRS_CONTEXT_PATH + '/index.htm' },
        { label: "${ ui.message("coreapps.app.system.administration.label") }", link: "${ ui.pageLink("coreapps", "systemadministration/systemAdministration") }" },
        { label: "Configure Lab Tests", link: "${ ui.pageLink("rwandaemr", "admin/configureLabTests") }" }
    ];
</script>

<style>
    .test-category {
        font-weight: bold;
        padding-top: 20px;
        padding-bottom: 10px;
    }
    .test-in-category {
        padding-left: 10px;
    }
</style>

<h3>Configure Lab Tests that are available for ordering in this instance</h3>

<form method="post">
    <% labSet.setMembers.each { category -> %>
        <div class="test-category">${ labOrderConfig.formatConcept(category) }</div>
        <% category.setMembers.each { labTest -> %>
            <% def testSelected = labTestsByCategory.isEmpty() || (labTestsByCategory.get(category) && labTestsByCategory.get(category).contains(labTest)) %>
            <div class="test-in-category">
                <input type="checkbox" name="labTests" value="${labTest.id}" ${testSelected ? " checked=\"checked\"" : ""} />
                ${ labOrderConfig.formatConcept(labTest) }
            </div>
        <% } %>
    <% } %>
    <br/>
    <input type="submit" value="Save configuration"/>
</form>
