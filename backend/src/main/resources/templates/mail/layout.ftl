<#ftl output_format="HTML">
<#-- Mise en page commune des e-mails. Gabarit provisoire, pour les tests : à remplacer par le gabarit définitif. -->
<#macro emailHeader subject lang>
    <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html lang="${lang}" xmlns="http://www.w3.org/1999/xhtml">
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Book In Pass | ${subject}</title>
    <style type="text/css">
        body, table, td, a {
            -webkit-text-size-adjust: 100%;
            -ms-text-size-adjust: 100%;
        }

        body {
            height: 100%;
            margin: 0;
            padding: 0;
            width: 100%;
            font-family: Arial, Helvetica, sans-serif;
            background: #f4f5f7;
        }

        table.mail {
            background-color: #ffffff;
            max-width: 600px;
            margin: 0 auto;
            padding: 20px;
            width: 100%;
        }

        .mail-content {
            font-size: 14px;
            color: #1f2933;
        }

        .footer-text {
            margin: 0;
            font-size: 11px;
            color: #6b7280;
        }

        .button {
            background: #0b3d91;
            color: #ffffff !important;
            padding: 10px 16px;
            border-radius: 8px;
            text-decoration: none;
            display: inline-block;
        }
    </style>
</head>
<body>
<table class="mail" align="center" border="0" cellpadding="0" cellspacing="0">
    <tr>
        <td style="border-bottom: 1px solid #eee; padding-bottom: 10px; font-size: 22px; font-weight: bold; color: #0b3d91;">Book In Pass</td>
    </tr>
    </#macro>

    <#-- Récapitulatif de réservation : PNR, vols, voyageurs (libellés selon la langue). -->
    <#macro booking lang>
        <p style="margin:16px 0 4px;font-weight:bold"><#if lang == "en">Booking reference (PNR)<#else>Référence de réservation (PNR)</#if>
            : ${pnr}</p>
        <p style="margin:16px 0 4px;font-weight:bold"><#if lang == "en">Flights<#else>Vols</#if></p>
        <ul style="margin:0;padding-left:20px">
            <#list segments![] as s>
                <li>${s.origin} → ${s.destination} — ${s.departure}<#if s.flight?has_content>, ${s.flight}</#if></li>
            <#else>
                <li>—</li>
            </#list>
        </ul>
        <p style="margin:16px 0 4px;font-weight:bold"><#if lang == "en">Travellers<#else>Voyageurs</#if></p>
        <ul style="margin:0;padding-left:20px">
            <#list passengers![] as p>
                <li>${p}</li></#list>
        </ul>
        <p style="margin-top:24px"><a class="button"
                                      href="${baseUrl}/flight/manage"><#if lang == "en">Manage my booking<#else>Gérer ma réservation</#if></a></p>
        <p class="footer-text"><#if lang == "en">You will need your PNR and the last name of one of the travellers.<#else>Munissez-vous de votre PNR et du nom de famille d'un voyageur.</#if></p>
    </#macro>

    <#macro emailFooter lang>
    <tr>
        <td style="border-top:1px solid #ddd; padding:15px 0 0;">
            <p class="footer-text">Book In Pass
                — <#if lang == "en">Flights, stays and activities in Senegal and beyond.<#else>Vols, séjours et activités au Sénégal et au-delà.</#if></p>
        </td>
    </tr>
</table>
</body>
</html>
</#macro>
