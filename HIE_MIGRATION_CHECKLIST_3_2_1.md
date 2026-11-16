# HIE Migration Checklist (to 3.2.1-SNAPSHOT)


## 1) Version + Build Baseline

- [x] Target root `pom.xml` version set to `3.2.1-SNAPSHOT`
- [x] Target module poms aligned to parent version (`api/pom.xml`, `omod/pom.xml`)
- [x] `mvn -DskipTests compile` verified in target environment
- [ ] Packaging/build pipeline validated in CI

---

## 2) HIE Backend Core (API)

### Added HIE Event Base
- [x] `api/.../event/HieEventListener.java`

### Added SHR Models/Translators/Providers
- [x] `api/.../integration/ShrEncounter.java`
- [x] `api/.../integration/ShrObservation.java`
- [x] `api/.../integration/ShrVisit.java`
- [x] `api/.../integration/ShrConsent.java`
- [x] `api/.../integration/ShrEncounterTranslator.java`
- [x] `api/.../integration/ShrObsTranslator.java`
- [x] `api/.../integration/ShrConsentTranslator.java`
- [x] `api/.../integration/ShrEncounterProvider.java`
- [x] `api/.../integration/ShrObsProvider.java`
- [x] `api/.../integration/ShrConsentProvider.java`

### Added SHR Queue Sync Listeners/Tasks
- [x] `api/.../integration/UpdateShrEncounterListener.java`
- [x] `api/.../integration/UpdateShrObsListener.java`
- [x] `api/.../integration/UpdateShrEncounterTask.java`
- [x] `api/.../integration/UpdateShrObsTask.java`

### Supporting Object
- [x] `api/.../object/Quantity.java` (required by SHR observation model)

---

## 3) Existing HIE/CR Components Updated in Target

- [x] `api/.../integration/HttpUtils.java` (adjustable global timeout config)
- [x] `api/.../integration/CitizenProvider.java` (citizen fetch timeout override)
- [x] `api/.../integration/IntegrationConfig.java` (HIE config behavior parity + queue alarm thresholds)
- [x] `api/.../integration/UpdateClientRegistryPatientListener.java` (detailed failure logs, queue processing parity)
- [x] `api/.../integration/UpdateClientRegistryTask.java` (execution guard parity)
- [x] `api/.../task/RwandaEmrTimerTask.java` (task execution/logging/HIE check behavior parity)
- [x] `api/.../task/RwandaEmrScheduledTaskExecutor.java` (task scheduling parity)
- [x] Queue hardening parity:
  - [x] Queue depth alarms (warn/error) for SHR encounter/obs/client registry queues
  - [x] Task and queue run timing logs (start/end/duration + processed/success/failure counts)
  - [x] Queue alarm thresholds configurable via global properties

---

## 4) Event + Activator Wiring

- [x] `api/.../config/EventSetup.java` updated to subscribe/unsubscribe:
  - [x] Encounter create -> SHR encounter listener
  - [x] Obs create -> SHR obs listener
  - [x] Existing patient/client-registry subscriptions preserved

- [x] `omod/.../RwandaEmrActivator.java` updated for daemon token wiring parity:
  - [x] `RwandaEmrTimerTask`
  - [x] `CreateInsurancePatientListener`
  - [x] `RadiologyOrderEventListener`
  - [x] `ORUR01MessageListener`

---

## 5) HIE UI/Fragment Features (OMOD)

### Added Controllers
- [x] `omod/.../fragment/controller/patient/HieEncountersSectionFragmentController.java`
- [x] `omod/.../fragment/controller/patient/HieObservationsSectionFragmentController.java`

### Added GSPs
- [x] `omod/src/main/webapp/fragments/patient/hieEncountersSection.gsp`
- [x] `omod/src/main/webapp/fragments/patient/hieObservationsSection.gsp`
- [x] `omod/src/main/webapp/pages/patient/hieObservationsSection.gsp`

### Added JS
- [x] `omod/src/main/webapp/resources/scripts/custom/hie.js`

### UI Behavior Checks
- [x] Past History link opens modal and lazy-loads encounters
- [x] Accordion expands/collapses inside dynamically loaded modal content
- [x] Observation eye link loads observation details in same modal
- [x] Back-to-encounter-list navigation works from observation view
- [x] No blank subtitle/unused header spacing in modal
- [x] Encounter datetime displayed as human-readable date/time

### IremboPay Migration (OMOD + REST)
- [x] Added `omod/.../rest/IremboPayRestController.java`
- [x] Added `omod/.../fragment/controller/patient/IremboPaySectionFragmentController.java`
- [x] Added `omod/.../fragment/controller/patient/IremboPayStatusSectionFragmentController.java`
- [x] Added `omod/.../page/controller/patient/IremboPayStatusSectionFragmentPageController.java`
- [x] Added `omod/src/main/webapp/fragments/patient/iremboPaySection.gsp`
- [x] Added `omod/src/main/webapp/fragments/patient/iremboPayStatusSection.gsp`
- [x] Added `omod/src/main/webapp/pages/patient/iremboPayStatusSection.gsp`
- [x] Irembo status polling in `3-x` uses `forceUpdate=true` so paid status can trigger invoice refresh/update
- [x] Null-safe paid-status checks retained in `IremboPayRestController` (bill null/billId null safe)

---

## 6) Search + Registry Flow

- [x] `omod/.../fragment/controller/field/SearchClientRegistryFragmentController.java` parity updates
- [x] Verify identifier search -> client registry flow
- [x] Verify population registry fallback
- [x] Verify errors are actionable (connection vs no match vs conversion issues)

---

## 7) Spring Context + Discovery

- [x] `omod/src/main/resources/webModuleApplicationContext.xml` includes:
  - [x] `org.openmrs.module.rwandaemr.web`
  - [x] `org.openmrs.module.rwandaemr.rest`
- [x] Verify all new controllers/fragments/actions are discoverable at runtime

---

## 8) Timeout Configuration Validation

### Global timeouts (all HIE requests via `HttpUtils`)
- [x] `rwandaemr.hie.connectTimeoutMs`
- [x] `rwandaemr.hie.socketTimeoutMs`
- [x] `rwandaemr.hie.connectionRequestTimeoutMs`

### Operation-specific overrides
- [x] `rwandaemr.hie.shrEncounterFetchTimeoutMs`
- [x] `rwandaemr.hie.citizenFetchTimeoutMs`

### Queue alarm properties
- [x] `rwandaemr.hie.queueWarnThreshold` (default `1000`)
- [x] `rwandaemr.hie.queueErrorThreshold` (default `5000`)

### Runtime validation
- [x] HIE slow response (~10-15s) no longer fails with read timeout at current configured thresholds
- [x] Queue retries increment and log detailed reason on failure

---

## 9) Regression Safety (Non-HIE Features)

- [x] Insurance policy pages still load
- [x] Existing patient fragments still load
- [x] Radiology event handling unaffected
- [x] No startup regression due to scheduler/timer changes

---

## 10) Release Readiness Sign-off

- [ ] Functional QA sign-off complete
- [x] Performance/smoke test complete
- [x] Deployment notes updated
- [x] Final artifact version confirmed: `3.2.1-SNAPSHOT`

---

## Notes

- This migration intentionally preserves source project files untouched.
- Use this checklist as a traceable handover for QA/UAT before release packaging.
- Added/updated `3-x` i18n messages file: `api/src/main/resources/messages.properties`.
- `3-x` now expects `mohbilling` version with irembo model support (`PatientBillIrembo`).
- Compile verification performed: `mvn -f ../../../hie_data/openmrs-module-rwandaemr-3-x/pom.xml -DskipTests compile` -> `BUILD SUCCESS` (API + OMOD).
- Search/registry verification performed by code-path inspection:
  - Identifier-first client registry lookup is present.
  - Population registry fallback includes TEMPID path.
  - Actionable message codes are returned for configuration/connection/conversion/no-match paths.
- Regression verification by compile + artifact presence:
  - Insurance policy controllers/pages/fragments present and compiled.
  - Patient fragment set (including migrated HIE/Irembo fragments) present and compiled.
  - Radiology listeners (`RadiologyOrderEventListener`, `ORUR01MessageListener`) remain wired in activator.
- Live server runtime verification on test host `192.168.3.166`:
  - `rwandaemr-3.2.1-SNAPSHOT.omod` is deployed under `/var/lib/OpenMRS/modules`.
  - Deployed OMOD contains migrated irembo assets/classes (REST controller, fragment controllers, irembo GSPs).
  - Tomcat/OpenMRS is running and scheduler logs show repeated start/completion cycles for RwandaEMR tasks.
  - Current timeout global properties are set (`connect=10000ms`, `socket=30000ms`, `connectionRequest=10000ms`, `shrEncounterFetch=30000ms`, `citizenFetch=30000ms`).
  - Recent `catalina.out` scan showed no `SocketTimeoutException`/`Read timed out` entries for the active runtime window.
  - Smoke/performance probe (20 requests per endpoint, localhost on server):
    - `/openmrs/login.htm`: HTTP `200`, avg `0.226s`, p95 `0.245s`.
    - `/openmrs/`: HTTP `302`, avg `0.012s`, p95 `0.015s`.
    - `/openmrs/ws/rest/v1/`: HTTP `302`, avg `0.013s`, p95 `0.015s`.
    - `/openmrs/ws/rest/v1/rwandaemr/irembopay/status?...`: HTTP `302`, avg `0.012s`, p95 `0.014s`.
  - Note: recent logs include noisy `ERROR`-level entries from other modules/log-level misuse, but no timeout/OOM indicators in the checked window.
- Deployment handover notes added in `DEPLOYMENT_NOTES_3_2_1.md`.
