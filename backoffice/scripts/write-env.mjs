// Écrit la configuration d'exécution du back-office (env.js) dans le build, à partir de API_BASE_URL (Vercel).
// Sans API_BASE_URL, le env.js du dépôt (`/api`) est gardé.
import { writeFileSync } from 'node:fs';

const target = new URL('../dist/backoffice/browser/env.js', import.meta.url);
const apiBaseUrl = process.env.API_BASE_URL?.trim();

if (apiBaseUrl) {
  writeFileSync(target, `window.__env = ${JSON.stringify({ apiBaseUrl })};\n`);
  console.log(`env.js : apiBaseUrl = ${apiBaseUrl}`);
} else {
  console.log('env.js : API_BASE_URL absent, /api conservé');
}
