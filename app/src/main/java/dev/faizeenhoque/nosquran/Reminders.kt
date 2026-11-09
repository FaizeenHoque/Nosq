package dev.faizeenhoque.nosquran

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.Calendar
import kotlinx.coroutines.*

fun scheduleReminder(context: Context) {
    val p = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)
    val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent =
        PendingIntent.getBroadcast(
            context,
            10,
            Intent(context, ReadingReminderReceiver::class.java).setAction("READ"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    alarm.cancel(intent)
    if (!p.getBoolean("enabled", false)) return
    val time =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, p.getInt("hour", 20))
            set(Calendar.MINUTE, p.getInt("minute", 0))
            set(Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DATE, 1)
        }
    alarm.setInexactRepeating(
        AlarmManager.RTC_WAKEUP,
        time.timeInMillis,
        AlarmManager.INTERVAL_DAY,
        intent,
    )
}

class ReadingReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            scheduleReminder(context)
            return
        }
        if (
            !context
                .getSharedPreferences("reminders", Context.MODE_PRIVATE)
                .getBoolean("enabled", false)
        )
            return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                "reading",
                "Reading reminders",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        )
        val open =
            PendingIntent.getActivity(
                context,
                11,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        if (
            Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
        )
            manager.notify(
                20,
                NotificationCompat.Builder(context, "reading")
                    .setSmallIcon(R.drawable.ic_book_open)
                    .setContentTitle("A moment for the Quran")
                    .setContentText("Return to your reading, at your own pace.")
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .build(),
            )
    }
}

@Composable
fun ReminderSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val loaded by
        produceState<Pair<android.content.SharedPreferences, Map<String, *>>?>(null) {
            value =
                withContext(Dispatchers.IO) {
                    val p = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)
                    p to p.all
                }
        }
    val snapshot =
        loaded
            ?: run {
                LinearProgressIndicator()
                return
            }
    val p = snapshot.first
    var enabled by remember { mutableStateOf(snapshot.second["enabled"] as? Boolean ?: false) }
    var hour by remember { mutableIntStateOf(snapshot.second["hour"] as? Int ?: 20) }
    var minute by remember { mutableIntStateOf(snapshot.second["minute"] as? Int ?: 0) }
    var denied by remember { mutableStateOf(false) }
    fun enable(value: Boolean) {
        enabled = value
        scope.launch(Dispatchers.IO) {
            p.edit().putBoolean("enabled", value).commit()
            scheduleReminder(context)
        }
    }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
            enable(allowed)
            denied = !allowed
        }
    ToggleRow("Reading reminder", "A gentle daily invitation", enabled) { value ->
        if (
            value &&
                Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
        )
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else enable(value)
    }
    if (denied)
        Text(
            "Notifications are disabled. Allow notifications in Android app settings to receive reminders.",
            style = MaterialTheme.typography.bodySmall,
        )
    SettingRow(
        "Reminder time",
        String.format(
            java.util.Locale.ENGLISH,
            "%02d:%02d · approximate, local time",
            hour,
            minute,
        ),
        R.drawable.ic_clock,
        {
            TimePickerDialog(
                    context,
                    { _, h, m ->
                        hour = h
                        minute = m
                        scope.launch(Dispatchers.IO) {
                            p.edit().putInt("hour", h).putInt("minute", m).commit()
                            scheduleReminder(context)
                        }
                    },
                    hour,
                    minute,
                    true,
                )
                .show()
        },
    )
}
