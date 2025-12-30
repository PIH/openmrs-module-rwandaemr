/*
See:  RWA-1046

The Rwanda EMR uses Orders to indicate that a lab test needs to be performed, and Obs associated with these Orders
to track the specific lab results that are associated with a given order.  Historically, there was no
clear mechanism to indicate that a lab Order had been completed by the lab, other than to check to see whether any
Obs were already associated with it.  However, in later OpenMRS versions, the standard is to use a new `fulfillerStatus`
property that was added to Order.  This allows indicating a status directly on the Order that indicates whether it
has been completed, as well as a number of other types of statuses that was not possible or standardized in the Obs-based
model.  To be compatible with this standard, this changeset will identify any Order that has at least one non-voided
Obs associated with it, and will set the fulfillerStatus filed on that order to COMPLETED
*/
update orders
left join drug_order on orders.order_id = drug_order.order_id
inner join obs on orders.order_id = obs.order_id
set orders.fulfiller_status = 'COMPLETED', orders.fulfiller_comment = 'Auto-updated to completed'
where drug_order.order_id is null
and obs.voided = 0
and orders.voided = 0
and orders.order_action != 'DISCONTINUE'
and orders.fulfiller_status is null;

/*
 Additionally, any lab order (defined as an order that is not also found in the drug_order table)
 that is older than 30 days and has not yet had any obs / fulfillerStatus associated with it, should be auto-expired
*/
update orders
left join drug_order on orders.order_id = drug_order.order_id
set orders.auto_expire_date = now(), orders.fulfiller_comment = 'Auto-updated to expired'
where drug_order.order_id is null
and orders.fulfiller_status is null
and orders.auto_expire_date is null
and orders.date_stopped is null
and orders.voided = 0
and orders.order_action != 'DISCONTINUE'
and timestampdiff(day, orders.date_activated, now()) >= 30
and (orders.scheduled_date is null or timestampdiff(day, orders.scheduled_date, now()) >= 30);