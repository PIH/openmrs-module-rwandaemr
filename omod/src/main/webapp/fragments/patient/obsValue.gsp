<%
    def obs = config.obs
    def padding = config.padding
%>
<% if (obs.obsGrouping) { %>
    <div class="obs-group">
        <% obs.groupMembers.eachWithIndex{ groupMember, index -> %>
            ${index == 0 ? "" : "<br/>"}
            <span style="padding: ${padding}px">
                ${ui.format(groupMember.concept)}: ${ui.includeFragment("rwandaemr", "patient/obsValue", ["obs": groupMember, "padding": padding+20])}
            </span>
        <% } %>
    </div>
<% } else { %>
    ${obs.getValueAsString(ui.locale)}
<% } %>
