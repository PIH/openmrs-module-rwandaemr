<%
    ui.decorateWith("appui", "standardEmrPage")
    ui.includeJavascript("pihapps", "lib/chartjs/chart.js")
    ui.includeJavascript("uicommons", "datatables/jquery.dataTables.min.js")
    ui.includeCss("uicommons", "datatables/jquery.dataTables.css")

    def emptyValue = '-'
    def display = { value -> value ?: emptyValue }
    def dashboardReturnUrl = ui.pageLink('coreapps', 'clinicianfacing/patient', ['patientId': patient.id])
    def selectedReturnUrl = selectedEpisodeId ? ui.pageLink('rwandaemr', 'patient/labourCareGuide', [
        'patientId': patient.id,
        'episodeId': selectedEpisodeId
    ]) : ui.pageLink('rwandaemr', 'patient/labourCareGuide', ['patientId': patient.id])
    def newReturnUrl = ui.pageLink('rwandaemr', 'patient/labourCareGuide', [
        'patientId': patient.id,
        'episodeId': newEpisodeId
    ])
    def enterFormUrl = { formFile, episodeId, returnUrl ->
        ui.pageLink('htmlformentryui', 'htmlform/enterHtmlFormWithStandardUi', [
            'patientId': patient.id,
            'definitionUiResource': "file:configuration/htmlforms/${formFile}",
            'labourEpisodeId': episodeId,
            'returnUrl': returnUrl
        ])
    }
    def newHourlyFormUrl = enterFormUrl('parto-hourly-form.xml', newEpisodeId, newReturnUrl)
    def selectedUpdatedLabel = selectedEpisodeSummary ? ui.formatDatePretty(selectedEpisodeSummary.lastUpdated) : emptyValue
    def returnLabel = ui.message('rwandaemr.encounterList.return')
    def guideActions = selectedEpisodeId ? [
        [label: 'Hourly Observation', form: 'parto-hourly-form.xml', primary: true],
        [label: 'Labour Summary', form: 'parto-labour-summary.xml'],
        [label: 'Newborn', form: 'parto-newborn.xml'],
        [label: 'PPH Diagnosis', form: 'parto-pph-diagnosis.xml'],
        [label: 'Postpartum Woman', form: 'parto-postpartum-woman.xml'],
        [label: 'Postnatal Newborn', form: 'parto-postpartum-newborn.xml'],
        [label: 'Discharge', form: 'parto-discharge.xml']
    ] : []
%>

${ ui.includeFragment('coreapps', 'patientHeader', [patient: patient.patient]) }

<script type="text/javascript">
    var lcg = {
        labels: ${ labels },
        fhr: ${ fhrData },
        pulse: ${ pulseData },
        respirations: ${ respData },
        sbp: ${ sbpData },
        dbp: ${ dbpData },
        temperature: ${ tempData },
        cervix: ${ cervixData },
        descent: ${ descentData },
        contractions: ${ contractionsData }
    };

    function makeChart(canvasId, datasets) {
        var canvas = document.getElementById(canvasId);
        if (!canvas) {
            return;
        }
        return new Chart(canvas, {
            type: "line",
            data: {
                labels: lcg.labels,
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
                    y: { beginAtZero: false, ticks: { font: { size: 10 } } }
                }
            }
        });
    }

        jq(document).ready(function() {
        jq("#return-button").click(function() {
            document.location.href = '${ dashboardReturnUrl }';
        });
        jq("#episode-selector").change(function() {
            if (this.value) {
                document.location.href = this.value;
            }
        });
        jq(".lcg-action").click(function() {
            var href = jq(this).data("href");
            if (href) {
                document.location.href = href;
            }
        });
        jq(".enc-row").click(function() {
            var href = jq(this).data("href");
            if (href) {
                document.location.href = href;
            }
        });
        <% if (!hourlyRows.isEmpty()) { %>
        jq("#hourly-table").dataTable({
            bFilter: false,
            bJQueryUI: false,
            bLengthChange: false,
            iDisplayLength: 12,
            bSort: false,
            sPaginationType: "full_numbers"
        });
        makeChart("chart-baby", [
            { label: "FHR", data: lcg.fhr, borderColor: "#b42318", backgroundColor: "#b4231822", borderWidth: 2, pointRadius: 4, spanGaps: true }
        ]);
        makeChart("chart-woman", [
            { label: "Pulse", data: lcg.pulse, borderColor: "#006d77", backgroundColor: "#006d7722", borderWidth: 2, pointRadius: 4, spanGaps: true },
            { label: "Respirations", data: lcg.respirations, borderColor: "#6f42c1", backgroundColor: "#6f42c122", borderWidth: 2, pointRadius: 4, spanGaps: true },
            { label: "Temperature", data: lcg.temperature, borderColor: "#ca6702", backgroundColor: "#ca670222", borderWidth: 2, pointRadius: 4, spanGaps: true }
        ]);
        makeChart("chart-bp", [
            { label: "Systolic BP", data: lcg.sbp, borderColor: "#0a9396", backgroundColor: "#0a939622", borderWidth: 2, pointRadius: 4, spanGaps: true },
            { label: "Diastolic BP", data: lcg.dbp, borderColor: "#ae2012", backgroundColor: "#ae201222", borderWidth: 2, pointRadius: 4, spanGaps: true }
        ]);
        makeChart("chart-progress", [
            { label: "Cervix", data: lcg.cervix, borderColor: "#007f5f", backgroundColor: "#007f5f22", borderWidth: 2, pointRadius: 4, spanGaps: true },
            { label: "Descent", data: lcg.descent, borderColor: "#495057", backgroundColor: "#49505722", borderWidth: 2, pointRadius: 4, spanGaps: true },
            { label: "Contractions", data: lcg.contractions, borderColor: "#9c6644", backgroundColor: "#9c664422", borderWidth: 2, pointRadius: 4, spanGaps: true }
        ]);
        <% } %>
    });
</script>

<style>
    #lcg-page { padding: 10px; }
    #lcg-page h2 { color: #0f6e56; border-bottom: 2px solid #0f6e56; margin: 10px 0 0; padding-bottom: 6px; }
    #lcg-page h3 { color: #174a3c; margin: 0 0 8px; }
    .lcg-toolbar { align-items: end; background: #f5f8f7; border-bottom: 1px solid #cfd9d6; display: grid; gap: 12px 20px; grid-template-columns: minmax(260px, 1fr) auto; padding: 14px 12px; }
    .lcg-picker label { color: #444; display: block; font-size: 0.85em; font-weight: bold; margin-bottom: 4px; }
    .lcg-picker select { box-sizing: border-box; max-width: 680px; width: 100%; }
    .lcg-actions { display: flex; flex-wrap: wrap; gap: 8px; margin: 12px 0; }
    .lcg-section { border-bottom: 1px solid #d9e1df; padding: 16px 0; }
    .lcg-status-grid { display: grid; gap: 10px; grid-template-columns: repeat(4, minmax(150px, 1fr)); }
    .lcg-status { background: #f8faf9; border: 1px solid #cfd9d6; border-radius: 4px; padding: 10px; }
    .lcg-status strong { display: block; }
    .lcg-alerts { background: #fff5e6; border: 1px solid #dc8a00; border-radius: 4px; color: #6f4300; margin: 12px 0; padding: 10px 12px; }
    .lcg-alerts span { display: inline-block; font-weight: bold; margin: 3px 12px 3px 0; }
    .lcg-ok { background: #eef8f1; border: 1px solid #7dbb8d; border-radius: 4px; color: #285b35; margin: 12px 0; padding: 10px 12px; }
    .lcg-chart-grid { display: grid; gap: 14px; grid-template-columns: repeat(2, minmax(260px, 1fr)); }
    .chart-panel { border: 1px solid #d9e1df; border-radius: 4px; padding: 12px; }
    .chart-box { height: 230px; position: relative; }
    .empty-section { color: #777; font-style: italic; padding: 16px 0; }
    table.lcg-table { border-collapse: collapse; width: 100%; }
    table.lcg-table th { background: #0f6e56; color: #fff; padding: 7px; text-align: left; }
    table.lcg-table td { border-bottom: 1px solid #e6ecea; padding: 6px 7px; vertical-align: top; }
    table.lcg-table tr:hover td { background: #f3faf8; cursor: pointer; }
    .alert-row td { background: #fff5e6 !important; }
    .muted { color: #777; }
    @media (max-width: 900px) {
        .lcg-toolbar, .lcg-chart-grid, .lcg-status-grid { grid-template-columns: 1fr; }
    }
</style>

<div id="lcg-page">
    <button id="return-button" class="cancel">&larr; ${ returnLabel }</button>
    <h2>Labour Care Guide</h2>

    <div class="lcg-toolbar">
        <div class="lcg-picker">
            <label for="episode-selector">Labour episode</label>
            <select id="episode-selector">
                <% if (episodeSummaries.isEmpty()) { %>
                    <option>No Labour Care Guide started</option>
                <% } else { %>
                    <% episodeSummaries.each { episode ->
                        def episodeUrl = ui.pageLink('rwandaemr', 'patient/labourCareGuide', ['patientId': patient.id, 'episodeId': episode.id])
                        def episodeSelected = episode.id == selectedEpisodeId
                    %>
                        <option value="${ episodeUrl }"<% if (episodeSelected) { %> selected="selected"<% } %>>
                            ${ episode.label } (${ episode.encounterCount } records)
                        </option>
                    <% } %>
                <% } %>
            </select>
        </div>
        <button class="confirm lcg-action" data-href="${ newHourlyFormUrl }">
            <i class="icon-plus"></i> Start New Labour Guide
        </button>
    </div>

    <% if (selectedEpisodeId) { %>
    <div class="lcg-actions">
        <% guideActions.each { action ->
            def actionClass = action.primary ? 'confirm lcg-action' : 'lcg-action'
            def actionUrl = enterFormUrl(action.form, selectedEpisodeId, selectedReturnUrl)
        %>
        <button class="${ actionClass }" data-href="${ actionUrl }">${ action.label }</button>
        <% } %>
    </div>

    <div class="lcg-status-grid">
        <div class="lcg-status"><strong>Hourly observations</strong>${ sectionStatus.hourly ?: 0 }</div>
        <div class="lcg-status"><strong>Labour summary</strong>${ sectionStatus.labourSummary ?: 0 }</div>
        <div class="lcg-status"><strong>Newborn</strong>${ sectionStatus.newborn ?: 0 }</div>
        <div class="lcg-status"><strong>PPH diagnosis</strong>${ sectionStatus.pph ?: 0 }</div>
        <div class="lcg-status"><strong>Postpartum woman</strong>${ sectionStatus.postpartumWoman ?: 0 }</div>
        <div class="lcg-status"><strong>Postnatal newborn</strong>${ sectionStatus.postpartumNewborn ?: 0 }</div>
        <div class="lcg-status"><strong>Discharge</strong>${ sectionStatus.discharge ?: 0 }</div>
        <div class="lcg-status"><strong>Updated</strong>${ selectedUpdatedLabel }</div>
    </div>

    <% if (activeAlerts.isEmpty()) { %>
        <div class="lcg-ok"><i class="icon-ok"></i> No active alerts from the latest hourly observation.</div>
    <% } else { %>
        <div class="lcg-alerts">
            <strong><i class="icon-warning-sign"></i> Active alerts:</strong>
            <% activeAlerts.each { alert -> %>
                <span>${ alert.label }: ${ alert.value } ${ alert.unit } (${ alert.threshold })</span>
            <% } %>
        </div>
    <% } %>

    <% if (hourlyRows.isEmpty()) { %>
        <div class="empty-section">No hourly observations recorded for this Labour Care Guide yet.</div>
    <% } else { %>
        <div class="lcg-section">
            <h3>Trends</h3>
            <div class="lcg-chart-grid">
                <div class="chart-panel"><h3>Baby</h3><div class="chart-box"><canvas id="chart-baby"></canvas></div></div>
                <div class="chart-panel"><h3>Woman</h3><div class="chart-box"><canvas id="chart-woman"></canvas></div></div>
                <div class="chart-panel"><h3>Blood Pressure</h3><div class="chart-box"><canvas id="chart-bp"></canvas></div></div>
                <div class="chart-panel"><h3>Labour Progress</h3><div class="chart-box"><canvas id="chart-progress"></canvas></div></div>
            </div>
        </div>

        <div class="lcg-section">
            <h3>Hourly Labour Guide</h3>
            <table id="hourly-table" class="lcg-table">
                <thead>
                    <tr>
                        <th>Time</th>
                        <th>Supportive care</th>
                        <th>Baby</th>
                        <th>Woman</th>
                        <th>Progress</th>
                        <th>Medication</th>
                        <th>Assessment / Plan</th>
                    </tr>
                </thead>
                <tbody>
                <% hourlyRows.each { row ->
                    def viewUrl = ui.pageLink('htmlformentryui', 'htmlform/viewEncounterWithHtmlForm', [
                        'patientId': row.encounter.patient.uuid,
                        'encounter': row.encounter.uuid,
                        'returnProvider': 'rwandaemr',
                        'returnPage': 'patient/labourCareGuide'
                    ])
                    def rowClass = row.alerts.isEmpty() ? '' : 'alert-row'
                    def urineVolume = row.urineVolume ? '(' + row.urineVolume + ' mL/hr)' : ''
                    def plan = row.plan ?: ''
                %>
                    <tr class="enc-row ${ rowClass }" data-href="${ viewUrl }">
                        <td>${ ui.format(row.encounter.encounterDatetime) }</td>
                        <td>
                            Companion: ${ display(row.companion) }<br/>
                            Pain: ${ display(row.painRelief) }<br/>
                            Fluid: ${ display(row.oralFluid) }<br/>
                            Posture: ${ display(row.posture) }
                        </td>
                        <td>
                            FHR: ${ display(row.fhr) }<br/>
                            Decel: ${ display(row.fhrDeceleration) }<br/>
                            Fluid: ${ display(row.amnioticFluid) }<br/>
                            Pos: ${ display(row.fetalPosition) }<br/>
                            Caput/Mould: ${ display(row.caput) } / ${ display(row.moulding) }
                        </td>
                        <td>
                            Pulse: ${ display(row.pulse) }<br/>
                            Resp: ${ display(row.respirations) }<br/>
                            BP: ${ display(row.sbp) } / ${ display(row.dbp) }<br/>
                            Temp: ${ display(row.temperature) }<br/>
                            Urine: ${ display(row.urine) } ${ urineVolume }
                        </td>
                        <td>
                            Cervix: ${ display(row.cervix) } cm<br/>
                            Descent: ${ display(row.descent) }<br/>
                            Contractions: ${ display(row.contractions) } /10min<br/>
                            Duration: ${ display(row.contractionDuration) } sec
                        </td>
                        <td>
                            Oxytocin: ${ display(row.oxytocin) }<br/>
                            Medicine: ${ display(row.medicine) }<br/>
                            IV fluids: ${ display(row.ivFluids) }
                        </td>
                        <td>
                            ${ display(row.assessment) }<br/>
                            <span class="muted">${ plan }</span><br/>
                            Initials: ${ display(row.initials) }
                        </td>
                    </tr>
                <% } %>
                </tbody>
            </table>
        </div>
    <% } %>
    <% } else { %>
        <div class="empty-section">Start a new Labour Care Guide to begin recording the exported Parto forms.</div>
    <% } %>
</div>
