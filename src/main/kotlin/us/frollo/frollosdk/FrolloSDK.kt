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
import android.os.Handler
import androidx.core.os.bundleOf
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.jakewharton.threetenabp.AndroidThreeTen
import org.threeten.bp.Duration
import org.threeten.bp.LocalDate
import org.threeten.bp.LocalDateTime
import org.threeten.bp.temporal.TemporalAdjusters
import us.frollo.frollosdk.aggregation.Aggregation
import us.frollo.frollosdk.authentication.Authentication
import us.frollo.frollosdk.authentication.AuthenticationStatus
import us.frollo.frollosdk.authentication.OAuth
import us.frollo.frollosdk.base.Result
import us.frollo.frollosdk.bills.Bills
import us.frollo.frollosdk.core.ACTION.ACTION_AUTHENTICATION_CHANGED
import us.frollo.frollosdk.core.ARGUMENT.ARG_AUTHENTICATION_STATUS
import us.frollo.frollosdk.core.DeviceInfo
import us.frollo.frollosdk.core.OnFrolloSDKCompletionListener
import us.frollo.frollosdk.core.FrolloSDKConfiguration
import us.frollo.frollosdk.database.SDKDatabase
import us.frollo.frollosdk.network.NetworkService
import us.frollo.frollosdk.error.FrolloSDKError
import us.frollo.frollosdk.events.Events
import us.frollo.frollosdk.extensions.notify
import us.frollo.frollosdk.extensions.toString
import us.frollo.frollosdk.keystore.Keystore
import us.frollo.frollosdk.logging.Log
import us.frollo.frollosdk.messages.Messages
import us.frollo.frollosdk.model.coredata.aggregation.transactions.Transaction
import us.frollo.frollosdk.notifications.Notifications
import us.frollo.frollosdk.preferences.Preferences
import us.frollo.frollosdk.reports.Reports
import us.frollo.frollosdk.surveys.Surveys
import us.frollo.frollosdk.version.Version
import java.lang.Exception
import java.util.Timer
import java.util.TimerTask

/**
 * Frollo SDK manager and main instantiation. Responsible for managing the lifecycle and coordination of the SDK
 */
object FrolloSDK {

    private const val TAG = "FrolloSDK"
    private const val CACHE_EXPIRY = 120000L // 2 minutes

    /**
     * Indicates if the SDK has completed setup or not
     */
    val isSetup: Boolean
        get() = _setup

    /**
     * Authentication - All authentication and user related data see [Authentication] for details
     */
    val authentication: Authentication
        get() =_authentication ?: throw IllegalAccessException("SDK not setup")

    /**
     * Aggregation - All account and transaction related data see [Aggregation] for details
     */
    val aggregation: Aggregation
        get() =_aggregation ?: throw IllegalAccessException("SDK not setup")

    /**
     * Messages - All messages management. See [Messages] for details
     */
    val messages: Messages
        get() =_messages ?: throw IllegalAccessException("SDK not setup")

    /**
     * Events - Triggering and handling of events. See [Events] for details
     */
    val events: Events
        get() =_events ?: throw IllegalAccessException("SDK not setup")

    /**
     * Notifications - Registering and handling of push notifications. See [Notifications] for details
     */
    val notifications: Notifications
        get() =_notifications ?: throw IllegalAccessException("SDK not setup")

    /**
     * Surveys - Handling surveys. See [Surveys] for details
     */
    val surveys: Surveys
        get() =_surveys ?: throw IllegalAccessException("SDK not setup")

    /**
     * Reports - Aggregation data reports. See [Reports] for details
     */
    val reports: Reports
        get() =_reports ?: throw IllegalAccessException("SDK not setup")

    /**
     * Bills - All bills and bill payments. See [Bills] for details
     */
    val bills: Bills
        get() =_bills ?: throw IllegalAccessException("SDK not setup")

    private var _setup = false
    private var _authentication: Authentication? = null
    private var _aggregation: Aggregation? = null
    private var _messages: Messages? = null
    private var _events: Events? = null
    private var _notifications: Notifications? = null
    private var _surveys: Surveys? = null
    private var _reports: Reports? = null
    private var _bills: Bills? = null
    private lateinit var keyStore: Keystore
    private lateinit var preferences: Preferences
    private lateinit var version: Version
    private lateinit var network: NetworkService
    private lateinit var database: SDKDatabase
    internal var refreshTimer: Timer? = null
        private set
    private var deviceLastUpdated: LocalDateTime? = null

    internal lateinit var app: Application

    /**
     * Setup the SDK
     *
     * Sets up the SDK for use by performing any database migrations or other underlying setup needed. Must be called and completed before using the SDK.
     *
     * @param application Application instance of the client app.
     * @param configuration Configuration and preferences needed to setup the SDK. See [FrolloSDKConfiguration] for details.
     * @param completion Completion handler with optional error if something goes wrong during the setup process.
     *
     * @throws FrolloSDKError if SDK is already setup or Server URL is empty.
     */
    @Throws(FrolloSDKError::class)
    fun setup(application: Application, configuration: FrolloSDKConfiguration, completion: OnFrolloSDKCompletionListener<Result>) {
        this.app = application

        if (_setup) throw FrolloSDKError("SDK already setup")
        if (configuration.serverUrl.isBlank()) throw FrolloSDKError("Server URL cannot be empty")

        val localBroadcastManager = LocalBroadcastManager.getInstance(app)

        try {
            val deviceInfo = DeviceInfo(application.applicationContext)

            // 1. Initialize ThreeTenABP
            initializeThreeTenABP()
            // 2. Setup Keystore
            keyStore = Keystore()
            keyStore.setup()
            // 3. Setup Preferences
            preferences = Preferences(application.applicationContext)
            // 4. Setup Database
            database = SDKDatabase.getInstance(application)
            // 5. Setup Version Manager
            version = Version(preferences)
            // 6. Setup Network Stack
            val oAuth = OAuth(config = configuration)
            network = NetworkService(oAuth = oAuth, keystore = keyStore, pref = preferences)
            // 7. Setup Logger
            // Initialize Log.network, Log.deviceId, Log.deviceName and Log.deviceType
            // before Log.logLevel as Log.logLevel is dependant on Log.network
            Log.network = network
            Log.deviceId = deviceInfo.deviceId
            Log.deviceName = deviceInfo.deviceName
            Log.deviceType = deviceInfo.deviceType
            Log.logLevel = configuration.logLevel
            // 8. Setup Authentication
            _authentication = Authentication(oAuth, deviceInfo, network, database, preferences)
            // 9. Setup Aggregation
            _aggregation = Aggregation(network, database, localBroadcastManager)
            // 10. Setup Messages
            _messages = Messages(network, database)
            // 11. Setup Events
            _events = Events(network)
            // 12. Setup Notifications
            _notifications = Notifications(authentication, events, messages)
            // 13. Setup Surveys
            _surveys = Surveys(network)
            // 14. Setup Reports
            _reports = Reports(network, database, aggregation)
            // 15. Setup Bills
            _bills = Bills(network, database, aggregation)

            if (version.migrationNeeded()) {
                version.migrateVersion()
            }

            _setup = true
            completion.invoke(Result.success())
        } catch (e: Exception) {
            val error = FrolloSDKError("Setup failed : ${e.message}")
            completion.invoke(Result.error(error))
        }
    }

    /**
     * Logout the currently authenticated user. Resets all caches, preferences and databases.
     * This resets the token storage.
     */
    fun logout() {
        authentication.logoutUser()
        reset()
    }

    /**
     * Delete the user account and complete logout activities on success
     *
     * @param completion Completion handler with any error that occurred
     */
    fun deleteUser(completion: OnFrolloSDKCompletionListener<Result>? = null) {
        authentication.deleteUser { result ->
            when (result.status) {
                Result.Status.SUCCESS -> reset(completion)
                Result.Status.ERROR -> completion?.invoke(Result.error(result.error))
            }
        }
    }

    /**
     * Reset the SDK. Clears all caches, databases and preferences. Called automatically from logout.
     *
     * @param completion Completion handler with option error if something goes wrong (optional)
     *
     * @throws IllegalAccessException if SDK is not setup
     */
    @Throws(IllegalAccessException::class)
    fun reset(completion: OnFrolloSDKCompletionListener<Result>? = null) {
        if (!_setup) throw IllegalAccessException("SDK not setup")

        pauseScheduledRefreshing()
        // NOTE: Keystore reset is not required as we do not store any data in there. Just keys.
        authentication.reset()
        preferences.reset()
        database.clearAllTables()
        completion?.invoke(Result.success())

        notify(ACTION_AUTHENTICATION_CHANGED,
                bundleOf(Pair(ARG_AUTHENTICATION_STATUS, AuthenticationStatus.LOGGED_OUT)))
    }

    private fun initializeThreeTenABP() {
        AndroidThreeTen.init(app)
    }

    /**
     * Application entered the background.
     *
     * Notify the SDK of an app lifecycle change. Call this to ensure proper refreshing of cache data occurs when the app enters background or resumes.
     */
    fun onAppBackgrounded() {
        pauseScheduledRefreshing()
    }

    /**
     * Application resumed from background
     *
     * Notify the SDK of an app lifecycle change. Call this to ensure proper refreshing of cache data occurs when the app enters background or resumes.
     */
    fun onAppForegrounded() {
        resumeScheduledRefreshing()

        // Update device timezone, name and IDs regularly
        val now = LocalDateTime.now()

        var updateDevice = true

        deviceLastUpdated?.let { lastUpdated ->
            val time = Duration.between(lastUpdated, now).toMillis()
            if (time < CACHE_EXPIRY) {
                updateDevice = false
            }
        }

        if (updateDevice) {
            deviceLastUpdated = now

            authentication.updateDevice()
        }
    }

    /**
     * Refreshes all cached data in an optimised way. Fetches most urgent data first and then proceeds to update other caches if needed.
     */
    fun refreshData() {
        refreshPrimary()
        Handler().postDelayed({ refreshSecondary() }, 3000)
        Handler().postDelayed({ refreshSystem() }, 20000)

        resumeScheduledRefreshing()
    }

    /**
     * Refresh data from the most time sensitive and important APIs, e.g. accounts, transactions
     */
    private fun refreshPrimary() {
        aggregation.refreshProviderAccounts()
        aggregation.refreshAccounts()
        aggregation.refreshTransactions(
                fromDate = LocalDate.now().minusMonths(1).with(TemporalAdjusters.firstDayOfMonth()).toString(Transaction.DATE_FORMAT_PATTERN),
                toDate = LocalDate.now().toString(Transaction.DATE_FORMAT_PATTERN))
        authentication.refreshUser()
        messages.refreshUnreadMessages()
    }

    /**
     * Refresh data from other important APIs that frequently change but are less time sensitive, e.g. bill payments
     */
    private fun refreshSecondary() {
        //TODO: Refresh Bill Payments
    }

    /**
     * Refresh data from long lived sources which don't change often, e.g. transaction categories, providers
     */
    private fun refreshSystem() {
        aggregation.refreshProviders()
        aggregation.refreshTransactionCategories()
        bills.refreshBills()
        authentication.updateDevice()
    }

    private fun resumeScheduledRefreshing() {
        cancelRefreshTimer()

        val timerTask = object : TimerTask() {
            override fun run() {
                refreshPrimary()
            }
        }
        refreshTimer = Timer()
        refreshTimer?.schedule(
                timerTask,
                CACHE_EXPIRY, // Initial delay set to CACHE_EXPIRY minutes, as refreshData() would have already run refreshPrimary() once.
                CACHE_EXPIRY) // Repeat every CACHE_EXPIRY minutes
    }

    private fun pauseScheduledRefreshing() {
        cancelRefreshTimer()
    }

    private fun cancelRefreshTimer() {
        refreshTimer?.cancel()
        refreshTimer = null
    }

    internal fun forcedLogout() {
        if (authentication.loggedIn)
            reset()
    }
}