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

            //jq("input[name='applicationNumber'").val('220919-7657-5617');

            jq("#client-registry-search-button").click(function() {

                let searchButton = jq("#client-registry-search-button");
                let loadingSpinner = jq("#search-client-registry-loading-spinner");
                let notFoundMessage = jq('#search-client-registry-not-found-message');
                let foundMessage = jq('#search-client-registry-found-message');

                jq(searchButton).prop('disabled', true);
                jq(loadingSpinner).show();
                jq(notFoundMessage).hide();
                jq(foundMessage).hide();

                let searchParams = {}
                <% config.formFields.each { field -> %>
                {
                    let identifierType = '${field.identifierTypeUuid}';
                    let identifierValue = jq("input[name='${field.formFieldName}']").val();
                    if (identifierValue) {
                        searchParams['identifier_' + identifierType] = identifierValue;
                    }
                }
                <% } %>

                if (Object.keys(searchParams).length) {
                    console.debug("Searching client registry for: " + JSON.stringify(searchParams));
                    jq.ajax({
                        url: "${ ui.actionLink("rwandaemr", "field/searchClientRegistry", "findByIdentifier") }",
                        dataType: "json",
                        data: searchParams,
                        success: function (data) {
                            successData = data;
                            jq(loadingSpinner).hide();
                            jq(searchButton).removeProp('disabled');
                            let registrationForm = jq(searchButton).closest("form");
                            for (const [key, value] of Object.entries(data)) {
                                jq(registrationForm).find("[name='" + key + "']").val(value);
                            }
                            jq(foundMessage).show();
                            console.debug(data);
                        },
                        error: function (data) {
                            jq(loadingSpinner).hide();
                            jq(searchButton).removeProp('disabled');
                            jq(notFoundMessage).show();
                            console.debug(data);
                        }
                    });
                }
            });

            jq("#client-registry-close-button").click(function() {
                jq("#client-registry-match-section").hide();
            });
        });
    </script>

<% } %>