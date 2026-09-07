# Keep the adapter class name for MAX reflection loading.
# NOTE: minifyEnabled is false for this library module, so these rules are NOT applied
# during the library's own R8 pass. The equivalent rule in consumer-rules.pro IS applied
# during the consuming app's build, which is where MAX's reflection loading happens.
-keep class com.applovin.mediation.adapters.VelocityAdsMediationAdapter { *; }
