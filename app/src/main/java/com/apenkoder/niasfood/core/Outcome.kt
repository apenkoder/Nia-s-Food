package com.apenkoder.niasfood.core

import kotlinx.coroutines.CancellationException

/** Reasons a business action can fail. The UI maps each one to a string resource. */
enum class AppError {
    NOT_ALLOWED, SHIFT_CLOSED, EMPTY_CART, SOLD_OUT, CASH_SHORT, BAD_MM_CODE, DUPLICATE_MM_CODE,
    AMOUNT_INVALID, REASON_REQUIRED, DESCRIPTION_REQUIRED, WEAK_PIN, BAD_PIN_FORMAT,
    SHIFT_ALREADY_OPEN, UNVERIFIED_PAYMENTS, VARIANCE_REASON_REQUIRED, ORDER_CLOSED_SHIFT,
    ALREADY_VOIDED, DISCOUNT_INVALID, APPROVAL_REQUIRED, NOT_FOUND, NAME_REQUIRED, BAD_TIME, PIN_MISMATCH, STORAGE
}

class AppException(val error: AppError, val detail: String? = null) : Exception(error.name)

sealed interface Outcome<out T> {
    data class Ok<T>(val value: T) : Outcome<T>
    data class Fail(val error: AppError, val detail: String? = null) : Outcome<Nothing>
}

/** Runs [block]; business failures become [Outcome.Fail], cancellation is rethrown. */
suspend fun <T> attempt(block: suspend () -> T): Outcome<T> = try {
    Outcome.Ok(block())
} catch (e: AppException) {
    Outcome.Fail(e.error, e.detail)
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Outcome.Fail(AppError.STORAGE)
}
