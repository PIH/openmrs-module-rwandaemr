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
    // console.log(hie.encounterUuid);
    hie.paymentViewDialog = emr.setupConfirmationDialog({
        selector: "#payment-view-dialog",
        actions: {
            confirm: function(){
                console.log("Here Make sure to send the request to the server to proceed with the payment");
                //hie.paymentViewDialog.close();
            },
            cancel: function(){
                hie.paymentViewDialog.close();
            }
        }
    });
    // console.log("we are done with the dialog!");
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