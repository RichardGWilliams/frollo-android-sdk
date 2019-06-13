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

package us.frollo.frollosdk

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.platform.app.InstrumentationRegistry
import com.jraska.livedata.test
import okhttp3.HttpUrl
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals

import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.threeten.bp.LocalDateTime
import org.threeten.bp.ZoneOffset
import us.frollo.frollosdk.base.Result
import us.frollo.frollosdk.core.testSDKConfig
import us.frollo.frollosdk.database.SDKDatabase
import us.frollo.frollosdk.error.FrolloSDKError
import us.frollo.frollosdk.mapping.toUser
import us.frollo.frollosdk.model.testUserResponseData
import us.frollo.frollosdk.preferences.Preferences
import us.frollo.frollosdk.testutils.wait

class FrolloSDKAndroidUnitTest {

    @get:Rule val testRule = InstantTaskExecutorRule()

    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private lateinit var preferences: Preferences
    private lateinit var database: SDKDatabase
    private lateinit var mockServer: MockWebServer
    private lateinit var baseUrl: HttpUrl

    @Before
    fun resetSingletonByReflection() {
        val setup = FrolloSDK::class.java.getDeclaredField("_setup")
        setup.isAccessible = true
        setup.setBoolean(null, false)
    }

    private fun initSetup() {
        mockServer = MockWebServer()
        mockServer.start()
        baseUrl = mockServer.url("/")

        preferences = Preferences(app)
        database = SDKDatabase.getInstance(app)

        preferences.loggedIn = true
        preferences.encryptedAccessToken = "EncryptedAccessToken"
        preferences.encryptedRefreshToken = "EncryptedRefreshToken"
        preferences.accessTokenExpiry = LocalDateTime.now(ZoneOffset.UTC).toEpochSecond(ZoneOffset.UTC) + 900
    }

    private fun tearDown() {
        mockServer.shutdown()
        preferences.resetAll()
        database.clearAllTables()
    }

    @Test
    fun testSDKInitFailIfServerURLNotSet() {
        assertFalse(FrolloSDK.isSetup)

        try {
            FrolloSDK.setup(app, testSDKConfig(serverUrl = "")) { }
        } catch (e: FrolloSDKError) {
            assertEquals("Server URL cannot be empty", e.localizedMessage)
        }
    }

    @Test
    fun testSDKSetupSuccess() {
        assertFalse(FrolloSDK.isSetup)

        FrolloSDK.setup(app, testSDKConfig()) { result ->
            assertEquals(Result.Status.SUCCESS, result.status)
            assertNull(result.error)

            assertTrue(FrolloSDK.isSetup)
            assertNotNull(FrolloSDK.authentication)
            assertNotNull(FrolloSDK.defaultAuthentication)
            assertNotNull(FrolloSDK.aggregation)
            assertNotNull(FrolloSDK.messages)
            assertNotNull(FrolloSDK.events)
            assertNotNull(FrolloSDK.notifications)
            assertNotNull(FrolloSDK.surveys)
            assertNotNull(FrolloSDK.reports)
            assertNotNull(FrolloSDK.bills)
            assertNotNull(FrolloSDK.userManagement)
        }
    }

    @Test
    fun testSDKAuthenticationThrowsErrorBeforeSetup() {
        assertFalse(FrolloSDK.isSetup)

        try {
            FrolloSDK.authentication
        } catch (e: IllegalAccessException) {
            assertEquals("SDK not setup", e.localizedMessage)
        }
    }

    @Test
    fun testSDKDefaultAuthenticationThrowsErrorBeforeSetup() {
        assertFalse(FrolloSDK.isSetup)

        try {
            FrolloSDK.defaultAuthentication
        } catch (e: IllegalAccessException) {
            assertEquals("SDK not setup", e.localizedMessage)
        }
    }

    @Test
    fun testSDKAggregationThrowsErrorBeforeSetup() {
        assertFalse(FrolloSDK.isSetup)

        try {
            FrolloSDK.aggregation
        } catch (e: IllegalAccessException) {
            assertEquals("SDK not setup", e.localizedMessage)
        }
    }

    @Test
    fun testSDKMessagesThrowsErrorBeforeSetup() {
        assertFalse(FrolloSDK.isSetup)

        try {
            FrolloSDK.messages
        } catch (e: IllegalAccessException) {
            assertEquals("SDK not setup", e.localizedMessage)
        }
    }

    @Test
    fun testSDKEventsThrowsErrorBeforeSetup() {
        assertFalse(FrolloSDK.isSetup)

        try {
            FrolloSDK.events
        } catch (e: IllegalAccessException) {
            assertEquals("SDK not setup", e.localizedMessage)
        }
    }

    @Test
    fun testSDKNotificationsThrowsErrorBeforeSetup() {
        assertFalse(FrolloSDK.isSetup)

        try {
            FrolloSDK.notifications
        } catch (e: IllegalAccessException) {
            assertEquals("SDK not setup", e.localizedMessage)
        }
    }

    @Test
    fun testSDKSurveysThrowsErrorBeforeSetup() {
        assertFalse(FrolloSDK.isSetup)

        try {
            FrolloSDK.surveys
        } catch (e: IllegalAccessException) {
            assertEquals("SDK not setup", e.localizedMessage)
        }
    }

    @Test
    fun testSDKReportsThrowsErrorBeforeSetup() {
        assertFalse(FrolloSDK.isSetup)

        try {
            FrolloSDK.reports
        } catch (e: IllegalAccessException) {
            assertEquals("SDK not setup", e.localizedMessage)
        }
    }

    @Test
    fun testSDKBillsThrowsErrorBeforeSetup() {
        assertFalse(FrolloSDK.isSetup)

        try {
            FrolloSDK.bills
        } catch (e: IllegalAccessException) {
            assertEquals("SDK not setup", e.localizedMessage)
        }
    }

    @Test
    fun testSDKUserManagementThrowsErrorBeforeSetup() {
        assertFalse(FrolloSDK.isSetup)

        try {
            FrolloSDK.userManagement
        } catch (e: IllegalAccessException) {
            assertEquals("SDK not setup", e.localizedMessage)
        }
    }

    @Test
    fun testPauseScheduledRefresh() {
        FrolloSDK.setup(app, testSDKConfig()) { result ->
            assertEquals(Result.Status.SUCCESS, result.status)
            assertNull(result.error)

            FrolloSDK.onAppBackgrounded()
            assertNull(FrolloSDK.refreshTimer)
        }
    }

    @Test
    fun testResumeScheduledRefresh() {
        FrolloSDK.setup(app, testSDKConfig()) { result ->
            assertEquals(Result.Status.SUCCESS, result.status)
            assertNull(result.error)

            FrolloSDK.onAppForegrounded()
            assertNotNull(FrolloSDK.refreshTimer)
        }
    }

    @Test
    fun testRefreshData() {
        /*initSetup()

        mockServer.setDispatcher(object: Dispatcher() {
            override fun dispatch(request: RecordedRequest?): MockResponse {
                if (request?.trimmedPath == UserAPI.URL_LOGIN) {
                    return MockResponse()
                            .setResponseCode(200)
                            .setBody(readStringFromJson(app, R.raw.user_details_complete))
                } else if (request?.trimmedPath == UserAPI.URL_USER_DETAILS) {
                    return MockResponse()
                            .setResponseCode(200)
                            .setBody(readStringFromJson(app, R.raw.user_details_complete))
                } else if (request?.trimmedPath == MessagesAPI.URL_UNREAD) {
                    return MockResponse()
                            .setResponseCode(200)
                            .setBody(readStringFromJson(app, R.raw.messages_unread))
                }
                return MockResponse().setResponseCode(404)
            }
        })

        FrolloSDK.setup(app, testSDKConfig()) {

            FrolloSDK.authentication.loginUser(AuthType.EMAIL, "user@frollo.us", "password") { error ->
                assertNull(error)

                FrolloSDK.refreshData() //TODO: This never calls the enqueue callback to load data into database

                wait(6)

                val testObserver = FrolloSDK.authentication.fetchUser().test()
                testObserver.awaitValue()
                assertNotNull(testObserver.value().data)

                val testObserver2 = FrolloSDK.messages.fetchMessages(read = false).test()
                testObserver2.awaitValue()
                val models = testObserver2.value().data
                assertNotNull(models)
                assertEquals(7, models?.size)
            }
        }

        wait(8)

        tearDown()*/
    }

    @Test
    fun testForcedLogout() {
        initSetup()

        database.users().insert(testUserResponseData().toUser())

        val testObserver = database.users().load().test()
        testObserver.awaitValue()
        assertNotNull(testObserver.value())

        FrolloSDK.setup(app, testSDKConfig()) { result ->
            assertEquals(Result.Status.SUCCESS, result.status)
            assertNull(result.error)

            FrolloSDK.forcedLogout()

            wait(3)

            assertFalse(preferences.loggedIn)
            assertNull(preferences.encryptedAccessToken)
            assertNull(preferences.encryptedRefreshToken)
            assertEquals(-1, preferences.accessTokenExpiry)

            val testObserver2 = database.users().load().test()
            testObserver2.awaitValue()
            assertNull(testObserver2.value())
        }

        tearDown()
    }

    @Test
    fun testSDKResetSuccess() {
        initSetup()

        database.users().insert(testUserResponseData().toUser())

        val testObserver = database.users().load().test()
        testObserver.awaitValue()
        assertNotNull(testObserver.value())

        FrolloSDK.setup(app, testSDKConfig()) { result ->
            assertEquals(Result.Status.SUCCESS, result.status)
            assertNull(result.error)

            FrolloSDK.reset { res ->
                assertEquals(Result.Status.SUCCESS, res.status)
                assertNull(res.error)

                assertFalse(preferences.loggedIn)
                assertNull(preferences.encryptedAccessToken)
                assertNull(preferences.encryptedRefreshToken)
                assertEquals(-1, preferences.accessTokenExpiry)

                val testObserver2 = database.users().load().test()
                testObserver2.awaitValue()
                assertNull(testObserver2.value())
            }
        }

        wait(3)

        tearDown()
    }
}