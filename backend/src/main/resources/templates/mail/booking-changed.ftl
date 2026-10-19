<#ftl output_format="HTML">
<#import "layout.ftl" as layout>

<@layout.emailHeader subject=subject lang="fr"/>

<tr>
    <td class="mail-content">
        <p>Bonjour ${name},</p>
        <p>La compagnie a modifié votre réservation (horaire ou vol). Vérifiez votre nouvel itinéraire ci-dessous ; contactez-nous s'il ne vous
            convient pas.</p>
        <@layout.booking lang="fr"/>
    </td>
</tr>

<@layout.emailFooter lang="fr"/>
