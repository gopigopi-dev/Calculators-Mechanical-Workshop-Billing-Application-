package com.example.calculators.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Phase 10: Room DAO for finalized and draft mechanical calibration bills.
 */
@Dao
interface BillDao {

    @Transaction
    @Query("SELECT * FROM bills WHERE status = :status ORDER BY createdAtMillis DESC")
    fun observeBillsByStatus(status: String = BillEntity.STATUS_FINALIZED): Flow<List<BillWithItems>>

    @Transaction
    @Query("SELECT * FROM bills WHERE status = :status ORDER BY createdAtMillis DESC")
    suspend fun getBillsSnapshot(status: String = BillEntity.STATUS_FINALIZED): List<BillWithItems>

    @Transaction
    @Query("SELECT * FROM bills WHERE billId = :billId LIMIT 1")
    suspend fun getBillWithItemsById(billId: String): BillWithItems?

    @Query("SELECT * FROM bills WHERE contentSignature = :signature AND status = :status LIMIT 1")
    suspend fun findBillBySignature(
        signature: String,
        status: String = BillEntity.STATUS_FINALIZED
    ): BillEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBill(bill: BillEntity)

    @Query("DELETE FROM bills WHERE billId = :billId")
    suspend fun deleteBillById(billId: String)

    @Query("SELECT COUNT(*) FROM bills WHERE status = :status")
    suspend fun getBillCount(status: String = BillEntity.STATUS_FINALIZED): Int
}
