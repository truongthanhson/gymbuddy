package com.gymbuddy.app.domain

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val utcMillis = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

/** Epoch milliseconds → the UTC text the server stores ("2026-10-26T05:55:16.123Z"). */
fun formatTimestamp(epochMillis: Long): String = utcMillis.format(Instant.ofEpochMilli(epochMillis))

/** Postgres timestamptz text (any fraction length, any offset) → epoch milliseconds, dropping sub-millisecond digits. */
fun parseTimestamp(text: String): Long = OffsetDateTime.parse(text).toInstant().toEpochMilli()
