package com.amvarpvtltd.swiftNote

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.textclassifier.TextClassifier
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import com.amvarpvtltd.swiftNote.permissions.PermissionManager
import com.amvarpvtltd.swiftNote.permissions.createPermissionManager
import com.amvarpvtltd.swiftNote.ui.theme.SelfNoteTheme
import com.amvarpvtltd.swiftNote.utils.PreferenceManager
import com.amvarpvtltd.swiftNote.utils.TextClassifierManager
import kotlinx.coroutines.launch
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {

    // Modular permission manager
    private lateinit var permissionManager: PermissionManager

    // Text classifier manager for smart text features
    private lateinit var textClassifierManager: TextClassifierManager

    // LiveData to hold the noteId from notification
    companion object {
        private const val TAG = "MainActivity"
        val noteIdToOpen = MutableLiveData<String?>()
    }

    private lateinit var preferenceManager: PreferenceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferenceManager = PreferenceManager.getInstance(this)
        enableEdgeToEdge()

        // Initialize modular permission manager
        initializePermissionManager()

        // Initialize text classifier
        initializeTextClassifier()

        // Check for noteId in intent
        handleIntent(intent)

        setContent {
            SelfNoteTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.Green
                ) { paddingValues ->
                    MyApp(
                        modifier = Modifier.padding(paddingValues)
                    )
                }
            }
        }

        // Setup full screen after content is set
        window.decorView.post { setupFullScreen() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        // Check if we have a noteId in the intent (from notification)
        val noteId = intent.getStringExtra("noteId")
        if (!noteId.isNullOrEmpty()) {
            Log.d(TAG, "📱 Received noteId from notification: $noteId")
            // Set the LiveData value to trigger navigation in MyApp
            noteIdToOpen.value = noteId
        }
    }

    private fun initializePermissionManager() {
        permissionManager = createPermissionManager { isGranted ->
            if (isGranted) {
                preferenceManager.resetNotificationDenialCount()
                Toast.makeText(this, "Notifications enabled successfully!", Toast.LENGTH_SHORT).show()
                onNotificationPermissionGranted()
            } else {
                preferenceManager.incrementNotificationDenialCount()
                preferenceManager.setLastRequestTime(System.currentTimeMillis())
                onNotificationPermissionDenied()
            }
        }

        checkAndRequestNotificationPermission()
    }

    private fun checkAndRequestNotificationPermission() {
        val currentTime = System.currentTimeMillis()
        val lastRequestTime = preferenceManager.getLastRequestTime()
        val timeSinceLastRequest = currentTime - lastRequestTime

        when {
            isNotificationPermissionGranted() -> {
                // Permission already granted, nothing to do
                return
            }
            preferenceManager.isFirstNotificationRequest() -> {
                preferenceManager.setFirstNotificationRequest(false)
                preferenceManager.setLastRequestTime(currentTime)
                permissionManager.requestNotificationPermissionIfNeeded()
                Toast.makeText(this, "Please enable notifications for the best experience", Toast.LENGTH_LONG).show()
            }
            preferenceManager.getNotificationDenialCount() == 1 &&
                    timeSinceLastRequest >= PreferenceManager.MIN_REQUEST_INTERVAL -> {
                // Second attempt after 24 hours
                permissionManager.requestNotificationPermissionIfNeeded()
                Toast.makeText(this, "Notifications help you stay updated. Please consider enabling them", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun isNotificationPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(this).areNotificationsEnabled()
        }
    }

    private fun onNotificationPermissionGranted() {
        // Additional logic when notification permission is granted
        // This is where you can add future enhancements like:
        // - Initialize notification channels
        // - Enable reminder features
        // - Update app settings
    }

    private fun onNotificationPermissionDenied() {
        val denialCount = preferenceManager.getNotificationDenialCount()
        if (denialCount > 1 && !preferenceManager.hasSeenSettings()) {
            showNotificationSettingsDialog()
        } else {
            Toast.makeText(
                this,
                "Notifications disabled. Some features may not work properly.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showNotificationSettingsDialog() {
        preferenceManager.setHasSeenSettings(true)
        AlertDialog.Builder(this)
            .setTitle("Enable Notifications")
            .setMessage("Notifications are important for reminders and updates. Would you like to enable them in settings?")
            .setPositiveButton("Open Settings") { _, _ ->
                openNotificationSettings()
                Toast.makeText(this, "Please enable notifications in Settings", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Not Now") { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(
                    this,
                    "You can enable notifications later in App Settings",
                    Toast.LENGTH_LONG
                ).show()
            }
            .show()
    }

    // Add this method to check notification status when returning from settings
    override fun onResume() {
        super.onResume()
        if (isNotificationPermissionGranted() && preferenceManager.getNotificationDenialCount() > 0) {
            preferenceManager.resetNotificationDenialCount()
            Toast.makeText(this, "Notifications enabled successfully!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initializeTextClassifier() {
        // Initialize the text classifier manager using singleton pattern
        textClassifierManager = TextClassifierManager.getInstance(applicationContext)

        // Setup lifecycle observer for cleanup
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                super.onDestroy(owner)
                // Cleanup if needed
            }
        })
    }

    /**
     * Process text content to identify entities like URLs, dates, etc.
     */
    private fun processTextContent(text: String) {
        lifecycleScope.launch {
            try {
                val entities = textClassifierManager.detectTextEntities(text)
                entities.forEach { (type, ranges) ->
                    Log.d(TAG, "Found entity type: $type at positions: $ranges")
                    handleEntityDetection(type, ranges, text)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error analyzing text", e)
            }
        }
    }

    /**
     * Process text selection and handle available actions
     */
    private fun processTextSelection(text: String, start: Int, end: Int) {
        lifecycleScope.launch {
            try {
                val actions = textClassifierManager.getTextActions(text, start, end)
                actions.forEach { action ->
                    Log.d(TAG, "Action available: ${action.title}")
                    action.actionIntent.send()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting text actions", e)
            }
        }
    }

    private fun handleEntityDetection(type: String, ranges: List<IntRange>, text: String) {
        ranges.forEach { range ->
            val entityText = text.substring(range.first, range.last + 1)
            Log.d(TAG, "Entity: $type = $entityText")
            when (type) {
                TextClassifier.TYPE_URL -> handleUrl(entityText)
                TextClassifier.TYPE_EMAIL -> handleEmail(entityText)
                TextClassifier.TYPE_PHONE -> handlePhone(entityText)
                TextClassifier.TYPE_ADDRESS -> handleAddress(entityText)
                TextClassifier.TYPE_DATE, TextClassifier.TYPE_DATE_TIME -> handleDateTime(entityText)
            }
        }
    }

    private fun handleUrl(url: String) {
        Log.d(TAG, "Detected URL: $url")
        // Add URL handling logic here
    }

    private fun handleEmail(email: String) {
        Log.d(TAG, "Detected email: $email")
        // Add email handling logic here
    }

    private fun handlePhone(phone: String) {
        Log.d(TAG, "Detected phone: $phone")
        // Add phone handling logic here
    }

    private fun handleAddress(address: String) {
        Log.d(TAG, "Detected address: $address")
        // Add address handling logic here
    }

    private fun handleDateTime(dateTime: String) {
        Log.d(TAG, "Detected date/time: $dateTime")
        // Add date/time handling logic here
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            setupFullScreen()
        }
    }

    private fun setupFullScreen() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                val controller = window.insetsController
                if (controller != null) {
                    controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                    controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)

                @Suppress("DEPRECATION")
                window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up full screen mode", e)
        }
    }

    private fun openNotificationSettings() {
        try {
            val intent = Intent().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    action = android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
                    putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName)
                } else {
                    action = android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    data = android.net.Uri.fromParts("package", packageName, null)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening notification settings", e)
            // Fallback to app settings if notification settings fails
            val fallbackIntent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.fromParts("package", packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(fallbackIntent)
        }
    }
}
