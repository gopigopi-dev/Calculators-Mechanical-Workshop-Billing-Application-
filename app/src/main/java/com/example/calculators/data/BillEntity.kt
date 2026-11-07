package com.example.calculators.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * Phase 10: Room entity representing a saved mechanical calibration bill.
 * Keeps Draft and Finalized statuses separate via `status`.
 */
@Entity(tableName = "bills")
data class BillEntity(
    @PrimaryKey
    val billId: String,
    val sessionId: String,
    val createdAtMillis: Long,
    val formattedDateTime: String,
    val dateKey: String,
    val grandTotalPlain: String,
    val grandTotalFormatted: String,
    val originalTranscript: String,
    val status: String = STATUS_FINALIZED,
    val contentSignature: String
) {
    companion object {
        const val STATUS_FINALIZED = "FINALIZED"
        const val STATUS_DRAFT = "DRAFT"
    }
}

/**
 * Relation combining a [BillEntity] with all of its [BillItemEntity] line entries.
 */
data class BillWithItems(
    @Embedded
    val bill: BillEntity,
    @Relation(
        parentColumn = "billId",
        entityColumn = "billId"
    )
    val items: List<BillItemEntity>
) {
    val sortedItems: List<BillItemEntity>
        get() = items.sortedBy { it.itemOrder }
}
