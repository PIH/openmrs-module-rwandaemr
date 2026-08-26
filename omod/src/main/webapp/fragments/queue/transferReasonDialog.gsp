<style type="text/css">
    #queue-transfer-reason-dialog.dialog {
        box-sizing: border-box;
        max-width: calc(100vw - 32px);
        width: 520px;
    }

    .queue-transfer-route {
        background: #f3f7f7;
        border-left: 4px solid #167b72;
        color: #334444;
        margin: 0 0 16px;
        padding: 10px 12px;
    }

    .queue-transfer-reason-label {
        color: #344343;
        display: block;
        font-weight: bold;
        margin-bottom: 5px;
    }

    #queue-transfer-reason,
    #queue-transfer-provider {
        box-sizing: border-box;
        width: 100%;
    }

    #queue-transfer-reason {
        min-height: 96px;
        resize: vertical;
    }

    .queue-transfer-provider-field {
        margin-top: 14px;
    }

    .queue-transfer-reason-error {
        color: #a11f17;
        font-size: 0.9em;
        margin-top: 5px;
    }

    .queue-transfer-dialog-actions {
        display: flex;
        gap: 8px;
        justify-content: flex-end;
        margin-top: 16px;
    }
</style>

<div id="queue-transfer-reason-dialog" class="dialog" role="dialog"
     aria-modal="true" aria-labelledby="queue-transfer-reason-title" style="display: none">
    <div class="dialog-header">
        <i class="icon-share-alt"></i>
        <h3 id="queue-transfer-reason-title">Send patient</h3>
    </div>
    <div class="dialog-content">
        <div class="queue-transfer-route">
            <strong id="queue-transfer-patient">Patient</strong>
            <span> from </span>
            <span id="queue-transfer-origin"></span>
            <span> to </span>
            <strong id="queue-transfer-destination"></strong>
        </div>
        <label class="queue-transfer-reason-label" for="queue-transfer-reason">
            Reason for transfer
        </label>
        <textarea id="queue-transfer-reason" maxlength="1024" rows="4"
                  placeholder="Enter the clinical or operational reason"></textarea>
        <div id="queue-transfer-reason-error" class="queue-transfer-reason-error"
             role="alert" style="display: none">
            Transfer reason is required.
        </div>
        <div class="queue-transfer-provider-field">
            <label class="queue-transfer-reason-label" for="queue-transfer-provider">
                Provider (optional)
            </label>
            <select id="queue-transfer-provider">
                <option value="">No provider assigned</option>
            </select>
        </div>
        <div class="queue-transfer-dialog-actions">
            <button type="button" class="button cancel">Cancel</button>
            <button type="button" class="button confirm">
                <i class="icon-share-alt"></i> Send patient
            </button>
        </div>
    </div>
</div>

<script type="text/javascript">
    (function(jq) {
        jq(function() {
            var dialogElement = jq("#queue-transfer-reason-dialog");
            var reasonInput = jq("#queue-transfer-reason");
            var providerInput = jq("#queue-transfer-provider");
            var reasonError = jq("#queue-transfer-reason-error");
            var pendingForm = null;
            var transferDialog = null;
            var providerLoadState = "idle";

            function loadProviders() {
                if (providerLoadState === "loading" || providerLoadState === "loaded") {
                    return;
                }
                providerLoadState = "loading";
                providerInput.prop("disabled", true).empty()
                    .append(jq("<option>").val("").text("Loading providers..."));
                jq.ajax({
                    url: "${ ui.actionLink("rwandaemr", "queue/transferReasonDialog", "getProviders") }",
                    dataType: "json",
                    success: function(data) {
                        providerInput.empty()
                            .append(jq("<option>").val("").text("No provider assigned"));
                        jq.each(data.providers || [], function(index, provider) {
                            providerInput.append(jq("<option>").val(provider.id).text(provider.label));
                        });
                        providerLoadState = "loaded";
                    },
                    error: function() {
                        providerInput.empty()
                            .append(jq("<option>").val("").text("Providers unavailable - no provider assigned"));
                        providerLoadState = "failed";
                    },
                    complete: function() {
                        providerInput.prop("disabled", false);
                    }
                });
            }

            function closeDialog() {
                pendingForm = null;
                reasonInput.val("");
                providerInput.val("");
                reasonError.hide();
                if (transferDialog) {
                    transferDialog.close();
                } else {
                    dialogElement.hide();
                }
            }

            function submitTransfer() {
                if (!pendingForm) {
                    return;
                }
                var reason = jq.trim(reasonInput.val());
                if (!reason) {
                    reasonError.show();
                    reasonInput.focus();
                    return;
                }

                var formToSubmit = pendingForm;
                formToSubmit.find("input[name='reason']").val(reason);
                formToSubmit.find("input[name='assignedProviderId']").val(providerInput.val());
                pendingForm = null;
                reasonError.hide();
                if (transferDialog) {
                    transferDialog.close();
                }
                formToSubmit.get(0).submit();
            }

            if (dialogElement.length && typeof emr !== "undefined"
                    && typeof emr.setupConfirmationDialog === "function") {
                transferDialog = emr.setupConfirmationDialog({
                    selector: "#queue-transfer-reason-dialog",
                    actions: {
                        confirm: submitTransfer,
                        cancel: closeDialog
                    },
                    dialogOpts: {
                        overlayClose: false
                    }
                });
            }

            jq(document).on("submit.queueTransferReason", "form.queue-transfer-form", function(event) {
                event.preventDefault();
                pendingForm = jq(this);

                var destination = pendingForm.find("select[name='destinationServicePointId'] option:selected").text();
                var patientName = pendingForm.attr("data-patient-name") || "Patient";
                var origin = pendingForm.attr("data-current-service-point") || "current service point";
                jq("#queue-transfer-patient").text(jq.trim(patientName));
                jq("#queue-transfer-origin").text(jq.trim(origin));
                jq("#queue-transfer-destination").text(jq.trim(destination));
                reasonInput.val("");
                providerInput.val("");
                reasonError.hide();
                loadProviders();

                if (transferDialog) {
                    transferDialog.show();
                    window.setTimeout(function() {
                        reasonInput.focus();
                    }, 0);
                    return;
                }

                var fallbackReason = window.prompt(
                        "Reason for sending " + jq.trim(patientName) + " to " + jq.trim(destination) + ":");
                if (fallbackReason === null) {
                    pendingForm = null;
                    return;
                }
                fallbackReason = jq.trim(fallbackReason);
                if (!fallbackReason) {
                    window.alert("Transfer reason is required.");
                    pendingForm = null;
                    return;
                }
                var fallbackForm = pendingForm;
                fallbackForm.find("input[name='reason']").val(fallbackReason);
                fallbackForm.find("input[name='assignedProviderId']").val(providerInput.val());
                pendingForm = null;
                fallbackForm.get(0).submit();
            });
        });
    })(jq);
</script>
