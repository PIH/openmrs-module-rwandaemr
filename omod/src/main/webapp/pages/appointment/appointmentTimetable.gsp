<%
    ui.decorateWith("appui", "standardEmrPage")
%>

<script type="text/javascript">
    var breadcrumbs = [
        { icon: "icon-home", link: '/' + OPENMRS_CONTEXT_PATH + '/index.htm' },
        { label: "Appointment Timetable", link: "${ ui.pageLink("rwandaemr", "appointment/appointmentTimetable") }" }
    ];
</script>

<style type="text/css">
    .appointment-toolbar,
    .appointment-editor {
        align-items: flex-end;
        background: #f7f9f9;
        border-bottom: 1px solid #d4dddd;
        border-top: 1px solid #d4dddd;
        display: flex;
        flex-wrap: wrap;
        gap: 12px;
        margin: 14px 0 20px;
        padding: 14px 10px;
    }

    .appointment-editor {
        background: #eef6f3;
        border-color: #b9d4cb;
    }

    .appointment-field {
        flex: 1 1 170px;
        max-width: 280px;
        min-width: 0;
    }

    .appointment-field.notes {
        flex-basis: 240px;
        max-width: 420px;
    }

    .appointment-field label {
        color: #344343;
        display: block;
        font-size: 0.84em;
        font-weight: bold;
        margin-bottom: 5px;
    }

    .appointment-field input,
    .appointment-field select {
        box-sizing: border-box;
        min-height: 36px;
        width: 100%;
    }

    .appointment-table-wrap {
        max-width: 100%;
        overflow-x: auto;
    }

    .appointment-table {
        min-width: 760px;
        width: 100%;
    }

    .appointment-capacity {
        min-width: 150px;
    }

    .appointment-capacity-label {
        display: flex;
        font-size: 0.86em;
        justify-content: space-between;
        margin-bottom: 5px;
    }

    .appointment-capacity-track {
        background: #e4eaea;
        height: 6px;
        overflow: hidden;
    }

    .appointment-capacity-fill {
        background: #2f7d68;
        height: 100%;
    }

    .appointment-status {
        border: 1px solid;
        border-radius: 3px;
        display: inline-block;
        font-size: 0.78em;
        font-weight: bold;
        padding: 3px 7px;
    }

    .appointment-status.open {
        background: #e5f5ec;
        border-color: #8bc4a5;
        color: #17643d;
    }

    .appointment-status.closed {
        background: #f2f3f3;
        border-color: #c9cece;
        color: #5d6666;
    }

    .appointment-actions {
        align-items: center;
        display: flex;
        flex-wrap: nowrap;
        gap: 8px;
        justify-content: space-between;
        min-width: 370px;
        width: 100%;
    }

    .appointment-actions-cell {
        box-sizing: border-box;
        min-width: 400px;
        width: 400px;
        white-space: nowrap;
    }

    .appointment-inline-form {
        display: inline-flex;
        flex: 0 0 auto;
        margin: 0 0 0 auto;
    }

    .appointment-capacity-form {
        align-items: center;
        display: inline-flex;
        flex: 0 0 auto;
        gap: 5px;
        margin: 0;
    }

    .appointment-capacity-form input.appointment-capacity-input {
        box-sizing: border-box;
        flex: 0 0 70px;
        height: 34px;
        max-width: 70px !important;
        min-width: 70px !important;
        width: 70px !important;
    }

    .appointment-capacity-form .appointment-update-button {
        box-sizing: border-box;
        flex: 0 0 160px;
        height: 34px;
        max-width: 160px;
        min-width: 160px;
        overflow: visible;
        padding: 0 12px;
        white-space: nowrap;
        width: 160px;
    }
</style>

<% if (!authorized) { %>
    <div class="note-container"><div class="note warning">You do not have permission to manage appointment timetables.</div></div>
<% } else { %>
    <h3>Appointment Timetable</h3>

    <form class="appointment-editor" method="post"
          action="${ ui.pageLink("rwandaemr", "appointment/appointmentTimetable") }">
        <input type="hidden" name="action" value="save"/>
        <input type="hidden" name="startDate" value="${ ui.escapeAttribute(selectedStartDate) }"/>
        <input type="hidden" name="endDate" value="${ ui.escapeAttribute(selectedEndDate) }"/>
        <div class="appointment-field">
            <label for="appointment-editor-service-point">Service point</label>
            <select id="appointment-editor-service-point" name="servicePointId" required="required">
                <option value="">Select service point</option>
                <% servicePoints.each { servicePoint -> %>
                    <option value="${ servicePoint.id }"
                            ${ selectedServicePoint?.id == servicePoint.id ? "selected=\"selected\"" : "" }>${ ui.encodeHtmlContent(servicePoint.name) }</option>
                <% } %>
            </select>
        </div>
        <div class="appointment-field">
            <label for="appointment-editor-date">Operating date</label>
            <input id="appointment-editor-date" type="date" name="scheduleDate"
                   min="${ today }" required="required"/>
        </div>
        <div class="appointment-field">
            <label for="appointment-editor-capacity">Maximum patients</label>
            <input id="appointment-editor-capacity" type="number" name="maximumPatients"
                   min="1" step="1" required="required"/>
        </div>
        <div class="appointment-field">
            <label for="appointment-editor-provider">Provider</label>
            <select id="appointment-editor-provider" name="providerId">
                <option value="">No provider</option>
                <% licensedProviders.each { provider ->
                    def providerName = provider.name ?: "Unnamed provider"
                    def providerLabel = providerName + " - License: " + providerLicenses[provider.id]
                %>
                    <option value="${ provider.id }">${ ui.encodeHtmlContent(providerLabel) }</option>
                <% } %>
            </select>
        </div>
        <div class="appointment-field notes">
            <label for="appointment-editor-notes">Notes</label>
            <input id="appointment-editor-notes" type="text" name="notes" maxlength="1024"/>
        </div>
        <button type="submit" class="button confirm">
            <i class="icon-save" aria-hidden="true"></i> Save Schedule
        </button>
    </form>

    <form class="appointment-toolbar" method="get"
          action="${ ui.pageLink("rwandaemr", "appointment/appointmentTimetable") }">
        <div class="appointment-field">
            <label for="appointment-filter-service-point">Service point</label>
            <select id="appointment-filter-service-point" name="servicePointId">
                <option value="">All service points</option>
                <% servicePoints.each { servicePoint -> %>
                    <option value="${ servicePoint.id }"
                            ${ selectedServicePoint?.id == servicePoint.id ? "selected=\"selected\"" : "" }>${ ui.encodeHtmlContent(servicePoint.name) }</option>
                <% } %>
            </select>
        </div>
        <div class="appointment-field">
            <label for="appointment-filter-start">Start date</label>
            <input id="appointment-filter-start" type="date" name="startDate"
                   value="${ ui.escapeAttribute(selectedStartDate) }"/>
        </div>
        <div class="appointment-field">
            <label for="appointment-filter-end">End date</label>
            <input id="appointment-filter-end" type="date" name="endDate"
                   value="${ ui.escapeAttribute(selectedEndDate) }"/>
        </div>
        <button type="submit" class="button">
            <i class="icon-filter" aria-hidden="true"></i> Filter
        </button>
    </form>

    <% if (dateRangeError) { %>
        <div class="note-container"><div class="note warning">${ ui.encodeHtmlContent(dateRangeError) }</div></div>
    <% } %>

    <div class="appointment-table-wrap">
        <table class="appointment-table">
            <thead>
            <tr>
                <th>Date</th>
                <th>Service point</th>
                <th>Provider</th>
                <th>Capacity</th>
                <th>Remaining</th>
                <th>Status</th>
                <th>Notes</th>
                <th>Action</th>
            </tr>
            </thead>
            <tbody>
            <% if (scheduleSummaries.isEmpty()) { %>
                <tr><td colspan="8">No appointment schedules match these filters.</td></tr>
            <% } %>
            <% scheduleSummaries.each { summary ->
                def schedule = summary.schedule
                def maximumPatients = schedule.maximumPatients ?: 0
                def percentBooked = maximumPatients > 0
                        ? Math.min(100, Math.round(summary.bookedPatients * 100.0 / maximumPatients))
                        : 0
            %>
                <tr>
                    <td>${ ui.format(schedule.scheduleDate) }</td>
                    <td>${ ui.encodeHtmlContent(schedule.servicePoint?.name ?: "") }</td>
                    <td>${ ui.encodeHtmlContent(schedule.provider?.name ?: "") }</td>
                    <td class="appointment-capacity">
                        <div class="appointment-capacity-label">
                            <span>${ summary.bookedPatients } booked</span>
                            <span>${ maximumPatients } maximum</span>
                        </div>
                        <div class="appointment-capacity-track">
                            <div class="appointment-capacity-fill" style="width: ${ percentBooked }%;"></div>
                        </div>
                    </td>
                    <td>${ summary.remainingCapacity }</td>
                    <td>
                        <span class="appointment-status ${ schedule.active ? "open" : "closed" }">
                            ${ schedule.active ? "Open" : "Closed" }
                        </span>
                    </td>
                    <td>${ ui.encodeHtmlContent(schedule.notes ?: "") }</td>
                    <td class="appointment-actions-cell">
                        <div class="appointment-actions">
                            <% if (schedule.scheduleDate?.after(todayDate)) { %>
                                <form class="appointment-capacity-form" method="post"
                                      action="${ ui.pageLink("rwandaemr", "appointment/appointmentTimetable") }">
                                    <input type="hidden" name="action" value="capacity"/>
                                    <input type="hidden" name="scheduleId" value="${ schedule.id }"/>
                                    <input type="hidden" name="servicePointId" value="${ selectedServicePoint?.id ?: "" }"/>
                                    <input type="hidden" name="startDate" value="${ ui.escapeAttribute(selectedStartDate) }"/>
                                    <input type="hidden" name="endDate" value="${ ui.escapeAttribute(selectedEndDate) }"/>
                                    <input type="number" name="maximumPatients" value="${ maximumPatients }"
                                           class="appointment-capacity-input"
                                           min="${ Math.max(1, summary.bookedPatients) }" step="1"
                                           aria-label="Maximum patients" required="required"/>
                                    <button type="submit" class="button appointment-update-button"
                                            title="Update capacity" aria-label="Update capacity">
                                        <i class="icon-save" aria-hidden="true"></i>
                                        Update Capacity
                                    </button>
                                </form>
                            <% } %>
                            <form class="appointment-inline-form" method="post"
                                  action="${ ui.pageLink("rwandaemr", "appointment/appointmentTimetable") }">
                                <input type="hidden" name="action" value="toggle"/>
                                <input type="hidden" name="scheduleId" value="${ schedule.id }"/>
                                <input type="hidden" name="servicePointId" value="${ selectedServicePoint?.id ?: "" }"/>
                                <input type="hidden" name="startDate" value="${ ui.escapeAttribute(selectedStartDate) }"/>
                                <input type="hidden" name="endDate" value="${ ui.escapeAttribute(selectedEndDate) }"/>
                                <input type="hidden" name="active" value="${ schedule.active ? "false" : "true" }"/>
                                <button type="submit" class="button">
                                    <i class="${ schedule.active ? "icon-lock" : "icon-unlock" }" aria-hidden="true"></i>
                                    ${ schedule.active ? "Close" : "Open" }
                                </button>
                            </form>
                        </div>
                    </td>
                </tr>
            <% } %>
            </tbody>
        </table>
    </div>
<% } %>
