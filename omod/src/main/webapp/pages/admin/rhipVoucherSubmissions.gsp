<%
    ui.decorateWith("appui", "standardEmrPage")
    def openmrsContextPath = contextPath ? (contextPath.startsWith("/") ? contextPath : "/" + contextPath) : ""
%>

<script type="text/javascript">
    var breadcrumbs = [
        { icon: "icon-home", link: '${ openmrsContextPath }/index.htm' },
        { label: "${ ui.message("coreapps.app.system.administration.label") }", link: "${ ui.pageLink("coreapps", "systemadministration/systemAdministration") }" },
        { label: "RHIP Voucher Submission", link: "${ ui.pageLink("rwandaemr", "admin/rhipVoucherSubmissions") }" }
    ];
</script>

<style>
    /* The tables have many columns: use the screen's width rather than the decorator's narrow column */
    #body-wrapper, #content {
        max-width: none !important;
        width: auto !important;
    }
    .rhip-page {
        max-width: 1800px;
        margin: 0 auto;
        padding: 0 16px;
    }
    .rhip-page-header {
        display: flex;
        align-items: flex-end;
        justify-content: space-between;
        gap: 16px;
        margin: 8px 0 18px;
    }
    .rhip-page-title {
        margin: 0;
        font-size: 28px;
        line-height: 1.15;
        color: #263238;
    }
    .rhip-page-subtitle {
        margin: 5px 0 0;
        color: #607d8b;
        font-size: 14px;
    }
    .rhip-count-card {
        min-width: 170px;
        border: 1px solid #d9e2e7;
        background: #ffffff;
        padding: 12px 16px;
        text-align: right;
        border-radius: 4px;
    }
    .rhip-count-card strong {
        display: block;
        font-size: 24px;
        color: #263238;
    }
    .rhip-filter-panel {
        border: 1px solid #d9e2e7;
        background: #ffffff;
        padding: 14px;
        margin-bottom: 14px;
        border-radius: 4px;
    }
    .rhip-filter-grid {
        display: grid;
        grid-template-columns: 150px 150px 150px 170px 170px 170px minmax(220px, 1fr) 150px 140px;
        gap: 10px;
        align-items: end;
    }
    .rhip-field label {
        display: block;
        margin-bottom: 4px;
        font-size: 12px;
        font-weight: bold;
        color: #455a64;
        text-transform: uppercase;
    }
    .rhip-field input,
    .rhip-field select {
        width: 100%;
        box-sizing: border-box;
        min-height: 34px;
        border: 1px solid #b0bec5;
        border-radius: 3px;
        padding: 6px 8px;
        background-color: #ffffff;
    }
    .rhip-filter-actions {
        display: flex;
        gap: 8px;
    }
    .rhip-filter-actions input,
    .rhip-filter-actions a {
        min-height: 34px;
        box-sizing: border-box;
        white-space: nowrap;
    }
    .rhip-table-wrap {
        border: 1px solid #d9e2e7;
        background: #ffffff;
        border-radius: 4px;
        overflow-x: auto;
    }
    table.rhip-table {
        width: 100%;
        border-collapse: collapse;
        min-width: 1280px;
    }
    .rhip-table th {
        background: #f3f6f8;
        color: #455a64;
        font-size: 12px;
        text-transform: uppercase;
        border-bottom: 1px solid #d9e2e7;
        padding: 10px 8px;
        text-align: left;
    }
    .rhip-table td {
        border-bottom: 1px solid #edf2f5;
        padding: 10px 8px;
        vertical-align: top;
        color: #263238;
    }
    .rhip-table tr.rhip-row:hover td {
        background: #f7fbfc;
        cursor: pointer;
    }
    .rhip-number {
        text-align: right;
        white-space: nowrap;
    }
    .rhip-muted {
        color: #78909c;
    }
    .rhip-chip {
        display: inline-block;
        min-width: 74px;
        padding: 4px 8px;
        border-radius: 12px;
        text-align: center;
        font-weight: bold;
        font-size: 12px;
    }
    .rhip-chip-NOT_SENT {
        color: #455a64;
        background: #eceff1;
    }
    .rhip-chip-PROCESSING {
        color: #795548;
        background: #fff3cd;
    }
    .rhip-chip-SENT {
        color: #1b5e20;
        background: #dff0d8;
    }
    .rhip-chip-CONFIRMED {
        color: #ffffff;
        background: #2e7d32;
    }
    .rhip-chip-FAILED {
        color: #9f1d1d;
        background: #f8d7da;
    }
    .rhip-chip-QUEUED {
        color: #0b5394;
        background: #d9edf7;
    }
    .rhip-actions {
        white-space: nowrap;
    }
    .rhip-action-group {
        display: flex;
        align-items: stretch;
        gap: 6px;
    }
    .rhip-actions form {
        display: flex;
        align-items: stretch;
        margin: 0;
    }
    .rhip-actions button[type=submit],
    .rhip-open-link {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        box-sizing: border-box;
        width: 34px;
        min-width: 34px;
        height: 34px;
        min-height: 34px;
        margin: 0;
        padding: 0;
        border-radius: 3px;
        font-size: 14px;
        line-height: 1;
        text-decoration: none;
        cursor: pointer;
        transition: background-color 120ms ease, border-color 120ms ease, box-shadow 120ms ease, color 120ms ease;
    }
    .rhip-actions button[type=submit] i,
    .rhip-open-link i {
        font-size: 14px;
        line-height: 1;
    }
    .rhip-actions button[type=submit]:disabled {
        opacity: 0.55;
        cursor: not-allowed;
        box-shadow: none;
    }
    .rhip-actions .primary {
        background: #007fff;
        color: #ffffff;
        border: 1px solid #0066cc;
    }
    .rhip-actions .primary:hover,
    .rhip-actions .primary:focus {
        background: #006bd6;
        border-color: #005ab5;
        box-shadow: 0 0 0 3px rgba(0, 127, 255, 0.16);
    }
    .rhip-actions .retry {
        background: #ff9800;
        color: #ffffff;
        border: 1px solid #e08600;
    }
    .rhip-actions .retry:hover,
    .rhip-actions .retry:focus {
        background: #ed8d00;
        border-color: #c87500;
        box-shadow: 0 0 0 3px rgba(255, 152, 0, 0.2);
    }
    .rhip-actions .confirm {
        background: #2e7d32;
        color: #ffffff;
        border: 1px solid #1b5e20;
    }
    .rhip-actions .confirm:hover,
    .rhip-actions .confirm:focus {
        background: #256b29;
        border-color: #174d1a;
        box-shadow: 0 0 0 3px rgba(46, 125, 50, 0.16);
    }
    .rhip-open-link,
    .rhip-open-link:link,
    .rhip-open-link:visited,
    .rhip-open-link:hover,
    .rhip-open-link:focus,
    .rhip-open-link:active {
        border: 1px solid #4a235a;
        background: #5b2c6f;
        color: #ffffff !important;
        text-decoration: none;
    }
    .rhip-open-link:hover,
    .rhip-open-link:focus {
        background: #4a235a;
        border-color: #351941;
        box-shadow: 0 0 0 3px rgba(91, 44, 111, 0.16);
    }
    .rhip-detail-row {
        display: none;
    }
    .rhip-detail-row td {
        background: #fbfdfe;
        padding: 16px;
    }
    .rhip-detail-grid {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 14px;
    }
    .rhip-transfer-tag {
        display: inline-block;
        padding: 2px 8px;
        border-radius: 50px;
        font-size: 12px;
        font-weight: 700;
        white-space: nowrap;
    }
    .rhip-transfer-received { background: #e3eedd; color: #2f5a12; }
    .rhip-transfer-sent { background: #e6e6f0; color: #323372; }
    .rhip-transfer-none { background: #fff1d6; color: #7a4f00; }
    .rhip-verification-actions .rhip-btn-transfer {
        color: #0f5149;
        border: 1px solid #0f5149;
        min-height: 32px;
    }
    .rhip-verification-actions .rhip-btn-transfer:hover:not([disabled]) {
        background: #e3eedd;
    }
    .rhip-transfer-overlay {
        position: fixed;
        inset: 0;
        background: rgba(0, 0, 0, 0.45);
        z-index: 20000;
    }
    .rhip-transfer-dialog {
        position: fixed;
        top: 50%;
        left: 50%;
        transform: translate(-50%, -50%);
        z-index: 20001;
        width: 1400px;
        max-width: 95vw;
        max-height: 92vh;
        display: flex;
        flex-direction: column;
        background: #ffffff;
        border: 1px solid #00473f;
        border-radius: 4px;
        box-shadow: 0 4px 20px rgba(0, 0, 0, 0.3);
    }
    .rhip-transfer-dialog[hidden], .rhip-transfer-overlay[hidden] { display: none; }
    .rhip-transfer-dialog-head {
        display: flex;
        align-items: flex-start;
        justify-content: space-between;
        gap: 12px;
        padding: 12px 16px;
        border-bottom: 1px solid #dddddd;
    }
    .rhip-transfer-dialog-head h3 { margin: 0 0 2px; color: #0f5149; }
    .rhip-transfer-dialog-body {
        overflow: auto;
        padding: 12px 16px;
        flex: 1 1 auto;
    }
    .rhip-transfer-dialog-foot {
        display: flex;
        justify-content: flex-end;
        gap: 8px;
        padding: 10px 16px;
        border-top: 1px solid #dddddd;
    }
    .rhip-transfer-error { color: #a94442; }
    .rhip-detail-card {
        border: 1px solid #d9e2e7;
        background: #ffffff;
        border-radius: 4px;
        padding: 12px;
    }
    .rhip-detail-card h4 {
        margin: 0 0 10px;
        color: #263238;
    }
    .rhip-kv {
        display: grid;
        grid-template-columns: 150px 1fr;
        gap: 6px 10px;
        font-size: 13px;
    }
    .rhip-kv label {
        color: #607d8b;
        font-weight: bold;
    }
    .rhip-mini-table {
        width: 100%;
        border-collapse: collapse;
    }
    .rhip-mini-table th,
    .rhip-mini-table td {
        padding: 7px;
        border-bottom: 1px solid #edf2f5;
        font-size: 12px;
    }
    .rhip-pagination {
        display: flex;
        align-items: center;
        gap: 12px;
        margin: 14px 0;
    }
    .rhip-empty {
        padding: 28px;
        text-align: center;
        color: #607d8b;
    }
    .rhip-chip-ACCEPTED {
        color: #1b5e20;
        background: #dff0d8;
    }
    .rhip-chip-REJECTED {
        color: #9f1d1d;
        background: #f8d7da;
    }
    .rhip-chip-PENDING {
        color: #455a64;
        background: #eceff1;
    }
    .rhip-mini-table tr.rhip-journey-row {
        cursor: pointer;
    }
    .rhip-mini-table tr.rhip-journey-row:hover td,
    .rhip-mini-table tr.rhip-journey-row:focus td {
        background: #e3f0ff;
    }
    .rhip-journey-row:focus {
        outline: 2px solid #007fff;
        outline-offset: -2px;
    }
    .rhip-view-link {
        color: #007fff;
        font-weight: bold;
        white-space: nowrap;
    }
    .rhip-mini-table tr.rhip-category-row td {
        background: #eef3f8;
        font-weight: 600;
        text-transform: uppercase;
        letter-spacing: .02em;
    }
    .rhip-mini-table tr.rhip-category-row td .rhip-muted {
        font-weight: 400;
        text-transform: none;
    }
    .rhip-mini-table tr.rhip-item-REJECTED td {
        background: #fdf1f2;
    }
    .rhip-mini-table tr.rhip-item-REJECTED .rhip-item-name {
        text-decoration: line-through;
        text-decoration-color: #9f1d1d;
    }
    .rhip-bulk-actions {
        display: inline-flex;
        flex-wrap: wrap;
        gap: 8px;
        align-items: center;
    }
    .rhip-bulk-actions[hidden],
    .rhip-decision-reason[hidden] {
        display: none;
    }
    .rhip-decision {
        display: flex;
        flex-wrap: wrap;
        gap: 6px;
        align-items: center;
    }
    .rhip-decision form {
        margin: 0;
    }
    .rhip-decision button,
    .rhip-reject-form button,
    .rhip-verification-actions button {
        min-height: 28px;
        padding: 3px 10px;
        border-radius: 3px;
        font-size: 12px;
        font-weight: bold;
        cursor: pointer;
        background: #ffffff;
    }
    .rhip-btn-accept {
        color: #2e7d32;
        border: 1px solid #2e7d32;
    }
    .rhip-btn-reject {
        color: #b3261e;
        border: 1px solid #b3261e;
    }
    .rhip-btn-decision[disabled] {
        cursor: not-allowed;
        opacity: 0.55;
    }
    .rhip-btn-decision.selected[disabled] {
        opacity: 1;
    }
    .rhip-btn-change {
        color: #455a64;
        border: 1px solid #b0bec5;
    }
    .rhip-btn-change[hidden] {
        display: none;
    }
    .rhip-btn-accept.selected {
        color: #ffffff;
        background: #2e7d32 !important;
    }
    .rhip-btn-reject.selected {
        color: #ffffff;
        background: #b3261e !important;
    }
    .rhip-decision-reason {
        flex-basis: 100%;
        color: #607d8b;
        font-size: 12px;
    }
    .rhip-reject-row {
        display: none;
    }
    .rhip-reject-row td {
        background: #fbfdfe;
    }
    .rhip-reject-form {
        display: grid;
        grid-template-columns: minmax(180px, 260px) minmax(180px, 1fr) minmax(180px, 1fr) auto;
        gap: 8px;
        align-items: end;
        margin: 0;
    }
    .rhip-reject-form label {
        display: block;
        font-size: 11px;
        font-weight: bold;
        text-transform: uppercase;
        color: #455a64;
        margin-bottom: 3px;
    }
    .rhip-reject-form select,
    .rhip-reject-form input[type=text] {
        width: 100%;
        box-sizing: border-box;
        min-height: 30px;
        border: 1px solid #b0bec5;
        border-radius: 3px;
        padding: 4px 6px;
    }
    .rhip-verification-head {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        justify-content: space-between;
        gap: 8px 16px;
        margin-bottom: 10px;
    }
    .rhip-verification-head h4 {
        margin: 0;
    }
    .rhip-verification-tiles {
        display: grid;
        grid-template-columns: repeat(4, minmax(0, 1fr));
        gap: 10px;
        margin-bottom: 12px;
    }
    .rhip-verification-tile {
        border: 1px solid #d9e2e7;
        border-radius: 4px;
        padding: 8px 10px;
        background: #fbfdfe;
    }
    .rhip-verification-tile label {
        display: block;
        font-size: 11px;
        font-weight: bold;
        text-transform: uppercase;
        color: #607d8b;
    }
    .rhip-verification-tile strong {
        font-size: 17px;
        font-variant-numeric: tabular-nums;
    }
    .rhip-verification-actions {
        display: flex;
        gap: 8px;
        align-items: center;
    }
    .rhip-verification-actions form {
        margin: 0;
    }
    .rhip-verification-actions .rhip-btn-submit {
        color: #ffffff;
        background: #007fff;
        border: 1px solid #0066cc;
        min-height: 32px;
        padding: 4px 14px;
    }
    .rhip-verification-actions .rhip-btn-submit:disabled {
        opacity: 0.5;
        cursor: not-allowed;
    }
    .rhip-verification-actions .rhip-btn-history {
        color: #5b2c6f;
        border: 1px solid #5b2c6f;
        min-height: 32px;
    }
    .rhip-verification-actions .rhip-btn-history:hover {
        background: #efe6f3;
    }
    .rhip-bulk-send {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        justify-content: space-between;
        gap: 10px 16px;
        border: 1px solid #b6d4f5;
        background: #f2f8ff;
        border-radius: 4px;
        padding: 10px 14px;
        margin-bottom: 14px;
    }
    .rhip-bulk-send form {
        margin: 0;
    }
    .rhip-bulk-send .rhip-btn-submit {
        color: #ffffff;
        background: #007fff;
        border: 1px solid #0066cc;
        border-radius: 3px;
        min-height: 34px;
        padding: 4px 14px;
        font-weight: bold;
        cursor: pointer;
    }
    .rhip-bulk-send .rhip-btn-submit:disabled {
        opacity: 0.5;
        cursor: not-allowed;
    }
    .rhip-bulk-reject {
        border: 1px solid #f3c7cb;
        background: #fdf6f7;
        border-radius: 4px;
        padding: 10px;
        margin-bottom: 12px;
    }
    .rhip-audit {
        list-style: none;
        margin: 0;
        padding: 0;
        font-size: 13px;
    }
    .rhip-audit li {
        display: grid;
        grid-template-columns: 150px 1fr;
        gap: 10px;
        padding: 8px 0;
        border-bottom: 1px solid #edf2f5;
    }
    .rhip-drawer-overlay {
        display: none;
        position: fixed;
        inset: 0;
        background: rgba(38, 50, 56, 0.45);
        z-index: 1000;
    }
    .rhip-drawer {
        display: none;
        position: fixed;
        top: 0;
        right: 0;
        bottom: 0;
        width: min(640px, 100%);
        background: #ffffff;
        box-shadow: 0 10px 30px rgba(38, 50, 56, 0.25);
        z-index: 1001;
        flex-direction: column;
    }
    .rhip-drawer.open,
    .rhip-drawer-overlay.open {
        display: flex;
    }
    .rhip-drawer-head {
        padding: 14px 18px 10px;
        border-bottom: 1px solid #d9e2e7;
    }
    .rhip-drawer-head h3 {
        margin: 4px 0;
        font-size: 19px;
        color: #263238;
    }
    .rhip-drawer-close {
        float: right;
        width: 32px;
        height: 32px;
        border: 1px solid #d9e2e7;
        background: #ffffff;
        border-radius: 3px;
        font-size: 18px;
        cursor: pointer;
    }
    .rhip-drawer-meta {
        display: flex;
        flex-wrap: wrap;
        gap: 4px 14px;
        font-size: 12px;
        color: #607d8b;
    }
    .rhip-drawer-body {
        overflow-y: auto;
        padding: 14px 18px 22px;
    }
    .rhip-drawer-body h5 {
        margin: 14px 0 6px;
        font-size: 12px;
        text-transform: uppercase;
        color: #455a64;
    }
    .rhip-readonly {
        display: inline-block;
        font-size: 11px;
        font-weight: bold;
        color: #607d8b;
        border: 1px solid #d9e2e7;
        border-radius: 3px;
        padding: 0 6px;
        text-transform: uppercase;
    }
    .rhip-obs {
        display: grid;
        grid-template-columns: 200px 1fr;
        gap: 5px 12px;
        margin: 0;
        font-size: 13px;
    }
    .rhip-obs dt {
        color: #607d8b;
        font-weight: bold;
    }
    .rhip-obs dd {
        margin: 0;
    }
    .rhip-obs-group {
        grid-column: 1 / -1;
        margin-top: 6px;
        font-weight: bold;
        color: #263238;
    }
    .rhip-lab-table td {
        vertical-align: top;
    }
    .rhip-flag {
        color: #9f1d1d;
        font-weight: bold;
        margin-left: 4px;
    }
    @media (max-width: 1100px) {
        .rhip-filter-grid {
            grid-template-columns: 1fr 1fr;
        }
        .rhip-detail-grid {
            grid-template-columns: 1fr;
        }
        .rhip-reject-form,
        .rhip-verification-tiles {
            grid-template-columns: 1fr 1fr;
        }
        .rhip-obs {
            grid-template-columns: 1fr;
        }
    }
    .rhip-closure-overlay {
        position: fixed;
        inset: 0;
        background: rgba(0, 0, 0, 0.4);
        z-index: 2000;
        display: flex;
        align-items: flex-start;
        justify-content: center;
        padding: 60px 16px;
    }
    .rhip-closure-overlay[hidden] {
        display: none;
    }
    .rhip-closure-box {
        background: #fff;
        border-radius: 4px;
        max-width: 640px;
        width: 100%;
        max-height: 80vh;
        overflow-y: auto;
        padding: 16px 20px;
        box-shadow: 0 4px 16px rgba(0, 0, 0, 0.25);
    }
    .rhip-closure-table {
        width: 100%;
        border-collapse: collapse;
        margin: 8px 0 12px 0;
        font-size: 13px;
    }
    .rhip-closure-table th, .rhip-closure-table td {
        border: 1px solid #ddd;
        padding: 5px 8px;
        text-align: left;
    }
    .rhip-closure-table tbody th {
        width: 35%;
        background: #f7f7f7;
    }
    .rhip-closure-tag {
        font-size: 11px;
        color: #31708f;
        background: #d9edf7;
        border-radius: 3px;
        padding: 1px 5px;
    }
    .rhip-closure-warning {
        color: #8a6d3b;
        background: #fcf8e3;
        border: 1px solid #faebcc;
        border-radius: 3px;
        padding: 6px 10px;
    }
    .rhip-closure-error {
        color: #a94442;
    }
    .rhip-closure-buttons {
        display: flex;
        justify-content: flex-end;
        gap: 8px;
        margin-top: 12px;
    }
</style>

<script type="text/javascript">
    (function() {
        var refreshStorageKey = "rhipVoucherSubmissionsRefreshAfterGlobalBill";
        var pageWasHidden = false;

        function markRefreshNeeded() {
            try {
                window.sessionStorage.setItem(refreshStorageKey, "true");
            }
            catch (e) {
                // Ignore storage failures; the bfcache pageshow handler still covers same-tab return.
            }
        }

        function refreshIfNeeded(event) {
            var shouldRefresh = event && event.persisted;
            try {
                shouldRefresh = shouldRefresh || window.sessionStorage.getItem(refreshStorageKey) === "true";
                window.sessionStorage.removeItem(refreshStorageKey);
            }
            catch (e) {}
            if (shouldRefresh) {
                window.location.reload();
            }
        }

        function refreshIfReturningToPage() {
            if (pageWasHidden) {
                refreshIfNeeded();
            }
        }

        jq(function() {
            jq(".rhip-open-link").on("click auxclick", markRefreshNeeded);
        });
        window.addEventListener("blur", function() {
            pageWasHidden = true;
        });
        document.addEventListener("visibilitychange", function() {
            if (document.hidden) {
                pageWasHidden = true;
            }
        });
        window.addEventListener("focus", refreshIfReturningToPage);
        window.addEventListener("pageshow", refreshIfNeeded);
    })();

    function toggleRhipDetails(id) {
        var row = jq("#rhip-detail-" + id);
        row.toggle();
        if (row.is(":visible") && window.loadRhipJourney) {
            window.loadRhipJourney(id);
        }
    }

    // The message of a failed request, from the JSON error the server sends when it can
    function rhipErrorMessage(xhr, fallback) {
        try {
            var data = xhr && xhr.responseJSON ? xhr.responseJSON : JSON.parse(xhr.responseText);
            if (data && data.message) {
                return data.message;
            }
            if (data && data.error && data.error.message) {
                return data.error.message;
            }
        }
        catch (e) {
        }
        if (xhr && (xhr.status === 401 || xhr.status === 403)) {
            return "You do not have permission to do this, or your session has expired. Reload the page.";
        }
        return fallback;
    }

    // Accepting or rejecting saves the decision and updates the bill on the page, without reloading it
    function submitRhipDecision(form) {
        form = jq(form);
        var confirmText = form.data("confirm");
        if (confirmText) {
            var count = jq.trim(form.closest(".rhip-bulk-actions").find(".rhip-awaiting-count").first().text());
            if (!confirm(String(confirmText).replace("{count}", count))) {
                return;
            }
        }
        var data = {
            globalBillId: form.find("input[name=globalBillId]").val(),
            decision: form.find("input[name=decision]").val()
        };
        var itemId = form.find("input[name=itemId]").val();
        if (itemId) {
            data.itemId = itemId;
        }
        var select = form.find("select[name=reasonId]");
        if (select.length) {
            if (select.val() === "other") {
                var typed = jq.trim(form.find("input[name=otherReason]").val());
                if (!typed) {
                    alert("Type the reason.");
                    return;
                }
                data.otherReason = typed;
            }
            else {
                data.reasonId = select.val();
            }
            data.note = form.find("input[name=note]").val();
        }
        var buttons = form.find("button");
        var submitButton = form.find("button[type=submit]");
        var label = submitButton.html();
        buttons.prop("disabled", true);
        submitButton.text("Saving...");
        jq.ajax({
            url: "${ openmrsContextPath }/ws/rest/v1/rwandaemr/billVerification/decide",
            type: "POST",
            data: data,
            dataType: "json"
        }).done(function(state) {
            applyRhipBillState(state);
            if (select.length) {
                form.find("input[name=otherReason]").val("");
                form.closest(".rhip-reject-row").hide();
            }
        }).fail(function(xhr) {
            alert(rhipErrorMessage(xhr, "The decision could not be saved."));
        }).always(function() {
            submitButton.html(label);
            buttons.prop("disabled", false);
        });
    }

    // Lets the decision on an item be changed, until the step is submitted
    function unlockRhipDecision(itemId) {
        var row = jq("#rhip-item-" + itemId);
        row.find(".rhip-btn-decision").prop("disabled", false);
        row.find(".rhip-btn-change").prop("hidden", true);
    }

    // Shows the bill as the server returned it after a decision
    function applyRhipBillState(state) {
        if (!state || !state.globalBillId) {
            return;
        }
        var id = state.globalBillId;
        var cell = jq("#rhip-verify-cell-" + id);
        cell.find(".rhip-chip").first().attr("class", "rhip-chip rhip-chip-" + state.chipClass).text(state.stageLabel);
        cell.find(".rhip-muted").first().text(state.stage === "CLOSED" ? state.rejectedCount + " of " + state.itemCount + " rejected"
            : (state.awaitingCount > 0 ? state.awaitingCount + " to decide" : "Ready to submit"));
        jq("#rhip-stage-chip-" + id).attr("class", "rhip-chip rhip-chip-" + state.chipClass).text(state.stageLabel);
        jq("#rhip-tile-accepted-" + id).text(state.acceptedAmount);
        jq("#rhip-tile-rejected-" + id).text(state.rejectedAmount);
        jq("#rhip-tile-pending-" + id).text(state.pendingAmount);
        jq("#rhip-tile-todo-" + id).text(state.awaitingCount + " of " + state.itemCount);
        jq("#rhip-history-count-" + id).text(state.eventCount);

        var bulk = jq("#rhip-bulk-" + id);
        bulk.prop("hidden", !(state.canAct && state.awaitingCount > 0));
        jq("#rhip-detail-" + id).find(".rhip-awaiting-count").text(state.awaitingCount);
        if (state.awaitingCount === 0) {
            jq("#rhip-reject-all-" + id).hide();
        }
        var submit = jq("#rhip-submit-" + id);
        submit.prop("disabled", state.awaitingCount > 0);
        if (state.awaitingCount > 0) {
            submit.attr("title", "Decide on every item first");
        }
        else {
            submit.removeAttr("title");
        }

        jq.each(state.items || [], function(i, item) {
            var row = jq("#rhip-item-" + item.itemId);
            if (!row.length) {
                return;
            }
            row.attr("class", "rhip-item-" + item.outcome);
            row.find(".rhip-item-chip").attr("class", "rhip-chip rhip-item-chip rhip-chip-" + item.outcome).text(item.statusLabel);
            row.find(".rhip-btn-accept").toggleClass("selected", !!item.accepted);
            row.find("button.rhip-btn-reject").toggleClass("selected", !!item.rejected);
            // A decided item is locked until "Change" is clicked
            var decided = !!(item.accepted || item.rejected);
            row.find(".rhip-btn-decision").prop("disabled", decided);
            row.find(".rhip-btn-change").prop("hidden", !decided);
            var reasonText = (item.reason || "") + (item.reason && item.note ? ": " : "") + (item.note || "");
            row.find(".rhip-decision-reason").text(reasonText).prop("hidden", !reasonText);
        });

        // A reason typed with "Other" is offered from then on
        jq.each(state.reasons || {}, function(type, reasons) {
            jq('select[data-reason-type="' + type + '"]').each(function() {
                var select = jq(this);
                var other = select.find('option[value="other"]');
                jq.each(reasons, function(i, reason) {
                    if (!select.find('option[value="' + reason.id + '"]').length) {
                        var option = jq("<option>").val(reason.id).text(reason.name);
                        if (other.length) {
                            option.insertBefore(other);
                        }
                        else {
                            select.append(option);
                        }
                    }
                });
            });
        });
        if (state.message && window.emr && typeof emr.successMessage === "function") {
            emr.successMessage(state.message);
        }
    }

    jq(function() {
        jq(document).on("submit", "form.rhip-ajax-form", function(e) {
            e.preventDefault();
            submitRhipDecision(this);
        });
        jq(document).on("click", ".rhip-journey-retry", function(e) {
            e.stopPropagation();
            var box = jq(this).closest(".rhip-journey");
            window.loadRhipJourney(box.data("globalBillId"));
        });
        // The bill opened after a reload shows its journey straight away
        jq(".rhip-detail-row:visible").each(function() {
            window.loadRhipJourney(this.id.replace("rhip-detail-", ""));
        });
    });

    function toggleRhipRejectForm(itemId) {
        var row = jq("#rhip-reject-" + itemId);
        row.toggle();
        if (row.is(":visible")) {
            updateRhipOtherReason(row.find("select[name=reasonId]")[0]);
            row.find("select, input[type=text]").filter(":visible").first().focus();
        }
    }

    // "Other" shows a box to type a new reason, which is saved and offered from then on
    function updateRhipOtherReason(select) {
        if (!select) {
            return;
        }
        var form = jq(select).closest("form");
        var isOther = jq(select).val() === "other";
        form.find(".rhip-other-reason").toggle(isOther);
        form.find("input[name=otherReason]").prop("required", isOther);
    }

    function submitRhipInsurerBulk(form, count) {
        if (!confirm("Submit the review of " + count + " bill(s)? Bills with rejected items go back to the facility, the others are closed. The decisions can't be changed afterwards.")) {
            return false;
        }
        jq(form).find("button[type=submit]").prop("disabled", true).text("Submitting...");
        return true;
    }

    function submitRhipBulkSend(form, count) {
        if (!confirm("Send " + count + " bill(s) to the insurer? The facility's decisions on their items can't be changed afterwards.")) {
            return false;
        }
        jq(form).find("button[type=submit]").prop("disabled", true).text("Sending...");
        return true;
    }

    function submitRhipVerification(form) {
        var label = jq(form).find("button[type=submit]").text();
        if (!confirm(jq.trim(label) + "? Decisions made at this step can't be changed afterwards.")) {
            return false;
        }
        jq(form).find("button[type=submit]").prop("disabled", true).text("Submitting...");
        return true;
    }

    // Read-only view of an encounter of the patient journey
    (function() {
        var lastFocus = null;

        function esc(value) {
            return String(value == null ? "" : value).replace(/[&<>"']/g, function(c) {
                return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
            });
        }

        function formatDate(value) {
            if (value == null || value === "") {
                return "";
            }
            var date = new Date(value);
            if (isNaN(date.getTime())) {
                return esc(value);
            }
            return date.toLocaleDateString("en-GB", { day: "2-digit", month: "short", year: "numeric" })
                + " " + date.toTimeString().slice(0, 5);
        }

        function formatNumber(value) {
            if (value == null || value === "") {
                return "";
            }
            var number = Number(value);
            return isNaN(number) ? esc(value) : number.toLocaleString("en-US");
        }

        function renderObs(list) {
            var html = "";
            jq.each(list || [], function(i, o) {
                if (o.members) {
                    html += '<div class="rhip-obs-group">' + esc(o.concept) + '</div>' + renderObs(o.members);
                    return;
                }
                var value = esc(o.value) + (o.units ? " " + esc(o.units) : "");
                if (o.flag) {
                    value += '<span class="rhip-flag">' + (o.flag === "HIGH" ? "High" : "Low") + '</span>';
                }
                if (o.range) {
                    value += ' <span class="rhip-muted">(' + esc(o.range) + ')</span>';
                }
                if (o.comment) {
                    value += '<div class="rhip-muted">' + esc(o.comment) + '</div>';
                }
                html += '<dt>' + esc(o.concept) + '</dt><dd>' + value + '</dd>';
            });
            return html;
        }

        // Lab tests show how far each test got, never its results
        function renderLabTests(tests) {
            var html = '<h5>Lab tests</h5>';
            if (!tests || !tests.length) {
                return html + '<div class="rhip-muted">No lab test is linked to this encounter.</div>';
            }
            html += '<p class="rhip-muted" style="margin:0 0 8px;">Results are not shown. Each test shows whether it was done, when and by whom.</p>';
            html += '<table class="rhip-mini-table rhip-lab-table"><thead><tr><th>Test</th><th>Ordered</th><th>Specimen recorded</th><th>Results entered</th><th>Status</th></tr></thead><tbody>';
            jq.each(tests, function(i, t) {
                var chip = t.status === "COMPLETED" ? "ACCEPTED" : (t.status === "NOT_PERFORMED" || t.status === "CANCELLED" ? "REJECTED" : "PENDING");
                // The order status can be ahead of what was found in the records: say so rather than contradict the status
                var specimenMissing = t.status === "ORDERED" || t.status === "CANCELLED" ? "No specimen"
                    : (t.status === "NOT_PERFORMED" ? "Not recorded" : "Recorded, time not found");
                var resultsMissing = t.status === "COMPLETED" ? "Entered, time not found"
                    : (t.status === "NOT_PERFORMED" ? "Not performed" : "Not yet");
                html += '<tr>'
                    + '<td><strong>' + esc(t.testName) + '</strong>'
                    + (t.orderNumber ? '<div class="rhip-muted">' + esc(t.orderNumber) + '</div>' : '')
                    + (t.urgency === "STAT" ? '<div class="rhip-flag" style="margin:0;">Urgent</div>' : '') + '</td>'
                    + '<td>' + step(t.orderedAt, t.orderedBy) + '</td>'
                    + '<td>' + step(t.specimenRecordedAt, t.specimenRecordedBy, specimenMissing) + '</td>'
                    + '<td>' + step(t.resultsEnteredAt, t.resultsEnteredBy, resultsMissing) + '</td>'
                    + '<td><span class="rhip-chip rhip-chip-' + chip + '">' + esc(t.statusLabel) + '</span>'
                    + (t.notPerformedReason ? '<div class="rhip-muted">' + esc(t.notPerformedReason) + '</div>' : '') + '</td>'
                    + '</tr>';
            });
            return html + '</tbody></table>';
        }

        function step(date, by, missing) {
            if (!date) {
                return '<span class="rhip-muted">' + esc(missing || "") + '</span>';
            }
            return formatDate(date) + (by ? '<div class="rhip-muted">by ' + esc(by) + '</div>' : '');
        }

        function orderStatus(o) {
            if (o.fulfillerStatus) {
                return o.fulfillerStatus.charAt(0) + o.fulfillerStatus.slice(1).toLowerCase().replace(/_/g, " ");
            }
            if (o.action === "DISCONTINUE") {
                return "Discontinued";
            }
            return o.active ? "Active" : (o.dateStopped ? "Stopped " + formatDate(o.dateStopped) : "Ordered");
        }

        function render(data) {
            var head = '<button type="button" class="rhip-drawer-close" aria-label="Close">&times;</button>'
                + '<span class="rhip-readonly">View only</span>'
                + (data.voided ? ' <span class="rhip-chip rhip-chip-FAILED">Voided</span>' : '')
                + '<h3 id="rhip-drawer-title">' + esc(data.encounterType) + (data.form ? ' &middot; ' + esc(data.form) : '') + '</h3>'
                + '<div class="rhip-drawer-meta"><span>' + formatDate(data.encounterDatetime) + '</span>'
                + '<span>' + esc(data.location) + '</span>'
                + (data.providers && data.providers.length ? '<span>Provider: ' + esc(data.providers.join(", ")) + '</span>' : '')
                + '<span>Entered by ' + esc(data.creator) + '</span></div>';

            var body = '';
            if (data.labEncounter) {
                body += renderLabTests(data.labTests);
            }
            else {
                body += '<h5>Recorded</h5>';
                body += data.obs && data.obs.length ? '<dl class="rhip-obs">' + renderObs(data.obs) + '</dl>'
                    : '<div class="rhip-muted">Nothing was recorded on this encounter.</div>';
            }

            if (!data.labEncounter || (data.orders && data.orders.length)) {
                body += '<h5>' + (data.labEncounter ? 'Other orders' : 'Ordered') + '</h5>';
            }
            if (data.labEncounter && !(data.orders && data.orders.length)) {
                // Lab encounter without other orders: nothing more to list
            }
            else if (data.orders && data.orders.length) {
                body += '<table class="rhip-mini-table"><thead><tr><th>Order</th><th>Details</th><th>Status</th></tr></thead><tbody>';
                jq.each(data.orders, function(i, o) {
                    var details = [o.dose, o.route, o.frequency, o.duration ? "for " + o.duration : "", o.quantity ? "qty " + o.quantity : "", o.instructions]
                        .filter(function(part) { return part; }).map(esc).join(" &middot; ");
                    body += '<tr><td><strong>' + esc(o.name) + '</strong><div class="rhip-muted">' + esc(o.orderType) + '</div></td>'
                        + '<td>' + details + '</td><td>' + esc(orderStatus(o)) + '</td></tr>';
                });
                body += '</tbody></table>';
            }
            else {
                body += '<div class="rhip-muted">Nothing was ordered on this encounter.</div>';
            }

            body += '<h5>Billed from these orders</h5>';
            if (data.billItems && data.billItems.length) {
                var verified = data.verified !== false;
                body += '<table class="rhip-mini-table"><thead><tr><th>Item</th><th class="rhip-number">Qty</th><th class="rhip-number">Amount</th>'
                    + (verified ? '<th>Verification</th>' : '') + '</tr></thead><tbody>';
                jq.each(data.billItems, function(i, item) {
                    var outcome = item.status === "INSURER_ACCEPTED" || item.status === "FINAL_ACCEPTED" ? "ACCEPTED"
                        : (item.status === "FACILITY_REJECTED" || item.status === "REJECTION_ACCEPTED" || item.status === "FINAL_REJECTED" ? "REJECTED" : "PENDING");
                    body += '<tr><td>' + esc(item.name) + '</td><td class="rhip-number">' + formatNumber(item.quantity) + '</td>'
                        + '<td class="rhip-number">' + formatNumber(item.amount) + '</td>'
                        + (verified ? '<td><span class="rhip-chip rhip-chip-' + outcome + '">' + esc(item.statusLabel) + '</span>'
                        + (item.reason ? '<div class="rhip-muted">' + esc(item.reason) + '</div>' : '') + '</td>' : '') + '</tr>';
                });
                body += '</tbody></table>';
            }
            else {
                body += '<div class="rhip-muted">No bill items were made from the orders of this encounter.</div>';
            }
            body += '<p class="rhip-muted" style="margin-top:14px;">' + (data.verified !== false
                ? 'Accept or reject items from the billing details of the bill. ' : '') + 'This panel shows the clinical record only.</p>';
            return { head: head, body: body };
        }

        function open(globalBillId, encounterId) {
            lastFocus = document.activeElement;
            var drawer = jq("#rhip-drawer");
            drawer.find(".rhip-drawer-head").html('<button type="button" class="rhip-drawer-close" aria-label="Close">&times;</button><h3 id="rhip-drawer-title">Loading...</h3>');
            drawer.find(".rhip-drawer-body").html("");
            drawer.addClass("open");
            jq("#rhip-drawer-overlay").addClass("open");
            drawer.find(".rhip-drawer-close").focus();
            var url = (typeof openmrsContextPath !== "undefined" ? openmrsContextPath : "${ openmrsContextPath }")
                + "/ws/rest/v1/rwandaemr/billVerification/encounter?globalBillId=" + encodeURIComponent(globalBillId)
                + "&encounterId=" + encodeURIComponent(encounterId);
            jq.ajax({ url: url, dataType: "json", cache: false })
                .done(function(data) {
                    if (typeof data === "string") {
                        try { data = JSON.parse(data); } catch (e) { data = null; }
                    }
                    // An error can come back as a normal response: show its message instead of an empty encounter
                    if (!data || data.encounterId == null) {
                        if (window.console) {
                            console.warn("Unexpected response for encounter " + encounterId, data);
                        }
                        showError(data && data.message ? data.message : "The encounter could not be loaded.");
                        return;
                    }
                    var html = render(data);
                    drawer.find(".rhip-drawer-head").html(html.head);
                    drawer.find(".rhip-drawer-body").html(html.body);
                    drawer.find(".rhip-drawer-close").focus();
                })
                .fail(function(xhr) {
                    var json = xhr.responseJSON;
                    if (!json && xhr.responseText) {
                        try { json = JSON.parse(xhr.responseText); } catch (e) { json = null; }
                    }
                    showError(json && json.message ? json.message : "The encounter could not be loaded (error " + xhr.status + ").");
                });
        }

        function showError(message) {
            var drawer = jq("#rhip-drawer");
            drawer.find(".rhip-drawer-head").html('<button type="button" class="rhip-drawer-close" aria-label="Close">&times;</button><h3 id="rhip-drawer-title">Encounter not available</h3>');
            drawer.find(".rhip-drawer-body").html('<div class="rhip-muted">' + esc(message) + '</div>');
            drawer.find(".rhip-drawer-close").focus();
        }

        // The verification history of a bill, rendered with the page and shown in the same side panel
        function openHistory(globalBillId) {
            lastFocus = document.activeElement;
            var drawer = jq("#rhip-drawer");
            var source = jq("#rhip-history-" + globalBillId);
            drawer.find(".rhip-drawer-head").html('<button type="button" class="rhip-drawer-close" aria-label="Close">&times;</button>'
                + '<span class="rhip-readonly">Audit trail</span>'
                + '<h3 id="rhip-drawer-title">Verification history</h3>'
                + '<div class="rhip-drawer-meta"><span>' + esc(source.data("billLabel")) + '</span></div>');
            var body = drawer.find(".rhip-drawer-body");
            body.html('<div class="rhip-muted">Loading...</div>');
            drawer.addClass("open");
            jq("#rhip-drawer-overlay").addClass("open");
            drawer.find(".rhip-drawer-close").focus();
            jq.ajax({
                url: "${ openmrsContextPath }/ws/rest/v1/rwandaemr/billVerification/history",
                data: { globalBillId: globalBillId },
                dataType: "json"
            }).done(function(data) {
                body.html(renderHistory(data && data.events));
            }).fail(function(xhr) {
                body.html('<div class="rhip-muted">' + esc(rhipErrorMessage(xhr, "The verification history could not be loaded.")) + '</div>');
            });
        }
        window.openRhipHistory = openHistory;

        function renderHistory(events) {
            if (!events || !events.length) {
                return '<div class="rhip-muted">No item of this bill has been verified yet.</div>';
            }
            var html = '<ul class="rhip-audit">';
            jq.each(events, function(i, event) {
                var what;
                if (event.itemId) {
                    var name = jq.trim(jq("#rhip-item-" + event.itemId + " .rhip-item-name").first().text()) || ("Item " + event.itemId);
                    what = esc(name) + ": " + esc(event.itemStatus)
                        + (event.reason ? " (" + esc(event.reason) + ")" : "")
                        + (event.note ? ", " + esc(event.note) : "");
                }
                else if (event.stage === "CLOSED") {
                    what = "Verification closed";
                }
                else {
                    what = "Sent for the " + esc(String(event.stageLabel || "").toLowerCase());
                }
                html += '<li><span class="rhip-muted">' + formatDate(event.date) + '</span>'
                    + '<span><strong>' + esc(event.username) + '</strong> &middot; ' + what + '</span></li>';
            });
            return html + '</ul>';
        }

        // The patient journey of a bill is read when the bill is opened
        function loadJourney(globalBillId) {
            var box = jq("#rhip-journey-" + globalBillId);
            if (!box.length || box.data("state") === "loading" || box.data("state") === "loaded") {
                return;
            }
            box.data("state", "loading");
            jq.ajax({
                url: "${ openmrsContextPath }/ws/rest/v1/rwandaemr/billVerification/journey",
                data: { globalBillId: globalBillId },
                dataType: "json"
            }).done(function(data) {
                box.data("state", "loaded");
                box.html(renderJourney(globalBillId, data && data.rows));
            }).fail(function(xhr) {
                box.data("state", "");
                box.html('<div class="rhip-muted">' + esc(rhipErrorMessage(xhr, "The patient journey could not be loaded."))
                    + ' <button type="button" class="rhip-journey-retry">Try again</button></div>');
            });
        }
        window.loadRhipJourney = loadJourney;

        function journeyDate(value) {
            if (!value) {
                return "";
            }
            var date = new Date(value);
            if (isNaN(date.getTime())) {
                return esc(value);
            }
            var months = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
            return ("0" + date.getDate()).slice(-2) + "." + months[date.getMonth()] + "." + date.getFullYear();
        }

        function renderJourney(globalBillId, rows) {
            if (!rows || !rows.length) {
                return '<div class="rhip-muted">No patient journey logs found for this visit.</div>';
            }
            var html = '<table class="rhip-mini-table"><thead><tr><th>Action</th><th>Encounter</th><th>Form</th><th>Location</th>'
                + '<th>User</th><th>Encounter Date</th><th>Action Date</th><th></th></tr></thead><tbody>';
            jq.each(rows, function(i, row) {
                html += '<tr class="rhip-journey-row" tabindex="0" data-global-bill-id="' + esc(globalBillId) + '" data-encounter-id="' + esc(row.encounterId) + '" title="View this encounter">'
                    + '<td>' + esc(row.action) + '</td><td>' + esc(row.encounterType) + '</td><td>' + esc(row.form) + '</td>'
                    + '<td>' + esc(row.location) + '</td><td>' + esc(row.username) + '</td>'
                    + '<td>' + journeyDate(row.encounterDatetime) + '</td><td>' + journeyDate(row.actionDatetime) + '</td>'
                    + '<td class="rhip-view-link">View &rsaquo;</td></tr>';
            });
            return html + '</tbody></table>';
        }

        function close() {
            jq("#rhip-drawer, #rhip-drawer-overlay").removeClass("open");
            if (lastFocus) {
                lastFocus.focus();
            }
        }

        jq(function() {
            jq(document).on("click", ".rhip-journey-row", function() {
                open(jq(this).data("globalBillId"), jq(this).data("encounterId"));
            });
            jq(document).on("keydown", ".rhip-journey-row", function(e) {
                if (e.key === "Enter" || e.key === " ") {
                    e.preventDefault();
                    open(jq(this).data("globalBillId"), jq(this).data("encounterId"));
                }
            });
            jq(document).on("click", ".rhip-drawer-close, #rhip-drawer-overlay", close);
            jq(document).on("keydown", function(e) {
                if (e.key === "Escape" && jq("#rhip-drawer").hasClass("open")) {
                    close();
                }
            });
            jq(".rhip-reject-form select[name=reasonId]").each(function() {
                updateRhipOtherReason(this);
            });
        });
    })();

    /**
     * Before the Confirm button closes an MMI bill (which cannot be undone), shows how MMI would split it:
     * /voucher-closure with preview true, nothing billed. The bill is closed only from the dialog.
     */
    function previewRhipVoucherClosure(form) {
        var dialog = jq("#rhip-closure-dialog");
        var body = dialog.find(".rhip-closure-body");
        var closeButton = dialog.find(".rhip-closure-confirm");
        dialog.data("form", form);
        closeButton.prop("disabled", true);
        body.html('<p><i class="icon-spinner icon-spin"></i> Asking MMI how this bill would be split...</p>');
        dialog.prop("hidden", false);
        jq.ajax({
            url: "${ openmrsContextPath }/ws/rest/v1/rwandaemr/rhipVoucher/closurePreview",
            data: { globalBillId: jq(form).data("globalBillId") },
            dataType: "json"
        }).done(function (preview) {
            body.html(rhipClosurePreviewHtml(preview));
            closeButton.prop("disabled", !(preview && preview.success));
        }).fail(function () {
            body.html('<p class="rhip-closure-error">The MMI cost split could not be loaded. Try again later.</p>');
        });
        return false;
    }

    function rhipClosurePreviewHtml(preview) {
        function text(value) {
            return jq("<div>").text(value === null || value === undefined ? "" : String(value)).html();
        }
        function amount(value) {
            return value === null || value === undefined || value === "" ? "" : Number(value).toLocaleString() + " RWF";
        }
        function percent(value) {
            return value === null || value === undefined || value === "" ? "" : " (" + Number(value) + "%)";
        }
        if (!preview || !preview.success) {
            return '<p class="rhip-closure-error">' + text(preview && preview.message ? preview.message
                    : "MMI did not return a cost split.") + '</p>';
        }
        var html = '<p>Voucher <b>' + text(preview.voucherCode) + '</b>' + (preview.billStatus ? ', MMI bill status <b>'
                + text(preview.billStatus) + '</b>' : '') + '. Nothing has been billed yet.</p>';
        html += '<table class="rhip-closure-table"><tbody>'
                + '<tr><th>Total</th><td>' + amount(preview.totalAmount) + '</td></tr>'
                + '<tr><th>MMI pays</th><td>' + amount(preview.insuranceAmount) + percent(preview.insurancePercentage) + '</td></tr>'
                + '<tr><th>Patient pays</th><td>' + amount(preview.patientAmount) + percent(preview.patientPercentage) + '</td></tr>';
        if (preview.otherAmount && Number(preview.otherAmount) !== 0) {
            html += '<tr><th>Other insurer</th><td>' + amount(preview.otherAmount) + percent(preview.otherPercentage) + '</td></tr>';
        }
        html += '</tbody></table>';
        if (preview.lines && preview.lines.length) {
            html += '<table class="rhip-closure-table"><thead><tr><th>Code</th><th>Item</th><th>Amount</th><th>Price</th></tr></thead><tbody>';
            jq.each(preview.lines, function (i, line) {
                html += '<tr><td>' + text(line.code) + '</td><td>' + text(line.name)
                        + (line.fromApproval ? ' <span class="rhip-closure-tag">approved, added at closure</span>' : '')
                        + '</td><td>' + amount(line.lineTotal) + '</td><td>' + text(line.priceSource) + '</td></tr>';
            });
            html += '</tbody></table>';
        }
        if (preview.rejectedLineCount > 0) {
            html += '<p class="rhip-closure-warning">' + preview.rejectedLineCount + ' line(s) of this global bill were not '
                    + 'recorded by MMI and are not on this bill. Once it is closed they cannot be added: resend them from the '
                    + 'global bill first if they should be paid by MMI.</p>';
        }
        html += '<p class="rhip-closure-warning">Closing settles the bill at MMI. This cannot be undone.</p>';
        return html;
    }

    jq(function () {
        var dialog = jq("#rhip-closure-dialog");
        dialog.find(".rhip-closure-cancel").on("click", function () {
            dialog.prop("hidden", true);
        });
        dialog.find(".rhip-closure-confirm").on("click", function () {
            var form = dialog.data("form");
            if (!form) {
                return;
            }
            jq(this).prop("disabled", true);
            dialog.prop("hidden", true);
            jq(form).find("button[type=submit]")
                .prop("disabled", true)
                .attr("title", "Processing...")
                .attr("aria-label", "Processing...")
                .find("i")
                .attr("class", "icon-spinner icon-spin");
            form.submit();
        });
    });

    function submitRhipVoucher(form) {
        var action = jq(form).find("input[name=action]").val();
        var prompt = action === "confirm"
            ? "Are you sure you want to confirm and close this RHIP voucher?"
            : "Are you sure you want to send the RHIP voucher for this global bill?";
        if (!confirm(prompt)) {
            return false;
        }
        jq(form).find("input[type=submit]").prop("disabled", true).val("Processing...");
        jq(form).find("button[type=submit]")
            .prop("disabled", true)
            .attr("title", "Processing...")
            .attr("aria-label", "Processing...")
            .find("i")
            .attr("class", "icon-spinner icon-spin");
        return true;
    }
</script>

<div class="rhip-page">
    <div class="rhip-page-header">
        <div>
            <h1 class="rhip-page-title">RHIP Voucher Submission</h1>
        </div>
        <div class="rhip-count-card">
            <strong>${ totalCount }</strong>
            <span class="rhip-muted">matching bills</span>
        </div>
    </div>

    <form method="get" action="${ ui.pageLink("rwandaemr", "admin/rhipVoucherSubmissions") }" class="rhip-filter-panel">
        <div class="rhip-filter-grid">
            <div class="rhip-field">
                <label for="dateType">Period by</label>
                <select id="dateType" name="dateType" title="The bill report and the insurance summary use the admission date">
                    <option value="DISCHARGE" ${ dateType != "ADMISSION" ? "selected" : "" }>Discharge date</option>
                    <option value="ADMISSION" ${ dateType == "ADMISSION" ? "selected" : "" }>Admission date</option>
                </select>
            </div>
            <div class="rhip-field">
                <label for="dischargeStartDate">From</label>
                <input id="dischargeStartDate" type="date" name="dischargeStartDate" value="${ dischargeStartDate }"/>
            </div>
            <div class="rhip-field">
                <label for="dischargeEndDate">To</label>
                <input id="dischargeEndDate" type="date" name="dischargeEndDate" value="${ dischargeEndDate }"/>
            </div>
            <div class="rhip-field">
                <label for="status">Submission Status</label>
                <select id="status" name="status">
                    <option value="NOT_SENT" ${ status == "NOT_SENT" ? "selected" : "" }>Not Sent</option>
                    <option value="FAILED" ${ status == "FAILED" ? "selected" : "" }>Failed</option>
                    <option value="SENT" ${ status == "SENT" ? "selected" : "" }>Sent</option>
                    <option value="CONFIRMED" ${ status == "CONFIRMED" ? "selected" : "" }>Confirmed</option>
                    <option value="ALL" ${ status == "ALL" ? "selected" : "" }>All</option>
                </select>
            </div>
            <div class="rhip-field">
                <label for="insuranceId">Insurance</label>
                <% if (insuranceLocked) { %>
                    <input type="hidden" name="insuranceId" value="${ insuranceId }"/>
                <% } %>
                <select id="insuranceId" ${ insuranceLocked ? 'disabled="disabled" title="You can only see the bills of the insurance you verify for"' : 'name="insuranceId"' }>
                    <% if (!insuranceLimited) { %>
                        <option value="" ${ !insuranceId ? "selected" : "" }>All</option>
                    <% } %>
                    <% insuranceOptions.each { option -> %>
                        <option value="${ ui.encodeHtmlAttribute(option.value) }" ${ insuranceId == option.value ? "selected" : "" }>${ ui.encodeHtmlContent(option.label) }</option>
                    <% } %>
                </select>
            </div>
            <div class="rhip-field">
                <label for="sortBy">Sort By</label>
                <select id="sortBy" name="sortBy">
                    <option value="dischargeDate" ${ sortBy == "dischargeDate" ? "selected" : "" }>Discharge date</option>
                    <option value="patientName" ${ sortBy == "patientName" ? "selected" : "" }>Patient name</option>
                    <option value="globalBillNumber" ${ sortBy == "globalBillNumber" ? "selected" : "" }>Global bill number</option>
                    <option value="totalAmount" ${ sortBy == "totalAmount" ? "selected" : "" }>Total amount</option>
                    <option value="status" ${ sortBy == "status" ? "selected" : "" }>RHIP status</option>
                </select>
            </div>
            <div class="rhip-field">
                <label for="query">Search</label>
                <input id="query" type="search" name="query" value="${ ui.encodeHtmlAttribute(query) }" placeholder="Bill number, patient, identifier, card number"/>
            </div>
            <div class="rhip-field">
                <label for="sortDirection">Direction</label>
                <select id="sortDirection" name="sortDirection">
                    <option value="desc" ${ sortDirection == "desc" ? "selected" : "" }>Descending</option>
                    <option value="asc" ${ sortDirection == "asc" ? "selected" : "" }>Ascending</option>
                </select>
            </div>
            <div class="rhip-field">
                <label for="pageSize">Page Size</label>
                <select id="pageSize" name="pageSize">
                    <option value="25" ${ pageSize == 25 ? "selected" : "" }>25</option>
                    <option value="50" ${ pageSize == 50 ? "selected" : "" }>50</option>
                    <option value="100" ${ pageSize == 100 ? "selected" : "" }>100</option>
                </select>
            </div>
        </div>
        <div class="rhip-filter-actions" style="margin-top: 12px;">
            <input type="submit" value="Apply Filters"/>
            <a class="button" href="${ ui.pageLink("rwandaemr", "admin/rhipVoucherSubmissions") }">Reset</a>
        </div>
    </form>

    <% if (insurerBulk && (insurerBulk.ready > 0 || insurerBulk.notReady > 0)) { %>
    <div class="rhip-bulk-send">
        <div>
            <strong>${ insurerBulk.ready } ${ insurerBulk.ready == 1 ? "review is" : "reviews are" } ready to submit</strong>
            <span class="rhip-muted">
                for the bills waiting for the insurer, ${ dateType == "ADMISSION" ? "admitted" : "discharged" } from ${ dischargeStartDate } to ${ dischargeEndDate },
                that match the filters. Bills with rejected items go back to the facility, the others are closed.
                <% if (insurerBulk.notReady > 0) { %>
                    ${ insurerBulk.notReady } ${ insurerBulk.notReady == 1 ? "bill still has" : "bills still have" } items to decide and will wait.
                <% } %>
            </span>
        </div>
        <form method="post" action="${ ui.pageLink("rwandaemr", "admin/rhipVoucherSubmissions") }"
              onsubmit="return submitRhipInsurerBulk(this, ${ insurerBulk.ready });">
            <input type="hidden" name="action" value="submitAllInsurerReviews"/>
            <input type="hidden" name="dischargeStartDate" value="${ dischargeStartDate }"/>
            <input type="hidden" name="dischargeEndDate" value="${ dischargeEndDate }"/>
            <input type="hidden" name="dateType" value="${ dateType }"/>
            <input type="hidden" name="insuranceId" value="${ insuranceId }"/>
            <input type="hidden" name="status" value="${ status }"/>
            <input type="hidden" name="query" value="${ ui.encodeHtmlAttribute(query) }"/>
            <input type="hidden" name="sortBy" value="${ sortBy }"/>
            <input type="hidden" name="sortDirection" value="${ sortDirection }"/>
            <input type="hidden" name="page" value="${ page }"/>
            <input type="hidden" name="pageSize" value="${ pageSize }"/>
            <button type="submit" class="rhip-btn-submit" ${ insurerBulk.ready == 0 ? 'disabled="disabled" title="No bill waiting for the insurer has every item decided"' : '' }>
                Submit ${ insurerBulk.ready } ${ insurerBulk.ready == 1 ? "review" : "reviews" }
            </button>
        </form>
    </div>
    <% } %>
    <% if (bulkSend && (bulkSend.ready > 0 || bulkSend.notReady > 0)) { %>
    <div class="rhip-bulk-send">
        <div>
            <strong>${ bulkSend.ready } ${ bulkSend.ready == 1 ? "bill is" : "bills are" } ready to send to the insurer</strong>
            <span class="rhip-muted">
                for the bills ${ dateType == "ADMISSION" ? "admitted" : "discharged" } from ${ dischargeStartDate } to ${ dischargeEndDate } that match the filters.
                <% if (bulkSend.notReady > 0) { %>
                    ${ bulkSend.notReady } ${ bulkSend.notReady == 1 ? "bill still has" : "bills still have" } items to decide and will stay at the facility review.
                <% } %>
            </span>
        </div>
        <form method="post" action="${ ui.pageLink("rwandaemr", "admin/rhipVoucherSubmissions") }"
              onsubmit="return submitRhipBulkSend(this, ${ bulkSend.ready });">
            <input type="hidden" name="action" value="sendAllToInsurer"/>
            <input type="hidden" name="dischargeStartDate" value="${ dischargeStartDate }"/>
            <input type="hidden" name="dischargeEndDate" value="${ dischargeEndDate }"/>
            <input type="hidden" name="dateType" value="${ dateType }"/>
            <input type="hidden" name="insuranceId" value="${ insuranceId }"/>
            <input type="hidden" name="status" value="${ status }"/>
            <input type="hidden" name="query" value="${ ui.encodeHtmlAttribute(query) }"/>
            <input type="hidden" name="sortBy" value="${ sortBy }"/>
            <input type="hidden" name="sortDirection" value="${ sortDirection }"/>
            <input type="hidden" name="page" value="${ page }"/>
            <input type="hidden" name="pageSize" value="${ pageSize }"/>
            <button type="submit" class="rhip-btn-submit" ${ bulkSend.ready == 0 ? 'disabled="disabled" title="No bill in this period has every item decided"' : '' }>
                Send ${ bulkSend.ready } ${ bulkSend.ready == 1 ? "bill" : "bills" } to insurer
            </button>
        </form>
    </div>
    <% } %>

    <div class="rhip-pagination">
        <% if (hasPreviousPage) { %>
            <a class="button" href="${ ui.pageLink("rwandaemr", "admin/rhipVoucherSubmissions") }?page=${ previousPage }&${ filterQueryString }">Previous</a>
        <% } else { %>
            <span class="rhip-muted">Previous</span>
        <% } %>
        <strong>Page ${ page } of ${ totalPages }</strong>
        <% if (hasNextPage) { %>
            <a class="button" href="${ ui.pageLink("rwandaemr", "admin/rhipVoucherSubmissions") }?page=${ nextPage }&${ filterQueryString }">Next</a>
        <% } else { %>
            <span class="rhip-muted">Next</span>
        <% } %>
    </div>

    <div class="rhip-table-wrap">
        <% if (!rows) { %>
            <div class="rhip-empty">No global bills match the selected filters.</div>
        <% } else { %>
            <table class="rhip-table">
                <thead>
                <tr>
                    <th>Global Bill</th>
                    <th>Patient</th>
                    <th>Insurance</th>
                    <th>Card Number</th>
                    <th>Transfer</th>
                    <th>Admission</th>
                    <th>Discharge</th>
                    <th>Total</th>
                    <th>Status</th>
                    <th>Verification</th>
                    <th>Payment</th>
                    <th>Last Attempt</th>
                    <th>Actions</th>
                </tr>
                </thead>
                <tbody>
                <% rows.each { row ->
                    def globalBill = row.globalBill
                    def globalBillId = globalBill.globalBillId
                    def consommations = consommationsByGlobalBillId[globalBillId] ?: []
                    def verification = verificationSummaries[globalBillId]
                    def stage = verification.stage
                    def canActOnBill = verificationCanAct[globalBillId]
                    // Only the bills of the insurances in rwandaemr.billVerification.insurances are verified
                    def verificationApplied = verificationApplies[globalBillId]
                    // The transfers of the visit, for the bills that are verified only
                    def visitTransfers = transfersByGlobalBillId[globalBillId]
                    def stageReasons = stage.name() == "FACILITY_RESPONSE" ? disputeReasons : rejectionReasons
                    def stageReasonType = stage.name() == "FACILITY_RESPONSE" ? "DISPUTE" : "REJECTION"
                    def itemNames = [:]
                    def hiddenFilters = """
                        <input type="hidden" name="globalBillId" value="${ globalBillId }"/>
                        <input type="hidden" name="dischargeStartDate" value="${ dischargeStartDate }"/>
                        <input type="hidden" name="dischargeEndDate" value="${ dischargeEndDate }"/>
                        <input type="hidden" name="dateType" value="${ dateType }"/>
                        <input type="hidden" name="insuranceId" value="${ insuranceId }"/>
                        <input type="hidden" name="status" value="${ status }"/>
                        <input type="hidden" name="query" value="${ ui.encodeHtmlAttribute(query) }"/>
                        <input type="hidden" name="sortBy" value="${ sortBy }"/>
                        <input type="hidden" name="sortDirection" value="${ sortDirection }"/>
                        <input type="hidden" name="page" value="${ page }"/>
                        <input type="hidden" name="pageSize" value="${ pageSize }"/>
                    """
                %>
                    <tr class="rhip-row" id="rhip-bill-${ globalBillId }" onclick="toggleRhipDetails('${ globalBillId }')">
                        <td>
                            <strong>${ ui.format(globalBill.billIdentifier) }</strong>
                            <div class="rhip-muted">ID ${ globalBillId }</div>
                        </td>
                        <td>
                            <strong>${ ui.format(row.patientName) }</strong>
                            <div class="rhip-muted">${ ui.format(row.patientIdentifier) }</div>
                        </td>
                        <td>${ ui.format(row.insuranceName) }</td>
                        <td style="white-space: nowrap;">${ ui.format(row.insuranceCardNumber) }</td>
                        <td>
                            <% if (visitTransfers != null) { %>
                                <% if (visitTransfers.received) { %>
                                    <span class="rhip-transfer-tag rhip-transfer-received"><i class="icon-signin" aria-hidden="true"></i> Received</span>
                                    <% if (visitTransfers.sent) { %><div class="rhip-muted">+${ visitTransfers.sent.size() } sent</div><% } %>
                                <% } else if (visitTransfers.sent) { %>
                                    <span class="rhip-transfer-tag rhip-transfer-sent"><i class="icon-signout" aria-hidden="true"></i> ${ visitTransfers.sent.size() } sent</span>
                                <% } else { %>
                                    <span class="rhip-transfer-tag rhip-transfer-none"><i class="icon-warning-sign" aria-hidden="true"></i> No transfer</span>
                                <% } %>
                            <% } %>
                        </td>
                        <td>${ ui.formatDatePretty(globalBill.admission?.admissionDate) }</td>
                        <td>${ ui.formatDatePretty(globalBill.closingDate) }</td>
                        <td class="rhip-number">${ ui.format(globalBill.globalAmount) }</td>
                        <td>
                            <span class="rhip-chip rhip-chip-${ row.effectiveStatus }">${ row.displayStatus }</span>
                        </td>
                        <td id="rhip-verify-cell-${ globalBillId }">
                            <% if (!verificationApplied) { %>
                                <span class="rhip-muted">Not verified</span>
                            <% } else { %>
                            <span class="rhip-chip rhip-chip-${ verification.chipClass }">${ stage.label }</span>
                            <div class="rhip-muted">
                                <% if (stage.name() == "CLOSED") { %>
                                    ${ verification.rejectedCount } of ${ verification.itemCount } rejected
                                <% } else if (verification.awaitingCount > 0) { %>
                                    ${ verification.awaitingCount } to decide
                                <% } else { %>
                                    Ready to submit
                                <% } %>
                            </div>
                            <% } %>
                        </td>
                        <td>
                            <span class="rhip-chip rhip-chip-${ row.paymentStatusClass }">${ row.paymentStatusLabel }</span>
                            <div class="rhip-muted">${ row.patientBillCount } bill(s)</div>
                        </td>
                        <td>${ row.lastSubmissionDate ? ui.formatDatePretty(row.lastSubmissionDate) : "" }</td>
                        <td class="rhip-actions" onclick="event.stopPropagation();">
                            <div class="rhip-action-group">
                            <% if (row.effectiveStatus == "NOT_SENT" && canSend) { %>
                                <form method="post" action="${ ui.pageLink("rwandaemr", "admin/rhipVoucherSubmissions") }" onsubmit="return submitRhipVoucher(this);">
                                    <input type="hidden" name="action" value="send"/>
                                    <input type="hidden" name="globalBillId" value="${ globalBillId }"/>
                                    <input type="hidden" name="dischargeStartDate" value="${ dischargeStartDate }"/>
                                    <input type="hidden" name="dischargeEndDate" value="${ dischargeEndDate }"/>
                                    <input type="hidden" name="dateType" value="${ dateType }"/>
                                    <input type="hidden" name="insuranceId" value="${ insuranceId }"/>
                                    <input type="hidden" name="status" value="${ status }"/>
                                    <input type="hidden" name="query" value="${ ui.encodeHtmlAttribute(query) }"/>
                                    <input type="hidden" name="sortBy" value="${ sortBy }"/>
                                    <input type="hidden" name="sortDirection" value="${ sortDirection }"/>
                                    <input type="hidden" name="page" value="${ page }"/>
                                    <input type="hidden" name="pageSize" value="${ pageSize }"/>
                                    <button class="primary" type="submit" title="Send RHIP voucher" aria-label="Send RHIP voucher">
                                        <i class="icon-share-alt"></i>
                                    </button>
                                </form>
                            <% } %>
                            <% if (row.effectiveStatus == "SENT" && row.mmiInsurance && canConfirm) { %>
                                <form method="post" action="${ ui.pageLink("rwandaemr", "admin/rhipVoucherSubmissions") }" onsubmit="return previewRhipVoucherClosure(this);"
                                      data-global-bill-id="${ globalBillId }">
                                    <input type="hidden" name="action" value="confirm"/>
                                    <input type="hidden" name="globalBillId" value="${ globalBillId }"/>
                                    <input type="hidden" name="dischargeStartDate" value="${ dischargeStartDate }"/>
                                    <input type="hidden" name="dischargeEndDate" value="${ dischargeEndDate }"/>
                                    <input type="hidden" name="dateType" value="${ dateType }"/>
                                    <input type="hidden" name="insuranceId" value="${ insuranceId }"/>
                                    <input type="hidden" name="status" value="${ status }"/>
                                    <input type="hidden" name="query" value="${ ui.encodeHtmlAttribute(query) }"/>
                                    <input type="hidden" name="sortBy" value="${ sortBy }"/>
                                    <input type="hidden" name="sortDirection" value="${ sortDirection }"/>
                                    <input type="hidden" name="page" value="${ page }"/>
                                    <input type="hidden" name="pageSize" value="${ pageSize }"/>
                                    <button class="confirm" type="submit" title="Confirm RHIP voucher" aria-label="Confirm RHIP voucher">
                                        <i class="icon-ok"></i>
                                    </button>
                                </form>
                            <% } %>
                            <% if (row.effectiveStatus == "FAILED" && canRetry) { %>
                                <form method="post" action="${ ui.pageLink("rwandaemr", "admin/rhipVoucherSubmissions") }" onsubmit="return submitRhipVoucher(this);">
                                    <input type="hidden" name="action" value="retry"/>
                                    <input type="hidden" name="globalBillId" value="${ globalBillId }"/>
                                    <input type="hidden" name="dischargeStartDate" value="${ dischargeStartDate }"/>
                                    <input type="hidden" name="dischargeEndDate" value="${ dischargeEndDate }"/>
                                    <input type="hidden" name="dateType" value="${ dateType }"/>
                                    <input type="hidden" name="insuranceId" value="${ insuranceId }"/>
                                    <input type="hidden" name="status" value="${ status }"/>
                                    <input type="hidden" name="query" value="${ ui.encodeHtmlAttribute(query) }"/>
                                    <input type="hidden" name="sortBy" value="${ sortBy }"/>
                                    <input type="hidden" name="sortDirection" value="${ sortDirection }"/>
                                    <input type="hidden" name="page" value="${ page }"/>
                                    <input type="hidden" name="pageSize" value="${ pageSize }"/>
                                    <button class="retry" type="submit" title="Retry RHIP voucher" aria-label="Retry RHIP voucher">
                                        <i class="icon-repeat"></i>
                                    </button>
                                </form>
                            <% } %>
                            <% if (!insurerOnly) { %>
                            <a class="rhip-open-link" href="${ openmrsContextPath }/module/mohbilling/viewGlobalBill.form?globalBillId=${ globalBillId }" title="Open global bill" aria-label="Open global bill">
                                <i class="icon-external-link"></i>
                            </a>
                            <% } %>
                            </div>
                        </td>
                    </tr>
                    <tr id="rhip-detail-${ globalBillId }" class="rhip-detail-row" ${ openGlobalBillId == globalBillId ? 'style="display: table-row;"' : '' }>
                        <td colspan="13">
                            <div class="rhip-detail-grid">
                                <div class="rhip-detail-card">
                                    <h4>Global Bill Summary</h4>
                                    <div class="rhip-kv">
                                        <label>Patient</label><span>${ ui.format(row.patientName) } (${ ui.format(row.patientIdentifier) })</span>
                                        <label>Admission</label><span>${ ui.formatDatePretty(globalBill.admission?.admissionDate) }</span>
                                        <label>Discharge</label><span>${ ui.formatDatePretty(globalBill.closingDate) }</span>
                                        <label>Insurance</label><span>${ ui.format(row.insuranceName) } / ${ ui.format(row.insuranceCardNumber) }</span>
                                        <% if (row.voucherReference) { %>
                                            <label>Voucher reference</label><span>${ ui.format(row.voucherReference) }</span>
                                        <% } %>
                                        <label>Total amount</label><span>${ ui.format(globalBill.globalAmount) }</span>
                                        <label>Patient contribution</label><span>${ ui.format(row.patientContribution) }</span>
                                        <label>Insurance contribution</label><span>${ ui.format(row.insuranceContribution) }</span>
                                        <label>Diagnoses</label><span>${ ui.format(globalBill.admission?.diseaseType) }</span>
                                    </div>
                                </div>

                                <div class="rhip-detail-card">
                                    <h4>Patient Journey Logs</h4>
                                    <% def journeyScope = journeyScopeByGlobalBillId[globalBillId] %>
                                    <div class="rhip-muted" style="margin:-4px 0 8px;">
                                        <% if (journeyScope?.visitId) { %>
                                            Encounters of the visit of this bill, as in the bill report:
                                            ${ journeyScope.start ? ui.formatDatetimePretty(journeyScope.start) : "" } to
                                            ${ journeyScope.end ? ui.formatDatetimePretty(journeyScope.end) : "now" }
                                        <% } else { %>
                                            This bill is not linked to a visit: showing encounters from the day of admission
                                            to the day the bill was closed.
                                        <% } %>
                                    </div>
                                    <% if (!canViewHistory) { %>
                                        <div class="rhip-muted">You do not have permission to view patient journey logs.</div>
                                    <% } else { %>
                                        <div id="rhip-journey-${ globalBillId }" class="rhip-journey" data-global-bill-id="${ globalBillId }">
                                            <div class="rhip-muted">Loading the patient journey...</div>
                                        </div>
                                    <% } %>
                                </div>
                            </div>

                            <div class="rhip-detail-card" style="margin-top: 14px;">
                                <div class="rhip-verification-head">
                                    <h4>Billable Services, Drugs and Consumables</h4>
                                    <div class="rhip-verification-actions">
                                        <% if (visitTransfers != null) {
                                            def transferPatientId = globalBill.admission?.insurancePolicy?.owner?.patientId
                                            def transferNoAccess = canPreviewTransfer ? '' : 'disabled="disabled" title="Viewing transfers needs the privilege Task: transferapp.listTransfers"'
                                        %>
                                            <% if (visitTransfers.empty) { %>
                                                <span class="rhip-transfer-tag rhip-transfer-none" title="No Transfer ID at registration, and none sent from this facility during the visit"><i class="icon-warning-sign" aria-hidden="true"></i> No transfer</span>
                                            <% } %>
                                            <% visitTransfers.received.each { received ->
                                                def counterReferral = received.counterReferralCompleted ?
                                                        "Counter-referral sent" + (received.counterReferralCompletedAt ? " on " + ui.formatDatetimePretty(received.counterReferralCompletedAt) : "") :
                                                        "Counter-referral not sent yet"
                                                def receivedMeta = "Received" + (received.sendingFacility ? " from " + received.sendingFacility : "") +
                                                        (received.recordedAt ? ", recorded at registration on " + ui.formatDatetimePretty(received.recordedAt) : "") +
                                                        (received.recordedBy ? " by " + received.recordedBy : "") + ". " + counterReferral + "."
                                            %>
                                                <button type="button" class="rhip-btn-transfer" data-direction="received"
                                                        data-transfer-id="${ ui.encodeHtmlAttribute(received.hieTransferId) }"
                                                        data-local-uuid="${ ui.encodeHtmlAttribute(received.localUuid ?: '') }"
                                                        data-patient-id="${ transferPatientId ?: '' }"
                                                        data-meta="${ ui.encodeHtmlAttribute(receivedMeta) }" ${ transferNoAccess }>
                                                    <i class="icon-eye-open" aria-hidden="true"></i> View transfer
                                                </button>
                                            <% } %>
                                            <% visitTransfers.sent.each { sent ->
                                                def sentMeta = "Sent" + (sent.receivingFacility ? " to " + sent.receivingFacility : "") +
                                                        (sent.decisionToTransferAt ? " on " + ui.formatDatetimePretty(sent.decisionToTransferAt) : "") +
                                                        (sent.createdBy ? " by " + sent.createdBy : "") +
                                                        (sent.approvalLabel ? ". Approval: " + sent.approvalLabel : "") +
                                                        ". " + (sent.hieSent ? "Sent to the HIE." : "Not sent to the HIE yet.")
                                            %>
                                                <button type="button" class="rhip-btn-transfer" data-direction="sent"
                                                        data-transfer-id="${ ui.encodeHtmlAttribute(sent.hieTransferId ?: '') }"
                                                        data-local-uuid="${ ui.encodeHtmlAttribute(sent.uuid ?: '') }"
                                                        data-meta="${ ui.encodeHtmlAttribute(sentMeta) }" ${ transferNoAccess }>
                                                    <i class="icon-signout" aria-hidden="true"></i> View sent transfer
                                                </button>
                                            <% } %>
                                        <% } %>
                                        <% if (verificationApplied) { %>
                                        <% if (canViewHistory) { %>
                                            <button type="button" class="rhip-btn-history" onclick="openRhipHistory('${ globalBillId }')">
                                                <i class="icon-time"></i> Verification history (<span id="rhip-history-count-${ globalBillId }">${ verificationEventCounts[globalBillId] ?: 0 }</span>)
                                            </button>
                                        <% } %>
                                        <span id="rhip-stage-chip-${ globalBillId }" class="rhip-chip rhip-chip-${ verification.chipClass }">${ stage.label }</span>
                                        <% if (canActOnBill) { %>
                                            <span id="rhip-bulk-${ globalBillId }" class="rhip-bulk-actions" ${ verification.awaitingCount > 0 ? '' : 'hidden="hidden"' }>
                                                <form class="rhip-ajax-form" method="post" action="${ ui.pageLink("rwandaemr", "admin/rhipVoucherSubmissions") }" data-confirm="${ ui.encodeHtmlAttribute(stage.acceptLabel) } the {count} item(s) still to decide?">
                                                    <input type="hidden" name="action" value="verifyAll"/>
                                                    <input type="hidden" name="decision" value="accept"/>
                                                    ${ hiddenFilters }
                                                    <button type="submit" class="rhip-btn-accept">${ stage.name() == "FACILITY_RESPONSE" ? "Accept all rejections" : "Accept all" } (<span class="rhip-awaiting-count">${ verification.awaitingCount }</span>)</button>
                                                </form>
                                                <button type="button" class="rhip-btn-reject" onclick="toggleRhipRejectForm('all-${ globalBillId }')">${ stage.name() == "FACILITY_RESPONSE" ? "Dispute all" : "Reject all" } (<span class="rhip-awaiting-count">${ verification.awaitingCount }</span>)</button>
                                            </span>
                                        <% } %>
                                        <% if (canActOnBill) { %>
                                            <form method="post" action="${ ui.pageLink("rwandaemr", "admin/rhipVoucherSubmissions") }" onsubmit="return submitRhipVerification(this);">
                                                <input type="hidden" name="action" value="submitVerification"/>
                                                ${ hiddenFilters }
                                                <button id="rhip-submit-${ globalBillId }" class="rhip-btn-submit" type="submit" ${ verification.awaitingCount > 0 ? 'disabled="disabled" title="Decide on every item first"' : '' }>${ stage.submitLabel }</button>
                                            </form>
                                        <% } else if (stage.actor) { %>
                                            <span class="rhip-muted">Waiting for the ${ stage.actor.name() == "FACILITY" ? "facility" : "insurer" }</span>
                                        <% } %>
                                        <% } %>
                                    </div>
                                </div>
                                <% if (canActOnBill) { %>
                                <div id="rhip-reject-all-${ globalBillId }" class="rhip-reject-row rhip-bulk-reject">
                                    <form class="rhip-reject-form rhip-ajax-form" method="post" action="${ ui.pageLink("rwandaemr", "admin/rhipVoucherSubmissions") }">
                                        <input type="hidden" name="action" value="verifyAll"/>
                                        <input type="hidden" name="decision" value="reject"/>
                                        ${ hiddenFilters }
                                        <div>
                                            <label for="rhip-reason-all-${ globalBillId }">${ stage.name() == "FACILITY_RESPONSE" ? "Reason for disputing all" : "Reason for rejecting all" } <span class="rhip-awaiting-count">${ verification.awaitingCount }</span> item(s)</label>
                                            <select id="rhip-reason-all-${ globalBillId }" name="reasonId" data-reason-type="${ stageReasonType }" onchange="updateRhipOtherReason(this)">
                                                <% stageReasons.each { reason -> %>
                                                    <option value="${ reason.reasonId }">${ ui.encodeHtmlContent(reason.name) }</option>
                                                <% } %>
                                                <option value="other" ${ stageReasons ? "" : "selected" }>Other (type a new reason)</option>
                                            </select>
                                        </div>
                                        <div class="rhip-other-reason">
                                            <label for="rhip-other-all-${ globalBillId }">New reason</label>
                                            <input type="text" id="rhip-other-all-${ globalBillId }" name="otherReason" maxlength="255" placeholder="Type the reason"/>
                                        </div>
                                        <div>
                                            <label for="rhip-note-all-${ globalBillId }">Note (optional)</label>
                                            <input type="text" id="rhip-note-all-${ globalBillId }" name="note" maxlength="1000"/>
                                        </div>
                                        <div class="rhip-decision">
                                            <button type="submit" class="rhip-btn-reject">${ stage.name() == "FACILITY_RESPONSE" ? "Dispute all" : "Reject all" }</button>
                                            <button type="button" onclick="toggleRhipRejectForm('all-${ globalBillId }')">Cancel</button>
                                        </div>
                                    </form>
                                </div>
                                <% } %>
                                <% if (verificationApplied) { %>
                                <div class="rhip-verification-tiles">
                                    <div class="rhip-verification-tile"><label>Accepted</label><strong id="rhip-tile-accepted-${ globalBillId }">${ ui.format(verification.acceptedAmount) }</strong></div>
                                    <div class="rhip-verification-tile"><label>Rejected</label><strong id="rhip-tile-rejected-${ globalBillId }">${ ui.format(verification.rejectedAmount) }</strong></div>
                                    <div class="rhip-verification-tile"><label>Not final yet</label><strong id="rhip-tile-pending-${ globalBillId }">${ ui.format(verification.pendingAmount) }</strong></div>
                                    <div class="rhip-verification-tile"><label>To decide now</label><strong id="rhip-tile-todo-${ globalBillId }">${ verification.awaitingCount } of ${ verification.itemCount }</strong></div>
                                </div>
                                <% } %>
                                <table class="rhip-mini-table">
                                    <thead>
                                    <tr>
                                        <th>Item</th>
                                        <th>Date</th>
                                        <th>Patient Bill</th>
                                        <th>Payment</th>
                                        <th class="rhip-number">Quantity</th>
                                        <th class="rhip-number">Unit Price</th>
                                        <th class="rhip-number">Amount</th>
                                        <% if (verificationApplied) { %><th>Verification</th><% } %>
                                    </tr>
                                    </thead>
                                    <tbody>
                                    <% (itemGroupsByGlobalBillId[globalBillId] ?: []).each { itemGroup -> %>
                                        <tr class="rhip-category-row">
                                            <td colspan="6">${ ui.encodeHtmlContent(itemGroup.name ?: "") } <span class="rhip-muted">(${ itemGroup.items.size() } item${ itemGroup.items.size() == 1 ? "" : "s" })</span></td>
                                            <td class="rhip-number">${ ui.format(itemGroup.total) }</td>
                                            <% if (verificationApplied) { %><td></td><% } %>
                                        </tr>
                                    <%  itemGroup.items.each { item ->
                                            def consommation = item.consommation
                                            if (item) {
                                                def itemName = item.service?.facilityServicePrice?.name ?: item.serviceOther ?: item.serviceOtherDescription ?: item.hopService?.name
                                                def patientBill = consommation?.patientBill
                                                def itemId = item.patientServiceBillId
                                                itemNames[itemId] = itemName
                                                def itemVerification = itemVerifications[itemId]
                                                def itemStatus = itemVerification?.status ?: pendingItemStatus
                                                def reviewable = canActOnBill && stage.isReviewable(itemStatus)
                                                // An item already decided at this step is locked until "Change" is clicked
                                                def decided = itemStatus == stage.acceptStatus || itemStatus == stage.rejectStatus
                                    %>
                                        <tr id="rhip-item-${ itemId }" class="${ verificationApplied ? "rhip-item-" + itemStatus.outcome : "" }">
                                            <td><span class="rhip-item-name">${ ui.format(itemName) }</span></td>
                                            <td>${ ui.formatDatePretty(item.serviceDate) }</td>
                                            <td>${ patientBill?.patientBillId ?: "" }</td>
                                            <td>
                                                <% if (patientBill) { %>
                                                    <span class="rhip-chip rhip-chip-${ patientBill.isPaid() ? "CONFIRMED" : "FAILED" }">${ patientBill.isPaid() ? "Paid" : "Unpaid" }</span>
                                                <% } %>
                                            </td>
                                            <td class="rhip-number">${ ui.format(item.quantity) }</td>
                                            <td class="rhip-number">${ ui.format(item.unitPrice) }</td>
                                            <td class="rhip-number">${ ui.format(item.amount) }</td>
                                            <% if (verificationApplied) { %>
                                            <td>
                                                <div class="rhip-decision">
                                                    <span class="rhip-chip rhip-item-chip rhip-chip-${ itemStatus.outcome }">${ itemStatus.label }</span>
                                                    <% if (reviewable) { %>
                                                        <form class="rhip-ajax-form" method="post" action="${ ui.pageLink("rwandaemr", "admin/rhipVoucherSubmissions") }">
                                                            <input type="hidden" name="action" value="verifyItem"/>
                                                            <input type="hidden" name="itemId" value="${ itemId }"/>
                                                            <input type="hidden" name="decision" value="accept"/>
                                                            ${ hiddenFilters }
                                                            <button type="submit" class="rhip-btn-accept rhip-btn-decision ${ itemStatus == stage.acceptStatus ? "selected" : "" }" ${ decided ? 'disabled="disabled"' : '' }>${ stage.acceptLabel }</button>
                                                        </form>
                                                        <button type="button" class="rhip-btn-reject rhip-btn-decision ${ itemStatus == stage.rejectStatus ? "selected" : "" }" ${ decided ? 'disabled="disabled"' : '' } onclick="toggleRhipRejectForm('${ itemId }')">${ stage.rejectLabel }</button>
                                                        <button type="button" class="rhip-btn-change" ${ decided ? '' : 'hidden="hidden"' } onclick="unlockRhipDecision('${ itemId }')" title="Change the decision on this item">Change</button>
                                                    <% } %>
                                                    <span class="rhip-decision-reason" ${ itemVerification?.reasonName || itemVerification?.note ? '' : 'hidden="hidden"' }>${ ui.encodeHtmlContent(itemVerification?.reasonName ?: "") }${ itemVerification?.reasonName && itemVerification?.note ? ": " : "" }${ ui.encodeHtmlContent(itemVerification?.note ?: "") }</span>
                                                </div>
                                            </td>
                                            <% } %>
                                        </tr>
                                        <% if (reviewable) { %>
                                        <tr id="rhip-reject-${ itemId }" class="rhip-reject-row">
                                            <td colspan="8">
                                                <form class="rhip-reject-form rhip-ajax-form" method="post" action="${ ui.pageLink("rwandaemr", "admin/rhipVoucherSubmissions") }">
                                                    <input type="hidden" name="action" value="verifyItem"/>
                                                    <input type="hidden" name="itemId" value="${ itemId }"/>
                                                    <input type="hidden" name="decision" value="reject"/>
                                                    ${ hiddenFilters }
                                                    <div>
                                                        <label for="rhip-reason-${ itemId }">${ stage.name() == "FACILITY_RESPONSE" ? "Reason for disputing" : "Reason for rejecting" }</label>
                                                        <select id="rhip-reason-${ itemId }" name="reasonId" data-reason-type="${ stageReasonType }" onchange="updateRhipOtherReason(this)">
                                                            <% stageReasons.each { reason -> %>
                                                                <option value="${ reason.reasonId }" ${ itemVerification?.reasonId == reason.reasonId ? "selected" : "" }>${ ui.encodeHtmlContent(reason.name) }</option>
                                                            <% } %>
                                                            <option value="other" ${ stageReasons ? "" : "selected" }>Other (type a new reason)</option>
                                                        </select>
                                                    </div>
                                                    <div class="rhip-other-reason">
                                                        <label for="rhip-other-${ itemId }">New reason</label>
                                                        <input type="text" id="rhip-other-${ itemId }" name="otherReason" maxlength="255" placeholder="Type the reason"/>
                                                    </div>
                                                    <div>
                                                        <label for="rhip-note-${ itemId }">Note (optional)</label>
                                                        <input type="text" id="rhip-note-${ itemId }" name="note" maxlength="1000" value="${ ui.encodeHtmlAttribute(itemVerification?.note ?: "") }"/>
                                                    </div>
                                                    <div class="rhip-decision">
                                                        <button type="submit" class="rhip-btn-reject">${ stage.rejectLabel }</button>
                                                        <button type="button" onclick="toggleRhipRejectForm('${ itemId }')">Cancel</button>
                                                    </div>
                                                </form>
                                            </td>
                                        </tr>
                                        <% } %>
                                    <%      }
                                        }
                                    } %>
                                    </tbody>
                                </table>
                            </div>

                            <% if (canViewHistory && verificationApplied) { %>
                            <div id="rhip-history-${ globalBillId }" hidden="hidden" data-bill-label="${ ui.encodeHtmlAttribute((globalBill.billIdentifier ?: "") + " · " + (row.patientName ?: "")) }"></div>
                            <% } %>
                        </td>
                    </tr>
                <% } %>
                </tbody>
            </table>
        <% } %>
    </div>

    <div class="rhip-pagination">
        <% if (hasPreviousPage) { %>
            <a class="button" href="${ ui.pageLink("rwandaemr", "admin/rhipVoucherSubmissions") }?page=${ previousPage }&${ filterQueryString }">Previous</a>
        <% } else { %>
            <span class="rhip-muted">Previous</span>
        <% } %>
        <strong>Page ${ page } of ${ totalPages }</strong>
        <% if (hasNextPage) { %>
            <a class="button" href="${ ui.pageLink("rwandaemr", "admin/rhipVoucherSubmissions") }?page=${ nextPage }&${ filterQueryString }">Next</a>
        <% } else { %>
            <span class="rhip-muted">Next</span>
        <% } %>
    </div>
</div>

<div id="rhip-drawer-overlay" class="rhip-drawer-overlay"></div>
<div id="rhip-closure-dialog" class="rhip-closure-overlay" hidden>
    <div class="rhip-closure-box" role="dialog" aria-modal="true" aria-labelledby="rhip-closure-title">
        <h3 id="rhip-closure-title">Close the MMI bill</h3>
        <div class="rhip-closure-body"></div>
        <div class="rhip-closure-buttons">
            <button type="button" class="rhip-closure-cancel">Cancel</button>
            <button type="button" class="rhip-closure-confirm confirm" disabled>Close the bill at MMI</button>
        </div>
    </div>
</div>

<aside id="rhip-drawer" class="rhip-drawer" role="dialog" aria-modal="true" aria-labelledby="rhip-drawer-title">
    <div class="rhip-drawer-head"></div>
    <div class="rhip-drawer-body"></div>
</aside>

<div id="rhip-transfer-overlay" class="rhip-transfer-overlay" hidden></div>
<div id="rhip-transfer-dialog" class="rhip-transfer-dialog" role="dialog" aria-modal="true" aria-labelledby="rhip-transfer-dialog-title" hidden>
    <div class="rhip-transfer-dialog-head">
        <div>
            <h3 id="rhip-transfer-dialog-title">Transfer</h3>
            <div id="rhip-transfer-dialog-meta" class="rhip-muted"></div>
        </div>
        <button type="button" class="rhip-transfer-close" aria-label="Close">&times;</button>
    </div>
    <div id="rhip-transfer-dialog-body" class="rhip-transfer-dialog-body"></div>
    <div class="rhip-transfer-dialog-foot">
        <a id="rhip-transfer-dialog-pdf" class="button" target="_blank" rel="noopener" hidden><i class="icon-file" aria-hidden="true"></i> Download PDF</a>
        <button type="button" class="rhip-transfer-close cancel">Close</button>
    </div>
</div>

<script type="text/javascript">
    // Previews a transfer of the visit with the Transfer app's own form preview, as its History page does
    (function() {
        var basePath = "${ ui.encodeJavaScript(openmrsContextPath) }";
        var previewScripts = null;
        var opener = null;
        window.transferOpenmrsPath = window.transferOpenmrsPath || basePath;

        function loadPreviewScripts() {
            if (typeof window.renderTransferPreviewInto === "function") {
                return jq.Deferred().resolve().promise();
            }
            if (!previewScripts) {
                previewScripts = jq.ajax({
                    url: basePath + "/moduleResources/transferapp/scripts/transferPreviewCommon.js",
                    dataType: "script",
                    cache: true
                });
            }
            return previewScripts;
        }

        function parse(response) {
            if (typeof response === "string") {
                try {
                    return jq.parseJSON(response);
                } catch (e) {
                    return null;
                }
            }
            return response;
        }

        function fetchLocal(uuid) {
            var result = jq.Deferred();
            if (!uuid) {
                return result.resolve(null).promise();
            }
            jq.ajax({ url: basePath + "/module/transferapp/transfer/preview.form", data: { uuid: uuid }, dataType: "json" })
                .done(function(response) {
                    response = parse(response);
                    result.resolve(response && response.status === "success" && response.transfer ? response.transfer : null);
                })
                .fail(function() { result.resolve(null); });
            return result.promise();
        }

        function fetchHie(transferId) {
            var result = jq.Deferred();
            if (!transferId) {
                return result.resolve(null).promise();
            }
            jq.ajax({ url: basePath + "/ws/rest/v1/transferapp/transfer", data: { transferId: transferId, activeOnly: false }, dataType: "json" })
                .done(function(response) {
                    response = parse(response);
                    var items = response && response.status !== "error" && response.data ? response.data : [];
                    result.resolve(items.length ? items[0] : null);
                })
                .fail(function() { result.resolve(null); });
            return result.promise();
        }

        function showMessage(text, isError) {
            jq("#rhip-transfer-dialog-body").empty().append(
                jq("<p/>").toggleClass("rhip-transfer-error", !!isError).text(text));
        }

        function openDialog(button) {
            opener = button;
            var direction = button.attr("data-direction");
            var transferId = jq.trim(button.attr("data-transfer-id") || "");
            var localUuid = jq.trim(button.attr("data-local-uuid") || "");
            var patientId = jq.trim(button.attr("data-patient-id") || "");
            jq("#rhip-transfer-dialog-title").text(direction === "sent" ? "Transfer sent from this facility" : "Transfer that brought the patient here");
            jq("#rhip-transfer-dialog-meta").text(button.attr("data-meta") || "");
            var pdf = jq("#rhip-transfer-dialog-pdf");
            if (direction === "received" && transferId && patientId) {
                pdf.attr("href", basePath + "/module/transferapp/transfer/exportHiePdf.form?patientId="
                    + encodeURIComponent(patientId) + "&hieTransferId=" + encodeURIComponent(transferId)).prop("hidden", false);
            } else {
                pdf.removeAttr("href").prop("hidden", true);
            }
            showMessage("Loading the transfer...", false);
            jq("#rhip-transfer-overlay, #rhip-transfer-dialog").prop("hidden", false);
            jq("#rhip-transfer-dialog .rhip-transfer-close").first().trigger("focus");

            fetchLocal(localUuid).then(function(transfer) {
                return transfer ? transfer : fetchHie(transferId);
            }).then(function(transfer) {
                if (!transfer) {
                    showMessage("The transfer could not be found, neither at this facility nor in the HIE.", true);
                    return;
                }
                loadPreviewScripts().done(function() {
                    if (typeof window.renderTransferPreviewInto === "function") {
                        window.renderTransferPreviewInto("#rhip-transfer-dialog-body", transfer);
                    } else {
                        showMessage("The Transfer app's preview could not be loaded.", true);
                    }
                }).fail(function() {
                    showMessage("The Transfer app's preview could not be loaded.", true);
                });
            });
        }

        function closeDialog() {
            jq("#rhip-transfer-overlay, #rhip-transfer-dialog").prop("hidden", true);
            if (opener) {
                opener.trigger("focus");
                opener = null;
            }
        }

        jq(function() {
            jq(document).on("click", ".rhip-btn-transfer", function(e) {
                e.preventDefault();
                e.stopPropagation();
                openDialog(jq(this));
            });
            jq(document).on("click", ".rhip-transfer-close, #rhip-transfer-overlay", function(e) {
                e.preventDefault();
                closeDialog();
            });
            jq(document).on("keydown", function(e) {
                if (e.key === "Escape" && !jq("#rhip-transfer-dialog").prop("hidden")) {
                    closeDialog();
                }
            });
        });
    })();
</script>
