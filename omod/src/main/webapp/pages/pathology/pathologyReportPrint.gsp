<%
    // The logo is an image of this module, or else a URL
    def logoUrl = logo ? ((logo.startsWith("http") || logo.startsWith("/")) ? logo : ui.resourceLink("rwandaemr", logo)) : null
%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8"/>
    <title>Pathology report ${ accessionNumber ? ui.encodeHtmlContent(accessionNumber) : "" }</title>
    <style>
        @page { size: A4; margin: 14mm 16mm; }
        body { font-family: Georgia, "Times New Roman", serif; color: #111; font-size: 15px; margin: 0; background: #fff; }
        .pr-sheet { max-width: 780px; margin: 20px auto; padding: 0 16px; }
        .pr-top { display: flex; justify-content: space-between; align-items: flex-start; }
        .pr-logo img { height: 110px; }
        .pr-logo .pr-facility { font-size: 18px; font-weight: bold; }
        .pr-lab { text-align: left; font-size: 16px; line-height: 1.3; }
        .pr-lab .pr-big { font-family: Arial, Helvetica, sans-serif; font-size: 22px; }
        h1 { text-align: center; font-size: 26px; margin: 10px 0 0; padding-bottom: 4px; border-bottom: 1.5px solid #111; }
        .pr-patient { display: flex; justify-content: space-between; padding: 14px 0 10px; border-bottom: 1.5px solid #111; }
        .pr-patient > div { width: 48%; }
        .pr-patient p { margin: 0 0 12px; }
        .pr-line { margin: 12px 0 0; }
        .pr-label { font-weight: bold; font-size: 18px; }
        .pr-section { margin-top: 18px; }
        .pr-section .pr-label { display: block; margin-bottom: 4px; }
        .pr-text { white-space: pre-wrap; line-height: 1.35; min-height: 1.3em; }
        .pr-location { margin-top: 16px; font-size: 19px; }
        .pr-location-item { margin: 10px 0 0 20px; }
        .pr-location-item .pr-text { display: inline; }
        .pr-validated { margin-top: 40px; font-size: 16px; }
        .pr-footer { margin-top: 24px; font-size: 11px; color: #777; text-align: center; font-family: Arial, Helvetica, sans-serif; }
        .pr-toolbar { text-align: center; margin: 14px 0; font-family: Arial, Helvetica, sans-serif; }
        .pr-toolbar button { font-size: 14px; padding: 8px 18px; cursor: pointer; margin: 0 4px; }
        .pr-toolbar .pr-hint { color: #607d8b; font-size: 12px; margin-top: 6px; }
        .pr-empty { padding: 30px; text-align: center; color: #607d8b; font-family: Arial, Helvetica, sans-serif; }
        @media print { .pr-toolbar { display: none; } .pr-sheet { margin: 0 auto; } }
    </style>
</head>
<body>
<% if (!authorized) { %>
    <div class="pr-empty">You do not have permission to view this report.</div>
<% } else if (error) { %>
    <div class="pr-empty">${ ui.encodeHtmlContent(error) }</div>
<% } else { %>
    <div class="pr-toolbar">
        <button type="button" onclick="window.print()">Print / Save as PDF</button>
        <button type="button" onclick="window.close()">Close</button>
        <div class="pr-hint">To download a PDF, choose "Save as PDF" as the printer.</div>
    </div>
    <div class="pr-sheet">
        <div class="pr-top">
            <div class="pr-logo">
                <% if (logoUrl) { %>
                    <img src="${ ui.encodeHtmlAttribute(logoUrl) }" alt="${ ui.encodeHtmlAttribute(facility ?: "") }"/>
                <% } else { %>
                    <div class="pr-facility">${ ui.encodeHtmlContent(facility ?: "") }</div>
                <% } %>
            </div>
            <div class="pr-lab">
                <% if (labPhoneNumber) { %>
                    <div>Lab phone number: <span class="pr-big">${ ui.encodeHtmlContent(labPhoneNumber) }</span></div>
                <% } %>
                <div>Accession number: <span class="pr-big">${ ui.encodeHtmlContent(accessionNumber ?: "") }</span></div>
            </div>
        </div>

        <h1>Pathology Report</h1>

        <div class="pr-patient">
            <div>
                <p>Patient Name: ${ ui.encodeHtmlContent(patientName ?: "") }</p>
                <p>Primary care ID : ${ ui.encodeHtmlContent(patientIdentifier ?: "") }</p>
                <p>Patient Birthdate: ${ ui.encodeHtmlContent(birthdate ?: "") }</p>
                <p>Patient Gender: ${ ui.encodeHtmlContent(gender ?: "") }</p>
            </div>
            <div>
                <p>Surgery Date: ${ ui.encodeHtmlContent(surgeryDate ?: "") }</p>
                <p style="margin-bottom: 0">Specimen Reception Date:<br/>${ ui.encodeHtmlContent(receptionDate ?: "") }</p>
                <p>Signout Date: ${ ui.encodeHtmlContent(signoutDate ?: "") }</p>
            </div>
        </div>

        <div class="pr-line"><span class="pr-label">Sending Physician:</span> ${ ui.encodeHtmlContent(sendingPhysician ?: "") }</div>
        <div class="pr-line"><span class="pr-label">Sending Facility:</span> ${ ui.encodeHtmlContent(sendingFacility ?: "") }</div>

        <div class="pr-location">Anatomical location:</div>
        <div class="pr-location-item"><span class="pr-label">i.System:</span> ${ ui.encodeHtmlContent(organSystem ?: "") }</div>
        <div class="pr-location-item"><span class="pr-label">ii.Organ:</span> ${ ui.encodeHtmlContent(organ ?: "") }</div>
        <div class="pr-location-item"><span class="pr-label">iii.Detail:</span> <div class="pr-text">${ ui.encodeHtmlContent(specimenDetail ?: "") }</div></div>

        <div class="pr-line" style="margin-top: 16px"><span class="pr-label">Procedure type:</span> ${ ui.encodeHtmlContent(procedure ?: "") }</div>

        <div class="pr-section">
            <span class="pr-label">Gross Description:</span>
            <div class="pr-text">${ ui.encodeHtmlContent(grossDescription ?: "") }</div>
        </div>
        <% if (additionalStains) { %>
        <div class="pr-section">
            <span class="pr-label">Additional Stains:</span>
            <div class="pr-text">${ ui.encodeHtmlContent(additionalStains) }</div>
        </div>
        <% } %>
        <div class="pr-section">
            <span class="pr-label">Microscopic Examination:</span>
            <div class="pr-text">${ ui.encodeHtmlContent(microscopicExamination ?: "") }</div>
        </div>
        <div class="pr-section">
            <span class="pr-label">Conclusion:</span>
            <div class="pr-text">${ ui.encodeHtmlContent(conclusion ?: "") }</div>
        </div>
        <div class="pr-section">
            <span class="pr-label">Comment:</span>
            <div class="pr-text">${ ui.encodeHtmlContent(comment ?: "") }</div>
        </div>

        <div class="pr-validated">
            <span class="pr-label">Validated by pathologist:</span> ${ ui.encodeHtmlContent(pathologist ?: "") }
            &nbsp;On: ${ ui.encodeHtmlContent(validatedOn ?: "") }
        </div>
        <% /* No signature and stamp line: the report is validated in the system by the pathologist named above. Add one
              back when reports can be signed electronically. */ %>

        <div class="pr-footer">Printed ${ printedOn }</div>
    </div>
    <script type="text/javascript">
        window.addEventListener('load', function() { window.print(); });
    </script>
<% } %>
</body>
</html>
