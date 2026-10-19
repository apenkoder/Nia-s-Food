package com.apenkoder.niasfood.data.security

import at.favre.lib.crypto.bcrypt.BCrypt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Real bcrypt (cost 12) with a per-hash random salt. Always call the suspend versions from the UI layer. */
object PinHasher {
    const val COST = 12

    fun hash(pin: String): String = BCrypt.withDefaults().hashToString(COST, pin.toCharArray())

    fun verify(pin: String, hash: String): Boolean =
        BCrypt.verifyer().verify(pin.toCharArray(), hash).verified

    suspend fun hashAsync(pin: String): String = withContext(Dispatchers.Default) { hash(pin) }

    suspend fun verifyAsync(pin: String, hash: String): Boolean =
        withContext(Dispatchers.Default) { verify(pin, hash) }
}
