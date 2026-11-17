package com.rat.focuslock

import android.app.*
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import java.util.Calendar

class WatcherService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            val prefs = getSharedPreferences("focus", Context.MODE_PRIVATE)
            val killed = prefs.getBoolean("killed_today", false)
            val block = currentBlock()
            val photoDone = prefs.getString("photo_done_block", "") == block?.name

            if (block == null || killed || (photoDone && block.photoTarget.isNotEmpty())) {
                if (block == null && pendingPhotoBlock(prefs) == null) {
                    stopSelf()
                    return
                }
            }

            val locked = block != null && !killed && !photoDone || pendingPhotoBlock(prefs) != null
            if (locked) {
                val allowed = (block?.allowedApps ?: emptySet()) + Schedule.alwaysAllowed
                val fg = foregroundPackage()
                if (fg != null && fg !in allowed && !fg.contains("launcher")) {
                    val i = Intent(this@WatcherService, LockActivity::class.java)
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    startActivity(i)
                }
            }
            handler.postDelayed(this, 1000)
        }
    }

    private fun nowMin(): Int {
        val c = Calendar.getInstance()
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }

    private fun currentBlock(): Block? = Schedule.activeBlock(nowMin())

    // A photo block that has ended but still needs its photo keeps the lock on.
    private fun pendingPhotoBlock(prefs: android.content.SharedPreferences): String? {
        val name = prefs.getString("awaiting_photo", "") ?: ""
        return if (name.isNotEmpty()) name else null
    }

    private fun foregroundPackage(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val events = usm.queryEvents(end - 5000, end)
        val e = android.app.usage.UsageEvents.Event()
        var last: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED) last = e.packageName
        }
        return last
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channelId = "watcher"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(channelId, "Focus lock", NotificationManager.IMPORTANCE_LOW)
        )
        val n = Notification.Builder(this, channelId)
            .setContentTitle("Focus block active")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .build()
        startForeground(1, n)
        DndHelper.on(this)
        running = true
        handler.post(tick)
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        DndHelper.off(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
