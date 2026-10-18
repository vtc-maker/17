# 🚗 Drivee Copilote — Guide de build Android Studio (version débutant)

Ce guide vous explique **pas à pas**, sans connaissance préalable, comment ouvrir le projet,
le compiler et l'installer sur votre téléphone Android.

⏱️ Durée : environ 30 à 60 minutes la première fois (surtout des téléchargements).

---

## 📋 Ce dont vous avez besoin

| Élément | Détail |
|---|---|
| Ordinateur | Windows 10/11, macOS ou Linux — **8 Go de RAM minimum** (16 Go conseillé) |
| Espace disque | ~15 Go libres |
| Connexion Internet | Obligatoire (téléchargement d'Android Studio + dépendances) |
| Téléphone Android | **Android 8.0 ou plus récent** (Android 14 conseillé) |
| Câble USB | Un câble qui transfère les données (pas seulement la charge) |

> ℹ️ L'émulateur Android peut servir pour voir l'interface, mais **la détection des offres Uber/Bolt
> ne fonctionne que sur un vrai téléphone** où ces applications sont installées.

---

## Étape 1 — Installer Android Studio

1. Allez sur **https://developer.android.com/studio**
2. Cliquez sur **« Download Android Studio »**, acceptez les conditions, téléchargez.
3. Lancez l'installateur :
   - **Windows** : double-cliquez sur le `.exe` puis « Next » partout.
   - **macOS** : ouvrez le `.dmg`, glissez Android Studio dans « Applications ».
   - **Linux** : décompressez l'archive puis lancez `android-studio/bin/studio.sh`.
4. Au premier lancement, choisissez **« Standard »** dans l'assistant (Setup Wizard)
   et laissez-le télécharger le **SDK Android**. Cliquez sur « Finish » quand c'est terminé.

---

## Étape 2 — Récupérer le projet

Vous devez avoir le dossier **`drivee-copilote`** sur votre ordinateur. Deux options :

- **Option A — ZIP** : téléchargez le code, puis faites « Extraire tout ».
  ⚠️ Placez-le dans un chemin **simple et sans accents ni espaces**, par exemple :
  `C:\Projets\drivee-copilote` ou `~/Projets/drivee-copilote`.
- **Option B — GitHub** : dans Android Studio, *File → New → Project from Version Control*,
  collez l'URL de votre dépôt.

Le dossier doit contenir notamment : `settings.gradle.kts`, `build.gradle.kts`, `app/`, `gradle/`.

---

## Étape 3 — Ouvrir le projet

1. Dans Android Studio : **File → Open** (ou « Open » sur l'écran d'accueil).
2. Sélectionnez le dossier **`drivee-copilote`** (celui qui contient `settings.gradle.kts`),
   **pas** le sous-dossier `app`.
3. Si une fenêtre demande **« Trust project? »**, cliquez sur **« Trust Project »**.
4. Attendez : en bas de la fenêtre, une barre indique **« Gradle sync »**.
   La première fois, cela peut prendre **5 à 15 minutes** (téléchargement de Gradle 8.2 et des librairies).

✅ C'est bon quand le message **« BUILD SUCCESSFUL »** ou **« Gradle sync finished »** apparaît.

> 💡 Si Android Studio propose **« Upgrade Android Gradle Plugin »**, cliquez sur
> **« Don't ask again for this project »** / « Remind me later ». Le projet est configuré pour
> AGP 8.2.2 + Kotlin 1.9.22 : ne mettez pas à jour tant que tout fonctionne.

---

## Étape 4 — Vérifier le JDK (Java 17)

Le projet utilise **Java 17**. Android Studio en fournit un, il suffit de vérifier :

1. **File → Settings** (macOS : **Android Studio → Settings**)
2. **Build, Execution, Deployment → Build Tools → Gradle**
3. Champ **« Gradle JDK »** : choisissez **« jbr-17 »** ou **« Embedded JDK (17 ou plus) »**.
4. Cliquez **OK**, puis l'icône 🐘 **« Sync Project with Gradle Files »** (en haut à droite).

---

## Étape 5 — Préparer votre téléphone (mode développeur)

1. Sur le téléphone : **Paramètres → À propos du téléphone**.
2. Appuyez **7 fois** sur **« Numéro de build »** (sur Xiaomi : « Version MIUI » ;
   sur Samsung : *Informations sur le logiciel → Numéro de version*).
   Un message « Vous êtes maintenant développeur » s'affiche.
3. Revenez dans **Paramètres → Système → Options pour les développeurs**.
4. Activez **« Débogage USB »**.
5. Branchez le téléphone à l'ordinateur avec le câble USB.
6. Sur le téléphone, acceptez la fenêtre **« Autoriser le débogage USB ? »** (cochez « Toujours autoriser »).

> 🪟 Windows : si le téléphone n'apparaît pas, installez le pilote USB du fabricant
> (Samsung, Xiaomi…) ou choisissez le mode USB **« Transfert de fichiers »**.

---

## Étape 6 — Lancer l'application sur le téléphone ▶️

1. En haut d'Android Studio, vérifiez :
   - la configuration : **`app`**
   - l'appareil : le nom de **votre téléphone**
2. Cliquez sur le bouton vert **▶ Run 'app'** (ou `Maj + F10`).
3. Patientez pendant la compilation (1 à 3 minutes la première fois).
4. L'application **Drivee Copilote** s'ouvre automatiquement sur le téléphone. 🎉

---

## Étape 7 — Activer les autorisations dans l'application

L'écran d'accueil (Onboarding) vous guide. Il faut activer **3 choses** :

1. **Service d'accessibilité** — *Paramètres → Accessibilité → Applications installées →
   « Drivee Copilote — Analyse d'offres » → Activer*.
   C'est ce qui permet de lire les offres Uber/Bolt à l'écran.
2. **Affichage par-dessus les autres applications** (bulle flottante) — autorisez « Drivee Copilote ».
3. **Notifications** — acceptez (nécessaire pour le service en arrière-plan).

Conseillé : **désactiver l'optimisation de batterie** pour l'app (sinon Android peut la couper).

> ⚠️ **Android 13/14 — « Paramètre restreint »** : si l'interrupteur Accessibilité est grisé,
> allez dans *Paramètres → Applications → Drivee Copilote → ⋮ (menu en haut à droite) →
> « Autoriser les paramètres restreints »*, puis réessayez. C'est normal pour une app installée hors Play Store.

Ensuite : réglez votre **coût/km, commission, charges et objectif horaire** dans l'onglet *Réglages*,
ouvrez Uber Driver ou Bolt Driver, et la bulle affichera la rentabilité de chaque offre.

---

## Étape 8 — Créer un fichier APK (pour installer sans câble)

### APK de test (debug) — le plus simple
1. Menu **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
2. Une notification « APK(s) generated successfully » apparaît → cliquez **« locate »**.
3. Le fichier se trouve ici : `app/build/outputs/apk/debug/app-debug.apk`
4. Envoyez-le sur le téléphone (mail, Drive, câble…) et ouvrez-le
   (autorisez « Installer des applications inconnues » si demandé).

### APK signé (release) — pour un usage durable ou le Play Store
1. **Build → Generate Signed Bundle / APK…**
2. Choisissez **APK** (ou *Android App Bundle* pour le Play Store) → *Next*.
3. **Key store path → Create new…** : choisissez un emplacement, un mot de passe, un alias
   (ex. `drivee`), remplissez au moins « First and Last Name » → *OK*.
   🔐 **Sauvegardez ce fichier `.jks` et les mots de passe** : sans eux, impossible de publier une mise à jour.
4. Choisissez **release** → *Create*.
5. Fichier généré : `app/release/app-release.apk` (ou `.aab`).

---

## Étape 9 — Lancer les tests unitaires (optionnel)

Dans le panneau de gauche, ouvrez `app/src/test/java/...`, faites un **clic droit sur le dossier `java`**
→ **« Run 'Tests in java' »**. Tout doit être vert ✅.

Ou dans le **Terminal** intégré d'Android Studio (en bas) :
```bash
./gradlew test          # macOS / Linux
gradlew.bat test        # Windows
```
> Si `gradlew` est introuvable : Android Studio le génère normalement à la synchronisation.
> Sinon, dans le Terminal : `gradle wrapper --gradle-version 8.2`.

---

## 🆘 Problèmes fréquents et solutions

| Problème | Solution |
|---|---|
| **« SDK location not found »** | *File → Project Structure → SDK Location* : indiquez le dossier du SDK. Ou créez `local.properties` à la racine avec `sdk.dir=C\:\\Users\\VOUS\\AppData\\Local\\Android\\Sdk` (Windows) / `sdk.dir=/Users/VOUS/Library/Android/sdk` (macOS). |
| **« Unsupported Java version » / « requires Java 17 »** | Refaites l'**Étape 4** (Gradle JDK = 17). |
| **« Failed to find target android-34 »** | *Tools → SDK Manager → SDK Platforms* : cochez **Android 14 (API 34)** → *Apply*. |
| **Gradle sync très long ou échoue (timeout)** | Vérifiez Internet / proxy, puis *File → Invalidate Caches → Invalidate and Restart*. |
| **« Could not resolve com.google.dagger… »** | Problème réseau : relancez 🐘 *Sync*. Vérifiez que « Offline mode » est désactivé (onglet Gradle à droite). |
| **Erreur kapt / Hilt / Room** | *Build → Clean Project*, puis *Build → Rebuild Project*. |
| **Le téléphone n'apparaît pas** | Câble data, débogage USB activé, autorisation acceptée sur le téléphone, pilote USB (Windows). Débranchez/rebranchez. |
| **« INSTALL_FAILED_UPDATE_INCOMPATIBLE »** | Désinstallez l'ancienne version de l'app du téléphone, puis relancez ▶. |
| **L'accessibilité se désactive toute seule** | Désactivez l'optimisation de batterie pour l'app ; sur Xiaomi/Huawei/Samsung activez aussi « Démarrage automatique » / « Ne pas mettre en veille ». |
| **La bulle ne détecte pas les offres** | Vérifiez que le service d'accessibilité est bien **activé** et que l'autorisation « superposition » est accordée. Les apps Uber/Bolt changent parfois leur interface : le parseur (`RideParser.kt`) peut nécessiter une mise à jour. |

---

## 📌 Récapitulatif technique (pour référence)

- Package : `com.drivee.copilote` — version `1.0.0`
- minSdk **26** (Android 8.0) · targetSdk / compileSdk **34** (Android 14)
- Gradle **8.2** · Android Gradle Plugin **8.2.2** · Kotlin **1.9.22** · Java **17**
- Jetpack Compose · Room · Hilt · AccessibilityService · Overlay (WindowManager)

Pour le fonctionnement détaillé de l'application, voir **`README.md`**.
