<%
    config.require("id")
%>

<div id="${config.id}" class="icd11-diagnoses-widget">
    ${config.hiddenInputHtml}
    <% if (!config.readOnly) { %>
        <label for="${config.id}-search">Search ICD-11 diagnosis</label>
        <input id="${config.id}-search" class="icd11-search" type="text" autocomplete="off"
               placeholder="Type at least 3 characters"/>
        <button id="${config.id}-open-browser" class="icd11-open-browser" type="button">Browse ICD-11</button>
        <div id="${config.id}-status" class="icd11-status"></div>
        <ul id="${config.id}-results" class="icd11-results"></ul>
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
    <% if (!config.readOnly) { %>
        <div id="${config.id}-browser" class="icd11-browser-overlay" style="display: none;">
            <div class="icd11-browser-dialog">
                <div class="icd11-browser-header">
                    <strong>ICD-11 for Mortality and Morbidity Statistics</strong>
                    <button id="${config.id}-close-browser" type="button">Close</button>
                    <div>
                        <input id="${config.id}-browser-search" type="text" autocomplete="off"
                               placeholder="Type for starting the search"/>
                    </div>
                </div>
                <div class="icd11-browser-body">
                    <div class="icd11-browser-tree">
                        <div class="icd11-browser-column-title">Browse ICD-11 categories</div>
                        <div id="${config.id}-browser-status" class="icd11-status"></div>
                        <ul id="${config.id}-browser-tree"></ul>
                    </div>
                    <div class="icd11-browser-details">
                        <div id="${config.id}-browser-details">
                            <h3>Select an ICD-11 category</h3>
                            <p>Expand the code tree or search to view diagnosis details.</p>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    <% } %>
</div>

<style>
    #${config.id} .icd11-results { margin: 0.25rem 0 1rem; padding: 0; list-style: none; }
    #${config.id} .icd11-results li { border-bottom: 1px solid #ddd; padding: 0.35rem; }
    #${config.id} .icd11-selected { width: 100%; margin-top: 0.5rem; }
    #${config.id} .icd11-selected td, #${config.id} .icd11-selected th { padding: 0.35rem; text-align: left; }
    #${config.id} .icd11-status { color: #666; min-height: 1.2rem; }
    #${config.id} .icd11-open-browser { margin-left: 0.5rem; }
    #${config.id} .icd11-browser-overlay { background: rgba(0, 0, 0, 0.45); bottom: 0; left: 0; position: fixed; right: 0; top: 0; z-index: 10000; }
    #${config.id} .icd11-browser-dialog { background: #fff; height: 82vh; margin: 4vh auto; max-width: 1400px; width: 92vw; }
    #${config.id} .icd11-browser-header { background: #2f008c; color: #fff; font-size: 1.25rem; padding: 0.75rem 1rem; }
    #${config.id} .icd11-browser-header button { float: right; }
    #${config.id} .icd11-browser-header input { margin-top: 0.55rem; width: 31rem; }
    #${config.id} .icd11-browser-body { display: flex; height: calc(82vh - 78px); }
    #${config.id} .icd11-browser-tree { border-right: 1px solid #ccc; box-sizing: border-box; overflow: auto; padding: 0.75rem; width: 44%; }
    #${config.id} .icd11-browser-details { box-sizing: border-box; overflow: auto; padding: 1rem 1.25rem; width: 56%; }
    #${config.id} .icd11-browser-column-title { border-bottom: 1px solid #ddd; font-weight: bold; margin-bottom: 0.5rem; padding-bottom: 0.4rem; }
    #${config.id} .icd11-browser-tree ul { list-style: none; margin: 0; padding-left: 1rem; }
    #${config.id} .icd11-browser-tree li { margin: 0.15rem 0; }
    #${config.id} .icd11-tree-toggle { background: transparent; border: 0; min-width: 1.25rem; padding: 0; }
    #${config.id} .icd11-tree-label { background: transparent; border: 0; color: #222; padding: 0.15rem; text-align: left; }
    #${config.id} .icd11-tree-label:hover { background: #fff1d8; }
    #${config.id} .icd11-code-card { background: #fff7e7; border: 1px solid #e1cda8; margin: 1rem 0; padding: 0.75rem; }
    #${config.id} .icd11-detail-section { margin-top: 1rem; }
    #${config.id} .icd11-detail-section h4 { margin-bottom: 0.35rem; }
    #${config.id} .icd11-detail-list { margin: 0.25rem 0; padding-left: 1.25rem; }
    #${config.id} .icd11-detail-uri { color: #777; float: right; font-size: 0.75rem; }
    #${config.id} .icd11-postcoordination { background: #f7f7f7; border-left: 2px solid #ed7d31; margin: 0.5rem 0; padding: 0.6rem; }
    #${config.id} .icd11-browser-search-results { list-style: none; padding: 0; }
    #${config.id} .icd11-browser-search-results li { border-bottom: 1px solid #ddd; padding: 0.35rem 0; }
</style>

<script type="text/javascript">
    (function(jq) {
        var widget = jq("#${config.id}");
        var hidden = widget.find("input[name='icd11DiagnosesJson']");
        var selected = [];
        var timer;
        var browserTimer;
        var browserLoaded = false;
        var browserSelection;
        var readOnly = ${config.readOnly ? "true" : "false"};

        function decodeInitial() {
            var value = hidden.val();
            if (!value) {
                value = decodeURIComponent(escape(window.atob("${config.initialDiagnosesBase64}")));
            }
            try {
                return value ? JSON.parse(value) : [];
            }
            catch (e) {
                return [];
            }
        }

        function sync() {
            hidden.val(JSON.stringify(selected));
        }

        function renderSelected() {
            var tbody = widget.find("#${config.id}-selected").empty();
            selected.forEach(function(diagnosis, index) {
                var row = jq("<tr>");
                jq("<td>").text((diagnosis.icd11Code ? diagnosis.icd11Code + " - " : "") + diagnosis.title).appendTo(row);
                if (readOnly) {
                    jq("<td>").text(diagnosis.diagnosisType).appendTo(row);
                    jq("<td>").text(diagnosis.certainty).appendTo(row);
                }
                else {
                    var type = jq("<select>").append("<option value='primary'>Primary</option>")
                        .append("<option value='secondary'>Secondary</option>").val(diagnosis.diagnosisType);
                    type.change(function() { diagnosis.diagnosisType = jq(this).val(); sync(); });
                    jq("<td>").append(type).appendTo(row);
                    var certainty = jq("<select>").append("<option value='confirmed'>Confirmed</option>")
                        .append("<option value='presumed'>Presumed</option>").val(diagnosis.certainty);
                    certainty.change(function() { diagnosis.certainty = jq(this).val(); sync(); });
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
                return diagnosis.entityUri === result.entityUri;
            });
            if (!alreadySelected) {
                selected.push({
                    icd11Code: result.icd11Code,
                    entityUri: result.entityUri,
                    foundationUri: result.foundationUri,
                    title: result.title,
                    linearization: result.linearization,
                    diagnosisType: selected.length === 0 ? "primary" : "secondary",
                    certainty: "confirmed",
                    conceptUuid: result.conceptUuid
                });
                sync();
                renderSelected();
            }
        }

        function renderResults(results) {
            var list = widget.find("#${config.id}-results").empty();
            results.forEach(function(result) {
                var add = jq("<button type='button'>").text("Add").click(function() { addDiagnosis(result); });
                jq("<li>").append(jq("<span>").text((result.icd11Code ? result.icd11Code + " - " : "") + result.title + " "))
                    .append(add).appendTo(list);
            });
        }

        function search(query) {
            widget.find("#${config.id}-status").text("Searching...");
            jq.ajax({
                url: openmrsContextPath + "/ws/rest/v1/rwandaemr/icd11/search",
                data: { q: query },
                dataType: "json"
            }).done(function(response) {
                renderResults(response.results || []);
                widget.find("#${config.id}-status").text((response.results || []).length ? "" : "No diagnoses found");
            }).fail(function(xhr) {
                var message = xhr.responseJSON && xhr.responseJSON.message ? xhr.responseJSON.message : "Unable to search ICD-11";
                widget.find("#${config.id}-status").text(message);
                renderResults([]);
            });
        }

        function showBrowserDetails(result) {
            browserSelection = result;
            var details = widget.find("#${config.id}-browser-details").empty();
            jq("<h3>").text((result.icd11Code ? result.icd11Code + " " : "") + result.title).appendTo(details);
            if (result.foundationUri) {
                jq("<div class='icd11-detail-uri'>").text("Foundation URI: " + result.foundationUri).appendTo(details);
            }
            jq("<div class='icd11-code-card'>").text("Code: " + (result.icd11Code || "Not assigned")).appendTo(details);
            appendTextSection(details, "Fully specified name", result.fullySpecifiedName);
            appendTextSection(details, "Description", result.definition);
            appendTextSection(details, "Additional information", result.longDefinition);
            appendTerms(details, "Inclusions", result.inclusions);
            appendTerms(details, "Exclusions", result.exclusions);
            appendTerms(details, "All index terms", result.indexTerms);
            appendUriSection(details, "Related categories in maternal chapter", result.relatedEntitiesInMaternalChapter);
            appendUriSection(details, "Related categories in perinatal chapter", result.relatedEntitiesInPerinatalChapter);
            appendTextSection(details, "Coding note", result.codingNote);
            appendPostcoordination(details, result.postcoordinationScales);
            if (!result.remoteMetadata) {
                jq("<p>").text("WHO metadata is currently unavailable. This local ICD-11 dictionary entry can still be selected.")
                    .appendTo(details);
            }
            if (result.browserUrl) {
                jq("<p>").append(jq("<a target='_blank' rel='noopener'>").attr("href", result.browserUrl)
                    .text("Open this category in the WHO ICD-11 browser")).appendTo(details);
            }
            jq("<button type='button'>").text("Add diagnosis").click(function() {
                addDiagnosis(browserSelection);
                widget.find("#${config.id}-browser").hide();
            }).appendTo(details);
        }

        function appendTextSection(details, title, text) {
            if (!text) {
                return;
            }
            var section = jq("<div class='icd11-detail-section'>").appendTo(details);
            jq("<h4>").text(title).appendTo(section);
            jq("<p>").text(text).appendTo(section);
        }

        function appendTerms(details, title, terms) {
            if (!terms || !terms.length) {
                return;
            }
            var section = jq("<div class='icd11-detail-section'>").appendTo(details);
            jq("<h4>").text(title + " (" + terms.length + ")").appendTo(section);
            var list = jq("<ul class='icd11-detail-list'>").appendTo(section);
            terms.forEach(function(term) {
                jq("<li>").text(term.label || term.linearizationReference || term.foundationReference).appendTo(list);
            });
        }

        function appendUriSection(details, title, values) {
            if (!values || !values.length) {
                return;
            }
            var section = jq("<div class='icd11-detail-section'>").appendTo(details);
            jq("<h4>").text(title).appendTo(section);
            var list = jq("<ul class='icd11-detail-list'>").appendTo(section);
            values.forEach(function(value) { jq("<li>").text(value).appendTo(list); });
        }

        function appendPostcoordination(details, scales) {
            if (!scales || !scales.length) {
                return;
            }
            var section = jq("<div class='icd11-detail-section'>").appendTo(details);
            jq("<h4>").text("Postcoordination").appendTo(section);
            scales.forEach(function(scale) {
                var axis = jq("<div class='icd11-postcoordination'>").appendTo(section);
                jq("<strong>").text(readableAxisName(scale.axisName)
                    + (scale.required ? " (required)" : " (optional)")).appendTo(axis);
                if (scale.scaleEntities && scale.scaleEntities.length) {
                    jq("<div>").text("Allowed value sets: " + scale.scaleEntities.join(", ")).appendTo(axis);
                }
            });
        }

        function readableAxisName(axisName) {
            if (!axisName) {
                return "Additional detail";
            }
            return axisName.substring(axisName.lastIndexOf("/") + 1).replace(/([A-Z])/g, " \$1").trim();
        }

        function loadBrowserCode(code) {
            widget.find("#${config.id}-browser-status").text("Loading diagnosis...");
            jq.ajax({
                url: openmrsContextPath + "/ws/rest/v1/rwandaemr/icd11/code/" + encodeURIComponent(code),
                dataType: "json"
            }).done(function(result) {
                widget.find("#${config.id}-browser-status").text("");
                showBrowserDetails(result);
            }).fail(function() {
                widget.find("#${config.id}-browser-status").text("Unable to load ICD-11 diagnosis details");
            });
        }

        function renderBrowserNodes(nodes, list) {
            list.empty();
            nodes.forEach(function(node) {
                var item = jq("<li>");
                var toggle = jq("<button type='button' class='icd11-tree-toggle'>")
                    .text(node.hasChildren ? "\u25b8" : "");
                var label = jq("<button type='button' class='icd11-tree-label'>")
                    .text(node.code.indexOf("chapter:") === 0 ? node.title : node.code + (node.title ? " " + node.title : ""));
                var children = jq("<ul style='display: none;'>");
                function expand() {
                    if (!node.hasChildren) {
                        return;
                    }
                    if (children.data("loaded")) {
                        children.toggle();
                        toggle.text(children.is(":visible") ? "\u25be" : "\u25b8");
                        return;
                    }
                    toggle.text("\u25be");
                    children.show().data("loaded", true);
                    loadBrowserBranch(node.code, children);
                }
                toggle.click(expand);
                label.click(function() {
                    if (node.code.indexOf("chapter:") !== 0) {
                        loadBrowserCode(node.code);
                    }
                    expand();
                });
                item.append(toggle).append(label).append(children).appendTo(list);
            });
        }

        function loadBrowserBranch(parent, list) {
            widget.find("#${config.id}-browser-status").text("Loading ICD-11 categories...");
            jq.ajax({
                url: openmrsContextPath + "/ws/rest/v1/rwandaemr/icd11/browse",
                data: parent ? { parent: parent } : {},
                dataType: "json"
            }).done(function(response) {
                widget.find("#${config.id}-browser-status").text("");
                renderBrowserNodes(response.results || [], list);
            }).fail(function() {
                widget.find("#${config.id}-browser-status").text("Unable to load ICD-11 categories");
            });
        }

        function browserSearch(query) {
            var details = widget.find("#${config.id}-browser-details").empty();
            details.append(jq("<h3>").text("Search results"));
            var list = jq("<ul class='icd11-browser-search-results'>").appendTo(details);
            jq.ajax({
                url: openmrsContextPath + "/ws/rest/v1/rwandaemr/icd11/search",
                data: { q: query },
                dataType: "json"
            }).done(function(response) {
                var results = response.results || [];
                if (!results.length) {
                    list.append(jq("<li>").text("No diagnoses found"));
                }
                results.forEach(function(result) {
                    var item = jq("<li>");
                    jq("<button type='button' class='icd11-tree-label'>")
                        .text((result.icd11Code ? result.icd11Code + " " : "") + result.title)
                        .click(function() { showBrowserDetails(result); }).appendTo(item);
                    item.appendTo(list);
                });
            }).fail(function() {
                list.append(jq("<li>").text("Unable to search ICD-11 diagnoses"));
            });
        }

        selected = decodeInitial();
        renderSelected();
        if (!readOnly) {
            widget.find("#${config.id}-open-browser").click(function() {
                widget.find("#${config.id}-browser").show();
                if (!browserLoaded) {
                    browserLoaded = true;
                    loadBrowserBranch("", widget.find("#${config.id}-browser-tree"));
                }
            });
            widget.find("#${config.id}-close-browser").click(function() {
                widget.find("#${config.id}-browser").hide();
            });
            widget.find("#${config.id}-browser-search").on("input", function() {
                var query = jq(this).val().trim();
                clearTimeout(browserTimer);
                if (query.length < 3) {
                    return;
                }
                browserTimer = setTimeout(function() { browserSearch(query); }, 300);
            });
            widget.find("#${config.id}-search").on("input", function() {
                var query = jq(this).val().trim();
                clearTimeout(timer);
                if (query.length < 3) {
                    renderResults([]);
                    widget.find("#${config.id}-status").text("");
                    return;
                }
                timer = setTimeout(function() { search(query); }, 300);
            });
        }
    }(jQuery));
</script>
