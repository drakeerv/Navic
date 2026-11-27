/*
 * Navic - https://codeberg.org/paige/Navic
 * Copyright (c) 2026 paige & contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.util.network

import okhttp3.Interceptor
import okhttp3.Response
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

class RateLimitInterceptor(
    private val defaultCapacity: Double = 10.0,
    private val defaultRefillRate: Double = 2.0,
    private val maxRetries: Int = 4,
    private val baseBackoffMs: Long = 500,
    private val maxBackoffMs: Long = 30_000,
    private val maxServerWaitMs: Long = 120_000,
) : Interceptor {
    private class Bucket(
        var capacity: Double,
        var refillRate: Double
    ) {
        var tokens = capacity
        var lastRefill = System.nanoTime()
        var blockedUntil = 0L

        @Synchronized
        fun acquire() {
            while (true) {
                val now = System.nanoTime()
                refill(now)

                val waitUntil = max(
                    blockedUntil,
                    if (tokens >= 1.0) now
                    else now + ((1.0 - tokens) / refillRate * 1e9).toLong()
                )

                if (waitUntil <= now) {
                    tokens -= 1.0
                    return
                }

                val waitMs = max(1L, (waitUntil - now) / 1_000_000)
                Thread.sleep(waitMs)
            }
        }

        @Synchronized
        fun update(
            limit: Double?,
            remaining: Double?,
            resetAfterMs: Long?
        ) {
            val now = System.nanoTime()
            refill(now)

            if (limit != null && limit > 0) {
                capacity = limit
                tokens = min(tokens, capacity)
            }

            if (remaining != null) {
                tokens = min(tokens, remaining.coerceIn(0.0, capacity))
            }

            if (remaining != null && remaining <= 0 && resetAfterMs != null) {
                blockedUntil = max(
                    blockedUntil,
                    now + resetAfterMs * 1_000_000
                )
            }
        }

        @Synchronized
        fun block(durationMs: Long) {
            blockedUntil = max(
                blockedUntil,
                System.nanoTime() + durationMs * 1_000_000
            )
        }

        private fun refill(now: Long) {
            val elapsed = (now - lastRefill) / 1e9
            tokens = min(capacity, tokens + elapsed * refillRate)
            lastRefill = now
        }
    }

    private val buckets = ConcurrentHashMap<String, Bucket>()

    private fun bucket(host: String) = buckets.getOrPut(host) {
        Bucket(defaultCapacity, defaultRefillRate)
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host
        val bucket = bucket(host)

        val retryableMethod = request.method in
            setOf("GET", "HEAD", "OPTIONS", "PUT", "DELETE")

        val hasIdempotencyKey =
            request.header("Idempotency-Key") != null

        val canRetry = retryableMethod || hasIdempotencyKey

        var attempt = 0

        while (true) {
            bucket.acquire()

            val response = chain.proceed(request)

            updateFromHeaders(bucket, response)

            val shouldRetry =
                canRetry &&
                attempt < maxRetries &&
                response.code in setOf(429, 503)

            if (!shouldRetry) {
                return response
            }

            val retryAfter = parseRetryAfter(
                response.header("Retry-After")
            )

            val backoff = exponentialBackoff(attempt)

            val waitMs = max(retryAfter ?: 0L, backoff)
                .coerceAtMost(maxServerWaitMs)

            response.close()

            bucket.block(waitMs)
            attempt++
        }
    }

    private fun exponentialBackoff(attempt: Int): Long {
        val exponential = (
            baseBackoffMs * 2.0.pow(attempt.toDouble())
        ).toLong().coerceAtMost(maxBackoffMs)

        return Random.nextLong(exponential + 1)
    }

    private fun parseRetryAfter(value: String?): Long? {
        if (value == null) return null

        value.toLongOrNull()?.let {
            return (it * 1000).coerceAtLeast(0)
        }

        return runCatching {
            val target = ZonedDateTime.parse(
                value,
                DateTimeFormatter.RFC_1123_DATE_TIME
            ).toInstant().toEpochMilli()

            (target - System.currentTimeMillis()).coerceAtLeast(0)
        }.getOrNull()
    }

    private fun updateFromHeaders(
        bucket: Bucket,
        response: Response
    ) {
        val headers = response.headers

        val limit = (
            headers["RateLimit-Limit"]
                ?: headers["X-RateLimit-Limit"]
            )?.substringBefore(";")?.toDoubleOrNull()

        val remaining = (
            headers["RateLimit-Remaining"]
                ?: headers["X-RateLimit-Remaining"]
            )?.toDoubleOrNull()

        val reset = (
            headers["RateLimit-Reset"]
                ?: headers["X-RateLimit-Reset"]
            )

        val resetMs = reset?.toDoubleOrNull()?.let {
            val seconds = if (
                headers["X-RateLimit-Reset"] != null &&
                it > 1_000_000_000
            ) {
                (it - System.currentTimeMillis() / 1000.0)
                    .coerceAtLeast(0.0)
            } else {
                it.coerceAtLeast(0.0)
            }

            (seconds * 1000).toLong()
        }

        bucket.update(limit, remaining, resetMs)
    }
}
