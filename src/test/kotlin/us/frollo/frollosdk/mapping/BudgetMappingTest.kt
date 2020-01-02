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

package us.frollo.frollosdk.mapping

import org.junit.Assert
import org.junit.Test
import us.frollo.frollosdk.model.testBudgetPeriodResponseData
import us.frollo.frollosdk.model.testBudgetResponseData

class BudgetMappingTest {

    @Test
    fun testBudgetResponseToBudget() {
        val response = testBudgetResponseData(budgetId = 12345)
        val model = response.toBudget()
        Assert.assertEquals(12345L, model.budgetId)
    }

    @Test
    fun testBudgetPeriodResponseToBudgetPeriod() {
        val response = testBudgetPeriodResponseData(budgetPeriodId = 12345)
        val model = response.toBudgetPeriod()
        Assert.assertEquals(12345L, model.budgetPeriodId)
    }
}