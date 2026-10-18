<#ftl output_format="HTML">
<#import "layout.ftl" as layout>

<@layout.emailHeader subject=subject lang="fr"/>

<tr>
    <td class="mail-content">
        <p>Bonjour ${name},</p>
        <p>Vous trouverez en pièce jointe la facture n° <strong>${invoiceNumber}</strong> de votre réservation, d'un montant de
            <strong>${amount}</strong> (payée).</p>
        <p><a href="${invoiceUrl}">Télécharger la facture</a></p>
        <@layout.booking lang="fr"/>
    </td>
</tr>

<@layout.emailFooter lang="fr"/>
