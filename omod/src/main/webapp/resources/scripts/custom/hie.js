var hie = hie || {}

hie.quickViewDialog = null;
hie.encounterUuid = null;
hie.paymentViewDialog = null;
hie.patientBillId = null;

/**
 * Function used to open the confirmation dialog for HIE Observation preview
 */

hie.createQuickViewDialog = function(){
    // console.log(hie.encounterUuid);
    hie.quickViewDialog = emr.setupConfirmationDialog({
        selector: "#quick-view-dialog",
        actions: {
            confirm: function(){
                hie.quickViewDialog.close();
            },
            cancel: function(){
                hie.quickViewDialog.close();
            }
        }
    });
    // console.log("we are done with the dialog!");
    hie.quickViewDialog.close();
};

/**
 * Function used to open the confirmation dialog for Payment preview
 */

hie.createPaymentDialog = function(){
    hie.paymentViewDialog = emr.setupConfirmationDialog({
        selector: "#payment-view-dialog",
        actions: {
            confirm: function(){
                var form = jq("#payment-data").find("form#irembo-pay-form");
                if (!form.length) {
                    hie.paymentViewDialog.close();
                    return;
                }
                var billId = form.find("input[name='billId']").val() || form.find("#irembo-pay-billId").val();
                var ownerCode = form.find("input[name='ownerCode']").val();
                if (!billId || !ownerCode || !ownerCode.trim()) {
                    emr.errorMessage("Phone number is required");
                    return;
                }
                var confirmBtn = jq("#payment-view-dialog .confirm");
                var spinner = confirmBtn.find("i.icon-spinner");
                confirmBtn.prop("disabled", true);
                spinner.show();
                var contextPath = jq("meta[name='openmrs-context-path']").attr("content") || "";
                if (!contextPath && typeof window.location !== "undefined") {
                    var path = window.location.pathname || "";
                    var openmrsIdx = path.indexOf("/openmrs");
                    contextPath = openmrsIdx >= 0 ? path.substring(0, openmrsIdx + 8) : "";
                }
                var initUrl = contextPath + "/ws/rest/v1/rwandaemr/irembopay/init";
                jq.ajax({
                    url: initUrl,
                    type: "POST",
                    data: { billId: billId, ownerCode: ownerCode.trim() },
                    dataType: "json"
                }).done(function(data) {
                    if (data && data.status === "success") {
                        emr.successMessage(data.message || "Payment request initiated");
                        hie.paymentViewDialog.close();
                        window.location.reload();
                    } else {
                        emr.errorMessage((data && data.message) ? data.message : "Request failed");
                    }
                }).fail(function(xhr) {
                    var msg = "Failed to initiate payment";
                    try {
                        var r = xhr.responseJSON;
                        if (r && r.message) msg = r.message;
                    } catch (e) {}
                    emr.errorMessage(msg);
                }).always(function() {
                    confirmBtn.prop("disabled", false);
                    spinner.hide();
                });
            },
            cancel: function(){
                hie.paymentViewDialog.close();
            }
        }
    });
    hie.paymentViewDialog.close();
};

hie.showQuickViewDialog = function(encounterUuid) {
    hie.encounterUuid = encounterUuid;

    if(hie.quickViewDialog == null){
        // console.log("Now try to call the dialog builder!");
        hie.createQuickViewDialog();
        // console.log("Dialog is built thanks");
        
    }
    // console.log(hie);
    hie.quickViewDialog.show();
};


hie.showPaymentViewDialog = function(patientBillId) {
    hie.patientBillId = patientBillId;

    if(hie.paymentViewDialog == null){
        // console.log("Now try to call the dialog builder!");
        hie.createPaymentDialog();
        // console.log("Dialog is built thanks");
        
    }
    // console.log(hie);
    hie.paymentViewDialog.show();
};