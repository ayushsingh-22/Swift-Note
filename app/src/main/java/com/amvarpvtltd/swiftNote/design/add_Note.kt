package com.amvarpvtltd.swiftNote.design

import android.content.ClipboardManager
import android.content.Context
import android.text.Html
import android.text.Html.fromHtml
import android.text.Spanned
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.amvarpvtltd.swiftNote.ai.DetectedReminder
import com.amvarpvtltd.swiftNote.ai.SmartReminderAI
import com.amvarpvtltd.swiftNote.components.BackgroundProvider
import com.amvarpvtltd.swiftNote.reminders.ReminderManager
import com.amvarpvtltd.swiftNote.repository.NoteRepository
import com.amvarpvtltd.swiftNote.theme.ProvideNoteTheme
import com.amvarpvtltd.swiftNote.utils.Constants
import com.amvarpvtltd.swiftNote.utils.UIUtils
import com.amvarpvtltd.swiftNote.utils.ValidationUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddScreen(navController: NavHostController, noteId: String?) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showBackDialog by remember { mutableStateOf(false) }
    var titleFocused by remember { mutableStateOf(false) }
    var descriptionFocused by remember { mutableStateOf(false) }

    // Smart Reminders state
    var detectedReminders by remember { mutableStateOf<List<DetectedReminder>>(emptyList()) }
    var pendingReminders by remember { mutableStateOf<List<DetectedReminder>>(emptyList()) }
    var showReminderSuggestions by remember { mutableStateOf(false) }
    var isAnalyzingText by remember { mutableStateOf(false) }
    // Formatted preview states (when clipboard HTML is pasted)
    var titleFormatted by remember { mutableStateOf<AnnotatedString?>(null) }
    var descriptionFormatted by remember { mutableStateOf<AnnotatedString?>(null) }
    // Preserve original HTML (if pasted) so we can save/load formatted content
    var titleHtml by remember { mutableStateOf<String?>(null) }
    var descriptionHtml by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val noteRepository = remember { NoteRepository(context) }
    val reminderManager = remember { ReminderManager.getInstance(context) }
    val smartReminderAI = remember { SmartReminderAI.getInstance(context) }
    val scope = rememberCoroutineScope()
    val titleFocusRequester = remember { FocusRequester() }
    val hapticFeedback = LocalHapticFeedback.current

    val isEditing = noteId != null
    val canSave = ValidationUtils.canSaveNote(title, description)
    val hasContent = title.trim().isNotEmpty() || description.trim().isNotEmpty()

    // Theme management
    val themeState = com.amvarpvtltd.swiftNote.theme.rememberThemeState()
    var currentTheme by themeState

    val titleProgress = UIUtils.calculateProgress(title.length, Constants.TITLE_MAX_LENGTH)

    // Clamp lengths to the respective max to avoid incorrect thresholds for extremely large inputs
    val safeTitleLength = title.length.coerceAtMost(Constants.TITLE_MAX_LENGTH)
    val safeDescriptionLength = description.length.coerceAtMost(Constants.DESCRIPTION_MAX_LENGTH)

    val descriptionProgress = UIUtils.calculateProgress(safeDescriptionLength, Constants.DESCRIPTION_MAX_LENGTH)

    val titleCountColor by animateColorAsState(
        targetValue = UIUtils.getProgressColor(safeTitleLength, Constants.TITLE_MAX_LENGTH),
        animationSpec = UIUtils.getColorAnimationSpec(),
        label = "title_count_color"
    )

    val descCountColor by animateColorAsState(
        targetValue = UIUtils.getProgressColor(safeDescriptionLength, Constants.DESCRIPTION_MAX_LENGTH),
        animationSpec = UIUtils.getColorAnimationSpec(),
        label = "desc_count_color"
    )

    // Initialize Smart Reminder AI
    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            smartReminderAI.initialize()
        }
    }

    // Automatically analyze text for reminders but store them as pending until note is saved
    LaunchedEffect(title, description) {
        val combinedText = "$title. $description".trim()

        // Detect HTML in clipboard and show formatted preview if user just pasted rich text
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            val item = clip?.getItemAt(0)
            val htmlText = item?.htmlText
            val plain = item?.coerceToText(context)?.toString()

            // If clipboard has HTML and the recent field change matches the plain text -> user pasted
            if (!htmlText.isNullOrBlank() && !plain.isNullOrBlank() && (plain.trim() == title.trim() || plain.trim() == description.trim())) {
                val spanned: Spanned = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    fromHtml(htmlText, Html.FROM_HTML_MODE_LEGACY)
                } else {
                    @Suppress("DEPRECATION")
                    (fromHtml(htmlText))
                }

                val annotated = spannedToAnnotatedString(spanned)
                if (plain.trim() == title.trim()) {
                    titleFormatted = annotated
                    titleHtml = htmlText
                }
                if (plain.trim() == description.trim()) {
                    descriptionFormatted = annotated
                    descriptionHtml = htmlText
                }
            }
        } catch (e: Exception) {
            Log.d("AddScreen", "Clipboard HTML detection skipped: ${e.message}")
        }

        // Run minute-pattern fallback first (don't depend on hasReminderKeywords)
        try {
            val minuteRegex = Regex("\\b(\\d{1,3})\\s*(?:min|mins|minm|minute|minutes)\\s*(?:mai|mein)?\\b", RegexOption.IGNORE_CASE)
            val match = minuteRegex.find(combinedText)
            if (match != null) {
                val n = match.groupValues[1].toIntOrNull()
                if (n != null && n > 0) {
                    val cal = java.util.Calendar.getInstance()
                    cal.add(java.util.Calendar.MINUTE, n)
                    val ts = cal.timeInMillis
                    val snippet = match.value.trim()
                    val userTitle = title.ifBlank { "Untitled" }
                    val detected = DetectedReminder(
                        id = java.util.UUID.randomUUID().toString(),
                        title = userTitle,  // Using user's title instead of auto-generated
                        description = description,  // Using user's description instead of auto-detected snippet
                        extractedText = snippet,
                        reminderDateTime = ts,
                        confidence = 0.8f,
                        entityType = "MinuteFallback",
                        originalNoteTitle = userTitle
                    )

                    if (isEditing) {
                        scope.launch(Dispatchers.IO) {
                            try {
                                val success = reminderManager.createReminderFromDetection(detected, noteId)
                                if (success) {
                                    detectedReminders = listOf(detected)
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "🤖 Auto-created 1 smart reminder", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("AddScreen", "Error creating minute-fallback reminder", e)
                            }
                        }
                    } else {
                        pendingReminders = listOf(detected)
                        detectedReminders = listOf(detected)
                        Toast.makeText(context, "🤖 Suggestion: reminder in $n min", Toast.LENGTH_SHORT).show()
                        Log.d("AddScreen", "Minute-fallback pending reminder stored: $n minutes")
                        Log.d("AddScreen", "📝 Stored 1 pending minute-fallback reminder for new note")
                    }

                    return@LaunchedEffect
                }
            }
        } catch (e: Exception) {
            Log.e("AddScreen", "Error in minute fallback detection (pre-check)", e)
        }

        // If no formatted HTML matched, clear any previous formatted preview when text diverges
        if (titleFormatted != null && titleFormatted?.text != title) {
            titleFormatted = null
            titleHtml = null
        }
        if (descriptionFormatted != null && descriptionFormatted?.text != description) {
            descriptionFormatted = null
            descriptionHtml = null
        }

        // Only run analysis when the AI's keyword detector finds relevant English/Hinglish keywords
        if (smartReminderAI.hasReminderKeywords(combinedText)) {

            scope.launch {
                delay(1500) // Debounce to avoid excessive API calls

                isAnalyzingText = true
                try {
                    val result = smartReminderAI.analyzeTextForReminders(combinedText, title)
                    if (result.isSuccess) {
                        val reminders = result.getOrNull() ?: emptyList()
                        if (reminders.isNotEmpty()) {
                            // For existing notes, create reminders immediately
                            if (isEditing) {
                                var createdCount = 0
                                reminders.forEach { reminder ->
                                    if (reminder.confidence >= 0.6f) {
                                        try {
                                            val success = reminderManager.createReminderFromDetection(reminder, noteId)
                                            if (success) {
                                                createdCount++
                                                Log.d("AddScreen", "✅ Auto-created reminder for existing note: ${reminder.title}")
                                            }
                                        } catch (e: Exception) {
                                            Log.e("AddScreen", "❌ Error auto-creating reminder for existing note", e)
                                        }
                                    }
                                }

                                if (createdCount > 0) {
                                    detectedReminders = reminders.filter { it.confidence >= 0.6f }
                                    Toast.makeText(
                                        context,
                                        "🤖 Auto-created $createdCount smart reminder${if (createdCount > 1) "s" else ""}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } else {
                                // For new notes, store as pending reminders
                                val highConfidenceReminders = reminders.filter { it.confidence >= 0.6f }
                                if (highConfidenceReminders.isNotEmpty()) {
                                    pendingReminders = highConfidenceReminders
                                    detectedReminders = highConfidenceReminders
                                    Log.d("AddScreen", "📝 Stored ${highConfidenceReminders.size} pending reminders for new note")
                                }
                            }
                        } else {
                            // If analysis ran but found nothing, clear previous
                            detectedReminders = emptyList()
                            // keep pendingReminders untouched if already set by prior analysis
                        }
                    } else {
                        Log.w("AddScreen", "SmartReminderAI analysis failed: ${result.exceptionOrNull()?.message}")
                    }
                } catch (e: Exception) {
                    Log.e("AddScreen", "Error analyzing text for reminders", e)
                } finally {
                    isAnalyzingText = false
                }
            }
        } else {
            // Clear previous analysis if no relevant keywords
            detectedReminders = emptyList()
            pendingReminders = emptyList()
            isAnalyzingText = false
        }
    }

    // Load existing note data
    LaunchedEffect(noteId) {
        if (noteId != null) {
            isLoading = true
            try {
                val result = noteRepository.loadNote(noteId, context)
                if (result.isSuccess) {
                    val note = result.getOrNull()
                    note?.let {
                        // If stored content contains HTML tags, render formatted preview and keep plain text in fields
                        val titleCandidate = it.title ?: ""
                        val descCandidate = it.description ?: ""

                        fun looksLikeHtml(s: String) = s.contains(Regex("<[^>]+>"))

                        if (looksLikeHtml(titleCandidate)) {
                            val sp = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                                Html.fromHtml(titleCandidate, Html.FROM_HTML_MODE_LEGACY)
                            } else {
                                @Suppress("DEPRECATION")
                                Html.fromHtml(titleCandidate)
                            }
                            titleFormatted = spannedToAnnotatedString(sp as Spanned)
                            titleHtml = titleCandidate
                            title = sp.toString()
                        } else {
                            title = titleCandidate
                        }

                        if (looksLikeHtml(descCandidate)) {
                            val spd = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                                Html.fromHtml(descCandidate, Html.FROM_HTML_MODE_LEGACY)
                            } else {
                                @Suppress("DEPRECATION")
                                Html.fromHtml(descCandidate)
                            }
                            descriptionFormatted = spannedToAnnotatedString(spd as Spanned)
                            descriptionHtml = descCandidate
                            description = spd.toString()
                        } else {
                            description = descCandidate
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Error loading note: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG)
                            .show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error loading note: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                isLoading = false
            }
        }
    }

    // Save note function - OFFLINE FIRST
    fun saveNote() {
        if (!canSave) {
            Toast.makeText(context, Constants.VALIDATION_WARNING_MESSAGE, Toast.LENGTH_LONG).show()
            return
        }

        isSaving = true
        scope.launch(Dispatchers.IO) {
            try {
                // ALWAYS save to Room database first (offline-first)
                // If we have HTML saved from a paste, persist the HTML so formatting is preserved.
                val titleToSave = titleHtml ?: title
                val descToSave = descriptionHtml ?: description

                val result = noteRepository.saveNote(titleToSave, descToSave, noteId, context)

               // kotlin
               // Add debug + safe navigation inside saveNote() success branch
               withContext(Dispatchers.Main) {
                   if (result.isSuccess) {
                       val savedNoteId = result.getOrNull()
                       val finalNoteId = savedNoteId ?: noteId

                       // Create any pending smart reminders for newly saved notes
                       if (finalNoteId != null && pendingReminders.isNotEmpty()) {
                          var createdCount = 0
                          try {
                              withContext(Dispatchers.IO) {
                                  pendingReminders.forEach { reminder ->
                                      if (reminder.confidence >= 0.6f) {
                                          try {
                                              val success = reminderManager.createReminderFromDetection(reminder, finalNoteId)
                                              if (success) createdCount++
                                          } catch (e: Exception) {
                                              Log.e("AddScreen", "❌ Error creating pending smart reminder", e)
                                          }
                                      }
                                  }
                              }

                              if (createdCount > 0) {
                                  // Clear pending reminders and update detected list for UI
                                  detectedReminders = pendingReminders.filter { it.confidence >= 0.6f }
                                  pendingReminders = emptyList()
                                  Toast.makeText(
                                      context,
                                      "🤖 Auto-created $createdCount smart reminder${if (createdCount > 1) "s" else ""}",
                                      Toast.LENGTH_SHORT
                                  ).show()
                              }
                          } catch (e: Exception) {
                              Log.e("AddScreen", "Error creating pending reminders after save", e)
                          }
                       }

                       val networkManager = com.amvarpvtltd.swiftNote.utils.NetworkManager.getInstance(context)
                       val isOnline = networkManager.isConnected()

                       if (isOnline) {
                           Toast.makeText(context, Constants.SAVE_SUCCESS_MESSAGE, Toast.LENGTH_SHORT).show()
                       } else {
                           Toast.makeText(context, "📱 Note saved offline. Will sync when online.", Toast.LENGTH_SHORT).show()
                       }

                       Log.d("AddScreen", "Save successful — currentRoute=${navController.currentBackStackEntry?.destination?.route}")

                       try {
                           navController.navigate("main") {
                               popUpTo("main") { inclusive = false }
                               launchSingleTop = true
                               restoreState = true
                           }
                           Log.d("AddScreen", "Navigation to main requested")
                       } catch (e: Exception) {
                           Log.e("AddScreen", "Navigation failed", e)
                           Toast.makeText(context, "Navigation error: ${e.message}", Toast.LENGTH_LONG).show()
                       }
                   } else {
                       Toast.makeText(context, "Error saving note", Toast.LENGTH_LONG).show()
                   }
               }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error saving note: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isSaving = false
                }
            }
        }
    }

    // Delete note function - OFFLINE FIRST
    fun deleteNote() {
        if (noteId == null) return

        scope.launch(Dispatchers.IO) {
            try {
                val result = noteRepository.deleteNote(noteId, context)
                if (result.isSuccess) {
                    withContext(Dispatchers.Main) {
                        val networkManager = com.amvarpvtltd.swiftNote.utils.NetworkManager.getInstance(context)
                        val isOnline = networkManager.isConnected()

                        if (!isOnline) {
                            Toast.makeText(context, "📱 Note deleted offline. Will sync when online.", Toast.LENGTH_SHORT).show()
                        }

                        navController.navigate("main")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error deleting note", Toast.LENGTH_LONG).show()
                }
                Log.e("AddScreen", "Error in deleteNote", e)
            }
        }
    }

    // Background gradient
    val backgroundBrush = BackgroundProvider.getBrush()

    // Enhanced delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    NoteTheme.Error.copy(alpha = 0.2f),
                                    NoteTheme.Error.copy(alpha = 0.05f)
                                )
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = null,
                        tint = NoteTheme.Error,
                        modifier = Modifier.size(32.dp)
                    )
                }
            },
            title = {
                Text(
                    "Delete Note?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = NoteTheme.OnSurface
                )
            },
            text = {
                Column {
                    Text(
                        "Are you sure you want to delete this note?",
                        style = MaterialTheme.typography.bodyLarge,
                        color = NoteTheme.OnSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = NoteTheme.ErrorContainer.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(Constants.CORNER_RADIUS_SMALL.dp)
                    ) {
                        Text(
                            text = "\"$title\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NoteTheme.OnSurface,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(Constants.CORNER_RADIUS_SMALL.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "This action cannot be undone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NoteTheme.Error,
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        showDeleteDialog = false
                        deleteNote()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NoteTheme.Error,
                        contentColor = NoteTheme.OnPrimary
                    ),
                    shape = RoundedCornerShape(Constants.CORNER_RADIUS_SMALL.dp)
                ) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDeleteDialog = false },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = NoteTheme.OnSurfaceVariant
                    ),
                    shape = RoundedCornerShape(Constants.CORNER_RADIUS_SMALL.dp)
                ) {
                    Text("Cancel", fontWeight = FontWeight.Medium)
                }
            },
            shape = RoundedCornerShape(Constants.CORNER_RADIUS_XL.dp),
            containerColor = NoteTheme.Surface
        )
    }

    // Back confirmation dialog
    if (showBackDialog) {
        AlertDialog(
            onDismissRequest = { showBackDialog = false },
            icon = {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    NoteTheme.Warning.copy(alpha = 0.2f),
                                    NoteTheme.Warning.copy(alpha = 0.05f)
                                )
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Warning,
                        contentDescription = null,
                        tint = NoteTheme.Warning,
                        modifier = Modifier.size(32.dp)
                    )
                }
            },
            title = {
                Text(
                    "Discard Changes?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = NoteTheme.OnSurface
                )
            },
            text = {
                Text(
                    "You have unsaved changes. Are you sure you want to go back? Your changes will be lost.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = NoteTheme.OnSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        showBackDialog = false
                        navController.navigateUp()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NoteTheme.Warning,
                        contentColor = NoteTheme.OnPrimary
                    ),
                    shape = RoundedCornerShape(Constants.CORNER_RADIUS_SMALL.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ExitToApp,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Discard", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showBackDialog = false },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = NoteTheme.OnSurfaceVariant
                    ),
                    shape = RoundedCornerShape(Constants.CORNER_RADIUS_SMALL.dp)
                ) {
                    Text("Keep Editing", fontWeight = FontWeight.Medium)
                }
            },
            shape = RoundedCornerShape(Constants.CORNER_RADIUS_XL.dp),
            containerColor = NoteTheme.Surface
        )
    }

    ProvideNoteTheme(themeMode = currentTheme) {
        Scaffold(
            modifier = Modifier.background(
                Brush.verticalGradient(
                    colors = listOf(
                        NoteTheme.Background,
                        NoteTheme.SurfaceVariant.copy(alpha = 0.3f),
                        NoteTheme.Background
                    )
                )
            ),
            containerColor = Color.Transparent,
            topBar = {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))

                    TopAppBar(
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isEditing) "Edit Note" else "New Note",
                                    fontWeight = FontWeight.Bold,
                                    color = NoteTheme.OnSurface,
                                    style = MaterialTheme.typography.titleLarge
                                )

                                if (isLoading) {
                                    Spacer(modifier = Modifier.width(Constants.CORNER_RADIUS_SMALL.dp))
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(Constants.ICON_SIZE_MEDIUM.dp),
                                        strokeWidth = 2.dp,
                                        color = NoteTheme.Primary
                                    )
                                }
                            }
                        },

                        navigationIcon = {
                            Card(
                                modifier = Modifier
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                        // Handle back navigation with confirmation
                                        if (hasContent && !isEditing) {
                                            showBackDialog = true
                                        } else {
                                            navController.navigateUp()
                                        }
                                    }
                                    .padding(start = 12.dp), // Move components inside from boundary
                                shape = CircleShape,
                                colors = CardDefaults.cardColors(
                                    containerColor = NoteTheme.Primary.copy(alpha = 0.1f)
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                            ) {
                                Box(
                                    modifier = Modifier.padding(Constants.PADDING_SMALL.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = NoteTheme.Primary,
                                        modifier = Modifier.size(Constants.ICON_SIZE_LARGE.dp)
                                    )
                                }
                            }
                        },
                        actions = {
                            Row(
                                modifier = Modifier.padding(end = 16.dp), // Move components inside from boundary
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isEditing) {
                                    Card(
                                        modifier = Modifier
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                hapticFeedback.performHapticFeedback(
                                                    HapticFeedbackType.LongPress
                                                )
                                                showDeleteDialog = true
                                            },
                                        shape = CircleShape,
                                        colors = CardDefaults.cardColors(
                                            containerColor = NoteTheme.Error.copy(alpha = 0.1f)
                                        ),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(Constants.PADDING_SMALL.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Outlined.Delete,
                                                contentDescription = "Delete",
                                                tint = NoteTheme.Error,
                                                modifier = Modifier.size(Constants.ICON_SIZE_LARGE.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                }
            },
            floatingActionButton = {
                // Only show save button when there's no error message and not loading
                AnimatedVisibility(
                    visible = !isLoading && canSave,
                    enter = scaleIn() + fadeIn(),
                    exit = scaleOut() + fadeOut()
                ) {
                    var fabPressed by remember { mutableStateOf(false) }
                    val fabScale by animateFloatAsState(
                        targetValue = if (fabPressed) 0.9f else 1f,
                        animationSpec = UIUtils.getSpringAnimationSpec(),
                        label = "fab_scale"
                    )


                    ExtendedFloatingActionButton(
                        onClick = {
                            fabPressed = true
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            saveNote()
                        },
                        modifier = Modifier
                            .scale(fabScale)
                            .shadow(
                                Constants.CORNER_RADIUS_SMALL.dp,
                                RoundedCornerShape(Constants.CORNER_RADIUS_LARGE.dp)
                            ),
                        containerColor = NoteTheme.Primary,
                        contentColor = NoteTheme.OnPrimary,
                        shape = RoundedCornerShape(Constants.CORNER_RADIUS_LARGE.dp),
                        elevation = FloatingActionButtonDefaults.elevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 0.dp
                        )
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(Constants.ICON_SIZE_MEDIUM.dp),
                                strokeWidth = 2.dp,
                                color = NoteTheme.OnPrimary
                            )
                            Spacer(modifier = Modifier.width(Constants.CORNER_RADIUS_SMALL.dp))
                            Text(
                                "Saving...",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        } else {
                            Icon(
                                Icons.Outlined.Check,
                                contentDescription = "Save",
                                modifier = Modifier.size(Constants.ICON_SIZE_LARGE.dp)
                            )
                            Spacer(modifier = Modifier.width(Constants.CORNER_RADIUS_SMALL.dp))
                            Text(
                                if (isEditing) "Update Note" else "Save Note",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }

                    LaunchedEffect(fabPressed) {
                        if (fabPressed) {
                            kotlinx.coroutines.delay(Constants.SPRING_ANIMATION_DELAY.toLong())
                            fabPressed = false
                        }
                    }
                }
            }
        ) { paddingValues ->
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(Constants.CORNER_RADIUS_LARGE.dp),
                        colors = CardDefaults.cardColors(containerColor = NoteTheme.Surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(Constants.PADDING_XL.dp)
                        ) {
                            CircularProgressIndicator(
                                color = NoteTheme.Primary,
                                strokeWidth = 4.dp
                            )
                            Spacer(modifier = Modifier.height(Constants.PADDING_MEDIUM.dp))
                            Text(
                                "Loading note...",
                                style = MaterialTheme.typography.titleMedium,
                                color = NoteTheme.OnSurface,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(backgroundBrush)
                        .padding(paddingValues)
                        .padding(Constants.PADDING_MEDIUM.dp)
                        .verticalScroll(rememberScrollState()), // Enable vertical scrolling
                    verticalArrangement = Arrangement.spacedBy(Constants.CORNER_RADIUS_LARGE.dp)
                ) {
                    // Title Section
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(),
                        colors = CardDefaults.cardColors(
                            containerColor = NoteTheme.Surface
                        ),
                        shape = RoundedCornerShape(Constants.CORNER_RADIUS_LARGE.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(Constants.CORNER_RADIUS_LARGE.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Title,
                                        contentDescription = null,
                                        tint = NoteTheme.Primary,
                                        modifier = Modifier.size(Constants.ICON_SIZE_MEDIUM.dp)
                                    )
                                    Spacer(modifier = Modifier.width(Constants.PADDING_SMALL.dp))
                                    Text(
                                        text = "Title",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = NoteTheme.OnSurface,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Progress indicator
                                    Box(
                                        modifier = Modifier
                                            .size(Constants.PROGRESS_INDICATOR_SIZE.dp)
                                            .background(
                                                color = titleCountColor.copy(alpha = 0.1f),
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            progress = { titleProgress },
                                            modifier = Modifier.fillMaxSize(),
                                            color = titleCountColor,
                                            strokeWidth = 2.dp
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(Constants.PADDING_SMALL.dp))

                                    Text(
                                        text = UIUtils.formatCharacterCount(
                                            title.length,
                                            Constants.TITLE_MAX_LENGTH
                                        ),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = titleCountColor,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(Constants.PADDING_MEDIUM.dp))

                            OutlinedTextField(
                                value = title,
                                onValueChange = {
                                    if (it.length <= Constants.TITLE_MAX_LENGTH) title = it
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(titleFocusRequester)
                                    .onFocusChanged { titleFocused = it.isFocused },
                                placeholder = {
                                    Text(
                                        "Enter a compelling title...",
                                        color = NoteTheme.OnSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = titleCountColor,
                                    unfocusedBorderColor = NoteTheme.OnSurfaceVariant.copy(alpha = 0.3f),
                                    cursorColor = NoteTheme.Primary,
                                    focusedLabelColor = titleCountColor,
                                    focusedTextColor = NoteTheme.OnSurface, // Use theme-aware color
                                    unfocusedTextColor = NoteTheme.OnSurface // Use theme-aware color
                                ),
                                shape = RoundedCornerShape(Constants.CORNER_RADIUS_SMALL.dp)
                            )

                            // Title requirements indicator
                            AnimatedVisibility(
                                visible = !ValidationUtils.isValidTitle(title),
                                enter = slideInVertically() + fadeIn(),
                                exit = slideOutVertically() + fadeOut()
                            ) {
                                Row(
                                    modifier = Modifier.padding(top = Constants.PADDING_SMALL.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Info,
                                        contentDescription = null,
                                        tint = titleCountColor,
                                        modifier = Modifier.size(Constants.ICON_SIZE_SMALL.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = ValidationUtils.getTitleValidationMessage(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = titleCountColor,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    // Description Section
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp) // Set minimum height instead of weight
                            .animateContentSize(),
                        colors = CardDefaults.cardColors(
                            containerColor = NoteTheme.Surface
                        ),
                        shape = RoundedCornerShape(Constants.CORNER_RADIUS_LARGE.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(Constants.CORNER_RADIUS_LARGE.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Description,
                                        contentDescription = null,
                                        tint = NoteTheme.Secondary,
                                        modifier = Modifier.size(Constants.ICON_SIZE_MEDIUM.dp)
                                    )
                                    Spacer(modifier = Modifier.width(Constants.PADDING_SMALL.dp))

                                    Text(
                                        text = "Description",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = NoteTheme.OnSurface,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Progress indicator
                                    Box(
                                        modifier = Modifier
                                            .size(Constants.PROGRESS_INDICATOR_SIZE.dp)
                                            .background(
                                                color = descCountColor.copy(alpha = 0.1f),
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            progress = { descriptionProgress },
                                            modifier = Modifier.fillMaxSize(),
                                            color = descCountColor,
                                            strokeWidth = 2.dp
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(Constants.PADDING_SMALL.dp))

                                    Text(
                                        text = UIUtils.formatCharacterCount(
                                            description.length,
                                            Constants.DESCRIPTION_MAX_LENGTH
                                        ),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = descCountColor,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(Constants.PADDING_MEDIUM.dp))

                            OutlinedTextField(
                                value = description,
                                onValueChange = {
                                    if (it.length <= Constants.DESCRIPTION_MAX_LENGTH) description =
                                        it
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 120.dp) // Set minimum height for text field
                                    .onFocusChanged { descriptionFocused = it.isFocused },
                                placeholder = {
                                    Text(
                                        "Write your thoughts here...\n\nExpress your ideas, capture important information, or jot down anything that comes to mind.",
                                        color = NoteTheme.OnSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = descCountColor,
                                    unfocusedBorderColor = NoteTheme.OnSurfaceVariant.copy(alpha = 0.3f),
                                    cursorColor = NoteTheme.Secondary,
                                    focusedLabelColor = descCountColor,
                                    focusedTextColor = NoteTheme.OnSurface, // Use theme-aware color
                                    unfocusedTextColor = NoteTheme.OnSurface // Use theme-aware color
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                                keyboardActions = KeyboardActions(
                                    onDone = { /* Allow default behavior - no action needed */ }
                                ),
                                shape = RoundedCornerShape(Constants.CORNER_RADIUS_SMALL.dp)
                            )
                        }
                    }

                    // Validation warning section
                    AnimatedVisibility(
                        visible = !canSave,
                        enter = slideInVertically() + fadeIn(),
                        exit = slideOutVertically() + fadeOut()
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = NoteTheme.Warning.copy(alpha = 0.1f)
                            ),
                            shape = RoundedCornerShape(Constants.CORNER_RADIUS_MEDIUM.dp),
                            border = BorderStroke(1.dp, NoteTheme.Warning.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(Constants.PADDING_MEDIUM.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Warning,
                                    contentDescription = null,
                                    tint = NoteTheme.Warning,
                                    modifier = Modifier.size(Constants.ICON_SIZE_MEDIUM.dp)
                                )
                                Spacer(modifier = Modifier.width(Constants.CORNER_RADIUS_SMALL.dp))
                                Text(
                                    text = ValidationUtils.getSaveValidationMessage(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = NoteTheme.Warning,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Smart Analysis Indicator - Shows when AI is analyzing text
                    if (isAnalyzingText) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = NoteTheme.Primary.copy(alpha = 0.05f)
                            ),
                            shape = RoundedCornerShape(Constants.CORNER_RADIUS_MEDIUM.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(Constants.PADDING_MEDIUM.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = NoteTheme.Primary
                                )
                                Spacer(modifier = Modifier.width(Constants.PADDING_SMALL.dp))
                                Text(
                                    text = "🧠 Analyzing for smart reminders...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = NoteTheme.Primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Auto-Created Reminders Status - Shows when reminders were created
                    if (detectedReminders.isNotEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = NoteTheme.Success.copy(alpha = 0.05f)
                            ),
                            shape = RoundedCornerShape(Constants.CORNER_RADIUS_MEDIUM.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(Constants.PADDING_MEDIUM.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.CheckCircle,
                                    contentDescription = null,
                                    tint = NoteTheme.Success,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(Constants.PADDING_SMALL.dp))
                                Text(
                                    text = "✅ ${detectedReminders.size} smart reminder${if (detectedReminders.size > 1) "s" else ""} created automatically",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = NoteTheme.Success,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Formatted preview (if paste contained HTML)
                    if (titleFormatted != null || descriptionFormatted != null) {
                        Card(colors = CardDefaults.cardColors(containerColor = NoteTheme.SurfaceVariant.copy(alpha = 0.06f)), shape = RoundedCornerShape(Constants.CORNER_RADIUS_MEDIUM.dp)) {
                            Column(modifier = Modifier.padding(Constants.PADDING_MEDIUM.dp)) {
                                if (titleFormatted != null) {
                                    Text(text = "Formatted Title Preview:", style = MaterialTheme.typography.bodySmall, color = NoteTheme.OnSurfaceVariant)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = titleFormatted!!, style = MaterialTheme.typography.titleMedium)
                                    Spacer(modifier = Modifier.height(Constants.PADDING_SMALL.dp))
                                }
                                if (descriptionFormatted != null) {
                                    Text(text = "Formatted Description Preview:", style = MaterialTheme.typography.bodySmall, color = NoteTheme.OnSurfaceVariant)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = descriptionFormatted!!, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }

                    // Add bottom spacing for FAB
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }

        // Auto-focus title when creating new note
        LaunchedEffect(Unit) {
            if (!isEditing && !isLoading) {
                kotlinx.coroutines.delay(Constants.LOADING_DELAY)
                titleFocusRequester.requestFocus()
            }
        }

        // Handle device back button
        BackHandler(enabled = true) {
            // Handle back navigation with confirmation for device back button
            if (hasContent && !isEditing) {
                showBackDialog = true
            } else {
                navController.navigateUp()
            }
        }
    }
}

// Helper: convert Android Spanned (from Html) into Compose AnnotatedString with basic styles
fun spannedToAnnotatedString(spanned: Spanned): AnnotatedString {
    val plain = spanned.toString()
    return buildAnnotatedString {
        append(plain)

        try {
            val spans = spanned.getSpans(0, spanned.length, Any::class.java)
            for (span in spans) {
                val start = spanned.getSpanStart(span).coerceIn(0, plain.length)
                val end = spanned.getSpanEnd(span).coerceIn(0, plain.length)
                val style = when (span) {
                    is android.text.style.StyleSpan -> {
                        when (span.style) {
                            android.graphics.Typeface.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                            android.graphics.Typeface.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                            else -> SpanStyle()
                        }
                    }
                    is android.text.style.UnderlineSpan -> SpanStyle(textDecoration = TextDecoration.Underline)
                    is android.text.style.StrikethroughSpan -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                    is android.text.style.ForegroundColorSpan -> SpanStyle(color = androidx.compose.ui.graphics.Color(span.foregroundColor))
                    is android.text.style.RelativeSizeSpan -> SpanStyle(fontSize = (14 * span.sizeChange).sp)
                    else -> null
                }
                if (style != null && start < end) addStyle(style, start, end)
            }
        } catch (e: Exception) {
            Log.d("AddScreen", "spannedToAnnotatedString: error converting spans: ${e.message}")
        }
    }
}
