<%
    def patient = config.patient
%>
<div class="info-section">
    <div class="info-header">
        <i class="icon-briefcase"></i>
        <h3>${ ui.message(config.label ? config.label : "rwandaemr.insurancePolicies").toUpperCase() }</h3>
        <a href="${ui.pageLink("rwandaemr", "patient/insurancePolicies", ["patientId": patient.patient.patientId])}" class="right">
            <i class="icon-share-alt edit-action" title="${ ui.message("rwandaemr.insurancePolicies") }"></i>
        </a>
        <% if (sessionContext.currentUser.hasPrivilege("Create Insurance Policy")) { %>
            <a href="${ui.pageLink("rwandaemr", "patient/insurancePolicy", ["patientId": patient.patient.patientId, "edit": true])}" class="right">
                <i class="icon-plus add-action" title="${ ui.message("rwandaemr.insurancePolicies.add") }"></i>
            </a>
        <% } %>
    </div>
    <div class="info-body">
        <% if (insurancePolicies.isEmpty()) { %>
            ${ui.message("coreapps.none")}
        <% } %>
        <% insurancePolicies.each { policy -> %>
            <div>
                <h3>${ policy.insurance.name }</h3>
                <p class="left">
                    <a href="${ui.pageLink("rwandaemr", "patient/insurancePolicy", [
                            "patientId": patient.patient.patientId,
                            "policyId": policy.insurancePolicyId])}">
                        ${ policy.insuranceCardNo }
                    </a>
                </p>
            </div>
        <% } %>
    </div>
</div>
