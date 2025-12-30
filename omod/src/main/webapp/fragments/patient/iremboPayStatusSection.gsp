<div class="row">
    <div class="col-6" style="text-align: center; font-weight: bold; color: #00473f;">
        ${invoiceNumber}
    </div>
    <div class="col-6" style="text-align: center; font-weight: bold; color: #00473f;">
        ${amount} RWF
    </div>
</div>
<div class="row">
    ${ ui.includeFragment("uicommons", "field/text", [
        id: "phone_number",
        label: "Phone Number",
        formFieldName: "ownerCode",
        initialValue: (phoneNumber ?: ''),
        size: 30
    ])}
</div>