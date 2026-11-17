package com.rat.focuslock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

object Scheduler {

    fun scheduleAll(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        var code = 100
        for (b in Schedule.blocks) {
            set(ctx, am, code++, b.startMin, "start", b.name)
            set(ctx, am, code++, b.endMin % (24 * 60), "end", b.name)
            set(ctx, am, code++, (b.startMin - Schedule.REMINDER_MINUTES + 24 * 60) % (24 * 60), "remind", b.name)
        }
    }

    private fun set(ctx: Context, am: AlarmManager, code: Int, minOfDay: Int, kind: String, name: String) {
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minOfDay / 60)
            set(Calendar.MINUTE, minOfDay % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        val i = Intent(ctx, AlarmReceiver::class.java)
            .putExtra("kind", kind).putExtra("name", name).putExtra("code", code).putExtra("min", minOfDay)
        val pi = PendingIntent.getBroadcast(
            ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, c.timeInMillis, pi)
    }
}
