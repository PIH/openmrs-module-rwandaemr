<%
    ui.decorateWith("appui", "standardEmrPage")
    ui.includeJavascript("uicommons", "datatables/jquery.dataTables.min.js")
    def returnUrl = ui.pageLink("rwandaemr", "patient/encounterList", [ "patientId": encounter.patient.uuid ])
%>

${ ui.includeFragment("coreapps", "patientHeader", [ patient: encounter.patient ]) }

<style>
    .obs-group {
        border: 1px solid #ccc;
    }
    .order-section {
        border-bottom: 1px solid #ccc;
        margin-bottom: 10px;
    }
    .order-action {
        font-weight: bold;
    }
</style>

<fieldset id="encounter-details">
    <legend>Encounter Details</legend>
    <div id="encounter-date-section" class="row col-12">
        <div class="label col-4">Encounter Date</div>
        <div class="value col-8">${ui.format(encounter.encounterDatetime)}</div>
    </div>
    <div id="encounter-type-section" class="row col-12">
        <div class="label col-4">Encounter Type</div>
        <div class="value col-8">${ui.format(encounter.encounterType)}</div>
    </div>
    <div id="encounter-location-section" class="row col-12">
        <div class="label col-4">Encounter Location</div>
        <div class="value col-8">${ui.format(encounter.location)}</div>
    </div>
    <div id="encounter-provider-section" class="row col-12">
        <div class="label col-4">Providers</div>
        <div class="value col-8">
            <% encounter.encounterProviders.eachWithIndex{ p, i -> %>
                ${i == 0 ? "": ", "}
                ${ui.format(p.provider)}
            <% } %>
        </div>
    </div>
    <div id="encounter-creator-section" class="row col-12">
        <div class="label col-4">Created By</div>
        <div class="value col-8">${ui.format(encounter.creator.person)} on ${ui.format(encounter.dateCreated)}</div>
    </div>
</fieldset>

<fieldset id="obs-details">
    <legend>Observations</legend>
    <% encounter.getObsAtTopLevel(false).each{obs -> %>
        <div class="top-level-obs-section row col-12">
            <div class="label col-4">${ui.format(obs.concept)}</div>
            <div class="value col-8">${ui.includeFragment("rwandaemr", "patient/obsValue", ["obs": obs, "padding": 0])}</div>
        </div>
    <% } %>
</fieldset>

<fieldset id="order-details">
    <legend>Orders</legend>
    <% encounter.orders.each{order ->
        def isDrugOrder = (order.class.simpleName == 'DrugOrder') %>
        <div class="order-section">
            <div class="order-type=section row col-12">
                <div class="label col-4">${ui.format(order.orderType)}</div>
                <div class="value col-8">
                    <span class="order-action">
                        ${order.action.name()}
                    </span>
                    <% if (isDrugOrder) {
                        def dosing =  ""
                        if (order.dose || order.doseUnits) {
                            dosing += order.dose + " " + ui.format(order.doseUnits)
                        }
                        if (order.route) {
                            dosing += (dosing == "" ? "" : ", ") + ui.format(order.route)
                        }
                        if (order.frequency) {
                            dosing += (dosing == "" ? "" : ", ") + ui.format(order.frequency)
                        }
                        if (order.duration || order.durationUnits) {
                            dosing += (dosing == "" ? "" : ", ") + order.duration + " " + ui.format(order.durationUnits)
                        }
                        if (order.asNeeded) {
                            dosing += (dosing == "" ? "" : ", ") + "PRN"
                        }
                        if (order.instructions) {
                            dosing += (dosing == "" ? "" : ", ") + order.instructions
                        }
                    %>
                        ${ui.format(order.drug ?: order.concept)} - ${dosing}
                    <% } else { %>
                        ${ui.format(order.concept)}
                    <% } %>
                    <br/>
                    <span class="order-dates">
                        ${ui.format(order.dateActivated)}
                        ${order.effectiveStopDate && order.effectiveStopDate != order.dateActivated ? " - " + ui.format(order.effectiveStopDate) : ""}
                    </span>
                </div>
            </div>
        </div>
    <% } %>
</fieldset>

<br/>

<div>
    <input id="return-button" type="button" value="${ ui.message("rwandaemr.encounterList.return") }" onclick="location.href='${returnUrl}'" />
</div>


