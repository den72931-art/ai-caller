package com.aicaller.app.ui

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aicaller.app.ui.theme.AiCallerTheme

class MainActivity : ComponentActivity() {

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* no-op: UI observes permission state on recompose */ }

    private val requestCallScreeningRole = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AiCallerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AiCallerApp(
                        onRequestPermissions = { requestRuntimePermissions() },
                        onRequestDefaultRole = { requestCallScreeningRoleFlow() },
                        hasAllPermissions = { allPermissionsGranted() }
                    )
                }
            }
        }
    }

    private fun requiredPermissions(): Array<String> = arrayOf(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.POST_NOTIFICATIONS
    )

    private fun allPermissionsGranted(): Boolean = requiredPermissions().all {
        ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestRuntimePermissions() {
        requestPermissions.launch(requiredPermissions())
    }

    /** Prompts the user to make AiCaller the device's call-screening / default phone app. */
    private fun requestCallScreeningRoleFlow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                requestCallScreeningRole.launch(intent)
                return
            }
        }
        // Fallback: send them to the system default-apps settings screen.
        startActivity(Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
    }
}

sealed class Screen(val route: String, val label: String) {
    data object Log : Screen("log", "Call Log")
    data object Settings : Screen("settings", "Settings")
}

@Composable
fun AiCallerApp(
    onRequestPermissions: () -> Unit,
    onRequestDefaultRole: () -> Unit,
    hasAllPermissions: () -> Boolean
) {
    val navController = rememberNavController()
    val screens = listOf(Screen.Log, Screen.Settings)

    Scaffold(
        bottomBar = {
            NavigationBar {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = backStackEntry?.destination?.route
                screens.forEach { screen ->
                    NavigationBarItem(
                        selected = currentRoute == screen.route,
                        onClick = {
                            navController.navigate(screen.route) {
                                launchSingleTop = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (screen is Screen.Log) Icons.Filled.Call else Icons.Filled.Settings,
                                contentDescription = screen.label
                            )
                        },
                        label = { Text(screen.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Log.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Log.route) {
                CallLogScreen(
                    onRequestPermissions = onRequestPermissions,
                    onRequestDefaultRole = onRequestDefaultRole,
                    hasAllPermissions = hasAllPermissions()
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen()
            }
        }
    }
}
