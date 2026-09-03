# Book In Pass — Note de cadrage

**Objet :** lecture critique du cahier UX/UI v2.0 et du dossier de maquettes, avant démarrage du
développement front.
**Destinataire :** produit / direction.
**Date :** 30 août 2026.

---

## 1. Objet et méthode

Trois documents ont été analysés :

| Document | Nature |
|---|---|
| `BookInPass_Cahier_UX_UI_Developpeur_v2.pdf` | Cahier de référence — 22 pages, 93 fiches écran, design system, modèle de données, 20 critères de recette |
| `bookinpass_design_pages.html` | Dossier de maquettes — 14 planches HTML/CSS générées par script |
| `bookinpass_design_pages.pdf` | Version imprimable des mêmes planches |

La méthode est volontairement factuelle : tout ce qui est chiffré dans ce document a été **mesuré
sur les fichiers eux-mêmes**, pas estimé. Les commandes de vérification sont en annexe (§12), pour
que chaque nombre soit reproductible et contestable.

Le verdict d'ensemble tient en une phrase : **le cahier est de bonne qualité, mais il n'est pas
exécutable en l'état.** Il décrit correctement ce qu'il faut construire ; il sous-estime ce qui reste
à décider, et il est en désaccord avec les maquettes sur plusieurs fondamentaux. Les arbitrages
rendus récemment lèvent l'essentiel des blocages ; ce document acte ce qui est tranché et isole ce
qui ne l'est pas.

---

## 2. Synthèse

### Ce qui est tranché

| Sujet | Décision |
|---|---|
| Source des données | Toutes les verticales passent par le **backend propre**, développé en parallèle. Le front ne parle à aucun fournisseur. |
| Fournisseur vol | **Duffel**, derrière le backend |
| Paiement | **Stripe** côté backend, **+ Wave + Orange Money** |
| Taux de change | **Conversion statique**, source de taux réels plus tard |
| Moteur Budget | **IA**, mais **différé** |
| Maquettes | **La maquette HTML fait foi** en cas de conflit avec le cahier |
| Typographie | Échelle de la maquette **non viable, à réajuster** ; famille **Segoe UI** conservée |
| Front | Angular 22 + Vite + Tailwind 4 + signals, Vitest, SSR dès le départ |

### Les trois constats qui changent le chiffrage

1. **Les maquettes couvrent 16 % du périmètre.** 15 écrans sur 93 disposent d'un visuel exploitable,
   7 d'un fragment, **71 n'ont rien**. Toute estimation construite « d'après les maquettes »
   sous-estime le travail d'un facteur voisin de 6. (§3)
2. **89 écrans sur 93 sont marqués P0.** Un périmètre de lancement à 89 écrans, back-office compris,
   n'est pas un MVP. Une recoupe est proposée : **33 écrans pour le lancement 1**. (§4)
3. **La typographie dessinée est sous le plancher que le cahier impose lui-même**, et la police
   retenue ne sera pas visible par la majorité des utilisateurs cibles. (§6)

### Ce qui reste à décider

Sept points, listés en §11. Aucun n'est technique : ce sont des arbitrages produit et de marque.

---

## 3. Couverture réelle des maquettes

C'est le chiffre le plus important du document, parce qu'il conditionne tous les autres.

**15 écrans maquettés · 7 partiels · 71 absents.**

| Légende | Sens |
|---|---|
| ● | Visuel dédié et exploitable |
| ◐ | Fragment visible dans une autre planche, pas d'écran propre |
| ○ | Aucun visuel |

### Socle public — 1 ● · 1 ◐ · 7 ○

| ID | Écran | Route | Prio | Maquette |
|---|---|---|---|---|
| C01 | Accueil | `/` | P0 | ● planches 3 et 4 |
| C02 | Explorer | `/explore` | P0 | ○ |
| C03 | Offres du moment | `/deals` | P1 | ○ |
| C04 | Aide et FAQ | `/help` | P0 | ○ |
| C05 | Contact | `/contact` | P0 | ◐ planche 13 (centre d'aide) |
| C06 | Connexion | `/login` | P0 | ○ |
| C07 | Inscription et vérification | `/register` | P0 | ○ |
| C08 | Récupération d'accès | `/forgot-password` | P0 | ○ |
| C09 | Pages légales | `/legal/:slug` | P0 | ○ |

### Transport — 4 ● · 1 ◐ · 20 ○

| ID | Écran | Route | Prio | Maquette |
|---|---|---|---|---|
| T01 | Hub Transport | `/transport` | P0 | ○ |
| F01 | Recherche de vols | `/transport/flights` | P0 | ◐ moteur des planches 3 et 4 |
| F02 | Résultats vols | `/transport/flights/results` | P0 | ● planche 5 |
| F03 | Détail et tarif vol | `/transport/flights/offer/:id` | P0 | ● planche 6 |
| F04 | Voyageurs vol | `/transport/flights/travelers` | P0 | ○ |
| F05 | Options vol | `/transport/flights/extras` | P1 | ○ |
| F06 | Vérification vol | `/transport/flights/review` | P0 | ○ |
| F07 | Confirmation vol | `/booking/:id/flight` | P0 | ○ |
| U01–U06 | **Bus** (6 écrans) | `/transport/bus/…` | P0 | ○ **aucun** |
| N01–N06 | **Bateau** (6 écrans) | `/transport/boat/…` | P0 | ○ **aucun** |
| A01 | Recherche transfert AIBD | `/transport/airport-transfer` | P0 | ● planche 6 (écran mobile) |
| A02 | Choix véhicule | `…/vehicles` | P0 | ● planche 6 (même écran) |
| A03 | Détails de prise en charge | `…/details` | P0 | ○ |
| A04 | Vérification transfert | `…/review` | P0 | ○ |
| A05 | Confirmation et suivi | `/booking/:id/transfer` | P0 | ○ |

### Tourisme et sport — 3 ● · 1 ◐ · 8 ○

| ID | Écran | Route | Prio | Maquette |
|---|---|---|---|---|
| X01 | Accueil tourisme | `/tourism` | P0 | ● planche 7 |
| X02 | Résultats tourisme | `/tourism/results` | P0 | ◐ grille de la planche 7 |
| X03 | Détail activité | `/tourism/:id` | P0 | ○ |
| X04 | Date, créneau et billets | `/tourism/:id/book` | P0 | ○ |
| X05 | Visiteurs et vérification | `/tourism/:id/review` | P0 | ○ |
| X06 | Pass tourisme | `/booking/:id/tourism` | P0 | ○ |
| S01 | Accueil sport | `/sport` | P0 | ○ |
| S02 | Résultats sport | `/sport/results` | P0 | ○ |
| S03 | Détail événement sportif | `/sport/:id` | P0 | ● planche 8 |
| S04 | Places et catégories | `/sport/:id/tickets` | P0 | ● planche 8 |
| S05 | Participants et vérification | `/sport/:id/review` | P0 | ○ |
| S06 | E-ticket sport | `/booking/:id/sport` | P0 | ○ |

### Hébergement — 1 ● · 5 ○

| ID | Écran | Route | Prio | Maquette |
|---|---|---|---|---|
| H01 | Recherche hébergement | `/stays` | P0 | ○ |
| H02 | Résultats hébergement | `/stays/results` | P0 | ● planche 9 |
| H03 | Détail hébergement | `/stays/:id` | P0 | ○ |
| H04 | Choix chambre | `/stays/:id/rooms` | P0 | ○ |
| H05 | Voyageurs et vérification | `/stays/review` | P0 | ○ |
| H06 | Confirmation séjour | `/booking/:id/stay` | P0 | ○ |

### Cadeau et Budget — 4 ● · 2 ◐ · 5 ○

| ID | Écran | Route | Prio | Maquette |
|---|---|---|---|---|
| G01 | Accueil Cadeau | `/gift` | P0 | ○ |
| G02 | Choix du cadeau | `/gift/select` | P0 | ○ |
| G03 | Personnalisation | `/gift/customize` | P0 | ● planche 11 |
| G04 | Envoi et retrait | `/gift/delivery` | P0 | ◐ bloc livraison, planche 11 |
| G05 | Confirmation cadeau | `/gift/:id/confirmation` | P0 | ○ |
| G06 | Retrait ou échange | `/gift/redeem/:token` | P0 | ○ |
| P01 | Budget et groupe | `/budget` | P0 | ● planche 10 |
| P02 | Préférences | `/budget/preferences` | P0 | ● planche 10 (même formulaire) |
| P03 | Recommandations | `/budget/recommendations` | P0 | ● planche 10 |
| P04 | Détail du programme | `…/:id` | P0 | ◐ chronologie, planche 10 |
| P05 | Remplacer, enregistrer, partager | `…/:id/edit` | P0 | ○ |

### Panier, paiement et compte — 2 ● · 2 ◐ · 11 ○

| ID | Écran | Route | Prio | Maquette |
|---|---|---|---|---|
| K01 | Panier multi-service | `/cart` | P0 | ◐ récapitulatif, planche 12 |
| K02 | Contact et facturation | `/checkout/contact` | P0 | ○ |
| K03 | Vérification finale | `/checkout/review` | P0 | ○ |
| K04 | Paiement | `/checkout/payment` | P0 | ● planche 12 |
| K05 | **Traitement** | `/checkout/processing` | P0 | ○ **critique, voir §8** |
| K06 | **Résultat paiement** | `/checkout/result/:orderId` | P0 | ○ **critique, voir §8** |
| M01 | Tableau de bord | `/account` | P0 | ○ |
| M02 | Mes réservations | `/account/bookings` | P0 | ○ |
| M03 | Détail réservation | `/account/bookings/:id` | P0 | ○ |
| M04 | Mes billets | `/account/tickets` | P0 | ● planche 13 |
| M05 | Mes cadeaux | `/account/gifts` | P0 | ○ |
| M06 | Favoris et programmes | `/account/saved` | P1 | ○ |
| M07 | Profil et voyageurs | `/account/profile` | P0 | ○ |
| M08 | Remboursements et support | `/account/support` | P0 | ◐ planche 13 |
| M09 | Notifications et sécurité | `/account/settings` | P0 | ○ |

### Back-office — 0 ● · 0 ◐ · 15 ○

**ADM01 à ADM15 : aucun écran n'est maquetté.** Tous sont marqués P0. C'est le plus gros angle mort
du dossier : 15 écrans denses (tableaux, filtres, tri, pagination, export, actions tracées) sans
aucune direction visuelle, alors que le cahier leur consacre deux pages de spécifications
fonctionnelles précises.

### Ce qu'il faut en retenir

Le dossier de maquettes est un **support de présentation**, pas un dossier de conception. Il montre
bien l'intention et le style, il ne couvre pas le produit. Trois zones entières sont à concevoir
avant de pouvoir être développées : **le back-office (15 écrans), les parcours bus et bateau
(12 écrans), et l'ensemble du tunnel d'achat et du compte (17 écrans)**.

---

## 4. Le périmètre P0 n'est pas un MVP

### Le constat

Comptage sur le texte du cahier : **89 écrans marqués P0, 4 marqués P1**, sur 93.

Les quatre seuls P1 sont C03 `/deals`, F05 `/transport/flights/extras`, M06 `/account/saved` et
ADM14 `/admin/content`. Autrement dit, la priorisation annoncée en couverture (« P0 — MVP priorisé »)
n'a pas été faite : **tout est prioritaire, donc rien ne l'est**. Le back-office au complet, les
trois modes de transport secondaires et l'intégralité du compte client sont dans le même lot que
l'accueil.

### La recoupe proposée

Elle s'appuie sur deux principes : **une verticale complète vaut mieux que six verticales
incomplètes**, et **on ne livre pas un écran dont la source de données n'est pas prête**.

| Lancement | Contenu | Écrans |
|---|---|---|
| **1 — Vol de bout en bout** | Socle public (C01, C04–C09) · Vol (F01–F04, F06, F07) · Tunnel complet (K01–K06) · Compte (M01–M04, M07–M09) · Back-office minimal (ADM01–ADM03, ADM11–ADM13, ADM15) | **33** |
| 2 — Tourisme et sport | X01–X06 · S01–S06 · ADM04 · ADM05 | 14 |
| 3 — Hébergement | H01–H06 · ADM07 | 7 |
| 4 — Cadeau | G01–G06 · M05 · ADM09 | 8 |
| 5 — Transport local | T01 · U01–U06 · N01–N06 · A01–A05 · ADM06 · ADM08 | 20 |
| 6 — Budget (IA) | P01–P05 · ADM10 | 6 |
| Reliquat | C02 · C03 · F05 · M06 · ADM14 | 5 |
| | **Total** | **93** |

**33 écrans au lancement 1** au lieu de 89. C'est un périmètre tenable, qui produit un produit
réellement utilisable — on peut chercher un vol, le payer et retrouver son billet — et qui met en
place *tous* les mécanismes transverses (recherche, offre, panier, paiement, billet, compte,
support, back-office). Les lancements suivants réutilisent ces mécanismes au lieu de les réinventer.

Deux remarques sur cette recoupe :

- **Le back-office ne peut pas être reporté en bloc.** On ne met pas un service de paiement en
  production sans écran pour retrouver une commande, rapprocher un paiement et répondre à un client.
  Sept écrans d'administration sont donc dans le lancement 1 ; les huit autres suivent leur verticale.
- **Le transport local passe en lancement 5**, alors que le cahier le plaçait en 4ᵉ position. Motif :
  bus, bateau et transfert AIBD représentent 17 écrans dont **aucun n'est maquetté** et dont le
  fonctionnement (plans de sièges, cabines, véhicules, zones tarifaires) est le plus éloigné de ce
  qui aura été construit pour le vol. C'est le lot le plus coûteux, pas le plus rapide.

---

## 5. Application de la règle « la maquette fait foi »

L'arbitrage est clair et il est le bon : entre un tableau de tokens et un rendu visible, le rendu
visible tranche. Il appelle toutefois trois précisions.

### 5.1 Une règle de complétion est indispensable

La maquette **est muette sur 78 écrans sur 93**. Elle est muette aussi sur des éléments dont le
cahier a besoin — au premier rang desquels **le panier**, absent du header dessiné alors que K01 est
un panier multi-service et que le cahier §3.1 le place explicitement dans l'en-tête.

Règle à retenir, et à écrire dans le design system :

> **La maquette fait foi en cas de conflit. Le cahier fait foi en cas de silence.**

Sans cette seconde moitié, la consigne se retourne en blocage dès la première question non dessinée,
c'est-à-dire immédiatement.

### 5.2 La palette retenue

Celle de la maquette, qui l'emporte sur le §6.1 du cahier :

| Rôle | Retenu (maquette) | Écarté (cahier §6.1) |
|---|---|---|
| Violet principal | `#5125D7` | ~~`#4F22D0`~~ |
| Violet profond | `#32106F` | ~~`#29105F`~~ |
| Orange action | `#FF6B1A` | `#FF6B1A` — identique |
| Lilas / sélection | `#EEE9FF` | ~~`#F0ECFF`~~ |
| Fond de page | `#F5F3FB` | ~~`#F7F7FC`~~ |
| Texte principal | `#171428` | `#171428` — identique |
| Texte secondaire | `#706B82` | ~~`#667085`~~ |

**Réserve à traiter :** la maquette n'est pas cohérente avec elle-même. `#4F22D0` — la valeur du
*cahier* — y est codée en dur dans trois dégradés (`.scorecard`, `.m-ticket-top`, `.order-thumb`).
Les deux palettes coexistent dans le même fichier. Ces trois occurrences seront normalisées sur
`#5125D7` lors de la reprise des tokens.

### 5.3 Les rayons

La maquette l'emporte, mais elle emploie **sept valeurs** (10, 11, 13, 14, 15, 17 et 18 px) là où un
design system en demande trois ou quatre. Le cahier en proposait trois (12 / 14 / 18), une annotation
de la planche 2 en annonce une quatrième (« rayon principal de 16 px ») — trois sources, trois
systèmes.

Proposition, fidèle au rendu dessiné et tenable dans le temps :

| Usage | Rayon |
|---|---|
| Bouton | 10 px |
| Champ de formulaire | 12 px |
| Carte | 14 px |
| Panneau, modale, grande surface | 18 px |
| Badge, pastille | plein (pill) |

### 5.4 La navigation

La maquette l'emporte : barre mobile **Accueil · Explorer · Billets · Cadeaux · Compte**, là où le
cahier §3.2 plaçait **Budget** en troisième position.

Observation utile : **le moteur Budget étant différé, le choix de la maquette devient le bon choix
pour le MVP.** Les deux décisions convergent — il n'y a pas lieu de réserver une entrée de navigation
permanente à une fonction qui ne sera pas livrée. À réexaminer au lancement 6.

Reste à replacer le **panier** dans l'en-tête desktop (silence de la maquette → cahier §3.1).

---

## 6. Typographie

### 6.1 L'échelle dessinée n'est pas viable — la mesure

Les planches sont dessinées à l'échelle **~0,71 à 0,75** : le téléphone fait 276 px utiles pour
représenter un viewport de 390 px ; la zone navigateur fait environ 1084 px pour représenter un cadre
de 1440 px.

Or le CSS des planches descend à **6 à 8 px**. Remis à l'échelle réelle, cela donne **8,5 à 11 px** —
sous le plancher de **12 px** que le cahier impose lui-même au §6.2. Les libellés de la barre de
navigation mobile (6 px dessinés) ressortiraient à environ 8,5 px sur un téléphone.

L'arbitrage rendu — « la maquette n'est pas viable côté typo, on réajuste » — est donc confirmé par
la mesure. Ce document ne se contente pas de le constater : il propose l'échelle de remplacement.

### 6.2 L'échelle proposée

Le cahier §6.2 offrait déjà une échelle saine. C'est la seule partie du cahier qu'il faut **garder
contre** la maquette :

| Niveau | Mobile | Desktop | Règle |
|---|---|---|---|
| Display | 34 / 40 — 700 | 48 / 56 — 700 | Un seul par page |
| H1 | 28 / 34 — 700 | 36 / 44 — 700 | Titre de page |
| H2 | 22 / 28 — 700 | 28 / 34 — 700 | Section |
| H3 | 18 / 24 — 600 | 20 / 28 — 600 | Sous-section, titre de carte |
| Corps | 16 / 24 — 400 | 16 / 24 — 400 | Texte courant |
| Corps réduit | 14 / 20 — 400 | 14 / 20 — 400 | Métadonnées, aide |
| Libellé | 12 / 16 — 600 | 12 / 16 — 600 | **Plancher absolu** |

Espacement inchangé : 4, 8, 12, 16, 24, 32, 48, 64.

### 6.3 La conséquence, qui n'est pas seulement typographique

Remonter la typographie sans rien changer d'autre fera **déborder les mises en page dessinées** :
elles ont été composées avec du texte deux fois trop petit. Règle de conception à appliquer aux
planches existantes :

> Chaque écran perd environ **un niveau d'information** par rapport à la planche. Ce qui tombe passe
> en second niveau (repli, infobulle) ou dans l'écran de détail.

Exemples concrets, tirés des planches :

- **Carte de résultat vol** (planche 5) : compagnie, horaires, durée, escales, deux puces de
  conditions, prix et bouton sur une seule ligne. À 12 px minimum, les puces passent sous la carte ou
  se réduisent à la plus discriminante.
- **Carte mobile** (planche 4, 120 px de large) : à 12 px de plancher, la carte doit passer à environ
  150 px, donc moins de cartes visibles — ou une mise en page verticale.
- **Barre de navigation mobile** : 5 entrées à 12 px sur 390 px reste confortable (78 px par entrée).
  Ce point-là passe sans difficulté.

### 6.4 Segoe UI : la famille est retenue, mais la majorité des utilisateurs ne la verra pas

C'est le point le moins visible du dossier et il mérite d'être dit clairement.

La maquette déclare `"Segoe UI", Arial, sans-serif` — une valeur unique dans tout le fichier. Or
**Segoe UI est une police livrée avec Windows** : elle est absente d'Android, d'iOS et de macOS, et
sa licence ne permet pas de la distribuer sur le web via `@font-face`.

Le cahier §2 vise explicitement « un téléphone d'entrée ou milieu de gamme » au Sénégal, donc un parc
très majoritairement **Android**. En pratique :

| Public | Ce qu'il verra |
|---|---|
| Clients sur Android | Le **fallback** — Roboto ou la sans système |
| Clients sur iPhone | Le **fallback** — San Francisco ou Helvetica |
| Agents back-office sur Windows | Segoe UI, la police dessinée |

Autrement dit : **la police choisie sera vue surtout par les équipes internes, pas par les clients.**

Ce n'est pas une raison de changer de famille — la décision est prise et elle est défendable. C'est
une raison de faire trois choses :

1. **Déclarer explicitement la pile complète** plutôt que de la subir :
   `"Segoe UI", system-ui, -apple-system, Roboto, "Helvetica Neue", Arial, sans-serif`.
   La maquette saute directement à Arial, ce qui laisse le rendu Android à la discrétion du
   navigateur.
2. **Calibrer et valider l'échelle du §6.2 sur le fallback Android, sur un téléphone réel.** Segoe UI
   et Roboto n'ont pas les mêmes proportions : une échelle jugée bonne sur un écran Windows ne se
   transpose pas telle quelle. Le rendu de référence doit être celui de la cible, pas celui du poste
   de conception.
3. **Poser la question une fois au client** : si l'identité visuelle doit être *identique sur tous
   les appareils*, il faut une police licenciable servie en `@font-face` ; sinon, on assume une
   variation Windows / Android. C'est une décision de marque, pas une décision technique.

---

## 7. Ce que Duffel impose au parcours

Duffel étant le fournisseur du vol, une contrainte de la distribution aérienne remonte jusqu'au
parcours client. Elle est vérifiée sur la documentation Duffel v2 et elle n'est pas négociable.

**Une offre de vol expire vite — typiquement en 30 minutes** (champ `expires_at`). Et surtout : les
prix renvoyés par les compagnies **ne sont pas garantis** au moment de la réservation. Il faut
re-solliciter l'offre juste avant de créer la commande, et le montant total **peut avoir changé**.

Conséquence produit, à assumer dans le parcours plutôt qu'à découvrir en recette :

- Le client doit **voir** que son offre a une durée de vie.
- Si le prix bouge entre la sélection et le paiement, il faut un **écran de re-confirmation** avec
  acceptation explicite — jamais un débit silencieux au nouveau prix, jamais un échec sec.
- Toute la saisie déjà faite (voyageurs, options, contact) doit être **intégralement conservée**.

Le cahier l'avait anticipé — F02 « prix revalidé avant sélection », F06 « toute variation de prix
exige une validation explicite », AC05 « une offre expirée se revalide sans perte de saisie ». Duffel
transforme ces bonnes intentions en **chemin nominal**. Ce n'est donc pas une finition de fin de
projet : c'est un mécanisme du socle, construit au premier lot et réutilisé par toutes les verticales.

Deux points de détail à inscrire au contrat d'API, pour éviter une reprise plus tard :

- Le **bagage inclus** (celui qui sert au filtre de F02, marqué P0) est une donnée **distincte** du
  **bagage payant** (`available_services` chez Duffel, écran F05, marqué P1). Les deux ne doivent pas
  être fusionnés dans le même champ.
- L'obligation de pièce d'identité varie selon l'offre (`passenger_identity_documents_required`).
  Les champs de F04 sont donc **conditionnels**, pas fixes.

---

## 8. Paiement : trois canaux, trois chemins de retour

La planche 12 montre un écran unique avec trois onglets (Wave, Orange Money, Carte). La réalité est
plus large, et elle touche directement le front.

**Stripe (carte).** Les données de carte ne doivent jamais atteindre le backend Book In Pass — c'est
AC18, et la maquette l'affirme elle-même : « Book In Pass ne conserve pas les données de carte ».
Cela impose que la saisie se fasse **dans un composant Stripe monté dans le navigateur**, ou par
redirection vers une page hébergée par Stripe. Contrainte technique associée : ce composant ne peut
pas s'exécuter au rendu serveur.

**Wave et Orange Money.** Parcours par redirection ou lien, confirmés par notification serveur
(webhook). Le navigateur ne voit jamais la confirmation : il ne peut **pas** conclure au succès.

D'où le point le plus important de cette section :

> **K05 (traitement) et K06 (résultat) ne sont pas des écrans secondaires.** Ce sont eux qui portent
> AC04 (un double clic ne crée pas deux transactions) et AC14 (un paiement incertain reste en attente
> jusqu'à vérification serveur). **Aucun des deux n'est maquetté.**

S'y ajoute un cas que le cahier traite bien mais qu'aucune maquette ne montre : la **confirmation
partielle**. Une commande groupée — un vol *et* une activité — peut voir une ligne confirmée et
l'autre échouer. K06 doit présenter un statut **par ligne** et proposer une action adaptée à chacune
(AC06). C'est un écran à concevoir, pas à décliner.

---

## 9. Conversion monétaire

La conversion statique est le bon choix pour un MVP : elle évite d'ouvrir le chantier « source de
taux » avant d'en avoir besoin. Deux règles doivent néanmoins être posées **maintenant**, parce
qu'elles coûtent cher à rattraper.

1. **Le taux vient du backend, jamais du front.** Même figé, il doit être servi par l'API. S'il est
   codé dans le front, le branchement futur d'une source réelle imposera un redéploiement front à
   chaque variation — exactement ce qu'on cherche à éviter.
2. **Quel montant fait foi ?** À l'écran de paiement, le client voit des francs CFA ; le fournisseur
   facture dans sa devise. Il faut décider lequel est contractuel, et si le taux et sa date sont
   affichés au client. C'est une question juridique autant que technique.

Note : l'équipe a déjà rencontré ce sujet sur le feed SBR d'Air Sénégal, où la présence de montants
multidevises bloquait tout calcul de panier moyen faute de politique de conversion. Même arbitrage —
autant le prendre une seule fois, ici, et le réutiliser.

---

## 10. Report du moteur Budget

Le report est cohérent avec les priorités, mais il a une conséquence qu'il faut énoncer.

Le cahier recommande lui-même **l'ordre inverse** de celui retenu (§1) : « la première version du
comparateur repose sur des règles contrôlables. Une couche IA peut suivre après collecte de données
fiables. » Le raisonnement est solide : un moteur de recommandation a besoin de données d'usage pour
être entraîné et évalué, et ces données n'existent pas avant d'avoir lancé.

Aller directement à l'IA revient donc à **livrer le produit sans comparateur**, ce qui est assumable
mais doit être dit : le comparateur « selon mon budget » est l'un des deux différenciateurs mis en
avant sur l'accueil dessiné, avec le cadeau. Le discours produit du lancement 1 repose donc sur un
seul des deux.

Deux points rassurants, à mettre en regard :

- Les écrans P01 à P05 sont **agnostiques du moteur**. Ils affichent trois plans, un total, un reste
  et un score de compatibilité — que ces valeurs viennent de règles ou d'un modèle ne change rien à
  l'interface. Ils seront construits tels quels le moment venu, **sans rejouer le design**.
- Le report libère 5 écrans du périmètre de lancement, ce qui va dans le sens de la recoupe du §4.

---

## 11. Décisions requises

Sept points. Aucun n'est technique.

| # | Question | Recommandation | Si on ne tranche pas |
|---|---|---|---|
| 1 | Valide-t-on l'échelle typographique du §6.2 ? | Oui, et on l'ajuste après test sur Android réel | Le design system ne peut pas être figé, donc aucun composant n'est stable |
| 2 | Faut-il une identité typographique identique sur tous les appareils ? | Non pour le MVP : pile de fallback explicite, rendu Windows/Android assumé | Le rendu client restera non maîtrisé et non testé |
| 3 | Valide-t-on la recoupe à 33 écrans pour le lancement 1 ? | Oui | Le chiffrage porte sur 89 écrans et le planning n'a pas de sens |
| 4 | Confirme-t-on le panier dans l'en-tête ? | Oui (cahier §3.1, maquette muette) | K01 n'est atteignable par aucun chemin |
| 5 | Quel montant fait foi, et affiche-t-on le taux ? | Montant en FCFA contractuel, taux affiché au récapitulatif | Risque juridique sur les commandes en devise |
| 6 | Traduction : i18n Angular natif ou Transloco ? | À figer avant le premier libellé écrit | Reprise ultérieure sur 93 écrans — le pire scénario |
| 7 | Qui produit le design des 78 écrans non maquettés, et quand ? | Par lot, en amont de chaque lancement | Le développement s'arrête à la fin du lancement 1 |

Les points 1, 3 et 7 conditionnent le démarrage. Les autres peuvent être tranchés pendant le lot 0.

---

## 12. Annexe — méthode de vérification

Tous les chiffres de ce document sont reproductibles depuis les fichiers sources.

**Extraction du texte du PDF.** `pdftotext` n'étant pas installé, l'extraction passe par PDFKit :

```bash
osascript -l JavaScript -e '
ObjC.import("Quartz");
var doc = $.PDFDocument.alloc.initWithURL(
  $.NSURL.fileURLWithPath("BookInPass_Cahier_UX_UI_Developpeur_v2.pdf"));
console.log(doc.string.js);'
```

**Comptage des priorités** — donne `P0=89, P1=4, total=93`, et 93 fiches au motif
`^[A-Z]{1,3}\d{2} - ` :

```bash
grep -c '| P0'   # 89
grep -c '| P1'   # 4
```

**Palette et rayons** — bloc `:root{…}` et déclarations `border-radius` de
`bookinpass_design_pages.html`, comparés aux tableaux §6.1 et §6.2 du cahier.

**Pile de police** — valeur unique dans tout le fichier :

```bash
grep -o 'font-family:[^;}]*' bookinpass_design_pages.html | sort -u
# font-family:"Segoe UI",Arial,sans-serif
```

**Échelle des planches** — téléphone dessiné : 292 px moins 8 px de bordure = 276 px utiles pour un
viewport de 390 px, soit 0,708. Zone navigateur : environ 1084 px pour un cadre de 1440 px, soit
0,753.

**Cohérence des montants de la maquette** — 285 000 × 2 = 570 000 ; + 8 000 de frais = 578 000 ;
+ 114 000 = 692 000 au récapitulatif de la planche 12. La chaîne de prix se tient de bout en bout,
ce qui est suffisamment rare pour être signalé au crédit du dossier.

**Comportement des offres Duffel** — expiration typique de 30 minutes, champ `expires_at`,
re-sollicitation obligatoire avant création de commande avec `total_amount` susceptible de changer,
`available_services`, `passenger_identity_documents_required` : documentation Duffel API v2,
page *Offers* — <https://duffel.com/docs/api/v2/offers>.

**Point non vérifiable depuis les fichiers.** Le statut de licence de Segoe UI (police Windows, non
distribuable en `@font-face`) relève des conditions Microsoft, pas d'une mesure sur les documents. À
faire confirmer avant diffusion externe de ce document.
