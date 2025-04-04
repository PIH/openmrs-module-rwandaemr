<%
    ui.decorateWith("appui", "standardEmrPage")
    ui.includeJavascript("uicommons", "datatables/jquery.dataTables.min.js")
%>

${ ui.includeFragment("coreapps", "patientHeader", [ patient: patient.patient ]) }

<script type="text/javascript">

    jq(document).ready(function() {
       jq("#return-button").click(function(event) {
           document.location.href = '${ui.pageLink("coreapps", "clinicianfacing/patient", ["patientId": patient.id])}';
       });
        jq("#add-button").click(function(event) {
            document.location.href = '${ui.pageLink("htmlformentryui", "htmlform/enterHtmlFormWithStandardUi", [
                "patientId": patient.id,
                "definitionUiResource": "file:configuration/htmlforms/auto-drug-administration.xml",
                "returnUrl": ui.pageLink("rwandaemr", "patient/drugAdministrations", ["patientId": patient.id])
            ])}';
        });

        <% if (drugAdministrations.size() > 0) { %>

            // Create a datatable
            let drugAdministrationTable = jq("#drug-administration-list-table").dataTable(
                {
                    bFilter: true,
                    bJQueryUI: true,
                    bLengthChange: false,
                    iDisplayLength: 10,
                    sPaginationType: 'full_numbers',
                    bSort: false,
                    sDom: 'ft<\"fg-toolbar ui-toolbar ui-corner-bl ui-corner-br ui-helper-clearfix datatables-info-and-pg \"ip>',
                    oLanguage:  {
                        oPaginate: {
                            sFirst: "${ ui.message("uicommons.dataTable.first") }",
                            sLast: "${ ui.message("uicommons.dataTable.last") }",
                            sNext:  "${ ui.message("uicommons.dataTable.next") }",
                            sPrevious:  "${ ui.message("uicommons.dataTable.previous") }"
                        },

                        sInfo:  "${ ui.message("uicommons.dataTable.info") }",
                        sSearch: "${ ui.message("uicommons.dataTable.search") }",
                        sZeroRecords: "${ ui.message("uicommons.dataTable.zeroRecords") }",
                        sEmptyTable: "${ ui.message("uicommons.dataTable.emptyTable") }",
                        sInfoFiltered:  "${ ui.message("uicommons.dataTable.infoFiltered") }",
                        sInfoEmpty:  "${ ui.message("uicommons.dataTable.infoEmpty") }",
                        sLengthMenu:  "${ ui.message("uicommons.dataTable.lengthMenu") }",
                        sLoadingRecords:  "${ ui.message("uicommons.dataTable.loadingRecords") }",
                        sProcessing:  "${ ui.message("uicommons.dataTable.processing") }",

                        oAria: {
                            sSortAscending:  "${ ui.message("uicommons.dataTable.sortAscending") }",
                            sSortDescending:  "${ ui.message("uicommons.dataTable.sortDescending") }"
                        }
                    }
                }
            );
        <% } %>
    });
</script>

<style>
    .encounter-link {
        cursor:pointer;
        color:blue;
        text-decoration:underline;
    }
    .pointer {
        cursor:pointer;
    }
    .date-column {
        width: 125px;
    }
</style>
<h3>${ ui.message("rwandaemr.drugAdministrations") }</h3>

<div style="width:100%; text-align: right;">
    <button id="add-button">${ui.message("rwandaemr.drugAdministrations.add")}</button>
</div>

<table id="drug-administration-list-table">
    <thead>
        <tr>
            <th>${ ui.message("rwandaemr.drugAdministrations.encounterDatetime") }</th>
            <th>${ ui.message("rwandaemr.drugAdministrations.drug") }</th>
            <th>${ ui.message("rwandaemr.drugAdministrations.frequency") }</th>
            <th>${ ui.message("rwandaemr.drugAdministrations.date") }</th>
            <th>${ ui.message("rwandaemr.drugAdministrations.route") }</th>
            <th>${ ui.message("rwandaemr.drugAdministrations.provider") }</th>
            <th>${ ui.message("rwandaemr.drugAdministrations.location") }</th>
        </tr>
    </thead>
    <tbody>
    <% if (drugAdministrations.size() == 0) { %>
        <tr>
            <td colspan="5">${ ui.message("emr.none") }</td>
        </tr>
    <% } %>
    <% drugAdministrations.each { a ->

        def e = a.group.encounter
        def pageLink = ui.pageLink("rwandaemr", "patient/simpleEncounterView", [ "encounter": e.uuid ])
        if (e.visit) {
            pageLink = ui.pageLink("coreapps", "patientdashboard/patientDashboard", [
                "patientId": e.patient.uuid,
                "visitId": e.visit.uuid,
            ])
        }
        %>
        <tr id="drug-administration-${ a.group.id }" class="drug-administration-row${pageLink ? ' pointer' :''}" data-href="${pageLink}">
            <td class="date-column${pageLink ? ' drug-administration-link' :''}">
                ${ ui.format(e.encounterDatetime) }
            </td>
            <td>
                ${ ui.format(a.drug?.valueDrug ?: a.drug?.valueCoded) }
            </td>
            <td>
                ${ ui.format(a.frequency?.valueText) }
            </td>
            <td class="date-column">
                ${ ui.format(a.date?.valueDatetime) }
            </td>
            <td>
                ${ ui.format(a.route?.valueCoded) }
            </td>
            <td>
                <% e.encounterProviders.eachWithIndex { ep, index -> %>
                    ${ ui.format(ep.provider) }${ e.encounterProviders.size() - index > 1 ? "<br/>" : ""}
                <% } %>
            </td>
            <td>
                ${ ui.format(e.location) }
            </td>

        </tr>
    <% } %>
    </tbody>
</table>

<div>
    <input id="return-button" type="button" class="cancel" value="${ ui.message("rwandaemr.encounterList.return") }"/>
</div>


