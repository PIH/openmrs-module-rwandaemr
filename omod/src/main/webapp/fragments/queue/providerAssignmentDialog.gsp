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

    .queue-provider-assignment-combobox {
        position: relative;
    }

    #queue-provider-assignment-search {
        box-sizing: border-box;
        padding-right: 34px;
        width: 100%;
    }

    .queue-provider-assignment-toggle {
        background: transparent;
        border: 0;
        color: #333;
        cursor: pointer;
        height: 100%;
        margin: 0;
        padding: 0 10px;
        position: absolute;
        right: 0;
        top: 0;
    }

    .queue-provider-autocomplete-results {
        max-height: 220px;
        overflow-x: hidden;
        overflow-y: auto;
        z-index: 10000 !important;
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
        <label class="queue-provider-assignment-label" for="queue-provider-assignment-search">Provider</label>
        <div class="queue-provider-assignment-combobox">
            <input id="queue-provider-assignment-search" type="text" autocomplete="off"
                   role="combobox" aria-autocomplete="list" placeholder="Choose provider" />
            <button type="button" class="queue-provider-assignment-toggle"
                    aria-label="Show providers" tabindex="-1"><i class="icon-chevron-down"></i></button>
        </div>
        <input id="queue-provider-assignment-select" type="hidden" />
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
            var providerSearch = jq("#queue-provider-assignment-search");
            var providerInput = jq("#queue-provider-assignment-select");
            var providerError = jq("#queue-provider-assignment-error");
            var pendingForm = null;
            var assignmentDialog = null;
            var providerLoadState = "idle";
            var providerOptions = [];
            var providerSelectionMade = false;

            function providerSuggestions(searchTerm) {
                var currentProviderId = pendingForm.attr("data-provider-id") || "";
                var normalizedSearch = jq.trim(searchTerm).toLowerCase();
                var suggestions = [];
                if (currentProviderId && (!normalizedSearch || "no provider assigned".indexOf(normalizedSearch) !== -1)) {
                    suggestions.push({ id: "", label: "No provider assigned", value: "No provider assigned" });
                }
                jq.each(providerOptions, function(index, provider) {
                    if (!normalizedSearch || String(provider.label).toLowerCase().indexOf(normalizedSearch) !== -1) {
                        suggestions.push({ id: String(provider.id), label: provider.label, value: provider.label });
                    }
                });
                if (!suggestions.length) {
                    suggestions.push({ unavailable: true, label: "No providers match your search", value: searchTerm });
                }
                return suggestions;
            }

            function configureProviderInput() {
                if (!pendingForm || providerLoadState !== "loaded") {
                    return;
                }
                var currentProviderId = pendingForm.attr("data-provider-id") || "";
                providerInput.val(currentProviderId);
                providerSearch.val(pendingForm.attr("data-provider-name") || "");
                providerSelectionMade = Boolean(currentProviderId);
            }

            providerSearch.autocomplete({
                minLength: 0,
                delay: 0,
                source: function(request, response) {
                    response(providerLoadState === "loaded" ? providerSuggestions(request.term) : []);
                },
                select: function(event, ui) {
                    if (ui.item.unavailable) {
                        return false;
                    }
                    providerInput.val(ui.item.id);
                    providerSearch.val(ui.item.label);
                    providerSelectionMade = true;
                    providerError.hide();
                    return false;
                }
            });
            providerSearch.autocomplete("widget").addClass("queue-provider-autocomplete-results");
            providerSearch.on("input", function() {
                providerInput.val("");
                providerSelectionMade = false;
                providerError.hide();
            }).on("click", function() {
                providerSearch.autocomplete("search", "");
            });
            dialogElement.find(".queue-provider-assignment-toggle").on("click", function() {
                providerSearch.focus().autocomplete("search", "");
            });

            function loadProviders() {
                if (providerLoadState === "loading" || providerLoadState === "loaded") {
                    configureProviderInput();
                    return;
                }
                providerLoadState = "loading";
                providerSearch.prop("disabled", true).val("Loading providers...");
                jq.ajax({
                    url: "${ ui.actionLink("rwandaemr", "queue/transferReasonDialog", "getProviders") }",
                    dataType: "json",
                    success: function(data) {
                        providerOptions = data.providers || [];
                        providerLoadState = "loaded";
                        providerError.hide();
                        configureProviderInput();
                    },
                    error: function() {
                        providerLoadState = "failed";
                        providerSearch.val("Providers unavailable");
                        providerError.text("Providers could not be loaded. Please try again.").show();
                    },
                    complete: function() {
                        providerSearch.prop("disabled", providerLoadState !== "loaded");
                    }
                });
            }

            function closeDialog() {
                pendingForm = null;
                providerError.hide();
                providerSearch.autocomplete("close");
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
                if (!providerSelectionMade) {
                    providerError.text("Select a provider from the list.").show();
                    providerSearch.focus().autocomplete("search", providerSearch.val());
                    return;
                }
                var selectedProviderId = providerInput.val() || "";
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
                    if (!providerSearch.prop("disabled")) {
                        providerSearch.focus();
                    }
                }, 0);
            });
        });
    })(jq);
</script>
