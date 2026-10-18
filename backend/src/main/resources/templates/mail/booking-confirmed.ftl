<#ftl output_format="HTML">
<#import "layout.ftl" as layout>

<@layout.emailHeader subject=subject lang="fr"/>

<tr>
    <td class="mail-content">
        <p>Bonjour ${name},</p>
        <p>Votre réservation est confirmée. Vous trouverez ci-dessous le récapitulatif de votre voyage.</p>
        <p><strong>Montant payé :</strong> ${amount}</p>
        <@layout.booking lang="fr"/>
    </td>
</tr>

<@layout.emailFooter lang="fr"/>
