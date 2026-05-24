package com.amvarpvtltd.swiftNote.ai

/**
 * Sealed hierarchy representing entities detected in note text.
 * Each variant carries the minimum info needed to render action chips.
 */
sealed class DetectedEntity {
    /** Human-readable text as found in the note */
    abstract val raw: String

    data class PhoneNumber(
        override val raw: String,
        val normalized: String
    ) : DetectedEntity()

    data class Email(
        override val raw: String,
        val address: String
    ) : DetectedEntity()

    data class Url(
        override val raw: String,
        val url: String
    ) : DetectedEntity()

    data class Address(
        override val raw: String,
        val text: String
    ) : DetectedEntity()

    data class DateTime(
        override val raw: String,
        val text: String
    ) : DetectedEntity()

    data class Amount(
        override val raw: String,
        val value: Double,
        val currency: String? = null
    ) : DetectedEntity()

    data class TrackingNumber(
        override val raw: String,
        val carrier: String? = null
    ) : DetectedEntity()
}
