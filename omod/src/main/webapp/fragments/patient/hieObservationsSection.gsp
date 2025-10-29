<div>
    <% if(observations.size() == 0 ) {
        %>
        ${ui.message("emr.none")}
        <%
    }
    %>
    <ul>
        <%
        observations.each { obsdata -> 
            %>
            <li class="clear">
                ${ui.format(obsdata.getComment()?:ui.message("emr.none"))}
                <span id="" class="tag">
                    ${ ui.format(obsdata.getValueText() ?: ui.message("emr.none"))}
                </span>
            </li>
            <%
        }
        %>
    </ul>

</div>
