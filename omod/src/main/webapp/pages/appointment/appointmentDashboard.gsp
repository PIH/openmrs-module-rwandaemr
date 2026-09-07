<%
    ui.decorateWith("appui", "standardEmrPage")
%>

<script type="text/javascript">
    var breadcrumbs = [
        { icon: "icon-home", link: '/' + OPENMRS_CONTEXT_PATH + '/index.htm' },
        { label: "Appointment Dashboard", link: "${ ui.pageLink("rwandaemr", "appointment/appointmentDashboard") }" }
    ];

    jq(function() {
        jq(".appointment-detail-toggle").click(function() {
            var button = jq(this);
            var detail = jq("#" + button.attr("aria-controls"));
            var expanded = button.attr("aria-expanded") === "true";
            button.attr("aria-expanded", expanded ? "false" : "true");
            button.find("i").attr("class", expanded ? "icon-chevron-right" : "icon-chevron-down");
            detail.toggle(!expanded);
        });

        jq(".appointment-status-select").change(function() {
            if (this.value) {
                this.form.submit();
            }
        });
    });
</script>

<style type="text/css">
    .appointment-dashboard-filters {
        align-items: flex-end;
        background: #f7f9f9;
        border-bottom: 1px solid #d4dddd;
        border-top: 1px solid #d4dddd;
        display: flex;
        flex-wrap: wrap;
        gap: 12px;
        margin: 14px 0 18px;
        padding: 14px 10px;
    }

    .appointment-filter-field {
        flex: 1 1 170px;
        max-width: 270px;
        min-width: 0;
    }

    .appointment-filter-field label {
        color: #344343;
        display: block;
        font-size: 0.84em;
        font-weight: bold;
        margin-bottom: 5px;
    }

    .appointment-filter-field input,
    .appointment-filter-field select {
        box-sizing: border-box;
        min-height: 36px;
        width: 100%;
    }

    .appointment-filter-actions {
        display: flex;
        flex: 0 0 auto;
        gap: 8px;
    }

    .appointment-summary {
        align-items: stretch;
        background: #eef5f4;
        border-bottom: 1px solid #bfd2cf;
        border-top: 1px solid #bfd2cf;
        display: flex;
        flex-wrap: wrap;
        margin-bottom: 20px;
    }

    .appointment-summary-item {
        border-right: 1px solid #bfd2cf;
        min-width: 130px;
        padding: 12px 18px;
    }

    .appointment-summary-value {
        color: #1f4f45;
        font-size: 1.45em;
        font-weight: bold;
    }

    .appointment-summary-label {
        color: #566666;
        font-size: 0.8em;
        margin-top: 2px;
    }

    .appointment-table-wrap {
        max-width: 100%;
        overflow-x: auto;
    }

    .appointment-table {
        min-width: 900px;
        width: 100%;
    }

    .appointment-detail-toggle {
        background: transparent;
        border: 0;
        color: #246b5c;
        cursor: pointer;
        padding: 2px 5px;
    }

    .appointment-detail-row {
        background: #f8fafa;
        display: none;
    }

    .appointment-detail-content {
        padding: 10px 14px 14px 38px;
    }

    .appointment-patient-table {
        margin: 0;
        min-width: 800px;
        width: 100%;
    }

    .appointment-status {
        border: 1px solid;
        border-radius: 3px;
        display: inline-block;
        font-size: 0.78em;
        font-weight: bold;
        padding: 3px 7px;
    }

    .appointment-status.requested {
        background: #fff4d6;
        border-color: #e0bd62;
        color: #735315;
    }

    .appointment-status.confirmed {
        background: #e3f0ff;
        border-color: #8ab6e8;
        color: #245a91;
    }

    .appointment-status.present {
        background: #e0f3f2;
        border-color: #78b9b4;
        color: #185f5a;
    }

    .appointment-status.completed {
        background: #e5f5ec;
        border-color: #8bc4a5;
        color: #17643d;
    }

    .appointment-status.cancelled,
    .appointment-status.no-show {
        background: #f2f3f3;
        border-color: #c9cece;
        color: #5d6666;
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

    .appointment-status-form {
        align-items: center;
        display: flex;
        gap: 6px;
        margin: 0;
    }

    .appointment-status-form select {
        max-width: 150px;
        min-height: 32px;
    }

    .appointment-booking-actions {
        align-items: center;
        display: flex;
        flex-wrap: wrap;
        gap: 6px;
    }

    .appointment-registration-link {
        white-space: nowrap;
    }

    .appointment-registration-form {
        margin: 0;
    }
</style>

<% if (!authorized) { %>
    <div class="note-container"><div class="note warning">You do not have permission to view appointments.</div></div>
<% } else { %>
    <h3>Appointment Dashboard</h3>

    <form class="appointment-dashboard-filters" method="get"
          action="${ ui.pageLink("rwandaemr", "appointment/appointmentDashboard") }">
        <div class="appointment-filter-field">
            <label for="appointment-dashboard-service-point">Service point</label>
            <select id="appointment-dashboard-service-point" name="servicePointId">
                <option value="">All service points</option>
                <% servicePoints.each { servicePoint -> %>
                    <option value="${ servicePoint.id }"
                            ${ selectedServicePoint?.id == servicePoint.id ? "selected=\"selected\"" : "" }>${ ui.encodeHtmlContent(servicePoint.name) }</option>
                <% } %>
            </select>
        </div>
        <div class="appointment-filter-field">
            <label for="appointment-dashboard-start">Start date</label>
            <input id="appointment-dashboard-start" type="date" name="startDate"
                   value="${ ui.escapeAttribute(selectedStartDate) }"/>
        </div>
        <div class="appointment-filter-field">
            <label for="appointment-dashboard-end">End date</label>
            <input id="appointment-dashboard-end" type="date" name="endDate"
                   value="${ ui.escapeAttribute(selectedEndDate) }"/>
        </div>
        <div class="appointment-filter-actions">
            <button type="submit" class="button">
                <i class="icon-filter" aria-hidden="true"></i> Filter
            </button>
            <button type="submit" name="export" value="excel" class="button">
                <i class="icon-download" aria-hidden="true"></i> Export Excel
            </button>
        </div>
    </form>

    <% if (dateRangeError) { %>
        <div class="note-container"><div class="note warning">${ ui.encodeHtmlContent(dateRangeError) }</div></div>
    <% } %>

    <div class="appointment-summary">
        <div class="appointment-summary-item">
            <div class="appointment-summary-value">${ scheduleSummaries.size() }</div>
            <div class="appointment-summary-label">Operating schedules</div>
        </div>
        <div class="appointment-summary-item">
            <div class="appointment-summary-value">${ totalCapacity }</div>
            <div class="appointment-summary-label">Total capacity</div>
        </div>
        <div class="appointment-summary-item">
            <div class="appointment-summary-value">${ totalBooked }</div>
            <div class="appointment-summary-label">Patients booked</div>
        </div>
        <div class="appointment-summary-item">
            <div class="appointment-summary-value">${ totalRemaining }</div>
            <div class="appointment-summary-label">Places remaining</div>
        </div>
    </div>

    <div class="appointment-table-wrap">
        <table class="appointment-table">
            <thead>
            <tr>
                <th>Patients</th>
                <th>Date</th>
                <th>Service point</th>
                <th>Provider</th>
                <th>Booked</th>
                <th>Maximum</th>
                <th>Remaining</th>
                <th>Schedule</th>
            </tr>
            </thead>
            <tbody>
            <% if (scheduleSummaries.isEmpty()) { %>
                <tr><td colspan="8">No appointment schedules match these filters.</td></tr>
            <% } %>
            <% scheduleSummaries.each { summary ->
                def schedule = summary.schedule
                def bookings = bookingsByScheduleId[schedule.id] ?: []
                def detailId = "appointment-schedule-detail-" + schedule.id
            %>
                <tr>
                    <td>
                        <button type="button" class="appointment-detail-toggle"
                                aria-expanded="false" aria-controls="${ detailId }"
                                title="Show booked patients">
                            <i class="icon-chevron-right" aria-hidden="true"></i>
                            ${ summary.bookedPatients }
                        </button>
                    </td>
                    <td>${ ui.format(schedule.scheduleDate) }</td>
                    <td>${ ui.encodeHtmlContent(schedule.servicePoint?.name ?: "") }</td>
                    <td>${ ui.encodeHtmlContent(schedule.provider?.name ?: "") }</td>
                    <td>${ summary.bookedPatients }</td>
                    <td>${ schedule.maximumPatients }</td>
                    <td>${ summary.remainingCapacity }</td>
                    <td>
                        <span class="appointment-status ${ schedule.active ? "open" : "closed" }">
                            ${ schedule.active ? "Open" : "Closed" }
                        </span>
                    </td>
                </tr>
                <tr id="${ detailId }" class="appointment-detail-row">
                    <td colspan="8">
                        <div class="appointment-detail-content">
                            <table class="appointment-patient-table">
                                <thead>
                                <tr>
                                    <th>Patient</th>
                                    <th>Identifier</th>
                                    <th>Phone number</th>
                                    <th>Program</th>
                                    <th>Visit type</th>
                                    <th>Status</th>
                                    <th>Requested</th>
                                    <th>Notes</th>
                                    <th>Action</th>
                                </tr>
                                </thead>
                                <tbody>
                                <% if (bookings.isEmpty()) { %>
                                    <tr><td colspan="9">No patients are booked for this schedule.</td></tr>
                                <% } %>
                                <% bookings.each { booking ->
                                    def statusName = booking.status?.name() ?: ""
                                    def statusClass = statusName.toLowerCase().replace("_", "-")
                                    def nextStatuses = []
                                    if (statusName == "REQUESTED" || statusName == "CONFIRMED" ||
                                            statusName == "PRESENT") {
                                        nextStatuses = ["COMPLETED", "CANCELLED"]
                                    }
                                    def canRegisterToday = statusName != "CANCELLED" &&
                                            booking.patient?.id && todayScheduleIds.contains(schedule.id)
                                    def registrationEncounterId = null
                                    def registrationActionLabel = "Register"
                                    if (canRegisterToday) {
                                        registrationEncounterId =
                                                registrationEncounterIdsByPatientId[booking.patient.id]
                                        if (registrationEncounterId) {
                                            registrationActionLabel = "Edit registration"
                                        }
                                    }
                                %>
                                    <tr>
                                        <td>${ ui.encodeHtmlContent(booking.patient?.personName?.fullName ?: "") }</td>
                                        <td>${ ui.encodeHtmlContent(booking.patient?.patientIdentifier?.identifier ?: "") }</td>
                                        <td>${ ui.encodeHtmlContent(phoneNumberByPatientId[booking.patient?.id] ?: "") }</td>
                                        <td>${ ui.encodeHtmlContent(booking.program?.name ?: "") }</td>
                                        <td>${ ui.encodeHtmlContent(booking.visitType?.displayName ?: "") }</td>
                                        <td>
                                            <span class="appointment-status ${ statusClass }">
                                                ${ ui.encodeHtmlContent(statusName.toLowerCase().replace("_", " ").capitalize()) }
                                            </span>
                                        </td>
                                        <td>${ ui.format(booking.requestedAt) }</td>
                                        <td>${ ui.encodeHtmlContent(booking.notes ?: "") }</td>
                                        <td>
                                            <% if (canRegisterToday || (canManageAppointments && !nextStatuses.isEmpty())) { %>
                                                <div class="appointment-booking-actions">
                                                    <% if (canRegisterToday) { %>
                                                        <form class="appointment-registration-form" method="post"
                                                              action="${ ui.pageLink("rwandaemr", "appointment/appointmentDashboard") }">
                                                            <input type="hidden" name="action" value="register"/>
                                                            <input type="hidden" name="bookingId" value="${ booking.id }"/>
                                                            <input type="hidden" name="servicePointId" value="${ selectedServicePoint?.id ?: "" }"/>
                                                            <input type="hidden" name="startDate" value="${ ui.escapeAttribute(selectedStartDate) }"/>
                                                            <input type="hidden" name="endDate" value="${ ui.escapeAttribute(selectedEndDate) }"/>
                                                            <button type="submit"
                                                                    class="button appointment-registration-link"
                                                                    title="${ ui.escapeAttribute(registrationActionLabel) }">
                                                                <i class="${ registrationEncounterId ? "icon-pencil" : "icon-user" }"
                                                                   aria-hidden="true"></i>
                                                                ${ ui.encodeHtmlContent(registrationActionLabel) }
                                                            </button>
                                                        </form>
                                                    <% } %>
                                                    <% if (canManageAppointments && !nextStatuses.isEmpty()) { %>
                                                        <form class="appointment-status-form" method="post"
                                                              action="${ ui.pageLink("rwandaemr", "appointment/appointmentDashboard") }">
                                                            <input type="hidden" name="bookingId" value="${ booking.id }"/>
                                                            <input type="hidden" name="servicePointId" value="${ selectedServicePoint?.id ?: "" }"/>
                                                            <input type="hidden" name="startDate" value="${ ui.escapeAttribute(selectedStartDate) }"/>
                                                            <input type="hidden" name="endDate" value="${ ui.escapeAttribute(selectedEndDate) }"/>
                                                            <select class="appointment-status-select"
                                                                    name="appointmentStatus" required="required">
                                                                <option value="">Change status</option>
                                                                <% nextStatuses.each { nextStatus -> %>
                                                                    <option value="${ nextStatus }">${ nextStatus.toLowerCase().replace("_", " ").capitalize() }</option>
                                                                <% } %>
                                                            </select>
                                                        </form>
                                                    <% } %>
                                                </div>
                                            <% } %>
                                        </td>
                                    </tr>
                                <% } %>
                                </tbody>
                            </table>
                        </div>
                    </td>
                </tr>
            <% } %>
            </tbody>
        </table>
    </div>
<% } %>
