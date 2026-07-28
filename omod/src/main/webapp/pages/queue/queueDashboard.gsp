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

        jq("#queue-location-filter").change(function() {
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
            var selectedLocationId = pendingSessionLocationId
                    || jq("#session-location ul.select li.selected").first().attr("locationId");
            if (!selectedLocationId && typeof sessionLocationModel !== "undefined" && sessionLocationModel.id) {
                selectedLocationId = sessionLocationModel.id();
            }

            var query = "?status=" + encodeURIComponent(status);
            if (selectedLocationId) {
                query += "&locationId=" + encodeURIComponent(selectedLocationId);
            }
            locationRefreshStarted = true;
            window.location.replace("${ ui.pageLink("rwandaemr", "queue/queueDashboard") }" + query);
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

            jq(".queue-vitals-link").click(function(event) {
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

    .queue-toolbar select {
        box-sizing: border-box;
        max-width: 100%;
        min-width: 190px;
    }

    .queue-toolbar .button {
        margin: 0;
    }

    .queue-table-wrap {
        background: #fff;
        border: 1px solid #d7dddd;
        border-radius: 4px;
        box-sizing: border-box;
        overflow-x: auto;
        width: 100%;
    }

    .queue-table {
        border-collapse: collapse;
        margin: 0;
        min-width: 1160px;
        table-layout: auto;
        width: 100%;
    }

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
        min-width: 125px;
        white-space: nowrap;
    }

    .queue-patient-cell {
        min-width: 185px;
    }

    .queue-patient-name {
        color: #253333;
        font-weight: bold;
        margin-top: 2px;
    }

    .queue-service-cell {
        min-width: 135px;
    }

    .queue-muted {
        color: #667272;
        font-size: 0.9em;
    }

    .queue-time {
        min-width: 135px;
        white-space: nowrap;
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
        min-width: 250px;
        width: 250px;
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
        min-width: 82px;
        white-space: nowrap;
    }

    @media (max-width: 640px) {
        .queue-page-header {
            align-items: flex-start;
            flex-direction: column;
        }

        .queue-filter-field,
        .queue-toolbar select,
        .queue-toolbar .button {
            width: 100%;
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
                status: selectedStatus
        ])
    %>
    <div class="queue-page-header">
        <h3>Queue Dashboard</h3>
        <span class="queue-count">${ entries.size() } ${ entries.size() == 1 ? "patient" : "patients" }</span>
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
            <label for="queue-status-filter">Status</label>
            <select id="queue-status-filter" name="status">
                <option value="ALL" ${ selectedStatus == "ALL" ? "selected=\"selected\"" : "" }>All</option>
                <% statuses.each { s -> %>
                    <option value="${ s.name() }" ${ selectedStatus == s.name() ? "selected=\"selected\"" : "" }>${ s.name().toLowerCase().replace('_', ' ').capitalize() }</option>
                <% } %>
            </select>
        </div>
        <button type="submit" class="button"><i class="icon-filter"></i> Filter</button>
    </form>

    <% if (entries.isEmpty()) { %>
        <div class="note-container"><div class="note">No queue entries found.</div></div>
    <% } else { %>
        <div class="queue-table-wrap">
            <table class="queue-table">
                <thead>
                    <tr>
                        <th>Queue #</th>
                        <th>Patient</th>
                        <th>Service point</th>
                        <th>Previous service point</th>
                        <th>Priority</th>
                        <th>Status</th>
                        <th>Waiting time</th>
                        <th>Arrival</th>
                        <% if (canTransferPatient || canManageQueue) { %>
                            <th>Actions</th>
                        <% } %>
                    </tr>
                </thead>
                <tbody>
                    <% entries.each { entry ->
                        def patient = entry.patient
                        def destinationServicePoints = servicePoints.findAll { sp -> entry.servicePoint?.id != sp.id }
                        def priorityName = entry.priority?.name() ?: ""
                        def priorityClass = priorityName.toLowerCase().replace('_', '-')
                        def priorityLabel = priorityName.toLowerCase().replace('_', ' ').capitalize()
                        def statusName = entry.status?.name() ?: ""
                        def statusClass = statusName.toLowerCase().replace('_', '-')
                        def statusLabel = statusName.toLowerCase().replace('_', ' ').capitalize()
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
                        <tr>
                            <td class="queue-number-cell">${ ui.encodeHtmlContent(entry.queueNumber) }</td>
                            <td class="queue-patient-cell">
                                ${ ui.encodeHtmlContent(patient?.patientIdentifier?.identifier ?: "") }
                                <div class="queue-patient-name">${ ui.encodeHtmlContent(patient?.personName?.fullName ?: "") }</div>
                                <div class="queue-muted">${ ui.encodeHtmlContent(patient?.gender ?: "") } / ${ patient?.age ?: "" }</div>
                                <% if (patient?.id || vitalsUrl) { %>
                                    <div class="queue-patient-actions">
                                        <% if (patient?.id) { %>
                                            <form class="queue-patient-link-form" method="post" action="${ ui.pageLink("rwandaemr", "queue/queueDashboard") }">
                                                <input type="hidden" name="action" value="openDashboard" />
                                                <input type="hidden" name="entryId" value="${ entry.id }" />
                                                <input type="hidden" name="locationId" value="${ selectedLocation?.id ?: "" }" />
                                                <input type="hidden" name="status" value="${ ui.encodeHtmlContent(selectedStatus) }" />
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
                            <td class="queue-service-cell">${ ui.encodeHtmlContent(entry.servicePoint?.name ?: "") }</td>
                            <td class="queue-service-cell">${ ui.encodeHtmlContent(entry.previousServicePoint?.name ?: "") }</td>
                            <td><span class="queue-badge queue-priority-${ priorityClass }">${ ui.encodeHtmlContent(priorityLabel) }</span></td>
                            <td><span class="queue-badge queue-status-${ statusClass }">${ ui.encodeHtmlContent(statusLabel) }</span></td>
                            <td class="queue-time">${ ui.encodeHtmlContent(waitingTimeByEntryId[entry.id] ?: "-") }</td>
                            <td class="queue-time">${ entry.arrivalTime ? entry.arrivalTime.format("yyyy-MM-dd HH:mm") : "" }</td>
                            <% if (canTransferPatient || canManageQueue) { %>
                                <td class="queue-actions">
                                    <% if (canManageQueue) { %>
                                        <form class="queue-select-action" method="post" action="${ ui.pageLink("rwandaemr", "queue/queueDashboard") }">
                                            <input type="hidden" name="action" value="updatePriority" />
                                            <input type="hidden" name="entryId" value="${ entry.id }" />
                                            <input type="hidden" name="locationId" value="${ selectedLocation?.id ?: "" }" />
                                            <input type="hidden" name="status" value="${ ui.encodeHtmlContent(selectedStatus) }" />
                                            <label for="priority-${ entry.id }">Priority</label>
                                            <select id="priority-${ entry.id }" name="priority">
                                                <% priorities.each { priority -> %>
                                                    <option value="${ priority.name() }" ${ entry.priority == priority ? "selected=\"selected\"" : "" }>${ priority.name().toLowerCase().replace('_', ' ').capitalize() }</option>
                                                <% } %>
                                            </select>
                                            <button type="submit" class="button"><i class="icon-save"></i> Update</button>
                                        </form>
                                    <% } %>
                                    <% if (canTransferPatient && !destinationServicePoints.isEmpty()) { %>
                                        <form class="queue-select-action" method="post" action="${ ui.pageLink("rwandaemr", "queue/queueDashboard") }">
                                            <input type="hidden" name="action" value="transfer" />
                                            <input type="hidden" name="entryId" value="${ entry.id }" />
                                            <input type="hidden" name="locationId" value="${ selectedLocation?.id ?: "" }" />
                                            <input type="hidden" name="status" value="${ ui.encodeHtmlContent(selectedStatus) }" />
                                            <label for="destination-${ entry.id }">Send to</label>
                                            <select id="destination-${ entry.id }" name="destinationServicePointId">
                                                <% destinationServicePoints.each { sp -> %>
                                                    <option value="${ sp.id }">${ ui.encodeHtmlContent(sp.name) }</option>
                                                <% } %>
                                            </select>
                                            <input type="hidden" name="reason" value="Transferred from queue dashboard" />
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
    </form>
<% } %>
