# Conformité Google Play — API d'accessibilité

Ce document contient (1) la **Prominent Disclosure** à afficher **dans l'application avant
toute activation** du service d'accessibilité, (2) la **politique de confidentialité** publique,
et (3) le **texte de justification** à fournir dans la Play Console lors de la revue.

> ⚠️ Google examine strictement toute app utilisant `AccessibilityService`. Vous devez :
> - afficher la *Prominent Disclosure* AVANT de rediriger l'utilisateur vers les réglages d'accessibilité ;
> - fournir une politique de confidentialité publique (URL) ;
> - remplir la déclaration "Usage de l'API d'accessibilité" dans la Play Console ;
> - joindre une **vidéo de démonstration** montrant l'usage concret (aide à la conduite).
> Le non-respect entraîne un refus ou un retrait de l'application.

---

## 1. Prominent Disclosure (à afficher DANS l'app, avant activation)

> **Drivee Copilote utilise le service d'accessibilité d'Android**
>
> Pour vous aider à décider en une seconde si une course est rentable, Drivee Copilote a
> besoin de **lire à l'écran les informations de l'offre affichée par votre application VTC**
> (Uber, Bolt) : le prix proposé, les distances et durées d'approche et de course, ainsi que
> les zones de départ et de destination.
>
> **Ce que nous faisons :**
> - Nous lisons uniquement les écrans des applications VTC que vous avez sélectionnées.
> - Nous calculons localement des indicateurs de rentabilité (€/km, €/h) et les affichons
>   dans une petite vignette, afin de limiter votre manipulation du téléphone en conduisant.
>
> **Ce que nous ne faisons PAS :**
> - Nous ne collectons **aucune donnée personnelle**.
> - Nous ne lisons **pas** vos mots de passe, messages ou autres applications.
> - **Aucune donnée d'écran n'est envoyée sur Internet** : tout le traitement et l'historique
>   restent **sur votre appareil**.
>
> Vous pouvez désactiver ce service à tout moment dans *Réglages > Accessibilité*.
>
> [ Refuser ]        [ J'accepte et j'active ]

*Règle d'implémentation :* le bouton "J'accepte et j'active" est le SEUL chemin qui ouvre
`Settings.ACTION_ACCESSIBILITY_SETTINGS`. Ne jamais rediriger sans ce consentement explicite.

---

## 2. Politique de confidentialité (page publique — à héberger sur une URL)

**Politique de confidentialité — Drivee Copilote**
*Dernière mise à jour : [DATE]*

**1. Responsable du traitement**
Cette application est éditée par [VOTRE NOM / SOCIÉTÉ], [ADRESSE], contact : [EMAIL].

**2. Principe : traitement 100 % local**
Drivee Copilote est un outil d'aide à la conduite pour chauffeurs VTC. L'application fonctionne
**entièrement sur votre appareil**. Nous n'exploitons aucun serveur qui recevrait le contenu
de vos écrans, votre position ou l'historique de vos courses.

**3. Utilisation du service d'accessibilité Android**
Avec votre autorisation explicite, l'application utilise l'API d'accessibilité d'Android
**uniquement** pour lire, en temps réel, les informations des offres de course affichées par
les applications VTC que vous avez sélectionnées (par défaut : Uber Driver et Bolt Driver).
Les informations lues se limitent à : prix proposé, distances/durées d'approche et de course,
zones de prise en charge et de destination.

Ces informations sont utilisées **immédiatement et localement** pour :
- calculer des indicateurs de rentabilité (€/km, €/h, score couleur) ;
- afficher une vignette d'aide à la décision par-dessus l'application VTC ;
- enregistrer, **sur votre appareil uniquement**, un historique des courses analysées.

**4. Données collectées et partagées**
- **Aucune donnée personnelle** n'est collectée, transmise ni vendue.
- **Aucune donnée n'est envoyée à des tiers** ni à des serveurs distants.
- Aucune publicité, aucun traceur analytique tiers n'est intégré.

**5. Stockage et conservation**
L'historique des courses et vos paramètres de calcul sont stockés localement (base de données
Room). Ils sont supprimés lorsque vous videz les données de l'application ou la désinstallez.

**6. Permissions utilisées**
- *Service d'accessibilité* : lire les offres à l'écran des apps VTC (voir §3).
- *Affichage par-dessus les autres apps* (`SYSTEM_ALERT_WINDOW`) : afficher la vignette.
- *Service au premier plan* (`FOREGROUND_SERVICE`) : maintenir la vignette pendant l'usage.
- *Notifications* : afficher la notification obligatoire du service au premier plan.
- *Optimisation batterie* (optionnel) : garder l'analyse active en arrière-plan.

**7. Vos droits**
Le traitement étant local, vous gardez le contrôle total : désactivez le service
d'accessibilité, videz les données de l'app, ou désinstallez-la à tout moment.

**8. Sécurité et conduite**
La vignette est conçue pour réduire la distraction (information glanceable). Restez maître de
votre véhicule et respectez le code de la route ; l'application est une aide, pas un pilote.

**9. Contact**
Pour toute question : [EMAIL].

---

## 3. Justification pour la Play Console (déclaration "Accessibility API")

> **Fonctionnalité principale utilisant l'API d'accessibilité :**
> Drivee Copilote est un outil d'assistance à la conduite destiné aux chauffeurs VTC
> professionnels. L'API d'accessibilité est utilisée pour lire, en temps réel, les paramètres
> d'une offre de course affichée par l'application VTC de l'utilisateur (prix, distances,
> durées), afin de calculer un indicateur de rentabilité et de l'afficher dans une vignette
> compacte.
>
> **Pourquoi l'API d'accessibilité est indispensable :**
> Ces informations ne sont pas exposées par une API publique des plateformes VTC. La lecture
> des vues à l'écran est le seul moyen d'obtenir ces données au moment précis où l'offre est
> présentée au chauffeur.
>
> **Bénéfice pour l'utilisateur (sécurité routière) :**
> En présentant l'information de rentabilité de façon immédiate et lisible en une seconde,
> l'application réduit le besoin pour le chauffeur de lire et d'analyser lui-même plusieurs
> chiffres sur l'écran pendant la conduite, ce qui limite la distraction et favorise une
> décision rapide et sûre.
>
> **Traitement des données :**
> Toutes les données lues sont traitées localement sur l'appareil, ne sont jamais transmises
> à des serveurs ni à des tiers, et l'utilisateur donne un consentement explicite via une
> *Prominent Disclosure* avant l'activation.
>
> **Packages ciblés :** `com.ubercab.driver`, `ee.mtakso.driver` (restriction déclarée dans
> `accessibility_service_config.xml`).

**Checklist de soumission :**
- [ ] Prominent Disclosure affichée in-app avant activation.
- [ ] URL de politique de confidentialité renseignée dans la fiche Play.
- [ ] Formulaire "Usage de l'API d'accessibilité" rempli (texte ci-dessus).
- [ ] Vidéo de démonstration (30–60 s) montrant l'overlay au-dessus d'Uber/Bolt.
- [ ] Section "Sécurité des données" : aucune donnée collectée/partagée, traitement local.
- [ ] `packageNames` limité aux apps VTC (pas d'écoute globale).
