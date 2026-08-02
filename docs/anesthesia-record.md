# Anesthesia Record Implementation

This module includes an MVP anesthesia monitoring dashboard at:

`/openmrs/rwandaemr/patient/anesthesiaRecord.page?patientId=<patientId>`

It follows the existing RwandaEMR pattern used by the Labour Care Guide:

- One `Anesthesia Record` encounter per saved section batch.
- A UUID operation identifier stored on every section encounter, keeping separate operations isolated.
- Separate forms for case details, vital signs, inhaled gas, medication/fluid events, and completion/recovery.
- Gas and medication forms can stage up to eight grouped rows before one save.
- Standard OpenMRS obs for vital signs.
- RwandaEMR-specific obs for anesthesia case details, agents, fluids, transfusion, remarks, and Aldrete score.
- A patient page that charts BP, pulse, respiratory rate, SpO2, and inhaled gas concentration over time.
- An operation selector that defaults to the latest operation and provides access to earlier operations.
- A dashboard fragment at `rwandaemr/patient/anesthesiaRecord` that can be added to a patient dashboard config.

## Metadata Required

Create these metadata items through the deployment's normal Initializer/concept dictionary process before using the form.

The RwandaEMR deployment configuration now includes the new anesthesia concepts in:

`/home/bniyonshuti/openmrs/openmrs/configuration/concepts/anesthesia.csv`

### Encounter Type

| Name | UUID |
| --- | --- |
| Anesthesia Record | `03c36ceb-d4c5-441f-aca5-d88fdd9b6964` |

### Standard Concepts Reused

| Field | UUID |
| --- | --- |
| Systolic blood pressure | `3ce934fa-26fe-102b-80cb-0017a47871b2` |
| Diastolic blood pressure | `3ce93694-26fe-102b-80cb-0017a47871b2` |
| Pulse | `3ce93824-26fe-102b-80cb-0017a47871b2` |
| Respiratory rate | `3ceb11f8-26fe-102b-80cb-0017a47871b2` |
| Temperature | `3ce939d2-26fe-102b-80cb-0017a47871b2` |
| Oxygen saturation | `3ce9401c-26fe-102b-80cb-0017a47871b2` |

### RwandaEMR Anesthesia Concepts

| Field | Datatype | UUID |
| --- | --- | --- |
| Anesthesia type | Coded | `a6e87d58-bf18-4d6b-a7f6-8a17db7452a7` |
| Urgency | Coded | `dedb6827-bd3f-4de1-9bc7-1cc63c8974e6` |
| Hospital service | Text | `21e73863-867c-4faa-8c4b-dc5b1c0319ac` |
| Surgical procedure | Text | `c3ecac79-8555-4588-a228-61c6d7f59892` |
| Surgeon | Text | `f1fe45a0-70aa-4819-9543-e98002c80401` |
| Anesthetist | Text | `54c9c4db-428b-4579-9023-84d8db65223a` |
| Premedication timing | Coded | `6ac01def-4790-4e05-98a3-4cc3da667005` |
| Ventilation mode | Coded | `7a81095b-c2d2-48bb-9b2f-81fe4050d791` |
| Medications administered | Text | `e2a92af0-e3a8-48c7-b73f-1bf5fb1b63ef` |
| Inhaled anesthetic agent | Text | `2b42694e-707c-4888-80a8-1f6325072678` |
| Inhaled anesthetic concentration | Numeric | `b6e47933-62b2-4929-b6f6-eaee3d5e9c8f` |
| Fluids / perfusion | Text | `074c4f8b-2f73-41c3-9b9b-9ee8826d4016` |
| Transfusion | Text | `4e56e745-a912-4734-bca7-62d78070b64d` |
| Airway technique | Coded | `0375a9d5-32d2-46aa-afd7-518bb7f66f28` |
| Position | Coded | `4080da65-d8dd-48a6-a90a-094866308d12` |
| Post-operative diagnosis | Text | `1c811683-d7f1-45f8-ac51-e7bf01344390` |
| Anesthesia remarks | Text | `8d3ea639-e9a4-43c7-b9bd-03556160b01d` |
| Aldrete score | Numeric | `c20d3924-d2ff-420b-b0e2-bb0b6d9d18b1` |
| Anesthesia operation identifier | Text | `c8641c43-5d65-4b6b-b6d3-2dbebf020ffc` |
| Anesthesia operation completed | Boolean | `20150cc7-a7ef-460e-bb23-c03ada212c89` |
| Anesthesia gas observation | Grouping set | `a04ca71f-cb71-4c9c-b05b-9397235699b7` |
| Anesthesia medication administration | Grouping set | `975ecb87-fe23-40f1-97ac-ad5e85356e60` |

## Form Installation

The form templates are:

| Section | Template | Runtime resource |
| --- | --- | --- |
| Case details | `docs/configuration/htmlforms/anesthesia-case-details.xml` | `file:configuration/htmlforms/anesthesia-case-details.xml` |
| BP, pulse, and other vital signs | `docs/configuration/htmlforms/anesthesia-vitals.xml` | `file:configuration/htmlforms/anesthesia-vitals.xml` |
| Inhaled gas | `docs/configuration/htmlforms/anesthesia-gas.xml` | `file:configuration/htmlforms/anesthesia-gas.xml` |
| Medications, fluids, and transfusion | `docs/configuration/htmlforms/anesthesia-medications.xml` | `file:configuration/htmlforms/anesthesia-medications.xml` |
| Completion and recovery | `docs/configuration/htmlforms/anesthesia-recovery.xml` | `file:configuration/htmlforms/anesthesia-recovery.xml` |

The active Kirehe copies are installed in:

`/home/bniyonshuti/openmrs/kirehe/configuration/htmlforms/`

Every form uses the same `Anesthesia Record` encounter type. The operation UUID is passed into HTML Form Entry and saved as a hidden required observation. The gas and medication forms provide add/remove row controls and save all completed rows in one encounter as separate obs groups. On submission, HTML Form Entry returns to the selected operation, which reloads only that operation's saved items into the corresponding chart or event log.

Starting **New Operation** generates a new operation UUID and opens Case Details. Saving Completion & Recovery records the operation-completed observation. Encounters created before operation tracking are retained under a read-only **Legacy anesthesia record** entry and are never combined with newly tracked operations.

## Dashboard Fragment

To show the compact card on a patient dashboard, add the fragment provider/path to the relevant dashboard configuration:

- Provider: `rwandaemr`
- Fragment: `patient/anesthesiaRecord`
- Config: pass the current `patient`

## Next Clinical Iteration

The medication observation is currently free text, so medication events are displayed as a chronological log. A future structured medication model could add drug, dose, unit, and route concepts while keeping the same timestamped encounter workflow.
