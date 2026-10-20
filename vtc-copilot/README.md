# VTC Copilot 🚕

**Assistant de rentabilité pour chauffeurs VTC (Uber / Bolt / Heetch) — Android natif, Kotlin.**

VTC Copilot lit les offres de course affichées par Uber Driver, Bolt Driver ou Heetch Pro,
calcule instantanément leur rentabilité (€/km, €/h) et affiche une **pop-up** en haut de l'écran
avec un verdict **SUPER / MOYEN / MAUVAIS** et une jauge « seuil rentable ». Toutes les courses analysées
sont historisées **localement** (aucun serveur, aucune donnée envoyée) pour produire des
statistiques, un export CSV comptable et un récap hebdomadaire partageable.

> ⚠️ Ce projet est une **application Android native** (Kotlin + Jetpack Compose). Il se compile
> avec **Android Studio** et se teste sur un **téléphone Android réel** (l'accessibilité et
> l'overlay ne fonctionnent pas dans un simple aperçu web).

---

## Sommaire

1. [Fonctionnalités](#1-fonctionnalités)
2. [Prérequis](#2-prérequis)
3. [Compiler et installer (déploiement)](#3-compiler-et-installer-déploiement)
4. [Guide d'utilisation](#4-guide-dutilisation)
5. [Comprendre le calcul de rentabilité](#5-comprendre-le-calcul-de-rentabilité)
6. [Adapter le parser quand Uber/Bolt changent d'interface](#6-adapter-le-parser)
7. [Architecture du code](#7-architecture-du-code)
8. [Tests](#8-tests)
9. [Publication Google Play & conformité](#9-publication-google-play--conformité)
10. [Dépannage](#10-dépannage)
11. [Limites connues](#11-limites-connues)

---

## 1. Fonctionnalités

| Écran / Service | Ce que ça fait |
|---|---|
| **Onboarding** | Écran d'accueil affichant la divulgation exigée par Google avant toute activation de l'accessibilité. |
| **Tableau de bord** | État du service (réellement actif ou non), Notifications, Batterie, et bouton **Tester la pop-up**. |
| **Service d'accessibilité** | Tourne en permanence en arrière-plan, écoute `com.ubercab.driver`, `ee.mtakso.driver` et `com.heetch.driver`, parcourt toutes leurs fenêtres, lit les textes de la carte d'offre, extrait prix / distances / durées / adresses. |
| **Moteur de rentabilité** | €/km = prix ÷ km, €/h = prix ÷ min × 60 (approche incluse). Verdict = le pire des deux face à VOS seuils. Bénéfice net calculé pour l'historique. |
| **Pop-up d'offre** | `PLATEFORME  €/km   PRIX   €/h`, « min · km », jauge colorée avec repère *seuil rentable*. En haut de l'écran, déplaçable, un appui la ferme, disparaît avec l'offre (max 30 s). Affichée par le service d'accessibilité (`TYPE_ACCESSIBILITY_OVERLAY`) : aucune permission « superposition » requise. |
| **Historique** | Liste de toutes les offres analysées (filtres Aujourd'hui / 7 jours / Tout), suppression, **export CSV** compatible Excel français (`;` et virgule décimale). |
| **Statistiques** | Graphiques du revenu net **par jour** (semaine en cours) et **par semaine** (6 dernières), nombre de courses, €/h moyen. |
| **Récap partageable** | Résumé hebdomadaire texte + CSV joint, prêt à envoyer au comptable en un tap (WhatsApp, Mail…). |
| **Réglages** | Coût/km, commission plateforme %, charges sociales %, seuil rentable €/km et €/h, zone MOYEN (%), prix affiché net/brut de commission. |

---

## 2. Prérequis

### Pour compiler
- **Android Studio** Iguana (2023.2) ou plus récent — [télécharger](https://developer.android.com/studio)
- **JDK 17** (fourni avec Android Studio)
- **Android SDK 34** (Android Studio le télécharge automatiquement à l'ouverture du projet)
- Connexion internet (téléchargement de Gradle 8.2 et des dépendances au premier build)

### Pour utiliser l'app
- Téléphone **Android 8.0 (API 26) ou plus récent** — testé jusqu'à Android 14/15
- Application **Uber Driver** et/ou **Bolt Driver** installée
- Le téléphone doit être le même que celui qui fait tourner l'app chauffeur (l'app lit l'écran localement)

---

## 3. Compiler et installer (déploiement)

> 🔰 **Débutant ?** Suivez le guide pas à pas illustré : [`BUILD_ANDROID_STUDIO_DEBUTANT.md`](./BUILD_ANDROID_STUDIO_DEBUTANT.md)

### 3.1 Récupérer le code

```bash
git clone <URL_DE_VOTRE_DEPOT>
cd vtc-copilot
```

### 3.2 Ouvrir dans Android Studio

1. **File ▸ Open…** puis sélectionner le dossier `vtc-copilot/` (celui qui contient `settings.gradle.kts`).
2. Android Studio détecte `gradle/wrapper/gradle-wrapper.properties` et télécharge **Gradle 8.2** tout seul.
3. Attendre la fin de la synchronisation Gradle (barre de progression en bas). Au premier lancement, cela prend 3 à 10 minutes.
4. Si Android Studio propose d'installer le **SDK 34** ou les **Build Tools**, accepter.

> 💡 Les scripts `gradlew` / `gradlew.bat` ne sont pas versionnés. Android Studio n'en a pas besoin.
> Pour la ligne de commande, générez-les une fois : `gradle wrapper --gradle-version 8.2`
> (nécessite un Gradle local) — ou utilisez **Build ▸ Make Project** dans l'IDE.

### 3.3 Activer le mode développeur sur le téléphone

1. **Paramètres ▸ À propos du téléphone** → appuyer **7 fois** sur *Numéro de build*.
2. **Paramètres ▸ Système ▸ Options pour les développeurs** → activer **Débogage USB**.
3. Brancher le téléphone en USB et accepter l'empreinte de l'ordinateur sur l'écran du téléphone.

### 3.4 Installer la version de développement (debug)

Dans Android Studio : sélectionner votre téléphone dans la liste des appareils, puis **▶ Run 'app'**
(ou `Maj + F10`). L'application s'installe et se lance.

En ligne de commande (si le wrapper a été généré) :

```bash
./gradlew :app:installDebug
```

### 3.5 Générer un APK / AAB de production (release)

La release est **minifiée (R8)** avec les règles `app/proguard-rules.pro`.

#### a) Créer une clé de signature (une seule fois — à conserver précieusement !)

**Build ▸ Generate Signed Bundle / APK… ▸ Create new…**
ou en ligne de commande :

```bash
keytool -genkeypair -v -keystore vtccopilot-release.jks -alias vtccopilot \
        -keyalg RSA -keysize 2048 -validity 10000
```

> 🔒 Ne versionnez **jamais** le fichier `.jks` (il est déjà dans `.gitignore`).
> Si vous le perdez, vous ne pourrez plus mettre à jour l'app sur le Play Store.

#### b) Déclarer la signature dans Gradle

Créer `keystore.properties` à la racine (ignoré par git) :

```properties
storeFile=/chemin/vers/vtccopilot-release.jks
storePassword=VOTRE_MDP
keyAlias=vtccopilot
keyPassword=VOTRE_MDP
```

Puis dans `app/build.gradle.kts`, ajouter dans `android { }` :

```kotlin
val keystoreProps = java.util.Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
signingConfigs {
    create("release") {
        storeFile = keystoreProps["storeFile"]?.let { file(it as String) }
        storePassword = keystoreProps["storePassword"] as String?
        keyAlias = keystoreProps["keyAlias"] as String?
        keyPassword = keystoreProps["keyPassword"] as String?
    }
}
buildTypes {
    release {
        signingConfig = signingConfigs.getByName("release")
        // ... (isMinifyEnabled etc. déjà présents)
    }
}
```

#### c) Construire

| Objectif | Android Studio | Ligne de commande | Fichier produit |
|---|---|---|---|
| **APK** à installer directement (hors store) | Build ▸ Generate Signed Bundle / APK ▸ **APK** | `./gradlew :app:assembleRelease` | `app/build/outputs/apk/release/app-release.apk` |
| **AAB** pour le Play Store | Build ▸ Generate Signed Bundle / APK ▸ **Android App Bundle** | `./gradlew :app:bundleRelease` | `app/build/outputs/bundle/release/app-release.aab` |

#### d) Installer un APK release sur un téléphone (sans store)

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```
ou copier l'APK sur le téléphone et l'ouvrir (autoriser *Installer des applications inconnues*).

---

## 4. Guide d'utilisation

### 4.1 Premier lancement — activer le service

Au premier démarrage, l'écran **Bienvenue** explique pourquoi l'app a besoin du service d'accessibilité
(c'est la *divulgation* imposée par Google). Appuyez sur **J'ai compris, activer** → vous arrivez sur le
**Tableau de bord** :

| Permission | Obligatoire ? | Comment l'activer |
|---|---|---|
| **Service d'accessibilité** | ✅ Oui | Bouton *Activer* → confirmer la divulgation → Android ouvre *Paramètres ▸ Accessibilité* → trouver **VTC Copilot — Analyse d'offres** → activer → *Autoriser*. |
| **Notifications** | Recommandé | Bouton *Autoriser* → affiche la notification *VTC Copilot actif* tant que le service tourne. |
| **Optimisation de la batterie** | Recommandé | Bouton *Configurer* → choisir *Ne pas optimiser* / *Autoriser*. Évite qu'Android coupe l'analyse en arrière-plan. |

Revenez dans VTC Copilot : les cartes passent au **vert "Activé"** automatiquement.

> Sur certains téléphones (Xiaomi, Huawei, Samsung…), il faut aussi autoriser **« Fenêtres contextuelles
> en arrière-plan »** / **« Démarrage automatique »** dans les paramètres constructeur.

### 4.2 Paramétrer VOS coûts (onglet Réglages ⚙️)

C'est l'étape la plus importante : la rentabilité affichée dépend de vos chiffres.

| Champ | Explication | Exemple |
|---|---|---|
| **Coût par km (€)** | Carburant + usure + assurance + entretien ramenés au km. Calcul : *(dépenses annuelles véhicule) ÷ (km annuels)*. | `0,25` |
| **Commission plateforme (%)** | Part prélevée par Uber/Bolt sur le prix affiché. | `25` |
| **Charges sociales (%)** | Cotisations URSSAF (micro-entrepreneur ≈ 21–22 %), impôts… appliquées sur le CA après commission. | `22` |
| **Seuil rentable (€/km)** | Repère « seuil rentable » de la jauge pour le €/km. | `1,00` |
| **Seuil rentable (€/h)** | Idem pour le €/h. | `30` |
| **Zone MOYEN à partir de (%)** | Entre ce % du seuil et le seuil → 🟠 MOYEN ; en dessous → 🔴 MAUVAIS ; au-dessus du seuil → 🟢 SUPER. | `80` |
| **Prix affiché déjà net de commission** | Cochez si l'app vous montre ce que **vous** touchez (cas d'Uber en France depuis 2023). Décochez si c'est le prix payé par le client. | ☑ |

Appuyez sur **Enregistrer** — un message *✓ Paramètres enregistrés* confirme.

### 4.3 En service — la pop-up

1. Dès que le service d'accessibilité est activé, la notification *VTC Copilot actif — Analyse des offres en cours…* apparaît (si les notifications sont autorisées).
2. Vérifiez l'affichage avec **Tableau de bord ▸ Tester la pop-up ▸ Afficher un exemple**.
3. Lancez **Uber Driver**, **Bolt Driver** ou **Heetch Pro** et passez en ligne. À chaque **offre de course**, la pop-up apparaît en haut de l'écran :

```
 ┌──────────────────────────────────────────────┐
 │ UBER 1.78€/km        16€         74€/h        │
 │                13 min · 9 km                  │
 │                                   SUPER       │
 │ ━━━━━━━━━━━━━━━━━━━━━━━|━━━━━━━━━━●           │
 │                  seuil rentable               │
 └──────────────────────────────────────────────┘
```

- 🟢 **SUPER** : €/km ET €/h au-dessus de leurs seuils
- 🟠 **MOYEN** : le plus faible des deux entre 80 % et 100 % du seuil
- 🔴 **MAUVAIS** : le plus faible des deux sous 80 % du seuil

4. La pop-up disparaît quand l'offre quitte l'écran (ou après 30 s), ou d'un simple **appui**. **Glissez-la** pour la déplacer.
5. **Son du verdict** (désactivable dans Réglages) : deux notes montantes = SUPER, une note = MOYEN, deux notes graves = MAUVAIS. Joué comme un guidage GPS (la musique baisse brièvement).
6. **Bolt** n'affiche pas la durée de la course : elle est estimée avec la *vitesse moyenne* des Réglages (35 km/h par défaut) et s'affiche avec « ~ » (ex. `~62 min · 36 km`). Les distances en mètres (`135 m`) sont reconnues.
5. La course analysée est **enregistrée dans l'Historique** quelle que soit votre décision (accepter ou non).

### 4.3 bis Bulle, pause et arrêt

- **Bulle (logo)** flottante (comme Uber/Bolt) : anneau **vert** = analyse active, **gris** = en pause. Un appui = pause / reprise, glisser = déplacer. Désactivable dans le Tableau de bord.
- **Notification** *VTC Copilot actif* : boutons **Pause / Reprendre** et **Arrêter** (arrêt complet du service ; à réactiver ensuite dans *Accessibilité*).
- **Lecture des offres** : d'abord les textes d'accessibilité, puis, si l'app VTC les masque (cas constaté sur Uber), **lecture de l'écran par OCR embarqué** (ML Kit, hors ligne, Android 11+, 1 capture/s max, la pop-up et la bulle sont exclues).
- **Diagnostic** (Tableau de bord) : résultat de la lecture par NŒUDS et par OCR + bouton Partager.

### 4.4 Historique 📋

- Filtres **Aujourd'hui / 7 jours / Tout**.
- Chaque ligne : date, plateforme, prix brut, distances, €/h & €/km nets, pastille score.
- **Supprimer** une ligne via l'icône poubelle.
- **Exporter CSV** (icône en haut) → feuille de partage Android → envoyez-vous le fichier par mail ou Drive.
  Le CSV s'ouvre directement dans **Excel / LibreOffice français** (séparateur `;`, décimales à virgule, UTF-8 avec BOM).

### 4.5 Statistiques 📊 et récap comptable

- **Revenu net par jour** : barres Lun → Dim de la semaine en cours.
- **Revenu net par semaine** : 6 dernières semaines.
- Indicateurs : nombre de courses, net total, €/h moyen.
- Bouton **Partager le récap** : génère un texte lisible + le CSV de la semaine en pièce jointe. Choisissez
  WhatsApp / Mail / Drive → votre comptable reçoit tout en un tap.

### 4.6 Arrêter l'analyse

Désactivez le service dans *Paramètres ▸ Accessibilité ▸ VTC Copilot*. La notification et la pop-up
disparaissent immédiatement. Vos données restent dans l'Historique.

---

## 5. Comprendre le calcul de rentabilité

Fichier : `domain/engine/ProfitabilityEngine.kt` (Kotlin pur, testé unitairement).

```
Distance totale   = km d'approche + km de course
Temps total (h)   = (min d'approche + min de course) ÷ 60

Prix brut         = prix lu sur l'écran
Après commission  = Prix brut × (1 − commission %)        ← sauté si "déjà net de commission" est coché
Après charges     = Après commission × (1 − charges %)
Coût véhicule     = Distance totale × coût/km
Bénéfice net      = Après charges − Coût véhicule

POP-UP
€/km              = Prix ÷ Distance totale
€/h               = Prix ÷ Temps total
Ratio             = min( €/km ÷ seuil €/km , €/h ÷ seuil €/h )
Verdict           = SUPER si ratio ≥ 1 · MOYEN si ratio ≥ 80 % · MAUVAIS sinon
Jauge             = point placé à ratio × 60 % de la barre (le repère « seuil rentable » est à 60 %)

HISTORIQUE
€/km net          = Bénéfice net ÷ Distance totale
€/h net           = Bénéfice net ÷ Temps total
```

**Exemples** (seuils 1,00 €/km et 30 €/h) :
- Uber 16 € · 9 km · 13 min → 1,78 €/km · 74 €/h → **SUPER**
- Heetch 14 € · 15 km · 26 min → 0,93 €/km · 32 €/h → **MOYEN** (le €/km est sous le seuil)
- Bolt 10 € · 14 km · 24 min → 0,71 €/km · 25 €/h → **MAUVAIS**

---

## 6. Adapter le parser

Uber et Bolt changent régulièrement leur interface. **Aucun identifiant de vue n'est codé en dur** :
tout est dans `data/config/DefaultParserConfig.kt` sous forme de regex souples, par application.

| Clé | Rôle |
|---|---|
| `offerAnchorPatterns` | Mots qui prouvent qu'on est sur une **carte d'offre** (`Accepter`, `Refuser`, `Nouvelle course`…). Si aucun ne matche, l'écran est ignoré → pas de faux positifs sur l'écran *Gains*. |
| `pricePatterns` | Nombre suivi de `€` (ou précédé pour Bolt). Virgule **et** point acceptés, espaces insécables tolérés. |
| `approachDistancePatterns` / `rideDistancePatterns` | `X km` sur la **même ligne** qu'un mot clé (*client*, *prise en charge* / *trajet*, *destination*). |
| `approachTimePatterns` / `rideTimePatterns` | Idem pour `X min`. |
| `pickupAddressPatterns` / `destinationAddressPatterns` | Texte après `Prise en charge :` / `Destination :`. |
| `priceViewIdHints` | Suffixes de `resource-id` (ex. `fare_amount`) — prioritaires sur la regex si présents. |

**Repli automatique** si les libellés manquent : avec 2 distances lues, la plus petite = approche, la plus grande = course ; avec une seule, elle est traitée comme la course.

### Comment découvrir les nouveaux textes
1. Dans `RideParser.kt`, passer `DEBUG_DUMP = true`.
2. Lancer l'app en debug, ouvrir Uber/Bolt, attendre une offre.
3. Dans **Logcat** filtrer sur `RideParser` : tous les textes captés s'affichent.
4. Ajuster les regex dans `DefaultParserConfig.kt`, ajouter un cas dans `RideParserTest.kt`, relancer les tests.
5. Remettre `DEBUG_DUMP = false` avant la release.

---

## 7. Architecture du code

MVVM + Clean Architecture légère, Hilt pour l'injection, Room pour la persistance, Compose pour l'UI.

```
app/src/main/java/com/vtccopilot/app/
├── VtcCopilotApp.kt                        Application Hilt
├── domain/                             ── Logique métier, 0 dépendance Android
│   ├── model/RideOffer.kt              Offre lue (prix, km, min, adresses)
│   ├── model/VehicleSettings.kt        Paramètres chauffeur + valeurs par défaut FR
│   ├── model/ProfitabilityMetrics.kt   Résultat du calcul + enum ProfitabilityScore
│   └── engine/ProfitabilityEngine.kt   Le calcul (pur, testé)
├── data/
│   ├── config/ParserConfig.kt          Modèle des règles d'extraction (sérialisable JSON)
│   ├── config/DefaultParserConfig.kt   Règles par défaut Uber / Bolt
│   ├── local/AppDatabase.kt            Room (rides, vehicle_settings)
│   ├── local/dao/, local/entity/
│   ├── prefs/OnboardingPrefs.kt        Flag "onboarding vu" (SharedPreferences)
│   └── repository/                     RideRepository, SettingsRepository (Flow)
├── di/AppModule.kt                     Fourniture Room / DAO / config
├── service/
│   ├── accessibility/RideAccessibilityService.kt   Écoute Uber/Bolt, parse, calcule, déclenche l'overlay, historise
│   ├── accessibility/RideParser.kt                 Parcours d'arbre + extraction regex (parseTexts() testable)
│   └── overlay/OfferOverlay.kt, GaugeView.kt       Pop-up (TYPE_ACCESSIBILITY_OVERLAY) + jauge
└── ui/
    ├── MainActivity.kt, navigation/VtcCopilotNavHost.kt (barre d'onglets)
    ├── theme/                          Palette sombre "cockpit"
    ├── onboarding/                     Divulgation Google
    ├── dashboard/                      Permissions (PermissionChecker)
    ├── settings/, history/, stats/     Écrans + ViewModels ; CsvExporter, WeeklySummary
res/
├── xml/accessibility_service_config.xml   packageNames restreints, flags, timeout 100 ms
├── xml/file_paths.xml                     FileProvider (cache/exports)
├── font/jetbrains_mono_medium.ttf         Police des chiffres de la pop-up
└── mipmap-anydpi-v26/                     Icône adaptative
```

**Flux de données en service :**
`Uber/Bolt` → événement accessibilité → `RideParser` → `RideOffer` → `ProfitabilityEngine` → `ProfitabilityMetrics`
→ `OfferOverlay.show()` **et** `RideRepository.save()` → Historique / Stats (Flow Room, mise à jour en direct).

Versions clés : Kotlin 1.9.22 · AGP 8.2.2 · Compose BOM 2024.02.00 · Hilt 2.50 · Room 2.6.1 · minSdk 26 · targetSdk 34.

---

## 8. Tests

### Tests unitaires JVM (sans appareil)

```bash
./gradlew :app:testDebugUnitTest
```
ou clic droit sur `app/src/test` ▸ **Run Tests**.

| Classe | Couverture |
|---|---|
| `ProfitabilityEngineTest` (4 tests) | Score vert/rouge, prix net de commission, données manquantes → UNKNOWN. |
| `RideParserTest` (7 tests) | Offre Uber complète, offre Bolt sans libellés, priorité resource-id, écran *Gains* ignoré, package inconnu, prix seul invalide, point/espace insécable. |

✅ Ces 11 tests ont été exécutés avec succès sur la couche pure Kotlin.

### Tests sur appareil (obligatoires avant publication)

Le service d'accessibilité et l'overlay ne peuvent être validés que sur un téléphone réel :

1. Activer le service, puis *Tester la pop-up* : les 3 exemples SUPER / MOYEN / MAUVAIS doivent s'afficher.
2. Ouvrir Uber/Bolt, attendre une offre → la pop-up doit apparaître en < 1 s.
3. Vérifier les valeurs lues vs l'écran (activer `DEBUG_DUMP` si écart).
4. Vérifier la disparition (✕, 20 s, offre expirée).
5. Vérifier l'Historique, l'export CSV, les Stats, le récap partagé.
6. Désactiver l'accessibilité → notification et pop-up doivent disparaître.
7. Tester après redémarrage du téléphone et après 30 min d'écran éteint (batterie).

---

## 9. Publication Google Play & conformité

L'usage de l'API d'accessibilité pour une app qui n'est pas destinée aux personnes handicapées est
**strictement encadré** par Google. Le projet intègre déjà les éléments exigés :

- ✅ **Divulgation proéminente** affichée *avant* d'ouvrir les paramètres d'accessibilité (`OnboardingScreen` + boîte de dialogue sur le Dashboard).
- ✅ **Description du service** (`strings.xml ▸ accessibility_service_description`) expliquant précisément l'usage.
- ✅ **Politique de confidentialité** : `PRIVACY_POLICY_FR.md` — à héberger sur une URL publique et à renseigner dans la Play Console.
- ✅ Écoute **restreinte** aux deux packages VTC (`packageNames`), aucune donnée transmise hors du téléphone.
- ✅ Foreground service déclaré `specialUse` avec justification (`PROPERTY_SPECIAL_USE_FGS_SUBTYPE`).

Dans la **Play Console** :
1. *Contenu de l'application ▸ API d'accessibilité* : déclarer l'usage, justifier par la **sécurité routière**
   (le chauffeur n'a plus à calculer mentalement en conduisant) et joindre une **vidéo** montrant le flux divulgation → activation → pop-up.
2. *Autorisations sensibles* : seule l'API d'accessibilité est à justifier (plus de `SYSTEM_ALERT_WINDOW` ni de foreground service).
3. Renseigner l'URL de la politique de confidentialité.
4. Section *Sécurité des données* : « Aucune donnée collectée ni partagée » (tout est local).

> ⚠️ Google peut refuser l'app malgré ces éléments. Une distribution **hors store (APK direct)** reste possible et
> n'est soumise à aucune revue.

---

## 10. Dépannage

| Symptôme | Cause probable | Solution |
|---|---|---|
| La carte *Accessibilité* reste rouge après activation | Le système n'a pas encore rafraîchi | Quitter et rouvrir VTC Copilot (l'état est relu au retour à l'écran). |
| Aucune pop-up lors d'une offre | Service coché mais pas actif (voir Tableau de bord) / interface de l'app VTC modifiée | Vérifier le Dashboard ; sinon activer `DEBUG_DUMP` et ajuster les regex (§6). |
| Vignette *Données insuffisantes* ⚪ | Prix ou distance non reconnus | Idem : regarder Logcat `RideParser`, ajuster `pricePatterns` / `*DistancePatterns`. |
| Service coupé après quelques minutes | Optimisation batterie / constructeur agressif | Exclure l'app de l'optimisation + autoriser le démarrage auto (Xiaomi/Huawei/Oppo). |
| Fausses courses dans l'Historique | Une ancre trop générique | Resserrer `offerAnchorPatterns` (ne jamais remettre `€` seul). |
| Build : *Unresolved reference* / kapt | Cache Gradle corrompu | **File ▸ Invalidate Caches / Restart**, puis **Build ▸ Clean Project**. |
| Build : `android.useAndroidX` manquant | `gradle.properties` absent | Il est fourni à la racine ; vérifier qu'il n'a pas été supprimé. |
| Export CSV : « Aucune application » | Aucune app de partage installée | Installer Gmail/Drive ou un gestionnaire de fichiers. |

Logs utiles (Logcat) : `RideA11yService`, `RideParser`, `OfferOverlay`.

---

## 11. Limites connues

- Le parser est basé sur des regex : un **changement d'interface** Uber/Bolt peut nécessiter une mise à jour des règles (§6). Les textes réels des apps n'ont pas pu être vérifiés sans appareil — **testez sur votre téléphone avant de vous y fier**.
- Les adresses complètes ne sont lues que si l'app les affiche sous forme de texte accessible.
- Le temps d'attente avant la course (hors trajet) n'est pas estimé.
- Pas de synchronisation cloud : une désinstallation efface l'historique → exportez régulièrement en CSV.

---

## Licence & responsabilité

Projet fourni « tel quel ». VTC Copilot n'est affilié ni à Uber ni à Bolt. L'utilisateur reste seul
responsable du respect des conditions d'utilisation des plateformes et du Code de la route : **ne manipulez
jamais votre téléphone en conduisant**.
