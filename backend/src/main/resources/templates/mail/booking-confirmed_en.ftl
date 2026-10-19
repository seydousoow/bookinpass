<#ftl output_format="HTML">
<#import "layout.ftl" as layout>

<@layout.emailHeader subject=subject lang="en"/>

<tr>
    <td class="mail-content">
        <p>Hello ${name},</p>
        <p>Your booking is confirmed. Please find your trip summary below.</p>
        <p><strong>Amount paid:</strong> ${amount}</p>
        <@layout.booking lang="en"/>
    </td>
</tr>

<@layout.emailFooter lang="en"/>
