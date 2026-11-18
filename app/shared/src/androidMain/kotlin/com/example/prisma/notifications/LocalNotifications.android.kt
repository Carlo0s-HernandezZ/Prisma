package com.example.prisma.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.prisma.api.ReminderDto
import com.example.prisma.network.PrismaDns
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.Calendar

actual object LocalNotifications {
    internal const val CHANNEL_ID = "prisma_alertas"
    private const val PREFS = "prisma_recordatorios"
    private const val KEY_PLANS = "planes"
    private val json = Json { ignoreUnknownKeys = true }

    /** Lo que se guarda para reprogramar sin conexión (al reiniciar el teléfono o tras cada aviso). */
    @Serializable
    internal data class Plan(val reminder: ReminderDto, val title: String, val body: String)

    private val context: Context? get() = PrismaDns.context

    actual fun requestPermission() = Unit // Android 13+: MainActivity ya pide POST_NOTIFICATIONS.

    actual fun notifyNow(id: Int, title: String, body: String) {
        context?.let { show(it, id, title, body) }
    }

    actual fun syncReminders(reminders: List<ReminderDto>, cardNames: Map<String, String>) {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // Cancela los avisos que se habían programado.
        loadPlans(ctx).forEach { cancel(ctx, it.reminder) }
        val plans = reminders.filter { it.activo }.map { Plan(it, ReminderPlanner.title(it), ReminderPlanner.body(it, cardNames)) }
        prefs.edit().putString(KEY_PLANS, json.encodeToString(ListSerializer(Plan.serializer()), plans)).apply()
        plans.forEach { schedule(ctx, it) }
    }

    internal fun loadPlans(ctx: Context): List<Plan> =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PLANS, null)
            ?.let { runCatching { json.decodeFromString(ListSerializer(Plan.serializer()), it) }.getOrNull() }
            .orEmpty()

    internal fun planFor(ctx: Context, reminderId: String): Plan? = loadPlans(ctx).firstOrNull { it.reminder.id == reminderId }

    private fun now(): ReminderTime = Calendar.getInstance().let {
        ReminderTime(it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1, it.get(Calendar.DAY_OF_MONTH), it.get(Calendar.HOUR_OF_DAY), it.get(Calendar.MINUTE))
    }

    private fun intent(ctx: Context, r: ReminderDto) =
        PendingIntent.getBroadcast(
            ctx,
            ReminderPlanner.requestCode(r),
            Intent(ctx, PrismaReminderReceiver::class.java).putExtra(PrismaReminderReceiver.EXTRA_ID, r.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun cancel(ctx: Context, r: ReminderDto) {
        (ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)?.cancel(intent(ctx, r))
    }

    /** Programa el siguiente aviso del recordatorio (el receptor programa el que sigue). */
    @SuppressLint("ScheduleExactAlarm")
    internal fun schedule(ctx: Context, plan: Plan) {
        val alarm = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val next = ReminderPlanner.upcoming(plan.reminder, now(), 1).firstOrNull() ?: return
        val at = Calendar.getInstance().apply {
            clear()
            set(next.year, next.month - 1, next.day, next.hour, next.minute, 0)
        }.timeInMillis
        val pending = intent(ctx, plan.reminder)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarm.canScheduleExactAlarms()
        runCatching {
            if (exact) alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }.onFailure { alarm.set(AlarmManager.RTC_WAKEUP, at, pending) }
    }

    internal fun show(ctx: Context, id: Int, title: String, body: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Avisos de Prisma", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Irregularidades en tu nómina y recordatorios para subir tus documentos"
                },
            )
        }
        val open = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.let {
            PendingIntent.getActivity(ctx, id, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .apply { open?.let(::setContentIntent) }
            .build()
        manager.notify(id, notification)
    }
}

/**
 * Muestra el recordatorio y programa el siguiente. Al reiniciar el teléfono vuelve a programarlos todos
 * (Android borra las alarmas al apagarse).
 */
class PrismaReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        PrismaDns.context = PrismaDns.context ?: context.applicationContext
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            LocalNotifications.loadPlans(context).forEach { LocalNotifications.schedule(context, it) }
            return
        }
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val plan = LocalNotifications.planFor(context, id) ?: return
        LocalNotifications.show(context, ReminderPlanner.requestCode(plan.reminder), plan.title, plan.body)
        LocalNotifications.schedule(context, plan)
    }

    companion object {
        const val EXTRA_ID = "reminder_id"
    }
}
