var hie = hie || {}

hie.quickViewDialog = null;
hie.encounterUuid = null;

/**
 * Function used to open the confirmation dialog
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