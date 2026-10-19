<#ftl output_format="HTML">
<#import "layout.ftl" as layout>

<@layout.emailHeader subject=subject lang="fr"/>

<tr>
    <td class="mail-content">
        <p>Bonjour ${name},</p>
        <p>Votre réservation a bien été annulée.</p>
        <p><strong>Remboursement annoncé par la compagnie :</strong> ${refund}</p>
        <@layout.booking lang="fr"/>
    </td>
</tr>

<@layout.emailFooter lang="fr"/>
