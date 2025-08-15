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

    def todayDate = new Date()
    def todayPlus3Months = org.apache.commons.lang.time.DateUtils.addMonths(todayDate, 3)
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
    input:disabled {
        background-color: #EEE;
    }
</style>

<script type="text/javascript">
    var breadcrumbs = [
        { icon: "icon-home", link: '/' + OPENMRS_CONTEXT_PATH + '/index.htm' },
        { label: '${ui.encodeJavaScript(ui.encodeHtmlContent(ui.format(patient.patient)))}', link: '${ui.pageLink("registrationapp", "registrationSummary", ["patientId": patient.id, "appId": "rwandaemr.registerPatient"])}' },
        { label: "${ ui.message("rwandaemr.insurancePolicies")}", link: '${ui.pageLink("rwandaemr", "patient/insurancePolicies", ["patientId": patient.id])}' },
        { label: "${ pageTitle }"}
    ];

    const insurancesToVerify = new Map();
    <% insurancesToVerify.each { e -> %>
        insurancesToVerify.set('${e.getKey().getInsuranceId()}', '${e.getValue()}');
    <% } %>

    function enableVerification() {
        jq("#owner-name-field").val("").attr("disabled", "disabled");
        jq("#company-field").val("").attr("disabled", "disabled");
        jq("#level-field").val("").attr("disabled", "disabled");
        jq("#policy-number-field").val("").attr("disabled", "disabled");
        jq("#start-date-picker-field").val("");
        jq("#start-date-picker-display").val("").attr("disabled", "disabled");
        jq("#start-date-picker-wrapper >> .icon-calendar").hide();
        jq("#expiration-date-picker-field").val("");
        jq("#expiration-date-picker-display").val("").attr("disabled", "disabled");
        jq("#expiration-date-picker-wrapper >> .icon-calendar").hide();
    }

    function disableVerification() {
        jq("#owner-name-field").removeAttr("disabled");
        jq("#company-field").removeAttr("disabled");
        jq("#level-field").removeAttr("disabled");
        jq("#policy-number-field").removeAttr("disabled");
        jq("#start-date-picker-display").removeAttr("disabled");
        jq("#start-date-picker-wrapper >> .icon-calendar").show();
        jq("#expiration-date-picker-display").removeAttr("disabled");
        jq("#expiration-date-picker-wrapper >> .icon-calendar").show();
    }

    function toggleVerificationButton() {
        const insuranceTypeId = jq("#insurance-type-field").val();
        const ownerCode = jq("#owner-code-field").val();
        if (insuranceTypeId !== "" || ownerCode !== "") {
            jq("#verify-button").show();
        }
        else {
            jq("#verify-button").hide();
        }
        if (insuranceTypeId !== "" && ownerCode !== "") {
            jq("#verify-button").removeAttr("disabled");
        }
        else {
            jq("#verify-button").attr("disabled", "disabled");
        }
    }

    function getDateDisplay(ymdDate) {
        return ymdDate ? moment(ymdDate).format("DD MMM YYYY") : '';
    }

    const verifyMemberDialog = emr.setupConfirmationDialog({
        selector: '#verify-member-dialog',
        actions: {
            confirm: function () {
                alert('Confirmed!');
            },
            cancel: function () {
                verifyMemberDialog.close();
            }
        }
    });

    function setVerifyResultsMessage(message) {
        jq("#verify-member-section").find(".verify-member-row").remove();
        jq("#verify-results-message").html(message ?? "");
    }

    jq(document).ready(function () {
        jq("#insurance-type-field").change(function () {
            const insuranceTypeId = jq(this).val();
            if (insuranceTypeId === "" || insurancesToVerify.has(insuranceTypeId)) {
                toggleVerificationButton();
                enableVerification();
            }
            else {
                disableVerification();
            }
        });
        jq("#insurance-type-field").change();

        jq("#owner-code-field").on("change paste keyup", function () {
            toggleVerificationButton();
        });

        jq("#verify-button").click(function () {
            jq("#verify-button").attr("disabled", "disabled");
            const insuranceTypeId = jq("#insurance-type-field").val();
            const insuranceType = insurancesToVerify.get(insuranceTypeId);
            const ownerCode = jq("#owner-code-field").val();

            setVerifyResultsMessage('Checking eligibility...');
            jq("#verify-member-table").hide();
            verifyMemberDialog.show();

            jq.get(openmrsContextPath + "/ws/rest/v1/rwandaemr/insurance/eligibility?type=" + insuranceType + "&identifier=" + ownerCode, function(data) {
                if (data.responseCode === 200) {
                    const entity = data.responseEntity;
                    let verifyRows = [];
                    if (entity.eligible) {
                        const details = entity.details || {};
                        if (insuranceType === 'rama') {
                            if (details.isEligible) {
                                verifyRows.push({
                                    name: details.firstName + " " + details.lastName,
                                    gender: details.gender,
                                    birthdate: details.dateOfBirth,
                                    type: 'HEAD',
                                    headHouseholdName: details.firstName + " " + details.lastName,
                                    company: details.employerName,
                                    insuranceCardNumber: details.mainAffiliateId,
                                    startDate: '${ui.dateToString(todayDate).substring(0, 10)}',
                                    endDate: '${ui.dateToString(todayPlus3Months).substring(0, 10)}'
                                });
                            }
                        } else if (insuranceType === 'cbhi') {
                            let headOfHouseholdName = '';
                            details.members.forEach((member) => {
                                if (member.type === 'HEAD') {
                                    headOfHouseholdName = member.firstName + ' ' + member.lastName;
                                }
                            });
                            details.members.forEach(member => {
                                if (member.isEligible) {
                                    verifyRows.push({
                                        name: member.firstName + " " + member.lastName,
                                        gender: member.gender,
                                        birthdate: member.dateOfBirth,
                                        type: member.type,
                                        headHouseholdName: headOfHouseholdName,
                                        company: '',
                                        insuranceCardNumber: member.documentNumber,
                                        startDate: member.eligibilityStartDate ? member.eligibilityStartDate.substring(0, 10) : null,
                                        endDate: member.eligibilityStartDate ? (parseInt(member.eligibilityStartDate.substring(0, 4)) + 1) + '-06-30' : null
                                    });
                                }
                            })
                        }
                    }
                    if (verifyRows.length === 0) {
                        setVerifyResultsMessage('No eligible insurances found.');
                    }
                    else {
                        setVerifyResultsMessage('Please choose the eligible member or cancel');
                        verifyRows.forEach((member) => {
                            const row = jq("#verify-member-row-template").clone();
                            jq(row).find(".member-name").html(member.name);
                            jq(row).find(".member-gender").html(member.gender);
                            jq(row).find(".member-birthdate").html(getDateDisplay(member.birthdate));
                            jq(row).find(".member-start-date").html(getDateDisplay(member.startDate));
                            jq(row).find(".member-select").click(function () {
                                jq("#owner-name-field").val(member.headHouseholdName);
                                jq("#company-field").val(member.company);
                                jq("#policy-number-field").val(member.insuranceCardNumber);
                                if (member.startDate) {
                                    jq("#start-date-picker-field").val(member.startDate);
                                    jq("#start-date-picker-display").val(getDateDisplay(member.startDate));
                                }
                                if (member.endDate) {
                                    jq("#expiration-date-picker-field").val(member.endDate);
                                    jq("#expiration-date-picker-display").val(getDateDisplay(member.endDate));
                                }
                                verifyMemberDialog.close();
                                disableVerification();
                            });
                            jq(row).addClass("verify-member-row");
                            jq(row).show();
                            jq("#verify-member-section").append(row);
                        });
                        jq("#verify-member-table").show();
                    }
                } else {
                    if (!data.enabled) {
                        setVerifyResultsMessage('Insurance verification is not enabled');
                        disableVerification();
                    } else if (data.endpointAccessible === false) {
                        setVerifyResultsMessage('Insurance verification endpoint is not accessible');
                        disableVerification();
                    }
                    else if (data.errorMessage) {
                        setVerifyResultsMessage('Insurance verification failed: ' + data.errorMessage);
                    }
                    else {
                        setVerifyResultsMessage('Verification failed');
                    }
                }
                jq("#verify-button").removeAttr("disabled");
            });
        });
    });
</script>

<h3>${pageTitle}</h3>

<form method="post" id="insurance-policy-form">
    <input type="hidden" value="${ui.format(policyModel.policyId)}" name="policyId" />
    <input type="hidden" value="${patient.patient.id}" name="patientId" />

    <div class="row">
        <div class="col-6">
            <fieldset>
                <legend>${ui.message("rwandaemr.insurance.owner")}</legend>

                <% if (editMode) { %>
                    ${ ui.includeFragment("uicommons", "field/dropDown", [
                            id: "owner",
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

                <% if (pageMode == 'create') { %>
                    ${ ui.includeFragment("uicommons", "field/dropDown", [
                            id: "insurance-type",
                            label: ui.message("rwandaemr.insurance.name"),
                            emptyOptionLabel: "",
                            formFieldName: "insuranceId",
                            initialValue: (policyModel.insuranceId ?: ''),
                            options: insuranceOptions
                    ])}
                <% } else { %>
                    <p>
                        <% if (editMode) { %>
                            <input type="hidden" name="insuranceId" value="${policyModel.insuranceId}" />
                        <% } %>
                        <label for="view-insurance-name">${ui.message("rwandaemr.insurance.name")}</label>
                        <span id="view-insurance-name" class="field-value">${ui.format(policy.insurance?.name)}</span>
                    </p>
                <% } %>

                <% if (editMode) { %>
                    ${ ui.includeFragment("uicommons", "field/text", [
                            id: "owner-code",
                            label: ui.message("rwandaemr.insurance.beneficiary.ownerCode"),
                            formFieldName: "ownerCode",
                            initialValue: (policyModel.ownerCode ?: ''),
                            size: 30
                    ])}
                    <p id="verify-section">
                        <input type="button" id="verify-button" value="Check eligibility"/>
                    </p>
                <% } else { %>
                    <p>
                        <label for="view-insurance-beneficiary-ownerCode">${ui.message("rwandaemr.insurance.beneficiary.ownerCode")}</label>
                        <span id="view-insurance-beneficiary-ownerCode" class="field-value">${ui.format(policyModel.ownerCode)}</span>
                    </p>
                <% } %>

                <% if (editMode) { %>
                    ${ ui.includeFragment("uicommons", "field/text", [
                            id: "owner-name",
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
                            id: "company",
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

                <!-- Policy mode level is deprecated.  Only show it if it has been previously recorded.  See RWA-979 -->
                <% if (policyModel.level) { %>

                    <% if (editMode) { %>
                        ${ ui.includeFragment("uicommons", "field/dropDown", [
                                id: "level",
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
        </div>
        <div class="col-6">
            <% if (editMode) { %>
                ${ ui.includeFragment("uicommons", "field/text", [
                        id: "policy-number",
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
                        id: "start-date-picker",
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
                        id: "expiration-date-picker",
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
                        id: "third-party",
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

        </div>
    </div>
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
<div id="verify-member-dialog" class="dialog" style="display: none; width: 90%;">
    <div class="dialog-header">
        <i class="icon-check-in"></i>
        <h3>
            Verify insurance eligibility
        </h3>
    </div>
    <div class="dialog-content" id="verify-results-section">
        <p class="dialog-instructions" id="verify-results-message"></p>
        <table id="verify-member-table" style="width: 100%">
            <thead>
                <tr>
                    <th>Name</th>
                    <th>Gender</th>
                    <th>Birthdate</th>
                    <th>Eligibility Date</th>
                    <th>Action</th>
                </tr>
            </thead>
            <tbody id="verify-member-section">
                <tr id="verify-member-row-template" style="display: none;">
                    <td class="member-name"></td>
                    <td class="member-gender"></td>
                    <td class="member-birthdate"></td>
                    <td class="member-start-date"></td>
                    <td class="member-action"><input type="button" class="member-select" value="Select"/></td>
                </tr>
            </tbody>
        </table>
        <br/>
        <button class="cancel">${ ui.message("coreapps.cancel") }</button>
    </div>
</div>