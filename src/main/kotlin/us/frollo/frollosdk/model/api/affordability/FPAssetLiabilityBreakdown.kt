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

package us.frollo.frollosdk.model.api.affordability
import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

/** Data representation of the breakdown of Assets or Liabilities of the Financial Passport */
data class FPAssetLiabilityBreakdown(
    /**  The name of name of liabilities breakdown; optional */
    @SerializedName("name") val name: String?,
    /**  The closing balance of liabilities breakdown; optional */
    @SerializedName("closing_balance") val closingBalance: BigDecimal?,
    /**  The percentage of liabilities breakdown; optional */
    @SerializedName("percentage") val percentage: Double?,
    /**  A list of accounts in assets or liabilities breakdown; optional */
    @SerializedName("accounts") val accounts: List<FPAssetsLiabilitiesBreakdownAccounts>?
)
