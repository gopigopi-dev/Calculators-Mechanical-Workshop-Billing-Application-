package com.example.calculators.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Phase 10: Room DAO for individual mechanical part items belonging to a saved bill.
 */
@Dao
interface BillItemDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBillItems(items: List<BillItemEntity>)

    @Query("SELECT * FROM bill_items WHERE billId = :billId ORDER BY itemOrder ASC")
    suspend fun getItemsForBill(billId: String): List<BillItemEntity>

    @Query("DELETE FROM bill_items WHERE billId = :billId")
    suspend fun deleteItemsForBill(billId: String)
}
