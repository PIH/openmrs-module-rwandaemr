<div class="info-section">
    <div class="info-header">
        <i class="icon-random"></i>
        <h3>${ ui.message(config.label ? config.label : "Transfer Information").toUpperCase() }</h3>
    </div>
    <div class="info-body">
        <g:if test="${error}">
            <div style="color: red; font-weight: bold;">${error}</div>
        </g:if>
        <g:if test="${showTransferLink && transferId?.trim() && (error == null || error.trim().isEmpty())}">
            <a id="open-transfer-link"
               href="javascript:void(0);"
               data-transfer-id="${transferId}"
               data-upid="${upid}"
               data-transfer-url="${ui.contextPath()}/ws/rest/v1/rwandaemr/transfer"
               title="Open transfer">Open Transfer <i class="fas fa-eye"></i></a>
        </g:if>
    </div>
</div>

<div id="transfer-quick-view-dialog" class="dialog" style="display: none">
    <div class="dialog-header">
        <i class="icon-random"></i>
        <h3 id="transfer_dialog_title">Transfer Information</h3>
    </div>
    <div class="dialog-content">
        <div id="transfer-clinician-body">
            <div style="padding: 10px;"><i class="icon-spinner icon-spin"></i> Loading transfer information...</div>
        </div>
        <button class="cancel">${ ui.message("coreapps.cancel") }</button>
    </div>
</div>

<style>
/* Same dialog technique used in hieEncountersSection.gsp */
#transfer-quick-view-dialog.dialog {
  position: fixed;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  z-index: 10000;
  background: white;
  border: 1px solid #00473f;
  border-radius: 4px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.3);
  max-width: 95%;
  max-height: 92vh;
  width: 1400px;
  display: flex;
  flex-direction: column;
}

#transfer-quick-view-dialog .dialog-header {
  padding: 15px 20px;
  border-bottom: 1px solid #00473f;
  background: #00473f;
  border-radius: 4px 4px 0 0;
  flex-shrink: 0;
}

#transfer-quick-view-dialog .dialog-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: bold;
}

#transfer-quick-view-dialog .dialog-content {
  padding: 20px;
  overflow-y: auto;
  overflow-x: hidden;
  flex: 1;
  min-height: 0;
}

#transfer-clinician-body {
  max-height: 70vh;
  overflow-y: auto;
  overflow-x: hidden;
  margin-bottom: 15px;
}
</style>

<script type="text/javascript">
    (function(jq) {
        var transferQuickViewDialog = null;

        function createTransferQuickViewDialog() {
            if (typeof emr !== "undefined" && typeof emr.setupConfirmationDialog === "function") {
                transferQuickViewDialog = emr.setupConfirmationDialog({
                    selector: "#transfer-quick-view-dialog",
                    actions: {
                        confirm: function() {
                            transferQuickViewDialog.close();
                        },
                        cancel: function() {
                            transferQuickViewDialog.close();
                        }
                    }
                });
                transferQuickViewDialog.close();
            }
        }

        function showTransferModal() {
            if (transferQuickViewDialog == null) {
                createTransferQuickViewDialog();
            }
            if (transferQuickViewDialog && typeof transferQuickViewDialog.show === "function") {
                transferQuickViewDialog.show();
            } else {
                jq("#transfer-quick-view-dialog").show();
            }
        }

        function hideTransferModal() {
            if (transferQuickViewDialog && typeof transferQuickViewDialog.close === "function") {
                transferQuickViewDialog.close();
            } else {
                jq("#transfer-quick-view-dialog").hide();
            }
        }

        function esc(v) {
            if (v === null || v === undefined) return "";
            return String(v)
                .replace(/&/g, "&amp;")
                .replace(/</g, "&lt;")
                .replace(/>/g, "&gt;");
        }

        function buildTransferClinicianHtml(item) {
            var yesNoCircle = function(flag) {
                return flag ? "&#9679;" : "&#9711;";
            };
            var line = function(value, minWidth) {
                var safe = esc(value || "");
                var width = minWidth || 180;
                return "<span class='tf-line' style='min-width:" + width + "px;'>" + safe + "</span>";
            };

            return `
                <style>
                    .tf-sheet { border: 2px solid #111; padding: 10px 12px; background: #fff; font-family: "Times New Roman", serif; color: #111; }
                    .tf-head { display:flex; gap: 10px; }
                    .tf-left { flex: 1; }
                    .tf-right { width: 48%; border: 2px solid #111; padding: 6px 10px; }
                    .tf-title { text-align:center; font-weight: 700; text-decoration: underline; font-size: 20px; margin: 8px 0 10px; letter-spacing: .5px; }
                    .tf-line { display:inline-block; border-bottom: 1px dashed #111; min-height: 20px; vertical-align: bottom; }
                    .tf-row { font-size: 19px; line-height: 1.25; margin: 2px 0; }
                    .tf-row strong { font-weight: 700; }
                    .tf-section-title { font-size: 23px; font-weight: 700; text-decoration: underline; margin-top: 8px; }
                    .tf-lines-block { border-bottom: 1px dashed #111; height: 34px; margin-top: 2px; }
                    .tf-circle { font-size: 20px; vertical-align: middle; margin: 0 8px 0 4px; }
                    .tf-bottom-gap { margin-top: 10px; }
                </style>
                <div class="tf-sheet">
                    <div class="tf-head">
                        <div class="tf-left">
                            <div class="tf-row"><strong>REPUBLIC OF RWANDA</strong></div>
                            <div class="tf-row" style="margin-top: 22px;"><strong>MINISTRY OF HEALTH</strong></div>
                        </div>
                        <div class="tf-right">
                            <div class="tf-row"><strong>Province:</strong>\${line(item.province, 320)}</div>
                            <div class="tf-row"><strong>District:</strong>\${line(item.district || item.patientDistrict, 332)}</div>
                            <div class="tf-row"><strong>Name of Hospital:</strong>\${line(item.hospitalName || item.referringFacilityName || item.origin, 230)}</div>
                            <div class="tf-row"><strong>Name of Referring Facility:</strong>\${line(item.referringFacilityName || item.origin, 172)}</div>
                            <div class="tf-row"><strong>Referring Unit:</strong>\${line(item.referringUnit, 280)}</div>
                            <div class="tf-row"><strong>Receiving Clinician/Phone:</strong>\${line(item.receivingClinicianPhone || item.staffContactPhone, 190)}</div>
                        </div>
                    </div>

                    <div class="tf-title">EXTERNAL TRANSFER FORM</div>

                    <div class="tf-row"><strong>Client Name:</strong> \${line(item.clientName || item.subject, 380)} <strong>Serial number in register/EMR ID:</strong> \${line(item.serialNumberOrEmrId || item.subject, 250)}</div>
                    <div class="tf-row"><strong>Age(DOB):</strong> \${line(item.ageDob, 150)} <strong>Sex:</strong> \${line(item.sex, 90)} <strong>Name of caregiver:</strong> \${line(item.caregiverName, 270)} <strong>Telephone:</strong> \${line(item.telephone, 180)}</div>
                    <div class="tf-row"><strong>District:</strong> \${line(item.patientDistrict || item.district, 200)} <strong>Sector:</strong> \${line(item.patientSector, 170)} <strong>Cell:</strong> \${line(item.patientCell, 210)} <strong>Village:</strong> \${line(item.patientVillage, 190)}</div>
                    <div class="tf-row"><strong>Date and time of Admission:</strong> \${line(item.admissionDatetime || item.date, 280)} <strong>Date and Time of decision to transfer:</strong> \${line(item.transferDecisionDatetime, 240)}</div>
                    <div class="tf-row"><strong>Receiving Facility:</strong> \${line(item.receivingFacility || item.destination, 240)} <strong>Receiving Service:</strong> \${line(item.receivingService || item.admitSource, 250)} <strong>Calling Time:</strong> \${line(item.callingTime, 140)}</div>
                    <div class="tf-row"><strong>Staff contacted at receiving facility:</strong> \${line(item.staffContactedAtReceivingFacility, 320)} <strong>Phone:</strong> \${line(item.staffContactPhone || item.receivingClinicianPhone, 260)}</div>

                    <div class="tf-row">
                        <strong>Type of transfer:</strong>
                        Emergency:<span class="tf-circle">\${yesNoCircle(item.isEmergency === true || item.isEmergency === "true")}</span>
                        Not- Emergency:<span class="tf-circle">\${yesNoCircle(item.isNonEmergency === true || item.isNonEmergency === "true")}</span>
                        Follow up:<span class="tf-circle">\${yesNoCircle(item.isFollowUp === true || item.isFollowUp === "true")}</span>
                    </div>
                    <div class="tf-row"><strong>If emergency:</strong> Time ambulance called: \${line(item.ambulanceCalledTime, 200)} Time of departure from referring facility: \${line(item.departureTime, 250)}</div>
                    <div class="tf-row"><strong>Reason for Transfer:</strong> \${line(item.reasonForTransfer, 830)}</div>

                    <div class="tf-section-title">Significant Findings:</div>
                    <div class="tf-row"><strong>Clinical Presentation:</strong> \${line(item.clinicalPresentation, 780)}</div>
                    <div class="tf-lines-block"></div>
                    <div class="tf-lines-block"></div>
                    <div class="tf-lines-block"></div>

                    <div class="tf-row"><strong>If person with disability, record the type of disability:</strong> \${line(item.disabilityType, 470)}</div>

                    <div class="tf-row">
                        <strong>Vital Signs:</strong>
                        T&#176;: \${line(item.temperature, 70)}
                        SpO<sub>2</sub>: \${line(item.spo2, 70)}
                        RR: \${line(item.respiratoryRate, 70)}
                        Pulse: \${line(item.pulse, 70)}
                        BP: \${line(item.bloodPressure, 90)}
                        Weight: \${line(item.weight, 80)}
                        Height: \${line(item.height, 80)}
                        MUAC: \${line(item.muac, 80)}
                    </div>
                    <div class="tf-row"><strong>Laboratory:</strong> \${line(item.laboratory, 880)}</div>
                    <div class="tf-row"><strong>Others:</strong> \${line(item.others, 925)}</div>
                    <div class="tf-row"><strong>Diagnosis:</strong> \${line(item.diagnosis, 900)}</div>
                    <div class="tf-row"><strong>Procedures and Treatments:</strong> \${line(item.proceduresAndTreatments, 765)}</div>

                    <div class="tf-row">
                        <strong>Type of Transportation:</strong>
                        Ambulance: <span class="tf-circle">\${yesNoCircle(item.isAmbulanceTransport === true || item.isAmbulanceTransport === "true")}</span>
                        Other (specify): \${line(item.otherTransportType, 330)}
                        NA: <span class="tf-circle">\${yesNoCircle(item.isNaTransport === true || item.isNaTransport === "true")}</span>
                    </div>

                    <div class="tf-row">
                        <strong>Health insurance:</strong>
                        CBHI (mutuelle): <span class="tf-circle">\${yesNoCircle(item.isCbhiInsurance === true || item.isCbhiInsurance === "true")}</span>
                        RSSB: <span class="tf-circle">\${yesNoCircle(item.isRssbInsurance === true || item.isRssbInsurance === "true")}</span>
                        MMI: <span class="tf-circle">\${yesNoCircle(item.isMmiInsurance === true || item.isMmiInsurance === "true")}</span>
                        Other (Specify): \${line(item.otherInsurance, 150)}
                        None: <span class="tf-circle">\${yesNoCircle(item.isNoInsurance === true || item.isNoInsurance === "true")}</span>
                    </div>

                    <div class="tf-row tf-bottom-gap">
                        <strong>Names of referring health care provider:</strong> \${line(item.referringProviderName, 280)}
                        <strong>Qualification:</strong> \${line(item.referringProviderQualification, 200)}
                    </div>
                    <div class="tf-row">
                        <strong>Date:</strong> \${line(item.formDate || item.date, 120)}
                        <strong>Time:</strong> \${line(item.formTime || item.transferDecisionDatetime, 120)}
                        <strong>Phone:</strong> \${line(item.providerPhone || item.telephone, 220)}
                        <strong>Signature and stamp:</strong> \${line(item.signatureAndStamp, 180)}
                    </div>
                </div>
            `;
        }

        jq(document).on("click", "#open-transfer-link", function(e) {
            e.preventDefault();
            var link = jq(this);
            var upid = link.data("upid");
            var transferId = link.data("transfer-id");
            // Prefer explicit attribute value and keep a hard fallback to avoid relative "transfer?..." 404 calls.
            var endpoint = link.attr("data-transfer-url") || "";
            var origin = window.location.origin || "";
            var normalizeAbsoluteUrl = function(base, path) {
                var b = String(base || "");
                var p = String(path || "");
                while (b.length > 0 && b.charAt(b.length - 1) === "/") {
                    b = b.substring(0, b.length - 1);
                }
                if (p.indexOf("http://") === 0 || p.indexOf("https://") === 0) {
                    return p;
                }
                while (p.length > 0 && p.charAt(0) === "/") {
                    p = p.substring(1);
                }
                p = "/" + p;
                return b + p;
            };
            if (!endpoint || endpoint.indexOf("/ws/rest/v1/rwandaemr/transfer") === -1) {
                endpoint = normalizeAbsoluteUrl(origin, "${ui.contextPath()}/ws/rest/v1/rwandaemr/transfer");
            } else if (endpoint.indexOf("http://") !== 0 && endpoint.indexOf("https://") !== 0) {
                endpoint = normalizeAbsoluteUrl(origin, endpoint);
            }

            if (!upid || !transferId) {
                return;
            }

            var requestUrl = endpoint + "?upid=" + encodeURIComponent(String(upid))
                + "&transferId=" + encodeURIComponent(String(transferId))
                + "&activeOnly=false";

            jq("#transfer_dialog_title").html("Transfer Information");
            jq("#transfer-clinician-body").html("<div style='padding: 10px;'><i class='icon-spinner icon-spin'></i> Loading transfer information...</div>");
            showTransferModal();

            jq.ajax({
                url: requestUrl,
                type: "GET",
                headers: {
                    "Accept": "application/json"
                }
            }).done(function(response) {
                if (typeof response === "string") {
                    try {
                        response = jq.parseJSON(response);
                    } catch (err) {
                        jq("#transfer-clinician-body").html(
                            "<div style='color:red;'>Transfer endpoint returned non-JSON response.</div>" +
                            "<div style='margin-top:8px; font-size:12px; color:#555;'>URL: " + esc(requestUrl) + "</div>"
                        );
                        return;
                    }
                }
                if (!response || response.status !== "success") {
                    var msg = response && response.message ? response.message : "Unable to retrieve transfer information";
                    jq("#transfer-clinician-body").html(
                        "<div style='color:red;'>" + esc(msg) + "</div>" +
                        "<div style='margin-top:8px; font-size:12px; color:#555;'>URL: " + esc(requestUrl) + "</div>"
                    );
                    return;
                }
                var data = response.data || [];
                if (!data.length) {
                    jq("#transfer-clinician-body").html("<div style='color:red;'>No matching transfer information found.</div>");
                    return;
                }
                jq("#transfer-clinician-body").html(buildTransferClinicianHtml(data[0]));
            }).fail(function(xhr, textStatus) {
                var details = "";
                if (xhr) {
                    details += "HTTP " + (xhr.status || "-");
                    if (textStatus) {
                        details += " (" + textStatus + ")";
                    }
                    if (xhr.responseText) {
                        details += "<br/>" + esc(String(xhr.responseText).substring(0, 250));
                    }
                }
                jq("#transfer-clinician-body").html(
                    "<div style='color:red;'>Error retrieving transfer information.</div>" +
                    "<div style='margin-top:8px; font-size:12px; color:#555;'>URL: " + esc(requestUrl) + "</div>" +
                    "<div style='margin-top:4px; font-size:12px; color:#555;'>" + details + "</div>"
                );
            });
        });

        jq(document).on("click", "#transfer-quick-view-dialog .cancel", function(e) {
            e.preventDefault();
            hideTransferModal();
        });
    })(jq);
</script>
