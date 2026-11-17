package com.rat.focuslock

import android.Manifest
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.POST_NOTIFICATIONS))
        Scheduler.scheduleAll(this)
        setContent { MaterialTheme { Home() } }
    }

    private fun hasUsageAccess(): Boolean {
        val ops = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return ops.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName
        ) == AppOpsManager.MODE_ALLOWED
    }

    @Composable
    fun Home() {
        val prefs = getSharedPreferences("focus", Context.MODE_PRIVATE)
        var pass by remember { mutableStateOf("") }
        var saved by remember { mutableStateOf(prefs.getString("kill_password", "")!!.isNotEmpty()) }

        Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())) {
            Text("FocusLock", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(16.dp))

            Text("1. Emergency password", style = MaterialTheme.typography.titleMedium)
            Text(if (saved) "Password is set." else "Pick a long passphrase you will only type in a real jam.")
            OutlinedTextField(value = pass, onValueChange = { pass = it }, singleLine = true, label = { Text("New password") })
            Button(onClick = {
                if (pass.length >= 8) {
                    prefs.edit().putString("kill_password", pass).apply()
                    saved = true; pass = ""
                }
            }) { Text("Save password (min 8 characters)") }

            Spacer(Modifier.height(20.dp))
            Text("2. Permissions", style = MaterialTheme.typography.titleMedium)
            Button(onClick = {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }) { Text("Usage access (to see which app is open)") }
            Button(onClick = {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }) { Text("Draw over other apps") }
            Button(onClick = {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            }) { Text("Do Not Disturb access") }
            Button(onClick = {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
            }) { Text("Exact alarms") }

            Spacer(Modifier.height(20.dp))
            Text("3. Your schedule", style = MaterialTheme.typography.titleMedium)
            Schedule.blocks.filter { it.name != "Sleep (after midnight)" }.forEach {
                Text("${fmt(it.startMin)} to ${fmt(it.endMin)}: ${it.name}")
            }
            Text("Everything else: free.")

            Spacer(Modifier.height(20.dp))
            Text("Emergency unlocks used: ${prefs.getInt("kill_uses", 0)}")
        }
    }

    private fun fmt(m: Int): String {
        val h = (m / 60) % 24
        val mm = m % 60
        return "%02d:%02d".format(h, mm)
    }
}
