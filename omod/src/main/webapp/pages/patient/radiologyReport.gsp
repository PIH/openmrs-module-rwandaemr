<%
    ui.decorateWith("appui", "standardEmrPage")
    def dateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm")
    def heading = report ? "Correct radiology report" : (draft ? "Continue draft report" : "Enter radiology report")
%>

${ ui.includeFragment("coreapps", "patientHeader", [ patient: patient.patient ]) }

<script type="text/javascript">
    var breadcrumbs = [
        { icon: "icon-home", link: '/' + OPENMRS_CONTEXT_PATH + '/index.htm' },
        { label: '${ ui.encodeJavaScript(ui.encodeHtmlContent(ui.format(patient.patient))) }', link: '${ ui.pageLink("coreapps", "clinicianfacing/patient", ["patientId": patient.id]) }' },
        { label: "Radiology Orders", link: '${ ui.pageLink("rwandaemr", "patient/radiologyOrders", ["patientId": patient.id]) }' },
        { label: "${ report ? 'Correct report' : 'Enter report' }" }
    ];

    jq(function() {
        var editor = jq('#report-editor');
        var editorElement = editor[0];
        var original = editor.html();
        var saving = false;

        try {
            document.execCommand('defaultParagraphSeparator', false, 'p');
            document.execCommand('styleWithCSS', false, false);
        }
        catch (e) {
            // Older browsers keep their own defaults
        }

        var isEmpty = function() {
            return jq.trim(editor.text()) === '';
        };

        var updateToolbar = function() {
            jq('#report-toolbar button[data-state]').each(function() {
                var active = false;
                try {
                    active = document.queryCommandState(jq(this).data('state'));
                }
                catch (e) {
                    active = false;
                }
                jq(this).toggleClass('rr-active', active);
            });
        };

        jq('#report-toolbar').on('mousedown', 'button', function(e) {
            // Keep the cursor in the report while a button is used
            e.preventDefault();
        });
        jq('#report-toolbar').on('click', 'button', function() {
            var command = jq(this).data('command');
            var value = jq(this).data('value') || null;
            editorElement.focus();
            document.execCommand(command, false, value);
            updateToolbar();
        });
        editor.on('keyup mouseup focus', updateToolbar);

        // Pasted text keeps its line breaks but not the formatting of the program it came from
        editor.on('paste', function(e) {
            var clipboard = (e.originalEvent || e).clipboardData || window.clipboardData;
            if (!clipboard) {
                return;
            }
            e.preventDefault();
            document.execCommand('insertText', false, clipboard.getData('text/plain') || clipboard.getData('Text'));
        });

        editor.on('input', function() {
            jq('#report-error').hide();
        });

        jq('#report-form button[data-action]').click(function() {
            jq('#report-action').val(jq(this).data('action'));
        });

        jq('#report-form').submit(function(e) {
            var action = jq('#report-action').val();
            if (action === 'discardDraft') {
                if (!confirm('Discard this draft? Its text will be lost.')) {
                    e.preventDefault();
                    return false;
                }
            }
            else {
                if (isEmpty()) {
                    e.preventDefault();
                    jq('#report-error').text('The report cannot be empty.').show();
                    editorElement.focus();
                    return false;
                }
                if (action === 'final' && !confirm('Save this report as final? Clinicians will be able to see it.')) {
                    e.preventDefault();
                    return false;
                }
            }
            jq('#report-text').val(editor.html());
            saving = true;
            jq('#report-form button[data-action]').prop('disabled', true);
        });

        // Warn before leaving with unsaved changes
        jq(window).on('beforeunload', function() {
            if (!saving && editor.html() !== original) {
                return 'The report has not been saved.';
            }
        });

        editorElement.focus();
    });
</script>

<style>
    .rr-page { max-width: 980px; }
    .rr-card { border: 1px solid #d9e2e7; background: #fff; border-radius: 6px; padding: 14px 16px; margin-bottom: 14px; }
    .rr-card h5 { margin: 0 0 8px; font-size: 12px; text-transform: uppercase; color: #607d8b; letter-spacing: .4px; }
    .rr-test { font-size: 16px; font-weight: bold; }
    .rr-meta { color: #607d8b; font-size: 13px; margin-top: 6px; }
    .rr-toolbar { display: flex; flex-wrap: wrap; gap: 4px; align-items: center; padding: 6px; border: 1px solid #b0bec5;
        border-bottom: none; border-radius: 4px 4px 0 0; background: #f5f7f8; }
    .rr-toolbar button { min-width: 34px; height: 32px; padding: 0 8px; margin: 0; border: 1px solid transparent;
        border-radius: 4px; background: transparent; color: #263238; font-size: 14px; cursor: pointer; line-height: 1; }
    .rr-toolbar button:hover { background: #e3eaee; border-color: #cfd8dc; }
    .rr-toolbar button.rr-active { background: #d6e4f0; border-color: #90a4ae; }
    .rr-toolbar .rr-sep { width: 1px; height: 22px; background: #cfd8dc; margin: 0 4px; }
    #report-editor { min-height: 360px; max-height: 70vh; overflow-y: auto; padding: 12px 14px; border: 1px solid #b0bec5;
        border-radius: 0 0 4px 4px; background: #fff; font-size: 15px; line-height: 1.55; outline: none; }
    #report-editor:focus { border-color: #00796b; box-shadow: 0 0 0 2px rgba(0, 121, 107, .15); }
    #report-editor p { margin: 0 0 8px; }
    #report-editor h3 { font-size: 17px; margin: 10px 0 6px; }
    #report-editor h4 { font-size: 15px; margin: 8px 0 4px; }
    #report-editor ul, #report-editor ol { margin: 0 0 8px 22px; }
    #report-editor .ra-center { text-align: center; }
    #report-editor .ra-right { text-align: right; }
    #report-editor .ra-justify { text-align: justify; }
    #report-error { display: none; color: #9f1d1d; font-weight: bold; margin: 6px 0; }
    .rr-actions { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; margin-top: 12px; }
    .rr-actions .rr-spacer { flex: 1; }
    .rr-note { color: #8a6d3b; background: #fcf8e3; border: 1px solid #f0e0b0; border-radius: 4px; padding: 8px 10px; margin-bottom: 10px; }
    .rr-draft-note { color: #0b5394; background: #e8f1fa; border: 1px solid #c6dbef; border-radius: 4px; padding: 8px 10px; margin-bottom: 10px; }
</style>

<div class="rr-page">
    <h3>${ heading }</h3>

    <div class="rr-card">
        <h5>Test</h5>
        <div class="rr-test">${ ui.encodeHtmlContent(ui.format(order.concept)) } <span class="rr-meta">${ ui.encodeHtmlContent(order.orderNumber) }</span></div>
        <div class="rr-meta">
            Requested ${ order.dateCreated ? dateFormat.format(order.dateCreated) : "" }
            <% if (order.orderer) { %> by ${ ui.encodeHtmlContent(ui.format(order.orderer)) }<% } %>
        </div>
        <% if (order.clinicalHistory) { %>
            <div class="rr-meta"><strong>Clinical history:</strong> ${ ui.encodeHtmlContent(order.clinicalHistory) }</div>
        <% } %>
        <% if (otherTestsInStudy) { %>
            <div class="rr-meta">
                <strong>Same PACS study as:</strong>
                <% otherTestsInStudy.eachWithIndex { o, i -> %>${ i > 0 ? ", " : "" }<a href="${ ui.pageLink("rwandaemr", "patient/radiologyReport", ["orderId": o.orderId]) }">${ ui.encodeHtmlContent(ui.format(o.concept)) }</a><% } %>
                &middot; each test has its own report
            </div>
        <% } %>
        <% if (imageUrl) { %>
            <p><a class="button" href="${ ui.encodeHtmlAttribute(imageUrl) }" target="_blank" rel="noopener noreferrer" referrerpolicy="no-referrer">&#128444; View images</a></p>
        <% } %>
    </div>

    <form id="report-form" method="post" action="${ ui.pageLink("rwandaemr", "patient/radiologyReport") }" class="rr-card">
        <input type="hidden" name="orderId" value="${ order.orderId }"/>
        <input type="hidden" name="returnUrl" value="${ ui.encodeHtmlAttribute(returnUrl) }"/>
        <input type="hidden" id="report-action" name="action" value="final"/>
        <input type="hidden" id="report-text" name="reportText" value=""/>

        <% if (report) { %>
            <div class="rr-note">
                This test already has a ${ ui.encodeHtmlContent(report.status ?: "") } report
                ${ report.radiologist ? "by " + ui.encodeHtmlContent(report.radiologist) : "" }
                ${ report.reportDate ? "on " + dateFormat.format(report.reportDate) : "" }.
                Saving changes records it as a correction, which clinicians will see.
            </div>
        <% } else if (draft) { %>
            <div class="rr-draft-note">
                Draft saved ${ draft.lastSaved ? "on " + dateFormat.format(draft.lastSaved) : "" }
                ${ draftSavedBy ? "by " + ui.encodeHtmlContent(draftSavedBy) : "" }.
                Only radiologists can see it until it is saved as final.
            </div>
        <% } %>

        <h5><label for="report-editor">Report</label></h5>
        <div id="report-toolbar" class="rr-toolbar" role="toolbar" aria-label="Report formatting">
            <button type="button" data-command="bold" data-state="bold" title="Bold (Ctrl+B)"><strong>B</strong></button>
            <button type="button" data-command="italic" data-state="italic" title="Italic (Ctrl+I)"><em>I</em></button>
            <button type="button" data-command="underline" data-state="underline" title="Underline (Ctrl+U)"><u>U</u></button>
            <span class="rr-sep"></span>
            <button type="button" data-command="formatBlock" data-value="h3" title="Heading">H1</button>
            <button type="button" data-command="formatBlock" data-value="h4" title="Sub-heading">H2</button>
            <button type="button" data-command="formatBlock" data-value="p" title="Normal text">&para;</button>
            <span class="rr-sep"></span>
            <button type="button" data-command="insertUnorderedList" data-state="insertUnorderedList" title="Bulleted list">&bull; List</button>
            <button type="button" data-command="insertOrderedList" data-state="insertOrderedList" title="Numbered list">1. List</button>
            <span class="rr-sep"></span>
            <button type="button" data-command="justifyLeft" data-state="justifyLeft" title="Align left">&#8676;</button>
            <button type="button" data-command="justifyCenter" data-state="justifyCenter" title="Centre">&#8596;</button>
            <button type="button" data-command="justifyRight" data-state="justifyRight" title="Align right">&#8677;</button>
            <span class="rr-sep"></span>
            <button type="button" data-command="removeFormat" title="Clear formatting">&#10007; Format</button>
            <button type="button" data-command="undo" title="Undo (Ctrl+Z)">&#8630;</button>
            <button type="button" data-command="redo" title="Redo (Ctrl+Y)">&#8631;</button>
        </div>
        <div id="report-editor" contenteditable="true" role="textbox" aria-multiline="true" spellcheck="true">${ editorHtml }</div>
        <div id="report-error"></div>

        <p class="rr-meta">
            Signed as <strong>${ radiologist ? ui.encodeHtmlContent(ui.format(radiologist)) : "(your account is not linked to a provider)" }</strong>
        </p>
        <div class="rr-actions">
            <% if (!report) { %>
                <button type="submit" data-action="draft" class="button" ${ radiologist ? "" : 'disabled="disabled"' }>Save draft</button>
            <% } %>
            <button type="submit" data-action="final" class="button confirm" ${ radiologist ? "" : 'disabled="disabled"' }>${ report ? "Save correction" : "Save as final" }</button>
            <span class="rr-spacer"></span>
            <% if (draft && !report) { %>
                <button type="submit" data-action="discardDraft" class="button">Discard draft</button>
            <% } %>
            <a class="button cancel" href="${ ui.encodeHtmlAttribute(returnUrl) }">Cancel</a>
        </div>
    </form>
</div>
