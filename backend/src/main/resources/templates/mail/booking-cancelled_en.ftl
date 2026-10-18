<#ftl output_format="HTML">
<#import "layout.ftl" as layout>

<@layout.emailHeader subject=subject lang="en"/>

<tr>
    <td class="mail-content">
        <p>Hello ${name},</p>
        <p>Your booking has been cancelled.</p>
        <p><strong>Refund announced by the airline:</strong> ${refund}</p>
        <@layout.booking lang="en"/>
    </td>
</tr>

<@layout.emailFooter lang="en"/>
