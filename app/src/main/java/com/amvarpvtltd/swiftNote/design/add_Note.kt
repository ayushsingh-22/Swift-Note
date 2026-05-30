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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.amvarpvtltd.swiftNote.ai.DetectedReminder
import com.amvarpvtltd.swiftNote.ai.SmartReminderAI
import com.amvarpvtltd.swiftNote.checklist.ChecklistItem
import com.amvarpvtltd.swiftNote.checklist.ChecklistParser
import com.amvarpvtltd.swiftNote.components.IconActionButton
import com.amvarpvtltd.swiftNote.components.LoadingCard
import com.amvarpvtltd.swiftNote.components.NoteScreenBackground
import com.amvarpvtltd.swiftNote.components.RichTextDisplay
import com.amvarpvtltd.swiftNote.components.RichTextToolbar
import com.amvarpvtltd.swiftNote.reminders.ReminderManager
import com.amvarpvtltd.swiftNote.repository.NoteRepository
import com.amvarpvtltd.swiftNote.richtext.RichTextBridge
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichTextEditor
import com.mohamedrejeb.richeditor.ui.material3.RichTextEditorDefaults
import com.amvarpvtltd.swiftNote.ai.AITitleGenerator
import com.amvarpvtltd.swiftNote.theme.ProvideNoteTheme
import com.amvarpvtltd.swiftNote.utils.AutoTitleGenerator
import com.amvarpvtltd.swiftNote.utils.Constants
import com.amvarpvtltd.swiftNote.utils.UIUtils
import com.amvarpvtltd.swiftNote.utils.ValidationUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Top-level Regex constants — avoids recreating on every recomposition
private val MINUTE_REGEX = Regex(
    "\\b(\\d{1,3})\\s*(?:min|mins|minm|minute|minutes)\\s*(?:mai|mein)?\\b",
    RegexOption.IGNORE_CASE
)
private val DIGIT_REGEX = Regex("(\\d+)")
private val HTML_TAG_REGEX = Regex("<[^>]+>")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddScreen(navController: NavHostController, noteId: String?) {
    var title by remember { mutableStateOf("") }
    // Phase 2: description migrated to RichTextState (compose-rich-editor library)
    val richTextState = rememberRichTextState()
    var isLoading by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showBackDialog by remember { mutableStateOf(false) }

    // Smart Reminders state
    var detectedReminders by remember { mutableStateOf<List<DetectedReminder>>(emptyList()) }
    var pendingReminders by remember { mutableStateOf<List<DetectedReminder>>(emptyList()) }
    // Reminders explicitly confirmed by the user via "Set" chip on a NEW note (not yet saved).
    // These are carried forward and created at save time alongside pendingReminders.
    var confirmedPendingReminders by remember { mutableStateOf<List<DetectedReminder>>(emptyList()) }
    var isAnalyzingText by remember { mutableStateOf(false) }

    // Permission state for notification/alarm access (Phase 0.2: point-of-use)
    var showPermissionRationale by remember { mutableStateOf(false) }
    var missingPermissionType by remember { mutableStateOf<com.amvarpvtltd.swiftNote.components.PermissionType?>(null) }
    var pendingReminderAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Phase 1: Checklist mode state
    var isChecklistMode by remember { mutableStateOf(false) }
    var checklistItems by remember { mutableStateOf(listOf(ChecklistItem(order = 0))) }
    var focusedItemIndex by remember { mutableStateOf(-1) }
    // Phase 4: Category state
    var selectedCategory by remember { mutableStateOf("") }
    // Phase 2: Toolbar focus + preview mode
    var descriptionFocused by remember { mutableStateOf(false) }
    var isPreviewMode by remember { mutableStateOf(false) }
    // Formatted preview states (when clipboard HTML is pasted)
    var titleFormatted by remember { mutableStateOf<AnnotatedString?>(null) }
    // Preserve original HTML (if pasted) so we can save/load formatted content
    var titleHtml by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val noteRepository = remember { NoteRepository(context) }
    val reminderManager = remember { ReminderManager.getInstance(context) }
    val smartReminderAI = remember { SmartReminderAI.getInstance(context) }
    val scope = rememberCoroutineScope()
    val titleFocusRequester = remember { FocusRequester() }
    val hapticFeedback = LocalHapticFeedback.current
    var categoryOptions by remember { mutableStateOf(com.amvarpvtltd.swiftNote.categories.CategoryManager.getAll(context)) }
    var showCustomCategoryDialog by remember { mutableStateOf(false) }
    var customCategoryName by remember { mutableStateOf("") }
    var customCategoryColor by remember {
        mutableStateOf(com.amvarpvtltd.swiftNote.categories.CategoryManager.presetColors.first())
    }

    val isEditing = noteId != null
    // Performance: derivedStateOf prevents recomposition unless the derived value actually changes
    val canSave by remember { derivedStateOf {
        if (isChecklistMode) {
            checklistItems.any { it.text.isNotBlank() } &&
                (title.trim().length >= Constants.MIN_CONTENT_LENGTH ||
                    AutoTitleGenerator.canGenerateTitle(ChecklistParser.serializeItems(checklistItems)))
        } else {
            ValidationUtils.canSaveNote(title, richTextState.annotatedString.text)
        }
    } }
    val hasContent by remember { derivedStateOf {
        title.trim().isNotEmpty() || richTextState.annotatedString.text.trim().isNotEmpty() ||
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
    val safeDescriptionLength by remember { derivedStateOf { richTextState.annotatedString.text.length.coerceAtMost(Constants.DESCRIPTION_MAX_LENGTH) } }

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

    // BUG-015 FIX: Clipboard HTML detection for title only — description is handled natively by RichTextEditor
    LaunchedEffect(Unit) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            val item = clip?.getItemAt(0)
            val htmlText = item?.htmlText
            val plain = item?.coerceToText(context)?.toString()

            if (!htmlText.isNullOrBlank() && !plain.isNullOrBlank()) {
                val spanned: Spanned = fromHtml(htmlText, Html.FROM_HTML_MODE_LEGACY)
                if (plain.trim() == title.trim() && title.isNotBlank()) {
                    titleFormatted = AnnotatedString(spanned.toString())
                    titleHtml = htmlText
                }
            }
        } catch (e: Exception) {
            Log.d("AddScreen", "Clipboard HTML detection skipped: ${e.message}")
        }
    }

    // Phase 3: Smart paste detection — auto-convert markdown to HTML when pasted
    // Monitors text length for sudden large increases (>20 chars = likely a paste event).
    // If pasted text contains markdown, converts to HTML for proper rich text display.
    var previousTextLength by remember { mutableStateOf(-1) }
    var skipNextConversion by remember { mutableStateOf(false) }
    LaunchedEffect(richTextState.annotatedString.text) {
        val currentText = richTextState.annotatedString.text
        val currentLength = currentText.length

        // Skip if this is the result of our own setHtml conversion
        if (skipNextConversion) {
            skipNextConversion = false
            previousTextLength = currentLength
            return@LaunchedEffect
        }

        val delta = currentLength - previousTextLength

        // Detect paste: large sudden insertion (>15 chars) that isn't initial load
        // Also handle first paste into empty note (previousTextLength == 0)
        if (delta > 15 && previousTextLength >= 0 && !isLoading) {
            // Only convert if the visible text contains markdown patterns
            // (annotatedString.text is the rendered text without HTML tags)
            if (com.amvarpvtltd.swiftNote.richtext.MarkdownToHtmlConverter.containsMarkdown(currentText)) {
                val convertedHtml = com.amvarpvtltd.swiftNote.richtext.MarkdownToHtmlConverter.convert(currentText)
                if (convertedHtml != null) {
                    skipNextConversion = true
                    richTextState.setHtml(convertedHtml)
                }
            }
        }

        previousTextLength = currentLength
    }

    // Phase 1: Derive checklist text for Smart Reminder AI analysis
    val checklistTextForAI by remember { derivedStateOf {
        if (isChecklistMode) checklistItems.joinToString(". ") { it.text } else ""
    } }

    // BUG-015 FIX: Debounced reminder analysis — only after user stops typing (800ms)
    // BUG-006 FIX: All Toast calls wrapped in withContext(Dispatchers.Main)
    LaunchedEffect(title, richTextState.annotatedString.text, checklistTextForAI) {
        val descriptionForAnalysis = if (isChecklistMode) checklistTextForAI
            else RichTextBridge.stripHtmlToPlainText(richTextState.toHtml())
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

        // Run minute-pattern fallback first (lightweight regex — OK after debounce)
        try {
            val match = MINUTE_REGEX.find(combinedText)
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
                        description = RichTextBridge.stripHtmlToPlainText(richTextState.toHtml()),
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
                        // Phase 4: Load category
                        selectedCategory = it.category

                        // If stored content contains HTML tags, render formatted preview and keep plain text in fields
                        val titleCandidate = it.title
                        val descCandidate = it.description

                        // Phase 1: Detect checklist content
                        if (ChecklistParser.isChecklistContent(descCandidate)) {
                            isChecklistMode = true
                            title = titleCandidate
                            val items = ChecklistParser.parseItems(descCandidate)
                            checklistItems = if (items.isEmpty()) listOf(ChecklistItem(order = 0)) else items
                            richTextState.setHtml("") // Keep description empty in checklist mode
                        } else {

                        fun looksLikeHtml(s: String) = HTML_TAG_REGEX.containsMatchIn(s)

                        if (looksLikeHtml(titleCandidate)) {
                            val sp = fromHtml(titleCandidate, Html.FROM_HTML_MODE_LEGACY)
                            titleFormatted = AnnotatedString(sp.toString())
                            titleHtml = titleCandidate
                            title = sp.toString()
                        } else {
                            title = titleCandidate
                        }

                        // Library handles HTML and plain text uniformly via setHtml().
                        // Phase 3: Also detect markdown content and convert for proper rendering.
                        val htmlToLoad = if (!HTML_TAG_REGEX.containsMatchIn(descCandidate) &&
                            com.amvarpvtltd.swiftNote.richtext.MarkdownToHtmlConverter.containsMarkdown(descCandidate)) {
                            com.amvarpvtltd.swiftNote.richtext.MarkdownToHtmlConverter.convert(descCandidate) ?: descCandidate
                        } else {
                            descCandidate
                        }
                        richTextState.setHtml(htmlToLoad)
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

    // Phase 5: Consume shared data from share intent or widget
    LaunchedEffect(Unit) {
        if (noteId == null && com.amvarpvtltd.swiftNote.share.SharedNoteData.hasPendingData) {
            val (sharedTitle, sharedDescription, asChecklist) = com.amvarpvtltd.swiftNote.share.SharedNoteData.consume()
            if (sharedTitle.isNotEmpty()) title = sharedTitle
            if (sharedDescription.isNotEmpty()) {
                // Phase 3: Detect markdown in shared content and convert to HTML
                val htmlToLoad = if (!HTML_TAG_REGEX.containsMatchIn(sharedDescription) &&
                    com.amvarpvtltd.swiftNote.richtext.MarkdownToHtmlConverter.containsMarkdown(sharedDescription)) {
                    com.amvarpvtltd.swiftNote.richtext.MarkdownToHtmlConverter.convert(sharedDescription) ?: sharedDescription
                } else {
                    sharedDescription
                }
                richTextState.setHtml(htmlToLoad)
            }
            if (asChecklist) {
                isChecklistMode = true
                checklistItems = listOf(ChecklistItem(order = 0))
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
                val descToSave = if (isChecklistMode) {
                    ChecklistParser.serializeItems(checklistItems)
                } else {
                    richTextState.toHtml()
                }

                // Phase 4: Auto-title — try LLM first, fall back to rule-based
                val effectiveTitle = if (title.trim().length >= Constants.MIN_CONTENT_LENGTH) {
                    title
                } else {
                    // Try AI-powered title generation (Gemini/Groq), falls back to rules
                    val aiTitleGen = AITitleGenerator.getInstance(context)
                    Log.d("AddScreen", "🤖 Auto-title: title is blank/short, trying AI generator. AI available: ${aiTitleGen.isAvailable()}")
                    val aiTitle = try {
                        aiTitleGen.generate(descToSave)
                    } catch (e: Exception) {
                        Log.e("AddScreen", "🤖 AI title generation threw exception", e)
                        ""
                    }
                    Log.d("AddScreen", "🤖 AI title result: \"$aiTitle\"")
                    aiTitle.ifEmpty { AutoTitleGenerator.generate(descToSave) }.ifEmpty { title }
                }
                val titleToSave = titleHtml ?: effectiveTitle

                val result = noteRepository.saveNote(
                    title = titleToSave,
                    description = descToSave,
                    noteId = noteId,
                    context = context,
                    category = selectedCategory
                )

               // kotlin
               // Add debug + safe navigation inside saveNote() success branch
               withContext(Dispatchers.Main) {
                   if (result.isSuccess) {
                       val savedNoteId = result.getOrNull()
                       val finalNoteId = savedNoteId ?: noteId

                        // Create any pending smart reminders for newly saved notes.
                        // Includes both:
                        //  • pendingReminders  — suggestions not yet explicitly confirmed
                        //  • confirmedPendingReminders — suggestions the user tapped "Set" on
                        //    while the note wasn't saved yet (new note path, isEditing=false)
                        val hasNotificationPermission = com.amvarpvtltd.swiftNote.components.checkReminderPermissions(context) == null
                        val remindersToCreate = (pendingReminders + confirmedPendingReminders).distinctBy { it.id }
                        if (finalNoteId != null && remindersToCreate.isNotEmpty() && hasNotificationPermission) {
                           var createdCount = 0
                           try {
                               withContext(Dispatchers.IO) {
                                   remindersToCreate.forEach { reminder ->
                                       if (reminder.confidence >= 0.6f) {
                                           try {
                                               // BUG-035 FIX: For minute-based reminders, recompute time from NOW (save time)
                                                val actualReminder = if (reminder.entityType == "MinuteFallback") {
                                                    val minuteMatch = DIGIT_REGEX.find(reminder.extractedText)
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
                                   // Clear all reminder queues and update detected list for UI
                                   detectedReminders = remindersToCreate.filter { it.confidence >= 0.6f }
                                   pendingReminders = emptyList()
                                   confirmedPendingReminders = emptyList()
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

    // Permission rationale sheet — shown before creating a reminder when notification permission is missing
    if (showPermissionRationale && missingPermissionType != null) {
        com.amvarpvtltd.swiftNote.components.PermissionRationaleSheet(
            permissionType = missingPermissionType!!,
            onDismiss = {
                showPermissionRationale = false
                missingPermissionType = null
                pendingReminderAction = null
            },
            onPermissionGranted = {
                showPermissionRationale = false
                missingPermissionType = null
                // Re-check: if there's another missing permission, show it; otherwise execute the pending action
                val nextMissing = com.amvarpvtltd.swiftNote.components.checkReminderPermissions(context)
                if (nextMissing != null) {
                    missingPermissionType = nextMissing
                    showPermissionRationale = true
                } else {
                    // Permission granted — execute the pending reminder action
                    pendingReminderAction?.invoke()
                    pendingReminderAction = null
                }
            }
        )
    }

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
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                                        Spacer(
                                            modifier = Modifier.width(
                                                Constants.CORNER_RADIUS_SMALL.dp
                                            )
                                        )
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(
                                                Constants.ICON_SIZE_MEDIUM.dp
                                            ),
                                            strokeWidth = 2.dp,
                                            color = NoteTheme.Primary
                                        )
                                    }
                                }
                            },

                            navigationIcon = {
                                IconActionButton(
                                    onClick = {
                                        hapticFeedback.performHapticFeedback(
                                            HapticFeedbackType.LongPress
                                        )
                                        if (hasContent && !isEditing) {
                                            showBackDialog =
                                                true
                                        } else {
                                            navController.navigateUp()
                                        }
                                    },
                                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    containerColor = NoteTheme.Primary.copy(
                                        alpha = 0.1f
                                    ),
                                    contentColor = NoteTheme.Primary,
                                    modifier = Modifier.padding(
                                        start = 12.dp
                                    )
                                )
                            },
                            actions = {
                                Row(
                                    modifier = Modifier.padding(
                                        end = 12.dp
                                    ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Phase 2: Preview toggle (only meaningful when not in checklist mode)
                                    if (!isChecklistMode) {
                                        IconActionButton(
                                            onClick = {
                                                isPreviewMode =
                                                    !isPreviewMode
                                            },
                                            icon = if (isPreviewMode)
                                                Icons.Outlined.EditNote
                                            else
                                                Icons.Outlined.Visibility,
                                            contentDescription = if (isPreviewMode) "Edit" else "Preview formatting",
                                            containerColor = if (isPreviewMode)
                                                NoteTheme.PrimaryContainer
                                            else
                                                NoteTheme.Primary.copy(
                                                    alpha = 0.1f
                                                ),
                                            contentColor = if (isPreviewMode)
                                                NoteTheme.OnPrimaryContainer
                                            else
                                                NoteTheme.Primary
                                        )
                                        Spacer(
                                            modifier = Modifier.width(
                                                4.dp
                                            )
                                        )
                                    }
                                    if (isEditing) {
                                        IconActionButton(
                                            onClick = {
                                                hapticFeedback.performHapticFeedback(
                                                    HapticFeedbackType.LongPress
                                                )
                                                showDeleteDialog =
                                                    true
                                            },
                                            icon = Icons.Outlined.Delete,
                                            contentDescription = "Delete note",
                                            containerColor = NoteTheme.Error.copy(
                                                alpha = 0.1f
                                            ),
                                            contentColor = NoteTheme.Error
                                        )
                                        Spacer(
                                            modifier = Modifier.width(
                                                4.dp
                                            )
                                        )
                                    }

                                    // Save / Update button in top bar
                                    if (!isLoading && canSave) {
                                        if (isSaving) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(
                                                    Constants.ICON_SIZE_MEDIUM.dp
                                                ),
                                                strokeWidth = 2.dp,
                                                color = NoteTheme.Primary
                                            )
                                        } else {
                                            IconActionButton(
                                                onClick = {
                                                    hapticFeedback.performHapticFeedback(
                                                        HapticFeedbackType.LongPress
                                                    )
                                                    saveNote()
                                                },
                                                icon = Icons.Outlined.Check,
                                                contentDescription = if (isEditing) "Update Note" else "Save Note",
                                                containerColor = NoteTheme.Primary,
                                                contentColor = NoteTheme.OnPrimary
                                            )
                                        }
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
                                modifier = Modifier.fillMaxWidth()
                                    .height(3.dp),
                                color = NoteTheme.Primary,
                                trackColor = NoteTheme.Primary.copy(
                                    alpha = 0.12f
                                ),
                                strokeCap = StrokeCap.Round
                            )
                        } else {
                            Spacer(
                                modifier = Modifier.height(
                                    3.dp
                                )
                            )
                        }
                    }
                },
                floatingActionButton = { },
            ) { paddingValues ->
                if (isLoading) {
                    LoadingCard(
                        "Loading note...",
                        "Fetching from local storage..."
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .imePadding()
                            .padding(
                                bottom = if (descriptionFocused && !isChecklistMode && !isPreviewMode)
                                    56.dp else 0.dp
                            )
                            .padding(Constants.PADDING_MEDIUM.dp)
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(Constants.CORNER_RADIUS_LARGE.dp)
                    ) {
                        // Title Section
                        AnimatedVisibility(
                            visible = heroVisible,
                            enter = fadeIn(
                                tween(
                                    400
                                )
                            ) + slideInVertically(
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
                                shape = RoundedCornerShape(
                                    20.dp
                                ),
                                elevation = CardDefaults.cardElevation(
                                    defaultElevation = 1.dp
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    NoteTheme.Outline.copy(
                                        alpha = 0.5f
                                    )
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(
                                        horizontal = 20.dp,
                                        vertical = 18.dp
                                    )
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
                                                modifier = Modifier.size(
                                                    Constants.ICON_SIZE_MEDIUM.dp
                                                )
                                            )
                                            Spacer(
                                                modifier = Modifier.width(
                                                    Constants.PADDING_SMALL.dp
                                                )
                                            )
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
                                                    .size(
                                                        Constants.PROGRESS_INDICATOR_SIZE.dp
                                                    )
                                                    .background(
                                                        color = titleCountColor.copy(
                                                            alpha = 0.1f
                                                        ),
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

                                            Spacer(
                                                modifier = Modifier.width(
                                                    Constants.PADDING_SMALL.dp
                                                )
                                            )

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

                                    Spacer(
                                        modifier = Modifier.height(
                                            Constants.PADDING_MEDIUM.dp
                                        )
                                    )

                                    OutlinedTextField(
                                        value = title,
                                        onValueChange = {
                                            if (it.length <= Constants.TITLE_MAX_LENGTH) title =
                                                it
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusRequester(
                                                titleFocusRequester
                                            ),
                                        placeholder = {
                                            Text(
                                                "Enter a compelling title...",
                                                color = NoteTheme.OnSurfaceVariant.copy(
                                                    alpha = 0.5f
                                                )
                                            )
                                        },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(
                                            imeAction = ImeAction.Next
                                        ),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = titleCountColor,
                                            unfocusedBorderColor = NoteTheme.OnSurfaceVariant.copy(
                                                alpha = 0.3f
                                            ),
                                            cursorColor = NoteTheme.Primary,
                                            focusedLabelColor = titleCountColor,
                                            focusedTextColor = NoteTheme.OnSurface,
                                            unfocusedTextColor = NoteTheme.OnSurface
                                        ),
                                        shape = RoundedCornerShape(
                                            Constants.CORNER_RADIUS_SMALL.dp
                                        )
                                    )

                                    // Title requirements indicator
                                    AnimatedVisibility(
                                        visible = !ValidationUtils.isValidTitle(
                                            title
                                        ),
                                        enter = slideInVertically() + fadeIn(),
                                        exit = slideOutVertically() + fadeOut()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(
                                                top = Constants.PADDING_SMALL.dp
                                            ),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Info,
                                                contentDescription = null,
                                                tint = titleCountColor,
                                                modifier = Modifier.size(
                                                    Constants.ICON_SIZE_SMALL.dp
                                                )
                                            )
                                            Spacer(
                                                modifier = Modifier.width(
                                                    6.dp
                                                )
                                            )
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

                        // Phase 4: Auto-title suggestion chip (rule-based instant + AI-enhanced async)
                        val descriptionText = richTextState.annotatedString.text
                        // Instant rule-based suggestion
                        val ruleBasedTitle = remember(descriptionText, title, isChecklistMode, checklistItems) {
                            if (title.isBlank() && (descriptionText.isNotBlank() || (isChecklistMode && checklistItems.any { it.text.isNotBlank() }))) {
                                AutoTitleGenerator.generate(
                                    if (isChecklistMode) ChecklistParser.serializeItems(checklistItems)
                                    else descriptionText
                                )
                            } else ""
                        }

                        // AI-enhanced suggestion (debounced, async)
                        var aiSuggestedTitle by remember { mutableStateOf("") }
                        var isAiTitleLoading by remember { mutableStateOf(false) }
                        LaunchedEffect(descriptionText, title, isChecklistMode, checklistItems) {
                            aiSuggestedTitle = "" // Reset on new input
                            if (title.isNotBlank() || (descriptionText.isBlank() && !isChecklistMode)) {
                                return@LaunchedEffect
                            }
                            val descForAI = if (isChecklistMode) ChecklistParser.serializeItems(checklistItems)
                                else richTextState.toHtml()
                            if (descForAI.isBlank()) return@LaunchedEffect

                            // Debounce: wait 1.5s after user stops typing before calling LLM
                            delay(1500)

                            val aiGen = AITitleGenerator.getInstance(context)
                            if (!aiGen.isAvailable()) {
                                Log.d("AddScreen", "🤖 AI title chip: AI not available (no API key)")
                                return@LaunchedEffect
                            }

                            isAiTitleLoading = true
                            Log.d("AddScreen", "🤖 AI title chip: triggering AI generation...")
                            try {
                                val result = withContext(Dispatchers.IO) {
                                    aiGen.generate(descForAI)
                                }
                                if (result.isNotBlank() && result != ruleBasedTitle) {
                                    aiSuggestedTitle = result
                                    Log.d("AddScreen", "🤖 AI title chip: got result \"$result\"")
                                }
                            } catch (e: Exception) {
                                Log.e("AddScreen", "🤖 AI title chip: failed", e)
                            } finally {
                                isAiTitleLoading = false
                            }
                        }

                        // Show the best available title (AI > rule-based)
                        val suggestedTitle = aiSuggestedTitle.ifEmpty { ruleBasedTitle }

                        AnimatedVisibility(
                            visible = title.isBlank() && (suggestedTitle.isNotBlank() || isAiTitleLoading),
                            enter = fadeIn(tween(200)) + slideInVertically(initialOffsetY = { -it / 4 }),
                            exit = fadeOut(tween(150)) + slideOutVertically(targetOffsetY = { -it / 4 })
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        start = Constants.PADDING_SMALL.dp,
                                        top = 4.dp,
                                        bottom = 4.dp
                                    )
                                    .clickable {
                                        if (suggestedTitle.isNotBlank()) {
                                            title = suggestedTitle
                                        }
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isAiTitleLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        strokeWidth = 1.5.dp,
                                        color = NoteTheme.Primary
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Outlined.EditNote,
                                        contentDescription = null,
                                        tint = NoteTheme.Primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (isAiTitleLoading && suggestedTitle.isBlank()) {
                                        "✨ Generating AI title..."
                                    } else if (aiSuggestedTitle.isNotBlank()) {
                                        "✨ \"$suggestedTitle\""
                                    } else {
                                        "Use \"$suggestedTitle\""
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NoteTheme.Primary,
                                    maxLines = 1
                                )
                            }
                        }

                        // Phase 4: Category Picker
                        AnimatedVisibility(
                            visible = contentVisible,
                            enter = fadeIn(
                                tween(
                                    350
                                )
                            ) + slideInVertically(
                                spring(Spring.DampingRatioMediumBouncy),
                                initialOffsetY = { it / 3 }
                            )
                        ) {
                            var showCategoryPicker by remember {
                                mutableStateOf(
                                    false
                                )
                            }

                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showCategoryPicker =
                                                !showCategoryPicker
                                        }
                                        .padding(
                                            vertical = 8.dp
                                        ),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(
                                        8.dp
                                    )
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Outlined.Label,
                                        contentDescription = null,
                                        tint = NoteTheme.OnSurfaceVariant,
                                        modifier = Modifier.size(
                                            18.dp
                                        )
                                    )
                                    Text(
                                        text = if (selectedCategory.isBlank()) "Add category" else selectedCategory,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (selectedCategory.isBlank()) NoteTheme.OnSurfaceVariant.copy(
                                            alpha = 0.6f
                                        ) else NoteTheme.OnSurface,
                                        fontWeight = if (selectedCategory.isNotBlank()) FontWeight.Medium else FontWeight.Normal
                                    )
                                    if (selectedCategory.isNotBlank()) {
                                        val catColor =
                                            com.amvarpvtltd.swiftNote.categories.CategoryManager.getCategoryColor(
                                                context,
                                                selectedCategory
                                            )
                                        if (catColor != null) {
                                            Box(
                                                modifier = Modifier
                                                    .size(
                                                        10.dp
                                                    )
                                                    .background(
                                                        catColor,
                                                        CircleShape
                                                    )
                                            )
                                        }
                                    }
                                }

                                AnimatedVisibility(
                                    visible = showCategoryPicker
                                ) {
                                    androidx.compose.foundation.lazy.LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(
                                            8.dp
                                        ),
                                        modifier = Modifier.padding(
                                            bottom = 8.dp
                                        )
                                    ) {
                                        // "None" option
                                        item {
                                            FilterChip(
                                                selected = selectedCategory.isBlank(),
                                                onClick = {
                                                    selectedCategory =
                                                        ""; showCategoryPicker =
                                                    false
                                                },
                                                label = {
                                                    Text(
                                                        "None",
                                                        fontSize = 12.sp,
                                                        color = NoteTheme.OnSurface
                                                    )
                                                }
                                            )
                                        }
                                        items(
                                            categoryOptions.size
                                        ) { idx ->
                                            val cat =
                                                categoryOptions[idx]
                                            FilterChip(
                                                selected = selectedCategory.equals(
                                                    cat.name,
                                                    ignoreCase = true
                                                ),
                                                onClick = {
                                                    selectedCategory =
                                                        cat.name; showCategoryPicker =
                                                    false
                                                },
                                                label = {
                                                    Text(
                                                        cat.name,
                                                        fontSize = 12.sp,
                                                        color = NoteTheme.OnSurface
                                                    )
                                                },
                                                leadingIcon = {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(
                                                                8.dp
                                                            )
                                                            .background(
                                                                cat.color,
                                                                CircleShape
                                                            )
                                                    )
                                                },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = cat.color.copy(
                                                        alpha = 0.15f
                                                    ),
                                                    selectedLabelColor = cat.color
                                                )
                                            )
                                        }
                                        item {
                                            FilterChip(
                                                selected = false,
                                                onClick = {
                                                    customCategoryName =
                                                        ""
                                                    customCategoryColor =
                                                        com.amvarpvtltd.swiftNote.categories.CategoryManager.presetColors.first()
                                                    showCustomCategoryDialog =
                                                        true
                                                },
                                                label = {
                                                    Text(
                                                        "Add custom",
                                                        fontSize = 12.sp
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (showCustomCategoryDialog) {
                            AlertDialog(
                                onDismissRequest = {
                                    showCustomCategoryDialog =
                                        false
                                },
                                title = {
                                    Text(
                                        text = "Create Category",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = NoteTheme.OnSurface
                                    )
                                },
                                text = {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(
                                            12.dp
                                        )
                                    ) {
                                        OutlinedTextField(
                                            value = customCategoryName,
                                            onValueChange = {
                                                customCategoryName =
                                                    it.take(
                                                        24
                                                    )
                                            },
                                            singleLine = true,
                                            label = {
                                                Text(
                                                    "Category name"
                                                )
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = NoteTheme.Primary,
                                                focusedLabelColor = NoteTheme.Primary
                                            )
                                        )

                                        Text(
                                            text = "Choose a color",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = NoteTheme.OnSurfaceVariant
                                        )

                                        androidx.compose.foundation.layout.FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(
                                                8.dp
                                            ),
                                            verticalArrangement = Arrangement.spacedBy(
                                                8.dp
                                            )
                                        ) {
                                            com.amvarpvtltd.swiftNote.categories.CategoryManager.presetColors.forEach { colorHex ->
                                                val swatchColor =
                                                    Color(
                                                        colorHex
                                                    )
                                                val isSelected =
                                                    customCategoryColor == colorHex
                                                Box(
                                                    modifier = Modifier
                                                        .size(
                                                            32.dp
                                                        )
                                                        .clip(
                                                            CircleShape
                                                        )
                                                        .background(
                                                            swatchColor
                                                        )
                                                        .border(
                                                            width = if (isSelected) 3.dp else 1.dp,
                                                            color = if (isSelected) NoteTheme.OnSurface else swatchColor.copy(
                                                                alpha = 0.25f
                                                            ),
                                                            shape = CircleShape
                                                        )
                                                        .clickable {
                                                            customCategoryColor =
                                                                colorHex
                                                        }
                                                )
                                            }
                                        }
                                    }
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            val trimmedName =
                                                customCategoryName.trim()
                                            if (trimmedName.isEmpty()) {
                                                Toast.makeText(
                                                    context,
                                                    "Category name can't be empty",
                                                    Toast.LENGTH_SHORT
                                                )
                                                    .show()
                                                return@TextButton
                                            }

                                            val added =
                                                com.amvarpvtltd.swiftNote.categories.CategoryManager.addCustom(
                                                    context = context,
                                                    name = trimmedName,
                                                    colorHex = customCategoryColor
                                                )
                                            if (added) {
                                                categoryOptions =
                                                    com.amvarpvtltd.swiftNote.categories.CategoryManager.getAll(
                                                        context
                                                    )
                                                selectedCategory =
                                                    trimmedName
                                                showCustomCategoryDialog =
                                                    false
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    "Category already exists or limit reached",
                                                    Toast.LENGTH_SHORT
                                                )
                                                    .show()
                                            }
                                        }
                                    ) {
                                        Text("Save")
                                    }
                                },
                                dismissButton = {
                                    TextButton(
                                        onClick = {
                                            showCustomCategoryDialog =
                                                false
                                        }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }

                        // Phase 1: Note Mode Toggle (Text / Checklist)
                        AnimatedVisibility(
                            visible = contentVisible,
                            enter = fadeIn(
                                tween(
                                    400
                                )
                            ) + slideInVertically(
                                spring(Spring.DampingRatioMediumBouncy),
                                initialOffsetY = { it / 3 }
                            )
                        ) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = NoteTheme.SurfaceVariant.copy(
                                        alpha = 0.3f
                                    )
                                ),
                                shape = RoundedCornerShape(
                                    Constants.CORNER_RADIUS_MEDIUM.dp
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            Constants.PADDING_SMALL.dp
                                        ),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Text mode button
                                    Card(
                                        modifier = Modifier
                                            .weight(
                                                1f
                                            )
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                if (isChecklistMode) {
                                                    // Convert checklist → text
                                                    richTextState.setHtml(
                                                        ChecklistParser.checklistToText(
                                                            checklistItems
                                                        )
                                                    )
                                                    isChecklistMode =
                                                        false
                                                }
                                            },
                                        shape = RoundedCornerShape(
                                            Constants.CORNER_RADIUS_SMALL.dp
                                        ),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (!isChecklistMode) NoteTheme.Primary.copy(
                                                alpha = 0.15f
                                            )
                                            else Color.Transparent
                                        ),
                                        elevation = CardDefaults.cardElevation(
                                            defaultElevation = 0.dp
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(
                                                    vertical = 10.dp
                                                ),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Outlined.Description,
                                                contentDescription = null,
                                                tint = if (!isChecklistMode) NoteTheme.Primary else NoteTheme.OnSurfaceVariant,
                                                modifier = Modifier.size(
                                                    18.dp
                                                )
                                            )
                                            Spacer(
                                                modifier = Modifier.width(
                                                    6.dp
                                                )
                                            )
                                            Text(
                                                "Text",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = if (!isChecklistMode) FontWeight.Bold else FontWeight.Medium,
                                                color = if (!isChecklistMode) NoteTheme.Primary else NoteTheme.OnSurfaceVariant
                                            )
                                        }
                                    }

                                    Spacer(
                                        modifier = Modifier.width(
                                            8.dp
                                        )
                                    )

                                    // Checklist mode button
                                    Card(
                                        modifier = Modifier
                                            .weight(
                                                1f
                                            )
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                if (!isChecklistMode) {
                                                    // Convert text → checklist
                                                    checklistItems =
                                                        if (richTextState.annotatedString.text.isNotBlank()) {
                                                            ChecklistParser.textToChecklist(
                                                                RichTextBridge.stripHtmlToPlainText(richTextState.toHtml())
                                                            )
                                                        } else {
                                                            listOf(
                                                                ChecklistItem(
                                                                    order = 0
                                                                )
                                                            )
                                                        }
                                                    isChecklistMode =
                                                        true
                                                }
                                            },
                                        shape = RoundedCornerShape(
                                            Constants.CORNER_RADIUS_SMALL.dp
                                        ),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isChecklistMode) NoteTheme.Primary.copy(
                                                alpha = 0.15f
                                            )
                                            else Color.Transparent
                                        ),
                                        elevation = CardDefaults.cardElevation(
                                            defaultElevation = 0.dp
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(
                                                    vertical = 10.dp
                                                ),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Outlined.CheckCircle,
                                                contentDescription = null,
                                                tint = if (isChecklistMode) NoteTheme.Primary else NoteTheme.OnSurfaceVariant,
                                                modifier = Modifier.size(
                                                    18.dp
                                                )
                                            )
                                            Spacer(
                                                modifier = Modifier.width(
                                                    6.dp
                                                )
                                            )
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
                                shape = RoundedCornerShape(
                                    Constants.CORNER_RADIUS_LARGE.dp
                                ),
                                elevation = CardDefaults.cardElevation(
                                    defaultElevation = 1.dp
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    NoteTheme.Outline.copy(
                                        alpha = 0.5f
                                    )
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(
                                        Constants.CORNER_RADIUS_LARGE.dp
                                    )
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
                                                modifier = Modifier.size(
                                                    Constants.ICON_SIZE_MEDIUM.dp
                                                )
                                            )
                                            Spacer(
                                                modifier = Modifier.width(
                                                    Constants.PADDING_SMALL.dp
                                                )
                                            )
                                            Text(
                                                text = "Checklist",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = NoteTheme.OnSurface,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }

                                        // Item count
                                        val checkedCount =
                                            checklistItems.count { it.isChecked }
                                        val totalCount =
                                            checklistItems.count { it.text.isNotBlank() }
                                        if (totalCount > 0) {
                                            Text(
                                                text = "$checkedCount/$totalCount done",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = NoteTheme.Primary,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }

                                    Spacer(
                                        modifier = Modifier.height(
                                            Constants.PADDING_MEDIUM.dp
                                        )
                                    )

                                    // Checklist items with drag-to-reorder
                                    com.amvarpvtltd.swiftNote.components.ReorderableChecklistColumn(
                                        items = checklistItems,
                                        onReorder = { fromIndex, toIndex ->
                                            checklistItems =
                                                checklistItems.toMutableList()
                                                    .also {
                                                        val item =
                                                            it.removeAt(
                                                                fromIndex
                                                            )
                                                        it.add(
                                                            toIndex,
                                                            item
                                                        )
                                                    }
                                        },
                                        onCheckedChange = { index, checked ->
                                            checklistItems =
                                                checklistItems.toMutableList()
                                                    .also {
                                                        it[index] =
                                                            it[index].copy(
                                                                isChecked = checked
                                                            )
                                                    }
                                        },
                                        onTextChange = { index, newText ->
                                            checklistItems =
                                                checklistItems.toMutableList()
                                                    .also {
                                                        it[index] =
                                                            it[index].copy(
                                                                text = newText
                                                            )
                                                    }
                                        },
                                        onDelete = { index ->
                                            if (checklistItems.size > 1) {
                                                checklistItems =
                                                    checklistItems.toMutableList()
                                                        .also {
                                                            it.removeAt(
                                                                index
                                                            )
                                                        }
                                                focusedItemIndex =
                                                    (index - 1).coerceAtLeast(
                                                        0
                                                    )
                                            }
                                        },
                                        onEnterPressed = { index ->
                                            if (ChecklistParser.canAddMoreItems(
                                                    checklistItems
                                                )
                                            ) {
                                                val newItem =
                                                    ChecklistItem(
                                                        order = index + 1
                                                    )
                                                checklistItems =
                                                    checklistItems.toMutableList()
                                                        .also {
                                                            it.add(
                                                                index + 1,
                                                                newItem
                                                            )
                                                        }
                                                focusedItemIndex =
                                                    index + 1
                                            }
                                        },
                                        onBackspaceOnEmpty = { index ->
                                            if (checklistItems.size > 1) {
                                                checklistItems =
                                                    checklistItems.toMutableList()
                                                        .also {
                                                            it.removeAt(
                                                                index
                                                            )
                                                        }
                                                focusedItemIndex =
                                                    (index - 1).coerceAtLeast(
                                                        0
                                                    )
                                            }
                                        },
                                        focusedItemIndex = focusedItemIndex
                                    )

                                    // Reset focusedItemIndex after it has been consumed
                                    LaunchedEffect(
                                        focusedItemIndex
                                    ) {
                                        if (focusedItemIndex >= 0) {
                                            delay(
                                                100
                                            )
                                            focusedItemIndex =
                                                -1
                                        }
                                    }

                                    // Add item button
                                    if (ChecklistParser.canAddMoreItems(
                                            checklistItems
                                        )
                                    ) {
                                        Spacer(
                                            modifier = Modifier.height(
                                                8.dp
                                            )
                                        )
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null
                                                ) {
                                                    val newItem =
                                                        ChecklistItem(
                                                            order = checklistItems.size
                                                        )
                                                    checklistItems =
                                                        checklistItems + newItem
                                                    focusedItemIndex =
                                                        checklistItems.size // will be new last index
                                                }
                                                .padding(
                                                    vertical = 8.dp,
                                                    horizontal = 4.dp
                                                ),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Outlined.Check,
                                                contentDescription = "Add item",
                                                tint = NoteTheme.Primary.copy(
                                                    alpha = 0.6f
                                                ),
                                                modifier = Modifier.size(
                                                    20.dp
                                                )
                                            )
                                            Spacer(
                                                modifier = Modifier.width(
                                                    12.dp
                                                )
                                            )
                                            Text(
                                                "Add item",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = NoteTheme.Primary.copy(
                                                    alpha = 0.6f
                                                ),
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
                                shape = RoundedCornerShape(
                                    20.dp
                                ),
                                elevation = CardDefaults.cardElevation(
                                    defaultElevation = 1.dp
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    NoteTheme.Outline.copy(
                                        alpha = 0.5f
                                    )
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(
                                        horizontal = 20.dp,
                                        vertical = 18.dp
                                    )
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
                                                modifier = Modifier.size(
                                                    Constants.ICON_SIZE_MEDIUM.dp
                                                )
                                            )
                                            Spacer(
                                                modifier = Modifier.width(
                                                    Constants.PADDING_SMALL.dp
                                                )
                                            )

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
                                                    .size(
                                                        Constants.PROGRESS_INDICATOR_SIZE.dp
                                                    )
                                                    .background(
                                                        color = descCountColor.copy(
                                                            alpha = 0.1f
                                                        ),
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

                                            Spacer(
                                                modifier = Modifier.width(
                                                    Constants.PADDING_SMALL.dp
                                                )
                                            )

                                            Text(
                                                text = UIUtils.formatCharacterCount(
                                                    richTextState.annotatedString.text.length,
                                                    Constants.DESCRIPTION_MAX_LENGTH
                                                ),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = descCountColor,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }

                                    Spacer(
                                        modifier = Modifier.height(
                                            Constants.PADDING_MEDIUM.dp
                                        )
                                    )

                                    // ── Phase 2: WYSIWYG editor (no preview/edit toggle needed) ───
                                    if (isPreviewMode) {
                                        androidx.compose.material3.Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(
                                                    min = 120.dp
                                                ),
                                            colors = androidx.compose.material3.CardDefaults.cardColors(
                                                containerColor = NoteTheme.SurfaceVariant.copy(
                                                    alpha = 0.5f
                                                )
                                            ),
                                            shape = RoundedCornerShape(
                                                Constants.CORNER_RADIUS_SMALL.dp
                                            )
                                        ) {
                                            RichTextDisplay(
                                                html = richTextState.toHtml(),
                                                modifier = Modifier.padding(
                                                    16.dp
                                                )
                                            )
                                        }
                                    } else {
                                        RichTextEditor(
                                            state = richTextState,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(
                                                    min = 200.dp
                                                )
                                                .onFocusChanged { focusState ->
                                                    descriptionFocused =
                                                        focusState.isFocused
                                                    if (focusState.isFocused) {
                                                        scope.launch {
                                                            delay(
                                                                300
                                                            )
                                                            scrollState.animateScrollTo(
                                                                scrollState.maxValue
                                                            )
                                                        }
                                                    }
                                                },
                                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                                color = NoteTheme.OnSurface
                                            ),
                                            placeholder = {
                                                Text(
                                                    "Write your thoughts here...\n\nExpress your ideas, capture important information, or jot down anything that comes to mind.",
                                                    color = NoteTheme.OnSurfaceVariant.copy(
                                                        alpha = 0.5f
                                                    )
                                                )
                                            },
                                            colors = RichTextEditorDefaults.outlinedRichTextEditorColors(
                                                focusedBorderColor = descCountColor,
                                                unfocusedBorderColor = NoteTheme.OnSurfaceVariant.copy(
                                                    alpha = 0.3f
                                                ),
                                                cursorColor = NoteTheme.Secondary
                                            ),
                                            shape = RoundedCornerShape(
                                                Constants.CORNER_RADIUS_SMALL.dp
                                            )
                                        )
                                    } // end preview/edit toggle
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
                                    containerColor = NoteTheme.Warning.copy(
                                        alpha = 0.1f
                                    )
                                ),
                                shape = RoundedCornerShape(
                                    Constants.CORNER_RADIUS_MEDIUM.dp
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    NoteTheme.Warning.copy(
                                        alpha = 0.3f
                                    )
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(
                                        Constants.PADDING_MEDIUM.dp
                                    ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Warning,
                                        contentDescription = null,
                                        tint = NoteTheme.Warning,
                                        modifier = Modifier.size(
                                            Constants.ICON_SIZE_MEDIUM.dp
                                        )
                                    )
                                    Spacer(
                                        modifier = Modifier.width(
                                            Constants.CORNER_RADIUS_SMALL.dp
                                        )
                                    )
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
                                    containerColor = NoteTheme.Primary.copy(
                                        alpha = 0.05f
                                    )
                                ),
                                shape = RoundedCornerShape(
                                    Constants.CORNER_RADIUS_MEDIUM.dp
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(
                                        Constants.PADDING_MEDIUM.dp
                                    ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(
                                            16.dp
                                        ),
                                        strokeWidth = 2.dp,
                                        color = NoteTheme.Primary
                                    )
                                    Spacer(
                                        modifier = Modifier.width(
                                            Constants.PADDING_SMALL.dp
                                        )
                                    )
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
                                    containerColor = NoteTheme.Secondary.copy(
                                        alpha = 0.06f
                                    )
                                ),
                                shape = RoundedCornerShape(
                                    Constants.CORNER_RADIUS_MEDIUM.dp
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    NoteTheme.Secondary.copy(
                                        alpha = 0.2f
                                    )
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(
                                        Constants.PADDING_MEDIUM.dp
                                    )
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Outlined.NotificationsActive,
                                            contentDescription = null,
                                            tint = NoteTheme.Secondary,
                                            modifier = Modifier.size(
                                                18.dp
                                            )
                                        )
                                        Spacer(
                                            modifier = Modifier.width(
                                                Constants.PADDING_SMALL.dp
                                            )
                                        )
                                        Text(
                                            text = "Smart Reminder Suggestion",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = NoteTheme.Secondary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    Spacer(
                                        modifier = Modifier.height(
                                            Constants.PADDING_SMALL.dp
                                        )
                                    )

                                    pendingReminders.forEach { reminder ->
                                        val timeText =
                                            remember(
                                                reminder.reminderDateTime
                                            ) {
                                                val sdf =
                                                    java.text.SimpleDateFormat(
                                                        "MMM dd, hh:mm a",
                                                        java.util.Locale.getDefault()
                                                    )
                                                sdf.format(
                                                    java.util.Date(
                                                        reminder.reminderDateTime
                                                    )
                                                )
                                            }

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(
                                                    vertical = 4.dp
                                                )
                                                .background(
                                                    NoteTheme.Secondary.copy(
                                                        alpha = 0.08f
                                                    ),
                                                    RoundedCornerShape(
                                                        Constants.CORNER_RADIUS_SMALL.dp
                                                    )
                                                )
                                                .padding(
                                                    horizontal = 12.dp,
                                                    vertical = 10.dp
                                                ),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(
                                                modifier = Modifier.weight(
                                                    1f
                                                )
                                            ) {
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

                                            Spacer(
                                                modifier = Modifier.width(
                                                    8.dp
                                                )
                                            )

                                            // Confirm chip
                                            Card(
                                                modifier = Modifier.clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null
                                                ) {
                                                    // Check notification permission before creating reminder
                                                    val missing =
                                                        com.amvarpvtltd.swiftNote.components.checkReminderPermissions(
                                                            context
                                                        )
                                                                     if (missing != null) {
                                                                        // Store the action to execute after permission is granted
                                                                        pendingReminderAction =
                                                                            {
                                                                                scope.launch {
                                                                                    try {
                                                                                        if (isEditing) {
                                                                                            withContext(
                                                                                                Dispatchers.IO
                                                                                            ) {
                                                                                                reminderManager.createReminderFromDetection(
                                                                                                    reminder,
                                                                                                    noteId!!
                                                                                                )
                                                                                            }
                                                                                        } else {
                                                                                            // New note: queue for creation at save time
                                                                                            if (!confirmedPendingReminders.any { it.id == reminder.id }) {
                                                                                                confirmedPendingReminders = confirmedPendingReminders + reminder
                                                                                            }
                                                                                        }
                                                                                        pendingReminders =
                                                                                            pendingReminders.filter { it.id != reminder.id }
                                                                                        detectedReminders =
                                                                                            detectedReminders.map {
                                                                                                if (it.id == reminder.id) it.copy(
                                                                                                    isConfirmed = true
                                                                                                ) else it
                                                                                            }
                                                                                        Toast.makeText(
                                                                                            context,
                                                                                            if (isEditing) "⏰ Reminder set!" else "⏰ Reminder will be set when you save",
                                                                                            Toast.LENGTH_SHORT
                                                                                        )
                                                                                            .show()
                                                                                    } catch (e: Exception) {
                                                                                        Log.e(
                                                                                            "AddScreen",
                                                                                            "Error confirming reminder",
                                                                                            e
                                                                                        )
                                                                                    }
                                                                                }
                                                                            }
                                                                        missingPermissionType =
                                                                            missing
                                                                        showPermissionRationale =
                                                                            true
                                                                     } else {
                                                                        // Permission already granted — create the reminder
                                                                        scope.launch {
                                                                            try {
                                                                                if (isEditing) {
                                                                                    // Editing an existing note: create immediately
                                                                                    withContext(
                                                                                        Dispatchers.IO
                                                                                    ) {
                                                                                        reminderManager.createReminderFromDetection(
                                                                                            reminder,
                                                                                            noteId!!
                                                                                        )
                                                                                    }
                                                                                } else {
                                                                                    // New note not saved yet: queue for creation at save time
                                                                                    if (!confirmedPendingReminders.any { it.id == reminder.id }) {
                                                                                        confirmedPendingReminders = confirmedPendingReminders + reminder
                                                                                    }
                                                                                }
                                                                                // Move from pending to confirmed
                                                                                pendingReminders =
                                                                                    pendingReminders.filter { it.id != reminder.id }
                                                                                detectedReminders =
                                                                                    detectedReminders.map {
                                                                                        if (it.id == reminder.id) it.copy(
                                                                                            isConfirmed = true
                                                                                        ) else it
                                                                                    }
                                                                                withContext(
                                                                                    Dispatchers.Main
                                                                                ) {
                                                                                    Toast.makeText(
                                                                                        context,
                                                                                        if (isEditing) "⏰ Reminder set!" else "⏰ Reminder will be set when you save",
                                                                                        Toast.LENGTH_SHORT
                                                                                    )
                                                                                        .show()
                                                                                }
                                                                            } catch (e: Exception) {
                                                                                Log.e(
                                                                                    "AddScreen",
                                                                                    "Error confirming reminder",
                                                                                    e
                                                                                )
                                                                            }
                                                                        }
                                                                    }
                                                },
                                                shape = RoundedCornerShape(
                                                    Constants.CORNER_RADIUS_SMALL.dp
                                                ),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = NoteTheme.Primary.copy(
                                                        alpha = 0.15f
                                                    )
                                                ),
                                                elevation = CardDefaults.cardElevation(
                                                    defaultElevation = 0.dp
                                                )
                                            ) {
                                                Text(
                                                    text = "Set",
                                                    modifier = Modifier.padding(
                                                        horizontal = 12.dp,
                                                        vertical = 6.dp
                                                    ),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = NoteTheme.Primary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            Spacer(
                                                modifier = Modifier.width(
                                                    6.dp
                                                )
                                            )

                                            // Dismiss chip
                                            Card(
                                                modifier = Modifier.clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null
                                                ) {
                                                    // Dismiss this suggestion
                                                    pendingReminders =
                                                        pendingReminders.filter { it.id != reminder.id }
                                                    if (pendingReminders.isEmpty()) {
                                                        detectedReminders =
                                                            emptyList()
                                                    }
                                                },
                                                shape = RoundedCornerShape(
                                                    Constants.CORNER_RADIUS_SMALL.dp
                                                ),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = NoteTheme.OnSurfaceVariant.copy(
                                                        alpha = 0.08f
                                                    )
                                                ),
                                                elevation = CardDefaults.cardElevation(
                                                    defaultElevation = 0.dp
                                                )
                                            ) {
                                                Icon(
                                                    Icons.Outlined.Close,
                                                    contentDescription = "Dismiss",
                                                    modifier = Modifier.padding(
                                                        6.dp
                                                    )
                                                        .size(
                                                            16.dp
                                                        ),
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
                                    containerColor = NoteTheme.Success.copy(
                                        alpha = 0.05f
                                    )
                                ),
                                shape = RoundedCornerShape(
                                    Constants.CORNER_RADIUS_MEDIUM.dp
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(
                                        Constants.PADDING_MEDIUM.dp
                                    ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Outlined.CheckCircle,
                                        contentDescription = null,
                                        tint = NoteTheme.Success,
                                        modifier = Modifier.size(
                                            16.dp
                                        )
                                    )
                                    Spacer(
                                        modifier = Modifier.width(
                                            Constants.PADDING_SMALL.dp
                                        )
                                    )
                                    val confirmedCount =
                                        detectedReminders.count { it.isConfirmed }
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
                        if (titleFormatted != null) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = NoteTheme.SurfaceVariant.copy(
                                        alpha = 0.06f
                                    )
                                ),
                                shape = RoundedCornerShape(
                                    Constants.CORNER_RADIUS_MEDIUM.dp
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(
                                        Constants.PADDING_MEDIUM.dp
                                    )
                                ) {
                                    if (titleFormatted != null) {
                                        Text(
                                            text = "Formatted Title Preview:",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = NoteTheme.OnSurfaceVariant
                                        )
                                        Spacer(
                                            modifier = Modifier.height(
                                                4.dp
                                            )
                                        )
                                        Text(
                                            text = titleFormatted
                                                ?: AnnotatedString(
                                                    ""
                                                ),
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Spacer(
                                            modifier = Modifier.height(
                                                Constants.PADDING_SMALL.dp
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        /* // Formatted preview - already shown above */

                        // Auto-focus title when creating new note
                        LaunchedEffect(Unit) {
                            if (!isEditing && !isLoading) {
                                delay(Constants.LOADING_DELAY)
                                titleFocusRequester.requestFocus()
                            }
                        }

                        // Handle device back button
                        BackHandler(enabled = true) {
                            if (hasContent && !isEditing) {
                                showBackDialog = true
                            } else {
                                navController.navigateUp()
                            }
                        }

                        // Bottom spacer so content clears the FAB + toolbar
                        Spacer(modifier = Modifier.height(100.dp))
                    } // end scrollable Column

                    // ── Sticky rich-text toolbar overlay (above keyboard) ──────────
                    androidx.compose.animation.AnimatedVisibility(
                        visible = descriptionFocused && !isChecklistMode && !isPreviewMode,
                        modifier = Modifier.align(Alignment.BottomCenter),
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit  = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        androidx.compose.material3.Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .imePadding(),
                            color = NoteTheme.Surface,
                            shadowElevation = 6.dp,
                            tonalElevation  = 1.dp
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                androidx.compose.material3.HorizontalDivider(
                                    color = NoteTheme.Outline.copy(alpha = 0.25f),
                                    thickness = 1.dp
                                )
                                RichTextToolbar(
                                    state = richTextState,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                    } // end Box
                } // end else (not loading)
            }
        }
    }
}
// spannedToAnnotatedString has been moved to richtext/RichTextRenderer.kt
// Use RichTextRenderer.spannedToAnnotatedString() instead.
