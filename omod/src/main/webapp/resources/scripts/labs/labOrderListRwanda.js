/**
 * Rwanda additions to the pihapps Lab Order List, registered through its extension points:
 *  - org.openmrs.module.pihapps.labOrderList.orderBadges: payment status
 * Loaded on every page as a global resource, so it only defines functions.
 */
(function() {

    const paymentBadges = {
        PAID: { text: "Paid", cssClass: "badge-success", showOnPatientRow: false },
        PARTIALLY_PAID: { text: "Partially paid", cssClass: "badge-warning", title: "Only part of the bill for this test has been paid" },
        NOT_PAID: { text: "Not paid", cssClass: "badge-danger", title: "The bill for this test has not been paid" },
        NOT_BILLED: { text: "Not billed", cssClass: "badge-secondary", title: "No bill was found for this test" }
    };

    window.rwandaLabOrderPaymentBadges = function(orders) {
        const orderUuids = orders.filter(o => !o.dateStopped).map(o => o.uuid);
        if (orderUuids.length === 0) {
            return Promise.resolve({});
        }
        return new Promise(function(resolve, reject) {
            jq.getJSON(openmrsContextPath + "/ws/rest/v1/rwandaemr/lab/orderPayments?" + jq.param({ order: orderUuids }, true))
                .done(function(payments) {
                    const badges = {};
                    Object.keys(payments || {}).forEach(function(orderUuid) {
                        const badge = paymentBadges[payments[orderUuid].status];
                        if (badge) {
                            badges[orderUuid] = badge;
                        }
                    });
                    resolve(badges);
                })
                .fail(reject);
        });
    };
})();
