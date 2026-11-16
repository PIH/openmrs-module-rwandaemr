<% if (clientRegistryEnabled && config.formFields) { %>
    <style>
        #search-client-registry-section {
            padding-top: 20px;
            clear: left;
        }
        .client-registry-photo-fieldset {
            min-height: 260px;
            padding-right: 230px !important;
            position: relative;
        }
        #search-client-registry-loading-spinner {
            display: none;
        }
        #search-client-registry-found-section {
            display: none;
        }
        #search-client-registry-not-found-section {
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
        #population-registry-photo-preview {
            border: 1px solid #d8d8d8;
            box-sizing: border-box;
            height: 220px;
            position: absolute;
            right: 10px;
            top: 6px;
            overflow: hidden;
            text-align: center;
            width: 180px;
        }
        #population-registry-photo-preview img {
            display: none;
            height: 100%;
            object-fit: cover;
            width: 100%;
        }
        #population-registry-photo-placeholder {
            color: #666;
            display: block;
            font-size: 1rem;
            left: 10px;
            position: absolute;
            right: 10px;
            top: 50%;
            transform: translateY(-50%);
        }
    </style>

    <div id="population-registry-photo-preview">
        <img id="population-registry-photo" alt="Population registry photo" />
        <span id="population-registry-photo-placeholder">No registry photo</span>
    </div>

    <p id="search-client-registry-section" class="left">
        <input id="client-registry-search-button" type="button" value="${ui.message("rwandaemr.clientRegistry.search")}" />
        <i id="search-client-registry-loading-spinner" class="icon-spinner icon-spin icon-2x"></i>
        <span id="search-client-registry-found-section">
            <i class="search-success-icon icon-ok"></i>
            <span id="search-client-registry-found-message"></span>
        </span>
        <span id="search-client-registry-not-found-section">
            <i class="search-failed-icon icon-remove"></i>
            <span id="search-client-registry-not-found-message"></span>
        </span>
    </p>

    <script type="text/javascript">
        jq(function() {
            positionRegistryPhotoPreview();

            //jq("input[name='applicationNumber'").val('220919-7657-5617');

            jq("#client-registry-search-button").click(function() {

                let searchButton = jq("#client-registry-search-button");
                let loadingSpinner = jq("#search-client-registry-loading-spinner");
                let notFoundSection = jq('#search-client-registry-not-found-section');
                let foundSection = jq('#search-client-registry-found-section');
                let foundMessage = jq('#search-client-registry-found-message');
                let notFoundMessage = jq('#search-client-registry-not-found-message');
                let registryPhoto = jq("#population-registry-photo");
                let registryPhotoPlaceholder = jq("#population-registry-photo-placeholder");

                jq(searchButton).prop('disabled', true);
                jq(loadingSpinner).show();
                jq(notFoundSection).hide();
                jq(foundSection).hide();
                jq(foundMessage).html("");
                jq(notFoundMessage).html("");
                clearRegistryPhoto("Loading photo...");

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

                console.debug("Searching client registry for: " + JSON.stringify(searchParams));
                jq.ajax({
                    url: "${ ui.actionLink("rwandaemr", "field/searchClientRegistry", "findByIdentifier") }",
                    dataType: "json",
                    data: searchParams,
                    success: function (data) {
                        successData = data;
                        jq(loadingSpinner).hide();
                        jq(searchButton).removeProp('disabled');
                        if (data.patient) {
                            let registrationForm = jq(searchButton).closest("form");
                            for (const [key, value] of Object.entries(data.patient)) {
                                if (key !== "photo") {
                                    jq(registrationForm).find("[name='" + key + "']").val(value);
                                }
                            }
                            showRegistryPhoto(data.patient.photo);
                            jq(foundMessage).html(data.message);
                            jq(foundSection).show();
                        }
                        else {
                            clearRegistryPhoto("No registry photo");
                            jq(notFoundMessage).html(data.message);
                            jq(notFoundSection).show();
                        }
                        console.debug(data);
                    },
                    error: function (data) {
                        jq(loadingSpinner).hide();
                        jq(searchButton).removeProp('disabled');
                        clearRegistryPhoto("No registry photo");
                        jq(notFoundMessage).html(data.message);
                        jq(notFoundSection).show();
                        console.error(data);
                    }
                });

                function clearRegistryPhoto(message) {
                    jq(registryPhoto).hide().removeAttr("src");
                    jq(registryPhotoPlaceholder).text(message || "No registry photo").show();
                }

                function showRegistryPhoto(photo) {
                    let photoSrc = getRegistryPhotoSrc(photo);
                    if (!photoSrc) {
                        clearRegistryPhoto("No registry photo");
                        return;
                    }
                    jq(registryPhoto)
                        .off("load error")
                        .on("load", function () {
                            jq(registryPhotoPlaceholder).hide();
                            jq(registryPhoto).show();
                        })
                        .on("error", function () {
                            clearRegistryPhoto("Photo unavailable");
                        })
                        .attr("src", photoSrc);
                }

                function getRegistryPhotoSrc(photo) {
                    if (!photo) {
                        return null;
                    }
                    let trimmed = jq.trim(photo);
                    if (!trimmed) {
                        return null;
                    }
                    let lower = trimmed.toLowerCase();
                    if (lower.indexOf("data:image/") === 0 || lower.indexOf("http://") === 0 || lower.indexOf("https://") === 0) {
                        return trimmed;
                    }
                    let compact = trimmed
                        .split(" ").join("")
                        .split(String.fromCharCode(9)).join("")
                        .split(String.fromCharCode(10)).join("")
                        .split(String.fromCharCode(13)).join("");
                    if (isBase64PhotoValue(compact)) {
                        return "data:image/jpeg;base64," + compact;
                    }
                    if (trimmed.charAt(0) === "/") {
                        return trimmed;
                    }
                    return null;
                }

                function isBase64PhotoValue(value) {
                    if (!value || value.length < 100) {
                        return false;
                    }
                    if (value.indexOf("/9j/") !== 0 && value.indexOf("iVBOR") !== 0) {
                        return false;
                    }
                    let validChars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789+/=";
                    for (let i = 0; i < value.length; i++) {
                        if (validChars.indexOf(value.charAt(i)) === -1) {
                            return false;
                        }
                    }
                    return true;
                }
            });

            function positionRegistryPhotoPreview() {
                let photoPreview = jq("#population-registry-photo-preview");
                let searchSection = jq("#search-client-registry-section");
                let fieldset = searchSection.closest("fieldset");
                if (photoPreview.length && fieldset.length) {
                    fieldset.addClass("client-registry-photo-fieldset");
                    photoPreview.appendTo(fieldset);
                }
            }
        });
    </script>

<% } %>
