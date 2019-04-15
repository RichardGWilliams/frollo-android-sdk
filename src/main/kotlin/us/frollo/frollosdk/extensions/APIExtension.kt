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

package us.frollo.frollosdk.extensions

import retrofit2.Call
import us.frollo.frollosdk.model.api.aggregation.merchants.MerchantResponse
import us.frollo.frollosdk.network.api.AggregationAPI
import us.frollo.frollosdk.model.api.aggregation.transactions.TransactionResponse
import us.frollo.frollosdk.model.api.aggregation.transactions.TransactionsSummaryResponse
import us.frollo.frollosdk.model.api.bills.BillPaymentResponse
import us.frollo.frollosdk.model.api.reports.AccountBalanceReportResponse
import us.frollo.frollosdk.model.api.reports.TransactionCurrentReportResponse
import us.frollo.frollosdk.model.api.reports.TransactionHistoryReportResponse
import us.frollo.frollosdk.model.coredata.aggregation.accounts.AccountType
import us.frollo.frollosdk.model.coredata.bills.BillPayment
import us.frollo.frollosdk.model.coredata.reports.ReportGrouping
import us.frollo.frollosdk.model.coredata.reports.ReportPeriod
import us.frollo.frollosdk.model.coredata.shared.BudgetCategory
import us.frollo.frollosdk.model.coredata.surveys.Survey
import us.frollo.frollosdk.network.api.BillsAPI
import us.frollo.frollosdk.network.api.ReportsAPI
import us.frollo.frollosdk.network.api.SurveysAPI

internal fun AggregationAPI.fetchTransactionsByQuery(
        fromDate: String, // yyyy-MM-dd
        toDate: String, // yyyy-MM-dd
        accountIds: LongArray? = null,
        accountIncluded: Boolean? = null, // TODO: not using this currently as this requires refactoring handleTransactionsResponse
        transactionIncluded: Boolean? = null,
        skip: Int? = null,
        count: Int? = null
) : Call<List<TransactionResponse>> {

    var queryMap = mapOf("from_date" to fromDate, "to_date" to toDate)
    skip?.let { queryMap = queryMap.plus(Pair("skip", it.toString())) }
    count?.let { queryMap = queryMap.plus(Pair("count", it.toString())) }
    accountIncluded?.let { queryMap = queryMap.plus(Pair("account_included", it.toString())) }
    transactionIncluded?.let { queryMap = queryMap.plus(Pair("transaction_included", it.toString())) }
    accountIds?.let { queryMap = queryMap.plus(Pair("account_ids", it.joinToString(","))) }

    return fetchTransactions(queryMap)
}

internal fun AggregationAPI.fetchTransactionsByIDs(transactionIds: LongArray) : Call<List<TransactionResponse>> =
        fetchTransactions(mapOf("transaction_ids" to transactionIds.joinToString(",")))

internal fun AggregationAPI.fetchTransactionsSummaryByQuery(
        fromDate: String, // yyyy-MM-dd
        toDate: String, // yyyy-MM-dd
        accountIds: LongArray? = null,
        accountIncluded: Boolean? = null,
        transactionIncluded: Boolean? = null
) : Call<TransactionsSummaryResponse> {

    var queryMap = mapOf("from_date" to fromDate, "to_date" to toDate)
    accountIncluded?.let { queryMap = queryMap.plus(Pair("account_included", it.toString())) }
    transactionIncluded?.let { queryMap = queryMap.plus(Pair("transaction_included", it.toString())) }
    accountIds?.let { queryMap = queryMap.plus(Pair("account_ids", it.joinToString(","))) }

    return fetchTransactionsSummary(queryMap)
}

internal fun AggregationAPI.fetchTransactionsSummaryByIDs(transactionIds: LongArray) : Call<TransactionsSummaryResponse> =
        fetchTransactionsSummary(mapOf("transaction_ids" to transactionIds.joinToString(",")))

internal fun AggregationAPI.fetchMerchantsByIDs(merchantIds: LongArray) : Call<List<MerchantResponse>> =
        fetchMerchantsByIds(mapOf("merchant_ids" to merchantIds.joinToString(",")))

internal fun ReportsAPI.fetchAccountBalanceReports(period: ReportPeriod, fromDate: String, toDate: String,
                                                   accountId: Long? = null, accountType: AccountType? = null) : Call<AccountBalanceReportResponse> {
    var queryMap = mapOf("period" to period.toString(), "from_date" to fromDate, "to_date" to toDate)
    accountType?.let { queryMap = queryMap.plus(Pair("container", it.toString())) }
    accountId?.let { queryMap = queryMap.plus(Pair("account_id", it.toString())) }
    return fetchAccountBalanceReports(queryMap)
}

internal fun ReportsAPI.fetchTransactionCurrentReports(grouping: ReportGrouping, budgetCategory: BudgetCategory? = null) : Call<TransactionCurrentReportResponse> {
    var queryMap = mapOf("grouping" to grouping.toString())
    budgetCategory?.let { queryMap = queryMap.plus(Pair("budget_category", it.toString())) }
    return fetchTransactionCurrentReports(queryMap)
}

internal fun ReportsAPI.fetchTransactionHistoryReports(grouping: ReportGrouping, period: ReportPeriod,
                                                       fromDate: String, toDate: String,
                                                       budgetCategory: BudgetCategory? = null) : Call<TransactionHistoryReportResponse> {
    var queryMap = mapOf("grouping" to grouping.toString(), "period" to period.toString(), "from_date" to fromDate, "to_date" to toDate)
    budgetCategory?.let { queryMap = queryMap.plus(Pair("budget_category", it.toString())) }
    return fetchTransactionHistoryReports(queryMap)
}

internal fun BillsAPI.fetchBillPayments(
        fromDate: String, // yyyy-MM-dd
        toDate: String, // yyyy-MM-dd
        skip: Int? = null,
        count: Int? = null) : Call<List<BillPaymentResponse>> {

    var queryMap = mapOf("from_date" to fromDate, "to_date" to toDate)
    skip?.let { queryMap = queryMap.plus(Pair("skip", it.toString())) }
    count?.let { queryMap = queryMap.plus(Pair("count", it.toString())) }

    return fetchBillPayments(queryMap)
}

internal fun SurveysAPI.fetchSurvey(surveyKey: String, latest: Boolean? = null) : Call<Survey> {
    var queryMap = mapOf<String, String>()
    latest?.let { queryMap = mapOf(Pair("latest", latest.toString())) }
    return fetchSurvey(surveyKey, queryMap)
}