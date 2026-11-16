<%
    ui.decorateWith("appui", "standardEmrPage")
    ui.includeJavascript("uicommons", "moment-with-locales.min.js")
    ui.includeJavascript("pihapps", "dateUtils.js")
    def patientIdentifier = patient.patientIdentifier ? patient.patientIdentifier.identifier : ""
%>

<script type="text/javascript">
    var breadcrumbs = [
        { icon: "icon-home", link: '/' + OPENMRS_CONTEXT_PATH + '/index.htm' },
        { label: "${ ui.encodeJavaScript(ui.format(patient)) }", link: '${ ui.encodeJavaScript(ui.pageLink("coreapps", "clinicianfacing/patient", [patientId: patient.uuid])) }' },
        { label: "Bacteriology results" }
    ];
</script>

<style>
    .br-page { max-width: 1000px; }
    .br-patient { color: #455a64; margin-bottom: 12px; }
    .br-exam { border: 1px solid #d9e2e7; border-radius: 6px; background: #fff; margin-bottom: 14px; }
    .br-header { display: flex; flex-wrap: wrap; gap: 20px; padding: 8px 12px; background: #f5f7f8; border-bottom: 1px solid #d9e2e7; border-radius: 6px 6px 0 0; }
    .br-header .br-test { font-weight: bold; color: #00473f; }
    .br-header .br-label { color: #607d8b; }
    .br-body { padding: 8px 12px; }
    .br-row { display: grid; grid-template-columns: 160px minmax(0, 1fr); column-gap: 12px; padding: 3px 0; }
    .br-row > span:first-child { color: #546e7a; }
    .br-subpanel-name { color: #37474f; font-weight: bold; padding: 3px 0; }
    .br-subpanel .br-row > span:first-child { padding-left: 16px; }
    .br-comment { color: #607d8b; font-size: 0.9em; }
    .br-susceptibility { margin-top: 8px; }
    .br-susceptibility h4 { margin: 0 0 2px; font-size: 1em; color: #455a64; }
    .br-empty { color: #607d8b; padding: 16px 0; }
</style>

<div class="br-page">
    <h2>Bacteriology results</h2>
    <div class="br-patient">${ ui.escapeHtml(patientIdentifier) } — ${ ui.escapeHtml(patient.personName ? patient.personName.fullName : "") }</div>
<% if (!authorized) { %>
    <div class="br-empty">You do not have permission to view lab results.</div>
<% } else { %>
    <div id="br-loading"><i class="icon-spinner icon-spin icon-2x"></i></div>
    <div id="br-results"></div>
<% } %>
</div>

<% if (authorized) { %>
<script type="text/javascript">
    jq(function() {
        const patientUuid = '${ patient.uuid }';
        moment.locale(window.sessionContext?.locale ?? 'en');
        const rest = openmrsContextPath + "/ws/rest/v1/";
        const getJson = (url) => new Promise((resolve, reject) => jq.getJSON(url).done(resolve).fail(reject));
        const configRep = "dateFormat,dateTimeFormat,labOrderConfig:(labTestOrderType:(uuid),resultsDateQuestion:(uuid))";
        const orderRep = "uuid,orderNumber,dateActivated,accessionNumber,fulfillerEncounter:(uuid),concept:(uuid,displayStringForLab,setMembers:(uuid,displayStringForLab,setMembers:(uuid,displayStringForLab)))";
        const obsRep = "uuid,concept:(uuid),valueCoded:(uuid,display,displayStringForLab),valueNumeric,valueText,valueDatetime,comment,order:(uuid)";
        const encounterRep = "uuid,obs:(" + obsRep + ",groupMembers:(" + obsRep + "))";

        Promise.all([
            getJson(rest + "pihapps/config?v=custom:(" + configRep + ")"),
            getJson(rest + "rwandaemr/lab/bacteriology/config")
        ]).then(([config, bacteriologyConfig]) => {
            const dateUtils = new PihAppsDateUtils(moment, config.dateFormat, config.dateTimeFormat);
            const bacteriology = new RwandaBacteriology(jq, bacteriologyConfig);
            // Only reported results: when validation is required, those a validator has completed
            const query = jq.param({
                patient: patientUuid,
                orderType: config.labOrderConfig.labTestOrderType?.uuid,
                orderFulfillmentStatus: ["COMPLETED_FULFILLMENT"],
                sortBy: "dateActivated-desc",
                limit: 100,
                v: "custom:(" + orderRep + ")"
            }, true);
            return getJson(rest + "pihapps/labOrder?" + query).then(data => {
                // The bacteriology exams are the lab sets with an antimicrobial susceptibility
                const orders = (data.results || []).filter(o => o.fulfillerEncounter
                    && (o.concept.setMembers || []).some(m => bacteriology.hasOwnWidget(m.uuid)));
                if (orders.length === 0) {
                    jq("#br-loading").hide();
                    jq("#br-results").append(jq("<div>").addClass("br-empty").text("No bacteriology results have been reported for this patient."));
                    return;
                }
                const encounters = {};
                const results = {};
                const loads = [...new Set(orders.map(o => o.fulfillerEncounter.uuid))].map(uuid =>
                    getJson(rest + "encounter/" + uuid + "?v=custom:(" + encounterRep + ")").then(e => { encounters[uuid] = e; }));
                loads.push(getJson(rest + "rwandaemr/lab/bacteriology?" + jq.param({ order: orders.map(o => o.uuid) }, true))
                    .then(result => Object.keys(result).forEach(uuid => { results[uuid] = RwandaBacteriology.fromRest(result[uuid]); })));
                return Promise.all(loads).then(() => {
                    jq("#br-loading").hide();
                    orders.forEach(order => jq("#br-results").append(renderExam(order, encounters[order.fulfillerEncounter.uuid], results[order.uuid])));
                });
            });

            function renderExam(order, encounter, results) {
                const allObs = (encounter?.obs || []).flatMap(o => o.groupMembers ? [o, ...o.groupMembers] : [o])
                    .filter(o => o.order?.uuid === order.uuid);
                const value = (o) => (o.valueCoded ? (o.valueCoded.displayStringForLab ?? o.valueCoded.display) : null)
                    ?? (o.valueNumeric != null ? String(o.valueNumeric) : null)
                    ?? (o.valueDatetime ? dateUtils.formatDateWithTimeIfPresent(o.valueDatetime) : o.valueText) ?? "";
                const resultDate = allObs.find(o => o.concept.uuid === config.labOrderConfig.resultsDateQuestion?.uuid);
                const item = (label, text) => jq("<span>").append(jq("<span>").addClass("br-label").text(label + ": ")).append(jq("<strong>").text(text ?? ""));
                const card = jq("<div>").addClass("br-exam");
                card.append(jq("<div>").addClass("br-header")
                    .append(jq("<span>").addClass("br-test").text(order.concept.displayStringForLab))
                    .append(item("Lab ID", order.accessionNumber))
                    .append(item("Ordered", dateUtils.formatAsDateWithoutTime(order.dateActivated)))
                    .append(resultDate ? item("Result date", dateUtils.formatDateWithTimeIfPresent(resultDate.valueDatetime)) : null));

                // A line per test with results, in the order of the exam, each value with its comment.  Tests that are a set
                // of findings, such as the wet mount, have a heading and a line per finding.
                const line = (member) => {
                    const values = allObs.filter(o => o.concept.uuid === member.uuid && !o.groupMembers);
                    if (values.length === 0) {
                        return null;
                    }
                    const cell = jq("<span>");
                    values.forEach((o, i) => {
                        cell.append(document.createTextNode((i > 0 ? ", " : "") + value(o)));
                        if (o.comment) {
                            cell.append(jq("<span>").addClass("br-comment").text(" (" + o.comment + ")"));
                        }
                    });
                    return jq("<div>").addClass("br-row").append(jq("<span>").text(member.displayStringForLab)).append(cell);
                };
                const body = jq("<div>").addClass("br-body");
                order.concept.setMembers.filter(m => !bacteriology.hasOwnWidget(m.uuid)).forEach(member => {
                    if ((member.setMembers || []).length > 0) {
                        const lines = member.setMembers.map(line).filter(l => l);
                        if (lines.length > 0) {
                            body.append(jq("<div>").addClass("br-subpanel")
                                .append(jq("<div>").addClass("br-subpanel-name").text(member.displayStringForLab)).append(lines));
                        }
                    }
                    else {
                        body.append(line(member));
                    }
                });
                if (results?.isolates?.length > 0) {
                    body.append(jq("<div>").addClass("br-susceptibility")
                        .append(jq("<h4>").text("Antimicrobial susceptibility"))
                        .append(bacteriology.susceptibilityTable(results.isolates)));
                }
                if (body.children().length === 0) {
                    body.append(jq("<div>").addClass("br-comment").text("No results"));
                }
                return card.append(body);
            }
        }).catch(error => {
            console.error(error);
            jq("#br-loading").hide();
            jq("#br-results").append(jq("<div>").addClass("br-empty").text("Unable to load the bacteriology results"));
        });
    });
</script>
<% } %>
