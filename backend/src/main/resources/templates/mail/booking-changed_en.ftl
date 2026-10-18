<#ftl output_format="HTML">
<#import "layout.ftl" as layout>

<@layout.emailHeader subject=subject lang="en"/>

<tr>
    <td class="mail-content">
        <p>Hello ${name},</p>
        <p>The airline has changed your booking (schedule or flight). Please check your new itinerary below and contact us if it does not suit
            you.</p>
        <@layout.booking lang="en"/>
    </td>
</tr>

<@layout.emailFooter lang="en"/>
