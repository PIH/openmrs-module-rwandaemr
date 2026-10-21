<%
    ui.decorateWith("appui", "standardEmrPage")
    def openmrsContextPath = contextPath ? (contextPath.startsWith("/") ? contextPath : "/" + contextPath) : ""
%>

<script type="text/javascript">
    var breadcrumbs = [
        { icon: "icon-home", link: '${ openmrsContextPath }/index.htm' },
        { label: "Radiology Queue" }
    ];
</script>

<% if (!authorized) { %>
    <div class="rq-empty">You do not have permission to view the radiology queue.</div>
<% } else { %>

<style>
    /* The queue is wide, so on this page it uses the whole screen: the standard page and the Rwanda EMR stylesheet
       (globalresources/styles/global.css, which sets body to 1500px) both make pages narrower */
    body { max-width: none !important; width: auto !important; margin: 10px 12px !important; }
    .rq-page { margin: 0 auto; }
    .rq-header { display: flex; align-items: flex-end; justify-content: space-between; gap: 16px; margin: 8px 0 12px; flex-wrap: wrap; }
    .rq-title { margin: 0; font-size: 28px; color: #263238; }
    .rq-subtitle { margin: 4px 0 0; color: #607d8b; font-size: 13px; }

    /* Summary cards double as quick filters */
    .rq-summary { display: flex; gap: 8px; flex-wrap: wrap; }
    .rq-card { border: 1px solid #d9e2e7; background: #fff; padding: 7px 12px; border-radius: 6px; text-align: right; min-width: 84px;
               cursor: pointer; transition: box-shadow .15s, border-color .15s, transform .15s; user-select: none; }
    .rq-card:hover { box-shadow: 0 2px 8px rgba(0,0,0,.08); transform: translateY(-1px); }
    .rq-card strong { display: block; font-size: 22px; color: #263238; line-height: 1.2; }
    .rq-card span { font-size: 12px; color: #607d8b; white-space: nowrap; }
    .rq-card.rq-active { border-color: #00473f; box-shadow: inset 0 0 0 2px #00473f; }
    .rq-card.rq-card-stat strong { color: #c62828; }
    .rq-card.rq-card-ready strong { color: #2e7d32; }
    .rq-card.rq-card-unpaid strong { color: #c62828; }

    .rq-panel { border: 1px solid #d9e2e7; background: #fff; padding: 10px 12px; margin-bottom: 10px; border-radius: 6px; }
    .rq-filters { display: grid; grid-template-columns: 140px 140px 105px 140px 140px 150px minmax(170px, 1fr); gap: 8px; align-items: end; }
    .rq-field label { display: block; margin-bottom: 3px; font-size: 11px; font-weight: bold; color: #455a64; text-transform: uppercase; }
    .rq-field input, .rq-field select { width: 100%; box-sizing: border-box; min-height: 32px; border: 1px solid #b0bec5; border-radius: 3px; padding: 4px 7px; background: #fff; }
    .rq-toolbar { display: flex; gap: 8px; margin-top: 8px; flex-wrap: wrap; align-items: center; }
    .rq-toolbar button, .rq-toolbar a { min-height: 32px; white-space: nowrap; }
    .rq-toolbar .rq-spacer { flex: 1; }
    .rq-alert-on { background: #2e7d32 !important; color: #fff !important; border-color: #2e7d32 !important; }
    .rq-columns { position: relative; display: inline-block; }
    .rq-columns-menu { display: none; position: absolute; z-index: 20; top: 36px; left: 0; background: #fff; border: 1px solid #d9e2e7;
                       border-radius: 6px; box-shadow: 0 6px 18px rgba(0,0,0,.12); padding: 8px 12px; min-width: 210px; }
    .rq-columns-menu label { display: flex; align-items: center; gap: 6px; padding: 3px 0; font-weight: normal; cursor: pointer; white-space: nowrap; }
    .rq-columns-menu input { width: auto; margin: 0; }

    .rq-status-line { display: flex; justify-content: space-between; color: #607d8b; font-size: 12px; margin: 0 0 6px; }
    .rq-error { color: #9f1d1d; font-weight: bold; }
    .rq-creat { white-space: nowrap; font-weight: bold; }
    .rq-creat-high { color: #9f1d1d; }
    .rq-report-test { margin-bottom: 4px; }
    .rq-report-test .rq-test-name { display: block; color: #455a64; font-size: 12px; }
    .rq-draft { display: inline-block; padding: 1px 6px; border-radius: 10px; font-size: 11px; font-weight: bold; color: #0b5394; background: #d9edf7; }
    .rq-resend { display: inline-block; margin-top: 4px; padding: 2px 10px; font-size: 11.5px; font-weight: bold; min-height: 0; line-height: 1.6;
        color: #0b5394; background: #fff; border: 1px solid #0b5394; border-radius: 12px; cursor: pointer; white-space: nowrap; }
    .rq-resend:hover { background: #d9edf7; }
    .rq-resend[disabled] { color: #78909c; border-color: #b0bec5; background: #eceff1; cursor: wait; }

    /* The table fits the screen; the header stays visible while scrolling */
    /* The table scrolls with the page, not in a box of its own; its header stays at the top of the screen while scrolling */
    .rq-table-wrap { border: 1px solid #d9e2e7; background: #fff; border-radius: 6px; }
    table.rq-table { width: 100%; border-collapse: separate; border-spacing: 0; }
    .rq-table th { position: sticky; top: 0; z-index: 2; background: #f3f6f8; color: #455a64; font-size: 11px; text-transform: uppercase;
                   border-bottom: 1px solid #d9e2e7; padding: 8px 7px; text-align: left; white-space: nowrap; }
    .rq-table th.rq-sortable { cursor: pointer; user-select: none; }
    .rq-table th.rq-sortable:hover { background: #e6edf1; }
    .rq-table th .rq-arrow { color: #90a4ae; margin-left: 3px; }
    .rq-table th.rq-sorted .rq-arrow { color: #263238; }
    .rq-table td { border-bottom: 1px solid #edf2f5; padding: 7px; vertical-align: top; color: #263238; font-size: 13px; }
    .rq-table tbody tr { cursor: pointer; }
    .rq-table tbody tr:hover td { background: #f5f9fb; }
    .rq-table tbody tr td:first-child { border-left: 4px solid transparent; }
    .rq-table tbody tr.rq-row-ready td:first-child { border-left-color: #43a047; }
    .rq-table tbody tr.rq-row-stat td:first-child { border-left-color: #e53935; }
    .rq-table tbody tr.rq-new td { background: #fff8e1; }
    .rq-hidden { display: none; }
    .rq-muted { color: #78909c; font-size: 12px; }
    .rq-nowrap { white-space: nowrap; }
    .rq-history { max-width: 230px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .rq-study { max-width: 260px; }

    .rq-chip { display: inline-block; padding: 3px 8px; border-radius: 12px; font-weight: bold; font-size: 11.5px; white-space: nowrap; color: #455a64; background: #eceff1; }
    .rq-PAID, .rq-REPORTED, .rq-processed { color: #1b5e20; background: #dff0d8; }
    .rq-PARTIALLY_PAID, .rq-SENT, .rq-IMAGES_RECEIVED { color: #0b5394; background: #d9edf7; }
    .rq-NOT_PAID, .rq-CANCELLED { color: #9f1d1d; background: #f8d7da; }
    .rq-NOT_BILLED, .rq-NOT_SENT, .rq-STUDY_DONE_NO_LINK, .rq-not-processed { color: #8a6d3b; background: #fcf8e3; }
    .rq-ready { color: #fff; background: #2e7d32; }
    .rq-stat { color: #fff; background: #e53935; font-size: 10.5px; padding: 2px 6px; margin-right: 4px; vertical-align: 1px; }
    /* Not paid pulses to draw attention; a soft pulse rather than a hard blink, which is tiring to watch all day */
    .rq-chip.rq-NOT_PAID { animation: rq-pulse 1.4s ease-in-out infinite; }
    @keyframes rq-pulse {
        0%, 100% { background: #f8d7da; box-shadow: 0 0 0 0 rgba(198, 40, 40, 0.55); }
        50% { background: #ef9a9a; box-shadow: 0 0 0 5px rgba(198, 40, 40, 0); }
    }

    .rq-wait { display: inline-block; margin-top: 3px; font-size: 11.5px; font-weight: bold; padding: 1px 6px; border-radius: 10px; color: #455a64; background: #eceff1; }
    .rq-wait-amber { color: #8a5300; background: #ffe0b2; }
    .rq-wait-red { color: #fff; background: #c62828; }
    /* Results are icons, named in their tooltip */
    .rq-links a { display: inline-block; margin: 0 4px 2px 0; padding: 2px 5px; font-size: 17px; line-height: 1.2; text-decoration: none; border-radius: 4px; }
    .rq-links a:hover { background: #e6edf1; }
    .rq-links a.rq-enter-report { color: #fff; background: #00473f; }

    /* Detail panel */
    .rq-overlay { display: none; position: fixed; inset: 0; background: rgba(38,50,56,.35); z-index: 1000; }
    .rq-drawer { position: fixed; top: 0; right: 0; height: 100%; width: 460px; max-width: 95vw; background: #fff; z-index: 1001;
                 box-shadow: -6px 0 24px rgba(0,0,0,.18); transform: translateX(105%); transition: transform .2s ease-out; overflow-y: auto; }
    .rq-drawer.rq-open { transform: translateX(0); }
    .rq-drawer-head { position: sticky; top: 0; background: #00473f; color: #fff; padding: 14px 16px; display: flex; justify-content: space-between; align-items: flex-start; gap: 10px; }
    .rq-drawer-head h4 { margin: 0; font-size: 18px; color: #fff; }
    .rq-drawer-head .rq-muted { color: #cfe3df; }
    .rq-drawer-close { background: transparent; border: 0; color: #fff; font-size: 24px; line-height: 1; cursor: pointer; padding: 0 4px; }
    .rq-drawer-body { padding: 14px 16px 24px; }
    .rq-section { margin-bottom: 16px; }
    .rq-section h5 { margin: 0 0 6px; font-size: 12px; text-transform: uppercase; color: #607d8b; letter-spacing: .4px; }
    .rq-section p { margin: 0; white-space: pre-wrap; }
    .rq-tests { list-style: none; padding: 0; margin: 0; }
    .rq-tests li { padding: 5px 0; border-bottom: 1px solid #edf2f5; }
    .rq-timeline { list-style: none; padding: 0; margin: 0; }
    .rq-timeline li { position: relative; padding: 0 0 12px 22px; }
    .rq-timeline li:before { content: ""; position: absolute; left: 5px; top: 5px; width: 10px; height: 10px; border-radius: 50%; background: #cfd8dc; }
    .rq-timeline li:after { content: ""; position: absolute; left: 9px; top: 17px; bottom: 0; width: 2px; background: #eceff1; }
    .rq-timeline li:last-child:after { display: none; }
    .rq-timeline li.rq-done:before { background: #43a047; }
    .rq-timeline li.rq-warn:before { background: #e53935; }
    .rq-timeline .rq-when { color: #78909c; font-size: 12px; }

    .rq-empty { border: 1px solid #d9e2e7; background: #fff; padding: 18px; color: #455a64; }

    /* Respect users whose device is set to reduce motion */
    @media (prefers-reduced-motion: reduce) {
        .rq-chip.rq-NOT_PAID { animation: none; outline: 2px solid #c62828; }
        .rq-drawer, .rq-card { transition: none; }
    }
    @media (max-width: 1200px) { .rq-filters { grid-template-columns: repeat(2, minmax(140px, 1fr)); } }
</style>

<div class="rq-page">
    <div class="rq-header">
        <div>
            <h3 class="rq-title">Radiology Queue</h3>
            <p class="rq-subtitle">Requested studies with PACS and payment status. Updates every 30 seconds. Click a card to filter, a row for details.</p>
        </div>
        <div class="rq-summary" id="rq-summary"></div>
    </div>

    <div class="rq-panel">
        <div class="rq-filters">
            <div class="rq-field">
                <label for="rq-start">From</label>
                <input id="rq-start" type="date" value="${ ui.encodeHtmlAttribute(queueRequest.startDate) }"/>
            </div>
            <div class="rq-field">
                <label for="rq-end">To</label>
                <input id="rq-end" type="date" value="${ ui.encodeHtmlAttribute(queueRequest.endDate) }"/>
            </div>
            <div class="rq-field">
                <label for="rq-modality">Modality</label>
                <select id="rq-modality">
                    <option value="">All</option>
                    <% modalities.each { m -> %>
                        <option value="${ m }" ${ queueRequest.modality == m ? 'selected="selected"' : '' }>${ m }</option>
                    <% } %>
                </select>
            </div>
            <div class="rq-field">
                <label for="rq-pacs">PACS status</label>
                <select id="rq-pacs">
                    <option value="">All</option>
                    <% pacsStatuses.each { s -> %>
                        <option value="${ s.name() }" ${ queueRequest.pacsStatus == s.name() ? 'selected="selected"' : '' }>${ s.label }</option>
                    <% } %>
                </select>
            </div>
            <div class="rq-field">
                <label for="rq-payment">Payment status</label>
                <select id="rq-payment">
                    <option value="">All</option>
                    <% paymentStatuses.each { s -> %>
                        <option value="${ s.name() }" ${ queueRequest.paymentStatus == s.name() ? 'selected="selected"' : '' }>${ s.label }</option>
                    <% } %>
                </select>
            </div>
            <div class="rq-field">
                <label for="rq-processed">Processed</label>
                <select id="rq-processed">
                    <option value="">All</option>
                    <option value="READY" ${ queueRequest.processed == 'READY' ? 'selected="selected"' : '' }>Ready for exam</option>
                    <option value="YES" ${ queueRequest.processed == 'YES' ? 'selected="selected"' : '' }>Processed</option>
                    <option value="NO" ${ queueRequest.processed == 'NO' ? 'selected="selected"' : '' }>Not processed</option>
                </select>
            </div>
            <div class="rq-field">
                <label for="rq-query">Patient ID, name, order number or study</label>
                <input id="rq-query" type="text" value="${ ui.encodeHtmlAttribute(queueRequest.query) }"/>
            </div>
        </div>
        <div class="rq-toolbar">
            <button type="button" id="rq-refresh">Refresh</button>
            <div class="rq-columns">
                <button type="button" id="rq-columns-button">Columns &#9662;</button>
                <div class="rq-columns-menu" id="rq-columns-menu"></div>
            </div>
            <button type="button" id="rq-alerts" title="Play a sound and show a notification when a new request arrives">&#128276; Alerts: Off</button>
            <button type="button" id="rq-reset" title="Clear filters, sort and column choices">Reset view</button>
            <span class="rq-spacer"></span>
            <a class="button" id="rq-export" href="#">Export to Excel</a>
        </div>
    </div>

    <div class="rq-status-line">
        <span id="rq-message"></span>
        <span id="rq-updated"></span>
    </div>

    <div class="rq-table-wrap">
        <table class="rq-table">
            <thead>
                <tr>
                    <th data-col="sno">S.No</th>
                    <th data-col="patientId" class="rq-sortable" data-sort="patientIdentifier">Patient ID<span class="rq-arrow">&#8597;</span></th>
                    <th data-col="patientName" class="rq-sortable" data-sort="patientName">Patient Name<span class="rq-arrow">&#8597;</span></th>
                    <th data-col="ageSex" class="rq-sortable" data-sort="age" data-type="number">Age/Sex<span class="rq-arrow">&#8597;</span></th>
                    <th data-col="modality" class="rq-sortable" data-sort="modality">Modality<span class="rq-arrow">&#8597;</span></th>
                    <th data-col="study" class="rq-sortable" data-sort="typeOfStudy">Type of Study<span class="rq-arrow">&#8597;</span></th>
                    <th data-col="history" class="rq-sortable" data-sort="clinicalHistory">Clinical History<span class="rq-arrow">&#8597;</span></th>
                    <th data-col="creatinine" title="The patient's latest creatinine result, shown for CT">Creatinine</th>
                    <th data-col="requestedBy" class="rq-sortable" data-sort="requestingPhysician">Requested By<span class="rq-arrow">&#8597;</span></th>
                    <th data-col="requested" class="rq-sortable" data-sort="timeRequested" data-type="number">Time Requested<span class="rq-arrow">&#8597;</span></th>
                    <th data-col="pacs" class="rq-sortable" data-sort="pacsStatusLabel">PACS Status<span class="rq-arrow">&#8597;</span></th>
                    <th data-col="processed" class="rq-sortable" data-sort="processedRank" data-type="number">Processed<span class="rq-arrow">&#8597;</span></th>
                    <th data-col="payment" class="rq-sortable" data-sort="paymentStatusLabel">Payment<span class="rq-arrow">&#8597;</span></th>
                    <th data-col="results">Results</th>
                </tr>
            </thead>
            <tbody id="rq-body"></tbody>
        </table>
    </div>
</div>

<div class="rq-overlay" id="rq-overlay"></div>
<aside class="rq-drawer" id="rq-drawer" aria-hidden="true">
    <div class="rq-drawer-head">
        <div id="rq-drawer-title"></div>
        <button type="button" class="rq-drawer-close" id="rq-drawer-close" aria-label="Close">&times;</button>
    </div>
    <div class="rq-drawer-body" id="rq-drawer-body"></div>
</aside>

<script type="text/javascript">
    (function() {
        var REFRESH_MS = 30000;
        var WAIT_AMBER_MIN = 60;
        var WAIT_RED_MIN = 120;
        var STORAGE_KEY = 'rwandaemr.radiologyQueue.view';
        var dataUrl = '${ openmrsContextPath }/ws/rest/v1/rwandaemr/radiologyqueue';
        var pageUrl = '${ ui.pageLink("rwandaemr", "radiology/radiologyQueue") }';
        var patientOrdersUrl = '${ ui.pageLink("rwandaemr", "patient/radiologyOrders") }';
        var orderUrl = '${ ui.pageLink("rwandaemr", "patient/radiologyOrder") }';
        var reportUrl = '${ ui.pageLink("rwandaemr", "patient/radiologyReport") }';
        var printUrl = '${ ui.pageLink("rwandaemr", "patient/radiologyReportPrint") }';
        var resendUrl = '${ openmrsContextPath }/ws/rest/v1/rwandaemr/radiologyqueue/resend';
        var startedWithUrlFilters = window.location.search.indexOf('modality=') !== -1;

        var COLUMNS = [
            { key: 'patientId', label: 'Patient ID' }, { key: 'patientName', label: 'Patient Name' },
            { key: 'ageSex', label: 'Age/Sex' }, { key: 'modality', label: 'Modality' },
            { key: 'study', label: 'Type of Study' }, { key: 'history', label: 'Clinical History' },
            { key: 'creatinine', label: 'Creatinine' },
            { key: 'requestedBy', label: 'Requested By' }, { key: 'requested', label: 'Time Requested' },
            { key: 'pacs', label: 'PACS Status' }, { key: 'processed', label: 'Processed' },
            { key: 'payment', label: 'Payment' }, { key: 'results', label: 'Results' }
        ];

        // Quick filters behind the summary cards
        var QUICK_FILTERS = {
            STAT: function(r) { return r.stat; },
            READY: function(r) { return r.readyForExam; },
            NOT_PROCESSED: function(r) { return !r.processed && r.pacsStatus !== 'CANCELLED'; },
            NOT_PAID: function(r) { return r.paymentStatus === 'NOT_PAID'; },
            NOT_SENT: function(r) { return r.pacsStatus === 'NOT_SENT'; },
            REPORTED: function(r) { return r.pacsStatus === 'REPORTED'; }
        };

        var rows = [];
        var knownKeys = null;
        var state = { sortKey: 'timeRequested', sortType: 'number', sortAscending: false, quickFilter: '', hiddenCols: [], alerts: false };
        var openRowKey = null;

        // ---------- helpers ----------
        var escapeHtml = function(value) {
            return value === null || value === undefined ? '' : String(value)
                .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
                .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
        };
        var pad = function(n) { return n < 10 ? '0' + n : '' + n; };
        var formatTime = function(millis) {
            if (!millis) { return ''; }
            var d = new Date(millis);
            return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes());
        };
        var formatDuration = function(minutes) {
            if (minutes < 60) { return minutes + ' min'; }
            var h = Math.floor(minutes / 60);
            if (h < 24) { return h + ' h ' + pad(minutes % 60) + ' min'; }
            return Math.floor(h / 24) + ' d ' + (h % 24) + ' h';
        };
        var rowKey = function(row) { return (row.orderIds || []).join('-'); };
        var waitingMinutes = function(r) {
            if (r.processed || r.pacsStatus === 'CANCELLED' || !r.timeRequested) { return null; }
            return Math.max(0, Math.floor((Date.now() - r.timeRequested) / 60000));
        };
        var storage = {
            load: function() { try { return JSON.parse(window.localStorage.getItem(STORAGE_KEY)) || null; } catch (e) { return null; } },
            save: function(value) { try { window.localStorage.setItem(STORAGE_KEY, JSON.stringify(value)); } catch (e) { /* storage unavailable */ } },
            clear: function() { try { window.localStorage.removeItem(STORAGE_KEY); } catch (e) { /* storage unavailable */ } }
        };

        var getFilters = function() {
            return {
                startDate: jq('#rq-start').val(),
                endDate: jq('#rq-end').val(),
                modality: jq('#rq-modality').val(),
                pacsStatus: jq('#rq-pacs').val(),
                paymentStatus: jq('#rq-payment').val(),
                processed: jq('#rq-processed').val(),
                query: jq.trim(jq('#rq-query').val())
            };
        };

        var saveView = function() {
            var f = getFilters();
            storage.save({
                sortKey: state.sortKey, sortType: state.sortType, sortAscending: state.sortAscending,
                quickFilter: state.quickFilter, hiddenCols: state.hiddenCols, alerts: state.alerts,
                modality: f.modality, pacsStatus: f.pacsStatus, paymentStatus: f.paymentStatus, processed: f.processed
            });
        };

        var restoreView = function() {
            var saved = storage.load();
            if (!saved) { return; }
            ['sortKey', 'sortType', 'sortAscending', 'quickFilter', 'alerts'].forEach(function(k) {
                if (saved[k] !== undefined) { state[k] = saved[k]; }
            });
            state.hiddenCols = saved.hiddenCols || [];
            // Filters given in the address take priority over the remembered ones
            if (!startedWithUrlFilters) {
                jq('#rq-modality').val(saved.modality || '');
                jq('#rq-pacs').val(saved.pacsStatus || '');
                jq('#rq-payment').val(saved.paymentStatus || '');
                jq('#rq-processed').val(saved.processed || '');
            }
        };

        var matchesFilters = function(row, f) {
            if (f.modality && row.modality !== f.modality) { return false; }
            if (f.pacsStatus && row.pacsStatus !== f.pacsStatus) { return false; }
            if (f.paymentStatus && row.paymentStatus !== f.paymentStatus) { return false; }
            if (f.processed === 'YES' && !row.processed) { return false; }
            if (f.processed === 'NO' && row.processed) { return false; }
            if (f.processed === 'READY' && !row.readyForExam) { return false; }
            if (f.query) {
                var text = [row.patientIdentifier, row.patientName, (row.orderNumbers || []).join(' '), row.typeOfStudy]
                    .join(' ').toLowerCase();
                if (text.indexOf(f.query.toLowerCase()) === -1) { return false; }
            }
            return true;
        };

        // STAT requests always come first, then the chosen column
        var compare = function(a, b) {
            if (a.stat !== b.stat) { return a.stat ? -1 : 1; }
            var x = a[state.sortKey], y = b[state.sortKey];
            var xEmpty = x === null || x === undefined || x === '';
            var yEmpty = y === null || y === undefined || y === '';
            if (xEmpty || yEmpty) { return xEmpty === yEmpty ? 0 : (xEmpty ? 1 : -1); }
            var result = state.sortType === 'number' ? (x - y) : String(x).localeCompare(String(y), undefined, { sensitivity: 'base' });
            return state.sortAscending ? result : -result;
        };

        var exportParams = function(f) {
            var p = { startDate: f.startDate, endDate: f.endDate, modality: f.modality, pacsStatus: f.pacsStatus,
                      paymentStatus: f.paymentStatus, processed: f.processed, query: f.query };
            var q = state.quickFilter;
            if (q === 'READY') { p.processed = 'READY'; }
            if (q === 'NOT_PROCESSED') { p.processed = 'NO'; }
            if (q === 'NOT_PAID') { p.paymentStatus = 'NOT_PAID'; }
            if (q === 'NOT_SENT') { p.pacsStatus = 'NOT_SENT'; }
            if (q === 'REPORTED') { p.pacsStatus = 'REPORTED'; }
            if (q === 'STAT') { p.stat = 'YES'; }
            return p;
        };

        var updateLinks = function() {
            var f = getFilters();
            var sep = pageUrl.indexOf('?') === -1 ? '?' : '&';
            if (window.history && window.history.replaceState) {
                window.history.replaceState(null, '', pageUrl + sep + jq.param(f));
            }
            jq('#rq-export').attr('href', pageUrl + sep + jq.param(exportParams(f)) + '&export=excel');
        };

        // ---------- rendering ----------
        var renderSummary = function(filtered) {
            var count = function(name) { return filtered.filter(QUICK_FILTERS[name]).length; };
            var cards = [
                { id: '', label: 'Studies', value: filtered.length, cls: '' },
                { id: 'STAT', label: 'STAT', value: count('STAT'), cls: 'rq-card-stat' },
                { id: 'READY', label: 'Ready for exam', value: count('READY'), cls: 'rq-card-ready' },
                { id: 'NOT_PROCESSED', label: 'Not processed', value: count('NOT_PROCESSED'), cls: '' },
                { id: 'NOT_PAID', label: 'Not paid', value: count('NOT_PAID'), cls: 'rq-card-unpaid' },
                { id: 'NOT_SENT', label: 'Not sent to PACS', value: count('NOT_SENT'), cls: '' },
                { id: 'REPORTED', label: 'Reported', value: count('REPORTED'), cls: '' }
            ];
            jq('#rq-summary').html(cards.map(function(c) {
                var active = (state.quickFilter || '') === c.id;
                return '<div class="rq-card ' + c.cls + (active ? ' rq-active' : '') + '" data-quick="' + c.id + '" title="' +
                    (c.id ? 'Show only: ' + c.label : 'Show all studies') + '"><span>' + c.label + '</span><strong>' + c.value + '</strong></div>';
            }).join(''));
        };

        var processedCell = function(r) {
            if (r.readyForExam) { return '<span class="rq-chip rq-ready">Ready for exam</span>'; }
            return '<span class="rq-chip ' + (r.processed ? 'rq-processed' : 'rq-not-processed') + '">' + escapeHtml(r.processedLabel) + '</span>';
        };

        var waitBadge = function(r) {
            var minutes = waitingMinutes(r);
            if (minutes === null) { return ''; }
            var cls = minutes >= WAIT_RED_MIN ? ' rq-wait-red' : (minutes >= WAIT_AMBER_MIN ? ' rq-wait-amber' : '');
            return '<div><span class="rq-wait' + cls + '" title="Waiting since the request">&#9201; ' + formatDuration(minutes) + '</span></div>';
        };

        // The time a step was reached; its tooltip says how long after the request, to follow the turnaround time
        var stepTime = function(r, millis) {
            if (!millis) { return ''; }
            var title = r.timeRequested ? ' title="' + formatDuration(Math.max(0, Math.floor((millis - r.timeRequested) / 60000))) +
                ' after the request"' : '';
            return '<div class="rq-muted rq-nowrap"' + title + '>' + formatTime(millis) + '</div>';
        };

        // When the study reached its PACS status
        var pacsStatusTime = function(r) {
            if (r.pacsStatus === 'SENT') { return r.sentAt; }
            if (r.pacsStatus === 'REPORTED') { return r.reportedAt; }
            if (r.pacsStatus === 'IMAGES_RECEIVED' || r.pacsStatus === 'STUDY_DONE_NO_LINK') { return r.imagesReceivedAt; }
            return null;
        };

        var iconLink = function(href, icon, label, attributes) {
            return '<a href="' + href + '" title="' + label + '" aria-label="' + label + '"' + (attributes || '') + '>' + icon + '</a>';
        };

        // Reports for modalities such as ultrasound are entered in OpenMRS by a radiologist, one report per test
        var testReportLinks = function(r, t, showName) {
            var links = [];
            if (t.reported) {
                links.push(iconLink(orderUrl + '?orderId=' + t.orderId, '&#128196;', 'View report'));
                links.push(iconLink(printUrl + '?orderId=' + t.orderId, '&#128424;', 'Print report', ' target="_blank" rel="noopener"'));
            }
            else if (t.hasDraft) {
                links.push('<span class="rq-draft" title="Only radiologists can see a draft">Draft</span>');
            }
            if (r.canEnterReport) {
                var returnUrl = window.location.pathname + window.location.search;
                links.push(iconLink(reportUrl + '?orderId=' + t.orderId + '&returnUrl=' + encodeURIComponent(returnUrl), '&#9998;',
                    t.reported ? 'Correct report' : (t.hasDraft ? 'Continue draft' : 'Enter report'), ' class="rq-enter-report"'));
            }
            if (links.length === 0) {
                return '';
            }
            return '<div class="rq-report-test">' + (showName ? '<span class="rq-test-name">' + escapeHtml(t.name) + '</span>' : '') +
                '<div class="rq-links">' + links.join('') + '</div></div>';
        };

        var resultLinks = function(r) {
            var links = [];
            if (r.imageUrl) {
                links.push(iconLink(escapeHtml(r.imageUrl), '&#128444;', 'View images', ' target="_blank" rel="noopener noreferrer" referrerpolicy="no-referrer"'));
            }
            if (r.manualReport) {
                var tests = r.tests || [];
                var perTest = tests.map(function(t) { return testReportLinks(r, t, tests.length > 1); }).join('');
                return '<div class="rq-links">' + links.join('') + '</div>' + perTest;
            }
            if (r.reportOrderId) {
                links.push(iconLink(orderUrl + '?orderId=' + r.reportOrderId, '&#128196;', 'View report'));
                links.push(iconLink(printUrl + '?orderId=' + r.reportOrderId, '&#128424;', 'Print report', ' target="_blank" rel="noopener"'));
            }
            return '<div class="rq-links">' + links.join('') + '</div>';
        };

        // The latest creatinine of the patient, shown for CT as contrast may be given
        var creatinineCell = function(r) {
            if (r.modality !== 'CT') {
                return '';
            }
            if (!r.creatinine) {
                return '<span class="rq-muted">No result</span>';
            }
            return '<span class="rq-creat' + (r.creatinineHigh ? ' rq-creat-high' : '') + '"' +
                (r.creatinineHigh ? ' title="Above the normal range"' : '') + '>' + escapeHtml(r.creatinine) +
                (r.creatinineHigh ? ' &#9650;' : '') + '</span>' +
                (r.creatinineDate ? '<div class="rq-muted rq-nowrap">' + formatTime(r.creatinineDate) + '</div>' : '');
        };

        // A study that never reached PACS can be sent again: only the message is sent, no order or bill is created
        var resendButton = function(r) {
            return r.canResend ? ' <button type="button" class="rq-resend" data-order-id="' + r.orderIds[0] + '"' +
                ' title="Send this study to PACS again. No new order or bill is created.">&#8635; Resend to PACS</button>' : '';
        };

        var resend = function(button) {
            button.prop('disabled', true).text('Sending...');
            jq.ajax({
                type: 'POST',
                url: resendUrl,
                data: { orderId: button.data('order-id') },
                dataType: 'json'
            }).done(function(result) {
                if (result.error) {
                    jq('#rq-message').html('<span class="rq-error">' + escapeHtml(result.error) + '</span>');
                    button.prop('disabled', false).html('&#8635; Resend to PACS');
                }
                else {
                    // Reloading the queue clears the message line, so the confirmation is shown as a toast
                    var sent = 'Sent to PACS as study ' + result.accessionNumber;
                    if (jq().toastmessage) {
                        jq().toastmessage('showSuccessToast', sent);
                    }
                    load(false);
                }
            }).fail(function(xhr) {
                jq('#rq-message').html('<span class="rq-error">Unable to send to PACS (HTTP ' + xhr.status + ')' +
                    (xhr.status == 401 || xhr.status == 403 ? ': your session may have expired or you lack permission' : '') + '</span>');
                button.prop('disabled', false).html('&#8635; Resend to PACS');
            });
        };

        var cell = function(col, html, extraClass) {
            var hidden = state.hiddenCols.indexOf(col) !== -1 ? ' rq-hidden' : '';
            return '<td data-col="' + col + '" class="' + (extraClass || '') + hidden + '">' + html + '</td>';
        };

        var render = function(newKeys) {
            var f = getFilters();
            var filtered = rows.filter(function(r) { return matchesFilters(r, f); });
            renderSummary(filtered);
            var quick = state.quickFilter && QUICK_FILTERS[state.quickFilter];
            var visible = quick ? filtered.filter(quick) : filtered;
            visible.sort(compare);

            jq('.rq-table th[data-col]').each(function() {
                jq(this).toggleClass('rq-hidden', state.hiddenCols.indexOf(jq(this).data('col')) !== -1);
            });
            jq('.rq-table th.rq-sortable').removeClass('rq-sorted').find('.rq-arrow').html('&#8597;');
            jq('.rq-table th[data-sort="' + state.sortKey + '"]').addClass('rq-sorted')
                .find('.rq-arrow').html(state.sortAscending ? '&#9650;' : '&#9660;');

            if (visible.length === 0) {
                jq('#rq-body').html('<tr><td colspan="14" class="rq-muted">No radiology requests match these filters.</td></tr>');
                return;
            }
            var html = [];
            visible.forEach(function(r, i) {
                var key = rowKey(r);
                var classes = [];
                if (newKeys && newKeys[key]) { classes.push('rq-new'); }
                if (r.stat) { classes.push('rq-row-stat'); }
                else if (r.readyForExam) { classes.push('rq-row-ready'); }
                var firstOrderId = (r.orderIds || [])[0];
                var study = (r.stat ? '<span class="rq-chip rq-stat">STAT</span>' : '') + escapeHtml(r.typeOfStudy) +
                    '<div class="rq-muted"><a href="' + orderUrl + '?orderId=' + firstOrderId + '">' +
                    escapeHtml((r.orderNumbers || []).join(', ')) + '</a></div>';
                var payment = '<span class="rq-chip rq-' + escapeHtml(r.paymentStatus) + '">' + escapeHtml(r.paymentStatusLabel) + '</span>' +
                    (r.timeOfPayment ? '<div class="rq-muted rq-nowrap">' + formatTime(r.timeOfPayment) + '</div>' : '');
                html.push('<tr class="' + classes.join(' ') + '" data-key="' + escapeHtml(key) + '">' +
                    cell('sno', i + 1) +
                    cell('patientId', escapeHtml(r.patientIdentifier), 'rq-nowrap') +
                    cell('patientName', '<a href="' + patientOrdersUrl + '?patientId=' + r.patientId + '">' + escapeHtml(r.patientName) + '</a>') +
                    cell('ageSex', escapeHtml(r.age === null || r.age === undefined ? '' : r.age) + ' ' + escapeHtml(r.sex), 'rq-nowrap') +
                    cell('modality', escapeHtml(r.modality)) +
                    cell('study', study, 'rq-study') +
                    cell('history', '<div class="rq-history" title="' + escapeHtml(r.clinicalHistory) + '">' + escapeHtml(r.clinicalHistory) + '</div>') +
                    cell('creatinine', creatinineCell(r)) +
                    cell('requestedBy', escapeHtml(r.requestingPhysician) + '<div class="rq-muted">' + escapeHtml(r.requestingDepartment) + '</div>') +
                    cell('requested', '<span class="rq-nowrap">' + formatTime(r.timeRequested) + '</span>' + waitBadge(r)) +
                    cell('pacs', '<span class="rq-chip rq-' + escapeHtml(r.pacsStatus) + '">' + escapeHtml(r.pacsStatusLabel) + '</span>' +
                        stepTime(r, pacsStatusTime(r)) + resendButton(r)) +
                    cell('processed', processedCell(r) + (r.processed ? stepTime(r, r.imagesReceivedAt || r.reportedAt) : '')) +
                    cell('payment', payment) +
                    cell('results', resultLinks(r)) +
                    '</tr>');
            });
            jq('#rq-body').html(html.join(''));
            window.setTimeout(function() { jq('#rq-body tr.rq-new').removeClass('rq-new'); }, 8000);
            if (openRowKey) { renderDrawer(findRow(openRowKey)); }
        };

        // ---------- detail panel ----------
        var findRow = function(key) {
            for (var i = 0; i < rows.length; i++) { if (rowKey(rows[i]) === key) { return rows[i]; } }
            return null;
        };

        var timelineItem = function(label, when, done, warn) {
            return '<li class="' + (done ? 'rq-done' : '') + (warn ? ' rq-warn' : '') + '"><strong>' + label + '</strong>' +
                (when ? '<div class="rq-when">' + when + '</div>' : '') + '</li>';
        };

        var renderDrawer = function(r) {
            if (!r) { closeDrawer(); return; }
            jq('#rq-drawer-title').html('<h4>' + escapeHtml(r.patientName) + '</h4><div class="rq-muted">' +
                escapeHtml(r.patientIdentifier) + ' &middot; ' + escapeHtml(r.age) + ' ' + escapeHtml(r.sex) + ' &middot; ' + escapeHtml(r.modality) + '</div>');
            var tests = (r.tests || []).map(function(t) {
                return '<li>' + (t.stat ? '<span class="rq-chip rq-stat">STAT</span>' : '') + escapeHtml(t.name) +
                    ' <a class="rq-muted" href="' + orderUrl + '?orderId=' + t.orderId + '">' + escapeHtml(t.orderNumber) + '</a></li>';
            }).join('');
            var paid = r.paymentStatus === 'PAID';
            var cancelled = r.pacsStatus === 'CANCELLED';
            var sent = r.pacsStatus !== 'NOT_SENT' && !cancelled;
            var wait = waitingMinutes(r);
            var timeline =
                timelineItem('Requested', formatTime(r.timeRequested) + (wait !== null ? ' &middot; waiting ' + formatDuration(wait) : ''), true, false) +
                timelineItem('Payment: ' + escapeHtml(r.paymentStatusLabel), formatTime(r.timeOfPayment), paid, r.paymentStatus === 'NOT_PAID') +
                timelineItem(cancelled ? 'Cancelled in PACS' : (sent ? 'Sent to PACS' : 'Not sent to PACS yet'), formatTime(r.sentAt), sent, cancelled) +
                timelineItem(r.imagesReceivedAt && !r.imageUrl ? 'Study done &ndash; no image link from PACS yet' : 'Images received',
                    formatTime(r.imagesReceivedAt), !!r.imagesReceivedAt, !!r.imagesReceivedAt && !r.imageUrl) +
                timelineItem('Reported' + (r.reportStatus ? ' (' + escapeHtml(r.reportStatus) + ')' : ''), formatTime(r.reportedAt), !!r.reportedAt, false);
            jq('#rq-drawer-body').html(
                '<div class="rq-section"><h5>' + ((r.tests || []).length > 1 ? 'Tests in this study' : 'Test') + '</h5><ul class="rq-tests">' + tests + '</ul></div>' +
                '<div class="rq-section"><h5>Status</h5>' +
                    '<span class="rq-chip rq-' + escapeHtml(r.pacsStatus) + '">' + escapeHtml(r.pacsStatusLabel) + '</span> ' + processedCell(r) +
                    resendButton(r) +
                    ' <span class="rq-chip rq-' + escapeHtml(r.paymentStatus) + '">' + escapeHtml(r.paymentStatusLabel) + '</span></div>' +
                '<div class="rq-section"><h5>Progress</h5><ul class="rq-timeline">' + timeline + '</ul></div>' +
                '<div class="rq-section"><h5>Clinical history</h5><p>' + (escapeHtml(r.clinicalHistory) || '<span class="rq-muted">None recorded</span>') + '</p></div>' +
                (r.reason ? '<div class="rq-section"><h5>Reason</h5><p>' + escapeHtml(r.reason) + '</p></div>' : '') +
                (r.modality === 'CT' ? '<div class="rq-section"><h5>Latest creatinine</h5>' + creatinineCell(r) + '</div>' : '') +
                '<div class="rq-section"><h5>Requested by</h5><p>' + escapeHtml(r.requestingPhysician) +
                    (r.requestingDepartment ? ' &middot; ' + escapeHtml(r.requestingDepartment) : '') + '</p></div>' +
                '<div class="rq-section"><h5>Results</h5>' + (r.imageUrl || r.reportOrderId || r.canEnterReport ? resultLinks(r) : '<span class="rq-muted">No results yet</span>') +
                    (r.manualReport && !r.reportOrderId ? '<div class="rq-muted">The report for this modality is entered in OpenMRS by a radiologist' +
                        (r.imagesReceivedAt ? '.' : ', once the images are received from PACS.') + '</div>' : '') + '</div>' +
                '<div class="rq-section"><a href="' + patientOrdersUrl + '?patientId=' + r.patientId + '">All radiology orders of this patient &rarr;</a></div>');
        };

        var openDrawer = function(key) {
            openRowKey = key;
            renderDrawer(findRow(key));
            jq('#rq-overlay').show();
            jq('#rq-drawer').addClass('rq-open').attr('aria-hidden', 'false');
        };
        var closeDrawer = function() {
            openRowKey = null;
            jq('#rq-overlay').hide();
            jq('#rq-drawer').removeClass('rq-open').attr('aria-hidden', 'true');
        };

        // ---------- alerts for new requests ----------
        var audioContext = null;
        var playChime = function() {
            try {
                var AudioCtx = window.AudioContext || window.webkitAudioContext;
                if (!AudioCtx) { return; }
                audioContext = audioContext || new AudioCtx();
                [880, 1175].forEach(function(freq, i) {
                    var osc = audioContext.createOscillator();
                    var gain = audioContext.createGain();
                    var start = audioContext.currentTime + i * 0.18;
                    osc.frequency.value = freq;
                    gain.gain.setValueAtTime(0.0001, start);
                    gain.gain.exponentialRampToValueAtTime(0.25, start + 0.02);
                    gain.gain.exponentialRampToValueAtTime(0.0001, start + 0.16);
                    osc.connect(gain);
                    gain.connect(audioContext.destination);
                    osc.start(start);
                    osc.stop(start + 0.17);
                });
            } catch (e) { /* sound not available */ }
        };
        var notifyNewRequests = function(newRows) {
            if (!state.alerts || newRows.length === 0) { return; }
            playChime();
            if (window.Notification && Notification.permission === 'granted') {
                newRows.slice(0, 3).forEach(function(r) {
                    try {
                        new Notification('New ' + (r.stat ? 'STAT ' : '') + (r.modality || 'radiology') + ' request', {
                            body: (r.patientName || '') + ' - ' + (r.typeOfStudy || ''), tag: 'rq-' + rowKey(r)
                        });
                    } catch (e) { /* notifications not available */ }
                });
            }
        };
        var updateAlertsButton = function() {
            jq('#rq-alerts').toggleClass('rq-alert-on', state.alerts).html('&#128276; Alerts: ' + (state.alerts ? 'On' : 'Off'));
        };

        // ---------- column chooser ----------
        var renderColumnsMenu = function() {
            jq('#rq-columns-menu').html(COLUMNS.map(function(c) {
                var checked = state.hiddenCols.indexOf(c.key) === -1 ? ' checked="checked"' : '';
                return '<label><input type="checkbox" data-col="' + c.key + '"' + checked + '/> ' + c.label + '</label>';
            }).join(''));
        };

        // ---------- loading ----------
        var load = function(resetKnownRows) {
            var f = getFilters();
            updateLinks();
            jq.ajax({
                type: 'GET',
                url: dataUrl,
                data: { startDate: f.startDate, endDate: f.endDate },
                dataType: 'json',
                cache: false
            }).done(function(result) {
                jq('#rq-message').html(result.error ? '<span class="rq-error">' + escapeHtml(result.error) + '</span>' : '');
                rows = result.rows || [];
                var newKeys = {};
                var currentKeys = {};
                var newRows = [];
                rows.forEach(function(r) {
                    var key = rowKey(r);
                    r.processedRank = r.readyForExam ? 0 : (r.processed ? 2 : 1);
                    currentKeys[key] = true;
                    if (knownKeys && !resetKnownRows && !knownKeys[key]) {
                        newKeys[key] = true;
                        newRows.push(r);
                    }
                });
                knownKeys = currentKeys;
                render(newKeys);
                notifyNewRequests(newRows);
                jq('#rq-updated').text('Last updated ' + formatTime(result.generatedAt || Date.now()));
            }).fail(function(xhr) {
                jq('#rq-message').html('<span class="rq-error">Unable to load the radiology queue (HTTP ' + xhr.status + ')' +
                    (xhr.status == 401 || xhr.status == 403 ? ': your session may have expired' : '') + '</span>');
            });
        };

        jq(function() {
            restoreView();
            updateAlertsButton();
            renderColumnsMenu();

            jq('.rq-table th.rq-sortable').click(function() {
                var key = jq(this).data('sort');
                if (key === state.sortKey) {
                    state.sortAscending = !state.sortAscending;
                }
                else {
                    state.sortKey = key;
                    state.sortType = jq(this).data('type') === 'number' ? 'number' : 'text';
                    state.sortAscending = state.sortType !== 'number' || key === 'processedRank';
                }
                saveView();
                render(null);
            });
            jq('#rq-summary').on('click', '.rq-card', function() {
                var quick = jq(this).data('quick') || '';
                state.quickFilter = state.quickFilter === quick ? '' : quick;
                saveView();
                updateLinks();
                render(null);
            });
            jq('#rq-body').on('click', 'tr[data-key]', function(e) {
                if (jq(e.target).closest('a, button').length) { return; }
                openDrawer(String(jq(this).data('key')));
            });
            jq('#rq-overlay, #rq-drawer-close').click(closeDrawer);
            jq(document).on('click', '.rq-resend', function(e) {
                e.stopPropagation();
                resend(jq(this));
            });
            jq(document).keydown(function(e) { if (e.key === 'Escape') { closeDrawer(); jq('#rq-columns-menu').hide(); } });

            jq('#rq-columns-button').click(function(e) { e.stopPropagation(); jq('#rq-columns-menu').toggle(); });
            jq('#rq-columns-menu').on('click', function(e) { e.stopPropagation(); });
            jq('#rq-columns-menu').on('change', 'input', function() {
                var col = jq(this).data('col');
                state.hiddenCols = state.hiddenCols.filter(function(c) { return c !== col; });
                if (!this.checked) { state.hiddenCols.push(col); }
                saveView();
                render(null);
            });
            jq(document).click(function() { jq('#rq-columns-menu').hide(); });

            jq('#rq-alerts').click(function() {
                state.alerts = !state.alerts;
                if (state.alerts) {
                    playChime();   // also confirms sound works, and unlocks audio after this click
                    if (window.Notification && Notification.permission === 'default') {
                        Notification.requestPermission();
                    }
                }
                saveView();
                updateAlertsButton();
            });
            jq('#rq-reset').click(function() {
                storage.clear();
                state = { sortKey: 'timeRequested', sortType: 'number', sortAscending: false, quickFilter: '', hiddenCols: [], alerts: false };
                jq('#rq-modality, #rq-pacs, #rq-payment, #rq-processed').val('');
                jq('#rq-query').val('');
                updateAlertsButton();
                renderColumnsMenu();
                updateLinks();
                render(null);
            });

            jq('#rq-start, #rq-end').change(function() { load(true); });
            jq('#rq-modality, #rq-pacs, #rq-payment, #rq-processed').change(function() { saveView(); updateLinks(); render(null); });
            jq('#rq-query').on('input', function() { updateLinks(); render(null); });
            jq('#rq-refresh').click(function() { load(false); });

            load(true);
            window.setInterval(function() { load(false); }, REFRESH_MS);
        });
    })();
</script>

<% } %>
