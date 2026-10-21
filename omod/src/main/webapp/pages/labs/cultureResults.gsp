<%
    ui.decorateWith("appui", "standardEmrPage")
    ui.includeJavascript("uicommons", "moment-with-locales.min.js")
    ui.includeJavascript("pihapps", "dateUtils.js")
    ui.includeJavascript("rwandaemr", "labs/antibiogram.js")
    ui.includeCss("rwandaemr", "labs/antibiogram.css")
    def patientIdentifier = patient.patientIdentifier ? patient.patientIdentifier.identifier : ""
%>

<script type="text/javascript">
    var breadcrumbs = [
        { icon: "icon-home", link: '/' + OPENMRS_CONTEXT_PATH + '/index.htm' },
        { label: "${ ui.encodeJavaScript(ui.format(patient)) }", link: '${ ui.encodeJavaScript(ui.pageLink("coreapps", "clinicianfacing/patient", [patientId: patient.uuid])) }' },
        { label: "Culture results" }
    ];
</script>

<style>
    .cr-page { max-width: 1000px; }
    .cr-patient { color: #455a64; margin-bottom: 12px; }
    .cr-culture { border: 1px solid #d9e2e7; border-radius: 6px; background: #fff; margin-bottom: 14px; }
    .cr-header { display: flex; flex-wrap: wrap; gap: 20px; padding: 8px 12px; background: #f5f7f8; border-bottom: 1px solid #d9e2e7; border-radius: 6px 6px 0 0; }
    .cr-header .cr-test { font-weight: bold; color: #00473f; }
    .cr-header .cr-label { color: #607d8b; }
    .cr-body { padding: 8px 12px; }
    .cr-row { display: grid; grid-template-columns: 200px minmax(0, 1fr); column-gap: 12px; padding: 2px 0; }
    .cr-row > span:first-child { color: #546e7a; }
    .cr-empty { color: #607d8b; padding: 16px 0; }
</style>

<div class="cr-page">
    <h2>Culture results</h2>
    <div class="cr-patient">${ ui.escapeHtml(patientIdentifier) } — ${ ui.escapeHtml(patient.personName ? patient.personName.fullName : "") }</div>
<% if (!authorized) { %>
    <div class="cr-empty">You do not have permission to view lab results.</div>
<% } else { %>
    <div id="cr-loading"><i class="icon-spinner icon-spin icon-2x"></i></div>
    <div id="cr-results"></div>
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
        const orderRep = "uuid,orderNumber,dateActivated,accessionNumber,fulfillerEncounter:(uuid),concept:(uuid,displayStringForLab,setMembers:(uuid,displayStringForLab))";
        const obsRep = "uuid,concept:(uuid),valueCoded:(uuid,display),valueNumeric,valueText,valueDatetime,comment,order:(uuid)";
        const encounterRep = "uuid,obs:(" + obsRep + ",groupMembers:(" + obsRep + "))";

        Promise.all([
            getJson(rest + "pihapps/config?v=custom:(" + configRep + ")"),
            getJson(rest + "rwandaemr/lab/antibiogram/config")
        ]).then(([config, antibiogramConfig]) => {
            const dateUtils = new PihAppsDateUtils(moment, config.dateFormat, config.dateTimeFormat);
            const antibiogram = new RwandaAntibiogram(jq, antibiogramConfig);
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
                const orders = (data.results || []).filter(o => antibiogram.isAntibiogramTest(o.concept.uuid) && o.fulfillerEncounter);
                if (orders.length === 0) {
                    jq("#cr-loading").hide();
                    jq("#cr-results").append(jq("<div>").addClass("cr-empty").text("No culture results have been reported for this patient."));
                    return;
                }
                const encounters = {};
                const antibiograms = {};
                const loads = [...new Set(orders.map(o => o.fulfillerEncounter.uuid))].map(uuid =>
                    getJson(rest + "encounter/" + uuid + "?v=custom:(" + encounterRep + ")").then(e => { encounters[uuid] = e; }));
                loads.push(getJson(rest + "rwandaemr/lab/antibiogram?" + jq.param({ order: orders.map(o => o.uuid) }, true))
                    .then(result => Object.keys(result).forEach(uuid => { antibiograms[uuid] = RwandaAntibiogram.fromRest(result[uuid]); })));
                return Promise.all(loads).then(() => {
                    jq("#cr-loading").hide();
                    orders.forEach(order => jq("#cr-results").append(renderCulture(order, encounters[order.fulfillerEncounter.uuid], antibiograms[order.uuid])));
                });
            });

            function renderCulture(order, encounter, isolates) {
                const allObs = (encounter?.obs || []).flatMap(o => o.groupMembers ? [o, ...o.groupMembers] : [o])
                    .filter(o => o.order?.uuid === order.uuid);
                const value = (o) => o.valueCoded?.display ?? (o.valueNumeric != null ? String(o.valueNumeric) : null)
                    ?? (o.valueDatetime ? dateUtils.formatDateWithTimeIfPresent(o.valueDatetime) : o.valueText) ?? "";
                const resultDate = allObs.find(o => o.concept.uuid === config.labOrderConfig.resultsDateQuestion?.uuid);
                const item = (label, text) => jq("<span>").append(jq("<span>").addClass("cr-label").text(label + ": ")).append(jq("<strong>").text(text ?? ""));
                const card = jq("<div>").addClass("cr-culture");
                card.append(jq("<div>").addClass("cr-header")
                    .append(jq("<span>").addClass("cr-test").text(order.concept.displayStringForLab))
                    .append(item("Ordered", dateUtils.formatAsDateWithoutTime(order.dateActivated)))
                    .append(item("Lab ID", order.accessionNumber))
                    .append(resultDate ? item("Result date", dateUtils.formatDateWithTimeIfPresent(resultDate.valueDatetime)) : null));
                const body = jq("<div>").addClass("cr-body");
                const members = (order.concept.setMembers || []).length > 0 ? order.concept.setMembers : [order.concept];
                members.forEach(member => {
                    const values = allObs.filter(o => o.concept.uuid === member.uuid && !o.groupMembers);
                    if (values.length > 0) {
                        body.append(jq("<div>").addClass("cr-row")
                            .append(jq("<span>").text(member.displayStringForLab))
                            .append(jq("<span>").text(values.map(o => value(o) + (o.comment ? " (" + o.comment + ")" : "")).join(", "))));
                    }
                });
                body.append(jq("<div>").addClass("cr-row")
                    .append(jq("<span>").text("Antibiogram"))
                    .append(jq("<span>").append(antibiogram.readOnly(isolates))));
                return card.append(body);
            }
        }).catch(error => {
            console.error(error);
            jq("#cr-loading").hide();
            jq("#cr-results").append(jq("<div>").addClass("cr-empty").text("Unable to load the culture results"));
        });
    });
</script>
<% } %>
