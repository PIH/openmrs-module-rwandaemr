<%
ui.includeJavascript("rwandaemr", "custom/hie.js")
ui.includeCss("rwandaemr", "hie/hie.css")
%>

<div id="quick-view-dialog" class="dialog" style="display: none">
    <div class="dialog-header">
        <i class="icon-check-in"></i>
        <h3 id="encounter_title">
            ${ ui.message("rwandaemr.hie.viewObs.title") }
        </h3>
    </div>
    <div class="dialog-content">
        <div id="hie-observation-data"></div>
        <button id="" class="confirm right">${ ui.message("rwandaemr.hie.done") }<i class="icon-spinner icon-spin icon-2x" style="display: none; margin-left: 10px;"></i></button>
        <button class="cancel">${ ui.message("coreapps.cancel") }</button>
    </div>
</div>

<div id="ips-view-dialog" class="dialog" style="display: none">
    <div class="dialog-header">
        <i class="icon-file-text"></i>
        <h3 id="ips_title">International Patient Summary (IPS)</h3>
    </div>
    <div class="dialog-content">
        <div id="ips-data"></div>
        <button class="cancel">${ ui.message("coreapps.cancel") }</button>
    </div>
</div>
<div class="info-section">
    <div class="info-header">
        <i class="icon-calendar"></i>
        <h3>${ ui.message(config.label ? config.label : "Past Clinical Summary").toUpperCase() }</h3>
        
    </div>
    <div class="info-body">
        <a id="open-past-history-link"
           data-upid="${upid}"
           data-load-url="${ ui.actionLink("rwandaemr", "patient/hieEncountersSection", "loadPastHistory") }"
           title="Check Past clinical history"
           href="javascript:void(0);">Past History <i class="fas fa-eye"></i></a>
        <span style="margin: 0 8px;">|</span>
        <a id="open-ips-link"
           data-upid="${upid}"
           data-load-ips-url="${ ui.actionLink("rwandaemr", "patient/hieEncountersSection", "loadIps") }"
           title="Open International Patient Summary"
           href="javascript:void(0);">International Patient Summary (IPS) <i class="fas fa-eye"></i></a>
        <g:if test="${error}">
            <div style="color: red; font-weight: bold; margin-top: 10px;">${error}</div>
        </g:if>
    </div>
</div>

<style>
.accordion-header {
  background: #00473f;
  color: white;
  padding: 10px;
  cursor: pointer;
}
.accordion-content {
  display: none;
  padding: 10px;
  border: 1px solid #00473f;
  border-top: none;
}

/* Dialog positioning and scrolling */
#quick-view-dialog.dialog {
  position: fixed;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  z-index: 10000;
  background: white;
  border: 1px solid #00473f;
  border-radius: 4px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.3);
  max-width: 90%;
  max-height: 90vh;
  width: 800px;
  display: flex;
  flex-direction: column;
}

#ips-view-dialog.dialog {
  position: fixed;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  z-index: 10000;
  background: white;
  border: 1px solid #00473f;
  border-radius: 4px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.3);
  max-width: 90%;
  max-height: 90vh;
  width: 900px;
  display: flex;
  flex-direction: column;
}

#quick-view-dialog .dialog-header {
  padding: 15px 20px;
  border-bottom: 1px solid #00473f;
  background: #00473f;
  border-radius: 4px 4px 0 0;
  flex-shrink: 0;
}

#ips-view-dialog .dialog-header {
  padding: 15px 20px;
  border-bottom: 1px solid #00473f;
  background: #00473f;
  border-radius: 4px 4px 0 0;
  flex-shrink: 0;
}

#quick-view-dialog .dialog-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: bold;
}

#ips-view-dialog .dialog-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: bold;
}

#quick-view-dialog .dialog-content {
  padding: 20px;
  overflow-y: auto;
  overflow-x: hidden;
  flex: 1;
  min-height: 0;
}

#ips-view-dialog .dialog-content {
  padding: 20px;
  overflow-y: auto;
  overflow-x: hidden;
  flex: 1;
  min-height: 0;
}

#quick-view-dialog .dialog-instructions {
  margin-bottom: 15px;
  color: #666;
  font-size: 14px;
}

#quick-view-dialog #hie-observation-data {
  max-height: 60vh;
  overflow-y: auto;
  overflow-x: hidden;
  margin-bottom: 15px;
}

#quick-view-dialog .dialog-content .confirm,
#quick-view-dialog .dialog-content .cancel {
  margin-top: 10px;
}

#ips-view-dialog #ips-data {
  max-height: 60vh;
  overflow-y: auto;
  overflow-x: hidden;
  margin-bottom: 15px;
}

.ips-tabs {
  display: flex;
  gap: 8px;
  border-bottom: 1px solid #d9d9d9;
  padding-bottom: 8px;
  margin-bottom: 12px;
  overflow-x: auto;
}

.ips-tab-btn {
  border: 1px solid #c8c8c8;
  background: #f6f6f6;
  color: #222;
  padding: 6px 10px;
  border-radius: 4px;
  cursor: pointer;
  white-space: nowrap;
  font-size: 13px;
}

.ips-tab-btn.active {
  background: #00473f;
  border-color: #00473f;
  color: white;
}

.ips-panel {
  display: none;
}

.ips-panel.active {
  display: block;
}

.ips-card {
  border: 1px solid #e5e5e5;
  border-radius: 4px;
  margin-bottom: 10px;
  padding: 10px;
  background: #fff;
}

.ips-key {
  text-transform: capitalize;
}

.ips-obs-table {
  width: 100%;
  border-collapse: collapse;
  margin-top: 6px;
}

.ips-obs-table th,
.ips-obs-table td {
  border: 1px solid #e5e5e5;
  padding: 6px;
  text-align: left;
  font-size: 12px;
}

.ips-obs-table th {
  background: #f7f7f7;
}

.ips-empty {
  color: #777;
  font-style: italic;
}
</style>

<script>
  jq(document).ready(function () {
    // Use delegated event binding because accordion sections are loaded dynamically into the modal.
    jq(document).on('click', '#hie-observation-data .accordion-header', function () {
      const content = jq(this).next('.accordion-content');

      // Close all other contents in this modal body
      jq('#hie-observation-data .accordion-content').not(content).slideUp();

      // Toggle current content
      content.slideToggle();
    });
  });
</script>

<script type="text/javascript">
    var hieEncounterListHtmlCache = null;
    var hieEncounterListTitleCache = null;
    var ipsViewDialog = null;

    function showIpsDialog() {
        if (ipsViewDialog == null && typeof emr !== "undefined" && typeof emr.setupConfirmationDialog === "function") {
            ipsViewDialog = emr.setupConfirmationDialog({
                selector: "#ips-view-dialog",
                actions: {
                    confirm: function () {
                        ipsViewDialog.close();
                    },
                    cancel: function () {
                        ipsViewDialog.close();
                    }
                }
            });
            ipsViewDialog.close();
        }
        if (ipsViewDialog && typeof ipsViewDialog.show === "function") {
            ipsViewDialog.show();
        } else {
            jq("#ips-view-dialog").show();
        }
    }

    function escHtml(value) {
        if (value === null || value === undefined) {
            return "";
        }
        var text = String(value);
        text = text.split("&").join("&amp;");
        text = text.split("<").join("&lt;");
        text = text.split(">").join("&gt;");
        text = text.split("\"").join("&quot;");
        text = text.split("'").join("&#39;");
        return text;
    }

    function renderIpsTabs(data) {
        var tabs = (data && data.tabs) ? data.tabs : [];
        var meta = (data && data.meta) ? data.meta : {};
        var html = "";
        html += "<div style='margin-bottom: 10px; color: #444;'>";
        html += "<strong>Bundle ID:</strong> " + escHtml(meta.bundleId || "") + " &nbsp; ";
        html += "<strong>Timestamp:</strong> " + escHtml(meta.timestamp || "");
        html += "</div>";
        html += "<div class='ips-tabs' id='ips-tabs-header'>";
        for (var i = 0; i < tabs.length; i++) {
            var tab = tabs[i] || {};
            var items = tab.items || [];
            var btnClass = i === 0 ? "ips-tab-btn active" : "ips-tab-btn";
            html += "<button type='button' class='" + btnClass + "' data-ips-tab='" + i + "'>";
            html += escHtml(tab.title || ("Tab " + (i + 1))) + " (" + items.length + ")";
            html += "</button>";
        }
        html += "</div>";
        html += "<div id='ips-tabs-content'>";
        for (var t = 0; t < tabs.length; t++) {
            var oneTab = tabs[t] || {};
            var oneItems = oneTab.items || [];
            var panelClass = t === 0 ? "ips-panel active" : "ips-panel";
            html += "<div class='" + panelClass + "' data-ips-panel='" + t + "'>";
            if (!oneItems.length) {
                html += "<div class='ips-empty'>No data in this section.</div>";
            } else {
                for (var j = 0; j < oneItems.length; j++) {
                    var item = oneItems[j] || {};
                    html += "<div class='ips-card'>";
                    for (var key in item) {
                        if (Object.prototype.hasOwnProperty.call(item, key)) {
                            if (key === "observations" && Object.prototype.toString.call(item[key]) === "[object Array]") {
                                html += "<div><strong class='ips-key'>Observations:</strong></div>";
                                if (!item[key].length) {
                                    html += "<div class='ips-empty'>No observations linked to this encounter.</div>";
                                } else {
                                    html += "<table class='ips-obs-table'><thead><tr><th>Code</th><th>Value</th><th>Category</th><th>Effective</th></tr></thead><tbody>";
                                    for (var o = 0; o < item[key].length; o++) {
                                        var obs = item[key][o] || {};
                                        html += "<tr>";
                                        html += "<td>" + escHtml(obs.code || "") + "</td>";
                                        html += "<td>" + escHtml(obs.value || "") + "</td>";
                                        html += "<td>" + escHtml(obs.category || "") + "</td>";
                                        html += "<td>" + escHtml(obs.effective || "") + "</td>";
                                        html += "</tr>";
                                    }
                                    html += "</tbody></table>";
                                }
                            } else {
                                html += "<div><strong class='ips-key'>" + escHtml(key) + ":</strong> " + escHtml(item[key]) + "</div>";
                            }
                        }
                    }
                    html += "</div>";
                }
            }
            html += "</div>";
        }
        html += "</div>";
        return html;
    }

    jq(document).on("click", "#hie-observation-data .open_encounter_pop", function (e) {
        e.preventDefault();
        var clicked = jq(this);
        var url = window.location.origin + clicked.data("url");
        var encounterType = clicked.data("encounter_type") || "Encounter";
        var encounterLocation = clicked.data("encounter_location") || "";
        var encounterDate = clicked.data("encounter_date") || "";
        if (hieEncounterListHtmlCache === null) {
            hieEncounterListHtmlCache = jq("#hie-observation-data").html();
            hieEncounterListTitleCache = jq("#encounter_title").html();
        }
        jq("#encounter_title").html(encounterType);
        jq("#hie-observation-data").html(
            "<div style='padding: 15px 10px; color: #444;'>" +
            "<i class='icon-spinner icon-spin' style='margin-right: 8px;'></i>" +
            "Loading observation details..." +
            "</div>"
        );
        if (typeof hie !== "undefined" && typeof hie.showQuickViewDialog === "function" && jq("#quick-view-dialog:visible").length === 0) {
            hie.showQuickViewDialog(clicked.data("uuid"));
        }
        jq("#hie-observation-data").load(url, function (response, status, xhr) {
            if (status == "error") {
                jq("#hie-observation-data").html("<p>Error loading observations.</p>");
            } else {
                var backLinkHtml = "<div style='margin-bottom: 10px;'><a href='javascript:void(0);' id='back-to-encounter-list'><i class='icon-arrow-left'></i> Back to encounter list</a></div>";
                jq("#hie-observation-data").prepend(backLinkHtml);
            }
        });
    });

    jq(document).on("click", "#back-to-encounter-list", function (e) {
        e.preventDefault();
        if (hieEncounterListHtmlCache !== null) {
            jq("#encounter_title").html(hieEncounterListTitleCache || "Past History");
            jq("#hie-observation-data").html(hieEncounterListHtmlCache);
        }
    });

    jq("#open-past-history-link").on("click", function (e) {
        e.preventDefault();
        var clicked = jq(this);
        var upid = clicked.data("upid");
        var loadUrl = clicked.data("load-url");

        jq("#encounter_title").html("Past History");
        jq("#hie-observation-data").html("<p>Loading...</p>");
        hieEncounterListHtmlCache = null;
        hieEncounterListTitleCache = null;
        if (typeof hie !== "undefined" && typeof hie.showQuickViewDialog === "function") {
            hie.showQuickViewDialog(upid);
        }

        jq.ajax({
            url: loadUrl,
            type: "GET",
            dataType: "json",
            data: { upid: upid }
        }).done(function (data) {
            var html = "";
            if (!data || data.success !== true) {
                var msg = data && data.message ? data.message : "Error retrieving HIE encounters.";
                jq("#hie-observation-data").html("<p style='color:red;'>" + msg + "</p>");
                return;
            }

            jq("p#encounter_header").html("");
            var visits = data.visits || [];
            if (visits.length === 0) {
                jq("#hie-observation-data").html("<p>${ ui.message("emr.none") }</p>");
                return;
            }

            html += "<div class='accordion'>";
            jq.each(visits, function (idx, visit) {
                html += "<div class='accordion-item'>";
                html += "<div class='accordion-header'>" + (visit.location || "Unspecified") + "</div>";
                html += "<div class='accordion-content'>";
                html += "<table id='encounter-list-table-" + idx + "'>";
                html += "<thead><tr>";
                html += "<th>${ ui.message("rwandaemr.encounterList.encounterDatetime") }</th>";
                html += "<th>${ ui.message("rwandaemr.encounterList.encounterType") }</th>";
                html += "<th>${ ui.message("rwandaemr.encounterList.location") }</th>";
                html += "<th>${ ui.message("rwandaemr.hie.encounterLis.actions") }</th>";
                html += "</tr></thead><tbody>";

                var encounters = visit.encounters || [];
                if (encounters.length === 0) {
                    html += "<tr><td colspan='4'>${ ui.message("emr.none") }</td></tr>";
                } else {
                    jq.each(encounters, function (_, encounter) {
                        var encounterDate = encounter.encounterDatetime || "";
                        var encounterType = encounter.encounterType || "";
                        var encounterLocation = encounter.location || "";
                        var encounterUuid = encounter.uuid || "";
                        var obsUrl = "${ui.pageLink("rwandaemr", "patient/hieObservationsSection")}?encounterUuid=" + encodeURIComponent(encounterUuid);
                        html += "<tr>";
                        html += "<td>" + encounterDate + "</td>";
                        html += "<td>" + encounterType + "</td>";
                        html += "<td>" + encounterLocation + "</td>";
                        html += "<td><a class='open_encounter_pop' data-encounter_date='" + encounterDate + "' data-encounter_type='" + encounterType + "' data-encounter_location='" + encounterLocation + "' data-url='" + obsUrl + "' data-uuid='" + encounterUuid + "' href='javascript:void(0);'><i class='fas fa-eye'></i></a></td>";
                        html += "</tr>";
                    });
                }
                html += "</tbody></table></div></div>";
            });
            html += "</div>";
            jq("#hie-observation-data").html(html);
            hieEncounterListHtmlCache = html;
            hieEncounterListTitleCache = "Past History";
        }).fail(function () {
            jq("#hie-observation-data").html("<p style='color:red;'>Error retrieving HIE encounters.</p>");
        });
    });

    jq("#open-ips-link").on("click", function (e) {
        e.preventDefault();
        var clicked = jq(this);
        var upid = clicked.data("upid") || "";
        var loadIpsUrl = clicked.data("load-ips-url") || "";
        jq("#ips_title").html("International Patient Summary (IPS)");
        jq("#ips-data").html(
            "<div style='padding: 10px 0;'>" +
            "<i class='icon-spinner icon-spin' style='margin-right: 8px;'></i>" +
            "Loading IPS..." +
            "</div>"
        );
        showIpsDialog();

        jq.ajax({
            url: loadIpsUrl,
            type: "GET",
            dataType: "json",
            data: { upid: upid }
        }).done(function (data) {
            if (!data || data.success !== true) {
                var msg = data && data.message ? data.message : "Error retrieving IPS.";
                jq("#ips-data").html("<p style='color:red;'>" + escHtml(msg) + "</p>");
                return;
            }
            jq("#ips-data").html(renderIpsTabs(data));
        }).fail(function () {
            jq("#ips-data").html("<p style='color:red;'>Error retrieving IPS.</p>");
        });
    });

    jq(document).on("click", ".ips-tab-btn", function () {
        var idx = jq(this).attr("data-ips-tab");
        jq(".ips-tab-btn").removeClass("active");
        jq(this).addClass("active");
        jq(".ips-panel").removeClass("active");
        jq(".ips-panel[data-ips-panel='" + idx + "']").addClass("active");
    });
</script>