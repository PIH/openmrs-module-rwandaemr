<%
    ui.decorateWith("appui", "standardEmrPage")

    def insuranceOptions = []
    insurances.each {
        insuranceOptions.push([ label: ui.format(it.name), value: it.insuranceId ])
    }
    insuranceOptions = insuranceOptions.sort { it.label }

    def thirdPartyOptions = []
    thirdParties.each {
        thirdPartyOptions.push([ label: ui.format(it.name), value: it.thirdPartyId ])
    }
    thirdPartyOptions = thirdPartyOptions.sort { it.label }

    def ownerOptions = []
    owners.each {
        ownerOptions.push([ label: ui.format(it), value: it.id ])
    }
    ownerOptions = ownerOptions.sort { it.label }

    def levelOptions = [[label: "1", value: 1],[label: "2", value: 2],[label: "3", value: 3],[label: "4", value: 4]]

    def pageMode = policyModel.policyId == null ? "create" : editMode ? "edit" : "view";
    def pageTitle = ui.message("rwandaemr.insurance.policy." + pageMode) + (pageMode == "create" ? "" : ": " + policyModel.insuranceCardNo)
%>

${ ui.includeFragment("coreapps", "patientHeader", [ patient: patient.patient ]) }

<style>
    div[data-lastpass-icon-root] {
        display: none;
    }
    #insurance-policy-form fieldset {
        margin-bottom: 0;
        padding-bottom: 0;
        padding-top: 5px;
        width: 100%;
        & legend {
            font-size: 1.2rem;
            font-weight: bold;
            padding: 0;
            margin-bottom: 0;
        }
    }
    #insurance-policy-form td {
        width: 50%;
        vertical-align: top;
    }
    .field-value {
        font-weight: bold;
    }
</style>

<script type="text/javascript">
    var breadcrumbs = [
        { icon: "icon-home", link: '/' + OPENMRS_CONTEXT_PATH + '/index.htm' },
        { label: '${ui.encodeJavaScript(ui.encodeHtmlContent(ui.format(patient.patient)))}', link: '${ui.pageLink("registrationapp", "registrationSummary", ["patientId": patient.id, "appId": "rwandaemr.registerPatient"])}' },
        { label: "${ ui.message("rwandaemr.insurancePolicies")}", link: '${ui.pageLink("rwandaemr", "patient/insurancePolicies", ["patientId": patient.id])}' },
        { label: "${ pageTitle }"}
    ];
</script>

<h3>${pageTitle}</h3>

<form method="post" id="insurance-policy-form">
    <input type="hidden" value="${ui.format(policyModel.policyId)}" name="policyId" />
    <input type="hidden" value="${patient.patient.id}" name="patientId" />

    <table style="width:100%"><tr>
        <td>
            <fieldset>
                <legend>${ui.message("rwandaemr.insurance.owner")}</legend>

                <% if (editMode) { %>
                    ${ ui.includeFragment("uicommons", "field/dropDown", [
                            label: ui.message("rwandaemr.patientName"),
                            hideEmptyLabel: true,
                            formFieldName: "owner",
                            initialValue: (policyModel.owner?.id ?: ''),
                            options: ownerOptions
                    ])}
                <% } else { %>
                    <p>
                        <label for="view-insurance-owner">${ui.message("rwandaemr.patientName")}</label>
                        <span id="view-insurance-owner" class="field-value">${ui.format(policyModel.owner)}</span>
                    </p>
                <% } %>
            </fieldset>
            <fieldset>
                <legend>${ui.message("rwandaemr.insurance")}</legend>

                <% if (editMode) { %>
                    ${ ui.includeFragment("uicommons", "field/dropDown", [
                            label: ui.message("rwandaemr.insurance.name"),
                            emptyOptionLabel: "",
                            formFieldName: "insuranceId",
                            initialValue: (policyModel.insuranceId ?: ''),
                            options: insuranceOptions
                    ])}
                <% } else { %>
                <p>
                    <label for="view-insurance-name">${ui.message("rwandaemr.insurance.name")}</label>
                    <span id="view-insurance-name" class="field-value">${ui.format(policy.insurance?.name)}</span>
                </p>
                <% } %>

                <% if (editMode) { %>
                    ${ ui.includeFragment("uicommons", "field/text", [
                            label: ui.message("rwandaemr.insurance.insuranceCardNo"),
                            formFieldName: "insuranceCardNo",
                            initialValue: (policyModel.insuranceCardNo ?: ''),
                            size: 30,
                            otherAttributes: ["autocomplete": "off"]
                    ])}
                <% } else { %>
                    <p>
                        <label for="view-insurance-card-number">${ui.message("rwandaemr.insurance.insuranceCardNo")}</label>
                        <span id="view-insurance-card-number" class="field-value">${ui.format(policyModel.insuranceCardNo)}</span>
                    </p>
                <% } %>

                <% if (editMode) { %>
                    ${ ui.includeFragment("uicommons", "field/datetimepicker", [
                            id: "policy-coverarge-start-date-picker",
                            label: ui.message("rwandaemr.insurance.coverageStartDate"),
                            formFieldName: "coverageStartDate",
                            defaultDate: policyModel.coverageStartDate,
                            useTime: false
                    ])}
                <% } else { %>
                    <p>
                        <label for="view-insurance-start-date">${ui.message("rwandaemr.insurance.coverageStartDate")}</label>
                        <span id="view-insurance-start-date" class="field-value">${ui.format(policyModel.coverageStartDate)}</span>
                    </p>
                <% } %>

                <% if (editMode) { %>
                    ${ ui.includeFragment("uicommons", "field/datetimepicker", [
                            id: "policy-coverarge-expiration-date-picker",
                            label: ui.message("rwandaemr.insurance.expirationDate"),
                            formFieldName: "expirationDate",
                            defaultDate: policyModel.expirationDate,
                            useTime: false
                    ])}
                <% } else { %>
                    <p>
                        <label for="view-insurance-expire-date">${ui.message("rwandaemr.insurance.expirationDate")}</label>
                        <span id="view-insurance-expire-date" class="field-value">${ui.format(policyModel.expirationDate)}</span>
                        <% if (policyModel.expirationDate != null && policyModel.expirationDate < new Date()) { %>
                            <b style="color:red;">${ui.message("rwandaemr.expired")}</b>
                        <% } %>
                    </p>
                <% } %>

                <% if (editMode) { %>
                    ${ ui.includeFragment("uicommons", "field/dropDown", [
                            label: ui.message("rwandaemr.insurance.thirdParty"),
                            emptyOptionLabel: "",
                            formFieldName: "thirdPartyId",
                            initialValue: (policyModel.thirdPartyId ?: ''),
                            options: thirdPartyOptions
                    ])}
                <% } else { %>
                    <p>
                        <label for="view-insurance-third-party">${ui.message("rwandaemr.insurance.thirdParty")}</label>
                        <span id="view-insurance-third-party" class="field-value">${ui.format(policy.thirdParty?.name)}</span>
                    </p>
                <% } %>
            </fieldset>
        </td>
        <td>
            <fieldset>
                <legend>${ui.message("rwandaemr.insurance.ownershipInfo")}</legend>

                <% if (editMode) { %>
                    ${ ui.includeFragment("uicommons", "field/text", [
                            label: ui.message("rwandaemr.insurance.beneficiary.company"),
                            formFieldName: "company",
                            initialValue: (policyModel.company ?: ''),
                            size: 30
                    ])}
                <% } else { %>
                    <p>
                        <label for="view-insurance-beneficiary-company">${ui.message("rwandaemr.insurance.beneficiary.company")}</label>
                        <span id="view-insurance-beneficiary-company" class="field-value">${ui.format(policyModel.company)}</span>
                    </p>
                <% } %>

                <% if (editMode) { %>
                    ${ ui.includeFragment("uicommons", "field/text", [
                            label: ui.message("rwandaemr.insurance.beneficiary.ownerName"),
                            formFieldName: "ownerName",
                            initialValue: (policyModel.ownerName ?: ''),
                            size: 30
                    ])}
                <% } else { %>
                    <p>
                        <label for="view-insurance-beneficiary-ownerName">${ui.message("rwandaemr.insurance.beneficiary.ownerName")}</label>
                        <span id="view-insurance-beneficiary-ownerName" class="field-value">${ui.format(policyModel.ownerName)}</span>
                    </p>
                <% } %>

                <% if (editMode) { %>
                    ${ ui.includeFragment("uicommons", "field/text", [
                            label: ui.message("rwandaemr.insurance.beneficiary.ownerCode"),
                            formFieldName: "ownerCode",
                            initialValue: (policyModel.ownerCode ?: ''),
                            size: 30
                    ])}
                <% } else { %>
                    <p>
                        <label for="view-insurance-beneficiary-ownerCode">${ui.message("rwandaemr.insurance.beneficiary.ownerCode")}</label>
                        <span id="view-insurance-beneficiary-ownerCode" class="field-value">${ui.format(policyModel.ownerCode)}</span>
                    </p>
                <% } %>

                <!-- Policy mode level is deprecated.  Only show it if it has been previously recorded.  See RWA-979 -->
                <% if (policyModel.level) { %>

                    <% if (editMode) { %>
                        ${ ui.includeFragment("uicommons", "field/dropDown", [
                                label: ui.message("rwandaemr.insurance.beneficiary.level"),
                                emptyOptionLabel: "",
                                formFieldName: "level",
                                initialValue: (policyModel.level ?: ''),
                                options: levelOptions
                        ])}
                    <% } else { %>
                        <p>
                            <label for="view-insurance-beneficiary-level">${ui.message("rwandaemr.insurance.beneficiary.level")}</label>
                            <span id="view-insurance-beneficiary-level" class="field-value">${policyModel.level}</span>
                        </p>
                    <% } %>

                <% } %>

            </fieldset>

        </td>
    </tr></table>
    <br/>
    <div>
        <% if (editMode) { %>
            <%
                def cancelUrl = ui.pageLink("rwandaemr", "patient/insurancePolicies", ["patientId": patient.id])
                if (policyModel.policyId != null) {
                    cancelUrl = returnUrl ?: ui.pageLink("rwandaemr", "patient/insurancePolicy", ["patientId": patient.id, "policyId": policyModel.policyId])
                }
            %>
            <input type="button" class="cancel" value="${ ui.message("emr.cancel") }" onclick="document.location.href = '${cancelUrl}';" />
            <input type="submit" class="confirm" id="save-button" value="${ ui.message("emr.save") }"  />
        <% } else if (policyModel.policyId != null && sessionContext.currentUser.hasPrivilege("Edit Insurance Policy")) { %>
            <% def url = ui.pageLink("rwandaemr", "patient/insurancePolicy", ["patientId": patient.id, "policyId": policyModel.policyId, "edit": true, "returnUrl": ui.pageLink("rwandaemr", "patient/insurancePolicy", ["patientId": patient.id, "policyId": policyModel.policyId])]) %>
            <input type="button" value="${ ui.message("emr.edit") }" onclick="document.location.href = '${url}';" />
        <% } %>
    </div>
</form>