package com.darktemp.app.data.firebase

import com.google.firebase.database.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal suspend fun <T> repositoryResult(action: suspend () -> T): Result<T> = try {
    Result.success(action())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    Result.failure(error)
}

internal suspend fun DatabaseReference.transact(action: (MutableData) -> Transaction.Result): Boolean =
    suspendCancellableCoroutine { continuation ->
        var operationError: Exception? = null
        runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                if (!continuation.isActive) return Transaction.abort()
                return try {
                    action(currentData)
                } catch (error: Exception) {
                    operationError = error
                    Transaction.abort()
                }
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                if (!continuation.isActive) return
                val failure = operationError ?: error?.toException()
                if (failure != null) continuation.resumeWithException(failure)
                else continuation.resume(committed)
            }
        }, false)
    }
