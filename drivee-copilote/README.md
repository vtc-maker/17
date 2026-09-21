# Drivee-Copilote — Assistant de rentabilité VTC (Android natif Kotlin)

> Application Android native d'aide à la décision pour chauffeurs VTC (Uber, Bolt…).
> Elle lit à la volée les offres de course affichées à l'écran, calcule des indicateurs
> de rentabilité personnalisés et affiche une **vignette flottante (overlay)** par-dessus
> l'application VTC.
>
> ⚠️ **Livrable = code source natif.** Ce projet est un projet **Android/Gradle en Kotlin**.
> Il ne s'exécute pas dans un environnement Expo/React Native. Ouvrez-le dans **Android Studio**
> (Giraffe+), synchronisez Gradle, puis exécutez sur un appareil réel (l'`AccessibilityService`
> et l'overlay `SYSTEM_ALERT_WINDOW` ne fonctionnent pas sur émulateur sans configuration
> particulière et jamais dans un aperçu web).

---

## 1. Stack technique

| Domaine | Choix |
|---|---|
| Langage | Kotlin 1.9+ |
| UI de configuration | Jetpack Compose (Material 3) — thème sombre "cockpit" |
| Architecture | MVVM + Clean Architecture (data / domain / ui / service) |
| Injection de dépendances | Hilt |
| Base de données locale | Room |
| Concurrence | Kotlin Coroutines + Flow |
| SDK min / cible | `minSdk 26` (Android 8.0) / `targetSdk 34` (Android 14) |
| Scraping écran | `AccessibilityService` + parsing d'`AccessibilityNodeInfo` |
| Overlay | `WindowManager` + `TYPE_APPLICATION_OVERLAY` (permission `SYSTEM_ALERT_WINDOW`) |

Marché ciblé : **France** — montants en euros, décimales à la **virgule** (`12,50 €`),
packages `com.ubercab.driver` (Uber Driver) et `ee.mtakso.driver` (Bolt Driver).

---

## 2. Architecture (Clean / MVVM)

```
com.drivee.copilote
├── DriveeApp.kt                      # Application Hilt (@HiltAndroidApp)
│
├── data/                             # Couche DONNÉES
│   ├── local/
│   │   ├── AppDatabase.kt            # Base Room
│   │   ├── dao/RideDao.kt            # Accès historique des courses
│   │   ├── dao/SettingsDao.kt        # Accès paramètres véhicule
│   │   ├── entity/RideEntity.kt
│   │   └── entity/VehicleSettingsEntity.kt
│   ├── config/
│   │   ├── ParserConfig.kt           # Config dynamique (JSON/Regex) des sélecteurs
│   │   └── DefaultParserConfig.kt    # JSON par défaut embarqué (Uber/Bolt)
│   └── repository/
│       ├── RideRepository.kt
│       └── SettingsRepository.kt
│
├── domain/                           # Couche MÉTIER (pure Kotlin, testable)
│   ├── model/
│   │   ├── RideOffer.kt              # Offre brute extraite de l'écran
│   │   ├── VehicleSettings.kt        # Paramètres de calcul de l'utilisateur
│   │   ├── ProfitabilityMetrics.kt   # Résultat des calculs
│   │   └── ProfitabilityScore.kt     # GREEN / ORANGE / RED
│   └── engine/
│       └── ProfitabilityEngine.kt    # ⭐ Moteur de rentabilité (logique pure)
│
├── service/                          # Couche SERVICES ANDROID
│   ├── accessibility/
│   │   ├── RideAccessibilityService.kt  # Le "scraper" d'écran
│   │   └── RideParser.kt             # ⭐ Extraction robuste (Regex + config)
│   └── overlay/
│       └── OverlayService.kt         # ⭐ Vignette flottante (WindowManager)
│
├── di/
│   └── AppModule.kt                  # Modules Hilt
│
└── ui/                               # Couche PRÉSENTATION (Compose)
    ├── MainActivity.kt
    ├── theme/{Color,Theme,Type}.kt   # Thème sombre cockpit
    ├── navigation/DriveeNavHost.kt
    ├── dashboard/{DashboardScreen,DashboardViewModel}.kt
    ├── settings/{SettingsScreen,SettingsViewModel}.kt
    └── history/{HistoryScreen,HistoryViewModel}.kt
```

**Flux de données runtime :**

```
[Uber/Bolt à l'écran]
        │  (événements TYPE_WINDOW_CONTENT_CHANGED / TYPE_WINDOW_STATE_CHANGED)
        ▼
RideAccessibilityService ──► RideParser.parse(rootNode, config) ──► RideOffer?
        │
        ▼ (si offre valide)
ProfitabilityEngine.compute(offer, settings) ──► ProfitabilityMetrics
        │
        ├──► OverlayService.showOrUpdate(metrics)      # vignette flottante
        └──► RideRepository.save(offer, metrics)       # historique Room
```

---

## 3. Plan de développement pas à pas

1. **Bootstrap projet** : Android Studio → *New Project* → *Empty Activity (Compose)*,
   `minSdk 26`, Kotlin. Ajouter Hilt, Room, Coroutines dans `build.gradle.kts`.
2. **Couche domain** : modèles (`RideOffer`, `VehicleSettings`, `ProfitabilityMetrics`) +
   `ProfitabilityEngine`. **Écrire les tests unitaires JVM d'abord** (logique pure).
3. **Couche data** : entités + DAO + `AppDatabase` Room, repositories, `ParserConfig`
   + JSON par défaut. Fournir les `SettingsRepository` avec valeurs par défaut France.
4. **Accessibilité** : `RideAccessibilityService` + `accessibility_service_config.xml`.
   Implémenter `RideParser` avec regex souples. Logger les nœuds en debug pour affiner.
5. **Overlay** : `OverlayService` (foreground service) + `overlay_view.xml`. Gérer
   ajout/màj/suppression de la vue sans crash, auto-dismiss temporisé.
6. **UI Compose** : Dashboard (état des permissions + boutons d'activation), Paramètres
   (formulaire de calcul + seuils couleur), Historique (liste filtrable + rentabilité moyenne).
7. **Permissions & onboarding** : deep-links vers réglages Accessibilité, Overlay,
   Optimisation batterie. Afficher la *Prominent Disclosure* AVANT d'activer l'accessibilité.
8. **Durcissement** : try/catch autour du parsing, throttling des événements, gestion
   du cycle de vie du service, tests sur appareils réels Uber/Bolt.
9. **Conformité Google Play** : politique de confidentialité + *Prominent Disclosure*
   (voir `PRIVACY_POLICY_FR.md`) + vidéo de démonstration pour la revue.

---

## 4. Fichiers clés demandés

| Demande | Fichier |
|---|---|
| Permissions & Manifest | `app/src/main/AndroidManifest.xml` |
| Config accessibilité | `app/src/main/res/xml/accessibility_service_config.xml` |
| Parser | `.../service/accessibility/RideParser.kt` |
| Moteur de rentabilité | `.../domain/engine/ProfitabilityEngine.kt` |
| Service Overlay | `.../service/overlay/OverlayService.kt` |
| Politique / Prominent Disclosure | `PRIVACY_POLICY_FR.md` |

---

## 5. Avertissements importants

- **Fragilité du scraping** : Uber/Bolt peuvent changer leur UI à tout moment.
  La configuration dynamique (JSON/Regex externalisée dans `ParserConfig`) permet de mettre
  à jour les sélecteurs sans republier l'app (par ex. via un fichier distant).
- **Politique Google Play** : l'API d'accessibilité fait l'objet d'un examen strict.
  Justifier l'usage (aide à la conduite sans distraction, sécurité routière) et fournir la
  *Prominent Disclosure*. Voir `PRIVACY_POLICY_FR.md`.
- **Aucune donnée ne quitte l'appareil** : tout est stocké **localement** (Room). Aucun
  contenu d'écran n'est transmis ni enregistré durablement au-delà des métriques de course.
