package com.rat.focuslock

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

class LockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LockScreen(onDone = { finish() }) }
    }
}

@Composable
fun LockScreen(onDone: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val prefs = ctx.getSharedPreferences("focus", Context.MODE_PRIVATE)
    val block = Schedule.activeBlock(
        java.util.Calendar.getInstance().let { it.get(java.util.Calendar.HOUR_OF_DAY) * 60 + it.get(java.util.Calendar.MINUTE) }
    )
    var pass by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    BackHandler(enabled = true) { /* back does nothing */ }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(block?.name ?: "Photo needed", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(12.dp))
        Text("Finish what you started. Only your allowed apps work right now.")
        Spacer(Modifier.height(24.dp))

        if (block == null || block.photoTarget.isNotEmpty()) {
            Button(onClick = {
                ctx.startActivity(Intent(ctx, PhotoActivity::class.java))
            }) { Text("Take proof photo") }
        }

        Spacer(Modifier.height(32.dp))
        Text("Emergency unlock")
        OutlinedTextField(
            value = pass,
            onValueChange = { pass = it; error = false },
            visualTransformation = PasswordVisualTransformation(),
            isError = error,
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            if (pass == prefs.getString("kill_password", "")) {
                prefs.edit()
                    .putBoolean("killed_today", true)
                    .putString("awaiting_photo", "")
                    .putInt("kill_uses", prefs.getInt("kill_uses", 0) + 1)
                    .apply()
                ctx.stopService(Intent(ctx, WatcherService::class.java))
                onDone()
            } else error = true
        }) { Text("Unlock") }
    }
}
