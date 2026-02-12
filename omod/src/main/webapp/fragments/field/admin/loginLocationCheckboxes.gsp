<%
    def location = config.location
    def padding = config.padding
    def visitSelected = config.initialVisitLocations.contains(location) ? " checked" : ""
    def loginSelected = config.initialLoginLocations.contains(location) ? " checked" : ""
%>

<div style="padding-left: ${padding}px" class="login-location-checkboxes">
    ${location.name}
    <span style="padding-left: 10px;">
        <input type="checkbox" name="${config.visitLocationFormFieldName}" value="${location.id}" ${visitSelected} /> Visit Location
        <input type="checkbox" name="${config.loginLocationFormFieldName}" value="${location.id}" ${loginSelected} /> Login Location
    </span>
    <% if (location.childLocations && !location.childLocations.isEmpty()) { %>
        <% location.childLocations.each{ l -> %>
            <div style="padding-left: ${padding}px">
                ${ui.includeFragment("rwandaemr", "field/admin/loginLocationCheckboxes", [
                        "location": l,
                        "padding": padding,
                        "visitLocationFormFieldName": config.visitLocationFormFieldName,
                        "loginLocationFormFieldName": config.loginLocationFormFieldName,
                        "initialVisitLocations": config.initialVisitLocations,
                        "initialLoginLocations": config.initialLoginLocations
                ])}
            </div>
        <% } %>
    <% } %>
</div>