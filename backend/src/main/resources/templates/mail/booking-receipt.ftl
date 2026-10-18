<#ftl output_format="HTML">
<#import "layout.ftl" as layout>

<@layout.emailHeader subject=subject lang="fr"/>

<tr>
    <td class="mail-content">
        <p>Bonjour ${name},</p>
        <p>Merci pour votre paiement de <strong>${amount}</strong>.<#if receiptNumber?has_content> Reçu n° ${receiptNumber}.</#if></p>
        <p><a href="${receiptUrl}">Voir et télécharger mon reçu</a></p>
        <p class="footer-text">Le lien du reçu est valable 30 jours ; vous pouvez le retélécharger à tout moment depuis « Gérer ma réservation ».</p>
        <@layout.booking lang="fr"/>
    </td>
</tr>

<@layout.emailFooter lang="fr"/>
