package com.example.calculators.data

import androidx.room.withTransaction
import com.example.calculators.model.BillingCalculator
import com.example.calculators.model.BillingSession
import com.example.calculators.model.Product
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * Result of finalizing and automatically saving a bill to the local Room database.
 */
sealed interface FinalizeBillResult {
    data class Saved(val billWithItems: BillWithItems) : FinalizeBillResult
    data class DuplicatePrevented(val existingBill: BillEntity) : FinalizeBillResult
    data class InvalidBill(val reason: String) : FinalizeBillResult
}

/**
 * Phase 10: Repository coordinating asynchronous Room persistence for finalized bills,
 * duplicate save prevention, and bill history search/filtering.
 */
class BillRepository(
    private val database: BillingDatabase
) {
    private val billDao: BillDao = database.billDao()
    private val billItemDao: BillItemDao = database.billItemDao()

    val finalizedBillsFlow: Flow<List<BillWithItems>> =
        billDao.observeBillsByStatus(BillEntity.STATUS_FINALIZED)

    suspend fun getFinalizedBillsSnapshot(): List<BillWithItems> {
        return billDao.getBillsSnapshot(BillEntity.STATUS_FINALIZED)
    }

    suspend fun getBillWithItemsById(billId: String): BillWithItems? {
        return billDao.getBillWithItemsById(billId)
    }

    /**
     * Validates, finalizes, and automatically saves a completed calibration bill and all its items
     * in a single Room database transaction.
     *
     * Rules:
     * - Does not silently save empty or invalid bills.
     * - Prevents accidental duplicate saving when the user taps Finalize multiple times.
     * - Preserves optional quantities (`null`), Tamil & English names, associated image references,
     *   individual amounts, Grand Total, and original voice transcript.
     */
    suspend fun finalizeAndSaveBill(
        session: BillingSession,
        originalTranscript: String = "",
        timestampMillis: Long = System.currentTimeMillis()
    ): FinalizeBillResult {
        val products = session.products
        if (products.isEmpty()) {
            return FinalizeBillResult.InvalidBill("Cannot finalize an empty bill. Add at least one mechanical part or service entry.")
        }

        val invalidEntry = products.firstOrNull { product ->
            !Product.isValidProductName(product.productName) ||
                !Product.isValidQuantity(product.quantity) ||
                !Product.isValidAmount(product.amount)
        }
        if (invalidEntry != null) {
            return FinalizeBillResult.InvalidBill(
                "Cannot finalize bill: entry '${invalidEntry.productName.ifBlank { "Unnamed" }}' has invalid details."
            )
        }

        val signature = buildContentSignature(session.sessionId, products, originalTranscript)
        val existingDuplicate = billDao.findBillBySignature(signature, BillEntity.STATUS_FINALIZED)
        if (existingDuplicate != null) {
            return FinalizeBillResult.DuplicatePrevented(existingDuplicate)
        }

        val grandTotal = BillingCalculator.calculateGrandTotal(products)
            .setScale(2, RoundingMode.HALF_UP)
        val grandTotalFormatted = BillingCalculator.formatCurrency(grandTotal)

        val currentCount = billDao.getBillCount(BillEntity.STATUS_FINALIZED)
        val generatedBillId = String.format(Locale.US, "BILL-%04d", currentCount + 1)
        val billId = if (billDao.getBillWithItemsById(generatedBillId) == null) {
            generatedBillId
        } else {
            "BILL-${UUID.randomUUID().toString().take(8).uppercase(Locale.US)}"
        }

        val billWithItems = buildBillWithItemsSnapshot(
            billId = billId,
            session = session,
            originalTranscript = originalTranscript,
            timestampMillis = timestampMillis,
            signature = signature
        )

        billDao.insertBill(billWithItems.bill)
        billItemDao.insertBillItems(billWithItems.items)

        return FinalizeBillResult.Saved(billWithItems)
    }

    suspend fun deleteBill(billId: String) {
        billItemDao.deleteItemsForBill(billId)
        billDao.deleteBillById(billId)
    }

    companion object {
        fun buildBillWithItemsSnapshot(
            billId: String,
            session: BillingSession,
            originalTranscript: String = "",
            timestampMillis: Long = System.currentTimeMillis(),
            signature: String = buildContentSignature(session.sessionId, session.products, originalTranscript)
        ): BillWithItems {
            val products = session.products
            val grandTotal = BillingCalculator.calculateGrandTotal(products)
                .setScale(2, RoundingMode.HALF_UP)
            val grandTotalFormatted = BillingCalculator.formatCurrency(grandTotal)

            val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            val dateKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val dateObj = Date(timestampMillis)

            val billEntity = BillEntity(
                billId = billId,
                sessionId = session.sessionId,
                createdAtMillis = timestampMillis,
                formattedDateTime = dateTimeFormat.format(dateObj),
                dateKey = dateKeyFormat.format(dateObj),
                grandTotalPlain = grandTotal.toPlainString(),
                grandTotalFormatted = grandTotalFormatted,
                originalTranscript = originalTranscript.trim(),
                status = BillEntity.STATUS_FINALIZED,
                contentSignature = signature
            )

            val itemEntities = products.mapIndexed { index, product ->
                val lineTotal = product.lineAmount.setScale(2, RoundingMode.HALF_UP)
                BillItemEntity(
                    itemId = "${billId}_ITEM_${index + 1}",
                    billId = billId,
                    itemOrder = index,
                    productName = product.productName,
                    quantity = product.quantity,
                    amount = product.amount,
                    lineAmountPlain = lineTotal.toPlainString(),
                    lineAmountFormatted = BillingCalculator.formatCurrency(lineTotal),
                    calibrationNotes = product.calibrationNotes,
                    imageUri = product.imageUri,
                    imageSourceName = product.imageSourceName,
                    isUnitPrice = product.isUnitPrice
                )
            }

            return BillWithItems(
                bill = billEntity,
                items = itemEntities
            )
        }

        fun buildContentSignature(
            sessionId: String,
            products: List<Product>,
            originalTranscript: String
        ): String {
            val itemsPart = products.joinToString("|") { p ->
                val qtyPart = p.quantity?.toString() ?: "NULL"
                val amtPart = p.lineAmount.setScale(2, RoundingMode.HALF_UP).toPlainString()
                "${p.productName.trim()}#$qtyPart#$amtPart#${p.imageUri.orEmpty()}#${p.calibrationNotes.trim()}"
            }
            return "$sessionId::${originalTranscript.trim()}::$itemsPart"
        }

        /**
         * Filters a list of saved bills (already ordered most-recent-first) by:
         * - `searchQuery`: matches Bill ID, Session ID, or any mechanical part / item name (Tamil or English).
         * - `dateFilter`: matches `dateKey` (`YYYY-MM-DD`) or substring of `formattedDateTime`.
         */
        fun filterBills(
            bills: List<BillWithItems>,
            searchQuery: String,
            dateFilter: String
        ): List<BillWithItems> {
            val trimmedSearch = searchQuery.trim()
            val trimmedDate = dateFilter.trim()

            return bills.filter { billWithItems ->
                val matchesSearch = if (trimmedSearch.isEmpty()) {
                    true
                } else {
                    billWithItems.bill.billId.contains(trimmedSearch, ignoreCase = true) ||
                        billWithItems.bill.sessionId.contains(trimmedSearch, ignoreCase = true) ||
                        billWithItems.items.any { item ->
                            item.productName.contains(trimmedSearch, ignoreCase = true) ||
                                item.calibrationNotes.contains(trimmedSearch, ignoreCase = true)
                        }
                }

                val matchesDate = if (trimmedDate.isEmpty()) {
                    true
                } else {
                    billWithItems.bill.dateKey.contains(trimmedDate, ignoreCase = true) ||
                        billWithItems.bill.formattedDateTime.contains(trimmedDate, ignoreCase = true)
                }

                matchesSearch && matchesDate
            }
        }
    }
}
