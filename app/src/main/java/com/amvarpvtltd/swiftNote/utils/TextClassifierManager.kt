package com.amvarpvtltd.swiftNote.utils

import android.annotation.SuppressLint
import android.content.Context
import android.app.RemoteAction
import android.view.textclassifier.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manager class that provides smart text classification features using Android's TextClassifier
 */
class TextClassifierManager(private val context: Context) {

    private val textClassifier: TextClassifier by lazy {
        val classificationManager = context.getSystemService(Context.TEXT_CLASSIFICATION_SERVICE) as? TextClassificationManager
        classificationManager?.textClassifier ?: TextClassifier.NO_OP
    }

    /**
     * Asynchronously identifies entities in text (URLs, addresses, phone numbers, dates, etc.)
     * Returns a map of detected entity types and their positions
     */
    suspend fun detectTextEntities(text: String): Map<String, List<IntRange>> = withContext(Dispatchers.Default) {
        try {
            val result = mutableMapOf<String, MutableList<IntRange>>()
            val request = TextLinks.Request.Builder(text).build()
            val links = textClassifier.generateLinks(request)

            for (link in links.links) {
                SUPPORTED_TYPES.forEach { entityType ->
                    val score = link.getConfidenceScore(entityType)
                    if (score >= CONFIDENCE_THRESHOLD) {
                        result.getOrPut(entityType) { mutableListOf() }
                            .add(link.start..link.end)
                    }
                }
            }

            result.mapValues { it.value.toList() }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyMap()
        }
    }

    /**
     * Returns suggestions for text selection with custom actions
     */
    suspend fun getTextActions(text: String, startIndex: Int, endIndex: Int): List<RemoteAction> = withContext(Dispatchers.Default) {
        try {
            if (startIndex < 0 || endIndex > text.length || startIndex >= endIndex) {
                return@withContext emptyList()
            }

            val request = TextClassification.Request.Builder(text, startIndex, endIndex).build()
            val result = textClassifier.classifyText(request)
            result.actions.toList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    companion object {
        private const val CONFIDENCE_THRESHOLD = 0.7f
        private val SUPPORTED_TYPES = setOf(
            TextClassifier.TYPE_ADDRESS,
            TextClassifier.TYPE_EMAIL,
            TextClassifier.TYPE_PHONE,
            TextClassifier.TYPE_URL,
            TextClassifier.TYPE_DATE,
            TextClassifier.TYPE_DATE_TIME
        )

        // Singleton instance
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var INSTANCE: TextClassifierManager? = null

        fun getInstance(context: Context): TextClassifierManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TextClassifierManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
