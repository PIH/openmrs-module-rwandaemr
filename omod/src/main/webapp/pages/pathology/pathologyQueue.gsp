<%
    ui.decorateWith("appui", "standardEmrPage")
    def openmrsContextPath = contextPath ? (contextPath.startsWith("/") ? contextPath : "/" + contextPath) : ""
%>

<script type="text/javascript">
    var breadcrumbs = [
        { icon: "icon-home", link: '${ openmrsContextPath }/index.htm' },
        { label: "Pathology Queue" }
    ];
</script>

<% if (!authorized) { %>
    <div class="pq-empty">You do not have permission to view the pathology queue.</div>
<% } else { %>

<style>
    /* The queue is wide, so on this page it uses the whole screen, as the radiology queue does */
    body { max-width: none !important; width: auto !important; margin: 10px 12px !important; }
    .pq-header { display: flex; align-items: flex-end; justify-content: space-between; gap: 16px; margin: 8px 0 12px; flex-wrap: wrap; }
    .pq-title { margin: 0; font-size: 28px; color: #263238; }
    .pq-subtitle { margin: 4px 0 0; color: #607d8b; font-size: 13px; }

    /* Summary cards double as quick filters */
    .pq-summary { display: flex; gap: 8px; flex-wrap: wrap; }
    .pq-card { border: 1px solid #d9e2e7; background: #fff; padding: 7px 12px; border-radius: 6px; text-align: right; min-width: 84px;
               cursor: pointer; transition: box-shadow .15s, transform .15s; user-select: none; }
    .pq-card:hover { box-shadow: 0 2px 8px rgba(0,0,0,.08); transform: translateY(-1px); }
    .pq-card strong { display: block; font-size: 22px; color: #263238; line-height: 1.2; }
    .pq-card span { font-size: 12px; color: #607d8b; white-space: nowrap; }
    .pq-card.pq-active { border-color: #00473f; box-shadow: inset 0 0 0 2px #00473f; }
    .pq-card.pq-card-unpaid strong, .pq-card.pq-card-rejected strong { color: #c62828; }
    .pq-card.pq-card-lab strong { color: #0b5394; }

    .pq-panel { border: 1px solid #d9e2e7; background: #fff; padding: 10px 12px; margin-bottom: 10px; border-radius: 6px; }
    .pq-filters { display: grid; grid-template-columns: 140px 140px 160px 140px 220px minmax(170px, 1fr); gap: 8px; align-items: end; }
    .pq-field label { display: block; margin-bottom: 3px; font-size: 11px; font-weight: bold; color: #455a64; text-transform: uppercase; }
    .pq-field input, .pq-field select { width: 100%; box-sizing: border-box; min-height: 32px; border: 1px solid #b0bec5; border-radius: 3px; padding: 4px 7px; background: #fff; }
    .pq-toolbar { display: flex; gap: 8px; margin-top: 8px; flex-wrap: wrap; align-items: center; }
    .pq-toolbar button, .pq-toolbar a { min-height: 32px; white-space: nowrap; }
    .pq-toolbar label { display: flex; align-items: center; gap: 6px; font-weight: normal; cursor: pointer; }
    .pq-toolbar label input { margin: 0; }
    .pq-spacer { flex: 1; }

    .pq-status-line { display: flex; justify-content: space-between; color: #607d8b; font-size: 12px; margin: 0 0 6px; }
    .pq-error { color: #9f1d1d; font-weight: bold; }

    /* The table scrolls with the page; its header stays at the top of the screen while scrolling */
    .pq-table-wrap { border: 1px solid #d9e2e7; background: #fff; border-radius: 6px; }
    table.pq-table { width: 100%; border-collapse: separate; border-spacing: 0; }
    .pq-table th { position: sticky; top: 0; z-index: 2; background: #f3f6f8; color: #455a64; font-size: 11px; text-transform: uppercase;
                   border-bottom: 1px solid #d9e2e7; padding: 8px 7px; text-align: left; white-space: nowrap; }
    .pq-table th.pq-sortable { cursor: pointer; user-select: none; }
    .pq-table th.pq-sortable:hover { background: #e6edf1; }
    .pq-table th .pq-arrow { color: #90a4ae; margin-left: 3px; }
    .pq-table th.pq-sorted .pq-arrow { color: #263238; }
    .pq-table td { border-bottom: 1px solid #edf2f5; padding: 7px; vertical-align: top; color: #263238; font-size: 13px; }
    .pq-table tbody tr { cursor: pointer; }
    .pq-table tbody tr:hover td { background: #f5f9fb; }
    .pq-table tbody tr td:first-child { border-left: 4px solid transparent; }
    .pq-table tbody tr.pq-row-ordered td:first-child { border-left-color: #f9a825; }
    .pq-table tbody tr.pq-row-lab td:first-child { border-left-color: #1e88e5; }
    .pq-table tbody tr.pq-new td { background: #fff8e1; }
    .pq-muted { color: #78909c; font-size: 12px; }
    .pq-nowrap { white-space: nowrap; }
    .pq-accession { font-weight: bold; font-family: monospace; font-size: 13.5px; white-space: nowrap; }

    .pq-chip { display: inline-block; padding: 3px 8px; border-radius: 12px; font-weight: bold; font-size: 11.5px; white-space: nowrap; color: #455a64; background: #eceff1; }
    .pq-PAID, .pq-SIGNED_OUT { color: #1b5e20; background: #dff0d8; }
    .pq-PARTIALLY_PAID, .pq-RECEIVED, .pq-GROSSED, .pq-PROCESSED, .pq-ADDITIONAL_STAINS { color: #0b5394; background: #d9edf7; }
    .pq-NOT_PAID, .pq-REJECTED, .pq-CANCELLED { color: #9f1d1d; background: #f8d7da; }
    .pq-NOT_BILLED, .pq-ORDERED { color: #8a6d3b; background: #fcf8e3; }
    .pq-urgent { color: #fff; background: #e53935; font-size: 10.5px; padding: 2px 6px; margin-right: 4px; vertical-align: 1px; }
    .pq-card.pq-card-urgent strong { color: #c62828; }
    .pq-table tbody tr.pq-row-urgent td:first-child { border-left-color: #e53935; }

    .pq-action { display: inline-block; margin: 0 4px 4px 0; padding: 2px 10px; font-size: 11.5px; font-weight: bold; min-height: 0; line-height: 1.6;
                 color: #00473f; background: #fff; border: 1px solid #00473f; border-radius: 12px; cursor: pointer; white-space: nowrap; }
    .pq-action:hover { background: #e0f2f1; }
    .pq-action.pq-action-reject { color: #9f1d1d; border-color: #9f1d1d; }
    .pq-action.pq-action-reject:hover { background: #f8d7da; }
    .pq-action[disabled] { color: #78909c; border-color: #b0bec5; background: #eceff1; cursor: wait; }

    /* Detail panel */
    .pq-overlay { display: none; position: fixed; inset: 0; background: rgba(38,50,56,.35); z-index: 1000; }
    .pq-drawer { position: fixed; top: 0; right: 0; height: 100%; width: 480px; max-width: 95vw; background: #fff; z-index: 1001;
                 box-shadow: -6px 0 24px rgba(0,0,0,.18); transform: translateX(105%); transition: transform .2s ease-out; overflow-y: auto; }
    .pq-drawer.pq-open { transform: translateX(0); }
    .pq-drawer-head { position: sticky; top: 0; background: #00473f; color: #fff; padding: 14px 16px; display: flex; justify-content: space-between; align-items: flex-start; gap: 10px; }
    .pq-drawer-head h4 { margin: 0; font-size: 18px; color: #fff; }
    .pq-drawer-head .pq-muted { color: #cfe3df; }
    .pq-drawer-close { background: transparent; border: 0; color: #fff; font-size: 24px; line-height: 1; cursor: pointer; padding: 0 4px; }
    .pq-drawer-body { padding: 14px 16px 24px; }
    .pq-section { margin-bottom: 16px; }
    .pq-section h5 { margin: 0 0 6px; font-size: 12px; text-transform: uppercase; color: #607d8b; letter-spacing: .4px; }
    .pq-section p { margin: 0; white-space: pre-wrap; }
    .pq-list { list-style: none; padding: 0; margin: 0; }
    .pq-list li { padding: 5px 0; border-bottom: 1px solid #edf2f5; }
    .pq-timeline { list-style: none; padding: 0; margin: 0; }
    .pq-timeline li { position: relative; padding: 0 0 12px 22px; }
    .pq-timeline li:before { content: ""; position: absolute; left: 5px; top: 5px; width: 10px; height: 10px; border-radius: 50%; background: #43a047; }
    .pq-timeline li:after { content: ""; position: absolute; left: 9px; top: 17px; bottom: 0; width: 2px; background: #eceff1; }
    .pq-timeline li:last-child:after { display: none; }
    .pq-timeline li.pq-warn:before { background: #e53935; }
    .pq-timeline .pq-when { color: #78909c; font-size: 12px; }
    .pq-warning { border: 1px solid #f0c36d; background: #fcf8e3; color: #8a6d3b; padding: 8px 10px; border-radius: 4px; margin-bottom: 8px; }
    .pq-reject-form { display: none; margin-top: 8px; }
    .pq-reject-form textarea { width: 100%; box-sizing: border-box; min-height: 60px; border: 1px solid #b0bec5; border-radius: 3px; padding: 6px; }

    .pq-empty { border: 1px solid #d9e2e7; background: #fff; padding: 18px; color: #455a64; }

    @media (prefers-reduced-motion: reduce) { .pq-drawer, .pq-card { transition: none; } }
    @media (max-width: 1200px) { .pq-filters { grid-template-columns: repeat(2, minmax(140px, 1fr)); } }
</style>

<div class="pq-page">
    <div class="pq-header">
        <div>
            <h3 class="pq-title">Pathology Queue</h3>
            <p class="pq-subtitle">Pathology requests, where each is in the lab and whether it is paid. Updates every 30 seconds. Click a card to filter, a row for details.</p>
        </div>
        <div class="pq-summary" id="pq-summary"></div>
    </div>

    <div class="pq-panel">
        <div class="pq-filters">
            <div class="pq-field">
                <label for="pq-start">Requested from</label>
                <input id="pq-start" type="date" value="${ ui.encodeHtmlAttribute(queueRequest.startDate) }"/>
            </div>
            <div class="pq-field">
                <label for="pq-end">To</label>
                <input id="pq-end" type="date" value="${ ui.encodeHtmlAttribute(queueRequest.endDate) }"/>
            </div>
            <div class="pq-field">
                <label for="pq-status">Sample status</label>
                <select id="pq-status">
                    <option value="">All</option>
                    <% statuses.each { s -> %>
                        <option value="${ s.name() }" ${ queueRequest.status == s.name() ? 'selected="selected"' : '' }>${ s.label }</option>
                    <% } %>
                    <option value="CANCELLED" ${ queueRequest.status == 'CANCELLED' ? 'selected="selected"' : '' }>Cancelled</option>
                </select>
            </div>
            <div class="pq-field">
                <label for="pq-payment">Payment status</label>
                <select id="pq-payment">
                    <option value="">All</option>
                    <% paymentStatuses.each { s -> %>
                        <option value="${ s.name() }" ${ queueRequest.paymentStatus == s.name() ? 'selected="selected"' : '' }>${ s.label }</option>
                    <% } %>
                </select>
            </div>
            <div class="pq-field">
                <label for="pq-test">Order type</label>
                <select id="pq-test">
                    <option value="">All</option>
                    <% tests.each { t -> %>
                        <option value="${ ui.encodeHtmlAttribute(t) }" ${ queueRequest.test == t ? 'selected="selected"' : '' }>${ ui.encodeHtmlContent(t) }</option>
                    <% } %>
                </select>
            </div>
            <div class="pq-field">
                <label for="pq-query">Accession number, patient ID, name or hospital</label>
                <input id="pq-query" type="text" value="${ ui.encodeHtmlAttribute(queueRequest.query) }"/>
            </div>
        </div>
        <div class="pq-toolbar">
            <button type="button" id="pq-refresh">Refresh</button>
            <label title="Cases can stay in the lab for weeks: show those still in the lab even if they were requested before these dates">
                <input type="checkbox" id="pq-in-lab" ${ queueRequest.includeCasesInLab ? 'checked="checked"' : '' }/> Include earlier cases still in the lab
            </label>
            <button type="button" id="pq-reset" title="Clear filters and sort">Reset view</button>
            <span class="pq-spacer"></span>
            <a class="button" id="pq-export" href="#">Export to Excel</a>
        </div>
    </div>

    <div class="pq-status-line">
        <span id="pq-message"></span>
        <span id="pq-updated"></span>
    </div>

    <div class="pq-table-wrap">
        <table class="pq-table">
            <thead>
                <tr>
                    <th>No</th>
                    <th class="pq-sortable" data-sort="accessionNumber">Accession<span class="pq-arrow">&#8597;</span></th>
                    <th class="pq-sortable" data-sort="patientIdentifier">Patient ID<span class="pq-arrow">&#8597;</span></th>
                    <th class="pq-sortable" data-sort="patientName">Names<span class="pq-arrow">&#8597;</span></th>
                    <th class="pq-sortable" data-sort="age" data-type="number">Age/Sex<span class="pq-arrow">&#8597;</span></th>
                    <th class="pq-sortable" data-sort="hospitalOfOrigin">Hospital of origin<span class="pq-arrow">&#8597;</span></th>
                    <th class="pq-sortable" data-sort="testsText">Order type<span class="pq-arrow">&#8597;</span></th>
                    <th class="pq-sortable" data-sort="timeRequested" data-type="number">Requested<span class="pq-arrow">&#8597;</span></th>
                    <th class="pq-sortable" data-sort="paymentStatusLabel">Payment<span class="pq-arrow">&#8597;</span></th>
                    <th class="pq-sortable" data-sort="statusRank" data-type="number">Sample status<span class="pq-arrow">&#8597;</span></th>
                    <th>Next step</th>
                </tr>
            </thead>
            <tbody id="pq-body"></tbody>
        </table>
    </div>
</div>

<div class="pq-overlay" id="pq-overlay"></div>
<aside class="pq-drawer" id="pq-drawer" aria-hidden="true">
    <div class="pq-drawer-head">
        <div id="pq-drawer-title"></div>
        <button type="button" class="pq-drawer-close" id="pq-drawer-close" aria-label="Close">&times;</button>
    </div>
    <div class="pq-drawer-body" id="pq-drawer-body"></div>
</aside>

<script type="text/javascript">
    (function() {
        var REFRESH_MS = 30000;
        var STORAGE_KEY = 'rwandaemr.pathologyQueue.view';
        var dataUrl = '${ openmrsContextPath }/ws/rest/v1/rwandaemr/pathologyqueue';
        var statusUrl = '${ openmrsContextPath }/ws/rest/v1/rwandaemr/pathologyqueue/status';
        var pageUrl = '${ ui.pageLink("rwandaemr", "pathology/pathologyQueue") }';
        var dashboardUrl = '${ ui.pageLink("coreapps", "clinicianfacing/patient") }';
        var requestFormUrl = '${ ui.pageLink("htmlformentryui", "htmlform/viewEncounterWithHtmlForm") }';
        var startedWithUrlFilters = window.location.search.indexOf('status=') !== -1;
        var STATUS_ORDER = ['ORDERED', 'RECEIVED', 'GROSSED', 'PROCESSED', 'ADDITIONAL_STAINS', 'SIGNED_OUT', 'REJECTED', 'CANCELLED'];

        // Quick filters behind the summary cards
        var QUICK_FILTERS = {
            URGENT: function(r) { return r.urgent && r.status !== 'CANCELLED'; },
            ORDERED: function(r) { return r.status === 'ORDERED'; },
            IN_LAB: function(r) { return r.inLab; },
            NOT_PAID: function(r) { return r.paymentStatus === 'NOT_PAID' && r.status !== 'CANCELLED'; },
            REJECTED: function(r) { return r.status === 'REJECTED'; },
            SIGNED_OUT: function(r) { return r.status === 'SIGNED_OUT'; }
        };

        var rows = [];
        var knownKeys = null;
        var DEFAULT_STATE = { sortKey: 'timeRequested', sortType: 'number', sortAscending: false, quickFilter: '' };
        var state = jq.extend({}, DEFAULT_STATE);
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
        var storage = {
            load: function() { try { return JSON.parse(window.localStorage.getItem(STORAGE_KEY)) || null; } catch (e) { return null; } },
            save: function(value) { try { window.localStorage.setItem(STORAGE_KEY, JSON.stringify(value)); } catch (e) { /* storage unavailable */ } },
            clear: function() { try { window.localStorage.removeItem(STORAGE_KEY); } catch (e) { /* storage unavailable */ } }
        };

        var getFilters = function() {
            return {
                startDate: jq('#pq-start').val(),
                endDate: jq('#pq-end').val(),
                status: jq('#pq-status').val(),
                paymentStatus: jq('#pq-payment').val(),
                test: jq('#pq-test').val(),
                query: jq.trim(jq('#pq-query').val()),
                inLab: jq('#pq-in-lab').is(':checked') ? 'YES' : 'NO'
            };
        };

        var saveView = function() {
            var f = getFilters();
            storage.save({ sortKey: state.sortKey, sortType: state.sortType, sortAscending: state.sortAscending,
                quickFilter: state.quickFilter, status: f.status, paymentStatus: f.paymentStatus, test: f.test });
        };

        var restoreView = function() {
            var saved = storage.load();
            if (!saved) { return; }
            ['sortKey', 'sortType', 'sortAscending', 'quickFilter'].forEach(function(k) {
                if (saved[k] !== undefined) { state[k] = saved[k]; }
            });
            // Filters given in the address take priority over the remembered ones
            if (!startedWithUrlFilters) {
                jq('#pq-status').val(saved.status || '');
                jq('#pq-payment').val(saved.paymentStatus || '');
                jq('#pq-test').val(saved.test || '');
            }
        };

        var matchesFilters = function(row, f) {
            if (f.status && row.status !== f.status) { return false; }
            if (f.paymentStatus && row.paymentStatus !== f.paymentStatus) { return false; }
            if (f.test && (row.tests || []).indexOf(f.test) === -1) { return false; }
            if (f.query) {
                var text = [row.accessionNumber, row.patientIdentifier, row.patientName, (row.orderNumbers || []).join(' '),
                    row.hospitalOfOrigin].join(' ').toLowerCase();
                if (text.indexOf(f.query.toLowerCase()) === -1) { return false; }
            }
            return true;
        };

        // Urgent requests always come first, then the chosen column
        var compare = function(a, b) {
            if (a.urgent !== b.urgent) { return a.urgent ? -1 : 1; }
            var x = a[state.sortKey], y = b[state.sortKey];
            var xEmpty = x === null || x === undefined || x === '';
            var yEmpty = y === null || y === undefined || y === '';
            if (xEmpty || yEmpty) { return xEmpty === yEmpty ? 0 : (xEmpty ? 1 : -1); }
            var result = state.sortType === 'number' ? (x - y) : String(x).localeCompare(String(y), undefined, { sensitivity: 'base', numeric: true });
            return state.sortAscending ? result : -result;
        };

        var exportParams = function(f) {
            var p = jq.extend({}, f);
            var q = state.quickFilter;
            if (q === 'ORDERED' || q === 'REJECTED' || q === 'SIGNED_OUT') { p.status = q; }
            if (q === 'NOT_PAID') { p.paymentStatus = 'NOT_PAID'; }
            if (q === 'URGENT') { p.urgent = 'YES'; }
            return p;
        };

        var updateLinks = function() {
            var f = getFilters();
            var sep = pageUrl.indexOf('?') === -1 ? '?' : '&';
            if (window.history && window.history.replaceState) {
                window.history.replaceState(null, '', pageUrl + sep + jq.param(f));
            }
            jq('#pq-export').attr('href', pageUrl + sep + jq.param(exportParams(f)) + '&export=excel');
        };

        // ---------- rendering ----------
        var renderSummary = function(filtered) {
            var count = function(name) { return filtered.filter(QUICK_FILTERS[name]).length; };
            var cards = [
                { id: '', label: 'Requests', value: filtered.length, cls: '', title: 'Pathology requests. Click to show all.' },
                { id: 'URGENT', label: 'Urgent', value: count('URGENT'), cls: 'pq-card-urgent' },
                { id: 'ORDERED', label: 'Awaiting specimen', value: count('ORDERED'), cls: '' },
                { id: 'IN_LAB', label: 'In the lab', value: count('IN_LAB'), cls: 'pq-card-lab' },
                { id: 'NOT_PAID', label: 'Not paid', value: count('NOT_PAID'), cls: 'pq-card-unpaid' },
                { id: 'REJECTED', label: 'Rejected', value: count('REJECTED'), cls: 'pq-card-rejected' },
                { id: 'SIGNED_OUT', label: 'Signed out', value: count('SIGNED_OUT'), cls: '' }
            ];
            jq('#pq-summary').html(cards.map(function(c) {
                var active = (state.quickFilter || '') === c.id;
                return '<div class="pq-card ' + c.cls + (active ? ' pq-active' : '') + '" data-quick="' + c.id + '" title="' +
                    (c.title || 'Show only: ' + c.label) + '"><span>' + c.label + '</span><strong>' + c.value + '</strong></div>';
            }).join(''));
        };

        var statusChip = function(r) {
            return '<span class="pq-chip pq-' + escapeHtml(r.status) + '">' + escapeHtml(r.statusLabel) + '</span>';
        };
        var paymentChip = function(r) {
            return '<span class="pq-chip pq-' + escapeHtml(r.paymentStatus) + '">' + escapeHtml(r.paymentStatusLabel) + '</span>';
        };

        // The steps the user may record next.  Rejecting asks for the reason in the detail panel.
        var actionButtons = function(r) {
            return (r.actions || []).map(function(a) {
                var reject = a.status === 'REJECTED';
                return '<button type="button" class="pq-action' + (reject ? ' pq-action-reject' : '') + '" data-key="' + r.key +
                    '" data-status="' + escapeHtml(a.status) + '">' + (reject ? 'Reject&hellip;' : escapeHtml(a.label)) + '</button>';
            }).join('');
        };

        var render = function(newKeys) {
            var f = getFilters();
            var filtered = rows.filter(function(r) { return matchesFilters(r, f); });
            renderSummary(filtered);
            var quick = state.quickFilter && QUICK_FILTERS[state.quickFilter];
            var visible = quick ? filtered.filter(quick) : filtered;
            visible.sort(compare);

            jq('.pq-table th.pq-sortable').removeClass('pq-sorted').find('.pq-arrow').html('&#8597;');
            jq('.pq-table th[data-sort="' + state.sortKey + '"]').addClass('pq-sorted')
                .find('.pq-arrow').html(state.sortAscending ? '&#9650;' : '&#9660;');

            if (visible.length === 0) {
                jq('#pq-body').html('<tr><td colspan="11" class="pq-muted">No pathology requests match these filters.</td></tr>');
                return;
            }
            var html = visible.map(function(r, i) {
                var classes = [];
                if (newKeys && newKeys[r.key]) { classes.push('pq-new'); }
                if (r.urgent) { classes.push('pq-row-urgent'); }
                else if (r.status === 'ORDERED') { classes.push('pq-row-ordered'); }
                else if (r.inLab) { classes.push('pq-row-lab'); }
                return '<tr class="' + classes.join(' ') + '" data-key="' + r.key + '">' +
                    '<td>' + (i + 1) + '</td>' +
                    '<td><span class="pq-accession">' + (escapeHtml(r.accessionNumber) || '<span class="pq-muted">&ndash;</span>') + '</span></td>' +
                    '<td class="pq-nowrap">' + escapeHtml(r.patientIdentifier) + '</td>' +
                    '<td><a href="' + dashboardUrl + '?patientId=' + escapeHtml(r.patientUuid) + '">' + escapeHtml(r.patientName) + '</a></td>' +
                    '<td class="pq-nowrap">' + escapeHtml(r.age === null || r.age === undefined ? '' : r.age) + ' ' + escapeHtml(r.sex) + '</td>' +
                    '<td>' + escapeHtml(r.hospitalOfOrigin) + '</td>' +
                    '<td>' + (r.urgent ? '<span class="pq-chip pq-urgent">URGENT</span>' : '') + (r.tests || []).map(escapeHtml).join('<br/>') + '</td>' +
                    '<td><span class="pq-nowrap">' + formatTime(r.timeRequested) + '</span><div class="pq-muted">' + escapeHtml(r.requestingPhysician) + '</div></td>' +
                    '<td>' + paymentChip(r) + (r.timeOfPayment ? '<div class="pq-muted pq-nowrap">' + formatTime(r.timeOfPayment) + '</div>' : '') + '</td>' +
                    '<td>' + statusChip(r) + (r.statusChangedAt ? '<div class="pq-muted pq-nowrap">' + formatTime(r.statusChangedAt) + '</div>' : '') + '</td>' +
                    '<td>' + actionButtons(r) + '</td>' +
                    '</tr>';
            });
            jq('#pq-body').html(html.join(''));
            window.setTimeout(function() { jq('#pq-body tr.pq-new').removeClass('pq-new'); }, 8000);
            if (openRowKey) { renderDrawer(findRow(openRowKey)); }
        };

        // ---------- detail panel ----------
        var findRow = function(key) {
            for (var i = 0; i < rows.length; i++) { if (String(rows[i].key) === String(key)) { return rows[i]; } }
            return null;
        };

        var renderDrawer = function(r) {
            if (!r) { closeDrawer(); return; }
            jq('#pq-drawer-title').html('<h4>' + escapeHtml(r.patientName) + '</h4><div class="pq-muted">' +
                escapeHtml(r.patientIdentifier) + ' &middot; ' + escapeHtml(r.age) + ' ' + escapeHtml(r.sex) +
                (r.accessionNumber ? ' &middot; ' + escapeHtml(r.accessionNumber) : '') + '</div>');
            var tests = (r.tests || []).map(function(t, i) {
                return '<li>' + escapeHtml(t) + ' <span class="pq-muted">' + escapeHtml((r.orderNumbers || [])[i]) + '</span></li>';
            }).join('');
            var specimens = (r.specimens || []).map(function(s) { return '<li>' + escapeHtml(s) + '</li>'; }).join('');
            var timeline = '<li><strong>Requested</strong><div class="pq-when">' + formatTime(r.timeRequested) +
                (r.requestingPhysician ? ' &middot; ' + escapeHtml(r.requestingPhysician) : '') + '</div></li>' +
                (r.history || []).map(function(h) {
                    return '<li class="' + (h.status === 'REJECTED' ? 'pq-warn' : '') + '"><strong>' + escapeHtml(h.statusLabel) + '</strong>' +
                        (h.reason ? '<div>' + escapeHtml(h.reason) + '</div>' : '') +
                        '<div class="pq-when">' + formatTime(h.date) + (h.by ? ' &middot; ' + escapeHtml(h.by) : '') + '</div></li>';
                }).join('');
            var unpaid = r.paymentStatus !== 'PAID' && r.status === 'ORDERED';
            var canReject = (r.actions || []).some(function(a) { return a.status === 'REJECTED'; });
            var requestFormLink = requestFormUrl + '?patientId=' + encodeURIComponent(r.patientUuid) + '&encounter=' + encodeURIComponent(r.encounterUuid);
            jq('#pq-drawer-body').html(
                '<div class="pq-section"><h5>Status</h5>' + (r.urgent ? '<span class="pq-chip pq-urgent">URGENT</span>' : '') +
                    statusChip(r) + ' ' + paymentChip(r) +
                    (r.rejectionReason ? '<p class="pq-error" style="margin-top:6px">Rejected: ' + escapeHtml(r.rejectionReason) + '</p>' : '') + '</div>' +
                ((r.actions || []).length ? '<div class="pq-section"><h5>Next step</h5>' +
                    (unpaid ? '<div class="pq-warning">Payment status: <strong>' + escapeHtml(r.paymentStatusLabel) + '</strong>. ' +
                        'The specimen can still be received.</div>' : '') +
                    actionButtons(r) +
                    (canReject ? '<div class="pq-reject-form" id="pq-reject-form"><label for="pq-reject-reason">Reason for rejecting the specimen</label>' +
                        '<textarea id="pq-reject-reason" maxlength="255" placeholder="For example: specimen not labelled, wrong fixative"></textarea>' +
                        '<button type="button" class="pq-action pq-action-reject" id="pq-reject-confirm" data-key="' + r.key + '">Reject specimen</button> ' +
                        '<button type="button" class="pq-action" id="pq-reject-cancel">Cancel</button></div>' : '') +
                    '</div>' : '') +
                '<div class="pq-section"><h5>' + ((r.tests || []).length > 1 ? 'Tests ordered' : 'Test ordered') + '</h5><ul class="pq-list">' + tests + '</ul></div>' +
                (r.additionalInformation ? '<div class="pq-section"><h5>Additional information</h5><p>' + escapeHtml(r.additionalInformation) + '</p></div>' : '') +
                (specimens ? '<div class="pq-section"><h5>Specimens obtained</h5><ul class="pq-list">' + specimens + '</ul></div>' : '') +
                (r.otherTestsRequested ? '<div class="pq-section"><h5>Other tests requested</h5><p>' + escapeHtml(r.otherTestsRequested) + '</p></div>' : '') +
                '<div class="pq-section"><h5>Hospital of origin</h5><p>' + (escapeHtml(r.hospitalOfOrigin) || '<span class="pq-muted">Not recorded</span>') + '</p></div>' +
                '<div class="pq-section"><h5>Progress</h5><ul class="pq-timeline">' + timeline + '</ul></div>' +
                '<div class="pq-section"><a href="' + escapeHtml(requestFormLink) + '">View the request form &rarr;</a><br/>' +
                    '<a href="' + dashboardUrl + '?patientId=' + escapeHtml(r.patientUuid) + '">Patient dashboard &rarr;</a></div>');
        };

        var openDrawer = function(key) {
            openRowKey = key;
            renderDrawer(findRow(key));
            jq('#pq-overlay').show();
            jq('#pq-drawer').addClass('pq-open').attr('aria-hidden', 'false');
        };
        var closeDrawer = function() {
            openRowKey = null;
            jq('#pq-overlay').hide();
            jq('#pq-drawer').removeClass('pq-open').attr('aria-hidden', 'true');
        };

        // ---------- recording a step ----------
        var changeStatus = function(button, key, status, reason) {
            var r = findRow(key);
            if (!r) { return; }
            // An unpaid specimen is received anyway, after a warning
            // No line breaks in the message: the page template would turn an escaped line break into a real one, which
            // breaks the script
            if (status === 'RECEIVED' && r.paymentStatus !== 'PAID' && !window.confirm('Payment status: ' + r.paymentStatusLabel +
                    '. Receive the specimen of ' + r.patientName + ' anyway?')) {
                return;
            }
            var label = button.html();
            button.prop('disabled', true).text('Saving...');
            jq.ajax({
                type: 'POST',
                url: statusUrl,
                data: { encounterId: r.encounterId, status: status, reason: reason || '' },
                dataType: 'json'
            }).done(function(result) {
                if (result.error) {
                    jq('#pq-message').html('<span class="pq-error">' + escapeHtml(result.error) + '</span>');
                    button.prop('disabled', false).html(label);
                }
                else {
                    var message = r.patientName + ': ' + result.statusLabel +
                        (status === 'RECEIVED' ? ' as ' + result.accessionNumber : '');
                    if (jq().toastmessage) { jq().toastmessage('showSuccessToast', message); }
                    load(false);
                }
            }).fail(function(xhr) {
                jq('#pq-message').html('<span class="pq-error">Unable to save (HTTP ' + xhr.status + ')' +
                    (xhr.status == 401 || xhr.status == 403 ? ': your session may have expired or you lack permission' : '') + '</span>');
                button.prop('disabled', false).html(label);
            });
        };

        // ---------- loading ----------
        var load = function(resetKnownRows) {
            var f = getFilters();
            updateLinks();
            jq.ajax({
                type: 'GET',
                url: dataUrl,
                data: { startDate: f.startDate, endDate: f.endDate, inLab: f.inLab },
                dataType: 'json',
                cache: false
            }).done(function(result) {
                jq('#pq-message').html(result.error ? '<span class="pq-error">' + escapeHtml(result.error) + '</span>' : '');
                rows = result.rows || [];
                var newKeys = {};
                var currentKeys = {};
                rows.forEach(function(r) {
                    r.testsText = (r.tests || []).join(', ');
                    r.statusRank = STATUS_ORDER.indexOf(r.status);
                    currentKeys[r.key] = true;
                    if (knownKeys && !resetKnownRows && !knownKeys[r.key]) { newKeys[r.key] = true; }
                });
                knownKeys = currentKeys;
                render(newKeys);
                jq('#pq-updated').text('Last updated ' + formatTime(result.generatedAt || Date.now()));
            }).fail(function(xhr) {
                jq('#pq-message').html('<span class="pq-error">Unable to load the pathology queue (HTTP ' + xhr.status + ')' +
                    (xhr.status == 401 || xhr.status == 403 ? ': your session may have expired' : '') + '</span>');
            });
        };

        jq(function() {
            restoreView();

            jq('.pq-table th.pq-sortable').click(function() {
                var key = jq(this).data('sort');
                if (key === state.sortKey) {
                    state.sortAscending = !state.sortAscending;
                }
                else {
                    state.sortKey = key;
                    state.sortType = jq(this).data('type') === 'number' ? 'number' : 'text';
                    state.sortAscending = state.sortType !== 'number' || key === 'statusRank';
                }
                saveView();
                render(null);
            });
            jq('#pq-summary').on('click', '.pq-card', function() {
                var quick = jq(this).data('quick') || '';
                state.quickFilter = state.quickFilter === quick ? '' : quick;
                saveView();
                updateLinks();
                render(null);
            });
            jq('#pq-body').on('click', 'tr[data-key]', function(e) {
                if (jq(e.target).closest('a, button').length) { return; }
                openDrawer(String(jq(this).data('key')));
            });
            jq('#pq-overlay, #pq-drawer-close').click(closeDrawer);
            jq(document).keydown(function(e) { if (e.key === 'Escape') { closeDrawer(); } });

            // Steps are recorded from the table or the detail panel; rejecting opens the panel to ask for the reason
            jq(document).on('click', '.pq-action[data-status]', function(e) {
                e.stopPropagation();
                var button = jq(this);
                var key = String(button.data('key'));
                if (button.data('status') === 'REJECTED') {
                    if (openRowKey !== key) { openDrawer(key); }
                    jq('#pq-reject-form').show();
                    jq('#pq-reject-reason').focus();
                    return;
                }
                changeStatus(button, key, button.data('status'));
            });
            jq(document).on('click', '#pq-reject-confirm', function() {
                var reason = jq.trim(jq('#pq-reject-reason').val());
                if (!reason) {
                    jq('#pq-reject-reason').focus();
                    return;
                }
                changeStatus(jq(this), String(jq(this).data('key')), 'REJECTED', reason);
            });
            jq(document).on('click', '#pq-reject-cancel', function() { jq('#pq-reject-form').hide(); });

            jq('#pq-reset').click(function() {
                storage.clear();
                state = jq.extend({}, DEFAULT_STATE);
                jq('#pq-status, #pq-payment, #pq-test').val('');
                jq('#pq-query').val('');
                updateLinks();
                render(null);
            });

            jq('#pq-start, #pq-end, #pq-in-lab').change(function() { load(true); });
            jq('#pq-status, #pq-payment, #pq-test').change(function() { saveView(); updateLinks(); render(null); });
            jq('#pq-query').on('input', function() { updateLinks(); render(null); });
            jq('#pq-refresh').click(function() { load(false); });

            load(true);
            // The table is not refreshed while a reason for rejecting is being typed
            window.setInterval(function() { if (!jq('#pq-reject-form').is(':visible')) { load(false); } }, REFRESH_MS);
        });
    })();
</script>

<% } %>
