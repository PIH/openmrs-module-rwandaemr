<%
    ui.decorateWith("appui", "standardEmrPage")
    ui.includeJavascript("pihapps", "lib/chartjs/chart.js")
    ui.includeJavascript("uicommons", "datatables/jquery.dataTables.min.js")
    ui.includeCss("uicommons", "datatables/jquery.dataTables.css")
    def selectedReturnUrl = ui.pageLink("rwandaemr", "patient/anesthesiaRecord", [
        "patientId": patient.id,
        "caseId": selectedCaseId
    ])
    def newCaseReturnUrl = ui.pageLink("rwandaemr", "patient/anesthesiaRecord", [
        "patientId": patient.id,
        "caseId": newCaseId
    ])
%>

${ ui.includeFragment("coreapps", "patientHeader", [ patient: patient.patient ]) }

<script type="text/javascript">
    var anesthesia = {
        vitalLabels: ${ vitalLabels },
        pulse: ${ pulseData },
        sbp: ${ sbpData },
        dbp: ${ dbpData },
        resp: ${ respData },
        spo2: ${ spo2Data },
        gasLabels: ${ gasLabels },
        gasPercent: ${ gasPercentData }
    };

    var anesthesiaFormUrls = {
        newCase: '${ui.pageLink("htmlformentryui", "htmlform/enterHtmlFormWithStandardUi", [
            "patientId": patient.id,
            "definitionUiResource": "file:configuration/htmlforms/anesthesia-case-details.xml",
            "anesthesiaCaseId": newCaseId,
            "returnUrl": newCaseReturnUrl
        ])}',
        caseDetails: '${ui.pageLink("htmlformentryui", "htmlform/enterHtmlFormWithStandardUi", [
            "patientId": patient.id,
            "definitionUiResource": "file:configuration/htmlforms/anesthesia-case-details.xml",
            "anesthesiaCaseId": selectedCaseId,
            "returnUrl": selectedReturnUrl
        ])}',
        vitals: '${ui.pageLink("htmlformentryui", "htmlform/enterHtmlFormWithStandardUi", [
            "patientId": patient.id,
            "definitionUiResource": "file:configuration/htmlforms/anesthesia-vitals.xml",
            "anesthesiaCaseId": selectedCaseId,
            "returnUrl": selectedReturnUrl
        ])}',
        gas: '${ui.pageLink("htmlformentryui", "htmlform/enterHtmlFormWithStandardUi", [
            "patientId": patient.id,
            "definitionUiResource": "file:configuration/htmlforms/anesthesia-gas.xml",
            "anesthesiaCaseId": selectedCaseId,
            "returnUrl": selectedReturnUrl
        ])}',
        medications: '${ui.pageLink("htmlformentryui", "htmlform/enterHtmlFormWithStandardUi", [
            "patientId": patient.id,
            "definitionUiResource": "file:configuration/htmlforms/anesthesia-medications.xml",
            "anesthesiaCaseId": selectedCaseId,
            "returnUrl": selectedReturnUrl
        ])}',
        recovery: '${ui.pageLink("htmlformentryui", "htmlform/enterHtmlFormWithStandardUi", [
            "patientId": patient.id,
            "definitionUiResource": "file:configuration/htmlforms/anesthesia-recovery.xml",
            "anesthesiaCaseId": selectedCaseId,
            "returnUrl": selectedReturnUrl
        ])}'
    };

    function makeChart(canvasId, labels, datasets, beginAtZero) {
        var canvas = document.getElementById(canvasId);
        if (!canvas) {
            return;
        }
        return new Chart(canvas, {
            type: "line",
            data: {
                labels: labels,
                datasets: datasets
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: { position: "bottom" },
                    tooltip: { mode: "index", intersect: false }
                },
                scales: {
                    x: { ticks: { maxRotation: 45, font: { size: 10 } } },
                    y: { beginAtZero: beginAtZero, ticks: { font: { size: 10 } } }
                }
            }
        });
    }

    function initializeTable(selector) {
        jq(selector).dataTable({
            bFilter: false,
            bJQueryUI: false,
            bLengthChange: false,
            iDisplayLength: 10,
            bSort: false,
            sPaginationType: "full_numbers"
        });
    }

    jq(document).ready(function() {
        jq("#return-button").click(function() {
            document.location.href = '${ui.pageLink("coreapps", "clinicianfacing/patient", ["patientId": patient.id])}';
        });
        jq("#new-operation-button").click(function() {
            document.location.href = anesthesiaFormUrls.newCase;
        });
        jq("#operation-selector").change(function() {
            if (this.value) {
                document.location.href = this.value;
            }
        });
        jq("#case-details-button").click(function() {
            document.location.href = anesthesiaFormUrls.caseDetails;
        });
        jq("#add-vitals-button").click(function() {
            document.location.href = anesthesiaFormUrls.vitals;
        });
        jq("#add-gas-button").click(function() {
            document.location.href = anesthesiaFormUrls.gas;
        });
        jq("#add-medications-button").click(function() {
            document.location.href = anesthesiaFormUrls.medications;
        });
        jq("#recovery-button").click(function() {
            document.location.href = anesthesiaFormUrls.recovery;
        });

        <% if (!vitalRows.isEmpty()) { %>
        initializeTable("#vitals-table");
        <% } %>
        <% if (!gasRows.isEmpty()) { %>
        initializeTable("#gas-table");
        <% } %>
        <% if (!medicationRows.isEmpty()) { %>
        initializeTable("#medications-table");
        <% } %>

        jq(".enc-row").click(function() {
            var href = jq(this).data("href");
            if (href) {
                document.location.href = href;
            }
        });

        <% if (!vitalRows.isEmpty()) { %>
        makeChart("chart-bp", anesthesia.vitalLabels, [
            { label: "Systolic BP", data: anesthesia.sbp, borderColor: "#006d77", backgroundColor: "#006d7722", borderWidth: 2, pointRadius: 4, spanGaps: true },
            { label: "Diastolic BP", data: anesthesia.dbp, borderColor: "#9b2226", backgroundColor: "#9b222622", borderWidth: 2, pointRadius: 4, spanGaps: true }
        ], false);
        makeChart("chart-vitals", anesthesia.vitalLabels, [
            { label: "Pulse", data: anesthesia.pulse, borderColor: "#005f73", backgroundColor: "#005f7322", borderWidth: 2, pointRadius: 4, spanGaps: true },
            { label: "Respiratory rate", data: anesthesia.resp, borderColor: "#7b2cbf", backgroundColor: "#7b2cbf22", borderWidth: 2, pointRadius: 4, spanGaps: true },
            { label: "SpO2", data: anesthesia.spo2, borderColor: "#2a9d8f", backgroundColor: "#2a9d8f22", borderWidth: 2, pointRadius: 4, spanGaps: true }
        ], false);
        <% } %>

        <% if (!gasRows.isEmpty()) { %>
        makeChart("chart-gas", anesthesia.gasLabels, [
            { label: "Inhaled gas concentration (%)", data: anesthesia.gasPercent, borderColor: "#ca6702", backgroundColor: "#ca670222", borderWidth: 2, pointRadius: 5, spanGaps: true }
        ], true);
        <% } %>
    });
</script>

<style>
    #anesthesia-page {
        padding: 10px;
    }
    #anesthesia-page h2 {
        color: #00473f;
        border-bottom: 2px solid #00473f;
        margin: 10px 0 0;
        padding-bottom: 6px;
    }
    .operation-bar {
        align-items: end;
        background: #f5f8f7;
        border-bottom: 1px solid #cfd9d6;
        display: grid;
        gap: 12px 20px;
        grid-template-columns: minmax(260px, 1fr) auto;
        padding: 14px 12px;
    }
    .operation-picker label {
        color: #444;
        display: block;
        font-size: 0.85em;
        font-weight: bold;
        margin-bottom: 4px;
    }
    .operation-picker select {
        box-sizing: border-box;
        max-width: 680px;
        width: 100%;
    }
    .operation-actions {
        align-items: center;
        display: flex;
        gap: 10px;
    }
    .case-status {
        border: 1px solid #8ca39d;
        border-radius: 3px;
        color: #31554c;
        font-size: 0.85em;
        padding: 6px 9px;
        white-space: nowrap;
    }
    .case-status.complete {
        border-color: #4b8063;
        color: #24613f;
    }
    .legacy-notice {
        background: #fff8e6;
        border-left: 3px solid #b7791f;
        color: #684714;
        padding: 9px 12px;
    }
    .anesthesia-section {
        border-bottom: 1px solid #d8d8d8;
        padding: 18px 0 20px;
    }
    .section-heading {
        align-items: center;
        display: flex;
        gap: 12px;
        justify-content: space-between;
        margin-bottom: 12px;
    }
    .section-heading h3 {
        color: #00473f;
        font-size: 1.15em;
        margin: 0;
    }
    .section-heading button {
        flex: 0 0 auto;
        white-space: nowrap;
    }
    .chart-grid {
        display: grid;
        gap: 18px;
        grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
        margin-bottom: 14px;
    }
    .chart-block h4 {
        color: #333;
        font-size: 0.95em;
        margin: 0 0 6px;
    }
    .chart-container {
        height: 250px;
        position: relative;
    }
    .record-table {
        border-collapse: collapse;
        table-layout: fixed;
        width: 100%;
    }
    .record-table th {
        background: #00473f;
        color: #fff;
        padding: 7px 9px;
        text-align: left;
    }
    .record-table td {
        border-bottom: 1px solid #eee;
        overflow-wrap: anywhere;
        padding: 7px 9px;
        vertical-align: top;
    }
    .record-table tr.enc-row:hover td {
        background: #eef7f5;
        cursor: pointer;
    }
    .empty-section {
        background: #f8f9fa;
        border-left: 3px solid #8a8a8a;
        color: #666;
        font-style: italic;
        padding: 10px 12px;
    }
    .summary-grid {
        display: grid;
        gap: 0 24px;
        grid-template-columns: repeat(4, minmax(0, 1fr));
        margin: 0;
    }
    .summary-item {
        border-bottom: 1px solid #eee;
        min-width: 0;
        padding: 9px 0;
    }
    .summary-item.wide {
        grid-column: span 2;
    }
    .summary-item dt {
        color: #666;
        font-size: 0.85em;
        font-weight: bold;
        margin-bottom: 3px;
    }
    .summary-item dd {
        color: #222;
        margin: 0;
        overflow-wrap: anywhere;
    }
    .summary-meta {
        color: #666;
        font-size: 0.85em;
        margin-top: 9px;
        text-align: right;
    }
    .anesthesia-footer {
        padding-top: 16px;
    }
    @media (max-width: 900px) {
        .operation-bar {
            align-items: stretch;
            grid-template-columns: minmax(0, 1fr);
        }
        .operation-actions {
            justify-content: space-between;
        }
        .chart-grid {
            grid-template-columns: minmax(0, 1fr);
        }
        .section-heading {
            align-items: stretch;
            flex-direction: column;
        }
        .section-heading button {
            width: 100%;
        }
        .record-table {
            table-layout: auto;
        }
        .summary-grid {
            grid-template-columns: repeat(2, minmax(0, 1fr));
        }
    }
    @media (max-width: 560px) {
        .operation-actions {
            align-items: stretch;
            flex-direction: column;
        }
        .operation-actions button {
            width: 100%;
        }
        .summary-grid {
            grid-template-columns: minmax(0, 1fr);
        }
        .summary-item.wide {
            grid-column: auto;
        }
    }
</style>

<div id="anesthesia-page">
    <h2>Anesthesia Record</h2>

    <div class="operation-bar">
        <div class="operation-picker">
            <label for="operation-selector">Operation</label>
            <select id="operation-selector" ${ operationSummaries.isEmpty() ? "disabled" : "" }>
                <% if (operationSummaries.isEmpty()) { %>
                <option value="">No operation recorded</option>
                <% } else { operationSummaries.each { operation ->
                    def operationUrl = ui.pageLink("rwandaemr", "patient/anesthesiaRecord", [
                        "patientId": patient.id,
                        "caseId": operation.id
                    ])
                %>
                <option value="${ operationUrl }" ${ operation.id == selectedCaseId ? "selected" : "" }>${ operation.label }${ operation.legacy ? " (Legacy)" : operation.complete ? " (Completed)" : " (In progress)" }</option>
                <% } } %>
            </select>
        </div>
        <div class="operation-actions">
            <% if (selectedCaseSummary != null) { %>
            <span class="case-status ${ selectedCaseSummary.complete ? "complete" : "" }">
                <i class="${ selectedCaseSummary.complete ? "icon-ok" : selectedCaseSummary.legacy ? "icon-lock" : "icon-time" }"></i>
                ${ selectedCaseSummary.complete ? "Completed" : selectedCaseSummary.legacy ? "Legacy record" : "In progress" }
            </span>
            <% } %>
            <button id="new-operation-button" type="button" class="confirm"><i class="icon-plus"></i> New Operation</button>
        </div>
    </div>

    <% if (legacyCaseSelected) { %>
    <div class="legacy-notice">This pre-upgrade anesthesia record is read-only. New observations require a tracked operation.</div>
    <% } %>

    <section class="anesthesia-section" id="case-details-section">
        <div class="section-heading">
            <h3>Case Details</h3>
            <% if (selectedCaseWritable) { %>
            <button id="case-details-button" type="button" class="confirm"><i class="icon-edit"></i> ${ caseDetailsRecorded ? "Update Case Details" : "Record Case Details" }</button>
            <% } %>
        </div>

        <% if (!caseDetailsRecorded) { %>
        <div class="empty-section">No anesthesia case details recorded.</div>
        <% } else { %>
        <dl class="summary-grid">
            <div class="summary-item"><dt>Service</dt><dd>${ caseDetails.service ?: "-" }</dd></div>
            <div class="summary-item wide"><dt>Procedure</dt><dd>${ caseDetails.procedure ?: "-" }</dd></div>
            <div class="summary-item"><dt>Urgency</dt><dd>${ caseDetails.urgency ?: "-" }</dd></div>
            <div class="summary-item"><dt>Surgeon</dt><dd>${ caseDetails.surgeon ?: "-" }</dd></div>
            <div class="summary-item"><dt>Anesthetist</dt><dd>${ caseDetails.anesthetist ?: "-" }</dd></div>
            <div class="summary-item"><dt>Anesthesia</dt><dd>${ caseDetails.anesthesiaType ?: "-" }</dd></div>
            <div class="summary-item"><dt>Premedication</dt><dd>${ caseDetails.premedication ?: "-" }</dd></div>
            <div class="summary-item"><dt>Ventilation</dt><dd>${ caseDetails.ventilation ?: "-" }</dd></div>
        </dl>
        <div class="summary-meta">Last updated ${ ui.format(caseDetailsEncounter.encounterDatetime) }</div>
        <% } %>
    </section>

    <section class="anesthesia-section" id="vitals-section">
        <div class="section-heading">
            <h3>Blood Pressure &amp; Pulse</h3>
            <% if (selectedCaseWritable) { %>
            <button id="add-vitals-button" type="button" class="confirm"><i class="icon-plus"></i> Record BP &amp; Pulse</button>
            <% } %>
        </div>

        <% if (vitalRows.isEmpty()) { %>
        <div class="empty-section">No blood pressure or pulse observations recorded.</div>
        <% } else { %>
        <div class="chart-grid">
            <div class="chart-block">
                <h4>Blood Pressure</h4>
                <div class="chart-container"><canvas id="chart-bp"></canvas></div>
            </div>
            <div class="chart-block">
                <h4>Pulse, Respiration &amp; SpO2</h4>
                <div class="chart-container"><canvas id="chart-vitals"></canvas></div>
            </div>
        </div>
        <table id="vitals-table" class="record-table">
            <thead>
            <tr>
                <th>Date &amp; Time</th>
                <th>BP</th>
                <th>Pulse</th>
                <th>Resp</th>
                <th>SpO2</th>
                <th>Temp</th>
                <th>Provider</th>
            </tr>
            </thead>
            <tbody>
            <% vitalRows.each { row ->
                def enc = row.encounter
                def viewUrl = ui.pageLink("htmlformentryui", "htmlform/viewEncounterWithHtmlForm", [
                    "patientId": enc.patient.uuid,
                    "encounter": enc.uuid,
                    "returnUrl": selectedReturnUrl
                ])
            %>
            <tr class="enc-row" data-href="${ viewUrl }">
                <td>${ ui.format(enc.encounterDatetime) }</td>
                <td>${ row.sbp != null ? row.sbp.intValue() : "-" } / ${ row.dbp != null ? row.dbp.intValue() : "-" }</td>
                <td>${ row.pulse != null ? row.pulse.intValue() : "-" }</td>
                <td>${ row.resp != null ? row.resp.intValue() : "-" }</td>
                <td>${ row.spo2 != null ? row.spo2.intValue() + "%" : "-" }</td>
                <td>${ row.temperature != null ? row.temperature + " C" : "-" }</td>
                <td>
                    <% enc.encounterProviders.eachWithIndex { ep, index -> %>
                    ${ ui.format(ep.provider) }${ enc.encounterProviders.size() - index > 1 ? "<br/>" : "" }
                    <% } %>
                </td>
            </tr>
            <% } %>
            </tbody>
        </table>
        <% } %>
    </section>

    <section class="anesthesia-section" id="gas-section">
        <div class="section-heading">
            <h3>Inhaled Gas</h3>
            <% if (selectedCaseWritable) { %>
            <button id="add-gas-button" type="button" class="confirm"><i class="icon-plus"></i> Record Gas</button>
            <% } %>
        </div>

        <% if (gasRows.isEmpty()) { %>
        <div class="empty-section">No inhaled gas observations recorded.</div>
        <% } else { %>
        <div class="chart-block">
            <h4>Gas Concentration</h4>
            <div class="chart-container"><canvas id="chart-gas"></canvas></div>
        </div>
        <table id="gas-table" class="record-table">
            <thead>
            <tr>
                <th>Date &amp; Time</th>
                <th>Agent</th>
                <th>Concentration</th>
                <th>Provider</th>
            </tr>
            </thead>
            <tbody>
            <% gasRows.each { row ->
                def enc = row.encounter
                def viewUrl = ui.pageLink("htmlformentryui", "htmlform/viewEncounterWithHtmlForm", [
                    "patientId": enc.patient.uuid,
                    "encounter": enc.uuid,
                    "returnUrl": selectedReturnUrl
                ])
            %>
            <tr class="enc-row" data-href="${ viewUrl }">
                <td>${ ui.format(enc.encounterDatetime) }</td>
                <td>${ row.inhaledAgent ?: "-" }</td>
                <td>${ row.inhaledAgentPercent != null ? row.inhaledAgentPercent + "%" : "-" }</td>
                <td>
                    <% enc.encounterProviders.eachWithIndex { ep, index -> %>
                    ${ ui.format(ep.provider) }${ enc.encounterProviders.size() - index > 1 ? "<br/>" : "" }
                    <% } %>
                </td>
            </tr>
            <% } %>
            </tbody>
        </table>
        <% } %>
    </section>

    <section class="anesthesia-section" id="medications-section">
        <div class="section-heading">
            <h3>Medications, Fluids &amp; Transfusion</h3>
            <% if (selectedCaseWritable) { %>
            <button id="add-medications-button" type="button" class="confirm"><i class="icon-plus"></i> Record Medication / Fluid</button>
            <% } %>
        </div>

        <% if (medicationRows.isEmpty()) { %>
        <div class="empty-section">No medication, fluid, or transfusion events recorded.</div>
        <% } else { %>
        <table id="medications-table" class="record-table">
            <thead>
            <tr>
                <th>Date &amp; Time</th>
                <th>Medication</th>
                <th>Fluids / Perfusion</th>
                <th>Transfusion</th>
                <th>Provider</th>
            </tr>
            </thead>
            <tbody>
            <% medicationRows.each { row ->
                def enc = row.encounter
                def viewUrl = ui.pageLink("htmlformentryui", "htmlform/viewEncounterWithHtmlForm", [
                    "patientId": enc.patient.uuid,
                    "encounter": enc.uuid,
                    "returnUrl": selectedReturnUrl
                ])
            %>
            <tr class="enc-row" data-href="${ viewUrl }">
                <td>${ ui.format(enc.encounterDatetime) }</td>
                <td>${ row.medications ?: "-" }</td>
                <td>${ row.fluids ?: "-" }</td>
                <td>${ row.transfusion ?: "-" }</td>
                <td>
                    <% enc.encounterProviders.eachWithIndex { ep, index -> %>
                    ${ ui.format(ep.provider) }${ enc.encounterProviders.size() - index > 1 ? "<br/>" : "" }
                    <% } %>
                </td>
            </tr>
            <% } %>
            </tbody>
        </table>
        <% } %>
    </section>

    <section class="anesthesia-section" id="recovery-section">
        <div class="section-heading">
            <h3>Completion &amp; Recovery</h3>
            <% if (selectedCaseWritable) { %>
            <button id="recovery-button" type="button" class="confirm"><i class="icon-edit"></i> ${ recoveryRecorded ? "Update Completion" : "Record Completion" }</button>
            <% } %>
        </div>

        <% if (!recoveryRecorded) { %>
        <div class="empty-section">No anesthesia completion or recovery information recorded.</div>
        <% } else { %>
        <dl class="summary-grid">
            <div class="summary-item"><dt>Airway technique</dt><dd>${ recoveryDetails.airway ?: "-" }</dd></div>
            <div class="summary-item"><dt>Position</dt><dd>${ recoveryDetails.position ?: "-" }</dd></div>
            <div class="summary-item"><dt>Aldrete score</dt><dd>${ recoveryDetails.aldrete != null ? recoveryDetails.aldrete.intValue() : "-" }</dd></div>
            <div class="summary-item wide"><dt>Post-operative diagnosis</dt><dd>${ recoveryDetails.postopDiagnosis ?: "-" }</dd></div>
            <div class="summary-item wide"><dt>Remarks</dt><dd>${ recoveryDetails.remarks ?: "-" }</dd></div>
        </dl>
        <div class="summary-meta">Last updated ${ ui.format(recoveryEncounter.encounterDatetime) }</div>
        <% } %>
    </section>

    <div class="anesthesia-footer">
        <input id="return-button" type="button" class="cancel" value="${ ui.message("rwandaemr.encounterList.return") }"/>
    </div>
</div>
