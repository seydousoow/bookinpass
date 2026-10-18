<#-- Mise en page commune des e-mails de réservation. Gabarit provisoire, pour les tests : à remplacer. -->
<#macro page title>
<!doctype html>
<html lang="fr">
<head><meta charset="utf-8"><title>${title}</title></head>
<body style="margin:0;background:#f4f5f7;font-family:Arial,Helvetica,sans-serif;color:#1f2933">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0"><tr><td align="center" style="padding:24px 12px">
  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:560px;background:#ffffff;border-radius:12px">
    <tr><td style="background:#0b3d91;color:#ffffff;padding:20px 24px;border-radius:12px 12px 0 0;font-size:20px;font-weight:bold">Book In Pass</td></tr>
    <tr><td style="padding:24px">
      <p>Bonjour ${traveller},</p>
      <#nested>
      <p style="margin:16px 0 4px;font-weight:bold">Référence de réservation (PNR) : ${pnr}</p>
      <p style="margin:16px 0 4px;font-weight:bold">Vols</p>
      <ul style="margin:0;padding-left:20px">
        <#list segments as s>
          <li>${s.origin} → ${s.destination} — ${s.departure}<#if s.flight?has_content>, ${s.flight}</#if></li>
        <#else>
          <li>—</li>
        </#list>
      </ul>
      <p style="margin:16px 0 4px;font-weight:bold">Voyageurs</p>
      <ul style="margin:0;padding-left:20px">
        <#list passengers as p><li>${p}</li></#list>
      </ul>
      <p style="margin-top:24px"><a href="${manageUrl}" style="background:#0b3d91;color:#ffffff;padding:10px 16px;border-radius:8px;text-decoration:none">Gérer ma réservation</a></p>
      <p style="font-size:12px;color:#6b7280">Munissez-vous de votre PNR et du nom de famille d'un voyageur.</p>
    </td></tr>
  </table>
</td></tr></table>
</body>
</html>
</#macro>
