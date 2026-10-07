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
    .rq-page { max-width: 1700px; margin: 0 auto; }
    .rq-header { display: flex; align-items: flex-end; justify-content: space-between; gap: 16px; margin: 8px 0 14px; flex-wrap: wrap; }
    .rq-title { margin: 0; font-size: 28px; color: #263238; }
    .rq-subtitle { margin: 4px 0 0; color: #607d8b; font-size: 13px; }
    .rq-summary { display: flex; gap: 10px; flex-wrap: wrap; }
    .rq-count { border: 1px solid #d9e2e7; background: #fff; padding: 8px 14px; border-radius: 4px; text-align: right; min-width: 92px; }
    .rq-count strong { display: block; font-size: 22px; color: #263238; }
    .rq-count span { font-size: 12px; color: #607d8b; }
    .rq-panel { border: 1px solid #d9e2e7; background: #fff; padding: 12px 14px; margin-bottom: 12px; border-radius: 4px; }
    .rq-filters { display: grid; grid-template-columns: 150px 150px 110px 150px 150px 140px minmax(180px, 1fr) auto; gap: 10px; align-items: end; }
    .rq-field label { display: block; margin-bottom: 4px; font-size: 12px; font-weight: bold; color: #455a64; text-transform: uppercase; }
    .rq-field input, .rq-field select { width: 100%; box-sizing: border-box; min-height: 34px; border: 1px solid #b0bec5; border-radius: 3px; padding: 5px 8px; background: #fff; }
    .rq-actions { display: flex; gap: 8px; }
    .rq-actions a, .rq-actions button { min-height: 34px; white-space: nowrap; }
    .rq-status-line { display: flex; justify-content: space-between; color: #607d8b; font-size: 12px; margin: 0 0 8px; }
    .rq-error { color: #9f1d1d; font-weight: bold; }
    .rq-table-wrap { border: 1px solid #d9e2e7; background: #fff; border-radius: 4px; overflow-x: auto; }
    table.rq-table { width: 100%; border-collapse: collapse; min-width: 1500px; }
    .rq-table th { background: #f3f6f8; color: #455a64; font-size: 12px; text-transform: uppercase; border-bottom: 1px solid #d9e2e7; padding: 9px 8px; text-align: left; white-space: nowrap; }
    .rq-table th.rq-sortable { cursor: pointer; user-select: none; }
    .rq-table th.rq-sortable:hover { background: #e6edf1; }
    .rq-table th .rq-arrow { color: #90a4ae; margin-left: 4px; }
    .rq-table th.rq-sorted .rq-arrow { color: #263238; }
    .rq-table td { border-bottom: 1px solid #edf2f5; padding: 8px; vertical-align: top; color: #263238; font-size: 13px; }
    .rq-table tr:hover td { background: #f7fbfc; }
    .rq-table tr.rq-new td { background: #fff8e1; transition: background 2s; }
    .rq-muted { color: #78909c; font-size: 12px; }
    .rq-history { max-width: 280px; white-space: pre-wrap; overflow-wrap: anywhere; }
    .rq-chip { display: inline-block; padding: 3px 8px; border-radius: 12px; font-weight: bold; font-size: 12px; white-space: nowrap; color: #455a64; background: #eceff1; }
    .rq-PAID, .rq-REPORTED { color: #1b5e20; background: #dff0d8; }
    .rq-PARTIALLY_PAID, .rq-SENT, .rq-IMAGES_RECEIVED { color: #0b5394; background: #d9edf7; }
    .rq-NOT_PAID, .rq-CANCELLED { color: #9f1d1d; background: #f8d7da; }
    .rq-NOT_BILLED, .rq-NOT_SENT, .rq-not-processed { color: #8a6d3b; background: #fcf8e3; }
    .rq-processed { color: #1b5e20; background: #dff0d8; }
    .rq-empty { border: 1px solid #d9e2e7; background: #fff; padding: 18px; color: #455a64; }
    @media (max-width: 1200px) { .rq-filters { grid-template-columns: repeat(2, minmax(150px, 1fr)); } }
</style>

<div class="rq-page">
    <div class="rq-header">
        <div>
            <h3 class="rq-title">Radiology Queue</h3>
            <p class="rq-subtitle">Requested studies with PACS and payment status. Updates automatically every 30 seconds.</p>
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
                    <option value="YES" ${ queueRequest.processed == 'YES' ? 'selected="selected"' : '' }>Processed</option>
                    <option value="NO" ${ queueRequest.processed == 'NO' ? 'selected="selected"' : '' }>Not processed</option>
                </select>
            </div>
            <div class="rq-field">
                <label for="rq-query">Patient ID, name, order number or study</label>
                <input id="rq-query" type="text" value="${ ui.encodeHtmlAttribute(queueRequest.query) }"/>
            </div>
            <div class="rq-actions">
                <button type="button" id="rq-refresh">Refresh</button>
                <a class="button" id="rq-export" href="#">Export to Excel</a>
            </div>
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
                    <th>S.No</th>
                    <th class="rq-sortable" data-sort="patientIdentifier">Patient ID<span class="rq-arrow">&#8597;</span></th>
                    <th class="rq-sortable" data-sort="patientName">Patient Name<span class="rq-arrow">&#8597;</span></th>
                    <th class="rq-sortable" data-sort="age" data-type="number">Age<span class="rq-arrow">&#8597;</span></th>
                    <th class="rq-sortable" data-sort="sex">Sex<span class="rq-arrow">&#8597;</span></th>
                    <th class="rq-sortable" data-sort="modality">Modality<span class="rq-arrow">&#8597;</span></th>
                    <th class="rq-sortable" data-sort="typeOfStudy">Type of Study<span class="rq-arrow">&#8597;</span></th>
                    <th class="rq-sortable" data-sort="clinicalHistory">Clinical History<span class="rq-arrow">&#8597;</span></th>
                    <th class="rq-sortable" data-sort="requestingPhysician">Requesting Physician<span class="rq-arrow">&#8597;</span></th>
                    <th class="rq-sortable" data-sort="requestingDepartment">Requesting Department<span class="rq-arrow">&#8597;</span></th>
                    <th class="rq-sortable" data-sort="timeRequested" data-type="number">Time Requested<span class="rq-arrow">&#8597;</span></th>
                    <th class="rq-sortable" data-sort="pacsStatusLabel">PACS Status<span class="rq-arrow">&#8597;</span></th>
                    <th class="rq-sortable" data-sort="processedLabel">Processed<span class="rq-arrow">&#8597;</span></th>
                    <th class="rq-sortable" data-sort="paymentStatusLabel">Payment Status<span class="rq-arrow">&#8597;</span></th>
                    <th class="rq-sortable" data-sort="timeOfPayment" data-type="number">Time of Payment<span class="rq-arrow">&#8597;</span></th>
                </tr>
            </thead>
            <tbody id="rq-body"></tbody>
        </table>
    </div>
</div>

<script type="text/javascript">
    (function() {
        var REFRESH_MS = 30000;
        var dataUrl = '${ openmrsContextPath }/ws/rest/v1/rwandaemr/radiologyqueue';
        var pageUrl = '${ ui.pageLink("rwandaemr", "radiology/radiologyQueue") }';
        var patientOrdersUrl = '${ ui.pageLink("rwandaemr", "patient/radiologyOrders") }';
        var orderUrl = '${ ui.pageLink("rwandaemr", "patient/radiologyOrder") }';

        var rows = [];
        var knownKeys = null;     // keys of the rows already shown, to highlight new requests
        var sortKey = 'timeRequested';
        var sortType = 'number';
        var sortAscending = false;

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

        var rowKey = function(row) { return (row.orderIds || []).join('-'); };

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

        var matchesFilters = function(row, f) {
            if (f.modality && row.modality !== f.modality) { return false; }
            if (f.pacsStatus && row.pacsStatus !== f.pacsStatus) { return false; }
            if (f.paymentStatus && row.paymentStatus !== f.paymentStatus) { return false; }
            if (f.processed === 'YES' && !row.processed) { return false; }
            if (f.processed === 'NO' && row.processed) { return false; }
            if (f.query) {
                var text = [row.patientIdentifier, row.patientName, (row.orderNumbers || []).join(' '), row.typeOfStudy]
                    .join(' ').toLowerCase();
                if (text.indexOf(f.query.toLowerCase()) === -1) { return false; }
            }
            return true;
        };

        var compare = function(a, b) {
            var x = a[sortKey], y = b[sortKey];
            var xEmpty = x === null || x === undefined || x === '';
            var yEmpty = y === null || y === undefined || y === '';
            if (xEmpty || yEmpty) { return xEmpty === yEmpty ? 0 : (xEmpty ? 1 : -1); }   // empty values last
            var result = sortType === 'number' ? (x - y) : String(x).localeCompare(String(y), undefined, { sensitivity: 'base' });
            return sortAscending ? result : -result;
        };

        var updateQueryString = function(f) {
            var params = jq.param(f);
            if (window.history && window.history.replaceState) {
                window.history.replaceState(null, '', pageUrl + (pageUrl.indexOf('?') === -1 ? '?' : '&') + params);
            }
            jq('#rq-export').attr('href', pageUrl + (pageUrl.indexOf('?') === -1 ? '?' : '&') + params + '&export=excel');
        };

        var renderSummary = function(visible) {
            var counts = { total: visible.length, NOT_PAID: 0, NOT_SENT: 0, REPORTED: 0, NOT_PROCESSED: 0 };
            visible.forEach(function(r) {
                if (r.paymentStatus === 'NOT_PAID') { counts.NOT_PAID++; }
                if (r.pacsStatus === 'NOT_SENT') { counts.NOT_SENT++; }
                if (r.pacsStatus === 'REPORTED') { counts.REPORTED++; }
                if (!r.processed) { counts.NOT_PROCESSED++; }
            });
            jq('#rq-summary').html(
                '<div class="rq-count"><span>Studies</span><strong>' + counts.total + '</strong></div>' +
                '<div class="rq-count"><span>Not processed</span><strong>' + counts.NOT_PROCESSED + '</strong></div>' +
                '<div class="rq-count"><span>Not paid</span><strong>' + counts.NOT_PAID + '</strong></div>' +
                '<div class="rq-count"><span>Not sent to PACS</span><strong>' + counts.NOT_SENT + '</strong></div>' +
                '<div class="rq-count"><span>Reported</span><strong>' + counts.REPORTED + '</strong></div>');
        };

        var render = function(newKeys) {
            var f = getFilters();
            var visible = rows.filter(function(r) { return matchesFilters(r, f); });
            visible.sort(compare);
            renderSummary(visible);

            jq('.rq-table th.rq-sortable').removeClass('rq-sorted').find('.rq-arrow').html('&#8597;');
            jq('.rq-table th[data-sort="' + sortKey + '"]').addClass('rq-sorted')
                .find('.rq-arrow').html(sortAscending ? '&#9650;' : '&#9660;');

            if (visible.length === 0) {
                jq('#rq-body').html('<tr><td colspan="15" class="rq-muted">No radiology requests match these filters.</td></tr>');
                return;
            }
            var html = [];
            visible.forEach(function(r, i) {
                var isNew = newKeys && newKeys[rowKey(r)];
                var firstOrderId = (r.orderIds || [])[0];
                html.push('<tr' + (isNew ? ' class="rq-new"' : '') + '>' +
                    '<td>' + (i + 1) + '</td>' +
                    '<td>' + escapeHtml(r.patientIdentifier) + '</td>' +
                    '<td><a href="' + patientOrdersUrl + '?patientId=' + r.patientId + '">' + escapeHtml(r.patientName) + '</a></td>' +
                    '<td>' + escapeHtml(r.age) + '</td>' +
                    '<td>' + escapeHtml(r.sex) + '</td>' +
                    '<td>' + escapeHtml(r.modality) + '</td>' +
                    '<td>' + escapeHtml(r.typeOfStudy) +
                        '<div class="rq-muted"><a href="' + orderUrl + '?orderId=' + firstOrderId + '">' +
                        escapeHtml((r.orderNumbers || []).join(', ')) + '</a></div></td>' +
                    '<td class="rq-history">' + escapeHtml(r.clinicalHistory) + '</td>' +
                    '<td>' + escapeHtml(r.requestingPhysician) + '</td>' +
                    '<td>' + escapeHtml(r.requestingDepartment) + '</td>' +
                    '<td>' + formatTime(r.timeRequested) + '</td>' +
                    '<td><span class="rq-chip rq-' + escapeHtml(r.pacsStatus) + '">' + escapeHtml(r.pacsStatusLabel) + '</span></td>' +
                    '<td><span class="rq-chip ' + (r.processed ? 'rq-processed' : 'rq-not-processed') + '">' + escapeHtml(r.processedLabel) + '</span></td>' +
                    '<td><span class="rq-chip rq-' + escapeHtml(r.paymentStatus) + '">' + escapeHtml(r.paymentStatusLabel) + '</span></td>' +
                    '<td>' + formatTime(r.timeOfPayment) + '</td>' +
                    '</tr>');
            });
            jq('#rq-body').html(html.join(''));
            // Let the highlight of new requests fade out
            window.setTimeout(function() { jq('#rq-body tr.rq-new').removeClass('rq-new'); }, 8000);
        };

        var load = function(resetKnownRows) {
            var f = getFilters();
            updateQueryString(f);
            jq.ajax({
                type: 'GET',
                url: dataUrl,
                data: { startDate: f.startDate, endDate: f.endDate },
                dataType: 'json',
                cache: false
            }).done(function(result) {
                if (result.error) {
                    jq('#rq-message').html('<span class="rq-error">' + escapeHtml(result.error) + '</span>');
                }
                else {
                    jq('#rq-message').text('');
                }
                rows = result.rows || [];
                var newKeys = {};
                var currentKeys = {};
                rows.forEach(function(r) {
                    var key = rowKey(r);
                    currentKeys[key] = true;
                    if (knownKeys && !resetKnownRows && !knownKeys[key]) { newKeys[key] = true; }
                });
                knownKeys = currentKeys;
                render(newKeys);
                jq('#rq-updated').text('Last updated ' + formatTime(result.generatedAt || new Date().getTime()));
            }).fail(function(xhr) {
                jq('#rq-message').html('<span class="rq-error">Unable to load the radiology queue (HTTP ' + xhr.status + ')' +
                    (xhr.status == 401 || xhr.status == 403 ? ': your session may have expired' : '') + '</span>');
            });
        };

        jq(function() {
            jq('.rq-table th.rq-sortable').click(function() {
                var key = jq(this).data('sort');
                if (key === sortKey) {
                    sortAscending = !sortAscending;
                }
                else {
                    sortKey = key;
                    sortType = jq(this).data('type') === 'number' ? 'number' : 'text';
                    sortAscending = sortType !== 'number';
                }
                render(null);
            });
            jq('#rq-start, #rq-end').change(function() { load(true); });
            jq('#rq-modality, #rq-pacs, #rq-payment, #rq-processed').change(function() { updateQueryString(getFilters()); render(null); });
            jq('#rq-query').on('input', function() { updateQueryString(getFilters()); render(null); });
            jq('#rq-refresh').click(function() { load(false); });
            load(true);
            window.setInterval(function() { load(false); }, REFRESH_MS);
        });
    })();
</script>

<% } %>
