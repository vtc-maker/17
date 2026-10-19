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

## Audit & vérification (2026-06, itération 4)
Audit statique complet du projet + compilation JVM (kotlinc 1.9.22, JDK 17) des couches pures.
- **Blocages de build corrigés** : icônes launcher absentes (adaptive icon `mipmap-anydpi-v26` +
  `ic_launcher_foreground`), `app/proguard-rules.pro` créé (règles kotlinx-serialization),
  `gradle.properties` créé (`android.useAndroidX=true` obligatoire), `gradle/wrapper/gradle-wrapper.properties`
  (Gradle 8.2), namespace `xmlns:tools` manquant dans `overlay_view.xml`, plugin kapt déclaré à la racine, `.gitignore`.
- **Bugs logiques corrigés** : regex parser débordant sur plusieurs lignes (`[^.]*?` → `[^.\n]*?`),
  offre à distance/durée unique rejetée (repli : 1 valeur = course), ancre `€` seule supprimée
  (faux positifs écran Gains), filtres Historique/Stats figés à l'instant de création (borne haute
  `Long.MAX_VALUE`), barres négatives dans les graphes, détection accessibilité par `ComponentName`
  (forme courte/longue), état réel de l'optimisation batterie, `hide()` ne démarre plus le FGS,
  `OverlayService.stop()` à la désactivation de l'accessibilité, type FGS `specialUse` explicite
  (Android 14), clavier `Decimal`, confirmation "✓ Paramètres enregistrés".
- **Tests** : `RideParser.parseTexts()` extrait (pur, testable) ; `RideParserTest` (7 cas) ajouté.
  11 tests JVM exécutés avec succès (ProfitabilityEngineTest 4 + RideParserTest 7).
- **README.md réécrit** : déploiement (Android Studio, debug, signature, APK/AAB), guide d'utilisation
  complet, formule de calcul, adaptation du parser, architecture, tests, Play Store, dépannage, limites.
- Non vérifiable ici : build Gradle Android complet (SDK absent), comportement a11y/overlay sur appareil.

- 2026-10-18: Ajout BUILD_ANDROID_STUDIO_DEBUTANT.md (guide build Android Studio débutant), lien depuis README §3.

- 2026-10-18: Refonte pop-up (style Drivee: plateforme · €/km · prix · €/h · min·km · jauge SUPER/MOYEN/MAUVAIS + seuil rentable), affichée par le service d'accessibilité (TYPE_ACCESSIBILITY_OVERLAY, plus de FGS/SYSTEM_ALERT_WINDOW), notification 'Drivee actif' au connect, Heetch ajouté, verdict = pire de €/km (seuil 1€) et €/h (seuil 30€), zone MOYEN 80%, bouton Tester la pop-up. DB v2.
- 2026-10-18: Son du verdict (AudioTrack synthétisé, guidage nav), Bolt: mètres + durée estimée (vitesse moyenne 35 km/h, '~'), tests captures réelles Uber/Bolt. DB v3.
- 2026-10-18: Diagnostic utilisateur = Uber n'expose pas la carte d'offre dans l'arbre a11y. Ajout OCR ML Kit (takeScreenshot API30+), isAccessibilityTool, prix = montant le plus proche des lignes km/min, bulle flottante D (pause/reprise), notification Pause/Arrêter, contrôles Dashboard.
- 2026-10-19: Bulle respirante (halo pulsé), appui bulle = ouvrir l'app, couleurs personnalisables (SUPER/MOYEN/MAUVAIS/anneau) via AppColors + ColorsSection, historique = courses ACCEPTÉES uniquement (AcceptanceTracker: clic Accepter/Refuser, écran de prise en charge, retour accueil, timeout 30 s), bouton Effacer l'historique.
- 2026-10-19: Renommage en 'VTC Copilot' (app_name + textes), nouveau logo (icône adaptative fond noir, logo dans la bulle et la pop-up).
- 2026-10-19: Fix €/h coupé dans la pop-up (fitFirstRow : réduction auto des tailles). Historique façon Drivee (À valider Pris/Pas pris, onglets prises/pas prises, période + dates), statut ACCEPTED/REJECTED/PENDING en base (v4), Stats (moyennes, qualité %, revenus par plateforme, Total CA, période/dates), footer 'Made by Faucobingo'.
- 2026-10-19: Import CSV (CsvImporter, colonnes par nom, dédoublonnage), export CSV enrichi (€/km brut, €/h brut, ratio, statut). Garde anti auto-lecture : pas d'OCR quand VTC Copilot est au premier plan; diagnostic à l'écran = compteurs seulement, journal partagé en fichier .txt.
- 2026-10-19: 16 KB : jniLibs useLegacyPackaging=true (libs ML Kit déjà ELF 16 KB, AGP 8.2 n'aligne pas le zip).
