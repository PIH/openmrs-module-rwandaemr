<% if (clientRegistryEnabled && config.formFields) { %>
    <style>
        #search-client-registry-section {
            padding-top: 20px;
            clear: left;
        }
        #search-client-registry-loading-spinner {
            display: none;
        }
        #search-client-registry-found-message {
            display: none;
        }
        #search-client-registry-not-found-message {
            display: none;
        }
        .search-success-icon {
            font-size: 1.5em;
            color: rgb(40, 201, 0);
            padding: 3px 2px;
        }
        .search-failed-icon {
            font-size: 1.5em;
            color: darkred;
            padding: 3px 2px;
        }
    </style>

    <p id="search-client-registry-section" class="left">
        <input id="client-registry-search-button" type="button" value="${ui.message("rwandaemr.clientRegistry.search")}" />
        <i id="search-client-registry-loading-spinner" class="icon-spinner icon-spin icon-2x"></i>
        <span id="search-client-registry-found-message">
            <i class="search-success-icon icon-ok"></i>
            ${ui.message("rwandaemr.clientRegistry.matchFound")}
        </span>
        <span id="search-client-registry-not-found-message">
            <i class="search-failed-icon icon-remove"></i>
            ${ui.message("rwandaemr.clientRegistry.noMatchFound")}
        </span>
    </p>

    <script type="text/javascript">
        jq(function() {

            jq("#client-registry-search-button").click(function() {
                jq("#client-registry-search-button").prop('disabled', true);
                jq("#search-client-registry-loading-spinner").show();
                jq('#search-client-registry-not-found-message').hide();
                jq('#search-client-registry-found-message').hide();
                let successData = null;
                <% config.formFields.each { field -> %>
                    if (!successData) {
                        let identifierField = jq("input[name='${field.formFieldName}']");
                        let registrationForm = jq(identifierField).closest("form");
                        let identifierValue = jq(identifierField).val();
                        if (identifierValue) {
                            console.debug("searching client registry for ${field.formFieldName}, ${field.identifierTypeUuid}: " + identifierValue);
                            jq.ajax({
                                url: "${ ui.actionLink("rwandaemr", "field/searchClientRegistry", "findByIdentifier") }",
                                dataType: "json",
                                data: {
                                    'identifier': jq("input[name='${field.formFieldName}']").val(),
                                    'identifierTypeUuid': '${field.identifierTypeUuid}'
                                },
                                success: function (data) {
                                    successData = data;
                                    jq('#search-client-registry-loading-spinner').hide();
                                    jq('#client-registry-search-button').removeProp('disabled');
                                    for (const [key, value] of Object.entries(data)) {
                                        jq(registrationForm).find("[name='" + key + "']").val(value);
                                    }
                                    jq('#search-client-registry-found-message').show();
                                    console.debug(data);
                                },
                                error: function (data) {
                                    jq('#search-client-registry-loading-spinner').hide();
                                    jq("#client-registry-search-button").removeProp('disabled');
                                    jq('#search-client-registry-not-found-message').show();
                                    console.debug(data);
                                }
                            });
                        }
                    }
                <% } %>
            });

            jq("#client-registry-close-button").click(function() {
                jq("#client-registry-match-section").hide();
            });
        });
    </script>

<% } %>