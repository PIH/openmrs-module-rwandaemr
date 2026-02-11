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
    .pill {
        border: none;
        padding: 10px 20px;
        text-align: center;
        text-decoration: none;
        display: inline-block;
        margin: 4px 2px;
        border-radius: 16px;
    }
    .eligible-cell {
        background-color: darkgreen; color: white; font-weight: bold;
    }
    .not-eligible-cell {
        background-color: darkred; color: white; font-weight: bold;
    }

    .simplemodal-data {
        max-height: 600px; /* Set a maximum height for the content */
        overflow-y: auto; /* Enable vertical scrolling if content exceeds max-height */
        overflow-x: hidden; /* Prevent horizontal scrolling */
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
    const insuranceNamesById = new Map();
    <% insurances.each { i -> %>
        insuranceNamesById.set('${i.getInsuranceId()}', '${ui.encodeJavaScript(ui.format(i.getName()))}');
    <% } %>
    const isEditMode = ${editMode ? "true" : "false"};
    const hasErrorsFlag = ${hasErrors ? "true" : "false"};
    const patientPhoneNumber = '${ui.encodeJavaScript(patientPhoneNumber ?: "")}';
    const patientId = ${patient.patient.id};
    let verifyMemberDialog = null;
    let mmiOtpCode = null;
    let isMmiEligibilityFlow = false;
    let mmiReceptionNumber = null;
    let mmiPatientType = null;
    let mmiMemberSelected = false;

    function enableVerification() {
        jq("#owner-name-field").val("").attr("disabled", "disabled");
        jq("#company-field").val("").attr("disabled", "disabled");
        jq("#level-field").val("").attr("disabled", "disabled");
        jq("#policy-number-field").val("").attr("disabled", "disabled");
        jq("#rhip-patient-id-field").val("");
        jq("#start-date-picker-field").val("");
        jq("#start-date-picker-display").val("").attr("disabled", "disabled");
        jq("#start-date-picker-wrapper >> .icon-calendar").hide();
        jq("#expiration-date-picker-field").val("");
        jq("#expiration-date-picker-display").val("").attr("disabled", "disabled");
        jq("#expiration-date-picker-wrapper >> .icon-calendar").hide();
        jq("#save-button").attr("disabled", "disabled");
    }

    function disableVerification() {
        jq("#owner-name-field").removeAttr("disabled");
        jq("#company-field").removeAttr("disabled");
        jq("#level-field").removeAttr("disabled");
        jq("#policy-number-field").removeAttr("disabled");
        jq("#rhip-patient-id-field").attr("disabled", "disabled");
        jq("#start-date-picker-display").removeAttr("disabled");
        jq("#start-date-picker-wrapper >> .icon-calendar").show();
        jq("#expiration-date-picker-display").removeAttr("disabled");
        jq("#expiration-date-picker-wrapper >> .icon-calendar").show();
        jq("#save-button").removeAttr("disabled");
    }

    function toggleVerificationButton() {
        const insuranceTypeId = getInsuranceTypeId();
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

    function getInsuranceTypeId() {
        return jq("#insurance-type-field").val()
            || jq("#insurance-type").val()
            || jq("select[name='insuranceId']").val()
            || jq("input[name='insuranceId']").val()
            || "";
    }

    function isMmiInsuranceName(name) {
        return name && name.toLowerCase().indexOf("mmi") !== -1;
    }

    function getSelectedInsuranceName() {
        const insuranceTypeId = getInsuranceTypeId();
        return insuranceNamesById.get(insuranceTypeId);
    }

    function isMmiInsuranceSelected() {
        return isMmiInsuranceName(getSelectedInsuranceName());
    }

    function getInsuranceTypeForRequest(insuranceTypeId) {
        const mappedType = insurancesToVerify.get(insuranceTypeId);
        if (mappedType) {
            return mappedType;
        }
        return isMmiInsuranceSelected() ? "mmi" : null;
    }

    function getMaskedPhoneNumber() {
        if (!patientPhoneNumber) {
            return null;
        }
        if (patientPhoneNumber.length <= 4) {
            return patientPhoneNumber;
        }
        return patientPhoneNumber.substring(0, patientPhoneNumber.length - 4).replace(/./g, "*") + patientPhoneNumber.substring(patientPhoneNumber.length - 4);
    }

    function setVerifyResultsMessage(message) {
        jq("#verify-member-section").find(".verify-member-row").remove();
        jq("#verify-results-message").html(message ?? "");
        if (mmiReceptionNumber) {
            jq("#mmi-reception-number-value").text(mmiReceptionNumber);
            jq("#mmi-reception-number-row").show();
            jq("#mmi-reception-number-field").val(mmiReceptionNumber);
            jq("#mmi-reception-number-field-wrapper").show();
        } else {
            jq("#mmi-reception-number-row").hide();
            jq("#mmi-reception-number-field-wrapper").hide();
        }
    }

    function updateMmiReceptionControls() {
        const isMmiSelected = isMmiInsuranceSelected();
        if (!isMmiSelected) {
            jq("#mmi-patient-type-page-row").hide();
            jq("#mmi-reception-action-row").hide();
            return;
        }
        jq("#mmi-patient-type-page-row").show();
        jq("#mmi-reception-action-row").show();
        const patientType = jq("#mmi-patient-type-page").val();
        const canCreate = isMmiEligibilityFlow && mmiMemberSelected && patientType;
        jq("#mmi-create-reception-button-page").prop("disabled", !canCreate);
    }

    function loadMmiPatientTypes() {
        const selectPage = jq("#mmi-patient-type-page");
        selectPage.empty();
        selectPage.append(jq("<option>").val("").text("Loading..."));
        selectPage.attr("disabled", "disabled");
        jq.get(openmrsContextPath + "/ws/rest/v1/rwandaemr/insurance/eligibility/mmi/patient-types", function (typesData) {
            selectPage.empty();
            selectPage.append(jq("<option>").val("").text(""));
            console.log("MMI patient types response", typesData);
            const entity = (typesData && typesData.responseEntity) ? typesData.responseEntity : typesData;
            const types = (entity && entity.patientTypes) ? entity.patientTypes :
                ((entity && entity.data && entity.data.patientTypes) ? entity.data.patientTypes : []);
            let activeCount = 0;
            types.forEach((type) => {
                const isActive = (type.isActive === undefined || type.isActive === null)
                    ? (type.active === undefined || type.active === null ? true : type.active)
                    : type.isActive;
                if (isActive) {
                    selectPage.append(jq("<option>").val(type.typeId).text(type.typeName));
                    activeCount += 1;
                }
            });
            if (activeCount === 0 && types.length > 0) {
                types.forEach((type) => {
                    selectPage.append(jq("<option>").val(type.typeId).text(type.typeName));
                });
            }
            if (selectPage.children("option").length > 1) {
                selectPage.removeAttr("disabled");
            } else {
                selectPage.attr("disabled", "disabled");
                setVerifyResultsMessage("No active patient types found.");
            }
            updateMmiReceptionControls();
        }).fail(function () {
            selectPage.empty();
            selectPage.append(jq("<option>").val("").text(""));
            selectPage.attr("disabled", "disabled");
            setVerifyResultsMessage("Unable to load patient types.");
            updateMmiReceptionControls();
        });
    }

    function setNoMatchingInsurancesFound(insuranceType) {
        if (insuranceType === 'cbhi') {
            setVerifyResultsMessage('Household not found');
        }
        else if (insuranceType === 'rama') {
            setVerifyResultsMessage('Rama Member not found');
        }
        else {
            setVerifyResultsMessage("Insurance not found");
        }
    }

    jq(document).ready(function () {
        if (jq("#verify-button").length > 0) {
            jq("#insurance-type-field, #insurance-type, select[name='insuranceId'], input[name='insuranceId']").change(function () {
                const insuranceTypeId = getInsuranceTypeId();
                if (insuranceTypeId === "" || insurancesToVerify.has(insuranceTypeId) || isMmiInsuranceSelected()) {
                    toggleVerificationButton();
                    enableVerification();
                    if (isMmiInsuranceSelected()) {
                        loadMmiPatientTypes();
                    }
                }
                else {
                    disableVerification();
                }
                updateMmiReceptionControls();
            });
            <% if (!hasErrors) { // Do not trigger change if returning to page after validation error on submit %>
                jq("#insurance-type-field, #insurance-type, select[name='insuranceId'], input[name='insuranceId']").change();
            <% } %>

            jq("#owner-code-field").on("change paste keyup", function () {
                toggleVerificationButton();
            });
            toggleVerificationButton();
            updateMmiReceptionControls();

            if (isEditMode && !hasErrorsFlag) {
                const insuranceTypeId = getInsuranceTypeId();
                const ownerCode = jq("#owner-code-field").val();
                const rhipPatientId = jq("#rhip-patient-id-field").val();
                if (insuranceTypeId && ownerCode && !rhipPatientId && insurancesToVerify.has(insuranceTypeId)) {
                    toggleVerificationButton();
                    jq("#verify-button").removeAttr("disabled");
                    jq("#verify-button").click();
                }
            }

            jq("#verify-button").click(function () {
                jq("#verify-button").attr("disabled", "disabled");
                const insuranceTypeId = getInsuranceTypeId();
                const insuranceType = getInsuranceTypeForRequest(insuranceTypeId);
                const ownerCode = jq("#owner-code-field").val();
                const isMmiInsurance = isMmiInsuranceSelected();
                isMmiEligibilityFlow = false;
                mmiMemberSelected = false;
                updateMmiReceptionControls();

                if (!insuranceTypeId || !ownerCode) {
                    setVerifyResultsMessage("Insurance and owner code are required.");
                    jq("#verify-button").removeAttr("disabled");
                    return;
                }

                setVerifyResultsMessage('Checking eligibility...');
                jq("#verify-member-table").hide();

                if (!isMmiInsurance) {
                    verifyMemberDialog = jq.modal(jq("#verify-member-dialog"), {
                        overlayClose: true,
                        overlayId: "modal-overlay",
                        opacity: 80,
                        persist: true,
                        closeClass: "cancel",
                        position: [20, 50],
                    });
                }

                if (!insuranceType) {
                    setVerifyResultsMessage('Insurance verification is not enabled');
                    jq("#verify-button").removeAttr("disabled");
                    return;
                }

                const eligibilityUrl = openmrsContextPath + "/ws/rest/v1/rwandaemr/insurance/eligibility";
                const eligibilityParams = jq.param({
                    type: insuranceType,
                    identifier: ownerCode,
                    sendOTP: isMmiInsurance
                });

                if (isMmiInsurance && !patientPhoneNumber) {
                    setVerifyResultsMessage("Patient phone number is required to send the OTP.");
                    jq("#verify-button").removeAttr("disabled");
                    return;
                }

                jq.get(eligibilityUrl + "?" + eligibilityParams, function(data) {
                    if (isMmiInsurance) {
                        if (data.responseCode === 200) {
                            const maskedPhone = getMaskedPhoneNumber();
                            const message = maskedPhone ? ("OTP sent to " + maskedPhone + ". Enter the code to continue.") : "OTP sent. Enter the code to continue.";
                            jq("#mmi-otp-message").text(message);
                            jq("#mmi-otp-code").val("");
                            jq("#mmi-otp-verify-button").data("insuranceType", insuranceType).data("ownerCode", ownerCode);
                            jq.modal(jq("#mmi-otp-dialog"), {
                                overlayClose: true,
                                overlayId: "modal-overlay",
                                opacity: 80,
                                persist: true,
                                closeClass: "cancel",
                                position: [20, 50],
                            });
                            setVerifyResultsMessage("Waiting for OTP verification...");
                        } else {
                            setVerifyResultsMessage("Failed to send OTP.");
                        }
                        jq("#verify-button").removeAttr("disabled");
                        return;
                    }
                    handleEligibilityResponse(data, insuranceType, verifyMemberDialog, false);
                    jq("#verify-button").removeAttr("disabled");
                });
            });
        }

        jq("#mmi-otp-verify-button").click(function () {
            const otpCode = jq("#mmi-otp-code").val().trim();
            const insuranceType = jq(this).data("insuranceType");
            const ownerCode = jq(this).data("ownerCode");
            if (!otpCode) {
                jq("#mmi-otp-message").text("OTP code is required.");
                return;
            }
            jq(this).attr("disabled", "disabled");
            jq.ajax({
                url: openmrsContextPath + "/ws/rest/v1/rwandaemr/insurance/eligibility/verify-otp",
                type: "POST",
                contentType: "application/json",
                data: JSON.stringify({
                    insuranceType: insuranceType,
                    identifier: ownerCode,
                    otpCode: otpCode,
                    patientId: patientId
                }),
                success: function (data) {
                    jq("#mmi-otp-verify-button").removeAttr("disabled");
                    jq.modal.close();
                    if (data && data.responseEntity && data.responseEntity.success) {
                        mmiOtpCode = otpCode;
                        mmiReceptionNumber = null;
                        mmiPatientType = null;
                        isMmiEligibilityFlow = true;
                        mmiMemberSelected = false;
                        updateMmiReceptionControls();
                        const eligibilityUrl = openmrsContextPath + "/ws/rest/v1/rwandaemr/insurance/eligibility";
                        const eligibilityParams = jq.param({
                            type: insuranceType,
                            identifier: ownerCode,
                            sendOTP: false
                        });
                        verifyMemberDialog = jq.modal(jq("#verify-member-dialog"), {
                            overlayClose: true,
                            overlayId: "modal-overlay",
                            opacity: 80,
                            persist: true,
                            closeClass: "cancel",
                            position: [20, 50],
                        });
                        jq.get(eligibilityUrl + "?" + eligibilityParams, function (eligibilityData) {
                            handleEligibilityResponse(eligibilityData, insuranceType, verifyMemberDialog, true);
                        });
                    } else {
                        const errorMessage = data && data.responseEntity && data.responseEntity.message ? data.responseEntity.message : "Unable to verify OTP.";
                        setVerifyResultsMessage(errorMessage);
                    }
                },
                error: function () {
                    jq("#mmi-otp-message").text("Unable to verify OTP. Please try again.");
                    jq("#mmi-otp-verify-button").removeAttr("disabled");
                }
            });
        });

        jq("#mmi-create-reception-button-page").click(function () {
            const ownerCode = jq("#owner-code-field").val();
            if (!ownerCode) {
                setVerifyResultsMessage("Owner code is required.");
                return;
            }
            mmiPatientType = jq("#mmi-patient-type-page").val();
            if (!mmiPatientType) {
                setVerifyResultsMessage("Patient type is required.");
                return;
            }
            jq(this).attr("disabled", "disabled");
            jq.ajax({
                url: openmrsContextPath + "/ws/rest/v1/rwandaemr/insurance/eligibility/mmi/reception",
                type: "POST",
                contentType: "application/json",
                data: JSON.stringify({
                    identifier: ownerCode,
                    otpCode: mmiOtpCode,
                    patientId: patientId,
                    patientType: mmiPatientType
                }),
                success: function (data) {
                    jq("#mmi-create-reception-button-page").removeAttr("disabled");
                    if (data && data.success) {
                        mmiReceptionNumber = data.receptionNumber || null;
                        setVerifyResultsMessage("MMI reception created.");
                        if (verifyMemberDialog) {
                            verifyMemberDialog.close();
                            verifyMemberDialog = null;
                        }
                        disableVerification();
                    } else {
                        const errorMessage = data && data.message ? data.message : "Unable to create reception.";
                        setVerifyResultsMessage(errorMessage);
                    }
                },
                error: function () {
                    jq("#mmi-create-reception-button-page").removeAttr("disabled");
                    setVerifyResultsMessage("Unable to create reception. Please try again.");
                }
            });
        });

        jq("#mmi-patient-type-page").change(function () {
            updateMmiReceptionControls();
        });
    });

    function handleEligibilityResponse(data, insuranceType, dialogModal, isMmiFlow) {
        console.log(data);
        if (data.responseCode === 200) {
            let verifyRows = [];
            if (data.responseEntity.success) {
                const owner = data.responseEntity.data;
                owner['ownerName'] = owner.fullName;
                verifyRows.push(owner);
                if (owner.dependants) {
                    owner.dependants.forEach((dependant) => {
                        dependant['ownerName'] = owner.fullName;
                        verifyRows.push(dependant);
                    });
                }
            }
            if (verifyRows.length === 0) {
                setNoMatchingInsurancesFound(insuranceType);
            }
            else {
                setVerifyResultsMessage('Please choose an eligible member or cancel');
                verifyRows.forEach((member) => {
                    const row = jq("#verify-member-row-template").clone();
                    jq(row).find(".member-name").html(member.fullName);
                    jq(row).find(".member-gender").html(member.gender);
                    jq(row).find(".member-birthdate").html(getDateDisplay(member.dateOfBirth));
                    jq(row).find(".member-start-date").html(getDateDisplay(member.eligibilityStartDate));
                    jq(row).find(".member-id").html(member.documentNumber);
                    jq(row).find(".member-rhip-id").html(member.patientId || "");
                    jq(row).find(".member-government-sponsored").html(member.isGovernmentSponsored)
                    if (member.isEligible) {
                        jq(row).find(".member-eligibility").html('<span class="pill eligible-cell">Eligible</span>');
                        jq(row).find(".member-select").click(function () {
                            jq("#owner-name-field").val(member.ownerName);
                            jq("#policy-number-field").val(member.documentNumber);
                            jq("#rhip-patient-id-field").val(member.patientId || "");
                            if (member.eligibilityStartDate) {
                                jq("#start-date-picker-field").val(member.eligibilityStartDate);
                                jq("#start-date-picker-display").val(getDateDisplay(member.eligibilityStartDate));
                                if (!member.endDate) {
                                    const startDate = moment(member.eligibilityStartDate);
                                    const startDateYear = startDate.year();
                                    const startDateMonth = startDate.month() + 1;
                                    const startDateDay = startDate.day();
                                    const startDateMonthAndDay = startDateMonth + "-" + startDateDay;
                                    const endDateMonthAndDay = "06-30";
                                    const endDateYear = (startDateMonthAndDay >= endDateMonthAndDay ? startDateYear + 1 : startDateYear);
                                    member.endDate = endDateYear + "-" + endDateMonthAndDay;
                                }
                            }
                            if (member.endDate) {
                                jq("#expiration-date-picker-field").val(member.endDate);
                                jq("#expiration-date-picker-display").val(getDateDisplay(member.endDate));
                            }
                            if (isMmiFlow) {
                                mmiMemberSelected = true;
                                updateMmiReceptionControls();
                                disableVerification();
                                if (dialogModal) {
                                    dialogModal.close();
                                    verifyMemberDialog = null;
                                }
                            } else {
                                if (dialogModal) {
                                    dialogModal.close();
                                    verifyMemberDialog = null;
                                }
                                disableVerification();
                            }
                        });
                    }
                    else {
                        jq(row).find(".member-eligibility").html('<span class="pill not-eligible-cell">Not Eligible</span>');
                        jq(row).find(".member-select").attr("disabled", "disabled");
                    }
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
                setVerifyResultsMessage('Insurance verification is currently unavailable. Please check your Internet.');
                disableVerification();
            }
            else if (data.errorMessage || data.responseEntity?.error) {
                const errorMessage = data.errorMessage && data.errorMessage !== 'null' ? data.errorMessage : data.responseEntity?.error;
                setVerifyResultsMessage('Error: ' + errorMessage ?? "Unknown");
                console.error(data);
                disableVerification();
            }
            else {
                setNoMatchingInsurancesFound(insuranceType);
            }
        }
    }
</script>

<h3>${pageTitle}</h3>

<form method="post" id="insurance-policy-form">
    <input type="hidden" value="${ui.format(policyModel.policyId)}" name="policyId" />
    <input type="hidden" value="${patient.patient.id}" name="patientId" />
    <input type="hidden" id="rhip-patient-id-field" value="${ui.format(policyModel.rhipPatientId)}" name="rhipPatientId" />

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
                            <input type="hidden" id="insurance-type-field" name="insuranceId" value="${policyModel.insuranceId}" />
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
                    <% if (!insurancesToVerify.isEmpty() || hasMmiInsurance) { %>
                        <p id="verify-section">
                            <input type="button" id="verify-button" value="Check eligibility"/>
                        </p>
                    <% } %>
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
                ${ ui.includeFragment("uicommons", "field/text", [
                        id: "rhip-patient-id",
                        label: "RHIP Patient ID",
                        formFieldName: "rhipPatientIdDisplay",
                        initialValue: (policyModel.rhipPatientId ?: ''),
                        size: 30,
                        otherAttributes: ["disabled": "disabled"]
                ])}
                <p id="mmi-reception-number-field-wrapper" style="display: none;">
                    <label for="mmi-reception-number-field">MMI Reception Number</label>
                    <input type="text" id="mmi-reception-number-field" disabled="disabled" />
                </p>
            <% } else { %>
                <p>
                    <label for="view-insurance-card-number">${ui.message("rwandaemr.insurance.insuranceCardNo")}</label>
                    <span id="view-insurance-card-number" class="field-value">${ui.format(policyModel.insuranceCardNo)}</span>
                </p>
                <p>
                    <label for="view-rhip-patient-id">RHIP Patient ID</label>
                    <span id="view-rhip-patient-id" class="field-value">${ui.format(policyModel.rhipPatientId)}</span>
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
                <p id="mmi-patient-type-page-row" style="display: none;">
                    <label for="mmi-patient-type-page">Patient Type (MMI only)</label>
                    <select id="mmi-patient-type-page" disabled="disabled"></select>
                </p>
                <p id="mmi-reception-action-row" style="display: none;">
                    <button type="button" id="mmi-create-reception-button-page" disabled="disabled">Create reception</button>
                </p>
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
        <p id="mmi-reception-number-row" style="display: none;">
            <label>MMI Reception Number</label>
            <span id="mmi-reception-number-value" class="field-value"></span>
        </p>
        <table id="verify-member-table" style="width: 100%">
            <thead>
                <tr>
                    <th>Name</th>
                    <th>Gender</th>
                    <th>Member ID</th>
                    <th>RHIP Patient ID</th>
                    <th>Birthdate</th>
                    <th>Eligibility Date</th>
                    <th>Eligibility</th>
                    <th>Government Sponsored</th>
                    <th>Action</th>
                </tr>
            </thead>
            <tbody id="verify-member-section">
                <tr id="verify-member-row-template" style="display: none;">
                    <td class="member-name"></td>
                    <td class="member-gender"></td>
                    <td class="member-id"></td>
                    <td class="member-rhip-id"></td>
                    <td class="member-birthdate"></td>
                    <td class="member-start-date"></td>
                    <td class="member-eligibility"></td>
                    <td class="member-government-sponsored"></td>
                    <td class="member-action"><input type="button" class="member-select" value="Select"/></td>
                </tr>
            </tbody>
        </table>
        <br/>
        <button class="cancel">${ ui.message("coreapps.cancel") }</button>
    </div>
</div>
<div id="mmi-otp-dialog" class="dialog" style="display: none; width: 400px;">
    <div class="dialog-header">
        <i class="icon-lock"></i>
        <h3>
            Verify MMI OTP
        </h3>
    </div>
    <div class="dialog-content">
        <p class="dialog-instructions" id="mmi-otp-message"></p>
        <p>
            <label for="mmi-otp-code">OTP Code</label>
            <input type="text" id="mmi-otp-code" autocomplete="one-time-code" />
        </p>
        <button type="button" id="mmi-otp-verify-button">Verify</button>
        <button type="button" class="cancel">${ ui.message("coreapps.cancel") }</button>
    </div>
</div>
