# MAX loads this adapter by its fully-qualified class name via reflection.
# This class name must survive R8/ProGuard in the consumer app's build.
-keep class com.applovin.mediation.adapters.VelocityAdsMediationAdapter { *; }
