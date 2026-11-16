<%
    ui.decorateWith("appui", "standardEmrPage")
%>

<script type="text/javascript">
    var breadcrumbs = [
        { icon: "icon-home", link: '/' + OPENMRS_CONTEXT_PATH + '/index.htm' },
        { label: "Aggregate Lab Report", link: "${ ui.pageLink('rwandaemr', 'labreports/aggregate') }" }
    ];
</script>

<style>
    .lab-report-filters {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
        gap: 12px;
        align-items: end;
        margin: 16px 0;
        padding: 12px;
        border: 1px solid #ddd;
        background: #f9f9f9;
    }
    .lab-report-filters label {
        display: block;
        font-weight: bold;
        margin-bottom: 4px;
    }
    .lab-report-filters input,
    .lab-report-filters select {
        width: 100%;
        box-sizing: border-box;
    }
    .lab-report-actions {
        display: flex;
        gap: 8px;
    }
    .lab-report-summary {
        margin: 12px 0;
        font-weight: bold;
    }
    .lab-report-summary.loading {
        color: #004b7a;
    }
    .lab-report-summary.error {
        color: #b21f2d;
    }
    .lab-report-actions button[disabled] {
        opacity: 0.65;
        cursor: wait;
    }
    .lab-report-table {
        width: 100%;
        border-collapse: collapse;
    }
    .lab-report-table th,
    .lab-report-table td {
        border: 1px solid #ddd;
        padding: 8px;
    }
    .lab-report-table th {
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

<h2>Aggregate Lab Report</h2>

<div class="lab-report-filters">
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
        <label for="resultType">Result type</label>
        <select id="resultType">
            <option value="all">All resulted</option>
            <option value="positive">Positive rows</option>
            <option value="negative">Negative rows</option>
        </select>
    </div>
    <div class="lab-report-actions">
        <button type="button" id="runReport">Run</button>
        <button type="button" id="exportReport">CSV</button>
    </div>
</div>

<div id="message" class="lab-report-summary"></div>

<table class="lab-report-table">
    <thead>
        <tr>
            <th>Category</th>
            <th>Test</th>
            <th class="numeric">Positive</th>
            <th class="numeric">Negative</th>
            <th class="numeric">Total</th>
        </tr>
    </thead>
    <tbody id="reportRows"></tbody>
</table>

<script type="text/javascript" src="${ ui.resourceLink('rwandaemr', 'scripts/labreports/aggregate.js') }"></script>
