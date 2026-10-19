<%
    ui.decorateWith("appui", "standardEmrPage")
    ui.includeJavascript("uicommons", "moment-with-locales.min.js")
    ui.includeJavascript("uicommons", "datetimepicker/bootstrap-datetimepicker.min.js")
    if (org.openmrs.api.context.Context.getLocale().getLanguage() != "en") {
        ui.includeJavascript("uicommons", "datetimepicker/locales/bootstrap-datetimepicker.${ org.openmrs.api.context.Context.getLocale() }.js")
    }
    ui.includeCss("uicommons", "datetimepicker.css")
    ui.includeJavascript("pihapps", "dateUtils.js")
    ui.includeJavascript("pihapps", "patientUtils.js")
    ui.includeJavascript("pihapps", "formHelper.js")
    ui.includeJavascript("pihapps", "labs/labResultFields.js")
    ui.includeCss("pihapps", "labs/labs.css")

    def validateMode = (mode == "validate")
    def title = validateMode ? "Validate lab results" : "Enter lab results"
    def labOrderListLink = ui.pageLink("pihapps", "labs/labOrderList") + "?grouping=patient"
    def patientIdentifier = patient.patientIdentifier ? patient.patientIdentifier.identifier : ""
%>

<script type="text/javascript">
    var breadcrumbs = [
        { icon: "icon-home", link: '/' + OPENMRS_CONTEXT_PATH + '/index.htm' },
        { label: "${ ui.encodeJavaScript(ui.message("pihapps.labOrderList")) }", link: '${ ui.encodeJavaScript(labOrderListLink) }' },
        { label: "${ title }" }
    ];
</script>

<style>
    .lr-page { max-width: 1200px; }
    .lr-title { display: flex; flex-wrap: wrap; align-items: baseline; gap: 16px; margin-bottom: 4px; }
    .lr-title h2 { margin: 0; }
    .lr-patient { color: #455a64; font-size: 1.05em; }
    .lr-hint { color: #607d8b; font-size: 0.9em; margin: 4px 0 12px; }

    /* Choice between the order encounters of the patient */
    .lr-groups { display: flex; flex-wrap: wrap; gap: 8px; margin: 8px 0 16px; }
    .lr-group-choice { border: 1px solid #cfd8dc; border-radius: 6px; padding: 6px 10px; background: #fff; color: #37474f;
                       text-decoration: none; max-width: 380px; line-height: 1.3; }
    .lr-group-choice:hover { border-color: #00473f; text-decoration: none; }
    .lr-group-choice.lr-current { border-color: #00473f; box-shadow: inset 0 0 0 1px #00473f; background: #f1f8f6; }
    .lr-group-choice small { display: block; color: #607d8b; }

    .lr-toolbar { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin-bottom: 12px; }
    .lr-toolbar .lr-label { color: #455a64; font-weight: bold; }

    .lr-specimen { border: 1px solid #d9e2e7; border-radius: 6px; background: #fff; margin-bottom: 16px; }
    .lr-specimen-header { display: flex; flex-wrap: wrap; gap: 24px; padding: 8px 12px; background: #f5f7f8;
                          border-bottom: 1px solid #d9e2e7; border-radius: 6px 6px 0 0; }
    .lr-specimen-header .lr-label { color: #607d8b; }
    .lr-category { padding: 4px 12px 8px; }
    .lr-category + .lr-category { border-top: 1px solid #d9e2e7; }
    .lr-category-header { display: flex; align-items: center; justify-content: space-between; margin: 8px 0 2px; }
    .lr-category-header h4 { margin: 0; font-size: 1em; color: #00473f; text-transform: capitalize; }
    .lr-category-header button { font-size: 0.85em; padding: 2px 8px; }

    /* Every line of the table: test, result, normal range, status (or who entered the results) */
    .lr-row, .lr-specimen .result-row {
        display: grid; grid-template-columns: 230px 380px 130px minmax(0, 1fr); column-gap: 12px; align-items: center;
        margin: 0; padding: 3px 0; min-height: 32px;
    }
    .lr-specimen .result-row > span { flex: none; max-width: none; width: auto; padding: 0; }
    .lr-columns { color: #78909c; font-size: 0.8em; text-transform: uppercase; border-bottom: 1px solid #eceff1; min-height: 0; }
    .lr-order { border-bottom: 1px solid #f1f3f4; }
    .lr-order:last-child { border-bottom: none; }
    .lr-order .test-name, .lr-name { font-weight: bold; }
    .lr-panel-members .test-name { font-weight: normal; padding-left: 16px; }
    .lr-panel-members .result-row { min-height: 28px; padding: 1px 0; }
    .lr-status-cell { justify-self: end; }
    .lr-status-cell .btn { padding: 1px 8px; font-size: 0.85em; }
    .lr-not-performed .result-row .form-field-widgets { visibility: hidden; }
    .lr-not-performed .lr-panel-members { display: none; }
    .lr-reason { margin: 0 0 6px 242px; }
    .lr-note { color: #b26a00; font-size: 0.85em; margin: 0 0 4px 242px; }
    .lr-hidden { display: none; }

    /* Result fields: the same width for each kind of field, so that units and comments line up */
    .lr-specimen .obs-widget { display: inline-flex; flex-wrap: wrap; align-items: center; gap: 0 8px; }
    .lr-specimen input.result-numeric-input { width: 110px; min-width: 0; }
    .lr-specimen input.result-text-input, .lr-specimen select.result-value-field { width: 210px; min-width: 0; }
    .lr-specimen .result-units { display: inline-block; width: 90px; padding-left: 0; color: #546e7a; font-size: 0.9em; }
    .lr-specimen .comment-toggle { font-size: 0.8em; color: #90a4ae; }
    .lr-specimen .comment-section { flex-basis: 100%; }
    .lr-specimen .comment-section textarea { width: 330px; margin-top: 2px; }
    .lr-specimen .multi-value-add { font-size: 0.8em; padding: 0 6px; }
    .lr-specimen .reference-range, .lr-range { color: #546e7a; font-size: 0.9em; }
    .lr-specimen .form-field-widgets p { margin: 0; }
    .field-error { display: inline; padding-left: 4px; font-weight: bold; font-size: 0.85em; }
    .field-error:empty { display: none; }
    .field-error.abnormal-value, .field-error.critical-value { color: #e65100; }
    .field-error.error-value { color: #c62828; }

    /* Validation */
    .lr-value.lr-abnormal { color: #e65100; font-weight: bold; }
    .lr-value.lr-critical { color: #c62828; font-weight: bold; }
    .lr-entered-by { color: #607d8b; font-size: 0.85em; }
    .lr-select { margin-right: 6px; }

    .lr-errors { color: #c62828; margin: 8px 0; }
    .lr-reminder { border: 1px solid #ffb74d; background: #fff8e1; border-radius: 6px; padding: 10px 12px; margin: 8px 0; }
    .lr-reminder p { margin: 0 0 8px; }
    .lr-actions { display: flex; flex-wrap: wrap; gap: 8px; align-items: flex-start; margin: 12px 0; }
    .lr-actions textarea { min-width: 320px; }
    .lr-empty { color: #607d8b; padding: 16px 0; }
</style>

<div class="lr-page">
    <div class="lr-title">
        <h2>${ title }</h2>
        <span class="lr-patient">${ ui.escapeHtml(patientIdentifier) } — ${ ui.escapeHtml(patient.personName ? patient.personName.fullName : "") }</span>
    </div>

<% if (!authorized) { %>
    <div class="lr-empty">You do not have permission to ${ validateMode ? "validate" : "enter" } lab results.</div>
    <a class="button" href="${ labOrderListLink }">Back to lab orders</a>
<% } else { %>
    <div id="lr-groups" class="lr-groups"></div>
    <div id="lr-loading"><i class="icon-spinner icon-spin icon-2x"></i></div>
    <div id="lr-content"></div>
<% } %>
</div>

<% if (authorized) { %>
<script type="text/javascript">
    jq(function() {
        const validateMode = ${ validateMode };
        const patientUuid = '${ patient.uuid }';
        const requestedOrderEncounter = '${ ui.encodeJavaScript(orderEncounter ?: "") }';
        const labOrderListLink = '${ ui.encodeJavaScript(labOrderListLink) }';
        const locale = window.sessionContext?.locale ?? 'en';
        moment.locale(locale);

        const messages = {
            estimated: '${ ui.encodeJavaScript(ui.message("pihapps.estimated")) }',
            valueMustBeInteger: '${ ui.encodeJavaScript(ui.message("pihapps.valueCannotBeDecimal")) }',
            abnormalValue: '${ ui.encodeJavaScript(ui.message("pihapps.abnormalValue")) }',
            criticalValue: '${ ui.encodeJavaScript(ui.message("pihapps.criticalValue")) }',
            minimumAllowedValue: '${ ui.encodeJavaScript(ui.message("pihapps.minimumAllowedValue")) }',
            maximumAllowedValue: '${ ui.encodeJavaScript(ui.message("pihapps.maximumAllowedValue")) }',
            resultDateCannotBeFuture: '${ ui.encodeJavaScript(ui.message("pihapps.resultDateCannotBeFuture")) }',
            resultDateCannotBeBeforeSpecimenDate: '${ ui.encodeJavaScript(ui.message("pihapps.resultDateCannotBeBeforeSpecimenDate")) }',
            errorsWithOneOrMoreFields: '${ ui.encodeJavaScript(ui.message("pihapps.errorsWithOneOrMoreFields")) }',
            reasonRequired: '${ ui.encodeJavaScript(ui.message("pihapps.reasonRequired")) }',
            addComment: '${ ui.encodeJavaScript(ui.message("pihapps.addComment")) }',
            removeComment: '${ ui.encodeJavaScript(ui.message("pihapps.removeComment")) }',
            removeResult: '${ ui.encodeJavaScript(ui.message("pihapps.removeResult")) }',
            addAnotherResult: '${ ui.encodeJavaScript(ui.message("pihapps.addAnotherResult")) }',
            completed: '${ ui.encodeJavaScript(ui.message("pihapps.fulfillerStatus.COMPLETED")) }',
            inProgress: '${ ui.encodeJavaScript(ui.message("pihapps.fulfillerStatus.IN_PROGRESS")) }',
            notPerformed: '${ ui.encodeJavaScript(ui.message("pihapps.fulfillerStatus.EXCEPTION")) }',
            labId: '${ ui.encodeJavaScript(ui.message("pihapps.labId")) }',
            specimenCollectionDate: '${ ui.encodeJavaScript(ui.message("pihapps.specimenCollectionDate")) }',
            specimenReceivedDate: '${ ui.encodeJavaScript(ui.message("pihapps.specimenReceivedDate")) }',
            resultDate: '${ ui.encodeJavaScript(ui.message("pihapps.resultDate")) }',
            reason: '${ ui.encodeJavaScript(ui.message("pihapps.reason")) }',
            labTest: '${ ui.encodeJavaScript(ui.message("pihapps.labTest")) }',
            result: '${ ui.encodeJavaScript(ui.message("pihapps.result")) }',
            normalRange: '${ ui.encodeJavaScript(ui.message("pihapps.normalRange")) }',
            status: '${ ui.encodeJavaScript(ui.message("pihapps.status")) }',
            save: '${ ui.encodeJavaScript(ui.message("coreapps.save")) }',
        };

        const rest = openmrsContextPath + "/ws/rest/v1/";
        const getJson = (url) => new Promise((resolve, reject) => jq.getJSON(url).done(resolve).fail(reject));
        const postJson = (url, payload) => new Promise((resolve, reject) => jq.ajax({
            url: url, type: "POST", contentType: "application/json; charset=utf-8", data: JSON.stringify(payload), dataType: "json"
        }).done(resolve).fail(reject));
        const errorMessage = (xhr) => {
            const error = xhr?.responseJSON?.error ?? xhr?.responseJSON;
            return error?.translatedMessage ?? error?.message ?? "Unable to save";
        };
        const escapeHtml = (text) => jq("<span>").text(text ?? "").html();

        // Leaving the page with unsaved results asks first
        let dirty = false;
        window.addEventListener("beforeunload", (event) => {
            if (dirty) {
                event.preventDefault();
                event.returnValue = "";
            }
        });

        const configRep = "dateFormat,dateTimeFormat,labOrderConfig:(labTestOrderType:(uuid)," +
            "availableLabTestsByCategory:(category:(uuid,displayStringForLab),labTests:(uuid))," +
            "labIdentifierConcept:(uuid),specimenReceivedDateQuestion:(uuid),resultsDateQuestion:(uuid,datatype:(name))," +
            "reasonTestNotPerformedQuestion:(uuid,datatype:(name),answers:(uuid,display)),collectResultComments,requireResultValidation)";
        const orderRep = "uuid,orderNumber,dateActivated,dateStopped,fulfillerStatus,fulfillerComment,accessionNumber,urgency," +
            "patient:(uuid),encounter:(uuid),fulfillerEncounter:(uuid),concept:(uuid,displayStringForLab)";
        const obsRep = "uuid,concept:(uuid,datatype:(name)),value,valueCoded:(uuid,display),valueDatetime,valueText,valueNumeric,comment,formNamespaceAndPath,order:(uuid)";
        const encounterRep = "uuid,encounterDatetime,encounterType:(uuid),location:(uuid,display)," +
            "encounterProviders:(provider:(uuid,display),encounterRole:(uuid,display)),obs:(" + obsRep + ",groupMembers:(" + obsRep + "))";

        getJson(rest + "pihapps/config?v=custom:(" + configRep + ")").then(config => {
            const labConfig = config.labOrderConfig;
            const dateUtils = new PihAppsDateUtils(moment, config.dateFormat, config.dateTimeFormat);

            // The category of each test, so that each specimen shows a section per category
            const categoryOfTest = {};
            const categoryOrder = [];
            labConfig.availableLabTestsByCategory.forEach(c => {
                categoryOrder.push(c.category.uuid);
                c.labTests.forEach(t => { categoryOfTest[t.uuid] = c.category; });
            });
            const otherCategory = { uuid: "other", displayStringForLab: "Other" };

            const statuses = validateMode ? ["AWAITING_VALIDATION"] : ["IN_FULFILLMENT", "AWAITING_VALIDATION"];
            const orderQuery = jq.param({
                patient: patientUuid,
                orderType: labConfig.labTestOrderType?.uuid,
                orderFulfillmentStatus: statuses,
                limit: 100,
                v: "custom:(" + orderRep + ")"
            }, true);

            return getJson(rest + "pihapps/labOrder?" + orderQuery).then(data => {
                const orders = (data.results || []).filter(o => o.fulfillerEncounter && !o.dateStopped);
                if (orders.length === 0) {
                    jq("#lr-loading").hide();
                    jq("#lr-content").append(jq("<div>").addClass("lr-empty").text(validateMode
                        ? "No lab results are awaiting validation for this patient."
                        : "No collected lab tests are awaiting results for this patient."))
                        .append(jq("<a>").addClass("button").attr("href", labOrderListLink).text("Back to lab orders"));
                    return;
                }

                // The tests ordered in one encounter are shown together, with every specimen collected for them
                const groups = [];
                orders.forEach(order => {
                    const key = order.encounter?.uuid ?? order.uuid;
                    let group = groups.find(g => g.key === key);
                    if (!group) {
                        group = { key: key, orders: [], specimens: [] };
                        groups.push(group);
                    }
                    group.orders.push(order);
                    let specimen = group.specimens.find(s => s.encounterUuid === order.fulfillerEncounter.uuid);
                    if (!specimen) {
                        specimen = { encounterUuid: order.fulfillerEncounter.uuid, orders: [] };
                        group.specimens.push(specimen);
                    }
                    specimen.orders.push(order);
                });
                groups.forEach(g => {
                    g.orderedOn = g.orders.map(o => o.dateActivated).sort()[0];
                    g.stat = g.orders.some(o => o.urgency === 'STAT');
                });
                groups.sort((a, b) => (b.stat - a.stat) || (a.orderedOn < b.orderedOn ? -1 : a.orderedOn > b.orderedOn ? 1 : 0));
                const group = groups.find(g => g.key === requestedOrderEncounter) ?? groups[0];
                renderGroupChoice(groups, group);

                const conceptUuids = [...new Set(group.orders.map(o => o.concept.uuid))];
                const orderables = {};
                const loads = conceptUuids.map(uuid => getJson(rest + "concept/" + uuid + "?v=custom:(" + PihAppsLabResultFields.orderableRep + ")")
                    .then(concept => { orderables[uuid] = concept; }));
                group.specimens.forEach(specimen => {
                    loads.push(getJson(rest + "encounter/" + specimen.encounterUuid + "?v=custom:(" + encounterRep + ")")
                        .then(encounter => { specimen.encounter = encounter; }));
                });
                let validation = {};
                if (validateMode) {
                    loads.push(getJson(rest + "rwandaemr/lab/resultValidation?" + jq.param({ order: group.orders.map(o => o.uuid) }, true))
                        .then(v => { validation = v; }));
                }

                return Promise.all(loads).then(() => {
                    jq("#lr-loading").hide();
                    jq("#lr-content").append(validateMode
                        ? renderValidation(group, orderables, validation)
                        : renderEntry(group, orderables));
                });
            });

            // ---- Shared ----

            function renderGroupChoice(groups, current) {
                if (groups.length < 2) {
                    return;
                }
                groups.forEach(g => {
                    const labIds = [...new Set(g.orders.map(o => o.accessionNumber).filter(id => id))];
                    const tests = g.orders.map(o => o.concept.displayStringForLab);
                    const href = "?" + jq.param({ patient: patientUuid, mode: validateMode ? "validate" : "enter", orderEncounter: g.key });
                    const choice = jq("<a>").addClass("lr-group-choice").attr("href", href)
                        .toggleClass("lr-current", g === current)
                        .append(jq("<strong>").html((g.stat ? '<i class="fas fa-fw fa-exclamation" style="color:red;"></i>' : '')
                            + escapeHtml(messages.labId + " " + labIds.join(", "))))
                        .append(jq("<small>").text(dateUtils.formatAsDateWithoutTime(g.orderedOn) + " · "
                            + tests.slice(0, 4).join(", ") + (tests.length > 4 ? " +" + (tests.length - 4) : "")));
                    jq("#lr-groups").append(choice);
                });
            }

            function groupByCategory(orders) {
                const groups = {};
                orders.forEach(order => {
                    const category = categoryOfTest[order.concept.uuid] ?? otherCategory;
                    (groups[category.uuid] = groups[category.uuid] || { category: category, orders: [] }).orders.push(order);
                });
                return Object.values(groups).sort((a, b) => {
                    const ai = categoryOrder.indexOf(a.category.uuid), bi = categoryOrder.indexOf(b.category.uuid);
                    return (ai < 0 ? 9999 : ai) - (bi < 0 ? 9999 : bi);
                });
            }

            function specimenHeader(specimen, obsValue) {
                const labId = specimen.orders[0].accessionNumber ?? obsValue(labConfig.labIdentifierConcept?.uuid)?.valueText ?? "";
                const received = obsValue(labConfig.specimenReceivedDateQuestion?.uuid)?.valueDatetime;
                const item = (label, value) => jq("<span>").append(jq("<span>").addClass("lr-label").text(label + ": ")).append(jq("<strong>").text(value ?? ""));
                return jq("<div>").addClass("lr-specimen-header")
                    .append(item(messages.labId, labId))
                    .append(item(messages.specimenCollectionDate, dateUtils.formatDateWithTimeIfPresent(specimen.encounter.encounterDatetime)))
                    .append(received ? item(messages.specimenReceivedDate, dateUtils.formatDateWithTimeIfPresent(received)) : null);
            }

            function testName(order) {
                return (order.urgency === 'STAT' ? '<i class="fas fa-fw fa-exclamation" style="color:red;"></i>' : '')
                    + escapeHtml(order.concept.displayStringForLab);
            }

            function columnsRow(lastColumn) {
                return jq("<div>").addClass("lr-row lr-columns")
                    .append(jq("<span>").text(messages.labTest))
                    .append(jq("<span>").text(messages.result))
                    .append(jq("<span>").text(messages.normalRange))
                    .append(jq("<span>").addClass("lr-status-cell").text(lastColumn));
            }

            function showErrors(container, errors) {
                container.find(".lr-errors").first().empty();
                errors.forEach(e => container.find(".lr-errors").first().append(jq("<div>").text(e)));
            }

            // ---- Entering results: every test of the order encounter on one form, saved together ----

            function renderEntry(group, orderables) {
                const page = jq("<div>");
                if (labConfig.requireResultValidation) {
                    page.append(jq("<div>").addClass("lr-hint").text("Tests stay In progress, and their results are kept but not reported, " +
                        "until you mark them Completed. Completed tests are sent for validation."));
                }

                // One result date, recorded on each test that is completed. It defaults to today.
                const resultDateQuestion = labConfig.resultsDateQuestion;
                const resultDatePicker = jq("<span>");
                const toolbar = jq("<div>").addClass("lr-toolbar")
                    .append(jq("<span>").addClass("lr-label").text(messages.resultDate))
                    .append(resultDatePicker);
                page.append(toolbar);

                const blocks = [];
                const specimenForms = [];
                let existingResultDate = null;
                group.specimens.forEach((specimen, specimenIndex) => {
                    const encounter = specimen.encounter;
                    const formHelper = new FormHelper({
                        jq: jq, moment: moment, locale: locale,
                        dateFormat: config.dateFormat, dateTimeFormat: config.dateTimeFormat,
                        formName: "pihapps^labResultForm", encounter: encounter, patientUuid: patientUuid
                    });
                    const obsFor = (conceptUuid, order) => formHelper.initialObs.find(o => o.concept.uuid === conceptUuid && o.order?.uuid === order.uuid);
                    const specimenBlocks = [];
                    const card = jq("<div>").addClass("lr-specimen");
                    card.append(specimenHeader(specimen, (conceptUuid) => formHelper.getInitialObsValue(conceptUuid)));

                    groupByCategory(specimen.orders).forEach(categoryGroup => {
                        const section = jq("<div>").addClass("lr-category");
                        const markAllCompleted = jq("<button>").attr({ type: "button", tabindex: -1 }).text("Mark all completed");
                        section.append(jq("<div>").addClass("lr-category-header")
                            .append(jq("<h4>").text(categoryGroup.category.displayStringForLab.toLowerCase()))
                            .append(markAllCompleted));
                        section.append(columnsRow(messages.status));

                        const categoryBlocks = [];
                        categoryGroup.orders.forEach(order => {
                            existingResultDate = existingResultDate ?? obsFor(resultDateQuestion.uuid, order)?.valueDatetime;
                            const block = renderEntryOrder(order, orderables[order.concept.uuid], formHelper, obsFor, "lr-" + specimenIndex + "-" + order.uuid);
                            section.append(block.element);
                            categoryBlocks.push(block);
                        });
                        markAllCompleted.on("click", () => categoryBlocks.forEach(b => b.setStatus("COMPLETED")));
                        specimenBlocks.push(...categoryBlocks);
                        card.append(section);
                    });
                    blocks.push(...specimenBlocks);
                    specimenForms.push({ specimen: specimen, formHelper: formHelper, blocks: specimenBlocks });
                    page.append(card);
                });

                const datePicker = specimenForms[0].formHelper.createDatePickerWidget({
                    id: "lr-result-date",
                    locale: locale,
                    useTime: resultDateQuestion.datatype.name === 'Datetime',
                    initialValue: existingResultDate ?? moment().startOf('day').format()
                });
                datePicker.find(".add-on, .icon-remove").attr("tabindex", -1);
                resultDatePicker.append(datePicker);

                const errors = jq("<div>").addClass("lr-errors");
                const reminder = jq("<div>").addClass("lr-reminder lr-hidden");
                const saveButton = jq("<button>").addClass("confirm").text(messages.save);
                const backLink = jq("<a>").addClass("button").attr("href", labOrderListLink).text("Back to lab orders");
                page.append(errors).append(reminder).append(jq("<div>").addClass("lr-actions").append(saveButton).append(backLink));

                page.on("change input", ":input", () => { dirty = true; });

                const save = (markCompleted) => {
                    reminder.addClass("lr-hidden").empty();
                    markCompleted.forEach(b => b.setStatus("COMPLETED"));
                    const resultDate = datePicker.find("#lr-result-date-field").val();
                    const problems = [];
                    if (resultDate && moment(resultDate).isAfter(moment())) {
                        problems.push(messages.resultDateCannotBeFuture);
                    }
                    specimenForms.forEach(form => {
                        // Compare calendar dates only, as the pihapps results form does
                        if (resultDate && form.specimen.encounter.encounterDatetime.substring(0, 10) > resultDate.substring(0, 10)
                            && form.blocks.some(b => b.status() === 'COMPLETED')) {
                            problems.push(messages.resultDateCannotBeBeforeSpecimenDate);
                        }
                    });
                    blocks.forEach(b => {
                        if (b.status() === 'EXCEPTION' && !b.reasonValue()) {
                            problems.push(b.order.concept.displayStringForLab + ": " + messages.reasonRequired);
                        }
                    });
                    if (page.find(".field-error.error-value").filter((i, e) => jq(e).text().trim()).length > 0) {
                        problems.push(messages.errorsWithOneOrMoreFields);
                    }
                    showErrors(page, [...new Set(problems)]);
                    if (problems.length > 0) {
                        return;
                    }

                    blocks.forEach(b => b.prepareForSave(resultDate));
                    saveButton.attr("disabled", "disabled");
                    const saved = [];
                    // Each specimen is saved with its own encounter, one after the other
                    specimenForms.reduce((previous, form) => previous.then(() => {
                        const payload = {
                            encounter: form.formHelper.constructEncounterPayload(),
                            orders: form.blocks.map(b => ({ uuid: b.order.uuid, fulfillerStatus: b.status() }))
                        };
                        return postJson(rest + "encounterFulfillingOrders/" + form.specimen.encounterUuid, payload)
                            .then(() => saved.push(form.specimen.orders[0].accessionNumber));
                    }), Promise.resolve()).then(() => {
                        dirty = false;
                        emr.successMessage("Lab results saved");
                        window.location.reload();
                    }).catch(xhr => {
                        saveButton.removeAttr("disabled");
                        showErrors(page, [errorMessage(xhr)].concat(saved.length > 0 ? ["Saved: " + messages.labId + " " + saved.join(", ")] : []));
                    });
                };

                saveButton.on("click", () => {
                    // Tests with results that are still in progress are probably meant to be completed: ask
                    const unfinished = blocks.filter(b => b.status() === 'IN_PROGRESS' && b.hasResults());
                    if (unfinished.length === 0) {
                        save([]);
                        return;
                    }
                    const markAndSave = jq("<button>").addClass("confirm").text("Mark completed and save");
                    const saveInProgress = jq("<button>").text("Save as in progress");
                    reminder.empty().removeClass("lr-hidden")
                        .append(jq("<p>").text(unfinished.length + " test" + (unfinished.length > 1 ? "s have" : " has")
                            + " results but " + (unfinished.length > 1 ? "are" : "is") + " still In progress: "
                            + unfinished.map(b => b.order.concept.displayStringForLab).join(", ") + ". Mark "
                            + (unfinished.length > 1 ? "them" : "it") + " Completed?"))
                        .append(markAndSave).append(" ").append(saveInProgress);
                    markAndSave.on("click", () => save(unfinished));
                    saveInProgress.on("click", () => save([]));
                    markAndSave.focus();
                });
                return page;
            }

            function renderEntryOrder(order, orderable, formHelper, obsFor, idPrefix) {
                const element = jq("<div>").addClass("lr-order");
                const isPanel = orderable.setMembers.length > 0;

                // Results already submitted for validation open as completed; anything else starts in progress
                const initialStatus = order.fulfillerStatus === 'EXCEPTION' ? 'EXCEPTION'
                    : (order.fulfillerComment === PihAppsPatientUtils.RESULTS_AWAITING_VALIDATION ? 'COMPLETED' : 'IN_PROGRESS');
                const statusOptions = [
                    { value: "IN_PROGRESS", display: messages.inProgress },
                    { value: "COMPLETED", display: messages.completed },
                    { value: "EXCEPTION", display: messages.notPerformed }
                ];
                const statusWidget = formHelper.createSelectWidget({
                    id: idPrefix + "-status", options: statusOptions, initialValue: initialStatus, includeEmptyOption: false
                });
                const statusCell = jq("<span>").addClass("lr-status-cell").append(statusWidget);
                formHelper.displaySelectWidgetAsButtons(statusWidget, { buttonClass: "btn-outline-secondary btn-sm" });
                statusCell.find(".select-buttons button").each((i, button) => {
                    jq(button).attr({ type: "button", tabindex: -1, "data-value": statusOptions[i].value });
                });

                const results = jq("<div>").addClass("lr-results");
                PihAppsLabResultFields.render({
                    jq: jq,
                    formHelper: formHelper,
                    order: order,
                    orderable: orderable,
                    container: results,
                    idPrefix: idPrefix + "-",
                    collectResultComments: labConfig.collectResultComments,
                    messages: messages
                });
                results.find(".comment-toggle, .multi-value-add, .multi-value-remove").attr("tabindex", -1);

                if (isPanel) {
                    // The panel on one line with its status, and its tests beneath it
                    element.append(jq("<div>").addClass("lr-row")
                        .append(jq("<span>").addClass("lr-name").attr("title", order.orderNumber).html(testName(order)))
                        .append(jq("<span>")).append(jq("<span>")).append(statusCell));
                    element.append(results.addClass("lr-panel-members"));
                }
                else {
                    // A single test is one line: its name, result, normal range and status
                    const row = results.find(".result-row").first();
                    row.find(".test-name").html(testName(order)).attr("title", order.orderNumber);
                    row.append(statusCell);
                    element.append(results);
                }

                if (order.fulfillerComment && order.fulfillerComment.indexOf("Returned for correction") === 0) {
                    element.append(jq("<div>").addClass("lr-note").text(order.fulfillerComment));
                }

                const reasonQuestion = labConfig.reasonTestNotPerformedQuestion;
                const reasonSection = jq("<div>").addClass("lr-reason")
                    .append(jq("<span>").addClass("lr-label").text(messages.reason + " "))
                    .append(formHelper.createObsWidget(reasonQuestion, {
                        id: idPrefix + "-reason",
                        orderUuid: order.uuid,
                        initialObsUuid: obsFor(reasonQuestion.uuid, order)?.uuid ?? null
                    }));
                element.append(reasonSection);

                // Each completed test records the result date chosen for the page
                const resultDateQuestion = labConfig.resultsDateQuestion;
                const resultDateWidget = formHelper.createObsWidget(resultDateQuestion, {
                    id: idPrefix + "-result-date",
                    orderUuid: order.uuid,
                    initialObsUuid: obsFor(resultDateQuestion.uuid, order)?.uuid ?? null
                });
                element.append(jq("<div>").addClass("lr-hidden").append(resultDateWidget));
                const resultDateField = resultDateWidget.find(".result-value-field");

                const status = () => statusWidget.val();
                const applyStatus = () => {
                    element.toggleClass("lr-not-performed", status() === 'EXCEPTION');
                    reasonSection.toggle(status() === 'EXCEPTION');
                };
                statusWidget.on("change", () => { dirty = true; applyStatus(); });
                applyStatus();

                const resultFields = () => results.find(".result-value-field");
                return {
                    order: order,
                    element: element,
                    status: status,
                    setStatus: (value) => statusCell.find(".select-buttons button[data-value='" + value + "']").click(),
                    reasonValue: () => reasonSection.find(".result-value-field").val(),
                    hasResults: () => resultFields().filter((i, e) => jq(e).is(":checkbox") ? jq(e).is(":checked") : !!jq(e).val()).length > 0,
                    prepareForSave: (resultDate) => {
                        // Results are kept unless the test was not performed; the reason only when it was not
                        if (status() === 'EXCEPTION') {
                            results.find(":input").not("button").val("");
                            results.find(":checkbox").prop("checked", false);
                            resultDateField.val("");
                        }
                        else {
                            reasonSection.find(":input").not("button").val("");
                            if (status() === 'COMPLETED') {
                                resultDateField.val(resultDate);
                            }
                        }
                    }
                };
            }

            // ---- Validating results: read only, validated or returned for correction together ----

            function renderValidation(group, orderables, validation) {
                const page = jq("<div>");
                group.specimens.forEach(specimen => {
                    const encounter = specimen.encounter;
                    const allObs = (encounter.obs || []).flatMap(o => o.groupMembers ? [o, ...o.groupMembers] : [o]);
                    const card = jq("<div>").addClass("lr-specimen");
                    card.append(specimenHeader(specimen, (conceptUuid) => allObs.find(o => o.concept.uuid === conceptUuid)));

                    groupByCategory(specimen.orders).forEach(categoryGroup => {
                        const section = jq("<div>").addClass("lr-category");
                        const selectAll = jq("<input>").attr("type", "checkbox").addClass("lr-select").prop("checked", true);
                        section.append(jq("<div>").addClass("lr-category-header")
                            .append(jq("<h4>").append(selectAll).append(document.createTextNode(categoryGroup.category.displayStringForLab.toLowerCase()))));
                        selectAll.on("change", () => section.find(".lr-select-order:enabled").prop("checked", selectAll.prop("checked")));
                        section.append(columnsRow("Entered by"));

                        categoryGroup.orders.forEach(order => {
                            const info = validation[order.uuid] || {};
                            const orderable = orderables[order.concept.uuid];
                            const isPanel = orderable.setMembers.length > 0;
                            const tests = isPanel ? orderable.setMembers : [orderable];
                            const checkbox = jq("<input>").attr("type", "checkbox").addClass("lr-select lr-select-order")
                                .attr("data-order-uuid", order.uuid).prop("checked", !!info.canValidate).prop("disabled", !info.canValidate);
                            const nameCell = jq("<span>").addClass("lr-name").attr("title", order.orderNumber).append(checkbox).append(testName(order));
                            const enteredBy = jq("<span>").addClass("lr-status-cell lr-entered-by").text((info.enteredBy || []).join(", "));
                            const element = jq("<div>").addClass("lr-order");

                            const valueRow = (concept, nameContent) => {
                                const values = allObs.filter(o => o.concept.uuid === concept.uuid && o.order?.uuid === order.uuid && !o.groupMembers);
                                const valueCell = jq("<span>").addClass("lr-value").html(values.map(o => displayValue(o, concept)).join("<br>"));
                                const rangeCell = jq("<span>").addClass("lr-range");
                                if (concept.datatype.name === 'Numeric' && values.length > 0) {
                                    PihAppsLabResultFields.fetchReferenceRange(jq, order, concept, (refRange) => {
                                        rangeCell.text(PihAppsLabResultFields.formatReferenceRange(refRange));
                                        const flag = values.map(o => PihAppsLabResultFields.validateNumericResult(messages, concept, refRange, o.valueNumeric)).find(f => f);
                                        if (flag) {
                                            valueCell.addClass(flag.type === 'critical' ? "lr-critical" : "lr-abnormal").attr("title", flag.message);
                                        }
                                    });
                                }
                                return { row: jq("<div>").addClass("lr-row").append(nameContent).append(valueCell).append(rangeCell), hasValues: values.length > 0 };
                            };

                            if (isPanel) {
                                element.append(jq("<div>").addClass("lr-row").append(nameCell).append(jq("<span>")).append(jq("<span>")).append(enteredBy));
                                const members = jq("<div>").addClass("lr-panel-members");
                                tests.forEach(concept => {
                                    const line = valueRow(concept, jq("<span>").addClass("test-name").text(concept.displayStringForLab));
                                    if (line.hasValues) {
                                        members.append(line.row);
                                    }
                                });
                                element.append(members);
                            }
                            else {
                                element.append(valueRow(tests[0], nameCell).row.append(enteredBy));
                            }
                            if (info.enteredByCurrentUser) {
                                element.append(jq("<div>").addClass("lr-note").text("You entered or changed these results, so another user must validate them."));
                            }
                            section.append(element);
                        });
                        card.append(section);
                    });
                    page.append(card);
                });

                const errors = jq("<div>").addClass("lr-errors");
                const validateButton = jq("<button>").addClass("confirm").text("Validate selected");
                const reason = jq("<textarea>").attr({ rows: 2, placeholder: "Reason, required to return results for correction" });
                const returnButton = jq("<button>").text("Return selected for correction");
                const backLink = jq("<a>").addClass("button").attr("href", labOrderListLink).text("Back to lab orders");
                page.append(errors).append(jq("<div>").addClass("lr-actions").append(validateButton).append(reason).append(returnButton).append(backLink));

                const decide = (action) => {
                    const selected = page.find(".lr-select-order:checked").map((i, e) => jq(e).attr("data-order-uuid")).get();
                    if (selected.length === 0) {
                        showErrors(page, ["Select the tests to " + (action === "VALIDATE" ? "validate" : "return")]);
                        return;
                    }
                    if (action === "RETURN" && !reason.val().trim()) {
                        showErrors(page, [messages.reasonRequired]);
                        return;
                    }
                    page.find(".lr-actions button").attr("disabled", "disabled");
                    postJson(rest + "rwandaemr/lab/resultValidation", { orders: selected, action: action, reason: reason.val() }).then(() => {
                        emr.successMessage(action === "VALIDATE" ? "Results validated" : "Results returned for correction");
                        window.location.reload();
                    }).catch(xhr => {
                        page.find(".lr-actions button").removeAttr("disabled");
                        showErrors(page, [errorMessage(xhr)]);
                    });
                };
                validateButton.on("click", () => decide("VALIDATE"));
                returnButton.on("click", () => decide("RETURN"));
                return page;
            }

            function displayValue(obs, concept) {
                let value;
                if (obs.valueCoded) {
                    value = obs.valueCoded.display;
                }
                else if (obs.valueNumeric != null) {
                    value = obs.valueNumeric + (concept.units ? " " + concept.units : "");
                }
                else if (obs.valueDatetime) {
                    value = dateUtils.formatDateWithTimeIfPresent(obs.valueDatetime);
                }
                else {
                    value = obs.valueText ?? (typeof obs.value === 'object' ? obs.value?.display : obs.value) ?? "";
                }
                return escapeHtml(value) + (obs.comment ? ' <span class="lr-entered-by">(' + escapeHtml(obs.comment) + ')</span>' : '');
            }
        }).catch(error => {
            console.error(error);
            jq("#lr-loading").hide();
            jq("#lr-content").append(jq("<div>").addClass("lr-errors").text("Unable to load the lab results"));
        });
    });
</script>
<% } %>
