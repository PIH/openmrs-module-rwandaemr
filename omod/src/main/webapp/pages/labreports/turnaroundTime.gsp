<%
    ui.decorateWith("appui", "standardEmrPage")
%>

<script type="text/javascript">
    var breadcrumbs = [
        { icon: "icon-home", link: '/' + OPENMRS_CONTEXT_PATH + '/index.htm' },
        { label: "Laboratory TaT", link: "${ ui.pageLink('rwandaemr', 'labreports/turnaroundTime') }" }
    ];
</script>

<style>
    .tat-filters {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
        gap: 12px;
        align-items: end;
        margin: 16px 0;
        padding: 12px;
        border: 1px solid #ddd;
        background: #f9f9f9;
    }
    .tat-filters label {
        display: block;
        font-weight: bold;
        margin-bottom: 4px;
    }
    .tat-filters input,
    .tat-filters select {
        width: 100%;
        box-sizing: border-box;
    }
    .tat-filters button[disabled] {
        opacity: 0.65;
        cursor: wait;
    }
    #message {
        margin: 12px 0;
        font-weight: bold;
    }
    #message.loading {
        color: #004b7a;
    }
    #message.error {
        color: #b21f2d;
    }
    .tat-kpis {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
        gap: 10px;
        margin: 16px 0;
    }
    .tat-kpi {
        border: 1px solid #ddd;
        background: #fff;
        padding: 12px;
    }
    .tat-kpi .value {
        display: block;
        font-size: 24px;
        font-weight: bold;
        margin-top: 4px;
    }
    .tat-chart {
        margin: 16px 0;
        border: 1px solid #ddd;
        padding: 12px;
        background: #fff;
    }
    .tat-bar {
        display: grid;
        grid-template-columns: 220px 1fr 60px;
        gap: 8px;
        align-items: center;
        margin: 6px 0;
    }
    .tat-bar-fill {
        height: 16px;
        background: #3f7fbf;
    }
    .tat-table {
        width: 100%;
        border-collapse: collapse;
    }
    .tat-table th,
    .tat-table td {
        border: 1px solid #ddd;
        padding: 8px;
    }
    .tat-table th {
        background: #f3f3f3;
        text-align: left;
    }
    .numeric {
        text-align: right;
    }
    .loading-row {
        color: #004b7a;
        font-weight: bold;
    }
</style>

<h2>Laboratory Turnaround Time</h2>

<div class="tat-filters">
    <div>
        <label for="startDate">Start date</label>
        <input type="text" id="startDate" placeholder="dd/MM/yyyy"/>
    </div>
    <div>
        <label for="endDate">End date</label>
        <input type="text" id="endDate" placeholder="dd/MM/yyyy"/>
    </div>
    <div>
        <label for="locationId">Location</label>
        <select id="locationId">
            <option value="">All locations</option>
            <% locations.each { location -> %>
                <option value="${ location.id }">${ ui.escapeHtml(location.name) }</option>
            <% } %>
        </select>
    </div>
    <div>
        <label for="categoryConceptId">Category</label>
        <select id="categoryConceptId">
            <option value="">All categories</option>
            <% labTestCategories.each { category -> %>
                <option value="${ category.category.id }">${ ui.escapeHtml(category.category.name.name) }</option>
            <% } %>
        </select>
    </div>
    <div>
        <label for="testConceptId">Test</label>
        <select id="testConceptId">
            <option value="">All tests</option>
            <% labTestCategories.each { category -> %>
                <optgroup label="${ ui.escapeHtml(category.category.name.name) }">
                    <% category.labTests.each { test -> %>
                        <option value="${ test.id }" data-category="${ category.category.id }">${ ui.escapeHtml(test.name.name) }</option>
                    <% } %>
                </optgroup>
            <% } %>
        </select>
    </div>
    <div>
        <label for="targetHours">Target hours</label>
        <input type="number" id="targetHours" min="1" value="48"/>
    </div>
    <div>
        <button type="button" id="runReport">Run</button>
    </div>
</div>

<div id="message"></div>

<div class="tat-kpis">
    <div class="tat-kpi">Total orders <span class="value" id="totalOrders">-</span></div>
    <div class="tat-kpi">Completed <span class="value" id="completedOrders">-</span></div>
    <div class="tat-kpi">Median hours <span class="value" id="medianHours">-</span></div>
    <div class="tat-kpi">90th percentile <span class="value" id="p90Hours">-</span></div>
    <div class="tat-kpi">Within target <span class="value" id="withinTarget">-</span></div>
    <div class="tat-kpi">Delayed <span class="value" id="delayedOrders">-</span></div>
</div>

<div class="tat-chart">
    <h3>Median TaT by test</h3>
    <div id="testChart"></div>
</div>

<table class="tat-table">
    <thead>
        <tr>
            <th>Ordered</th>
            <th>Test</th>
            <th>Location</th>
            <th>Status</th>
            <th>Specimen received</th>
            <th>Resulted</th>
            <th class="numeric">Order to received</th>
            <th class="numeric">Received to result</th>
            <th class="numeric">Total TaT</th>
        </tr>
    </thead>
    <tbody id="tatRows"></tbody>
</table>

<script type="text/javascript" src="${ ui.resourceLink('rwandaemr', 'scripts/labreports/turnaroundTime.js') }"></script>
