<%
    ui.decorateWith("appui", "standardEmrPage")
%>

<script type="text/javascript">
    var breadcrumbs = [
        { icon: "icon-home", link: '/' + OPENMRS_CONTEXT_PATH + '/index.htm' },
        { label: "Queue Reports", link: "${ ui.pageLink("rwandaemr", "queue/queueReports") }" }
    ];
</script>

<style type="text/css">
    .queue-report-badge {
        background: #f1f4f4;
        border: 1px solid #cfd7d7;
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

    .queue-priority-emergency,
    .queue-status-emergency {
        animation: queue-report-emergency-blink 1.1s steps(1, end) infinite;
        background: #fee4e2;
        border-color: #f9b9b4;
        color: #a11f17;
    }

    @keyframes queue-report-emergency-blink {
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
        .queue-priority-emergency,
        .queue-status-emergency {
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

    .queue-report-wait-time { white-space: nowrap; }

    .queue-report-patient-toggle {
        align-items: center;
        background: transparent;
        border: 0;
        box-shadow: none;
        color: #007fff;
        cursor: pointer;
        display: inline-flex;
        font: inherit;
        gap: 7px;
        margin: 0;
        min-height: 28px;
        padding: 0;
        text-align: left;
    }

    .queue-report-patient-toggle:hover,
    .queue-report-patient-toggle:focus {
        background: transparent;
        color: #005eb8;
    }

    .queue-report-patient-toggle:focus {
        outline: 2px solid #007fff;
        outline-offset: 2px;
    }

    .queue-report-patient-toggle i {
        flex: 0 0 auto;
        transition: transform 150ms ease;
    }

    .queue-report-patient-toggle[aria-expanded="true"] i {
        transform: rotate(90deg);
    }

    .queue-report-patient-name {
        font-weight: bold;
    }

    .queue-report-detail > td {
        background: #f7f9f9;
        border-top: 0;
        padding: 12px 16px 14px 42px;
    }

    .queue-report-journey {
        align-items: flex-start;
        display: flex;
        flex-wrap: wrap;
        gap: 12px;
    }

    .queue-report-journey-title {
        color: #3f4d4d;
        flex: 0 0 auto;
        font-size: 0.85em;
        line-height: 26px;
    }

    .queue-report-service-points {
        display: flex;
        flex: 1 1 320px;
        flex-wrap: wrap;
        gap: 8px;
        list-style: none;
        margin: 0;
        padding: 0;
    }

    .queue-report-service-points li {
        align-items: center;
        background: #fff;
        border: 1px solid #cfd7d7;
        border-radius: 3px;
        color: #334141;
        display: inline-flex;
        gap: 7px;
        line-height: 1.35;
        min-height: 26px;
        padding: 3px 9px 3px 4px;
    }

    .queue-report-service-point-order {
        align-items: center;
        background: #e6f0ff;
        border-radius: 50%;
        color: #174ea6;
        display: inline-flex;
        flex: 0 0 22px;
        font-size: 0.78em;
        font-weight: bold;
        height: 22px;
        justify-content: center;
        width: 22px;
    }

    .queue-report-filters {
        align-items: flex-end;
        background: #f7f9f9;
        border-bottom: 1px solid #d5dddd;
        border-top: 1px solid #d5dddd;
        display: flex;
        flex-wrap: wrap;
        gap: 12px;
        margin-bottom: 18px;
        padding: 12px 10px;
    }

    .queue-report-filter-field label {
        color: #3f4d4d;
        display: block;
        font-size: 0.82em;
        font-weight: bold;
        margin-bottom: 4px;
    }

    .queue-report-filter-field {
        flex: 1 1 155px;
        max-width: 240px;
        min-width: 0;
    }

    .queue-report-filter-field input,
    .queue-report-filter-field select {
        box-sizing: border-box;
        max-width: 100%;
        min-height: 36px;
        min-width: 155px;
        width: 100%;
    }

    .queue-report-filter-actions {
        display: flex;
        flex: 0 0 auto;
        flex-wrap: wrap;
        gap: 8px;
    }

    .queue-report-export {
        background: #217346;
        border-color: #185c37;
        color: #fff;
    }

    .queue-report-export:hover {
        background: #185c37;
        color: #fff;
    }
</style>

<% if (!authorized) { %>
    <div class="note-container"><div class="note warning">You do not have permission to view queue reports.</div></div>
<% } else { %>
    <h3>Queue Reports</h3>
    <form class="queue-report-filters" method="get" action="${ ui.pageLink("rwandaemr", "queue/queueReports") }">
        <% if (canViewAllLocations) { %>
            <div class="queue-report-filter-field">
                <label for="queue-report-location">Location</label>
                <select id="queue-report-location" name="locationId">
                    <option value="ALL" ${ selectedLocation == null ? "selected=\"selected\"" : "" }>All locations</option>
                    <% locations.each { loc -> %>
                        <option value="${ loc.id }" ${ selectedLocation?.id == loc.id ? "selected=\"selected\"" : "" }>${ ui.encodeHtmlContent(loc.name) }</option>
                    <% } %>
                </select>
            </div>
        <% } %>
        <div class="queue-report-filter-field">
            <label for="queue-report-status">Status</label>
            <select id="queue-report-status" name="status">
                <option value="ALL" ${ selectedStatus == "ALL" ? "selected=\"selected\"" : "" }>All</option>
                <% statuses.each { s -> %>
                    <option value="${ s.name() }" ${ selectedStatus == s.name() ? "selected=\"selected\"" : "" }>${ s.name() }</option>
                <% } %>
            </select>
        </div>
        <div class="queue-report-filter-field">
            <label for="queue-report-start-date">Start date</label>
            <input id="queue-report-start-date" name="startDate" type="date" required="required"
                   value="${ ui.escapeAttribute(selectedStartDate) }" />
        </div>
        <div class="queue-report-filter-field">
            <label for="queue-report-end-date">End date</label>
            <input id="queue-report-end-date" name="endDate" type="date" required="required"
                   value="${ ui.escapeAttribute(selectedEndDate) }" />
        </div>
        <div class="queue-report-filter-actions">
            <button type="submit" class="button"><i class="icon-filter"></i> Run</button>
            <button type="submit" name="export" value="excel" class="button queue-report-export">
                <i class="icon-download"></i> Export Excel
            </button>
        </div>
    </form>
    <% if (dateRangeError) { %>
        <div class="note-container"><div class="note warning">${ ui.encodeHtmlContent(dateRangeError) }</div></div>
    <% } %>
    <h4>Total entries: ${ entries.size() }</h4>
    <table>
        <thead><tr><th>Queue #</th><th>Patient</th><th>Service point</th><th>Priority</th><th>Status</th><th>Waiting time</th><th>Arrival</th><th>Completed</th></tr></thead>
        <tbody>
            <% entries.each { entry ->
                def priorityName = entry.priority?.name() ?: ""
                def priorityClass = priorityName.toLowerCase().replace('_', '-')
                def priorityLabel = priorityName.toLowerCase().replace('_', ' ').capitalize()
                def statusName = entry.status?.name() ?: ""
                def statusClass = statusName.toLowerCase().replace('_', '-')
                def statusLabel = statusName.toLowerCase().replace('_', ' ').capitalize()
                def patientName = entry.patient?.personName?.fullName ?: ""
                def visitedServicePoints = visitedServicePointsByEntryId[entry.id] ?: []
                def detailId = "queue-report-service-points-" + entry.id
            %>
                <tr>
                    <td>${ ui.encodeHtmlContent(entry.queueNumber) }</td>
                    <td>
                        <button type="button" class="queue-report-patient-toggle"
                                aria-expanded="false" aria-controls="${ detailId }"
                                aria-label="Show visited service points for ${ ui.escapeAttribute(patientName) }"
                                title="Show visited service points">
                            <i class="icon-chevron-right" aria-hidden="true"></i>
                            <span class="queue-report-patient-name">${ ui.encodeHtmlContent(patientName) }</span>
                        </button>
                    </td>
                    <td>${ ui.encodeHtmlContent(entry.servicePoint?.name ?: "") }</td>
                    <td><span class="queue-report-badge queue-priority-${ priorityClass }">${ ui.encodeHtmlContent(priorityLabel) }</span></td>
                    <td><span class="queue-report-badge queue-status-${ statusClass }">${ ui.encodeHtmlContent(statusLabel) }</span></td>
                    <td class="queue-report-wait-time">${ ui.encodeHtmlContent(waitingTimeByEntryId[entry.id] ?: "-") }</td>
                    <td>${ entry.arrivalTime ? entry.arrivalTime.format("yyyy-MM-dd HH:mm") : "" }</td>
                    <td>${ entry.completedTime ? entry.completedTime.format("yyyy-MM-dd HH:mm") : "" }</td>
                </tr>
                <tr id="${ detailId }" class="queue-report-detail" hidden="hidden">
                    <td colspan="8">
                        <div class="queue-report-journey">
                            <strong class="queue-report-journey-title">Visited service points</strong>
                            <ol class="queue-report-service-points">
                                <% visitedServicePoints.eachWithIndex { servicePoint, pointIndex -> %>
                                    <li>
                                        <span class="queue-report-service-point-order">${ pointIndex + 1 }</span>
                                        <span>${ ui.encodeHtmlContent(servicePoint.name) }</span>
                                    </li>
                                <% } %>
                            </ol>
                        </div>
                    </td>
                </tr>
            <% } %>
        </tbody>
    </table>
    <script type="text/javascript">
        (function() {
            var toggles = document.querySelectorAll(".queue-report-patient-toggle");
            for (var index = 0; index < toggles.length; index++) {
                toggles[index].addEventListener("click", function() {
                    var expanded = this.getAttribute("aria-expanded") === "true";
                    var detail = document.getElementById(this.getAttribute("aria-controls"));
                    if (!detail) {
                        return;
                    }
                    this.setAttribute("aria-expanded", expanded ? "false" : "true");
                    this.setAttribute("aria-label", expanded
                            ? this.getAttribute("aria-label").replace("Hide", "Show")
                            : this.getAttribute("aria-label").replace("Show", "Hide"));
                    this.setAttribute("title", expanded
                            ? "Show visited service points"
                            : "Hide visited service points");
                    detail.hidden = expanded;
                });
            }
        })();
    </script>
<% } %>
