<% if (sessionContext.currentUser.hasPrivilege("App: coreapps.findPatient")) { %>
    <script type="text/javascript">
        jq(function() {
            jq('#patient-search').focus();
        });
    </script>
    ${ ui.message("rwandaemr.searchPatientHeading") }
    ${ ui.includeFragment("coreapps", "patientsearch/patientSearchWidget", [
            showLastViewedPatients: 'false',
            afterSelectedUrl: '/coreapps/clinicianfacing/patient.page?patientId={{patientId}}'
    ])}
<% } %>