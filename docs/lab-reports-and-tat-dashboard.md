# Laboratory Reports and TaT Dashboard

This note proposes how RwandaEMR should replace the legacy
`laboratorymanagement/monthlyReport.form` report and add a laboratory
turnaround-time dashboard using data already captured by PIH Apps.

## Goals

- Give lab users a RwandaEMR page where they can request aggregate lab report
  data using filters instead of opening the old laboratorymanagement module.
- Preserve the old monthly report's main output: test/category totals with
  positive, negative, and total resulted counts.
- Add a clear TaT dashboard based on PIH Apps lab workflow timestamps.
- Keep the report logic reusable through service methods and REST endpoints, so
  the same data can power UI tables, charts, CSV, or Excel export.

## Existing Data Sources

### Legacy monthly report

The old report is implemented in `openmrs-module-laboratorymanagement-v2`:

- URL: `module/laboratorymanagement/monthlyReport.form`
- Controller: `ViewMonthlyReportController`
- JSP: `reportedMontly.jsp`
- Logic: `MappedLabExamManagement.getMappedExamsByLabTypeBetweenTwoDates`

The old row shape is:

```text
category, test, positive_count, negative_count, total_resulted_count, concept_id
```

For coded results, the old module defines:

- Negative: `obs.value_coded = 664`
- Positive: `obs.value_coded IS NOT NULL AND obs.value_coded <> 664`

For numeric/text/date results, positive and negative are blank and only total is
shown.

Important limitation: the old JSP contains a Location filter, but the controller
and SQL do not use `locationId`. The new RwandaEMR implementation should fix
this.

### PIH Apps lab workflow

PIH Apps already provides the lab metadata needed for reporting and TaT:

- Lab orders are `TestOrder` rows in `orders`.
- Result obs are linked to orders through `obs.order_id`.
- Order date is available on `orders.date_activated`.
- Order location can be taken from the order encounter location.
- Result date can be taken from the result obs or the configured result-date
  obs.
- Specimen metadata is stored as obs in the fulfiller/laboratory encounter.

Relevant PIH Apps configuration:

| Meaning | Configuration |
|---|---|
| Lab order type | `pihapps.labs.labOrderType`, with legacy fallbacks |
| Lab orderable concept set | `orderentryowa.labOrderablesConceptSet` |
| Result category concept set | `pihapps.labs.labResultCategoriesConceptSet` |
| Specimen received date | `pihapps.labs.specimenReceivedDateConcept`, default `PIH:21057` |
| Results date | `pihapps.labs.resultsDateConcept`, default `PIH:10783` |
| Lab identifier | `pihapps.labs.labIdentifierConcept`, default `CIEL:162086` |
| Test location | `pihapps.labs.locationOfLaboratory` |

## Proposed RwandaEMR Pages

### Aggregate Lab Report

Suggested route:

```text
/openmrs/rwandaemr/labReports.page
```

Filters:

- Date range, required
- Location/facility
- Lab category
- Lab test
- Result type: all, positive, negative, resulted
- Optional grouping period: none, day, week, month

Default output:

```text
Category | Test | Positive | Negative | Total resulted
```

Actions:

- Run report
- Export CSV
- Export Excel
- Drill into a count to see matching patient/order/result rows, subject to
  privilege checks

### Laboratory TaT Dashboard

Suggested route:

```text
/openmrs/rwandaemr/labTurnaroundTime.page
```

Filters:

- Date range
- Location/facility
- Lab category
- Lab test
- Urgency
- Status

Dashboard widgets:

- KPI cards: total completed, median TaT, average TaT, 90th percentile TaT,
  percent within target, delayed count
- Trend chart: median TaT over time
- Bar chart: median TaT by test
- Bar chart: median TaT by location
- Stage chart: where time is spent
- Detail table: delayed orders/specimens

## Proposed REST API

Use RwandaEMR endpoints because these are Rwanda-specific reports, but reuse PIH
Apps configuration and data conventions.

```text
GET /ws/rest/v1/rwandaemr/labReports/aggregate
GET /ws/rest/v1/rwandaemr/labReports/aggregate/export
GET /ws/rest/v1/rwandaemr/labReports/detail
GET /ws/rest/v1/rwandaemr/labReports/tat/summary
GET /ws/rest/v1/rwandaemr/labReports/tat/trends
GET /ws/rest/v1/rwandaemr/labReports/tat/details
```

Recommended service package:

```text
api/src/main/java/org/openmrs/module/rwandaemr/labreports/
```

Suggested classes:

- `LabReportService`
- `LabReportServiceImpl`
- `LabReportSearchCriteria`
- `AggregateLabReportRow`
- `LabTurnaroundTimeRow`
- `LabTurnaroundTimeSummary`

## Aggregate Report SQL Skeleton

This is the core query shape. The implementation should resolve configured
concepts from `LabOrderConfig` or equivalent service code rather than hardcoding
the full concept list in SQL.

```sql
select
    category_name.name as category,
    test_name.name as test,
    test.concept_id as test_concept_id,
    sum(case
        when result.value_coded is not null
         and result.value_coded <> :negativeConceptId
        then 1 else 0
    end) as positive_count,
    sum(case
        when result.value_coded = :negativeConceptId
        then 1 else 0
    end) as negative_count,
    count(result.obs_id) as total_resulted_count
from obs result
inner join concept test
    on test.concept_id = result.concept_id
inner join concept_name test_name
    on test_name.concept_id = test.concept_id
   and test_name.locale_preferred = 1
inner join concept_set category_member
    on category_member.concept_id = test.concept_id
inner join concept category
    on category.concept_id = category_member.concept_set
inner join concept_name category_name
    on category_name.concept_id = category.concept_id
   and category_name.locale_preferred = 1
left join orders o
    on o.order_id = result.order_id
left join encounter order_encounter
    on order_encounter.encounter_id = o.encounter_id
where result.voided = 0
  and result.obs_datetime >= :startDate
  and result.obs_datetime < date_add(:endDate, interval 1 day)
  and (
      result.value_coded is not null
      or result.value_numeric is not null
      or result.value_text is not null
      or result.value_datetime is not null
  )
  and (:locationId is null or order_encounter.location_id = :locationId)
  and (:categoryConceptId is null or category.concept_id = :categoryConceptId)
  and (:testConceptId is null or test.concept_id = :testConceptId)
group by
    category_name.name,
    test_name.name,
    test.concept_id
order by
    category_name.name,
    test_name.name;
```

Notes:

- This handles direct category -> test concept sets. If Rwanda concept metadata
  still uses category -> group -> test nesting, the implementation should expand
  concept sets recursively in Java and pass the leaf test concept ids into the
  query.
- Location can be taken from the order encounter. If a result has no linked
  order, location can optionally fall back to `result.location_id` or
  `result.encounter_id -> encounter.location_id`.
- `:negativeConceptId` should be configurable. The old module used concept id
  `664`; the new implementation should resolve it by UUID/reference where
  possible.

## TaT Data Model

Recommended timestamp mapping:

| Stage | Timestamp source |
|---|---|
| Ordered | `orders.date_activated` |
| Specimen received | Obs with concept `pihapps.labs.specimenReceivedDateConcept` linked to the fulfiller encounter |
| Result entered | Earliest resulted obs linked to the order, or obs with concept `pihapps.labs.resultsDateConcept` |
| Completed | `orders.fulfiller_status = COMPLETED`; use result timestamp unless a better completion audit timestamp is available |

Potential TaT measures:

- `order_to_received_hours`
- `received_to_result_hours`
- `order_to_result_hours`

If later the workflow captures specimen collection separately from specimen
received, add:

- `order_to_collection_hours`
- `collection_to_received_hours`

## TaT SQL Skeleton

```sql
select
    o.order_id,
    o.uuid as order_uuid,
    o.date_activated as ordered_at,
    order_encounter.location_id as order_location_id,
    test_name.name as test,
    o.urgency,
    specimen_received.value_datetime as specimen_received_at,
    coalesce(results_date.value_datetime, min(result.obs_datetime)) as resulted_at,
    timestampdiff(
        minute,
        o.date_activated,
        specimen_received.value_datetime
    ) / 60.0 as order_to_received_hours,
    timestampdiff(
        minute,
        specimen_received.value_datetime,
        coalesce(results_date.value_datetime, min(result.obs_datetime))
    ) / 60.0 as received_to_result_hours,
    timestampdiff(
        minute,
        o.date_activated,
        coalesce(results_date.value_datetime, min(result.obs_datetime))
    ) / 60.0 as order_to_result_hours
from orders o
inner join concept_name test_name
    on test_name.concept_id = o.concept_id
   and test_name.locale_preferred = 1
left join encounter order_encounter
    on order_encounter.encounter_id = o.encounter_id
left join obs result
    on result.order_id = o.order_id
   and result.voided = 0
   and (
      result.value_coded is not null
      or result.value_numeric is not null
      or result.value_text is not null
      or result.value_datetime is not null
   )
left join obs specimen_received
    on specimen_received.encounter_id = result.encounter_id
   and specimen_received.concept_id = :specimenReceivedDateConceptId
   and specimen_received.voided = 0
left join obs results_date
    on results_date.encounter_id = result.encounter_id
   and results_date.concept_id = :resultsDateConceptId
   and results_date.voided = 0
where o.voided = 0
  and o.date_activated >= :startDate
  and o.date_activated < date_add(:endDate, interval 1 day)
  and (:locationId is null or order_encounter.location_id = :locationId)
  and (:testConceptId is null or o.concept_id = :testConceptId)
group by
    o.order_id,
    o.uuid,
    o.date_activated,
    order_encounter.location_id,
    test_name.name,
    o.urgency,
    specimen_received.value_datetime,
    results_date.value_datetime;
```

For production code, prefer a service method that first resolves the fulfiller
encounter per order using the PIH Apps linkage convention (`obs.order_id` and
configured linking concepts). This avoids assuming all metadata obs share the
same encounter as a result obs, especially for orders that have specimen
metadata but no result yet.

## Implementation Plan

1. Add API models and `LabReportService` in RwandaEMR.
2. Implement aggregate report query with date, location, category, and test
   filters.
3. Add REST endpoint for the aggregate report and CSV export.
4. Build the Aggregate Lab Report page in RwandaEMR.
5. Implement TaT row query from order + PIH Apps specimen/result timestamps.
6. Add REST endpoints for TaT summary, trends, and details.
7. Build the TaT dashboard page with KPI cards, charts, and delayed order table.
8. Validate numbers against known monthly reports and a sample of individual
   lab orders.

## Open Decisions

- Should positive/negative keep the old `664` logic, or should each test define
  explicit positive and negative answer concepts?
- Should date filtering for the aggregate report use result obs date, results
  date obs, order date, or allow the user to choose?
- Should location filter use order encounter location, lab location obs, result
  encounter location, or a fallback chain?
- What are the TaT targets by test, category, or urgency?
- Should drill-down expose patient identifiers to all lab users, or require an
  additional privilege?
