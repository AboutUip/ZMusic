package com.kite.zmusic.workshop

import android.util.Base64

/**
 * 工坊 Ed25519 公钥表（kid → 32 字节）。私钥只在社区机。
 */
object WorkshopKeys {
    const val KID_WORKSHOP_1 = "workshop-1"

    private val BY_KID: Map<String, ByteArray> = mapOf(
        KID_WORKSHOP_1 to Base64.decode(
            "XCi5FGrasOtnawhTolion0ifRhxlJ5YTN0u7OuGJkeQ=",
            Base64.DEFAULT,
        ),
    )

    init {
        BY_KID.forEach { (kid, key) ->
            require(key.size == 32) { "workshop pubkey $kid must be 32 bytes" }
        }
    }

    fun publicKey(kid: String): ByteArray? = BY_KID[kid.trim()]?.copyOf()

    fun knownKids(): Set<String> = BY_KID.keys
}
