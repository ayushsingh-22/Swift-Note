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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NotificationsActive
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
import androidx.compose.runtime.derivedStateOf
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
import com.amvarpvtltd.swiftNote.checklist.ChecklistItem
import com.amvarpvtltd.swiftNote.checklist.ChecklistParser
import com.amvarpvtltd.swiftNote.components.ChecklistItemRow
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.graphics.StrokeCap
import com.amvarpvtltd.swiftNote.components.IconActionButton
import com.amvarpvtltd.swiftNote.components.LoadingCard
import com.amvarpvtltd.swiftNote.components.NoteScreenBackground

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

    // Phase 1: Checklist mode state
    var isChecklistMode by remember { mutableStateOf(false) }
    var checklistItems by remember { mutableStateOf(listOf(ChecklistItem(order = 0))) }
    var focusedItemIndex by remember { mutableStateOf(-1) }
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
    // Performance: derivedStateOf prevents recomposition unless the derived value actually changes
    val canSave by remember { derivedStateOf {
        if (isChecklistMode) {
            title.trim().length >= Constants.MIN_CONTENT_LENGTH &&
                checklistItems.any { it.text.isNotBlank() }
        } else {
            ValidationUtils.canSaveNote(title, description)
        }
    } }
    val hasContent by remember { derivedStateOf {
        title.trim().isNotEmpty() || description.trim().isNotEmpty() ||
            (isChecklistMode && checklistItems.any { it.text.isNotBlank() })
    } }

    // Theme management
    val themeState = com.amvarpvtltd.swiftNote.theme.rememberThemeState()
    var currentTheme by themeState

    // Scroll state for reading progress indicator
    val scrollState = rememberScrollState()
    val scrollFraction = if (scrollState.maxValue > 0)
        scrollState.value.toFloat() / scrollState.maxValue.toFloat() else 0f
    val animatedScrollProgress by animateFloatAsState(
        targetValue = scrollFraction, animationSpec = tween(150), label = "scroll_progress"
    )

    // Staggered entrance animations
    var heroVisible by remember { mutableStateOf(false) }
    var contentVisible by remember { mutableStateOf(false) }

    LaunchedEffect(isLoading) {
        if (!isLoading) {
            delay(80)
            heroVisible = true
            delay(120)
            contentVisible = true
        }
    }

    // Performance: derivedStateOf for progress calculations - only recomputes when title/description length changes
    val titleProgress by remember { derivedStateOf { UIUtils.calculateProgress(title.length, Constants.TITLE_MAX_LENGTH) } }

    // Clamp lengths to the respective max to avoid incorrect thresholds for extremely large inputs
    val safeTitleLength by remember { derivedStateOf { title.length.coerceAtMost(Constants.TITLE_MAX_LENGTH) } }
    val safeDescriptionLength by remember { derivedStateOf { description.length.coerceAtMost(Constants.DESCRIPTION_MAX_LENGTH) } }

    val descriptionProgress by remember { derivedStateOf { UIUtils.calculateProgress(safeDescriptionLength, Constants.DESCRIPTION_MAX_LENGTH) } }

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

    // BUG-015 FIX: Separate clipboard detection — only once on composition mount, not every keystroke
    LaunchedEffect(Unit) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            val item = clip?.getItemAt(0)
            val htmlText = item?.htmlText
            val plain = item?.coerceToText(context)?.toString()

            if (!htmlText.isNullOrBlank() && !plain.isNullOrBlank()) {
                val spanned: Spanned = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    fromHtml(htmlText, Html.FROM_HTML_MODE_LEGACY)
                } else {
                    @Suppress("DEPRECATION")
                    (fromHtml(htmlText))
                }

                val annotated = spannedToAnnotatedString(spanned)
                if (plain.trim() == title.trim() && title.isNotBlank()) {
                    titleFormatted = annotated
                    titleHtml = htmlText
                }
                if (plain.trim() == description.trim() && description.isNotBlank()) {
                    descriptionFormatted = annotated
                    descriptionHtml = htmlText
                }
            }
        } catch (e: Exception) {
            Log.d("AddScreen", "Clipboard HTML detection skipped: ${e.message}")
        }
    }

    // Phase 1: Derive checklist text for Smart Reminder AI analysis
    val checklistTextForAI by remember { derivedStateOf {
        if (isChecklistMode) checklistItems.joinToString(". ") { it.text } else ""
    } }

    // BUG-015 FIX: Debounced reminder analysis — only after user stops typing (800ms)
    // BUG-006 FIX: All Toast calls wrapped in withContext(Dispatchers.Main)
    LaunchedEffect(title, description, checklistTextForAI) {
        val descriptionForAnalysis = if (isChecklistMode) checklistTextForAI else description
        val combinedText = "$title. $descriptionForAnalysis".trim()

        // Fast exit: skip all processing for very short input
        if (combinedText.length < 3) return@LaunchedEffect

        // Debounce ALL processing — wait 800ms after last keystroke
        delay(800)

        // Clear formatted previews if text diverged from clipboard paste
        if (titleFormatted != null && titleFormatted?.text != title) {
            titleFormatted = null
            titleHtml = null
        }
        if (descriptionFormatted != null && descriptionFormatted?.text != description) {
            descriptionFormatted = null
            descriptionHtml = null
        }

        // Run minute-pattern fallback first (lightweight regex — OK after debounce)
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
                        title = userTitle,
                        description = description,
                        extractedText = snippet,
                        reminderDateTime = ts,
                        confidence = 0.8f,
                        entityType = "MinuteFallback",
                        originalNoteTitle = userTitle
                    )

                    // Show as suggestion chip — user confirms via UI
                    pendingReminders = listOf(detected)
                    detectedReminders = listOf(detected)
                    Log.d("AddScreen", "Minute-fallback reminder suggestion shown: $n minutes")

                    return@LaunchedEffect
                }
            }
        } catch (e: Exception) {
            Log.e("AddScreen", "Error in minute fallback detection (pre-check)", e)
        }

        // Only run AI analysis when relevant keywords found
        if (smartReminderAI.hasReminderKeywords(combinedText)) {

            // Additional debounce for AI (heavy operation)
            delay(700)

            isAnalyzingText = true
            try {
                val result = withContext(Dispatchers.IO) {
                    smartReminderAI.analyzeTextForReminders(combinedText, title)
                }
                if (result.isSuccess) {
                    val reminders = result.getOrNull() ?: emptyList()
                    if (reminders.isNotEmpty()) {
                        // Show as suggestion chips — user confirms via UI (both new and editing notes)
                        val highConfidenceReminders = reminders.filter { it.confidence >= 0.6f }
                        if (highConfidenceReminders.isNotEmpty()) {
                            pendingReminders = highConfidenceReminders
                            detectedReminders = highConfidenceReminders
                            Log.d("AddScreen", "Showing ${highConfidenceReminders.size} reminder suggestion chips")
                        }
                    } else {
                        detectedReminders = emptyList()
                    }
                } else {
                    Log.w("AddScreen", "SmartReminderAI analysis failed: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.e("AddScreen", "Error analyzing text for reminders", e)
            } finally {
                isAnalyzingText = false
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

                        // Phase 1: Detect checklist content
                        if (ChecklistParser.isChecklistContent(descCandidate)) {
                            isChecklistMode = true
                            title = titleCandidate
                            val items = ChecklistParser.parseItems(descCandidate)
                            checklistItems = if (items.isEmpty()) listOf(ChecklistItem(order = 0)) else items
                            description = "" // Keep description empty in checklist mode
                        } else {

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
                        } // end else (non-checklist)
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
        if (isSaving) return  // BUG-021 FIX: Prevent double-tap duplicate saves
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
                val descToSave = if (isChecklistMode) {
                    ChecklistParser.serializeItems(checklistItems)
                } else {
                    descriptionHtml ?: description
                }

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
                                              // BUG-035 FIX: For minute-based reminders, recompute time from NOW (save time)
                                              val actualReminder = if (reminder.entityType == "MinuteFallback") {
                                                  val minuteMatch = Regex("(\\d+)").find(reminder.extractedText)
                                                  val minutes = minuteMatch?.value?.toIntOrNull()
                                                  if (minutes != null) {
                                                      val freshCal = java.util.Calendar.getInstance()
                                                      freshCal.add(java.util.Calendar.MINUTE, minutes)
                                                      reminder.copy(reminderDateTime = freshCal.timeInMillis)
                                                  } else reminder
                                              } else reminder

                                              val success = reminderManager.createReminderFromDetection(actualReminder, finalNoteId)
                                              if (success) createdCount++
                                          } catch (e: Exception) {
                                              Log.e("AddScreen", "Error creating pending smart reminder", e)
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

    // Performance: remember the brush to avoid re-creating gradient objects on every recomposition
    // (kept for backward compat but NoteScreenBackground is now used as wrapper)

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
        NoteScreenBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Column {

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
                            IconActionButton(
                                onClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (hasContent && !isEditing) {
                                        showBackDialog = true
                                    } else {
                                        navController.navigateUp()
                                    }
                                },
                                icon = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                containerColor = NoteTheme.Primary.copy(alpha = 0.1f),
                                contentColor = NoteTheme.Primary,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        },
                        actions = {
                            Row(
                                modifier = Modifier.padding(end = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isEditing) {
                                    IconActionButton(
                                        onClick = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                            showDeleteDialog = true
                                        },
                                        icon = Icons.Outlined.Delete,
                                        contentDescription = "Delete note",
                                        containerColor = NoteTheme.Error.copy(alpha = 0.1f),
                                        contentColor = NoteTheme.Error
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent
                        )
                    )

                    // Reading / scroll progress bar
                    if (scrollState.maxValue > 0) {
                        LinearProgressIndicator(
                            progress = { animatedScrollProgress },
                            modifier = Modifier.fillMaxWidth().height(3.dp),
                            color = NoteTheme.Primary,
                            trackColor = NoteTheme.Primary.copy(alpha = 0.12f),
                            strokeCap = StrokeCap.Round
                        )
                    } else {
                        Spacer(modifier = Modifier.height(3.dp))
                    }
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
                LoadingCard("Loading note...", "Fetching from local storage...")
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(Constants.PADDING_MEDIUM.dp)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(Constants.CORNER_RADIUS_LARGE.dp)
                ) {
                    // Title Section
                    AnimatedVisibility(
                        visible = heroVisible,
                        enter = fadeIn(tween(400)) + slideInVertically(
                            spring(Spring.DampingRatioMediumBouncy),
                            initialOffsetY = { it / 4 }
                        )
                    ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(),
                        colors = CardDefaults.cardColors(
                            containerColor = NoteTheme.Surface
                        ),
                        shape = RoundedCornerShape(20.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)
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
                    } // end AnimatedVisibility heroVisible

                    // Phase 1: Note Mode Toggle (Text / Checklist)
                    AnimatedVisibility(
                        visible = contentVisible,
                        enter = fadeIn(tween(400)) + slideInVertically(
                            spring(Spring.DampingRatioMediumBouncy),
                            initialOffsetY = { it / 3 }
                        )
                    ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = NoteTheme.SurfaceVariant.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(Constants.CORNER_RADIUS_MEDIUM.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Constants.PADDING_SMALL.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Text mode button
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        if (isChecklistMode) {
                                            // Convert checklist → text
                                            description = ChecklistParser.checklistToText(checklistItems)
                                            isChecklistMode = false
                                        }
                                    },
                                shape = RoundedCornerShape(Constants.CORNER_RADIUS_SMALL.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (!isChecklistMode) NoteTheme.Primary.copy(alpha = 0.15f)
                                    else Color.Transparent
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Outlined.Description,
                                        contentDescription = null,
                                        tint = if (!isChecklistMode) NoteTheme.Primary else NoteTheme.OnSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "Text",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (!isChecklistMode) FontWeight.Bold else FontWeight.Medium,
                                        color = if (!isChecklistMode) NoteTheme.Primary else NoteTheme.OnSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Checklist mode button
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        if (!isChecklistMode) {
                                            // Convert text → checklist
                                            checklistItems = if (description.isNotBlank()) {
                                                ChecklistParser.textToChecklist(description)
                                            } else {
                                                listOf(ChecklistItem(order = 0))
                                            }
                                            isChecklistMode = true
                                        }
                                    },
                                shape = RoundedCornerShape(Constants.CORNER_RADIUS_SMALL.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isChecklistMode) NoteTheme.Primary.copy(alpha = 0.15f)
                                    else Color.Transparent
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Outlined.CheckCircle,
                                        contentDescription = null,
                                        tint = if (isChecklistMode) NoteTheme.Primary else NoteTheme.OnSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "Checklist",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (isChecklistMode) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isChecklistMode) NoteTheme.Primary else NoteTheme.OnSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    } // end AnimatedVisibility contentVisible

                    // Phase 1: Checklist Editor Section
                    if (isChecklistMode) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 200.dp)
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
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Outlined.CheckCircle,
                                            contentDescription = null,
                                            tint = NoteTheme.Primary,
                                            modifier = Modifier.size(Constants.ICON_SIZE_MEDIUM.dp)
                                        )
                                        Spacer(modifier = Modifier.width(Constants.PADDING_SMALL.dp))
                                        Text(
                                            text = "Checklist",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = NoteTheme.OnSurface,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    // Item count
                                    val checkedCount = checklistItems.count { it.isChecked }
                                    val totalCount = checklistItems.count { it.text.isNotBlank() }
                                    if (totalCount > 0) {
                                        Text(
                                            text = "$checkedCount/$totalCount done",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = NoteTheme.Primary,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(Constants.PADDING_MEDIUM.dp))

                                // Checklist items with drag-to-reorder
                                com.amvarpvtltd.swiftNote.components.ReorderableChecklistColumn(
                                    items = checklistItems,
                                    onReorder = { fromIndex, toIndex ->
                                        checklistItems = checklistItems.toMutableList().also {
                                            val item = it.removeAt(fromIndex)
                                            it.add(toIndex, item)
                                        }
                                    },
                                    onCheckedChange = { index, checked ->
                                        checklistItems = checklistItems.toMutableList().also {
                                            it[index] = it[index].copy(isChecked = checked)
                                        }
                                    },
                                    onTextChange = { index, newText ->
                                        checklistItems = checklistItems.toMutableList().also {
                                            it[index] = it[index].copy(text = newText)
                                        }
                                    },
                                    onDelete = { index ->
                                        if (checklistItems.size > 1) {
                                            checklistItems = checklistItems.toMutableList().also {
                                                it.removeAt(index)
                                            }
                                            focusedItemIndex = (index - 1).coerceAtLeast(0)
                                        }
                                    },
                                    onEnterPressed = { index ->
                                        if (ChecklistParser.canAddMoreItems(checklistItems)) {
                                            val newItem = ChecklistItem(order = index + 1)
                                            checklistItems = checklistItems.toMutableList().also {
                                                it.add(index + 1, newItem)
                                            }
                                            focusedItemIndex = index + 1
                                        }
                                    },
                                    onBackspaceOnEmpty = { index ->
                                        if (checklistItems.size > 1) {
                                            checklistItems = checklistItems.toMutableList().also {
                                                it.removeAt(index)
                                            }
                                            focusedItemIndex = (index - 1).coerceAtLeast(0)
                                        }
                                    },
                                    focusedItemIndex = focusedItemIndex
                                )

                                // Reset focusedItemIndex after it has been consumed
                                LaunchedEffect(focusedItemIndex) {
                                    if (focusedItemIndex >= 0) {
                                        kotlinx.coroutines.delay(100)
                                        focusedItemIndex = -1
                                    }
                                }

                                // Add item button
                                if (ChecklistParser.canAddMoreItems(checklistItems)) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                val newItem = ChecklistItem(order = checklistItems.size)
                                                checklistItems = checklistItems + newItem
                                                focusedItemIndex = checklistItems.size // will be new last index
                                            }
                                            .padding(vertical = 8.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Outlined.Check,
                                            contentDescription = "Add item",
                                            tint = NoteTheme.Primary.copy(alpha = 0.6f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            "Add item",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = NoteTheme.Primary.copy(alpha = 0.6f),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                    // Description Section
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp) // Set minimum height instead of weight
                            .animateContentSize(),
                        colors = CardDefaults.cardColors(
                            containerColor = NoteTheme.Surface
                        ),
                        shape = RoundedCornerShape(20.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)
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
                    } // end else (text mode description)

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

                    // Phase 1: Smart Reminder Suggestion Chips — interactive chips instead of auto-fire
                    if (detectedReminders.isNotEmpty() && pendingReminders.isNotEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = NoteTheme.Secondary.copy(alpha = 0.06f)
                            ),
                            shape = RoundedCornerShape(Constants.CORNER_RADIUS_MEDIUM.dp),
                            border = BorderStroke(1.dp, NoteTheme.Secondary.copy(alpha = 0.2f))
                        ) {
                            Column(
                                modifier = Modifier.padding(Constants.PADDING_MEDIUM.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Outlined.NotificationsActive,
                                        contentDescription = null,
                                        tint = NoteTheme.Secondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(Constants.PADDING_SMALL.dp))
                                    Text(
                                        text = "Smart Reminder Suggestion",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = NoteTheme.Secondary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Spacer(modifier = Modifier.height(Constants.PADDING_SMALL.dp))

                                pendingReminders.forEach { reminder ->
                                    val timeText = remember(reminder.reminderDateTime) {
                                        val sdf = java.text.SimpleDateFormat("MMM dd, hh:mm a", java.util.Locale.getDefault())
                                        sdf.format(java.util.Date(reminder.reminderDateTime))
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .background(
                                                NoteTheme.Secondary.copy(alpha = 0.08f),
                                                RoundedCornerShape(Constants.CORNER_RADIUS_SMALL.dp)
                                            )
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "\"${reminder.extractedText}\"",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = NoteTheme.OnSurface,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "⏰ $timeText",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = NoteTheme.OnSurfaceVariant
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        // Confirm chip
                                        Card(
                                            modifier = Modifier.clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                // Accept: create the reminder
                                                scope.launch {
                                                    try {
                                                        if (isEditing && noteId != null) {
                                                            withContext(Dispatchers.IO) {
                                                                reminderManager.createReminderFromDetection(reminder, noteId)
                                                            }
                                                        }
                                                        // Move from pending to confirmed
                                                        pendingReminders = pendingReminders.filter { it.id != reminder.id }
                                                        detectedReminders = detectedReminders.map {
                                                            if (it.id == reminder.id) it.copy(isConfirmed = true) else it
                                                        }
                                                        withContext(Dispatchers.Main) {
                                                            Toast.makeText(context, "⏰ Reminder set!", Toast.LENGTH_SHORT).show()
                                                        }
                                                    } catch (e: Exception) {
                                                        Log.e("AddScreen", "Error confirming reminder", e)
                                                    }
                                                }
                                            },
                                            shape = RoundedCornerShape(Constants.CORNER_RADIUS_SMALL.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = NoteTheme.Primary.copy(alpha = 0.15f)
                                            ),
                                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                                        ) {
                                            Text(
                                                text = "Set",
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = NoteTheme.Primary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(6.dp))

                                        // Dismiss chip
                                        Card(
                                            modifier = Modifier.clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                // Dismiss this suggestion
                                                pendingReminders = pendingReminders.filter { it.id != reminder.id }
                                                if (pendingReminders.isEmpty()) {
                                                    detectedReminders = emptyList()
                                                }
                                            },
                                            shape = RoundedCornerShape(Constants.CORNER_RADIUS_SMALL.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = NoteTheme.OnSurfaceVariant.copy(alpha = 0.08f)
                                            ),
                                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                                        ) {
                                            Icon(
                                                Icons.Outlined.Close,
                                                contentDescription = "Dismiss",
                                                modifier = Modifier.padding(6.dp).size(16.dp),
                                                tint = NoteTheme.OnSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Confirmed Reminders Status — shows after user taps "Set"
                    if (detectedReminders.any { it.isConfirmed }) {
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
                                val confirmedCount = detectedReminders.count { it.isConfirmed }
                                Text(
                                    text = "✅ $confirmedCount reminder${if (confirmedCount > 1) "s" else ""} set",
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
                                    Text(text = titleFormatted ?: AnnotatedString(""), style = MaterialTheme.typography.titleMedium)
                                    Spacer(modifier = Modifier.height(Constants.PADDING_SMALL.dp))
                                }
                                if (descriptionFormatted != null) {
                                    Text(text = "Formatted Description Preview:", style = MaterialTheme.typography.bodySmall, color = NoteTheme.OnSurfaceVariant)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = descriptionFormatted ?: AnnotatedString(""), style = MaterialTheme.typography.bodyMedium)
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
        } // end NoteScreenBackground
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
