<%
    ui.decorateWith("appui", "standardEmrPage")
%>

<script type="text/javascript">
    var breadcrumbs = [
        { icon: "icon-home", link: '/' + OPENMRS_CONTEXT_PATH + '/index.htm' },
        { label: "Queue Dashboard", link: "${ ui.pageLink("rwandaemr", "queue/queueDashboard") }" }
    ];

    jq(function() {
        var filterForm = jq("#queue-dashboard-filters");
        var pendingSessionLocationId = null;
        var locationRefreshStarted = false;
        if (!filterForm.length) {
            return;
        }

        jq("#queue-location-filter, #queue-assignment-filter").change(function() {
            filterForm.get(0).submit();
        });

        var patientNameFilter = jq("#queue-patient-name-filter");
        var patientNameFilterTimer = null;
        patientNameFilter.on("input", function() {
            window.clearTimeout(patientNameFilterTimer);
            patientNameFilterTimer = window.setTimeout(function() {
                filterForm.get(0).submit();
            }, 350);
        }).on("keydown", function(event) {
            if (event.which === 13) {
                window.clearTimeout(patientNameFilterTimer);
            }
        });

        jq("#queue-page-size").change(function() {
            filterForm.get(0).submit();
        });

        jq(document).on("click.queueDashboard", "#session-location ul.select li", function() {
            pendingSessionLocationId = jq(this).attr("locationId") || null;
        });

        jq(document).on("sessionLocationChanged.queueDashboard", function() {
            if (locationRefreshStarted) {
                return;
            }

            var status = jq("#queue-status-filter").val() || "ALL";
            var arrivalDay = jq("#queue-arrival-day-filter").val() || "TODAY";
            var patientName = jq("#queue-patient-name-filter").val() || "";
            var assignment = jq("#queue-assignment-filter").val() || "ALL";
            var pageSize = jq("#queue-page-size").val() || "10";
            var selectedLocationId = pendingSessionLocationId
                    || jq("#session-location ul.select li.selected").first().attr("locationId");
            if (!selectedLocationId && typeof sessionLocationModel !== "undefined" && sessionLocationModel.id) {
                selectedLocationId = sessionLocationModel.id();
            }

            var query = "?status=" + encodeURIComponent(status)
                    + "&arrivalDay=" + encodeURIComponent(arrivalDay)
                    + "&patientName=" + encodeURIComponent(patientName)
                    + "&assignment=" + encodeURIComponent(assignment)
                    + "&pageSize=" + encodeURIComponent(pageSize);
            if (selectedLocationId) {
                query += "&locationId=" + encodeURIComponent(selectedLocationId);
            }
            locationRefreshStarted = true;
            window.location.replace("${ ui.pageLink("rwandaemr", "queue/queueDashboard") }" + query);
        });

        jq(document).on("submit.queueDashboardStatus", ".queue-status-action", function(event) {
            var form = jq(this);
            var statusLabel = form.find("select[name='action'] option:selected").text();
            var patientName = form.attr("data-patient-name") || "this patient";
            if (!window.confirm("Change " + patientName + " status to " + statusLabel + "?")) {
                event.preventDefault();
            }
        });

        function copyPatientIdentifier(identifier) {
            var textArea = document.createElement("textarea");
            textArea.value = identifier;
            textArea.setAttribute("readonly", "readonly");
            textArea.style.position = "fixed";
            textArea.style.left = "-9999px";
            document.body.appendChild(textArea);
            try {
                textArea.focus();
                textArea.select();
                textArea.setSelectionRange(0, textArea.value.length);
                document.execCommand("copy");
            } finally {
                document.body.removeChild(textArea);
            }
        }

        jq(document).on("click.queueDashboardLabOrders", ".queue-lab-orders-link", function(event) {
            event.preventDefault();
            var link = jq(this);
            var form = link.closest("form").get(0);
            var identifier = link.attr("data-patient-identifier") || "";
            if (!form || link.data("opening")) {
                return;
            }
            link.data("opening", true);

            var openLabOrders = function() {
                form.submit();
            };
            if (navigator.clipboard && navigator.clipboard.writeText && window.isSecureContext) {
                navigator.clipboard.writeText(identifier).then(openLabOrders, function() {
                    try {
                        copyPatientIdentifier(identifier);
                    } finally {
                        openLabOrders();
                    }
                });
            } else {
                try {
                    copyPatientIdentifier(identifier);
                } finally {
                    openLabOrders();
                }
            }
        });

        var queueLiveRegion = jq("#queue-live-region");
        var queueCount = jq("#queue-patient-count");
        var queueRefreshInProgress = false;

        function queueInteractionInProgress() {
            var vitalsFrameSource = jq("#queue-vitals-frame").attr("src");
            return document.hidden
                    || jq(document.activeElement).closest("#queue-live-region").length > 0
                    || jq("#queue-transfer-reason-dialog:visible").length > 0
                    || jq("#queue-provider-assignment-dialog:visible").length > 0
                    || (vitalsFrameSource && vitalsFrameSource !== "about:blank");
        }

        function refreshQueueLiveRegion() {
            if (!queueLiveRegion.length || queueRefreshInProgress || queueInteractionInProgress()) {
                return;
            }

            queueRefreshInProgress = true;
            jq.ajax({
                url: queueLiveRegion.attr("data-refresh-url"),
                type: "GET",
                dataType: "html",
                cache: false,
                global: false
            }).done(function(responseHtml) {
                var responseDocument = new window.DOMParser().parseFromString(responseHtml, "text/html");
                var refreshedRegion = responseDocument.getElementById("queue-live-region");
                var refreshedCount = responseDocument.getElementById("queue-patient-count");
                if (!refreshedRegion || !refreshedCount) {
                    return;
                }

                if (queueLiveRegion.html() !== refreshedRegion.innerHTML) {
                    queueLiveRegion.html(refreshedRegion.innerHTML);
                }
                queueCount.text(refreshedCount.textContent || refreshedCount.innerText);
            }).always(function() {
                queueRefreshInProgress = false;
            });
        }

        var queueRefreshTimer = window.setInterval(refreshQueueLiveRegion, 10000);
        jq(window).one("beforeunload.queueDashboard", function() {
            window.clearInterval(queueRefreshTimer);
        });

        var vitalsDialogElement = jq("#queue-vitals-dialog");
        if (vitalsDialogElement.length) {
            var vitalsFrame = jq("#queue-vitals-frame");
            var vitalsLoading = jq("#queue-vitals-loading");
            var vitalsCompletedForm = jq("#queue-vitals-completed-form");
            var queueDashboardPath = "${ ui.encodeJavaScript(ui.pageLink("rwandaemr", "queue/queueDashboard")) }";
            var vitalsFormPath = "${ ui.encodeJavaScript(ui.pageLink("htmlformentryui", "htmlform/enterHtmlFormWithSimpleUi")) }";
            var vitalsDialog;
            var vitalsCompleting = false;

            function queryParameter(search, name) {
                var query = search.charAt(0) === "?" ? search.substring(1) : search;
                var match = new RegExp("(?:^|&)" + name + "=([^&]*)").exec(query);
                return match ? decodeURIComponent(match[1].split("+").join(" ")) : null;
            }

            function prepareEmbeddedVitalsFrame(frame) {
                var frameDocument = frame.contentDocument || frame.contentWindow.document;
                if (!frameDocument.getElementById("queue-vitals-embedded-style")) {
                    var style = frameDocument.createElement("style");
                    style.id = "queue-vitals-embedded-style";
                    style.type = "text/css";
                    style.appendChild(frameDocument.createTextNode(
                            "body > header, body > #breadcrumbs, .patient-header, .secondLineFragments, "
                            + ".visit-location, .visit-dates, #form-actions-container, "
                            + ".active-visit-started-at-message, .active-visit-message, "
                            + "#edit-patient-identifier-dialog { display: none !important; } "
                            + "#body-wrapper { margin-top: 0 !important; padding-top: 0 !important; } "
                            + "html, body, #body-wrapper, #content { box-sizing: border-box; max-width: 100%; min-width: 0; } "
                            + "body { overflow-x: hidden !important; } "
                            + "#content { padding-top: 8px !important; width: 100% !important; } "
                            + "#queue-latest-vitals-summary { box-sizing: border-box; max-width: 100%; "
                            + "min-width: 0; overflow: hidden; width: 100%; } "
                            + ".queue-latest-vitals { background: #f6fbfa; border-bottom: 1px solid #c8dcda; "
                            + "border-left: 4px solid #007b83; border-top: 1px solid #c8dcda; box-sizing: border-box; "
                            + "margin: 0 0 16px; max-width: 100%; min-width: 0; overflow: hidden; "
                            + "padding: 11px 14px 13px; width: 100%; } "
                            + ".queue-latest-vitals-header { align-items: center; display: flex; flex-wrap: wrap; "
                            + "gap: 6px 12px; justify-content: space-between; margin-bottom: 10px; min-width: 0; } "
                            + ".queue-latest-vitals-title { align-items: center; color: #174d50; display: inline-flex; "
                            + "font-size: 16px; font-weight: bold; gap: 6px; max-width: 100%; min-width: 0; } "
                            + ".queue-latest-vitals-time { color: #586767; font-size: 12px; white-space: normal; } "
                            + ".queue-latest-vitals-values { display: flex; flex-wrap: wrap; gap: 10px 6px; "
                            + "max-width: 100%; min-width: 0; width: 100%; } "
                            + ".queue-latest-vital { border-left: 3px solid #62aeb2; box-sizing: border-box; "
                            + "flex: 1 1 125px; max-width: 100%; min-width: 0; padding: 1px 8px; } "
                            + ".queue-latest-vital:nth-child(4n+2) { border-left-color: #5b82c4; } "
                            + ".queue-latest-vital:nth-child(4n+3) { border-left-color: #d0943d; } "
                            + ".queue-latest-vital:nth-child(4n+4) { border-left-color: #5b9b69; } "
                            + ".queue-latest-vital-label { color: #637171; display: block; font-size: 11px; "
                            + "line-height: 1.25; margin-bottom: 2px; overflow-wrap: anywhere; word-break: break-word; } "
                            + ".queue-latest-vital-value { color: #243333; display: block; font-size: 16px; "
                            + "line-height: 1.25; overflow-wrap: anywhere; word-break: break-word; } "
                            + ".queue-latest-vital-unit { color: #637171; font-size: 11px; font-weight: normal; "
                            + "margin-left: 3px; }"
                    ));
                    frameDocument.head.appendChild(style);
                }

                var embeddedHeaderElements = frameDocument.querySelectorAll(
                        ".visit-location, .visit-dates, #form-actions-container");
                for (var elementIndex = 0; elementIndex < embeddedHeaderElements.length; elementIndex++) {
                    var embeddedHeaderElement = embeddedHeaderElements[elementIndex];
                    if (embeddedHeaderElement.parentNode) {
                        embeddedHeaderElement.parentNode.removeChild(embeddedHeaderElement);
                    }
                }

                var previousSummary = frameDocument.getElementById("queue-latest-vitals-summary");
                if (previousSummary && previousSummary.parentNode) {
                    previousSummary.parentNode.removeChild(previousSummary);
                }
                var summaryHtml = vitalsFrame.data("latest-vitals-html");
                var content = frameDocument.getElementById("content");
                if (summaryHtml && content) {
                    var summary = frameDocument.createElement("div");
                    summary.id = "queue-latest-vitals-summary";
                    summary.innerHTML = summaryHtml;
                    content.insertBefore(summary, content.firstChild);
                }
            }

            function closeVitalsDialog() {
                vitalsDialog.close();
                vitalsFrame.attr("src", "about:blank");
            }

            vitalsDialog = emr.setupConfirmationDialog({
                selector: "#queue-vitals-dialog",
                actions: {
                    cancel: closeVitalsDialog
                },
                dialogOpts: {
                    overlayClose: false
                }
            });

            jq(document).on("click.queueDashboardVitals", ".queue-vitals-link", function(event) {
                event.preventDefault();
                var link = jq(this);
                var patientName = link.attr("data-patient-name");

                vitalsCompleting = false;
                jq("#queue-vitals-patient").text(patientName);
                vitalsFrame.attr("title", "Collect vitals for " + patientName);
                vitalsFrame.attr("data-return-url", link.attr("data-return-url"));
                var summaryTemplate = jq("#queue-vitals-summary-" + link.attr("data-entry-id"));
                vitalsFrame.data("latest-vitals-html", summaryTemplate.length ? summaryTemplate.html() : "");
                vitalsCompletedForm.find("[name='entryId']").val(link.attr("data-entry-id"));
                vitalsLoading.css("display", "flex");
                vitalsDialog.show();
                vitalsFrame.attr("src", link.attr("href"));
                jq("#queue-vitals-close").focus();
            });

            vitalsFrame.on("load", function() {
                var frame = this;
                var source = vitalsFrame.attr("src");
                if (!source || source === "about:blank") {
                    return;
                }

                try {
                    var frameLocation = frame.contentWindow.location;
                    if (frameLocation.pathname === queueDashboardPath) {
                        var encounterId = queryParameter(frameLocation.search, "encounterId");
                        if (encounterId && !vitalsCompleting) {
                            vitalsCompleting = true;
                            vitalsCompletedForm.find("[name='encounterId']").val(encounterId);
                            vitalsCompletedForm.get(0).submit();
                            return;
                        }
                        window.location.href = vitalsFrame.attr("data-return-url");
                        return;
                    }
                    if (frameLocation.pathname === vitalsFormPath) {
                        prepareEmbeddedVitalsFrame(frame);
                    }
                }
                catch (ignored) {
                    // The form is expected to be same-origin; leave it visible if a proxy changes that.
                }

                vitalsLoading.hide();
            });
        }
    });
</script>

<style>
    .queue-page-header {
        align-items: center;
        display: flex;
        gap: 10px;
        justify-content: space-between;
        margin-bottom: 14px;
    }

    .queue-page-header h3 {
        margin: 0;
    }

    .queue-count {
        background: #f1f4f4;
        border: 1px solid #d7dddd;
        border-radius: 3px;
        color: #465252;
        font-size: 0.85em;
        font-weight: bold;
        padding: 4px 8px;
        white-space: nowrap;
    }

    .queue-toolbar {
        align-items: end;
        background: #f7f9f9;
        border: 1px solid #d7dddd;
        border-radius: 4px;
        display: flex;
        flex-wrap: wrap;
        gap: 10px 14px;
        margin-bottom: 16px;
        padding: 12px;
    }

    .queue-filter-field {
        min-width: 190px;
    }

    .queue-toolbar label {
        color: #394646;
        display: block;
        font-size: 0.9em;
        font-weight: bold;
        margin-bottom: 4px;
    }

    .queue-toolbar select,
    .queue-toolbar input[type="search"] {
        box-sizing: border-box;
        max-width: 100%;
        min-width: 190px;
    }

    .queue-toolbar .button {
        margin: 0;
    }

    .queue-pagination {
        align-items: center;
        display: flex;
        flex-wrap: wrap;
        gap: 10px 16px;
        justify-content: space-between;
        margin-top: 12px;
    }

    .queue-page-summary {
        color: #526060;
        font-size: 0.9em;
    }

    .queue-page-links {
        align-items: center;
        display: flex;
        flex-wrap: wrap;
        gap: 5px;
    }

    .queue-page-link {
        align-items: center;
        background: #fff;
        border: 1px solid #c7d0d0;
        border-radius: 3px;
        box-sizing: border-box;
        color: #176b70;
        display: inline-flex;
        font-weight: bold;
        height: 34px;
        justify-content: center;
        min-width: 34px;
        padding: 0 8px;
        text-decoration: none;
    }

    .queue-page-link:hover,
    .queue-page-link:focus {
        background: #eef7f7;
        border-color: #6aa7aa;
        color: #104f53;
    }

    .queue-page-link.current {
        background: #176b70;
        border-color: #176b70;
        color: #fff;
    }

    .queue-page-link.disabled {
        background: #f1f4f4;
        border-color: #d7dddd;
        color: #99a3a3;
    }

    .queue-page-ellipsis {
        color: #667272;
        padding: 0 2px;
    }

    .queue-table-wrap {
        background: #fff;
        border: 1px solid #d7dddd;
        border-radius: 4px;
        box-sizing: border-box;
        overflow: hidden;
        width: 100%;
    }

    .queue-table {
        border-collapse: collapse;
        margin: 0;
        table-layout: fixed;
        width: 100%;
    }

    .queue-column-number { width: 16%; }
    .queue-column-patient { width: 25%; }
    .queue-column-service { width: 29%; }
    .queue-column-state { width: 14%; }
    .queue-column-timing { width: 16%; }

    .queue-table-has-actions .queue-column-number { width: 12%; }
    .queue-table-has-actions .queue-column-patient { width: 18%; }
    .queue-table-has-actions .queue-column-service { width: 24%; }
    .queue-table-has-actions .queue-column-state { width: 12%; }
    .queue-table-has-actions .queue-column-timing { width: 13%; }
    .queue-table-has-actions .queue-column-actions { width: 21%; }

    .queue-table th,
    .queue-table td {
        box-sizing: border-box;
        overflow-wrap: break-word;
        padding: 11px 10px;
        text-align: left;
        vertical-align: top;
    }

    .queue-table th {
        background: #f1f4f4;
        border-bottom: 2px solid #c7d0d0;
        color: #394646;
        font-size: 0.85em;
        font-weight: bold;
        letter-spacing: 0;
        white-space: nowrap;
    }

    .queue-table td {
        border-bottom: 1px solid #e1e6e6;
    }

    .queue-table tbody tr:last-child td {
        border-bottom: 0;
    }

    .queue-table tbody tr:hover td {
        background: #fafcfc;
    }

    .queue-number-cell {
        color: #253333;
        font-weight: bold;
        overflow-wrap: anywhere;
    }

    .queue-patient-cell {
        min-width: 0;
    }

    .queue-patient-name {
        color: #253333;
        font-weight: bold;
        margin-top: 2px;
    }

    .queue-row-assigned-to-me .queue-patient-cell {
        box-shadow: inset 4px 0 0 #2f8a48;
    }

    .queue-assigned-to-me {
        background: #e8f5eb;
        border: 1px solid #95cba2;
        border-radius: 3px;
        color: #237a3b;
        display: inline-block;
        font-size: 0.8em;
        font-weight: bold;
        margin-top: 5px;
        padding: 2px 6px;
    }

    .queue-service-current {
        color: #253333;
        display: block;
        font-weight: bold;
        margin-bottom: 7px;
        overflow-wrap: anywhere;
    }

    .queue-detail-list {
        display: grid;
        gap: 5px 8px;
        grid-template-columns: minmax(66px, auto) minmax(0, 1fr);
        margin: 0;
    }

    .queue-detail-list dt {
        color: #667272;
        font-size: 0.78em;
        font-weight: bold;
        margin: 0;
    }

    .queue-detail-list dd {
        margin: 0;
        min-width: 0;
        overflow-wrap: anywhere;
    }

    .queue-provider-detail {
        align-items: baseline;
        display: flex;
        gap: 6px;
    }

    .queue-provider-name {
        min-width: 0;
        overflow-wrap: anywhere;
    }

    .queue-provider-inline-form {
        flex: none;
        margin: 0;
    }

    button.queue-provider-link {
        background: none;
        border: 0;
        box-shadow: none;
        color: #007b8a;
        cursor: pointer;
        font: inherit;
        font-size: 0.85em;
        padding: 0;
        text-decoration: underline;
    }

    button.queue-provider-link:hover,
    button.queue-provider-link:focus {
        background: none;
        color: #005f69;
        text-decoration: none;
    }

    .queue-state-list {
        grid-template-columns: 1fr;
    }

    .queue-state-list dd {
        margin-bottom: 3px;
    }

    .queue-muted {
        color: #667272;
        font-size: 0.9em;
    }

    .queue-time {
        white-space: normal;
    }

    .queue-badge {
        background: #f1f4f4;
        border: 1px solid transparent;
        border-color: #cfd7d7;
        border-radius: 3px;
        color: #465252;
        display: inline-block;
        font-size: 0.82em;
        font-weight: bold;
        letter-spacing: 0;
        line-height: 1.4;
        padding: 3px 7px;
        white-space: nowrap;
    }

    .queue-priority-emergency {
        animation: queue-emergency-blink 1.1s steps(1, end) infinite;
        background: #fee4e2;
        border-color: #f9b9b4;
        color: #a11f17;
    }

    @keyframes queue-emergency-blink {
        0%, 49% {
            background: #fee4e2;
            border-color: #f9b9b4;
            box-shadow: none;
            color: #a11f17;
        }

        50%, 100% {
            background: #b42318;
            border-color: #8f1b13;
            box-shadow: 0 0 0 2px rgba(180, 35, 24, 0.18);
            color: #fff;
        }
    }

    @media (prefers-reduced-motion: reduce) {
        .queue-priority-emergency {
            animation: none;
            background: #b42318;
            border-color: #8f1b13;
            color: #fff;
        }
    }

    .queue-priority-elderly { background: #e8eafc; border-color: #c5c9f1; color: #37358c; }
    .queue-priority-pregnant { background: #fce7f3; border-color: #f5bad7; color: #9d174d; }
    .queue-priority-child { background: #e0f2fe; border-color: #a9daf5; color: #075985; }
    .queue-priority-disability { background: #fff3cd; border-color: #efd483; color: #7a4b00; }
    .queue-priority-normal { background: #f1f4f4; border-color: #cfd7d7; color: #465252; }

    .queue-status-waiting { background: #fff3cd; border-color: #efd483; color: #7a4b00; }
    .queue-status-called { background: #e6f0ff; border-color: #b7cff5; color: #174ea6; }
    .queue-status-in-progress { background: #dcf7f2; border-color: #9edfd3; color: #0b6b5e; }
    .queue-status-on-hold { background: #ffead8; border-color: #f5c69d; color: #91400f; }
    .queue-status-transferred { background: #eee9ff; border-color: #cfc3f5; color: #5b3aa4; }
    .queue-status-completed { background: #e2f5e8; border-color: #addbb9; color: #246b36; }
    .queue-status-cancelled { background: #fce8e8; border-color: #efb9b9; color: #982525; }

    .queue-patient-actions {
        align-items: center;
        display: flex;
        flex-wrap: wrap;
        gap: 5px 12px;
        margin-top: 6px;
    }

    .queue-patient-link-form {
        margin: 0;
    }

    .queue-patient-link,
    .queue-vitals-link {
        align-items: center;
        background: transparent;
        border: 0;
        color: #007b83;
        cursor: pointer;
        display: inline-flex;
        gap: 4px;
        font: inherit;
        font-weight: bold;
        padding: 0;
    }

    .queue-patient-link:hover,
    .queue-patient-link:focus,
    .queue-vitals-link:hover,
    .queue-vitals-link:focus {
        text-decoration: underline;
    }

    .queue-vitals-dialog.dialog {
        box-sizing: border-box;
        height: calc(100vh - 40px);
        max-height: 820px;
        max-width: 1200px;
        overflow: hidden;
        padding: 0;
        width: calc(100vw - 40px);
    }

    .queue-vitals-dialog.simplemodal-data {
        display: flex !important;
        flex-direction: column;
    }

    .queue-vitals-dialog .dialog-header {
        align-items: center;
        display: flex;
        flex: 0 0 auto;
        gap: 8px;
        min-height: 44px;
        padding: 3px 12px;
    }

    .queue-vitals-dialog .dialog-header h3 {
        font-size: 1.05em;
        margin: 5px 0;
    }

    .queue-vitals-patient {
        color: #e3f6f3;
        flex: 1 1 auto;
        font-size: 0.9em;
        min-width: 0;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    .queue-vitals-close {
        background: transparent;
        border: 0;
        color: #fff;
        cursor: pointer;
        flex: 0 0 36px;
        font-size: 1.15em;
        height: 36px;
        margin: 0 0 0 auto;
        padding: 0;
        width: 36px;
    }

    .queue-vitals-close:hover,
    .queue-vitals-close:focus {
        background: rgba(0, 0, 0, 0.16);
    }

    .queue-vitals-dialog .queue-vitals-content {
        box-sizing: border-box;
        flex: 1 1 auto;
        max-width: 100%;
        min-height: 0;
        min-width: 0;
        overflow: hidden;
        padding: 0;
        position: relative;
    }

    .queue-vitals-frame {
        background: #fff;
        border: 0;
        box-sizing: border-box;
        display: block;
        height: 100%;
        max-width: 100%;
        min-width: 0;
        width: 100%;
    }

    .queue-vitals-loading {
        align-items: center;
        background: #fff;
        color: #4b5757;
        display: flex;
        inset: 0;
        justify-content: center;
        position: absolute;
        z-index: 1;
    }

    .queue-vitals-loading i {
        color: #007b83;
        margin-right: 8px;
    }

    .queue-actions {
        min-width: 0;
        width: auto;
    }

    .queue-actions .queue-select-action {
        align-items: end;
        box-sizing: border-box;
        display: grid;
        gap: 4px 6px;
        grid-template-columns: minmax(0, 1fr) auto;
        margin: 0 0 9px;
        width: 100%;
    }

    .queue-actions .queue-select-action:last-child {
        margin-bottom: 0;
    }

    .queue-actions .queue-select-action label {
        color: #4b5757;
        font-size: 0.82em;
        font-weight: bold;
        grid-column: 1 / -1;
    }

    .queue-actions .queue-select-action select {
        box-sizing: border-box;
        max-width: 100%;
        min-width: 0;
        width: 100%;
    }

    .queue-actions .queue-select-action .button {
        margin: 0;
        min-width: 76px;
        white-space: nowrap;
    }

    @media (max-width: 1100px) {
        .queue-table-wrap {
            background: transparent;
            border: 0;
            overflow: visible;
        }

        .queue-table,
        .queue-table tbody,
        .queue-table tr,
        .queue-table td {
            display: block;
            width: 100%;
        }

        .queue-table thead {
            clip: rect(0 0 0 0);
            clip-path: inset(50%);
            height: 1px;
            overflow: hidden;
            position: absolute;
            white-space: nowrap;
            width: 1px;
        }

        .queue-table tbody tr {
            background: #fff;
            border: 1px solid #d7dddd;
            border-radius: 4px;
            box-sizing: border-box;
            margin-bottom: 12px;
            padding: 3px 12px;
        }

        .queue-table td,
        .queue-table tbody tr:last-child td {
            border-bottom: 1px solid #e1e6e6;
            padding: 9px 0;
        }

        .queue-table td:last-child {
            border-bottom: 0;
        }

        .queue-table td::before {
            color: #667272;
            content: attr(data-label);
            display: block;
            font-size: 0.78em;
            font-weight: bold;
            margin-bottom: 4px;
        }

        .queue-table tbody tr:hover td {
            background: transparent;
        }

        .queue-actions .queue-select-action {
            max-width: 480px;
        }
    }

    @media (max-width: 640px) {
        .queue-page-header {
            align-items: flex-start;
            flex-direction: column;
        }

        .queue-filter-field,
        .queue-toolbar select,
        .queue-toolbar input[type="search"],
        .queue-toolbar .button {
            width: 100%;
        }

        .queue-pagination {
            align-items: flex-start;
            flex-direction: column;
        }

        .queue-vitals-dialog.dialog {
            height: calc(100vh - 16px);
            width: calc(100vw - 16px);
        }
    }
</style>

<% if (!authorized) { %>
    <div class="note-container"><div class="note warning">You do not have permission to view queue entries.</div></div>
<% } else { %>
    <%
        def queueReturnUrl = ui.pageLink("rwandaemr", "queue/queueDashboard", [
                locationId: selectedLocation?.id,
                status: selectedStatus,
                arrivalDay: selectedArrivalDay,
                patientName: patientName,
                assignment: selectedAssignment,
                page: currentPage,
                pageSize: pageSize
        ])
    %>
    <div class="queue-page-header">
        <h3>Queue Dashboard</h3>
        <span id="queue-patient-count" class="queue-count">${ totalEntries } ${ totalEntries == 1 ? "patient" : "patients" }</span>
    </div>
    <form id="queue-dashboard-filters" class="queue-toolbar" method="get" action="${ ui.pageLink("rwandaemr", "queue/queueDashboard") }">
        <% if (canViewAllLocations) { %>
            <div class="queue-filter-field">
                <label for="queue-location-filter">Login location</label>
                <select id="queue-location-filter" name="locationId">
                    <% locations.each { loc -> %>
                        <option value="${ loc.id }" ${ selectedLocation?.id == loc.id ? "selected=\"selected\"" : "" }>
                            ${ ui.encodeHtmlContent(loc.name) }
                        </option>
                    <% } %>
                </select>
            </div>
        <% } %>
        <div class="queue-filter-field">
            <label for="queue-patient-name-filter">Patient name</label>
            <input id="queue-patient-name-filter" type="search" name="patientName"
                   value="${ ui.escapeAttribute(patientName) }" />
        </div>
        <div class="queue-filter-field">
            <label for="queue-assignment-filter">Provider assignment</label>
            <select id="queue-assignment-filter" name="assignment">
                <option value="ALL" ${ selectedAssignment == "ALL" ? "selected=\"selected\"" : "" }>All assignments</option>
                <option value="ASSIGNED_TO_ME" ${ selectedAssignment == "ASSIGNED_TO_ME" ? "selected=\"selected\"" : "" }>Assigned to me</option>
                <option value="NOT_ASSIGNED_TO_ME" ${ selectedAssignment == "NOT_ASSIGNED_TO_ME" ? "selected=\"selected\"" : "" }>Not assigned to me</option>
            </select>
        </div>
        <div class="queue-filter-field">
            <label for="queue-status-filter">Status</label>
            <select id="queue-status-filter" name="status">
                <option value="ALL" ${ selectedStatus == "ALL" ? "selected=\"selected\"" : "" }>All</option>
                <% statuses.each { s -> %>
                    <option value="${ s.name() }" ${ selectedStatus == s.name() ? "selected=\"selected\"" : "" }>${ s.name().toLowerCase().replace('_', ' ').capitalize() }</option>
                <% } %>
            </select>
        </div>
        <div class="queue-filter-field">
            <label for="queue-arrival-day-filter">Arrival date</label>
            <select id="queue-arrival-day-filter" name="arrivalDay">
                <option value="TODAY" ${ selectedArrivalDay == "TODAY" ? "selected=\"selected\"" : "" }>Today</option>
                <option value="YESTERDAY" ${ selectedArrivalDay == "YESTERDAY" ? "selected=\"selected\"" : "" }>Yesterday</option>
            </select>
        </div>
        <div class="queue-filter-field">
            <label for="queue-page-size">Patients per page</label>
            <select id="queue-page-size" name="pageSize">
                <% pageSizeOptions.each { option -> %>
                    <option value="${ option }" ${ pageSize == option ? "selected=\"selected\"" : "" }>${ option }</option>
                <% } %>
            </select>
        </div>
        <button type="submit" class="button"><i class="icon-filter"></i> Filter</button>
    </form>

    <div id="queue-live-region" data-refresh-url="${ ui.escapeAttribute(queueReturnUrl) }">
        <% if (entries.isEmpty()) { %>
            <div class="note-container"><div class="note">No queue entries found.</div></div>
        <% } else { %>
            <div class="queue-table-wrap">
                <table class="queue-table${ (canTransferPatient || canManageQueue || canCallPatient) ? ' queue-table-has-actions' : '' }">
                <thead>
                    <tr>
                        <th class="queue-column-number">Queue #</th>
                        <th class="queue-column-patient">Patient</th>
                        <th class="queue-column-service">Service details</th>
                        <th class="queue-column-state">Queue state</th>
                        <th class="queue-column-timing">Timing</th>
                        <% if (canTransferPatient || canManageQueue || canCallPatient) { %>
                            <th class="queue-column-actions">Actions</th>
                        <% } %>
                    </tr>
                </thead>
                <tbody>
                    <% entries.each { entry ->
                        def patient = entry.patient
                        def patientIdentifier = patient?.patientIdentifier?.identifier ?: ""
                        def phoneNumber = phoneNumberByPatientId[patient?.id]
                        def assignedToCurrentProvider = entry.assignedProvider?.id != null &&
                                currentProviderIds.contains(entry.assignedProvider.id)
                        def priorityName = entry.priority?.name() ?: ""
                        def priorityClass = priorityName.toLowerCase().replace('_', '-')
                        def priorityLabel = entry.priority?.displayName ?: ""
                        def statusName = entry.status?.name() ?: ""
                        def statusClass = statusName.toLowerCase().replace('_', '-')
                        def statusLabel = statusName.toLowerCase().replace('_', ' ').capitalize()
                        def activeStatus = ["WAITING", "CALLED", "IN_PROGRESS", "ON_HOLD"].contains(statusName)
                        def concurrentServicePoints = concurrentServicePointsByEntryId[entry.id] ?: []
                        def destinationServicePoints = transferServicePointsByEntryId[entry.id] ?: []
                        def concurrentServicePointNames = concurrentServicePoints.collect { it.name }.findAll { it }.join(", ")
                        def vitalsUrl = patient?.uuid && entry.visit?.uuid ? ui.pageLink(
                                "htmlformentryui",
                                "htmlform/enterHtmlFormWithSimpleUi",
                                [
                                        patientId: patient.uuid,
                                        visitId: entry.visit.uuid,
                                        definitionUiResource: "file:configuration/htmlforms/auto-vital-signs.xml",
                                        returnUrl: queueReturnUrl
                                ]) : null
                    %>
                        <tr class="${ assignedToCurrentProvider ? 'queue-row-assigned-to-me' : '' }">
                            <td class="queue-number-cell" data-label="Queue #">${ ui.encodeHtmlContent(entry.queueNumber) }</td>
                            <td class="queue-patient-cell" data-label="Patient">
                                ${ ui.encodeHtmlContent(patientIdentifier) }
                                <div class="queue-patient-name">${ ui.encodeHtmlContent(patient?.personName?.fullName ?: "") }</div>
                                <div class="queue-muted">${ ui.encodeHtmlContent(patient?.gender ?: "") } / ${ patient?.age ?: "" }</div>
                                <% if (phoneNumber) { %>
                                    <div class="queue-muted"><i class="icon-phone" aria-hidden="true"></i> Phone: ${ ui.encodeHtmlContent(phoneNumber) }</div>
                                <% } %>
                                <% if (assignedToCurrentProvider) { %>
                                    <span class="queue-assigned-to-me">Assigned to me</span>
                                <% } %>
                                <% if ((!laboratoryLocation && patient?.id) || (laboratoryLocation && patient?.id && patientIdentifier) || vitalsUrl) { %>
                                    <div class="queue-patient-actions">
                                        <% if (laboratoryLocation && patient?.id && patientIdentifier) { %>
                                            <form class="queue-patient-link-form" method="post" action="${ ui.pageLink("rwandaemr", "queue/queueDashboard") }">
                                                <input type="hidden" name="action" value="openLabOrders" />
                                                <input type="hidden" name="entryId" value="${ entry.id }" />
                                                <a class="queue-patient-link queue-lab-orders-link"
                                                   href="${ ui.pageLink("pihapps", "labs/labOrderList") }"
                                                   data-patient-identifier="${ ui.escapeAttribute(patientIdentifier) }">
                                                    <i class="icon-beaker"></i> Lab orders
                                                </a>
                                            </form>
                                        <% } else if (!laboratoryLocation && patient?.id) { %>
                                            <form class="queue-patient-link-form" method="post" action="${ ui.pageLink("rwandaemr", "queue/queueDashboard") }">
                                                <input type="hidden" name="action" value="openDashboard" />
                                                <input type="hidden" name="entryId" value="${ entry.id }" />
                                                <input type="hidden" name="locationId" value="${ selectedLocation?.id ?: "" }" />
                                                <input type="hidden" name="status" value="${ ui.encodeHtmlContent(selectedStatus) }" />
                                                <input type="hidden" name="arrivalDay" value="${ ui.encodeHtmlContent(selectedArrivalDay) }" />
                                                <input type="hidden" name="patientName" value="${ ui.escapeAttribute(patientName) }" />
                                                <input type="hidden" name="assignment" value="${ ui.escapeAttribute(selectedAssignment) }" />
                                                <button type="submit" class="queue-patient-link"><i class="icon-user"></i> Dashboard</button>
                                            </form>
                                        <% } %>
                                        <% if (vitalsUrl) { %>
                                            <a class="queue-vitals-link"
                                               href="${ ui.escapeAttribute(vitalsUrl) }"
                                               data-patient-name="${ ui.escapeAttribute(patient?.personName?.fullName ?: "Patient") }"
                                               data-entry-id="${ entry.id }"
                                               data-return-url="${ ui.escapeAttribute(queueReturnUrl) }">
                                                <i class="icon-heart"></i> Vitals
                                            </a>
                                        <% } %>
                                    </div>
                                <% } %>
                            </td>
                            <td data-label="Service details">
                                <strong class="queue-service-current">${ ui.encodeHtmlContent(entry.servicePoint?.name ?: "-") }</strong>
                                <dl class="queue-detail-list">
                                    <dt>Requested</dt>
                                    <dd>${ ui.encodeHtmlContent(entry.serviceRequestedConcept?.name?.name ?: entry.serviceRequestedConcept?.uuid ?: "-") }</dd>
                                    <dt>Provider</dt>
                                    <dd class="queue-provider-detail">
                                        <span class="queue-provider-name">${ ui.encodeHtmlContent(entry.assignedProvider?.name ?: entry.assignedProvider?.identifier ?: "Unassigned") }</span>
                                        <% if (canManageQueue) { %>
                                            <form class="queue-provider-inline-form queue-provider-form" method="post"
                                                  action="${ ui.pageLink("rwandaemr", "queue/queueDashboard") }"
                                                  data-patient-name="${ ui.escapeAttribute(patient?.personName?.fullName ?: "Patient") }"
                                                  data-provider-id="${ entry.assignedProvider?.id ?: "" }"
                                                  data-provider-name="${ ui.escapeAttribute(entry.assignedProvider?.name ?: entry.assignedProvider?.identifier ?: "") }">
                                                <input type="hidden" name="action" value="updateProvider" />
                                                <input type="hidden" name="entryId" value="${ entry.id }" />
                                                <input type="hidden" name="assignedProviderId" value="" />
                                                <input type="hidden" name="locationId" value="${ selectedLocation?.id ?: "" }" />
                                                <input type="hidden" name="status" value="${ ui.encodeHtmlContent(selectedStatus) }" />
                                                <input type="hidden" name="arrivalDay" value="${ ui.encodeHtmlContent(selectedArrivalDay) }" />
                                                <input type="hidden" name="patientName" value="${ ui.escapeAttribute(patientName) }" />
                                                <input type="hidden" name="assignment" value="${ ui.escapeAttribute(selectedAssignment) }" />
                                                <input type="hidden" name="page" value="${ currentPage }" />
                                                <input type="hidden" name="pageSize" value="${ pageSize }" />
                                                <button type="submit" class="queue-provider-link">
                                                    <i class="${ entry.assignedProvider ? 'icon-pencil' : 'icon-user' }"></i>
                                                    ${ entry.assignedProvider ? "Edit provider" : "Assign provider" }
                                                </button>
                                            </form>
                                        <% } %>
                                    </dd>
                                    <dt>Previous</dt>
                                    <dd>${ ui.encodeHtmlContent(entry.previousServicePoint?.name ?: "-") }</dd>
                                    <dt>Transfer reason</dt>
                                    <dd>${ ui.encodeHtmlContent(entry.transferReason ?: "-") }</dd>
                                    <% if (concurrentServicePointNames) { %>
                                        <dt>Also queued at</dt>
                                        <dd>${ ui.encodeHtmlContent(concurrentServicePointNames) }</dd>
                                    <% } %>
                                </dl>
                            </td>
                            <td data-label="Queue state">
                                <dl class="queue-detail-list queue-state-list">
                                    <dt>Priority</dt>
                                    <dd><span class="queue-badge queue-priority-${ priorityClass }">${ ui.encodeHtmlContent(priorityLabel) }</span></dd>
                                    <dt>Status</dt>
                                    <dd><span class="queue-badge queue-status-${ statusClass }">${ ui.encodeHtmlContent(statusLabel) }</span></dd>
                                </dl>
                            </td>
                            <td class="queue-time" data-label="Timing">
                                <dl class="queue-detail-list">
                                    <dt>Waiting</dt>
                                    <dd>${ ui.encodeHtmlContent(waitingTimeByEntryId[entry.id] ?: "-") }</dd>
                                    <dt>Arrival</dt>
                                    <dd>${ entry.arrivalTime ? entry.arrivalTime.format("yyyy-MM-dd HH:mm") : "-" }</dd>
                                </dl>
                            </td>
                            <% if (canTransferPatient || canManageQueue || canCallPatient) { %>
                                <td class="queue-actions" data-label="Actions">
                                    <% if (activeStatus && (canCallPatient || canManageQueue)) { %>
                                        <form class="queue-select-action queue-status-action" method="post"
                                              action="${ ui.pageLink("rwandaemr", "queue/queueDashboard") }"
                                              data-patient-name="${ ui.escapeAttribute(patient?.personName?.fullName ?: "Patient") }">
                                            <input type="hidden" name="entryId" value="${ entry.id }" />
                                            <input type="hidden" name="locationId" value="${ selectedLocation?.id ?: "" }" />
                                            <input type="hidden" name="status" value="${ ui.encodeHtmlContent(selectedStatus) }" />
                                            <input type="hidden" name="arrivalDay" value="${ ui.encodeHtmlContent(selectedArrivalDay) }" />
                                            <input type="hidden" name="patientName" value="${ ui.escapeAttribute(patientName) }" />
                                            <input type="hidden" name="assignment" value="${ ui.escapeAttribute(selectedAssignment) }" />
                                            <input type="hidden" name="page" value="${ currentPage }" />
                                            <input type="hidden" name="pageSize" value="${ pageSize }" />
                                            <input type="hidden" name="reason" value="Cancelled from queue dashboard" />
                                            <label for="queue-status-action-${ entry.id }">Status</label>
                                            <select id="queue-status-action-${ entry.id }" name="action" required="required">
                                                <option value="" selected="selected" disabled="disabled">Choose status</option>
                                                <% if (canCallPatient) { %>
                                                    <option value="complete">Completed</option>
                                                <% } %>
                                                <% if (canManageQueue) { %>
                                                    <option value="cancel">Cancelled</option>
                                                <% } %>
                                            </select>
                                            <button type="submit" class="button"><i class="icon-save"></i> Update</button>
                                        </form>
                                    <% } %>
                                    <% if (canManageQueue) { %>
                                        <form class="queue-select-action" method="post"
                                              action="${ ui.pageLink("rwandaemr", "queue/queueDashboard") }">
                                            <input type="hidden" name="action" value="updatePriority" />
                                            <input type="hidden" name="entryId" value="${ entry.id }" />
                                            <input type="hidden" name="locationId" value="${ selectedLocation?.id ?: "" }" />
                                            <input type="hidden" name="status" value="${ ui.encodeHtmlContent(selectedStatus) }" />
                                            <input type="hidden" name="arrivalDay" value="${ ui.encodeHtmlContent(selectedArrivalDay) }" />
                                            <input type="hidden" name="patientName" value="${ ui.escapeAttribute(patientName) }" />
                                            <input type="hidden" name="assignment" value="${ ui.escapeAttribute(selectedAssignment) }" />
                                            <input type="hidden" name="page" value="${ currentPage }" />
                                            <input type="hidden" name="pageSize" value="${ pageSize }" />
                                            <label for="priority-${ entry.id }">Priority</label>
                                            <select id="priority-${ entry.id }" name="priority">
                                                <% priorities.each { priority -> %>
                                                    <option value="${ priority.name() }" ${ entry.priority == priority ? "selected=\"selected\"" : "" }>${ ui.encodeHtmlContent(priority.displayName) }</option>
                                                <% } %>
                                            </select>
                                            <button type="submit" class="button"><i class="icon-save"></i> Update</button>
                                        </form>
                                    <% } %>
                                    <% if (canTransferPatient && !destinationServicePoints.isEmpty()) { %>
                                        <form class="queue-select-action queue-transfer-form" method="post"
                                              action="${ ui.pageLink("rwandaemr", "queue/queueDashboard") }"
                                              data-patient-name="${ ui.escapeAttribute(patient?.personName?.fullName ?: "Patient") }"
                                              data-current-service-point="${ ui.escapeAttribute(entry.servicePoint?.name ?: "") }">
                                            <input type="hidden" name="action" value="transfer" />
                                            <input type="hidden" name="entryId" value="${ entry.id }" />
                                            <input type="hidden" name="locationId" value="${ selectedLocation?.id ?: "" }" />
                                            <input type="hidden" name="status" value="${ ui.encodeHtmlContent(selectedStatus) }" />
                                            <input type="hidden" name="arrivalDay" value="${ ui.encodeHtmlContent(selectedArrivalDay) }" />
                                            <input type="hidden" name="patientName" value="${ ui.escapeAttribute(patientName) }" />
                                            <input type="hidden" name="assignment" value="${ ui.escapeAttribute(selectedAssignment) }" />
                                            <input type="hidden" name="page" value="${ currentPage }" />
                                            <input type="hidden" name="pageSize" value="${ pageSize }" />
                                            <label for="destination-${ entry.id }">Send to</label>
                                            <select id="destination-${ entry.id }" name="destinationServicePointId">
                                                <% destinationServicePoints.each { sp -> %>
                                                    <option value="${ sp.id }">${ ui.encodeHtmlContent(sp.name + (entry.previousServicePoint?.id == sp.id ? " (previous)" : "")) }</option>
                                                <% } %>
                                            </select>
                                            <input type="hidden" name="reason" value="" />
                                            <input type="hidden" name="assignedProviderId" value="" />
                                            <button type="submit" class="button"><i class="icon-share-alt"></i> Send</button>
                                        </form>
                                    <% } %>
                                </td>
                            <% } %>
                        </tr>
                    <% } %>
                </tbody>
                </table>
            </div>
        <% } %>

        <% if (totalEntries > 0) { %>
            <div class="queue-pagination">
                <span class="queue-page-summary">Showing ${ pageStart }-${ pageEnd } of ${ totalEntries } patients</span>
                <% if (totalPages > 1) { %>
                    <nav class="queue-page-links" aria-label="Queue pages">
                        <% if (currentPage > 1) { %>
                            <a class="queue-page-link" aria-label="Previous page" title="Previous page"
                               href="${ ui.pageLink("rwandaemr", "queue/queueDashboard", [locationId: selectedLocation?.id, status: selectedStatus, arrivalDay: selectedArrivalDay, patientName: patientName, assignment: selectedAssignment, page: currentPage - 1, pageSize: pageSize]) }">
                                <i class="icon-chevron-left" aria-hidden="true"></i>
                            </a>
                        <% } else { %>
                            <span class="queue-page-link disabled" aria-disabled="true"><i class="icon-chevron-left" aria-hidden="true"></i></span>
                        <% } %>

                        <% if (pageNumbers[0] > 1) { %>
                            <a class="queue-page-link" href="${ ui.pageLink("rwandaemr", "queue/queueDashboard", [locationId: selectedLocation?.id, status: selectedStatus, arrivalDay: selectedArrivalDay, patientName: patientName, assignment: selectedAssignment, page: 1, pageSize: pageSize]) }">1</a>
                            <% if (pageNumbers[0] > 2) { %><span class="queue-page-ellipsis">...</span><% } %>
                        <% } %>
                        <% pageNumbers.each { pageNumber -> %>
                            <% if (pageNumber == currentPage) { %>
                                <span class="queue-page-link current" aria-current="page">${ pageNumber }</span>
                            <% } else { %>
                                <a class="queue-page-link" href="${ ui.pageLink("rwandaemr", "queue/queueDashboard", [locationId: selectedLocation?.id, status: selectedStatus, arrivalDay: selectedArrivalDay, patientName: patientName, assignment: selectedAssignment, page: pageNumber, pageSize: pageSize]) }">${ pageNumber }</a>
                            <% } %>
                        <% } %>
                        <% if (pageNumbers[pageNumbers.size() - 1] < totalPages) { %>
                            <% if (pageNumbers[pageNumbers.size() - 1] < totalPages - 1) { %><span class="queue-page-ellipsis">...</span><% } %>
                            <a class="queue-page-link" href="${ ui.pageLink("rwandaemr", "queue/queueDashboard", [locationId: selectedLocation?.id, status: selectedStatus, arrivalDay: selectedArrivalDay, patientName: patientName, assignment: selectedAssignment, page: totalPages, pageSize: pageSize]) }">${ totalPages }</a>
                        <% } %>

                        <% if (currentPage < totalPages) { %>
                            <a class="queue-page-link" aria-label="Next page" title="Next page"
                               href="${ ui.pageLink("rwandaemr", "queue/queueDashboard", [locationId: selectedLocation?.id, status: selectedStatus, arrivalDay: selectedArrivalDay, patientName: patientName, assignment: selectedAssignment, page: currentPage + 1, pageSize: pageSize]) }">
                                <i class="icon-chevron-right" aria-hidden="true"></i>
                            </a>
                        <% } else { %>
                            <span class="queue-page-link disabled" aria-disabled="true"><i class="icon-chevron-right" aria-hidden="true"></i></span>
                        <% } %>
                    </nav>
                <% } %>
            </div>
        <% } %>

        <% latestVitalsByEntryId.each { entryId, summary -> %>
            <div id="queue-vitals-summary-${ entryId }" style="display: none">
                <section class="queue-latest-vitals" aria-label="Most recent vitals for this visit">
                    <div class="queue-latest-vitals-header">
                        <span class="queue-latest-vitals-title"><i class="icon-heart"></i> Most recent vitals</span>
                        <% if (summary.recordedAt) { %>
                            <time class="queue-latest-vitals-time" datetime="${ summary.recordedAt.format("yyyy-MM-dd'T'HH:mm:ss") }">
                                <i class="icon-time"></i> ${ summary.recordedAt.format("dd MMM yyyy, HH:mm") }
                            </time>
                        <% } %>
                    </div>
                    <div class="queue-latest-vitals-values">
                        <% summary.values.each { vital -> %>
                            <div class="queue-latest-vital">
                                <span class="queue-latest-vital-label">${ ui.encodeHtmlContent(vital.label) }</span>
                                <strong class="queue-latest-vital-value">
                                    ${ ui.encodeHtmlContent(vital.value) }<% if (vital.unit) { %><span class="queue-latest-vital-unit">${ ui.encodeHtmlContent(vital.unit) }</span><% } %>
                                </strong>
                            </div>
                        <% } %>
                    </div>
                </section>
            </div>
        <% } %>
    </div>

    <div id="queue-vitals-dialog" class="dialog queue-vitals-dialog" role="dialog"
         aria-modal="true" aria-labelledby="queue-vitals-title" style="display: none">
        <div class="dialog-header">
            <i class="icon-heart"></i>
            <h3 id="queue-vitals-title">Collect vitals</h3>
            <span id="queue-vitals-patient" class="queue-vitals-patient"></span>
            <button id="queue-vitals-close" type="button" class="queue-vitals-close cancel"
                    aria-label="Close vitals form" title="Close">
                <i class="icon-remove"></i>
            </button>
        </div>
        <div class="dialog-content queue-vitals-content">
            <div id="queue-vitals-loading" class="queue-vitals-loading" aria-live="polite">
                <i class="icon-spinner icon-spin"></i> Loading vitals form
            </div>
            <iframe id="queue-vitals-frame" class="queue-vitals-frame" src="about:blank" title="Collect vitals"></iframe>
        </div>
    </div>
    <form id="queue-vitals-completed-form" method="post"
          action="${ ui.pageLink("rwandaemr", "queue/queueDashboard") }" style="display: none">
        <input type="hidden" name="action" value="vitalsSaved" />
        <input type="hidden" name="entryId" value="" />
        <input type="hidden" name="encounterId" value="" />
        <input type="hidden" name="locationId" value="${ selectedLocation?.id ?: "" }" />
        <input type="hidden" name="status" value="${ ui.encodeHtmlContent(selectedStatus) }" />
        <input type="hidden" name="arrivalDay" value="${ ui.encodeHtmlContent(selectedArrivalDay) }" />
        <input type="hidden" name="patientName" value="${ ui.escapeAttribute(patientName) }" />
        <input type="hidden" name="assignment" value="${ ui.escapeAttribute(selectedAssignment) }" />
        <input type="hidden" name="page" value="${ currentPage }" />
        <input type="hidden" name="pageSize" value="${ pageSize }" />
    </form>
    <%= ui.includeFragment("rwandaemr", "queue/transferReasonDialog") %>
    <%= ui.includeFragment("rwandaemr", "queue/providerAssignmentDialog") %>
<% } %>
