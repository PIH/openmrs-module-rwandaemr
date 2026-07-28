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
patient already has an active queue entry today for the same service point
location. Active statuses are:

- `WAITING`
- `CALLED`
- `IN_PROGRESS`
- `ON_HOLD`

If one exists, RwandaEMR logs the duplicate and returns the existing queue entry
instead of creating another one.

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
