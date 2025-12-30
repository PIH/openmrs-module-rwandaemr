<%@ include file="/WEB-INF/template/include.jsp"%>
<%@ include file="/WEB-INF/template/header.jsp"%>
<openmrs:require privilege="Edit Patients" otherwise="/login.htm" redirect="/module/rwandaemr/triggerSync.htm"/>

<script type="text/javascript" charset="utf-8">

	jQuery(document).ready(function() {

		// Redirect to listing page
		jQuery('#triggerButton').click(function(event){
			var pId = jQuery("#patientId").val();
			jQuery.get("${pageContext.request.contextPath}/module/rwandaemr/triggerSync.form?patientId=" + pId, function(data) {
				jQuery("#errorSection").html(data.error);
				jQuery("#messagesSection").html("");
				if (data && data.messages && data.messages.length > 0) {
					data.messages.forEach(function (message) {
						jQuery("#messagesSection").append(message).append("<br/><br/>");
					});
				}
			});
		});
	} );

</script>


<div class="boxHeader">
	<b>Trigger Sync for Patient</b>
</div>
<div class="box">
	<label for="patientId">Patient ID or UUID</label>
	<input id="patientId" type="text" size="50" name="patientId" />&nbsp;&nbsp;
	<input id="triggerButton" type="button" value="Trigger" />
</div>
<br/>
<div id="errorSection" class="error"></div>
<div id="messagesSection"></div>

<%@ include file="/WEB-INF/template/footer.jsp"%>