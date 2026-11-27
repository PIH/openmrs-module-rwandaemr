<%
    // Shown on the pihapps lab order page (extension point org.openmrs.module.pihapps.labOrder.fragments): warns when a
    // test needing the patient's insurer's approval is in the draft and, once the orders are saved, requests that
    // approval for the ticked tests (RHIP Unified Insurance Integration Guide: MMI, section 7)
%>
<div id="insurance-lab-approval-check" style="display:none; margin:12px 0; padding:12px 14px; border:1px solid #e0a800; border-left:5px solid #e0a800; background:#fff8e1; border-radius:4px;">
    <strong>These tests need <span class="insurance-lab-approval-insurer">the insurer</span>'s approval</strong>
    <div id="insurance-lab-approval-help" style="margin:4px 0 8px;">They will not be billed to <span class="insurance-lab-approval-insurer">the insurer</span> until it approves them.</div>
    <div id="insurance-lab-approval-list"></div>
    <div id="insurance-lab-approval-note-section" style="margin-top:8px; display:none;">
        <label for="insurance-lab-approval-note">Note for the approver (optional)</label><br/>
        <textarea id="insurance-lab-approval-note" rows="2" style="width:100%;"></textarea>
    </div>
</div>
<script type="text/javascript">
    (function() {
        var patientUuid = '${ ui.encodeJavaScript(config.patient.uuid) }';
        var restUrl = '/' + OPENMRS_CONTEXT_PATH + '/ws/rest/v1/rwandaemr/insuranceApproval/';
        var procedures = null;
        var canRequest = false;
        var current = [];
        var unticked = {};
        var listeners = window.pihappsLabOrderListeners = window.pihappsLabOrderListeners || [];

        function needingApproval() {
            var ret = [];
            if (!procedures) {
                return ret;
            }
            jq.each(current, function(i, uuid) {
                if (procedures[uuid]) {
                    ret.push(uuid);
                }
            });
            return ret;
        }

        function render() {
            var uuids = needingApproval();
            var list = jq('#insurance-lab-approval-list').empty();
            jq.each(uuids, function(i, uuid) {
                var procedure = procedures[uuid];
                var label = jq('<label style="display:block;"/>');
                if (canRequest) {
                    var box = jq('<input type="checkbox"/>').prop('checked', !unticked[procedure.code]);
                    box.on('change', function() {
                        unticked[procedure.code] = !jq(this).is(':checked');
                    });
                    label.append(box).append(' ');
                }
                label.append(document.createTextNode(procedure.name + ' (' + procedure.code + ')'));
                list.append(label);
            });
            jq('#insurance-lab-approval-check').toggle(uuids.length > 0);
        }

        jq.getJSON(restUrl + 'procedures', { patient: patientUuid }).done(function(answer) {
            if (!answer || !answer.checked) {
                return;
            }
            jq('.insurance-lab-approval-insurer').text(answer.insuranceName || 'the insurer');
            procedures = answer.procedures || {};
            canRequest = answer.canRequest;
            jq('#insurance-lab-approval-help').append(canRequest
                ? ' The ticked ones are sent for approval when you save; untick a test you will not do.'
                : jq('<span style="color:#b00020;"/>').text(' This patient has no MMI reception yet, so approval cannot be requested. Register the MMI reception first.'));
            jq('#insurance-lab-approval-note-section').toggle(canRequest);
            render();
        });

        listeners.push({
            onDraftChange: function(orderedConceptUuids) {
                current = orderedConceptUuids || [];
                render();
            },
            afterSave: function(encounter) {
                if (!canRequest || !encounter || !encounter.uuid) {
                    return;
                }
                var concepts = jq.grep(needingApproval(), function(uuid) {
                    return !unticked[procedures[uuid].code];
                });
                if (!concepts.length) {
                    return;
                }
                // A failure is recorded on the RHIP approvals page; it never keeps the orders from being saved
                return jq.ajax({
                    url: restUrl + 'request',
                    type: 'POST',
                    contentType: 'application/json; charset=utf-8',
                    dataType: 'json',
                    timeout: 20000,
                    data: JSON.stringify({ encounter: encounter.uuid, concepts: concepts, note: jq('#insurance-lab-approval-note').val() })
                }).then(function(answer) {
                    if (answer && !answer.success) {
                        console.warn('Insurance approval request failed: ' + answer.message);
                    }
                }, function() {
                    console.warn('Insurance approval request could not be sent');
                });
            }
        });
    })();
</script>
