<script type="text/javascript">
    var sessionLocationModel = {
        id: () => "${ sessionContext.sessionLocationId }",
        text: () => "${ ui.escapeJs(ui.encodeHtmlContent(ui.format(sessionContext.sessionLocation))) }"
    }
</script>
<header>
    <nav class="navbar navbar-expand-lg navbar-dark navigation">
        <div class="logo">
            <a href="/">
                <img src="${ ui.resourceLink("file", "configuration/globalresources/images/rwandaLogo.png") }"/>
            </a>
        </div>
        <% if (context.authenticated) { %>
            <button class="navbar-toggler" type="button" data-toggle="collapse" data-target="#navbarSupportedContent" aria-controls="navbarSupportedContent" aria-expanded="false" aria-label="Toggle navigation">
                <span class="navbar-toggler-icon"></span>
            </button>
            <div class="collapse navbar-collapse" id="navbarSupportedContent">
                <ul class="navbar-nav ml-auto user-options">
                    <li class="nav-item">
                        <a href="${ui.pageLink("authenticationui", "account/userAccount")}">
                            <i class="icon-user small"></i>
                            ${ context.authenticatedUser.username ?: context.authenticatedUser.systemId }
                        </a>
                    </li>
                    <% if (sessionContext.sessionLocation) { %>
                        <li class="nav-item">
                            <a href="${ui.pageLink("rwandaemr", "loginLocation")}">
                                <i class="icon-map-marker small"></i>
                                <span>${ sessionContext.sessionLocation.name }</span>
                            </a>
                        </li>
                    <% } %>
                    <li class="nav-item logout">
                        <a href="${ ui.actionLink("appui", "header", "logout", ["successUrl": contextPath]) }">
                            ${ui.message("emr.logout")}
                            <i class="icon-signout small"></i>
                        </a>
                    </li>
                </ul>
            </div>
        <% } %>
    </nav>
</header>
