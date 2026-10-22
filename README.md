openmrs-module-rwandaemr
===================================

## Queue management

RwandaEMR queue management adds patients to a service queue when the TODAY'S
REGISTRATION form is submitted. Service points are OpenMRS `Location` records,
not a separate custom master table. A location is available as a queue service
point only when it has the OpenMRS location tag used by RwandaEMR for login
locations: `Login Location`.

### Registration workflow

1. The registration encounter is saved by the existing HTML Form Entry flow.
2. RwandaEMR reads the saved Service Requested obs from the registration
   encounter. The Service Requested concept is configured by the global
   property `registration.serviceRequestedConcept`.
3. RwandaEMR uses the saved encounter location as the queue service point when
   that location is tagged as `Login Location`.
4. If the encounter location is not a login location, RwandaEMR falls back to
   resolving the selected Service Requested value to a mapped queue service
   point location.
5. If a login-location service point is found, a queue entry is created with
   status `WAITING` and priority `NORMAL`.
6. The existing appointment behavior then runs.

Update the TODAY'S REGISTRATION form post-submission action from:

```xml
<postSubmissionAction class="org.openmrs.module.rwandaemr.htmlformentry.CreateAppointmentAction"/>
```

to:

```xml
<postSubmissionAction class="org.openmrs.module.rwandaemr.htmlformentry.CreateQueueAction"/>
```

The new action implements `CustomFormSubmissionAction`, creates the queue entry,
then delegates to the existing appointment creation behavior.

### Service point configuration

Queue service point mappings are configured in:

```text
/rwandaemr/queue/queueServicePointConfig.page
```

The Service Requested question is configured by the global property
`registration.serviceRequestedConcept`, which defaults to concept ID `6702`.
It must be a coded concept. Each mapping connects one of its answers to an
OpenMRS location tagged as `Login Location`.

The queue code rejects mappings to locations that are not login locations. If a
registration encounter has no Service Requested obs, or neither the selected
encounter location nor the selected concept resolves to a login-location service
point, registration still succeeds and RwandaEMR logs why no queue entry was
created.

### Queue entry data

Queue entries are stored in `rwandaemr_queue_entry` and linked to:

- patient
- visit
- encounter
- encounter location
- current session/login location
- service point location
- Service Requested concept
- assigned provider, when called
- creator and audit fields

The concept-to-location mapping is stored in
`rwandaemr_queue_service_point_concept_map`. Status transitions are stored in
`rwandaemr_queue_status_history`.

### Statuses and priorities

Supported queue statuses:

- `WAITING`
- `CALLED`
- `IN_PROGRESS`
- `ON_HOLD`
- `TRANSFERRED`
- `COMPLETED`
- `CANCELLED`

Supported priorities:

- `NORMAL`
- `EMERGENCY`
- `ELDERLY`
- `PREGNANT`
- `CHILD`
- `DISABILITY`

Default registration queue entries use `WAITING` and `NORMAL`.

Queue ordering is:

1. `EMERGENCY`
2. other priority patients
3. `NORMAL`
4. arrival time within each priority group

### Duplicate prevention

Before creating a queue entry from registration, RwandaEMR checks whether the
patient already has an active queue entry from the preceding 24 hours for the
same service point location. Active statuses are:

- `WAITING`
- `CALLED`
- `IN_PROGRESS`
- `ON_HOLD`

If one exists, RwandaEMR logs the duplicate and returns the existing queue entry
instead of creating another one.

Live queue pages retain active entries for 24 hours from arrival, including
across midnight. Completed, transferred, and cancelled entries do not carry
over from the previous calendar day. Queue reports continue to use the selected
calendar date range.

The Queue Dashboard arrival-date filter shows either Today or Yesterday using
the entry arrival time. Unlike the live queue window, each option is restricted
to that calendar day. The patient-name filter matches partial given, middle,
and family names and can be combined with the date, status, and location filters.

For example, a patient added at 23:30 remains on the live queue the following
morning and can move to another internal service point. An internal service-point
change keeps the patient active; it is not treated as a transfer out.

### Location-based visibility

Provider queue visibility is based on the current OpenMRS session location. A
provider sees queue entries for the current login location when:

- the queue entry session location matches the current session location
- or the queue service point location matches the current session location
- or the encounter location matches the current session location

Users with `RwandaEMR Queue: View All Locations` can use the queue dashboard
location filter to view another login location. Providers without that privilege
are restricted to their current session location.

### Queue pages

RwandaEMR includes App Framework JSON and GSP pages for:

- Queue Dashboard: `/rwandaemr/queue/queueDashboard.page`
- My Location Queue: `/rwandaemr/queue/providerQueue.page`
- Service Point Queue View: `/rwandaemr/queue/servicePointQueue.page`
- Patient-facing Display Screen: `/rwandaemr/queue/queueDisplay.page`
- Queue Service Point Mappings:
  `/rwandaemr/queue/queueServicePointConfig.page`
- Queue Reports: `/rwandaemr/queue/queueReports.page`

The patient-facing display screen can be filtered by service point location and
shows now serving, next patients, and waiting patients.

### Privileges

The module defines these queue privileges:

- `RwandaEMR Queue: View`
- `RwandaEMR Queue: Manage`
- `RwandaEMR Queue: Call Patient`
- `RwandaEMR Queue: Transfer Patient`
- `RwandaEMR Queue: Configure`
- `RwandaEMR Queue: Reports`
- `RwandaEMR Queue: View All Locations`

Typical providers need `RwandaEMR Queue: View` and `RwandaEMR Queue: Call
Patient`. Queue administrators need `RwandaEMR Queue: Configure` for service
point mappings and `RwandaEMR Queue: View All Locations` for cross-location
visibility.

## Service-point appointments

The appointment capacity feature is separate from the legacy
`CreateAppointmentAction` workflow. It provides these pages:

- Appointment Timetable:
  `/rwandaemr/appointment/appointmentTimetable.page`
- Request Appointment:
  `/rwandaemr/appointment/requestAppointment.page`
- Appointment Dashboard:
  `/rwandaemr/appointment/appointmentDashboard.page`

The timetable defines a maximum number of patients for a Login Location service
point on a specific date. Saving the same service point and date updates the
existing timetable. Capacity cannot be reduced below the number of active
bookings, can only be changed for future dates, and a timetable can be closed to
stop new appointment requests.

The request page uses the standard OpenMRS patient search. After selecting a
patient and service point, it shows only open dates with remaining capacity.
Booking locks the selected timetable while checking capacity, and the database
also prevents duplicate patient bookings for the same timetable. New
appointments are confirmed automatically and can optionally record an active
OpenMRS program and an `Initial` or `Follow-Up` visit type. An active appointment
can be postponed to a later available date at the same service point.

The dashboard can be filtered by service point and date range. It displays
total capacity, booked patients, remaining places, and expandable patient lists.
The available actions for a confirmed appointment are `COMPLETED` and
`CANCELLED`. Legacy `REQUESTED` and `NO_SHOW` status values remain readable for
existing data.

Appointment privileges:

- `RwandaEMR Appointments: View`
- `RwandaEMR Appointments: Manage Schedules`
- `RwandaEMR Appointments: Book`
- `RwandaEMR Appointments: Manage`

### HTML Form Entry appointment booking tag

RwandaEMR registers the custom `<appointmentBooking/>` HTML Form Entry tag when
the module starts. Add the tag inside an HTML Form Entry form to let the user
book a future appointment for the patient associated with the form encounter:

```xml
<appointmentBooking/>
```

The widget first displays a service-point selector. After the user selects a
service point and clicks **Check Availability**, it displays the open future
schedules that still have capacity. Each date option includes the provider,
when one is assigned, and the number of remaining places.

The selected schedule is validated again when the form is submitted. This
prevents a stale page from booking a schedule that has since been closed or
filled. After HTML Form Entry successfully saves the encounter, RwandaEMR calls
the facility appointment service to create a confirmed booking for the
encounter patient. The same capacity checks, duplicate prevention, audit
metadata, and appointment confirmation SMS used by the Request Appointment page
also apply to bookings created by this tag.

Only one `<appointmentBooking/>` tag should be used in a form.

#### Tag attributes

The tag supports these optional attributes:

| Attribute | Default | Description |
| --- | --- | --- |
| `id` | `appointment-booking` | HTML identifier for the widget. It must start with a letter and contain only letters, numbers, `_`, or `-`. |
| `required` | `true` | Set to `false` to allow the form to be submitted without booking an appointment. |
| `notes` | empty | Notes saved on the appointment booking. |
| `servicePoints` | all appointment service points | Comma-separated service points to display. Each value may be an OpenMRS location ID, UUID, or exact location name. |

For example, this tag limits the dropdown to NCD Clinic and Oncology and saves
a note on the resulting booking:

```xml
<appointmentBooking
    id="followup-appointment"
    servicePoints="NCD Clinic,Oncology"
    notes="Booked from follow-up form"/>
```

Service-point identifiers can be mixed:

```xml
<appointmentBooking
    servicePoints="11,0d8a4f20-89e0-4d7f-b6c6-f46f493c8f20,Maternity"/>
```

Whitespace around comma-separated values is ignored, and duplicate values are
shown only once. If a configured ID, UUID, or name does not match an available
appointment service-point location, the form reports an `Unknown appointment
service point` design error instead of silently displaying an unintended list.

To make appointment selection optional:

```xml
<appointmentBooking required="false"/>
```

#### Configuration and permissions

The available dates come from schedules created on the Appointment Timetable
page. A service point with no open future schedule that has remaining capacity
will show a `No appointment dates with remaining capacity are available`
message.

The user entering the form needs `RwandaEMR Appointments: Book` or
`RwandaEMR Appointments: Manage` so the tag can read availability and create the
booking. For confirmation SMS delivery, RBC Messaging must be running and its
patient phone-number attribute and gateway settings must be configured.

### SMS hospital signature

Every SMS sent by RwandaEMR ends with the hospital name on a separate line. The
hospital is the parent location of the current user's login location. For
scheduled appointment reminders, where there may be no interactive login
location, RwandaEMR uses the parent of the appointment service point. The same
signature is applied to appointment confirmation, reminder and reschedule
messages, lab-result notifications, custom lab messages, and payment-clearance
messages. A hospital name already present at the end of a message is not added
again.
