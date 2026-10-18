<#ftl output_format="HTML">
<#import "layout.ftl" as layout>

<@layout.emailHeader subject=subject lang="en"/>

<tr>
    <td class="mail-content">
        <p>Hello ${name},</p>
        <p>Please find attached invoice no. <strong>${invoiceNumber}</strong> for your booking, amounting to
            <strong>${amount}</strong> (paid).</p>
        <p><a href="${invoiceUrl}">Download the invoice</a></p>
        <@layout.booking lang="en"/>
    </td>
</tr>

<@layout.emailFooter lang="en"/>
