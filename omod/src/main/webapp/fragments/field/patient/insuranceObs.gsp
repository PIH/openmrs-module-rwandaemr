<%
    config.require("id")
    // config.insurancePolicyIdFormFieldName
    // config.insuranceConceptFormFieldName
    // config.insuranceNumberFormFieldName
    // config.initialInsuranceConcept
    // config.initialInsuranceNumber
    // config.initialEncounterDate
    // config.encounterDateFieldId
    // config.hideLabel
    // config.label
    // config.classes
    // config.hideEmptyLabel
    // config.emptyOptionLabel
    // config.showAll
%>

<p id="${ config.id }">

    <% if (config.label != null && config.label != '') { %>
        <label for="${ config.id }-field">
            ${ ui.message(config.label) }
        </label>
    <% } %>

    <select id="${ config.id }-field"<% if (config.classes) { %> class="${ config.classes.join(' ') }" <% } %>></select>

    <% if (config.insurancePolicyIdFormFieldName != null) { %>
        <input type="hidden" id="${ config.id }-policy-id-input" name="${config.insurancePolicyIdFormFieldName}" value="${config.initialPolicyId ?: ""}"/>
        ${ ui.includeFragment("uicommons", "fieldErrors", [ fieldName: config.insurancePolicyIdFormFieldName ]) }
    <% } %>
    <% if (config.insuranceConceptFormFieldName != null) { %>
        <input type="hidden" id="${ config.id }-concept-input" name="${config.insuranceConceptFormFieldName}" value="${config.initialInsuranceConcept ?: ""}"/>
        ${ ui.includeFragment("uicommons", "fieldErrors", [ fieldName: config.insuranceConceptFormFieldName ]) }
    <% } %>
    <% if (config.insuranceNumberFormFieldName != null) { %>
        <input type="hidden" id="${ config.id }-number-input" name="${config.insuranceNumberFormFieldName}" value="${config.initialInsuranceNumber ?: ""}"/>
        ${ ui.includeFragment("uicommons", "fieldErrors", [ fieldName: config.insuranceNumberFormFieldName ]) }
    <% } %>

</p>

<script type="text/javascript">
    (function(jq) {

        let policies = [];
        <% policies.each{ policy -> %>
            policies.push({
                    policyId: '${policy.policyId}',
                    conceptId: '${policy.insuranceConceptId}',
                    name: '${policy.insuranceName}',
                    cardNumber: '${policy.cardNumber}',
                    startDate: '${policy.coverageStartDate}',
                    endDate: '${policy.coverageEndDate}',
                }
            );
        <% } %>

        let fieldInput = jq("#${ config.id }-field");
        jq(fieldInput).change(function() {
            let policyId = jq(this).val();
            jq("#${ config.id }-policy-id-input").val(policyId);
            if (policyId === "") {
                jq("#${ config.id }-concept-input").val("");
                jq("#${ config.id }-number-input").val("");
            }
            policies.forEach(p => {
                if (p.policyId === policyId) {
                    jq("#${ config.id }-concept-input").val(p.conceptId);
                    jq("#${ config.id }-number-input").val(p.cardNumber);
                };
            });
        });

        function configureOptions(encounterDateYmd) {
            if (!encounterDateYmd) {
                encounterDateYmd = new Date().toISOString().split('T')[0];
            }
            let initialInsuranceConcept = '${initialInsuranceConcept}';
            let initialInsuranceNumber = '${initialInsuranceNumber}';
            jq(fieldInput).empty();
            <% if (!config.hideEmptyLabel) { %>
                jq(fieldInput).append('<option value="">${ui.message(config.emptyOptionLabel ?: '&nbsp;')}</option>');
            <% } %>
            policies.forEach(p => {
                let policyValid = (p.startDate === '' || p.startDate <= encounterDateYmd) && (p.endDate === '' || p.endDate >= encounterDateYmd);
                let policySelected = p.conceptId === initialInsuranceConcept && p.cardNumber === initialInsuranceNumber;
                if (policySelected || policyValid) {
                    let option = jq('<option>', {value: p.policyId, text: p.name + ':  ' + p.cardNumber});
                    if (policySelected) {
                        jq(option).attr('selected', 'selected');
                    }
                    jq(fieldInput).append(jq(option));
                }
            });
            jq(fieldInput).attr("size", ${config.showAll} ? jq(fieldInput).find("option").length : 1);
        };

        let initialEncounterDate = '${initialEncounterDate}';
        configureOptions(initialEncounterDate);

        <% if (config.encounterDateFieldId) { %>
            jq("#${config.encounterDateFieldId}").change(function() {
                configureOptions(jq(this).val());
            });
        <% } %>

    }( jQuery ));
</script>