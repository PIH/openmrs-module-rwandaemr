<%
ui.includeJavascript("rwandaemr", "custom/hie.js")
ui.includeCss("rwandaemr", "hie/hie.css")
%>

<div id="payment-view-dialog" class="dialog" style="display: none">
    <div class="dialog-header">
        <i class="icon-check-in"></i>
        <h3 id="payment_request_title">
            ${ ui.message("rwandaemr.billing.paymentrequest.title") }
        </h3>
    </div>
    <div class="dialog-content">
        <div id="payment-data"></div>
        <button id="" class="confirm right">${ ui.message("rwandaemr.hie.done") }<i class="icon-spinner icon-spin icon-2x" style="display: none; margin-left: 10px;"></i></button>
        <button class="cancel">${ ui.message("coreapps.cancel") }</button>
    </div>
</div>
<div class="info-section">
    <div class="info-header">
        <i class="icon-calendar"></i>
        <h3>${ ui.message(config.label ? config.label : "rwandaemr.clinicianfacing.paymentRequestLabel").toUpperCase() }</h3>
        
    </div>
    <div class="info-body">
        <g:if test="${error}">
            <span style="color: red;">${error}</span>
        </g:if>
        <% if(bills.size() == 0) { %>
            <span style="color: red;">${ ui.message("emr.none") }</span>
        <% } %>
        <%
        if(bills.size() > 0) {
            %>
            <table id="bills-list-table">
                <thead>
                    <tr>
                        <th>${ ui.message("rwandaemr.paymentrequest.amount") }</th>
                        <th>${ ui.message("rwandaemr.paymentrequest.service") }</th>
                        <th>${ ui.message("rwandaemr.paymentrequest.invoice") }</th>
                    </tr>
                </thead>
                <tbody>
                    <% bills.each { bill ->
                        def pageLink
                        %>
                        <tr id="bill-${ bill.patientBillId }" class="bill-row${pageLink ? ' pointer' :''}" data-href="#">
                            <td>
                                ${ ui.format(bill.amount) }
                            </td>
                            <td>
                                
                            </td>
                            <td>
                                <a class="open_payment_request_pop" data-bill_amount="${ ui.format(bill.amount) }" data-bill_id="${ ui.format(bill.patientBillId) }" data-invoice_number="${ ui.format(bill.invoiceNumber) }" data-url='${ui.pageLink("rwandaemr", "patient/iremboPayStatusSection")}?billId=${bill.patientBillId}'" title="Create Payment Request" href="javascript:hie.showPaymentViewDialog('${bill.patientBillId}')">${ ui.format(bill.invoiceNumber) } <i class="icon-share-alt right"></i></a>
                            </td>
                        </tr>
                        <% 
                    }
                    %>
                </tbody>
            </table>
            <%
        }
        %>
    </div>
</div>

<script type="text/javascript">
    jq(".open_payment_request_pop").click(function (e) {
        //e.preventDefault();

        var clicked = jq(this);
        //console.log(clicked.data("uuid"));
        var url = window.location.origin + clicked.data("url");
        jq("#payment_request_title").html(clicked.data("encounter_type"));
        // jq("p#payment_invoice_number").html("Invoice: <b>" + clicked.data("invoice_number") + "</b>");
        // jq("p#payment_invoice_amount").html("Amount: <b>" + clicked.data("bill_amount") + " RWF</b>");
        jq("#payment-data").load(url, function (response, status, xhr) {
            if (status == "error") {
                jq("#payment-data").html("<p>Error Irembo Pay Parameters.</p>");
            }
        });

    });
</script>