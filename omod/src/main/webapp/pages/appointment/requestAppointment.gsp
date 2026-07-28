<%
    ui.decorateWith("appui", "standardEmrPage")
%>

<script type="text/javascript">
    var breadcrumbs = [
        { icon: "icon-home", link: '/' + OPENMRS_CONTEXT_PATH + '/index.htm' },
        { label: "Request Appointment", link: "${ ui.pageLink("rwandaemr", "appointment/requestAppointment") }" }
    ];
</script>

<style type="text/css">
    .appointment-patient-band {
        align-items: center;
        background: #eef5f4;
        border-bottom: 1px solid #bfd2cf;
        border-top: 1px solid #bfd2cf;
        display: flex;
        justify-content: space-between;
        margin: 14px 0 20px;
        padding: 12px 14px;
    }

    .appointment-patient-name {
        color: #263838;
        font-size: 1.15em;
        font-weight: bold;
    }

    .appointment-muted {
        color: #687575;
        font-size: 0.86em;
        margin-top: 3px;
    }

    .appointment-booking-band {
        align-items: flex-end;
        background: #f7f9f9;
        border-bottom: 1px solid #d4dddd;
        border-top: 1px solid #d4dddd;
        display: flex;
        flex-wrap: wrap;
        gap: 12px;
        margin: 14px 0 22px;
        padding: 14px 10px;
    }

    .appointment-booking-band.available {
        background: #eef6f3;
        border-color: #b9d4cb;
    }

    .appointment-patient-search {
        width: 100%;
    }

    .appointment-field {
        flex: 1 1 220px;
        max-width: 390px;
        min-width: 0;
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

    .appointment-inline-form {
        display: inline-flex;
        margin: 0;
    }

    .appointment-actions,
    .appointment-postpone-form {
        align-items: center;
        display: flex;
        flex-wrap: wrap;
        gap: 6px;
    }

    .appointment-postpone-form select {
        margin: 0;
        max-width: 210px;
        min-width: 150px;
    }

    .appointment-table-wrap {
        max-width: 100%;
        overflow-x: auto;
    }

    .appointment-table {
        min-width: 1040px;
        width: 100%;
    }
</style>

<% if (!authorized) { %>
    <div class="note-container"><div class="note warning">You do not have permission to request appointments.</div></div>
<% } else { %>
    <h3>Request Appointment</h3>

    <% if (!patient) { %>
        <div class="appointment-booking-band">
            <div class="appointment-patient-search">
                <h4>Select patient</h4>
                ${ ui.includeFragment('coreapps', 'patientsearch/patientSearchWidget', [
                        showLastViewedPatients: 'true',
                        afterSelectedUrl: '/rwandaemr/appointment/requestAppointment.page?patientId={{patientId}}'
                ])}
            </div>
        </div>
    <% } else { %>
        <div class="appointment-patient-band">
            <div>
                <div class="appointment-patient-name">${ ui.encodeHtmlContent(patient.personName?.fullName ?: "") }</div>
                <div class="appointment-muted">
                    ${ ui.encodeHtmlContent(patient.patientIdentifier?.identifier ?: "") }
                    ${ patient.gender ? " | " + ui.encodeHtmlContent(patient.gender) : "" }
                    ${ patient.age != null ? " | " + patient.age + " years" : "" }
                </div>
            </div>
            <a class="button" href="${ ui.pageLink("rwandaemr", "appointment/requestAppointment") }">
                <i class="icon-search" aria-hidden="true"></i> Change Patient
            </a>
        </div>

        <form class="appointment-booking-band" method="get"
              action="${ ui.pageLink("rwandaemr", "appointment/requestAppointment") }">
            <input type="hidden" name="patientId" value="${ ui.escapeAttribute(patient.uuid) }"/>
            <div class="appointment-field">
                <label for="appointment-service-point">Service point</label>
                <select id="appointment-service-point" name="servicePointId" required="required"
                        onchange="this.form.submit();">
                    <option value="">Select service point</option>
                    <% servicePoints.each { servicePoint -> %>
                        <option value="${ servicePoint.id }"
                                ${ selectedServicePoint?.id == servicePoint.id ? "selected=\"selected\"" : "" }>${ ui.encodeHtmlContent(servicePoint.name) }</option>
                    <% } %>
                </select>
            </div>
            <button type="submit" class="button">
                <i class="icon-calendar" aria-hidden="true"></i> Check Availability
            </button>
        </form>

        <% if (selectedServicePoint) { %>
            <% if (availableSchedules.isEmpty()) { %>
                <div class="note-container">
                    <div class="note warning">No appointment dates with remaining capacity are available for this service point.</div>
                </div>
            <% } else { %>
                <form class="appointment-booking-band available" method="post"
                      action="${ ui.pageLink("rwandaemr", "appointment/requestAppointment") }">
                    <input type="hidden" name="action" value="request"/>
                    <input type="hidden" name="patientId" value="${ ui.escapeAttribute(patient.uuid) }"/>
                    <input type="hidden" name="servicePointId" value="${ selectedServicePoint.id }"/>
                    <div class="appointment-field">
                        <label for="appointment-available-date">Available date</label>
                        <select id="appointment-available-date" name="scheduleId" required="required">
                            <option value="">Select available date</option>
                            <% availableSchedules.each { summary -> %>
                                <option value="${ summary.schedule.id }">
                                    ${ ui.format(summary.schedule.scheduleDate) } - ${ summary.remainingCapacity } places remaining
                                </option>
                            <% } %>
                        </select>
                    </div>
                    <div class="appointment-field">
                        <label for="appointment-program">Program <span class="appointment-muted">(optional)</span></label>
                        <select id="appointment-program" name="programId">
                            <option value="">No program</option>
                            <% programs.each { program -> %>
                                <option value="${ program.id }">${ ui.encodeHtmlContent(program.name ?: "") }</option>
                            <% } %>
                        </select>
                    </div>
                    <div class="appointment-field">
                        <label for="appointment-visit-type">Visit type <span class="appointment-muted">(optional)</span></label>
                        <select id="appointment-visit-type" name="visitType">
                            <option value="">No visit type</option>
                            <% visitTypes.each { visitType -> %>
                                <option value="${ visitType.name() }">${ ui.encodeHtmlContent(visitType.displayName) }</option>
                            <% } %>
                        </select>
                    </div>
                    <div class="appointment-field">
                        <label for="appointment-request-notes">Notes</label>
                        <input id="appointment-request-notes" type="text" name="notes" maxlength="1024"/>
                    </div>
                    <button type="submit" class="button confirm">
                        <i class="icon-calendar" aria-hidden="true"></i> Request Appointment
                    </button>
                </form>
            <% } %>
        <% } %>

        <h4>Upcoming Appointments</h4>
        <div class="appointment-table-wrap">
            <table class="appointment-table">
                <thead>
                <tr>
                    <th>Date</th>
                    <th>Service point</th>
                    <th>Program</th>
                    <th>Visit type</th>
                    <th>Status</th>
                    <th>Notes</th>
                    <th>Action</th>
                </tr>
                </thead>
                <tbody>
                <% if (patientBookings.isEmpty()) { %>
                    <tr><td colspan="7">This patient has no upcoming appointments.</td></tr>
                <% } %>
                <% patientBookings.each { booking ->
                    def statusName = booking.status?.name() ?: ""
                    def statusClass = statusName.toLowerCase().replace("_", "-")
                    def canCancel = statusName == "REQUESTED" || statusName == "CONFIRMED"
                    def postponeSchedules = postponeSchedulesByBookingId[booking.id] ?: []
                %>
                    <tr>
                        <td>${ ui.format(booking.schedule?.scheduleDate) }</td>
                        <td>${ ui.encodeHtmlContent(booking.schedule?.servicePoint?.name ?: "") }</td>
                        <td>${ ui.encodeHtmlContent(booking.program?.name ?: "") }</td>
                        <td>${ ui.encodeHtmlContent(booking.visitType?.displayName ?: "") }</td>
                        <td>
                            <span class="appointment-status ${ statusClass }">
                                ${ ui.encodeHtmlContent(statusName.toLowerCase().replace("_", " ").capitalize()) }
                            </span>
                        </td>
                        <td>${ ui.encodeHtmlContent(booking.notes ?: "") }</td>
                        <td>
                            <% if (canCancel) { %>
                                <div class="appointment-actions">
                                    <% if (!postponeSchedules.isEmpty()) { %>
                                        <form class="appointment-postpone-form" method="post"
                                              action="${ ui.pageLink("rwandaemr", "appointment/requestAppointment") }">
                                            <input type="hidden" name="action" value="postpone"/>
                                            <input type="hidden" name="bookingId" value="${ booking.id }"/>
                                            <input type="hidden" name="patientId" value="${ ui.escapeAttribute(patient.uuid) }"/>
                                            <input type="hidden" name="servicePointId" value="${ selectedServicePoint?.id ?: "" }"/>
                                            <select name="newScheduleId" required="required" title="New appointment date">
                                                <option value="">Postpone to...</option>
                                                <% postponeSchedules.each { summary -> %>
                                                    <option value="${ summary.schedule.id }">
                                                        ${ ui.format(summary.schedule.scheduleDate) } - ${ summary.remainingCapacity } places
                                                    </option>
                                                <% } %>
                                            </select>
                                            <button type="submit" class="button" title="Postpone appointment">
                                                <i class="icon-calendar" aria-hidden="true"></i> Postpone
                                            </button>
                                        </form>
                                    <% } %>
                                    <form class="appointment-inline-form" method="post"
                                          action="${ ui.pageLink("rwandaemr", "appointment/requestAppointment") }">
                                        <input type="hidden" name="action" value="cancel"/>
                                        <input type="hidden" name="bookingId" value="${ booking.id }"/>
                                        <input type="hidden" name="patientId" value="${ ui.escapeAttribute(patient.uuid) }"/>
                                        <input type="hidden" name="servicePointId" value="${ selectedServicePoint?.id ?: "" }"/>
                                        <button type="submit" class="button">
                                            <i class="icon-remove" aria-hidden="true"></i> Cancel
                                        </button>
                                    </form>
                                </div>
                            <% } %>
                        </td>
                    </tr>
                <% } %>
                </tbody>
            </table>
        </div>
    <% } %>
<% } %>
