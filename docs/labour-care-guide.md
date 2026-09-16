# Labour Care Guide Fresh Implementation Plan

This is a clean plan for implementing the Rwanda Labour Care Guide from the newly shared metadata exports and PDF. It intentionally ignores the previous module scaffold and the existing local configuration copies.

## Source Forms

The requested implementation baseline is the six exported form packages inside `/home/bniyonshuti/Downloads/labour_guide.zip`.

| Package | HtmlForm UUID | Form UUID | Form name | Encounter type |
| --- | --- | --- | --- | --- |
| `parto_hourly_form` | `322d941a-9500-414b-9fed-479a590d2f8d` | `fa87ef0b-ac54-413d-be2d-4595e4e7ea79` | `partogram_hourly_assessment` | `Findings` `76162246-15d8-43b0-9666-5884ad1e2be4` |
| `parto_labour_summary` | `3765901c-492d-4dcb-b8b5-b2b3cf967e23` | `c9955720-0b2b-4c4f-b361-fc29901fe1ea` | `Labour Summary Form` | `Partogram` `d0d3777d-a40e-4e5d-a116-0163aab5fd6c` |
| `parto_newborn` | `e14a24f0-9385-4a34-9997-67ccd8a45173` | `41e9ff3a-cb42-4182-a3db-5df44d7671cb` | `New Born From` | `Partogram` `d0d3777d-a40e-4e5d-a116-0163aab5fd6c` |
| `parto_pph_diagnosis` | `4d96e5a7-ddf0-41a0-98fb-92e4ec080f2f` | `4a1686ea-6c69-4483-a1d6-47dbc21016b4` | `pph_diagnosis` | `Partogram` `d0d3777d-a40e-4e5d-a116-0163aab5fd6c` |
| `parto_post_PARTUMs`, woman | `9715242c-331a-4b3e-8834-e6ff5a6d9982` | `63bb4384-f3b0-424b-9b2f-5ed1c0140c74` | `WHO LCG Postpartum and Postnatal ollowup Women` | `Partogram` `d0d3777d-a40e-4e5d-a116-0163aab5fd6c` |
| `parto_post_PARTUMs`, newborn | `c958cb7d-3d09-4a79-9899-4814b362f041` | `0de0f0bc-2f34-48d4-8ae2-13d175defebd` | `WHO LCG Postpartum and Postnatal ollowup born` | embedded/no direct encounter type in export |
| `Parto_discharge` | `9c017cd0-3680-4596-9906-cb9daad1dfa0` | `e2b20dbe-6c2d-4e98-b3e3-3a4e9333ab9e` | `parto Discharge form` | `Partogram` `d0d3777d-a40e-4e5d-a116-0163aab5fd6c` |

## Clinical Coverage

The exports cover these parts of the PDF:

- `parto_hourly_form`: repeated labour monitoring for supportive care, baby, woman, labour progress, medication, assessment, plan, and initials.
- `parto_labour_summary`: delivery mode, provider, uterotonic/AMTSL, placenta delivery, blood loss, perineum, complications.
- `parto_newborn`: newborn outcome, cord cut time, APGAR, resuscitation, sex, weight, head circumference, height, skin-to-skin, breastfeeding, malformations, vaccines/treatments.
- `parto_pph_diagnosis`: PPH diagnosis date/time, diagnostic criterion, bleeding cause, and interventions.
- `parto_post_PARTUMs`: postpartum woman follow-up rows and postnatal newborn follow-up rows.
- `Parto_discharge`: woman/baby discharge readiness, vitals, HIV/syphilis/haemoglobin, danger signs, breastfeeding, screening, prevention, treatment, referral, and advice.

## Missing Or Risky Data

The six forms do not fully cover the PDF admission/header section:

- ID, name, gravida, para, abortions, stillbirths, number of live children.
- Risk factors.
- Labour onset.
- Active labour diagnosis date/time.
- Ruptured membranes date/time.
- The 12-hour LCG sheet boundary and continuation on a new LCG if labour extends beyond 12 hours.

Recommended action: add a small `parto_labour_admission` form or reuse a verified maternity admission form only after mapping those concepts. This form should start the labour episode and store the episode identifier.

The export also needs metadata hardening before module code depends on it:

- `parto_post_PARTUMs` contains two HtmlForms; the newborn follow-up form has no direct encounter type in its own embedded export node. Decide whether it is always embedded under the woman follow-up or should become a separately entered form with an encounter type.
- Most forms use `Partogram`, but hourly monitoring uses `Findings`. Decide whether to preserve that or standardize on one Labour Care Guide encounter type.
- Some concept names are form-specific and typo-prone, for example `WHO LCG Postpartum and Postnatal ollowup Women`. Keep UUIDs stable, but normalize display names where possible.

## Recommended Module Design

Use the same structural idea as anesthesia, but with labour episodes instead of operations:

1. Add a `Labour Care Guide episode identifier` text concept.
2. Add a hidden required episode-id observation to all six forms and the proposed admission/header form.
3. Build `/rwandaemr/patient/labourCareGuide.page?patientId=...&episodeId=...`.
4. Group all LCG encounters by episode id.
5. Show an episode selector, defaulting to the latest active episode.
6. Provide section actions for each source form:
   - Admission/Header
   - Hourly Monitoring
   - Labour Summary
   - Newborn
   - PPH Diagnosis
   - Postpartum/Postnatal Follow-up
   - Discharge
7. Keep encounters without an episode id in a read-only legacy bucket.

## Dashboard Behavior

The full page should prioritize the paper LCG workflow rather than only charts:

- Header band: episode status, admission details, last observation time, elapsed hours.
- Alert strip: current active alerts from the latest monitoring/discharge values.
- Labour grid: chronological rows from `parto_hourly_form`, with columns matching the PDF sections.
- Labour progress chart: cervical dilation and descent over time.
- Maternal/fetal vitals charts: FHR, pulse, respirations, BP, temperature, contractions.
- Outcome panels: labour summary, newborn summary, PPH diagnosis, postpartum/postnatal follow-up, discharge.

Alert rules from the PDF should be implemented centrally:

- FHR `<110` or `>=160`.
- Pulse `<60` or `>=120`.
- Respirations `<12` or `>=20`.
- Systolic BP `<80` or `>=140`.
- Diastolic BP `>=90`.
- Temperature `<35.0` or `>=37.5`.
- Urine protein `P++`, acetone `A++`, or volume `<30 mL/hr`.
- Contractions `<=2` or `>5` per 10 minutes.
- Contraction duration `<20` or `>60` seconds.
- Caput `+++` or moulding `+++`.
- Blood loss `>=500 mL`.
- Cervical dilation progress lag based on the PDF thresholds.

## Implementation Phases

1. Metadata preparation: extract the six HtmlForm XML definitions, install/import concepts and forms, and add the missing episode-id concept.
2. Admission gap: add a small admission/header form or confirm an existing maternity form maps the missing PDF fields.
3. Module shell: add page controller, fragment controller, GSP page, and messages.
4. Data extraction: build a Labour Care Guide support class for encounter grouping, obs extraction, and alert evaluation.
5. UI: render the full LCG table, charts, outcome summaries, and section action buttons.
6. Tests: cover episode grouping, alert thresholds, section completion state, and legacy records without episode ids.

## Current Fresh Build State

The module now has a fresh Labour Care Guide page and fragment based on the exported `parto-*` forms. Each form has been extracted as a file-backed HTML Form Entry resource under `docs/configuration/htmlforms/` and receives a hidden `labourEpisodeIdObs` value, mirroring the anesthesia operation-id pattern.

For SDK testing, the same files were copied to `/home/bniyonshuti/openmrs/kirehe/configuration/htmlforms/`, the rebuilt `rwandaemr-4.1.0-SNAPSHOT.omod` was copied to `/home/bniyonshuti/openmrs/kirehe/modules/`, and concept/encounter type stubs were copied under `configuration/concepts/` and `configuration/encountertypes/`.

The generated `docs/labour-care-guide-exported-concepts-review.csv` concept file is a bootstrap conversion of the shared metadata export, not a production dictionary review. It is intentionally outside the loadable `configuration/concepts` tree and should be checked against the Rwanda dictionary before final rollout.
