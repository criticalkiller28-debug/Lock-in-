package com.rat.focuslock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.Calendar

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Scheduler.scheduleAll(ctx)
        val c = Calendar.getInstance()
        val now = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        if (Schedule.activeBlock(now) != null) {
            ctx.startForegroundService(Intent(ctx, WatcherService::class.java))
        }
    }
}
