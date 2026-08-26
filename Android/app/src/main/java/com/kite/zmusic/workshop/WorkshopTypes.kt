package com.kite.zmusic.workshop

data class WorkshopSignature(
    val kid: String,
    val alg: String,
    val sig: String,
)

data class WorkshopPluginCard(
    val id: String,
    val name: String,
    val version: Int,
    val description: String,
    val coverUrl: String,
    val author: String,
    val publisherUid: String,
    val ratingAvg: Double,
    val ratingCount: Int,
    val downloads: Int,
    val updatedAt: Long,
    val engineMin: Int,
    val engineMax: Int?,
)

data class WorkshopPluginDetail(
    val card: WorkshopPluginCard,
    val readme: String,
    val readmeTruncated: Boolean,
    val sizeBytes: Long,
    val sha256: String,
    val signature: WorkshopSignature,
    val myRating: Int?,
    val packageUrl: String = "",
)

data class WorkshopComment(
    val id: String,
    val uid: String,
    val nickname: String,
    val avatarUrl: String,
    val body: String,
    val createdAt: Long,
)

data class WorkshopRatingResult(
    val ratingAvg: Double,
    val ratingCount: Int,
    val myRating: Int?,
)

data class WorkshopPage<T>(
    val ok: Boolean,
    val error: String,
    val more: Boolean,
    val entries: List<T>,
)

sealed class WorkshopApiError : Exception() {
    data object Unauthorized : WorkshopApiError()
    data object RateLimited : WorkshopApiError()
    data object Missing : WorkshopApiError()
    data class Message(override val message: String) : WorkshopApiError()
}
