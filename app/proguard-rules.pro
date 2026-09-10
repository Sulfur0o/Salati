# Salati relies on library consumer rules for WorkManager and generated serializers.
# These narrow keeps are a belt-and-suspenders match for persisted JSON and
# WorkManager's reflective worker construction under R8 full mode.
-keepclassmembers class com.sulfuro.salati.core.work.AlarmCacheRestorationWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keepclassmembers class com.sulfuro.salati.core.work.AlarmNetworkRefreshWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keepclassmembers class com.sulfuro.salati.core.work.AlarmMaintenanceWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keepclassmembers class com.sulfuro.salati.core.work.AlarmSettingsRefreshDebounceWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keepclassmembers class com.sulfuro.salati.data.settings.CalculationSettings {
    <fields>;
}
-keepclassmembers class com.sulfuro.salati.data.settings.LocationSettings {
    <fields>;
}
-keepclassmembers class com.sulfuro.salati.data.settings.PrayerMethodSettings {
    <fields>;
}
-keepclassmembers class com.sulfuro.salati.data.settings.AlarmPreferences {
    <fields>;
}
-keepclassmembers class com.sulfuro.salati.data.settings.ZakatPreferences {
    <fields>;
}
-keepclassmembers class com.sulfuro.salati.data.settings.AppearanceSettings {
    <fields>;
}
-keepclassmembers class com.sulfuro.salati.core.alarms.RegisteredAlarm {
    <fields>;
}
