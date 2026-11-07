package com.example.calculators.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.calculators.image.ImageMatchStatus
import com.example.calculators.model.Product

/**
 * Phase 10: Room entity representing a single mechanical part / calibration item within a saved bill.
 * Preserves optional quantities (`quantity = null` when omitted), Tamil/English names in original script,
 * and associated image references (`imageUri` and `imageSourceName`).
 */
@Entity(
    tableName = "bill_items",
    foreignKeys = [
        ForeignKey(
            entity = BillEntity::class,
            parentColumns = ["billId"],
            childColumns = ["billId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["billId"])]
)
data class BillItemEntity(
    @PrimaryKey
    val itemId: String,
    val billId: String,
    val itemOrder: Int,
    val productName: String,
    val quantity: Double?,
    val amount: Double,
    val lineAmountPlain: String,
    val lineAmountFormatted: String,
    val calibrationNotes: String = "",
    val imageUri: String? = null,
    val imageSourceName: String = "",
    val isUnitPrice: Boolean = false
) {
    val formattedQuantity: String
        get() = toDomainProduct().formattedQuantity

    fun toDomainProduct(): Product {
        return Product(
            id = itemId,
            productName = productName,
            quantity = quantity,
            amount = amount,
            calibrationNotes = calibrationNotes,
            imageUri = imageUri,
            imageSourceName = imageSourceName,
            imageMatchStatus = if (imageUri != null) {
                ImageMatchStatus.EXACT_FILENAME_MATCH
            } else {
                ImageMatchStatus.NO_MATCH
            },
            imageNeedsConfirmation = false,
            isUnitPrice = isUnitPrice
        )
    }
}
