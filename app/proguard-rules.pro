# Pleos Connect SDK
-keep class ai.pleos.playground.** { *; }
-dontwarn ai.pleos.playground.**
# Room
-keep class ai.soran.app.data.db.** { *; }

# Tink crypto library missing annotation rules
-dontwarn javax.annotation.**
