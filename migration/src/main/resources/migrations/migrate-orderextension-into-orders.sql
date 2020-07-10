/*
 These migrations pull data out of the orderextension tables and into the core order tables
 Mostly things migrate appropriately, with the exception of data around cycles.  For this we have had to be a bit creative for now
 The way this was originally designed out is to support these via:
 a) order set attributes, allowing storage of "cyclical" and "cycle_length" + "cycle_length_units" directly on the order_set
 b) order group attributes, allowing storage of "cycle_number" directly on the order group
 However, these are not available as of OpenMRS 2.3.  As a workaround below, we do the following:
 a) add cyclical and cycle_length to the template for every order set member within an order group (consuming code can pull first/any from this)
 b) add cycle_number for a given order group to every order within the group, using an unused attribute of order (eg. order_reason_non_coded)
 The goal is to allow us to move away from the need for a custom data model in support of orderextension, and an easier progressive migration
 path in the future as more appropriate places to store this data comes online.
 This approach is still tentative and under review.
*/

/*
 Order Set maps pretty much 1:1, except for cyclical and cycle_number.  This is added to each member as a workaround.
 */
insert into order_set (
    order_set_id, uuid, name, description, operator, category,
    creator, date_created, changed_by, date_changed,
    retired, retired_by, date_retired, retire_reason
)
select  id, uuid, name, description, operator, indication,
        creator, date_created, changed_by, date_changed,
        retired, retired_by, date_retired, retire_reason
from    orderextension_order_set
;

/*
 Order Set Member maps from explicit columns to a more generic template.  We implement this as a simple JSON structure.
 This generic template allows us to store information about cycles that the set is unable to represent otherwise
 There are several fields not migrated as they are not used (or only have the same value in every case, in the
 orderextension_order_set_member table: comment, selected, template, nested_order_set_id,
 member_type (always org.openmrs.module.orderextension.DrugOrderSetMember), as_needed (always 0)
 */
select order_type_id into @drug_order_type from order_type where name = 'Drug Order';
insert into order_set_member (
    order_set_member_id, uuid, order_set_id, sequence_number,
    order_type, concept_id, order_template_type, order_template,
    creator, date_created, changed_by, date_changed,
    retired, retired_by, date_retired, retire_reason
)
select  m.id, m.uuid, m.order_set_id, m.sort_weight,
        @drug_order_type, m.concept_id, 'DRUG_ORDER',
        concat('{',
               '"title": "', m.title, '", ',
               '"indication": ', m.indication, ', ',
               '"drug_id": ', m.drug_id, ', ',
               '"dose": ', m.dose, ', ',
               '"units": "', m.units, '", ',
               '"route": ', m.route, ', ',
               '"frequency": "', m.frequency, '", ',
               '"relative_start_day": ', m.relative_start_day, ', ',
               '"length_days": ', m.length_days, ', ',
               '"instructions": "', m.instructions, '", ',
               '"administration_instructions": "', m.administration_instructions, '", ',
               '"cyclical": "', s.cyclical, '", ',
               '"cycle_length": "', s.cycle_length,
               '}'),
        s.creator, s.date_created, s.changed_by, s.date_changed,
        s.retired, s.retired_by, s.date_retired, s.retire_reason
from    orderextension_order_set_member m
            inner join orderextension_order_set s on s.id = m.order_set_id
;

/*
 Order Group is little tricky as the orderextension representation does not link to patient or encounter explicitly,
 so these need to be pulled off of the underlying orders referenced to the group.
 We don't migrate "group_type" as it is always the same - org.openmrs.module.orderextension.DrugRegimen
 NOTE:  There are ~100 orderextension_order_group entries that can't migrate as they do not have any orders associated with them.
 (eg. patient_id and encounter_id are thus null). All of these were created between 10/2012 and 2/2013
 */
alter table orderextension_order_group add patient_id int;
alter table orderextension_order_group add encounter_id int;

update      orderextension_order_group g
            inner join  orderextension_order oo on g.id = oo.group_id
            inner join  orders o on oo.order_id = o.order_id
set         g.patient_id = o.patient_id,
            g.encounter_id = o.encounter_id
;

insert into order_group (
    order_group_id, uuid, order_set_id, patient_id, encounter_id,
    creator, date_created, changed_by, date_changed,
    voided, voided_by, date_voided, void_reason
)
select      g.id, g.uuid, g.order_set_id, g.patient_id, g.encounter_id,
            g.creator, g.date_created, g.creator, g.date_created,
            g.voided, g.voided_by, g.date_voided, g.void_reason
from        orderextension_order_group g
where       g.patient_id is not null
;

/**
  Many of the fields we need to add to extended order are now available on order in 2.x.  These migrate across
 */
update      orders o
inner join  orderextension_order oo on o.order_id = oo.order_id
set         o.order_group_id = oo.group_id,
            o.sort_weight = oo.order_index,
            o.order_reason = oo.indication
;

/**
  Many of the fields we need to add to extended order are now available on drug_order in 2.x.  These migrate across
 */
update      drug_order o
inner join  orderextension_order oo on o.order_id = oo.order_id
set         o.route = oo.route,
            o.dosing_instructions = oo.administration_instructions;
;

/**
  The one field on orderextension_order_group without a good analog is cycle_number.
  Ideally we'd associate this more concretely and correctly (eg. in an order group attribute) but there is no
  mechanism in place yet that will allow this.  So we migrate this into the order_reason_non_coded of all orders within
  the order group that this cycle number applies to.  We can pull this out from here in code as needed.
 */
update      orders o
inner join  orderextension_order oo on o.order_id = oo.order_id
inner join  orderextension_order_group oog on oo.group_id = oog.id
set         o.order_reason_non_coded = concat('cycle_number:', oog.cycle_number)
where       oog.cycle_number is not null
;
