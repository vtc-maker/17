# PRD — Drivee Copilote (assistant de rentabilité VTC, Android natif)

## Problème original
L'utilisateur demande une app Android native (Kotlin) type "Drivee" : analyse en temps réel des
offres de course VTC (Uber/Bolt) via AccessibilityService, moteur de rentabilité paramétrable,
vignette flottante (overlay), app de config Compose (dashboard/paramètres/historique), et texte
de conformité Google Play.

## Décision de plateforme
La plateforme Emergent construit des apps Expo/React Native. Le cœur du produit (scraping
d'écran + overlay par-dessus d'autres apps) exige du code natif Android non testable ici.
**Choix utilisateur (1c)** : livrer le **code source Kotlin natif complet** en documentation
(projet Android/Gradle), sans exécution sur cette plateforme.

## Choix utilisateur retenus
- Livrable = code source Kotlin (Manifest, config a11y, RideParser, ProfitabilityEngine, OverlayService).
- Moteur : coût/km + commission % + charges % + objectif €/h (tout paramétrable).
- Historique : local uniquement (Room).
- Thème : sombre "cockpit".
- Marché France : euros, virgule décimale, packages com.ubercab.driver & ee.mtakso.driver, regex souples.

## Architecture
Kotlin + MVVM/Clean + Jetpack Compose + Hilt + Room + Coroutines. minSdk 26 / targetSdk 34.
Couches : data (Room + config parser dynamique), domain (modèles + ProfitabilityEngine pur),
service (AccessibilityService + RideParser + OverlayService), ui (Compose : dashboard/settings/history).

## Livré (2026-06)
- README.md : architecture complète + plan de dev pas à pas.
- AndroidManifest.xml : permissions + déclaration des services (a11y, overlay foreground specialUse).
- accessibility_service_config.xml : flags (flagReportViewIds, flagRetrieveInteractiveWindows…).
- RideParser.kt : extraction robuste (parcours d'arbre borné, regex config dynamique, repli heuristique).
- ProfitabilityEngine.kt : logique pure + tests unitaires JVM (4 cas).
- OverlayService.kt : vignette WindowManager, cycle de vie sûr, drag, auto-dismiss, foreground.
- RideAccessibilityService.kt : écoute events, throttling, dédup, sauvegarde Room.
- Couche data complète (entities/DAO/DB/repositories) + config parser Uber/Bolt par défaut.
- UI Compose : Dashboard (permissions), Paramètres (formulaire calcul + seuils), Historique (filtre + moyenne).
- Thème sombre cockpit, ressources (strings/colors/drawables/themes), fichiers Gradle (KTS).
- PRIVACY_POLICY_FR.md : Prominent Disclosure + politique de confidentialité + justif Play Console.

## Backlog / prochaines étapes
- P2 : mise à jour distante de ParserConfig (JSON téléchargé + cache) pour suivre les changements d'UI VTC.
- P2 : stats par jour/semaine, graphes de rentabilité.
- P2 : profils de véhicule multiples, détection auto brut/net par plateforme.
- P2 : réglage de la taille/position mémorisée de la vignette.

## Ajouts (2026-06, itération 2)
- Onboarding : écran d'accueil `OnboardingScreen` + `DisclosureText`/`DisclosureBody` réutilisables,
  flag persistant `OnboardingPrefs` (SharedPreferences). Divulgation affichée avant toute
  redirection vers les réglages d'accessibilité (gate au 1er lancement + boîte de dialogue sur
  le tableau de bord). Conforme à l'exigence Google Play.
- Export CSV : `CsvExporter` (séparateur ';', décimales virgule, BOM UTF-8), partage via
  `FileProvider` (aucune permission de stockage), bouton "Exporter CSV" dans l'historique
  exportant les courses de la période filtrée.

## Ajouts (2026-06, itération 3)
- Onglet "Stats" (4e tab) : `StatsViewModel` agrège l'historique des 6 dernières semaines.
- `StatsScreen` : graphes à barres (Canvas, sans dépendance externe) du revenu net par jour
  (semaine en cours) et par semaine (6 dernières), + cartes résumé (net, courses, moyenne €/h).
- `WeeklySummary` : génère un récap hebdomadaire texte + pièce jointe CSV de la semaine,
  partageable en un tap via `FileProvider` (bouton "Partager le récap hebdo").
- Navigation passée à 4 onglets : Accueil / Stats / Historique / Réglages.

## Notes
- Non exécutable sur Expo/preview ; ouvrir dans Android Studio, tester sur appareil réel.
- Aucune donnée d'écran transmise en ligne (traitement 100 % local).
