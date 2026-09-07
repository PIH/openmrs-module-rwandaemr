<%
    ui.decorateWith("appui", "standardEmrPage")
    def openmrsContextPath = contextPath ? (contextPath.startsWith("/") ? contextPath : "/" + contextPath) : ""
%>

<script type="text/javascript">
    var breadcrumbs = [
        { icon: "icon-home", link: '${ openmrsContextPath }/index.htm' },
        { label: "${ ui.message("coreapps.app.system.administration.label") }", link: "${ ui.pageLink("coreapps", "systemadministration/systemAdministration") }" },
        { label: "RHIP Approvals", link: "${ ui.pageLink("rwandaemr", "admin/rhipApprovals") }" }
    ];
    var rhipOpenmrsContextPath = "${ openmrsContextPath }";
    var rhipApprovalsPageUrl = "${ ui.pageLink("rwandaemr", "admin/rhipApprovals") }";
    var rhipCanAutoCheckStatus = ${ canCheckApprovalStatus ? "true" : "false" };
    var rhipStatusPollDelay = 300000;
    var rhipStatusPollTimer = null;
    var rhipStatusPollInProgress = false;

    jq(function() {
        <% if (openRequest) { %>
            openRhipApprovalModal();
        <% } %>
        jq("#approval-patient-field").on("change", function() {
            if (jq(this).val()) {
                var form = jq("#approval-patient-search-form")[0];
                submitRhipPatientSearch(form);
                form.submit();
            }
        });
        scheduleRhipApprovalStatusPoll();
    });

    function openRhipApprovalModal() {
        jq("#rhip-approval-modal").show();
        jq("#approval-patient-display").focus();
    }

    function closeRhipApprovalModal() {
        jq("#rhip-approval-modal").hide();
    }

    function selectRhipApprovalProduct(code, name, type, price) {
        jq("#productCode").val(code);
        jq("#productName").val(name);
        jq("#productType").val(type);
        jq("#tariffPrice").val(price);
        jq("#selected-product-label").val(code + " - " + name);
        jq(".rhip-procedure-option").removeClass("selected");
        jq("#procedure-" + code.replace(/[^a-zA-Z0-9_-]/g, "-")).addClass("selected");
    }

    function submitRhipPatientSearch(form) {
        jq("#approval-search-query").val(jq("#approval-patient-display").val());
        return true;
    }

    function submitRhipApprovalRequest(form) {
        if (!confirm("Submit this approval request?")) {
            return false;
        }
        jq(form).find("input[type=submit], button[type=submit]").prop("disabled", true).each(function() {
            var button = jq(this);
            if (button.is("input")) {
                button.val("Requesting...");
            }
            else {
                button.find(".rhip-button-label").text("Requesting...");
            }
        });
        return true;
    }

    function submitRhipApprovalStatus(form) {
        jq(form).find("input[type=submit], button[type=submit]").prop("disabled", true).each(function() {
            var button = jq(this);
            if (button.is("input")) {
                button.val("Checking...");
            }
            else {
                button.find(".rhip-button-label").text("Checking...");
            }
        });
        return true;
    }

    function submitRhipApprovalRetry(form) {
        if (!confirm("Retry this failed approval request?")) {
            return false;
        }
        jq(form).find("input[type=submit], button[type=submit]").prop("disabled", true).each(function() {
            var button = jq(this);
            if (button.is("input")) {
                button.val("Retrying...");
            }
            else {
                button.find(".rhip-button-label").text("Retrying...");
            }
        });
        return true;
    }

    function showRhipApprovalDetails(id) {
        jq(".rhip-details-panel").hide();
        jq("#" + id).show();
    }

    function hideRhipApprovalDetails(id) {
        jq("#" + id).hide();
    }

    function scheduleRhipApprovalStatusPoll() {
        if (!rhipCanAutoCheckStatus || !getPendingRhipApprovalIds().length) {
            return;
        }
        if (rhipStatusPollTimer) {
            clearTimeout(rhipStatusPollTimer);
        }
        rhipStatusPollTimer = setTimeout(checkPendingRhipApprovalStatuses, rhipStatusPollDelay);
    }

    function getPendingRhipApprovalIds() {
        var ids = [];
        jq("[data-rhip-approval-id]").each(function() {
            var row = jq(this);
            if ((row.attr("data-rhip-approval-status") || "").toUpperCase() === "PENDING") {
                ids.push(row.attr("data-rhip-approval-id"));
            }
        });
        return ids;
    }

    function checkPendingRhipApprovalStatuses() {
        var ids = getPendingRhipApprovalIds();
        if (!ids.length || rhipStatusPollInProgress) {
            scheduleRhipApprovalStatusPoll();
            return;
        }
        rhipStatusPollInProgress = true;
        jq.ajax({
            type: "POST",
            url: rhipApprovalsPageUrl,
            dataType: "json",
            data: {
                action: "autoCheckStatus",
                approvalRequestIds: ids.join(",")
            }
        }).done(function(response) {
            handleRhipApprovalStatusUpdates(response);
        }).always(function() {
            rhipStatusPollInProgress = false;
            scheduleRhipApprovalStatusPoll();
        });
    }

    function handleRhipApprovalStatusUpdates(response) {
        if (!response || !response.success || !response.updates) {
            return;
        }
        var changed = [];
        jq.each(response.updates, function(index, update) {
            var row = jq("[data-rhip-approval-id='" + update.id + "']");
            if (!row.length || !update.newStatus) {
                return;
            }
            var oldStatus = (row.attr("data-rhip-approval-status") || "").toUpperCase();
            var newStatus = (update.newStatus || "").toUpperCase();
            row.attr("data-rhip-approval-status", newStatus);
            var chip = row.find(".rhip-chip").first();
            chip.removeClass(function(i, className) {
                var classesToRemove = [];
                jq.each((className || "").split(" "), function(classIndex, cssClass) {
                    if (cssClass.indexOf("rhip-chip-") === 0) {
                        classesToRemove.push(cssClass);
                    }
                });
                return classesToRemove.join(" ");
            }).addClass("rhip-chip rhip-chip-" + newStatus).text(update.statusLabel || newStatus);
            if (update.changed && oldStatus !== newStatus) {
                changed.push(update);
            }
        });
        if (changed.length) {
            showRhipApprovalStatusNotification(changed);
        }
    }

    function showRhipApprovalStatusNotification(updates) {
        var list = jq("#rhip-status-notification-list");
        list.empty();
        jq.each(updates, function(index, update) {
            var patientName = update.patientName || "Patient";
            var procedureName = update.procedureName ? " - " + update.procedureName : "";
            var status = update.statusLabel || update.newStatus;
            list.append(jq("<li/>").text(patientName + procedureName + ": " + status));
        });
        jq("#rhip-status-notification").show();
    }

    function hideRhipApprovalStatusNotification() {
        jq("#rhip-status-notification").hide();
    }
</script>

<style>
    body {
        background: #f6f8fb;
    }
    .rhip-approval-page {
        max-width: 1320px;
        margin: 0 auto;
        color: #17203a;
    }
    .rhip-approval-header {
        margin: 6px 0 16px;
    }
    .rhip-approval-title {
        margin: 0;
        font-size: 24px;
        line-height: 1.15;
        color: #10192f;
    }
    .rhip-approval-subtitle {
        margin: 8px 0 0;
        color: #4b587c;
        font-size: 14px;
    }
    .rhip-section {
        border: 1px solid #d8dfeb;
        background: #ffffff;
        padding: 16px;
        margin-bottom: 18px;
        border-radius: 6px;
        box-shadow: 0 8px 24px rgba(23, 32, 58, 0.04);
    }
    .rhip-section-header {
        display: flex;
        justify-content: space-between;
        gap: 12px;
        align-items: center;
        margin-bottom: 12px;
    }
    .rhip-section-header h2 {
        margin: 0;
        font-size: 18px;
        color: #10192f;
    }
    .rhip-button,
    .rhip-primary-button,
    .rhip-secondary-button {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        gap: 6px;
        min-height: 32px;
        border-radius: 5px;
        padding: 7px 12px;
        font-weight: 700;
        font-size: 13px;
        line-height: 1.2;
        text-decoration: none;
        cursor: pointer;
        transition: background-color 120ms ease, border-color 120ms ease, box-shadow 120ms ease, color 120ms ease;
    }
    .rhip-button i,
    .rhip-primary-button i,
    .rhip-secondary-button i {
        font-size: 13px;
        line-height: 1;
    }
    .rhip-primary-button {
        background: #1f62e6;
        border: 1px solid #1f62e6;
        color: #fff;
    }
    .rhip-primary-button:hover,
    .rhip-primary-button:focus {
        background: #174fc2;
        border-color: #174fc2;
        color: #fff;
        box-shadow: 0 0 0 3px rgba(31, 98, 230, 0.18);
    }
    .rhip-button:disabled,
    .rhip-primary-button:disabled,
    .rhip-secondary-button:disabled {
        opacity: 0.62;
        cursor: not-allowed;
        box-shadow: none;
    }
    .rhip-modal {
        display: none;
        position: fixed;
        z-index: 10000;
        left: 0;
        top: 0;
        width: 100%;
        height: 100%;
        background: rgba(16, 25, 47, 0.45);
        overflow: auto;
    }
    .rhip-modal-dialog {
        width: min(980px, calc(100% - 36px));
        margin: 42px auto;
        background: #fff;
        border: 1px solid #d8dfeb;
        border-radius: 6px;
        box-shadow: 0 18px 42px rgba(16, 25, 47, 0.22);
    }
    .rhip-modal-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 14px 18px;
        border-bottom: 1px solid #edf1f7;
    }
    .rhip-modal-header h2 {
        margin: 0;
        font-size: 18px;
    }
    .rhip-modal-close {
        border: 0;
        background: transparent;
        font-size: 22px;
        cursor: pointer;
        color: #667493;
    }
    .rhip-modal-body {
        padding: 18px;
    }
    .rhip-form-grid {
        display: grid;
        grid-template-columns: repeat(2, minmax(240px, 1fr));
        gap: 14px;
        align-items: start;
    }
    .rhip-field label {
        display: block;
        margin-bottom: 7px;
        font-size: 12px;
        font-weight: bold;
        color: #1f2a44;
    }
    .rhip-field input,
    .rhip-field select,
    .rhip-field textarea {
        width: 100%;
        box-sizing: border-box;
        min-height: 36px;
        border: 1px solid #cfd7e6;
        border-radius: 5px;
        padding: 7px 10px;
        background-color: #ffffff;
    }
    .rhip-field input[disabled],
    .rhip-field input[readonly] {
        background: #f5f7fb;
        color: #4b587c;
    }
    .rhip-field textarea {
        min-height: 92px;
        resize: vertical;
    }
    .rhip-full {
        grid-column: 1 / -1;
    }
    .rhip-subsection {
        border: 1px solid #edf1f7;
        border-radius: 6px;
        padding: 12px;
        background: #fbfcfe;
    }
    .rhip-subsection-title {
        margin: 0 0 10px;
        font-size: 14px;
        font-weight: bold;
        color: #10192f;
    }
    .rhip-procedure-list {
        border: 1px solid #d8dfeb;
        border-radius: 5px;
        max-height: 188px;
        overflow-y: auto;
        background: #fff;
    }
    .rhip-procedure-option {
        display: block;
        width: 100%;
        border: 0;
        border-bottom: 1px solid #edf1f7;
        background: #fff;
        color: #17203a;
        cursor: pointer;
        padding: 10px 12px;
        text-align: left;
        font-size: 13px;
    }
    .rhip-procedure-option:hover,
    .rhip-procedure-option.selected {
        background: #dfe8ff;
    }
    .rhip-diagnosis-results {
        display: none;
        border: 1px solid #d8dfeb;
        border-radius: 5px;
        margin-top: 6px;
        max-height: 150px;
        overflow-y: auto;
        background: #fff;
    }
    .rhip-diagnosis-option {
        display: block;
        width: 100%;
        border: 0;
        border-bottom: 1px solid #edf1f7;
        background: #fff;
        color: #17203a;
        cursor: pointer;
        padding: 8px 10px;
        text-align: left;
        font-size: 13px;
    }
    .rhip-diagnosis-option:hover {
        background: #dfe8ff;
    }
    .rhip-diagnosis-option.disabled {
        color: #8a94a9;
        cursor: not-allowed;
        background: #f5f7fb;
    }
    .rhip-selected-diagnosis {
        margin-top: 6px;
        min-height: 18px;
        color: #4b587c;
        font-size: 12px;
    }
    .rhip-muted {
        color: #667493;
    }
    .rhip-error {
        color: #be2533;
        margin: 8px 0 0;
    }
    .rhip-table-wrap {
        border: 1px solid #d8dfeb;
        background: #ffffff;
        border-radius: 6px;
        overflow-x: auto;
    }
    table.rhip-table {
        width: 100%;
        border-collapse: collapse;
        min-width: 1040px;
    }
    .rhip-table th {
        background: #f5f7fb;
        color: #1f2a44;
        font-size: 12px;
        text-transform: uppercase;
        border-bottom: 1px solid #d8dfeb;
        padding: 10px 8px;
        text-align: left;
    }
    .rhip-table td {
        border-bottom: 1px solid #edf1f7;
        padding: 12px 10px;
        vertical-align: top;
        color: #17203a;
    }
    .rhip-chip {
        display: inline-block;
        min-width: 94px;
        padding: 5px 10px;
        border-radius: 12px;
        text-align: center;
        font-weight: bold;
        font-size: 12px;
        border: 1px solid transparent;
    }
    .rhip-chip-PENDING {
        color: #b36b00;
        background: #fff6dd;
        border-color: #ffc96b;
    }
    .rhip-chip-APPROVED {
        color: #207638;
        background: #e5f8eb;
        border-color: #5abb76;
    }
    .rhip-chip-PARTIALLY_APPROVED {
        color: #0b4f6c;
        background: #d9edf7;
    }
    .rhip-chip-REJECTED,
    .rhip-chip-FAILED {
        color: #be2533;
        background: #fff0f2;
        border-color: #e94b5b;
    }
    .rhip-actions {
        display: flex;
        align-items: center;
        flex-wrap: wrap;
        gap: 8px;
        min-width: 188px;
    }
    .rhip-actions form {
        margin: 0;
    }
    .rhip-status-notification {
        display: none;
        position: fixed;
        right: 24px;
        top: 84px;
        width: 360px;
        max-width: calc(100vw - 48px);
        background: #ffffff;
        border: 1px solid #cfd7e6;
        border-left: 5px solid #5abb76;
        border-radius: 6px;
        box-shadow: 0 10px 28px rgba(23, 32, 58, 0.18);
        padding: 14px;
        z-index: 1100;
    }
    .rhip-status-notification h3 {
        margin: 0 0 8px;
        font-size: 16px;
        color: #17203a;
    }
    .rhip-status-notification ul {
        margin: 0 0 12px 18px;
        padding: 0;
        color: #17203a;
    }
    .rhip-status-notification-actions {
        text-align: right;
    }
    .rhip-secondary-button {
        border: 1px solid #c7d2e4;
        background: #ffffff;
        color: #1f2a44;
    }
    .rhip-secondary-button:hover,
    .rhip-secondary-button:focus {
        border-color: #8fa2c4;
        background: #f5f8ff;
        color: #10192f;
        box-shadow: 0 0 0 3px rgba(143, 162, 196, 0.16);
    }
    .rhip-secondary-button.rhip-retry-button {
        border-color: #f3b064;
        background: #fff8ed;
        color: #8a4b00;
    }
    .rhip-secondary-button.rhip-retry-button:hover,
    .rhip-secondary-button.rhip-retry-button:focus {
        border-color: #d89037;
        background: #fff1d9;
        color: #6c3c00;
        box-shadow: 0 0 0 3px rgba(243, 176, 100, 0.22);
    }
    .rhip-secondary-button.rhip-status-button {
        border-color: #9fc8ad;
        background: #f1fbf5;
        color: #207638;
    }
    .rhip-secondary-button.rhip-status-button:hover,
    .rhip-secondary-button.rhip-status-button:focus {
        border-color: #6cad81;
        background: #e5f8eb;
        color: #185a2b;
        box-shadow: 0 0 0 3px rgba(90, 187, 118, 0.16);
    }
    .rhip-empty {
        padding: 24px;
        text-align: center;
        color: #667493;
    }
    .rhip-details-panel {
        display: none;
        border: 1px solid #cfd7e6;
        border-radius: 6px;
        padding: 12px;
        margin-top: 12px;
        background: #fbfcfe;
    }
    .rhip-details-grid {
        display: grid;
        grid-template-columns: repeat(2, minmax(220px, 1fr));
        gap: 10px;
    }
    .rhip-detail-box {
        border: 1px solid #d8dfeb;
        border-radius: 5px;
        background: #fff;
        padding: 10px;
        white-space: pre-wrap;
        max-height: 220px;
        overflow: auto;
    }
    @media (max-width: 760px) {
        .rhip-form-grid,
        .rhip-details-grid {
            grid-template-columns: 1fr;
        }
    }
</style>

<div class="rhip-approval-page">
    <div class="rhip-approval-header">
        <h1 class="rhip-approval-title">Insurance Approval Requests</h1>
        <p class="rhip-approval-subtitle">Create approval requests separately from voucher creation and track insurer decisions.</p>
    </div>

    <div class="rhip-section">
        <div class="rhip-section-header">
            <h2>Request Approval</h2>
            <% if (canRequestApproval) { %>
                <button type="button" class="rhip-primary-button" onclick="openRhipApprovalModal();">
                    <i class="icon-plus"></i><span class="rhip-button-label">New Approval Request</span>
                </button>
            <% } %>
        </div>
        <div class="rhip-muted">Start a new request, choose the patient insurance, diagnosis, procedure, and quantity.</div>
    </div>

    <div id="rhip-approval-modal" class="rhip-modal">
        <div class="rhip-modal-dialog">
            <div class="rhip-modal-header">
                <h2>New Approval Request</h2>
                <button type="button" class="rhip-modal-close" onclick="closeRhipApprovalModal();">x</button>
            </div>
            <div class="rhip-modal-body">
                <div class="rhip-form-grid">
                    <div class="rhip-subsection">
                        <h3 class="rhip-subsection-title">Select Patient</h3>
                        <form id="approval-patient-search-form" method="get" action="${ ui.pageLink("rwandaemr", "admin/rhipApprovals") }" onsubmit="return submitRhipPatientSearch(this);">
                            <input type="hidden" name="openRequest" value="true"/>
                            <input type="hidden" id="approval-search-query" name="searchQuery" value="${ ui.escapeAttribute(searchQuery ?: "") }"/>
                            <div class="rhip-field">
                                <label>Patient name, insurance number, System ID, or Primary ID</label>
                                ${ ui.includeFragment("pihapps", "field/patient", [
                                        id: "approval-patient",
                                        formFieldName: "patientId",
                                        initialValue: patient,
                                        placeholder: "coreapps.searchPatientHeading"
                                ]) }
                            </div>
                        </form>
                        <% if (patient) { %>
                            <div style="margin-top: 12px;">
                                <strong>${ ui.format(patient) }</strong>
                                <div class="rhip-muted">System ID: ${ ui.format(patient.patientIdentifier?.identifier ?: "") }</div>
                                <div class="rhip-muted">RHIP ID: ${ ui.format(patientIdentifier ?: "") }</div>
                            </div>
                        <% } else { %>
                            <div class="rhip-muted" style="margin-top: 12px;">No patient selected.</div>
                        <% } %>
                    </div>

                    <div class="rhip-subsection">
                        <h3 class="rhip-subsection-title">Select Insurance</h3>
                        <% if (patient && approvalPolicies) { %>
                            <form method="get" action="${ ui.pageLink("rwandaemr", "admin/rhipApprovals") }">
                                <input type="hidden" name="openRequest" value="true"/>
                                <input type="hidden" name="searchQuery" value="${ ui.escapeAttribute(searchQuery ?: "") }"/>
                                <input type="hidden" name="patientId" value="${ ui.escapeAttribute(patient.uuid) }"/>
                                <div class="rhip-field">
                                    <label>Insurance policy</label>
                                    <select name="insurancePolicyId" onchange="this.form.submit();">
                                        <% approvalPolicies.each { approvalPolicy -> %>
                                            <% def policy = approvalPolicy.policy %>
                                            <option value="${ ui.escapeAttribute(policy.insurancePolicyId?.toString() ?: "") }" ${ selectedPolicy?.policy?.insurancePolicyId == policy.insurancePolicyId ? "selected=\"selected\"" : "" }>
                                                ${ ui.format(approvalPolicy.label) } (${ ui.format(approvalPolicy.insuranceType) })
                                            </option>
                                        <% } %>
                                    </select>
                                </div>
                            </form>
                            <div class="rhip-muted" style="margin-top: 10px;">Member ID: ${ ui.format(selectedPolicy?.policy?.insuranceCardNo ?: "") }</div>
                        <% } else if (patient) { %>
                            <div class="rhip-muted">No approval-supported insurance policy found for this patient.</div>
                        <% } else { %>
                            <div class="rhip-muted">Select a patient first.</div>
                        <% } %>
                    </div>

                    <div class="rhip-subsection">
                        <h3 class="rhip-subsection-title">Procedure Requiring Approval</h3>
                        <% if (patient && selectedPolicy) { %>
                            <form method="get" action="${ ui.pageLink("rwandaemr", "admin/rhipApprovals") }" style="margin-bottom: 10px;">
                                <input type="hidden" name="openRequest" value="true"/>
                                <input type="hidden" name="searchQuery" value="${ ui.escapeAttribute(searchQuery ?: "") }"/>
                                <input type="hidden" name="patientId" value="${ ui.escapeAttribute(patient.uuid) }"/>
                                <input type="hidden" name="insurancePolicyId" value="${ ui.escapeAttribute(selectedPolicy?.policy?.insurancePolicyId?.toString() ?: "") }"/>
                                <input type="hidden" name="productPage" value="${ ui.escapeAttribute(productPage?.toString() ?: "") }"/>
                                <input type="hidden" name="productLimit" value="${ ui.escapeAttribute(productLimit?.toString() ?: "") }"/>
                                <div class="rhip-field">
                                    <label>Search procedure</label>
                                    <input type="text" name="productSearch" value="${ ui.escapeAttribute(productSearch ?: "") }" placeholder="Filter procedure"/>
                                </div>
                            </form>
                            <% if (productError) { %>
                                <p class="rhip-error">${ ui.format(productError) }</p>
                            <% } %>
                            <% if (products) { %>
                                <div class="rhip-procedure-list">
                                    <% products.each { product -> %>
                                        <% def procedureId = "procedure-" + (product.productCode ?: "").replaceAll("[^a-zA-Z0-9_-]", "-") %>
                                        <button type="button" id="${ ui.escapeAttribute(procedureId) }" class="rhip-procedure-option"
                                                onclick="selectRhipApprovalProduct('${ ui.encodeJavaScript(product.productCode ?: "") }', '${ ui.encodeJavaScript(product.productName ?: "") }', '${ ui.encodeJavaScript(product.productType ?: "") }', '${ ui.encodeJavaScript(product.tariffPrice ?: "") }');">
                                            ${ ui.format(product.productName ?: product.productCode ?: "") }
                                            <span class="rhip-muted">${ ui.format(product.productCode ?: "") }</span>
                                        </button>
                                    <% } %>
                                </div>
                            <% } else { %>
                                <div class="rhip-muted">No approval-required procedures loaded.</div>
                            <% } %>
                        <% } else { %>
                            <div class="rhip-muted">Select patient and insurance first.</div>
                        <% } %>
                    </div>

                    <div class="rhip-subsection">
                        <h3 class="rhip-subsection-title">Request Details</h3>
                        <form method="post" action="${ ui.pageLink("rwandaemr", "admin/rhipApprovals") }" onsubmit="return submitRhipApprovalRequest(this);">
                            <input type="hidden" name="action" value="requestApproval"/>
                            <input type="hidden" name="searchQuery" value="${ ui.escapeAttribute(searchQuery ?: "") }"/>
                            <% if (patient) { %>
                                <input type="hidden" name="patientId" value="${ ui.escapeAttribute(patient.uuid) }"/>
                            <% } %>
                            <input type="hidden" name="insurancePolicyId" value="${ ui.escapeAttribute(selectedPolicy?.policy?.insurancePolicyId?.toString() ?: "") }"/>
                            <input type="hidden" name="productSearch" value="${ ui.escapeAttribute(productSearch ?: "") }"/>
                            <input type="hidden" name="productPage" value="${ ui.escapeAttribute(productPage?.toString() ?: "") }"/>
                            <input type="hidden" name="productLimit" value="${ ui.escapeAttribute(productLimit?.toString() ?: "") }"/>
                            <input type="hidden" id="productCode" name="productCode"/>
                            <input type="hidden" id="productName" name="productName"/>
	                            <input type="hidden" id="productType" name="productType"/>
	                            <input type="hidden" id="tariffPrice" name="tariffPrice"/>
	                            <% if ((selectedPolicy?.insuranceType ?: "").equalsIgnoreCase("mmi")) { %>
	                                <input type="hidden" name="receptionNumber" value="${ ui.escapeAttribute(approvalIdentifier ?: "") }"/>
	                            <% } %>

	                            <div class="rhip-field">
	                                <label>Select Diagnosis</label>
                                <% if (patient && patientDiagnoses) { %>
                                    <select id="diagnosisIds" name="diagnosisIds">
                                        <option value="">Select recorded diagnosis</option>
                                        <% patientDiagnoses.each { diagnosis -> %>
                                            <option value="${ ui.escapeAttribute(diagnosis.code ?: "") }">
                                                ${ ui.format(diagnosis.label ?: diagnosis.code ?: "") }
                                            </option>
                                        <% } %>
                                    </select>
                                <% } else if (patient) { %>
                                    <input type="hidden" id="diagnosisIds" name="diagnosisIds"/>
                                    <div class="rhip-muted">No diagnosis recorded for this patient.</div>
                                <% } else { %>
                                    <input type="hidden" id="diagnosisIds" name="diagnosisIds"/>
                                    <div class="rhip-muted">Select a patient first.</div>
                                <% } %>
                            </div>
                            <div class="rhip-field" style="margin-top: 10px;">
                                <label>Selected Procedure</label>
                                <input type="text" id="selected-product-label" disabled="disabled" placeholder="Select procedure"/>
                            </div>
                            <div class="rhip-field" style="margin-top: 10px;">
	                                <label>Quantity</label>
	                                <input id="requestedQuantity" type="number" step="0.01" min="0.01" name="requestedQuantity" value="1"/>
	                            </div>
	                            <% if ((selectedPolicy?.insuranceType ?: "").equalsIgnoreCase("mmi")) { %>
	                                <div class="rhip-field" style="margin-top: 10px;">
	                                    <label>MMI Reception Number</label>
	                                    <input type="text" value="${ ui.escapeAttribute(approvalIdentifier ?: "") }" disabled="disabled"/>
	                                </div>
	                            <% } else if (selectedPolicy) { %>
	                                <div class="rhip-field" style="margin-top: 10px;">
	                                    <label>RAMA Patient Identifier</label>
	                                    <input type="text" value="${ ui.escapeAttribute(approvalIdentifier ?: "") }" disabled="disabled"/>
	                                </div>
	                            <% } %>
                            <div class="rhip-field" style="margin-top: 10px;">
                                <label>License Number</label>
                                <input type="text" name="practitionerLicenseNumber" value="${ ui.escapeAttribute(currentPractitionerLicenseNumber ?: "") }" readonly="readonly"/>
                            </div>
                            <div class="rhip-field" style="margin-top: 10px;">
                                <label>Medical Notes</label>
                                <textarea name="clinicalKnowledge" placeholder="Enter medical notes"></textarea>
                            </div>
                            <div style="margin-top: 14px;">
                                <button type="submit" class="rhip-primary-button">
                                    <i class="icon-ok"></i><span class="rhip-button-label">Request Approval</span>
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            </div>
        </div>
    </div>

    <div class="rhip-section">
        <div class="rhip-section-header">
            <h2>Requested Approvals</h2>
        </div>
        <% if (approvalRequests) { %>
            <div class="rhip-table-wrap">
                <table class="rhip-table">
                    <thead>
                    <tr>
                        <th>Patient</th>
                        <th>Insurance</th>
                        <th>Diagnosis</th>
                        <th>Procedure</th>
                        <th>Qty</th>
                        <th>Request Date</th>
                        <th>Status</th>
                        <th>Action</th>
                    </tr>
                    </thead>
                    <tbody>
                    <% approvalRequests.each { view -> %>
                        <% def request = view.request %>
                        <% def detailsId = "approval-details-" + request.id %>
                        <tr data-rhip-approval-id="${ ui.escapeAttribute(request.id?.toString() ?: "") }"
                            data-rhip-approval-status="${ ui.escapeAttribute(view.statusClass ?: "") }">
                            <td>${ ui.format(view.patientName ?: "") }</td>
                            <td>
                                ${ ui.format(request.insuranceName ?: "") }
                                <div class="rhip-muted">${ ui.format(request.insuranceType ?: "") }</div>
                            </td>
                            <td>${ ui.format(request.diagnosisIds ?: "") }</td>
                            <td>
                                ${ ui.format(request.productName ?: "") }
                                <div class="rhip-muted">${ ui.format(request.productCode ?: "") }</div>
                            </td>
                            <td>${ ui.format(request.requestedQuantity ?: "") }</td>
                            <td>${ ui.format(request.dateCreated) }</td>
                            <td><span class="rhip-chip rhip-chip-${ ui.escapeAttribute(view.statusClass) }">${ ui.format(view.statusLabel) }</span></td>
                            <td class="rhip-actions">
                                <button type="button" class="rhip-secondary-button" onclick="showRhipApprovalDetails('${ ui.escapeAttribute(detailsId) }');">
                                    <i class="icon-eye-open"></i><span class="rhip-button-label">View</span>
                                </button>
                                <% if (canCheckApprovalStatus && request.approvalCode) { %>
                                    <form method="post" action="${ ui.pageLink("rwandaemr", "admin/rhipApprovals") }" onsubmit="return submitRhipApprovalStatus(this);">
                                        <input type="hidden" name="action" value="checkStatus"/>
                                        <input type="hidden" name="approvalRequestId" value="${ ui.escapeAttribute(request.id?.toString() ?: "") }"/>
                                        <input type="hidden" name="patientId" value="${ ui.escapeAttribute((view.patient?.uuid ?: "").toString()) }"/>
                                        <button type="submit" class="rhip-secondary-button rhip-status-button">
                                            <i class="icon-refresh"></i><span class="rhip-button-label">Check Status</span>
                                        </button>
                                    </form>
                                <% } %>
                                <% if (canRequestApproval && "FAILED".equalsIgnoreCase(view.statusClass ?: "")) { %>
                                    <form method="post" action="${ ui.pageLink("rwandaemr", "admin/rhipApprovals") }" onsubmit="return submitRhipApprovalRetry(this);">
                                        <input type="hidden" name="action" value="retryApproval"/>
                                        <input type="hidden" name="approvalRequestId" value="${ ui.escapeAttribute(request.id?.toString() ?: "") }"/>
                                        <input type="hidden" name="patientId" value="${ ui.escapeAttribute((view.patient?.uuid ?: "").toString()) }"/>
                                        <button type="submit" class="rhip-secondary-button rhip-retry-button">
                                            <i class="icon-repeat"></i><span class="rhip-button-label">Retry</span>
                                        </button>
                                    </form>
                                <% } %>
                            </td>
                        </tr>
                        <tr>
                            <td colspan="8" style="padding: 0; border-bottom: 0;">
                                <div id="${ ui.escapeAttribute(detailsId) }" class="rhip-details-panel">
                                    <div class="rhip-section-header" style="margin-bottom: 10px;">
                                        <h2>Approval Details</h2>
                                        <button type="button" class="rhip-secondary-button" onclick="hideRhipApprovalDetails('${ ui.escapeAttribute(detailsId) }');">
                                            <i class="icon-remove"></i><span class="rhip-button-label">Close</span>
                                        </button>
                                    </div>
                                    <div class="rhip-details-grid">
                                        <div>
                                            <strong>Patient</strong>
                                            <div>${ ui.format(view.patientName ?: "") }</div>
                                        </div>
                                        <div>
                                            <strong>Approval Code</strong>
                                            <div>${ ui.format(request.approvalCode ?: "") }</div>
                                        </div>
                                        <div>
                                            <strong>Insurance</strong>
                                            <div>${ ui.format(request.insuranceName ?: "") } / ${ ui.format(request.insuranceCardNo ?: "") }</div>
                                        </div>
                                        <div>
                                            <strong>Status</strong>
                                            <div>${ ui.format(view.statusLabel) }</div>
                                        </div>
                                        <div>
                                            <strong>Diagnosis</strong>
                                            <div>${ ui.format(request.diagnosisIds ?: "") }</div>
                                        </div>
                                        <div>
                                            <strong>License Number</strong>
                                            <div>${ ui.format(request.practitionerLicenseNumber ?: "") }</div>
                                        </div>
                                        <div class="rhip-full">
                                            <strong>Medical Notes</strong>
                                            <div class="rhip-detail-box">${ ui.format(request.clinicalKnowledge ?: "") }</div>
                                        </div>
                                        <div class="rhip-full">
                                            <strong>Insurer Response / Rejection Reason</strong>
                                            <div class="rhip-detail-box">${ ui.format(request.message ?: "No response message recorded.") }</div>
                                        </div>
                                        <div class="rhip-full">
                                            <strong>Raw Response</strong>
                                            <div class="rhip-detail-box">${ ui.format(request.responsePayload ?: "") }</div>
                                        </div>
                                    </div>
                                </div>
                            </td>
                        </tr>
                    <% } %>
                    </tbody>
                </table>
            </div>
        <% } else { %>
            <div class="rhip-empty">No approval requests have been submitted yet.</div>
        <% } %>
    </div>
</div>

<div id="rhip-status-notification" class="rhip-status-notification">
    <h3>Approval Status Updated</h3>
    <ul id="rhip-status-notification-list"></ul>
    <div class="rhip-status-notification-actions">
        <button type="button" class="rhip-secondary-button" onclick="hideRhipApprovalStatusNotification();">
            <i class="icon-remove"></i><span class="rhip-button-label">Close</span>
        </button>
        <button type="button" class="rhip-primary-button" onclick="window.location.reload();">
            <i class="icon-refresh"></i><span class="rhip-button-label">Refresh</span>
        </button>
    </div>
</div>
