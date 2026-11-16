# Deployment Notes - RwandaEMR 3.2.1-SNAPSHOT

## Scope

Deployment notes for HIE migration and iremboPay migration into `openmrs-module-rwandaemr-3-x` (`3.2.1-SNAPSHOT`).

## Deployed Artifact

- Module artifact: `rwandaemr-3.2.1-SNAPSHOT.omod`
- Verified deployed path on test server: `/var/lib/OpenMRS/modules/rwandaemr-3.2.1-SNAPSHOT.omod`

## Key Migrated Features Confirmed in Deployed OMOD

- iremboPay REST and UI:
  - `IremboPayRestController.class`
  - `IremboPaySectionFragmentController.class`
  - `IremboPayStatusSectionFragmentController.class`
  - `web/module/fragments/patient/iremboPaySection.gsp`
  - `web/module/fragments/patient/iremboPayStatusSection.gsp`
  - `web/module/pages/patient/iremboPayStatusSection.gsp`
- HIE scheduler/logging hardening:
  - scheduled task start/end/duration logs
  - queue processing/timing/threshold support

## Runtime Configuration Verified

Global properties on test server:

- `rwandaemr.hie.connectTimeoutMs=10000`
- `rwandaemr.hie.socketTimeoutMs=30000`
- `rwandaemr.hie.connectionRequestTimeoutMs=10000`
- `rwandaemr.hie.shrEncounterFetchTimeoutMs=30000`
- `rwandaemr.hie.citizenFetchTimeoutMs=30000`
- `rwandaemr.hie.queueWarnThreshold=1000` (default behavior in code/config)
- `rwandaemr.hie.queueErrorThreshold=5000` (default behavior in code/config)

## Runtime Validation Evidence

- Tomcat/OpenMRS process active on test server.
- Scheduler activity observed in logs:
  - repeated `Starting scheduled task: ...`
  - repeated `Completed scheduled task: ... in ... ms`
- No timeout symptoms in checked runtime window:
  - no `SocketTimeoutException`
  - no `Read timed out`

## Smoke/Performance Snapshot (Server-Local Probe)

20 requests per endpoint:

- `/openmrs/login.htm`: `200`, avg `0.226s`, p95 `0.245s`
- `/openmrs/`: `302`, avg `0.012s`, p95 `0.015s`
- `/openmrs/ws/rest/v1/`: `302`, avg `0.013s`, p95 `0.015s`
- `/openmrs/ws/rest/v1/rwandaemr/irembopay/status?...`: `302`, avg `0.012s`, p95 `0.014s`

## Operational Notes

- Some unrelated modules log non-critical `ERROR`-level messages; these should be cleaned up separately to reduce log noise.
- CI pipeline validation remains a separate release gate.
- Functional QA/UAT sign-off remains a separate release gate.
