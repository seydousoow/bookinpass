<#ftl output_format="HTML">
<#import "layout.ftl" as layout>

<@layout.emailHeader subject=subject lang="en"/>

<tr>
    <td class="mail-content">
        <p>Hello ${name},</p>
        <p>Thank you for your payment of <strong>${amount}</strong>.<#if receiptNumber?has_content> Receipt no. ${receiptNumber}.</#if></p>
        <p><a href="${receiptUrl}">View and download my receipt</a></p>
        <p class="footer-text">The receipt link is valid for 30 days; you can download it again anytime from "Manage my booking".</p>
        <@layout.booking lang="en"/>
    </td>
</tr>

<@layout.emailFooter lang="en"/>
