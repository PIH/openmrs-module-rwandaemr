<%
    config.require("id")
%>

<link rel="stylesheet" href="https://icdcdn.who.int/embeddedct/icd11ect-1.8.css"/>

<div id="${config.id}" class="icd11-diagnoses-widget">
    ${config.hiddenInputHtml}
    <% if (!config.readOnly) { %>
        <% if (config.enabled) { %>
            <label for="${config.id}-search">Search ICD-11 diagnosis</label>
            <input id="${config.id}-search" class="ctw-input icd11-search" type="text" autocomplete="off"
                   data-ctw-ino="${config.id}" placeholder="Type for starting the search"/>
            <div id="${config.id}-status" class="icd11-status"></div>
            <div id="${config.id}-coding-tool" class="ctw-window icd11-coding-tool" data-ctw-ino="${config.id}"></div>
        <% } else { %>
            <div class="icd11-status">ICD-11 integration is disabled.</div>
        <% } %>
    <% } %>
    <table class="icd11-selected">
        <thead>
        <tr>
            <th>ICD-11 diagnosis</th>
            <th>Type</th>
            <th>Certainty</th>
            <% if (!config.readOnly) { %><th>Action</th><% } %>
        </tr>
        </thead>
        <tbody id="${config.id}-selected"></tbody>
    </table>
</div>

<style>
    #${config.id} .icd11-search {
        box-sizing: border-box;
        margin-bottom: 0.35rem;
        max-width: 42rem;
        width: 100%;
    }
    #${config.id} .icd11-status {
        color: #666;
        min-height: 1.2rem;
    }
    #${config.id} .icd11-coding-tool {
        box-sizing: border-box;
        margin-bottom: 1rem;
        max-width: 100%;
        overflow-x: auto;
        overflow-y: visible;
        width: 100%;
    }
    #${config.id} .icd11-coding-tool .ectComponent {
        min-width: 820px;
    }
    #${config.id} .icd11-coding-tool .ectComponent .wordlist ul,
    #${config.id} .icd11-coding-tool .ectComponent .entitylist ul.toplevel {
        margin-left: 0;
        padding-left: 0;
    }
    #${config.id} .icd11-coding-tool .ectComponent .wordlist li {
        box-sizing: border-box;
        min-width: 140px;
        padding-left: 0;
        text-indent: 0;
        width: auto;
    }
    #${config.id} .icd11-selected {
        margin-top: 0.5rem;
        width: 100%;
    }
    #${config.id} .icd11-selected td,
    #${config.id} .icd11-selected th {
        padding: 0.35rem;
        text-align: left;
        vertical-align: top;
    }
    #${config.id} .icd11-selected select {
        max-width: 10rem;
        width: 100%;
    }
    #${config.id} .icd11-empty {
        color: #777;
    }
</style>

<% if (!config.readOnly && config.enabled) { %>
    <script src="https://icdcdn.who.int/embeddedct/icd11ect-1.8.js"></script>
<% } %>
<script type="text/javascript">
    (function(jq) {
        var widget = jq("#${config.id}");
        var hidden = widget.find("input[name='icd11DiagnosesJson']");
        var selected = [];
        var readOnly = ${config.readOnly ? "true" : "false"};
        var enabled = ${config.enabled ? "true" : "false"};
        var ectId = "${config.id}";

        function decodeBase64Json(value) {
            if (!value) {
                return null;
            }
            try {
                return JSON.parse(decodeURIComponent(escape(window.atob(value))));
            }
            catch (e) {
                return null;
            }
        }

        function decodeInitial() {
            var value = hidden.val();
            if (value) {
                try {
                    return JSON.parse(value);
                }
                catch (e) {
                    return [];
                }
            }
            return decodeBase64Json("${config.initialDiagnosesBase64}") || [];
        }

        function sync() {
            hidden.val(JSON.stringify(selected));
        }

        function displayLabel(diagnosis) {
            return (diagnosis.icd11Code ? diagnosis.icd11Code + " - " : "") + (diagnosis.title || "");
        }

        function renderSelected() {
            var tbody = widget.find("#${config.id}-selected").empty();
            if (!selected.length) {
                var emptyRow = jq("<tr>");
                jq("<td colspan='" + (readOnly ? "3" : "4") + "' class='icd11-empty'>")
                    .text("No ICD-11 diagnosis selected")
                    .appendTo(emptyRow);
                tbody.append(emptyRow);
                sync();
                return;
            }
            selected.forEach(function(diagnosis, index) {
                var row = jq("<tr>");
                jq("<td>").text(displayLabel(diagnosis)).appendTo(row);
                if (readOnly) {
                    jq("<td>").text(diagnosis.diagnosisType || "").appendTo(row);
                    jq("<td>").text(diagnosis.certainty || "").appendTo(row);
                }
                else {
                    var type = jq("<select>").append("<option value='primary'>Primary</option>")
                        .append("<option value='secondary'>Secondary</option>")
                        .val(diagnosis.diagnosisType || "secondary");
                    type.change(function() {
                        diagnosis.diagnosisType = jq(this).val();
                        sync();
                    });
                    jq("<td>").append(type).appendTo(row);

                    var certainty = jq("<select>").append("<option value='confirmed'>Confirmed</option>")
                        .append("<option value='presumed'>Presumed</option>")
                        .val(diagnosis.certainty || "confirmed");
                    certainty.change(function() {
                        diagnosis.certainty = jq(this).val();
                        sync();
                    });
                    jq("<td>").append(certainty).appendTo(row);

                    var remove = jq("<button type='button'>").text("Remove").click(function() {
                        selected.splice(index, 1);
                        sync();
                        renderSelected();
                    });
                    jq("<td>").append(remove).appendTo(row);
                }
                tbody.append(row);
            });
            sync();
        }

        function addDiagnosis(result) {
            var alreadySelected = selected.some(function(diagnosis) {
                return diagnosis.entityUri === result.entityUri && diagnosis.title === result.title;
            });
            if (!alreadySelected) {
                selected.push({
                    icd11Code: result.icd11Code,
                    entityUri: result.entityUri,
                    foundationUri: result.foundationUri,
                    title: result.title,
                    linearization: result.linearization || "mms",
                    diagnosisType: selected.length === 0 ? "primary" : "secondary",
                    certainty: "confirmed"
                });
                sync();
                renderSelected();
            }
        }

        function addSelectedEntity(selectedEntity) {
            if (!selectedEntity) {
                return;
            }
            addDiagnosis({
                icd11Code: selectedEntity.code,
                entityUri: selectedEntity.linearizationUri || selectedEntity.uri,
                foundationUri: selectedEntity.foundationUri,
                title: selectedEntity.selectedText || selectedEntity.title || selectedEntity.bestMatchText,
                linearization: "mms"
            });
            widget.find("#${config.id}-status").text("");
            if (window.ECT && window.ECT.Handler) {
                window.ECT.Handler.clear(ectId);
            }
        }

        function preventCodingToolSubmitButtons() {
            var codingToolInteraction = false;
            var form = hidden.closest("form");
            var formElement = form.length ? form.get(0) : null;
            var selector = "[data-ctw-ino='" + ectId + "'] button, "
                + "button[data-ctw-ino='" + ectId + "'], "
                + ".ctw-window button, .ctw-window input[type='submit'], "
                + ".ctw-popup button, .ctw-popup input[type='submit'], "
                + ".ctw-modal button, .ctw-modal input[type='submit'], "
                + ".ectComponent button, .ectComponent input[type='submit'], "
                + "[data-ctw-ino='" + ectId + "'] input[type='submit'], "
                + "input[type='submit'][data-ctw-ino='" + ectId + "']";

            function isCodingToolElement(element) {
                return jq(element).closest(".ctw-window, .ctw-popup, .ctw-modal, .ectComponent, [data-ctw-ino='" + ectId + "']").length > 0
                    && jq(element).closest(".icd11-selected").length === 0;
            }

            jq(document).on("mousedown keydown", function(event) {
                if (isCodingToolElement(event.target)) {
                    codingToolInteraction = true;
                    window.setTimeout(function() {
                        codingToolInteraction = false;
                    }, 500);
                }
            });

            jq(document).on("click", selector, function(event) {
                if (isCodingToolElement(event.target)) {
                    codingToolInteraction = true;
                    event.preventDefault();
                    window.setTimeout(function() {
                        codingToolInteraction = false;
                    }, 500);
                }
            });

            if (formElement) {
                formElement.addEventListener("submit", function(event) {
                    if (codingToolInteraction || isCodingToolElement(document.activeElement)) {
                        event.preventDefault();
                        event.stopImmediatePropagation();
                    }
                }, true);
            }

            form.on("submit.icd11CodingTool", function(event) {
                if (codingToolInteraction || isCodingToolElement(document.activeElement)) {
                    event.preventDefault();
                    event.stopImmediatePropagation();
                    return false;
                }
                sync();
            });

            function markButtonsAsNonSubmit() {
                jq(selector).each(function() {
                    var control = jq(this);
                    if (control.is("button") && !control.attr("type")) {
                        control.attr("type", "button");
                    }
                    if (control.is("input[type='submit']")) {
                        control.attr("type", "button");
                    }
                });
            }

            markButtonsAsNonSubmit();
            if (window.MutationObserver) {
                new MutationObserver(markButtonsAsNonSubmit).observe(document.body, {
                    childList: true,
                    subtree: true
                });
            }
        }

        function configureEmbeddedCodingTool() {
            if (!enabled || readOnly) {
                return;
            }
            if (!window.ECT || !window.ECT.Handler) {
                widget.find("#${config.id}-status").text("Unable to load the WHO ICD-11 Coding Tool.");
                return;
            }

            window.rwandaEmrIcd11 = window.rwandaEmrIcd11 || {};
            window.rwandaEmrIcd11.widgets = window.rwandaEmrIcd11.widgets || {};
            window.rwandaEmrIcd11.widgets[ectId] = {
                addSelectedEntity: addSelectedEntity
            };

            var settings = decodeBase64Json("${config.ectSettingsBase64}") || {};
            var callbacks = {
                selectedEntityFunction: function(selectedEntity) {
                    var target = selectedEntity && window.rwandaEmrIcd11.widgets[selectedEntity.iNo];
                    if (target) {
                        target.addSelectedEntity(selectedEntity);
                    }
                },
                getNewTokenFunction: async function() {
                    var response = await fetch(openmrsContextPath + "/ws/rest/v1/rwandaemr/icd11/token", {
                        credentials: "same-origin",
                        headers: {
                            "Accept": "application/json"
                        }
                    });
                    if (!response.ok) {
                        throw new Error("Unable to retrieve ICD-11 token");
                    }
                    var result = await response.json();
                    return result.token;
                },
                searchStartedFunction: function() {
                    widget.find("#${config.id}-status").text("Searching...");
                },
                searchEndedFunction: function() {
                    widget.find("#${config.id}-status").text("");
                }
            };

            window.ECT.Handler.configure(settings, callbacks);
        }

        selected = decodeInitial();
        renderSelected();
        preventCodingToolSubmitButtons();
        configureEmbeddedCodingTool();
    }(jQuery));
</script>
