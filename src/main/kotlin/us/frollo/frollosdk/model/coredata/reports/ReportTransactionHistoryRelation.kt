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

package us.frollo.frollosdk.model.coredata.reports

import androidx.room.*
import us.frollo.frollosdk.model.IAdapterModel

/** History Transactions Report with associated data */
data class ReportTransactionHistoryRelation(

        /** Overall report */
        @Embedded
        var report: ReportTransactionHistory? = null,

        /** Associated group reports */
        @Relation(parentColumn = "report_id", entityColumn = "report_id", entity = ReportGroupTransactionHistory::class)
        var groups: List<ReportGroupTransactionHistoryRelation>? = null

): IAdapterModel