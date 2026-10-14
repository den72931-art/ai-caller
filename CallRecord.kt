package com.aicaller.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class CallOutcome {
    ANSWERED_BY_AI,
    SCREENED_BLOCKED,
    SCREENED_SILENCED,
    MISSED,
    HANDED_TO_USER,
    FORWARDED
}

@Entity(tableName = "call_records")
data class CallRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phoneNumber: String,
    val displayName: String?,
    val startTimeMillis: Long,
    val endTimeMillis: Long?,
    val outcome: CallOutcome,
    val transcript: String?,
    val aiSummary: String?
)
