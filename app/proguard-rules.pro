# Salati relies on library consumer rules for WorkManager and generated serializers.
# These narrow keeps are a belt-and-suspenders match for persisted JSON and
# WorkManager's reflective worker construction under R8 full mode.
-keepclassmembers class io.github.sulfuro25.salati.core.notifications.AlarmCacheRestorationWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keepclassmembers class io.github.sulfuro25.salati.core.notifications.AlarmNetworkRefreshWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keepclassmembers class io.github.sulfuro25.salati.core.notifications.AlarmMaintenanceWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keepclassmembers class io.github.sulfuro25.salati.core.notifications.AlarmSettingsRefreshDebounceWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keepclassmembers class io.github.sulfuro25.salati.data.settings.CalculationSettings {
    <fields>;
}
-keepclassmembers class io.github.sulfuro25.salati.core.notifications.RegisteredAlarm {
    <fields>;
}
