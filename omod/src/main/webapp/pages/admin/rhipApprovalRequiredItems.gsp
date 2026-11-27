<%
    ui.decorateWith("appui", "standardEmrPage")
    def openmrsContextPath = contextPath ? (contextPath.startsWith("/") ? contextPath : "/" + contextPath) : ""
    def pageLink = ui.pageLink("rwandaemr", "admin/rhipApprovalRequiredItems")
    def filterLink = { Map params ->
        def merged = [insuranceType: insuranceType, show: show, q: q] + params
        def query = merged.findAll { k, v -> v }.collect { k, v -> k + "=" + java.net.URLEncoder.encode(v.toString(), "UTF-8") }.join("&")
        return pageLink + (query ? "?" + query : "")
    }
%>

<script type="text/javascript">
    var breadcrumbs = [
        { icon: "icon-home", link: '${ openmrsContextPath }/index.htm' },
        { label: "${ ui.message("coreapps.app.system.administration.label") }", link: "${ ui.pageLink("coreapps", "systemadministration/systemAdministration") }" },
        { label: "RHIP Approvals", link: "${ ui.pageLink("rwandaemr", "admin/rhipApprovals") }" },
        { label: "Approval-Required Items" }
    ];
</script>

<style>
    #body-wrapper, #content {
        max-width: none !important;
        width: auto !important;
    }
    .arp-page { max-width: 1800px; margin: 0 auto; padding: 0 16px; color: #17203a; }
    .arp-header { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: flex-end; gap: 12px; margin: 6px 0 14px; }
    .arp-header h1 { margin: 0; font-size: 22px; font-weight: normal; }
    .arp-muted { color: #5f6b7a; font-size: 12px; }
    .arp-cards { display: flex; flex-wrap: wrap; gap: 10px; margin-bottom: 14px; }
    .arp-card { display: block; min-width: 170px; padding: 10px 14px; border: 1px solid #d8dfeb; border-radius: 6px; background: #ffffff; color: #17203a; text-decoration: none; }
    .arp-card strong { display: block; font-size: 22px; }
    .arp-card.arp-active { border-color: #2563eb; box-shadow: 0 0 0 1px #2563eb inset; }
    .arp-card-warning strong { color: #b45309; }
    .arp-filters { display: flex; flex-wrap: wrap; gap: 10px; align-items: flex-end; margin-bottom: 12px; }
    .arp-filters label { display: block; font-size: 12px; color: #5f6b7a; }
    .arp-filters select, .arp-filters input[type=text] { min-width: 180px; min-height: 32px; }
    .arp-table-wrap { border: 1px solid #d8dfeb; background: #ffffff; border-radius: 6px; overflow-x: auto; }
    table.arp-table { width: 100%; border-collapse: collapse; font-size: 13px; }
    .arp-table th { background: #f5f7fb; font-size: 12px; text-transform: uppercase; text-align: left; padding: 9px 8px; border-bottom: 1px solid #d8dfeb; }
    .arp-table td { padding: 8px; border-bottom: 1px solid #edf1f7; vertical-align: top; }
    .arp-nowrap { white-space: nowrap; }
    .arp-number { text-align: right; white-space: nowrap; }
    .arp-tag { display: inline-block; padding: 2px 8px; border-radius: 10px; font-size: 11px; font-weight: bold; }
    .arp-tag-missing { background: #fff4e5; color: #b45309; border: 1px solid #f6c77d; }
    .arp-tag-removed { background: #f1f5f9; color: #475569; border: 1px solid #cbd5e1; }
    .arp-empty { padding: 24px; text-align: center; color: #5f6b7a; }
</style>

<div class="arp-page">
    <div class="arp-header">
        <div>
            <h1>Approval-Required Items</h1>
            <div class="arp-muted">
                Procedures each insurer must approve before they are billed, from RHIP, and the billing item each one is linked to
                (by RHIC/RLTC code). Refreshed automatically every few hours.
                ${ lastRefresh ? "Last refreshed: " + ui.format(lastRefresh) + "." : "Not refreshed from RHIP yet." }
            </div>
        </div>
        <% if (canRefresh) { %>
            <form method="post" action="${ pageLink }">
                <input type="hidden" name="insuranceType" value="${ ui.escapeAttribute(insuranceType ?: "") }"/>
                <button type="submit" class="confirm"><i class="icon-refresh"></i> Refresh now</button>
            </form>
        <% } %>
    </div>

    <div class="arp-cards">
        <a class="arp-card ${ show == 'all' ? 'arp-active' : '' }" href="${ filterLink(show: 'all') }">
            <strong>${ linkedCount + notLinkedCount }</strong>Need approval
        </a>
        <a class="arp-card ${ show == 'linked' ? 'arp-active' : '' }" href="${ filterLink(show: 'linked') }">
            <strong>${ linkedCount }</strong>Linked to a billing item
        </a>
        <a class="arp-card arp-card-warning ${ show == 'unlinked' ? 'arp-active' : '' }" href="${ filterLink(show: 'unlinked') }">
            <strong>${ notLinkedCount }</strong>No billing item with this code
        </a>
        <a class="arp-card ${ show == 'removed' ? 'arp-active' : '' }" href="${ filterLink(show: 'removed') }">
            <strong>${ removedCount }</strong>No longer listed by RHIP
        </a>
    </div>

    <form class="arp-filters" method="get" action="${ pageLink }">
        <input type="hidden" name="show" value="${ ui.escapeAttribute(show ?: "") }"/>
        <div>
            <label>Insurance</label>
            <select name="insuranceType" onchange="this.form.submit();">
                <option value="">All insurances</option>
                <% insuranceTypes.each { type -> %>
                    <option value="${ ui.escapeAttribute(type) }" ${ type == insuranceType ? 'selected="selected"' : '' }>${ ui.format(type.toUpperCase()) }</option>
                <% } %>
            </select>
        </div>
        <div>
            <label>Search code, procedure or billing item</label>
            <input type="text" name="q" value="${ ui.escapeAttribute(q ?: "") }" placeholder="e.g. RHIC-OPTH or cataract"/>
        </div>
        <div><button type="submit">Search</button></div>
    </form>

    <div class="arp-table-wrap">
        <% if (rows) { %>
            <table class="arp-table">
                <thead>
                <tr>
                    <th>Insurance</th>
                    <th>Code</th>
                    <th>Procedure (RHIP)</th>
                    <th>Type</th>
                    <th class="arp-number">Max. Tariff</th>
                    <th>Billing Item</th>
                    <th>Listed Since</th>
                    <th>Last Seen</th>
                </tr>
                </thead>
                <tbody>
                <% rows.each { row -> %>
                    <tr>
                        <td class="arp-nowrap">${ ui.format((row.insuranceType ?: "").toUpperCase()) }</td>
                        <td class="arp-nowrap">${ ui.format(row.productCode ?: "") }</td>
                        <td>${ ui.format(row.productName ?: "") }</td>
                        <td>${ ui.format(row.productType ?: "") }</td>
                        <td class="arp-number">${ row.tariffPrice != null ? ui.format(row.tariffPrice) : "" }</td>
                        <td>
                            <% if (row.removed) { %>
                                <span class="arp-tag arp-tag-removed">No longer listed</span>
                            <% } %>
                            <% if (row.facilityServicePriceId) { %>
                                ${ ui.format(row.facilityServiceName ?: "") }
                            <% } else { %>
                                <span class="arp-tag arp-tag-missing">No billing item</span>
                            <% } %>
                        </td>
                        <td class="arp-nowrap">${ ui.format(row.firstSeen) }</td>
                        <td class="arp-nowrap">${ ui.format(row.lastSeen) }</td>
                    </tr>
                <% } %>
                </tbody>
            </table>
        <% } else { %>
            <div class="arp-empty">
                ${ lastRefresh ? "No procedure matches." : "The list has not been obtained from RHIP yet. It is refreshed a minute after startup and every few hours, or now with Refresh now." }
            </div>
        <% } %>
    </div>
</div>
