<%
    def roundedAmount = (amount != null ? Math.ceil(amount.toDouble()) : 0)
    def roundedAmountFormatted = String.format('%.2f', roundedAmount)
%>
<form id="irembo-pay-form" class="irembo-pay-form">
    <input type="hidden" name="billId" id="irembo-pay-billId" value="${billId}" />
    <div class="row">
        <div class="col-6" style="text-align: center; font-weight: bold; color: #00473f;">
            ${invoiceNumber}
        </div>
        <div class="col-6" style="text-align: center; font-weight: bold; color: #00473f;">
            ${roundedAmountFormatted} RWF
        </div>
    </div>
    <div class="row">
        <div class="col-12">
            <div class="alert alert-info text-center">Enter the phone number with ${roundedAmountFormatted} RWF on MoMo Balance to complete the payment</div>
        </div>
    </div>
    <div class="row">
        <div class="col-12">
            ${ ui.includeFragment("uicommons", "field/text", [
                id: "phone_number",
                label: "Phone Number",
                formFieldName: "ownerCode",
                initialValue: (phoneNumber ?: ''),
                size: 40
            ])}
        </div>
    </div>
</form>