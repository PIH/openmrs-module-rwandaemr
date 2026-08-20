<%
    ui.decorateWith("appui", "standardEmrPage")
%>

<script type="text/javascript">
    var breadcrumbs = [
        { icon: "icon-home", link: '/' + OPENMRS_CONTEXT_PATH + '/index.htm' },
        { label: "Service Point Queue", link: "${ ui.pageLink("rwandaemr", "queue/servicePointQueue") }" }
    ];
</script>

<style>
    .queue-table { width: 100%; table-layout: fixed; }
    .queue-table th, .queue-table td { vertical-align: top; overflow-wrap: anywhere; }
    .queue-actions form { display: inline-block; margin: 0 4px 4px 0; max-width: 100%; }
    .queue-actions .queue-select-action { box-sizing: border-box; display: flex; flex-direction: column; gap: 4px; margin-bottom: 10px; width: 100%; }
    .queue-actions .queue-select-action label { font-size: 0.85em; font-weight: bold; }
    .queue-actions .queue-select-action select { box-sizing: border-box; max-width: 100%; min-width: 0; width: 100%; }
    .queue-actions .queue-select-action .button { align-self: flex-start; margin: 0; }
    .queue-muted { color: #666; font-size: 0.9em; }
    .queue-patient-link-form { margin: 4px 0 0; }
    .queue-patient-link { display: inline-block; border: 0; background: transparent; color: #007fff; cursor: pointer; padding: 0; font: inherit; }
    .queue-patient-link:hover { text-decoration: underline; }
</style>

<% if (!authorized) { %>
    <div class="note-container"><div class="note warning">You do not have permission to view queue entries.</div></div>
<% } else { %>
    <h3>Service Point Queue</h3>
    <form method="get" action="${ ui.pageLink("rwandaemr", "queue/servicePointQueue") }">
        <select name="servicePointId">
            <option value="">All login-location service points</option>
            <% servicePoints.each { sp -> %>
                <option value="${ sp.id }" ${ selectedServicePoint?.id == sp.id ? "selected=\"selected\"" : "" }>${ ui.encodeHtmlContent(sp.name) }</option>
            <% } %>
        </select>
        <select name="status">
            <option value="ALL" ${ selectedStatus == "ALL" ? "selected=\"selected\"" : "" }>All</option>
            <% statuses.each { s -> %>
                <option value="${ s.name() }" ${ selectedStatus == s.name() ? "selected=\"selected\"" : "" }>${ s.name() }</option>
            <% } %>
        </select>
        <button type="submit" class="button">Filter</button>
    </form>
    <br/>
    <% if (entries.isEmpty()) { %>
        <div class="note-container"><div class="note">No queue entries found.</div></div>
    <% } else { %>
        <table class="queue-table">
            <thead>
                <tr>
                    <th>Queue #</th>
                    <th>Patient</th>
                    <th>Service point</th>
                    <th>Service requested</th>
                    <th>Reason for transfer</th>
                    <th>Priority</th>
                    <th>Status</th>
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
                %>
                    <tr>
                        <td>${ ui.encodeHtmlContent(entry.queueNumber) }</td>
                        <td>
                            ${ ui.encodeHtmlContent(patient?.patientIdentifier?.identifier ?: "") }
                            <div>${ ui.encodeHtmlContent(patient?.personName?.fullName ?: "") }</div>
                            <div class="queue-muted">${ ui.encodeHtmlContent(patient?.gender ?: "") } / ${ patient?.age ?: "" }</div>
                            <% if (patient?.id) { %>
                                <form class="queue-patient-link-form" method="post" action="${ ui.pageLink("rwandaemr", "queue/servicePointQueue") }">
                                    <input type="hidden" name="action" value="openDashboard" />
                                    <input type="hidden" name="entryId" value="${ entry.id }" />
                                    <input type="hidden" name="servicePointId" value="${ selectedServicePoint?.id ?: "" }" />
                                    <input type="hidden" name="status" value="${ ui.encodeHtmlContent(selectedStatus) }" />
                                    <button type="submit" class="queue-patient-link"><i class="icon-user"></i> Dashboard</button>
                                </form>
                            <% } %>
                        </td>
                        <td>${ ui.encodeHtmlContent(entry.servicePoint?.name ?: "") }</td>
                        <td>${ ui.encodeHtmlContent(entry.serviceRequestedConcept?.name?.name ?: entry.serviceRequestedConcept?.uuid ?: "-") }</td>
                        <td>${ ui.encodeHtmlContent(entry.transferReason ?: "-") }</td>
                        <td>${ ui.encodeHtmlContent(entry.priority?.displayName ?: "") }</td>
                        <td>${ ui.encodeHtmlContent(entry.status?.name() ?: "") }</td>
                        <td>${ entry.arrivalTime ? entry.arrivalTime.format("HH:mm") : "" }</td>
                        <% if (canTransferPatient || canManageQueue) { %>
                            <td class="queue-actions">
                                <% if (canManageQueue) { %>
                                    <form class="queue-select-action" method="post"
                                          action="${ ui.pageLink("rwandaemr", "queue/servicePointQueue") }">
                                        <input type="hidden" name="action" value="updatePriority" />
                                        <input type="hidden" name="entryId" value="${ entry.id }" />
                                        <input type="hidden" name="servicePointId" value="${ selectedServicePoint?.id ?: "" }" />
                                        <input type="hidden" name="status" value="${ ui.encodeHtmlContent(selectedStatus) }" />
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
                                          action="${ ui.pageLink("rwandaemr", "queue/servicePointQueue") }"
                                          data-patient-name="${ ui.escapeAttribute(patient?.personName?.fullName ?: "Patient") }"
                                          data-current-service-point="${ ui.escapeAttribute(entry.servicePoint?.name ?: "") }">
                                        <input type="hidden" name="action" value="transfer" />
                                        <input type="hidden" name="entryId" value="${ entry.id }" />
                                        <input type="hidden" name="servicePointId" value="${ selectedServicePoint?.id ?: "" }" />
                                        <input type="hidden" name="status" value="${ ui.encodeHtmlContent(selectedStatus) }" />
                                        <label for="destination-${ entry.id }">Send to</label>
                                        <select id="destination-${ entry.id }" name="destinationServicePointId">
                                            <% destinationServicePoints.each { sp -> %>
                                                <option value="${ sp.id }">${ ui.encodeHtmlContent(sp.name) }</option>
                                            <% } %>
                                        </select>
                                        <input type="hidden" name="reason" value="" />
                                        <button type="submit" class="button">Send</button>
                                    </form>
                                <% } %>
                            </td>
                        <% } %>
                    </tr>
                <% } %>
            </tbody>
        </table>
    <% } %>
    <%= ui.includeFragment("rwandaemr", "queue/transferReasonDialog") %>
<% } %>
