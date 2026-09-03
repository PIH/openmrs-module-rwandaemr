<style type="text/css">
    #queue-provider-assignment-dialog.dialog {
        box-sizing: border-box;
        max-width: calc(100vw - 32px);
        width: 480px;
    }

    .queue-provider-assignment-summary {
        background: #f3f7f7;
        border-left: 4px solid #167b72;
        color: #334444;
        margin: 0 0 16px;
        padding: 10px 12px;
    }

    #queue-provider-assignment-select {
        box-sizing: border-box;
        width: 100%;
    }

    .queue-provider-assignment-label {
        color: #344343;
        display: block;
        font-weight: bold;
        margin-bottom: 5px;
    }

    .queue-provider-assignment-error {
        color: #a11f17;
        font-size: 0.9em;
        margin-top: 5px;
    }

    .queue-provider-assignment-actions {
        display: flex;
        gap: 8px;
        justify-content: flex-end;
        margin-top: 16px;
    }
</style>

<div id="queue-provider-assignment-dialog" class="dialog" role="dialog"
     aria-modal="true" aria-labelledby="queue-provider-assignment-title" style="display: none">
    <div class="dialog-header">
        <i class="icon-user"></i>
        <h3 id="queue-provider-assignment-title">Assign provider</h3>
    </div>
    <div class="dialog-content">
        <div class="queue-provider-assignment-summary">
            <strong id="queue-provider-assignment-patient">Patient</strong>
            <div id="queue-provider-assignment-current"></div>
        </div>
        <label class="queue-provider-assignment-label" for="queue-provider-assignment-select">Provider</label>
        <select id="queue-provider-assignment-select"></select>
        <div id="queue-provider-assignment-error" class="queue-provider-assignment-error"
             role="alert" style="display: none"></div>
        <div class="queue-provider-assignment-actions">
            <button type="button" class="button cancel">Cancel</button>
            <button type="button" class="button confirm">
                <i class="icon-save"></i> Save provider
            </button>
        </div>
    </div>
</div>

<script type="text/javascript">
    (function(jq) {
        jq(function() {
            var dialogElement = jq("#queue-provider-assignment-dialog");
            var title = jq("#queue-provider-assignment-title");
            var patientLabel = jq("#queue-provider-assignment-patient");
            var currentProviderLabel = jq("#queue-provider-assignment-current");
            var providerInput = jq("#queue-provider-assignment-select");
            var providerError = jq("#queue-provider-assignment-error");
            var pendingForm = null;
            var assignmentDialog = null;
            var providerLoadState = "idle";

            function configureProviderInput() {
                if (!pendingForm || providerLoadState !== "loaded") {
                    return;
                }
                var currentProviderId = pendingForm.attr("data-provider-id") || "";
                var currentProviderName = pendingForm.attr("data-provider-name") || "";
                var emptyOption = providerInput.find("option[value='']");
                if (currentProviderId) {
                    emptyOption.text("No provider assigned").prop("disabled", false);
                    if (!providerInput.find("option[value='" + currentProviderId + "']").length) {
                        providerInput.append(jq("<option>").val(currentProviderId).text(currentProviderName));
                    }
                    providerInput.val(currentProviderId);
                } else {
                    emptyOption.text("Choose provider").prop("disabled", true);
                    providerInput.val("");
                }
            }

            function loadProviders() {
                if (providerLoadState === "loading" || providerLoadState === "loaded") {
                    configureProviderInput();
                    return;
                }
                providerLoadState = "loading";
                providerInput.prop("disabled", true).empty()
                    .append(jq("<option>").val("").text("Loading providers..."));
                jq.ajax({
                    url: "${ ui.actionLink("rwandaemr", "queue/transferReasonDialog", "getProviders") }",
                    dataType: "json",
                    success: function(data) {
                        providerInput.empty().append(jq("<option>").val("").text("Choose provider"));
                        jq.each(data.providers || [], function(index, provider) {
                            providerInput.append(jq("<option>").val(provider.id).text(provider.label));
                        });
                        providerLoadState = "loaded";
                        providerError.hide();
                        configureProviderInput();
                    },
                    error: function() {
                        providerLoadState = "failed";
                        providerInput.empty().append(jq("<option>").val("").text("Providers unavailable"));
                        providerError.text("Providers could not be loaded. Please try again.").show();
                    },
                    complete: function() {
                        providerInput.prop("disabled", providerLoadState !== "loaded");
                    }
                });
            }

            function closeDialog() {
                pendingForm = null;
                providerError.hide();
                if (assignmentDialog) {
                    assignmentDialog.close();
                } else {
                    dialogElement.hide();
                }
            }

            function saveProvider() {
                if (!pendingForm || providerLoadState !== "loaded") {
                    return;
                }
                var selectedProviderId = providerInput.val() || "";
                var currentProviderId = pendingForm.attr("data-provider-id") || "";
                if (!currentProviderId && !selectedProviderId) {
                    providerError.text("Select a provider.").show();
                    providerInput.focus();
                    return;
                }
                var formToSubmit = pendingForm;
                formToSubmit.find("input[name='assignedProviderId']").val(selectedProviderId);
                pendingForm = null;
                providerError.hide();
                if (assignmentDialog) {
                    assignmentDialog.close();
                } else {
                    dialogElement.hide();
                }
                formToSubmit.get(0).submit();
            }

            if (dialogElement.length && typeof emr !== "undefined"
                    && typeof emr.setupConfirmationDialog === "function") {
                assignmentDialog = emr.setupConfirmationDialog({
                    selector: "#queue-provider-assignment-dialog",
                    actions: {
                        confirm: saveProvider,
                        cancel: closeDialog
                    },
                    dialogOpts: {
                        overlayClose: false
                    }
                });
            } else {
                dialogElement.find(".confirm").on("click", saveProvider);
                dialogElement.find(".cancel").on("click", closeDialog);
            }

            jq(document).on("submit.queueProviderAssignment", "form.queue-provider-form", function(event) {
                event.preventDefault();
                pendingForm = jq(this);
                var currentProviderName = pendingForm.attr("data-provider-name") || "";
                title.text(currentProviderName ? "Edit provider" : "Assign provider");
                patientLabel.text(pendingForm.attr("data-patient-name") || "Patient");
                currentProviderLabel.text(currentProviderName
                        ? "Currently assigned to " + currentProviderName
                        : "No provider currently assigned");
                providerError.hide();
                loadProviders();

                if (assignmentDialog) {
                    assignmentDialog.show();
                } else {
                    dialogElement.show();
                }
                window.setTimeout(function() {
                    if (!providerInput.prop("disabled")) {
                        providerInput.focus();
                    }
                }, 0);
            });
        });
    })(jq);
</script>
