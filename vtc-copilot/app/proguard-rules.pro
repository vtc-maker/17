# Règles R8 / ProGuard pour la release (isMinifyEnabled = true).
# Hilt et Room embarquent déjà leurs propres règles "consumer".

# --- kotlinx.serialization (ParserConfig / PackageRules) ---
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.vtccopilot.app.**$$serializer { *; }
-keepclassmembers class com.vtccopilot.app.** { *** Companion; }
-keepclasseswithmembers class com.vtccopilot.app.** { kotlinx.serialization.KSerializer serializer(...); }

# --- Modèles domaine (sécurité : conservés tels quels pour les logs/debug) ---
-keep class com.vtccopilot.app.domain.model.** { *; }

# --- Service d'accessibilité : référencé par le système via le Manifest ---
-keep class com.vtccopilot.app.service.** { *; }

# --- Conserver les numéros de ligne pour des stack traces lisibles ---
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
