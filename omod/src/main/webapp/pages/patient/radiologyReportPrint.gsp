<%
    def dateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm")
    def dayFormat = new java.text.SimpleDateFormat("yyyy-MM-dd")
    // Closures cannot be used inside the page's expressions, so the list of order numbers is built here
    def orderNumbers = studyOrders ? studyOrders.collect { it.orderNumber }.join(", ") : ""
%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8"/>
    <title>Radiology report</title>
    <style>
        @page { size: A4; margin: 18mm 16mm; }
        body { font-family: "Segoe UI", "Helvetica Neue", Arial, sans-serif; color: #212121; font-size: 13px; margin: 0; }
        .rp-sheet { max-width: 780px; margin: 20px auto; padding: 0 16px; }
        .rp-header { display: flex; justify-content: space-between; align-items: flex-end; border-bottom: 2px solid #00473f; padding-bottom: 8px; margin-bottom: 14px; }
        .rp-header h1 { margin: 0; font-size: 20px; color: #00473f; }
        .rp-header .rp-facility { font-size: 15px; font-weight: bold; }
        table.rp-details { width: 100%; border-collapse: collapse; margin-bottom: 16px; }
        .rp-details td { padding: 4px 6px; vertical-align: top; border-bottom: 1px solid #eceff1; }
        .rp-details td.rp-label { width: 22%; color: #607d8b; font-weight: bold; white-space: nowrap; }
        .rp-section-title { font-size: 12px; text-transform: uppercase; letter-spacing: .5px; color: #607d8b; margin: 14px 0 6px; font-weight: bold; }
        .rp-report { line-height: 1.55; font-size: 14px; border: 1px solid #cfd8dc; border-radius: 4px; padding: 12px 14px; min-height: 260px; }
        .rp-report p { margin: 0 0 8px; }
        .rp-report h3 { font-size: 16px; margin: 10px 0 6px; }
        .rp-report h4 { font-size: 14px; margin: 8px 0 4px; }
        .rp-report ul, .rp-report ol { margin: 0 0 8px 22px; }
        .rp-report .ra-center { text-align: center; }
        .rp-report .ra-right { text-align: right; }
        .rp-report .ra-justify { text-align: justify; }
        .rp-signature { margin-top: 36px; display: flex; justify-content: space-between; }
        .rp-signature div { width: 45%; }
        .rp-line { border-top: 1px solid #424242; margin-top: 42px; padding-top: 4px; color: #607d8b; font-size: 12px; }
        .rp-footer { margin-top: 24px; font-size: 11px; color: #90a4ae; text-align: center; }
        .rp-toolbar { text-align: center; margin: 14px 0; }
        .rp-toolbar button { font-size: 14px; padding: 8px 18px; cursor: pointer; }
        .rp-empty { padding: 30px; text-align: center; color: #607d8b; }
        @media print { .rp-toolbar { display: none; } .rp-sheet { margin: 0 auto; } }
    </style>
</head>
<body>
<% if (!authorized) { %>
    <div class="rp-empty">You do not have permission to view this report.</div>
<% } else if (!report) { %>
    <div class="rp-empty">There is no report for this study yet.</div>
<% } else { %>
    <div class="rp-toolbar">
        <button type="button" onclick="window.print()">Print</button>
        <button type="button" onclick="window.close()">Close</button>
    </div>
    <div class="rp-sheet">
        <div class="rp-header">
            <div>
                <div class="rp-facility">${ ui.encodeHtmlContent(facility ?: "") }</div>
                <h1>Radiology Report</h1>
            </div>
            <div>${ ui.encodeHtmlContent(report.status ?: "") }</div>
        </div>

        <table class="rp-details">
            <tr>
                <td class="rp-label">Patient</td>
                <td>${ ui.encodeHtmlContent(ui.format(patient)) }</td>
                <td class="rp-label">Patient ID</td>
                <td>${ ui.encodeHtmlContent(patientIdentifier ?: "") }</td>
            </tr>
            <tr>
                <td class="rp-label">Age / Sex</td>
                <td>${ patient.age ?: "" } ${ ui.encodeHtmlContent(patient.gender ?: "") }</td>
                <td class="rp-label">Date of birth</td>
                <td>${ patient.birthdate ? dayFormat.format(patient.birthdate) : "" }</td>
            </tr>
            <tr>
                <td class="rp-label">Study</td>
                <td colspan="3">
                    <% studyOrders.eachWithIndex { o, i -> %>${ i > 0 ? ", " : "" }${ ui.encodeHtmlContent(ui.format(o.concept)) }<% } %>
                </td>
            </tr>
            <tr>
                <td class="rp-label">Order number(s)</td>
                <td>${ ui.encodeHtmlContent(orderNumbers) }</td>
                <td class="rp-label">Requested</td>
                <td>${ order.dateCreated ? dateFormat.format(order.dateCreated) : "" }</td>
            </tr>
            <tr>
                <td class="rp-label">Requested by</td>
                <td>${ order.orderer ? ui.encodeHtmlContent(ui.format(order.orderer)) : "" }</td>
                <td class="rp-label">Department</td>
                <td>${ order.encounter?.location ? ui.encodeHtmlContent(order.encounter.location.name) : "" }</td>
            </tr>
            <% if (order.clinicalHistory) { %>
            <tr>
                <td class="rp-label">Clinical history</td>
                <td colspan="3">${ ui.encodeHtmlContent(order.clinicalHistory) }</td>
            </tr>
            <% } %>
        </table>

        <div class="rp-section-title">Report</div>
        <div class="rp-report">${ reportHtml ?: "" }</div>

        <div class="rp-signature">
            <div>
                <strong>${ ui.encodeHtmlContent(report.radiologist ?: "") }</strong>
                <div class="rp-line">Radiologist</div>
            </div>
            <div>
                <strong>${ report.reportDate ? dateFormat.format(report.reportDate) : "" }</strong>
                <div class="rp-line">Date of report</div>
            </div>
        </div>
        <div class="rp-footer">Printed ${ dateFormat.format(new Date()) }</div>
    </div>
    <script type="text/javascript">
        window.addEventListener('load', function() { window.print(); });
    </script>
<% } %>
</body>
</html>
