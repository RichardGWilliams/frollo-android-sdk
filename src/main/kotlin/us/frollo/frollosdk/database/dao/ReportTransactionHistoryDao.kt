/*
 * Copyright 2019 Frollo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package us.frollo.frollosdk.database.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery
import us.frollo.frollosdk.model.coredata.reports.ReportGrouping
import us.frollo.frollosdk.model.coredata.reports.ReportPeriod
import us.frollo.frollosdk.model.coredata.reports.ReportTransactionHistory
import us.frollo.frollosdk.model.coredata.reports.ReportTransactionHistoryRelation
import us.frollo.frollosdk.model.coredata.shared.BudgetCategory

@Dao
internal interface ReportTransactionHistoryDao {

    @Transaction
    @RawQuery(observedEntities = [ReportTransactionHistory::class])
    fun loadByQuery(queryStr: SupportSQLiteQuery): LiveData<List<ReportTransactionHistoryRelation>>

    @RawQuery
    fun find(queryStr: SupportSQLiteQuery): MutableList<ReportTransactionHistory>

    @Query("SELECT report_id FROM report_transaction_history WHERE (date BETWEEN :fromDate AND :toDate) AND report_grouping = :grouping AND period = :period AND filtered_budget_category IS :budgetCategory AND date NOT IN (:dates)")
    fun findStaleIds(fromDate: String, toDate: String, grouping: ReportGrouping, period: ReportPeriod, budgetCategory: BudgetCategory?, dates: Array<String>): LongArray

    @RawQuery
    fun findStaleIds(queryStr: SupportSQLiteQuery): LongArray

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(vararg models: ReportTransactionHistory): LongArray

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(model: ReportTransactionHistory): Long

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun update(vararg models: ReportTransactionHistory): Int

    @Query("DELETE FROM report_transaction_history WHERE report_id IN (:reportIds)")
    fun deleteMany(reportIds: LongArray)

    @Query("DELETE FROM report_transaction_history")
    fun clear()
}