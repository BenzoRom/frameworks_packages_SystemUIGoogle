-keep class com.google.android.systemui.SystemUIGoogleFactory
-keep,allowoptimization,allowaccessmodification class com.google.android.systemui.dagger.DaggerSysUIGoogleGlobalRootComponent$SysUIGoogleSysUIComponentImpl { !synthetic *; }
-keep class com.google.** { *; }
-keep class vendor.google.** { *; }

-dontwarn com.google.android.systemui.dreamliner.*

-include ../../../frameworks/base/packages/SystemUI/proguard.flags
