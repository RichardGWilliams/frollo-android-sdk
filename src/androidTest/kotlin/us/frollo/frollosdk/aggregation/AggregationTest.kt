package us.frollo.frollosdk.aggregation

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.test.platform.app.InstrumentationRegistry
import com.jraska.livedata.test
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.threeten.bp.LocalDateTime
import org.threeten.bp.ZoneOffset
import us.frollo.frollosdk.FrolloSDK
import us.frollo.frollosdk.authentication.OAuth
import us.frollo.frollosdk.base.Resource
import us.frollo.frollosdk.base.Result
import us.frollo.frollosdk.core.testSDKConfig
import us.frollo.frollosdk.database.SDKDatabase
import us.frollo.frollosdk.network.NetworkService
import us.frollo.frollosdk.network.api.AggregationAPI
import us.frollo.frollosdk.error.DataError
import us.frollo.frollosdk.error.DataErrorSubType
import us.frollo.frollosdk.error.DataErrorType
import us.frollo.frollosdk.keystore.Keystore
import us.frollo.frollosdk.mapping.*
import us.frollo.frollosdk.model.*
import us.frollo.frollosdk.model.coredata.aggregation.accounts.AccountSubType
import us.frollo.frollosdk.preferences.Preferences
import us.frollo.frollosdk.test.R
import us.frollo.frollosdk.testutils.*

class AggregationTest {

    @get:Rule
    val testRule = InstantTaskExecutorRule()
    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private lateinit var mockServer: MockWebServer
    private lateinit var preferences: Preferences
    private lateinit var keystore: Keystore
    private lateinit var database: SDKDatabase
    private lateinit var network: NetworkService

    private lateinit var aggregation: Aggregation

    private fun initSetup() {
        mockServer = MockWebServer()
        mockServer.start()
        val baseUrl = mockServer.url("/")

        val config = testSDKConfig(serverUrl = baseUrl.toString())
        if (!FrolloSDK.isSetup) FrolloSDK.setup(app, config) {}

        keystore = Keystore()
        keystore.setup()
        preferences = Preferences(app)
        database = SDKDatabase.getInstance(app)
        val oAuth = OAuth(config = config)
        network = NetworkService(oAuth = oAuth, keystore = keystore, pref = preferences)

        preferences.encryptedAccessToken = keystore.encrypt("ExistingAccessToken")
        preferences.encryptedRefreshToken = keystore.encrypt("ExistingRefreshToken")
        preferences.accessTokenExpiry = LocalDateTime.now(ZoneOffset.UTC).toEpochSecond(ZoneOffset.UTC) + 900

        aggregation = Aggregation(network, database, LocalBroadcastManager.getInstance(app))
    }

    private fun tearDown() {
        mockServer.shutdown()
        preferences.resetAll()
        database.clearAllTables()
    }

    // Provider Tests

    @Test
    fun testFetchProviderByID() {
        initSetup()

        val data = testProviderResponseData()
        val list = mutableListOf(testProviderResponseData(), data, testProviderResponseData())
        database.providers().insertAll(*list.map { it.toProvider() }.toList().toTypedArray())

        val testObserver = aggregation.fetchProvider(data.providerId).test()
        testObserver.awaitValue()
        assertNotNull(testObserver.value().data)
        assertEquals(data.providerId, testObserver.value().data?.providerId)

        tearDown()
    }

    @Test
    fun testFetchProviders() {
        initSetup()

        val data1 = testProviderResponseData()
        val data2 = testProviderResponseData()
        val data3 = testProviderResponseData()
        val data4 = testProviderResponseData()
        val list = mutableListOf(data1, data2, data3, data4)

        database.providers().insertAll(*list.map { it.toProvider() }.toList().toTypedArray())

        val testObserver = aggregation.fetchProviders().test()
        testObserver.awaitValue()
        assertNotNull(testObserver.value().data)
        assertEquals(4, testObserver.value().data?.size)

        tearDown()
    }

    @Test
    fun testFetchProviderByIDWithRelation() {
        initSetup()

        database.providers().insert(testProviderResponseData(providerId = 123).toProvider())
        database.providerAccounts().insert(testProviderAccountResponseData(providerAccountId = 234, providerId = 123).toProviderAccount())
        database.providerAccounts().insert(testProviderAccountResponseData(providerAccountId = 235, providerId = 123).toProviderAccount())

        val testObserver = aggregation.fetchProviderWithRelation(providerId = 123).test()
        testObserver.awaitValue()

        val model = testObserver.value().data

        assertEquals(123L, model?.provider?.providerId)
        assertEquals(2, model?.providerAccounts?.size)
        assertEquals(234L, model?.providerAccounts?.get(0)?.providerAccountId)
        assertEquals(235L, model?.providerAccounts?.get(1)?.providerAccountId)

        val data = testProviderResponseData()
        val list = mutableListOf(testProviderResponseData(), data, testProviderResponseData())
        database.providers().insertAll(*list.map { it.toProvider() }.toList().toTypedArray())

        tearDown()
    }

    @Test
    fun testFetchProvidersWithRelation() {
        initSetup()

        database.providers().insert(testProviderResponseData(providerId = 123).toProvider())
        database.providerAccounts().insert(testProviderAccountResponseData(providerAccountId = 234, providerId = 123).toProviderAccount())
        database.providerAccounts().insert(testProviderAccountResponseData(providerAccountId = 235, providerId = 123).toProviderAccount())

        val testObserver = aggregation.fetchProvidersWithRelation().test()
        testObserver.awaitValue()
        assertNotNull(testObserver.value().data)
        assertEquals(1, testObserver.value().data?.size)

        val model = testObserver.value().data?.get(0)

        assertEquals(123L, model?.provider?.providerId)
        assertEquals(2, model?.providerAccounts?.size)
        assertEquals(234L, model?.providerAccounts?.get(0)?.providerAccountId)
        assertEquals(235L, model?.providerAccounts?.get(1)?.providerAccountId)

        tearDown()
    }

    @Test
    fun testRefreshProviders() {
        initSetup()

        val body = readStringFromJson(app, R.raw.providers_valid)
        mockServer.setDispatcher(object: Dispatcher() {
            override fun dispatch(request: RecordedRequest?): MockResponse {
                if (request?.trimmedPath == AggregationAPI.URL_PROVIDERS) {
                    return MockResponse()
                            .setResponseCode(200)
                            .setBody(body)
                }
                return MockResponse().setResponseCode(404)
            }
        })

        aggregation.refreshProviders { result ->
            assertEquals(Result.Status.SUCCESS, result.status)
            assertNull(result.error)

            val testObserver = aggregation.fetchProviders().test()
            testObserver.awaitValue()
            val models = testObserver.value().data
            assertNotNull(models)
            assertEquals(311, models?.size)
        }

        val request = mockServer.takeRequest()
        assertEquals(AggregationAPI.URL_PROVIDERS, request.trimmedPath)

        wait(3)

        tearDown()
    }

    @Test
    fun testRefreshProviderByID() {
        initSetup()

        val body = readStringFromJson(app, R.raw.provider_id_12345)
        mockServer.setDispatcher(object: Dispatcher() {
            override fun dispatch(request: RecordedRequest?): MockResponse {
                if (request?.trimmedPath == "aggregation/providers/12345") {
                    return MockResponse()
                            .setResponseCode(200)
                            .setBody(body)
                }
                return MockResponse().setResponseCode(404)
            }
        })

        aggregation.refreshProvider(12345L) { result ->
            assertEquals(Result.Status.SUCCESS, result.status)
            assertNull(result.error)

            val testObserver = aggregation.fetchProvider(12345L).test()
            testObserver.awaitValue()
            val model = testObserver.value().data
            assertNotNull(model)
            assertEquals(12345L, model?.providerId)
        }

        val request = mockServer.takeRequest()
        assertEquals("aggregation/providers/12345", request.trimmedPath)

        wait(3)

        tearDown()
    }

    // Provider Account Tests

    @Test
    fun testFetchProviderAccountByID() {
        initSetup()

        val data = testProviderAccountResponseData()
        val list = mutableListOf(testProviderAccountResponseData(), data, testProviderAccountResponseData())
        database.providerAccounts().insertAll(*list.map { it.toProviderAccount() }.toList().toTypedArray())

        val testObserver = aggregation.fetchProviderAccount(data.providerAccountId).test()
        testObserver.awaitValue()
        assertNotNull(testObserver.value().data)
        assertEquals(data.providerAccountId, testObserver.value().data?.providerAccountId)

        tearDown()
    }

    @Test
    fun testFetchProviderAccounts() {
        initSetup()

        val data1 = testProviderAccountResponseData()
        val data2 = testProviderAccountResponseData()
        val data3 = testProviderAccountResponseData()
        val data4 = testProviderAccountResponseData()
        val list = mutableListOf(data1, data2, data3, data4)

        database.providerAccounts().insertAll(*list.map { it.toProviderAccount() }.toList().toTypedArray())

        val testObserver = aggregation.fetchProviderAccounts().test()
        testObserver.awaitValue()
        assertNotNull(testObserver.value().data)
        assertEquals(4, testObserver.value().data?.size)

        tearDown()
    }

    @Test
    fun testFetchProviderAccountsByProviderId() {
        initSetup()

        val data1 = testProviderAccountResponseData(providerId = 1)
        val data2 = testProviderAccountResponseData(providerId = 2)
        val data3 = testProviderAccountResponseData(providerId = 1)
        val data4 = testProviderAccountResponseData(providerId = 1)
        val list = mutableListOf(data1, data2, data3, data4)

        database.providerAccounts().insertAll(*list.map { it.toProviderAccount() }.toList().toTypedArray())

        val testObserver = aggregation.fetchProviderAccountsByProviderId(providerId = 1).test()
        testObserver.awaitValue()
        assertNotNull(testObserver.value().data)
        assertEquals(3, testObserver.value().data?.size)

        tearDown()
    }

    @Test
    fun testFetchProviderAccountByIDWithRelation() {
        initSetup()

        database.providers().insert(testProviderResponseData(providerId = 123).toProvider())
        database.providerAccounts().insert(testProviderAccountResponseData(providerAccountId = 234, providerId = 123).toProviderAccount())
        database.accounts().insert(testAccountResponseData(accountId = 345, providerAccountId = 234).toAccount())
        database.accounts().insert(testAccountResponseData(accountId = 346, providerAccountId = 234).toAccount())

        val testObserver = aggregation.fetchProviderAccountWithRelation(providerAccountId = 234).test()
        testObserver.awaitValue()

        val model = testObserver.value().data

        assertEquals(123L, model?.provider?.providerId)
        assertEquals(234L, model?.providerAccount?.providerAccountId)
        assertEquals(2, model?.accounts?.size)
        assertEquals(345L, model?.accounts?.get(0)?.accountId)
        assertEquals(346L, model?.accounts?.get(1)?.accountId)

        tearDown()
    }

    @Test
    fun testFetchProviderAccountsWithRelation() {
        initSetup()

        database.providers().insert(testProviderResponseData(providerId = 123).toProvider())
        database.providerAccounts().insert(testProviderAccountResponseData(providerAccountId = 234, providerId = 123).toProviderAccount())
        database.accounts().insert(testAccountResponseData(accountId = 345, providerAccountId = 234).toAccount())
        database.accounts().insert(testAccountResponseData(accountId = 346, providerAccountId = 234).toAccount())

        val testObserver = aggregation.fetchProviderAccountsWithRelation().test()
        testObserver.awaitValue()
        assertNotNull(testObserver.value().data)
        assertEquals(1, testObserver.value().data?.size)

        val model = testObserver.value().data?.get(0)

        assertEquals(123L, model?.provider?.providerId)
        assertEquals(234L, model?.providerAccount?.providerAccountId)
        assertEquals(2, model?.accounts?.size)
        assertEquals(345L, model?.accounts?.get(0)?.accountId)
        assertEquals(346L, model?.accounts?.get(1)?.accountId)

        tearDown()
    }

    @Test
    fun testFetchProviderAccountsByProviderIdWithRelation() {
        initSetup()

        database.providers().insert(testProviderResponseData(providerId = 123).toProvider())
        database.providerAccounts().insert(testProviderAccountResponseData(providerAccountId = 234, providerId = 123).toProviderAccount())
        database.accounts().insert(testAccountResponseData(accountId = 345, providerAccountId = 234).toAccount())
        database.accounts().insert(testAccountResponseData(accountId = 346, providerAccountId = 234).toAccount())
        database.providerAccounts().insert(testProviderAccountResponseData(providerAccountId = 235, providerId = 123).toProviderAccount())
        database.accounts().insert(testAccountResponseData(accountId = 347, providerAccountId = 235).toAccount())
        database.accounts().insert(testAccountResponseData(accountId = 348, providerAccountId = 235).toAccount())

        val testObserver = aggregation.fetchProviderAccountsByProviderIdWithRelation(providerId = 123).test()
        testObserver.awaitValue()
        assertNotNull(testObserver.value().data)
        assertEquals(2, testObserver.value().data?.size)

        val model1 = testObserver.value().data?.get(0)

        assertEquals(123L, model1?.provider?.providerId)
        assertEquals(234L, model1?.providerAccount?.providerAccountId)
        assertEquals(2, model1?.accounts?.size)
        assertEquals(345L, model1?.accounts?.get(0)?.accountId)
        assertEquals(346L, model1?.accounts?.get(1)?.accountId)

        val model2 = testObserver.value().data?.get(1)

        assertEquals(123L, model2?.provider?.providerId)
        assertEquals(235L, model2?.providerAccount?.providerAccountId)
        assertEquals(2, model2?.accounts?.size)
        assertEquals(347L, model2?.accounts?.get(0)?.accountId)
        assertEquals(348L, model2?.accounts?.get(1)?.accountId)

        tearDown()
    }

    @Test
    fun testRefreshProviderAccounts() {
        initSetup()

        val body = readStringFromJson(app, R.raw.provider_accounts_valid)
        mockServer.setDispatcher(object: Dispatcher() {
            override fun dispatch(request: RecordedRequest?): MockResponse {
                if (request?.trimmedPath == AggregationAPI.URL_PROVIDER_ACCOUNTS) {
                    return MockResponse()
                            .setResponseCode(200)
                            .setBody(body)
                }
                return MockResponse().setResponseCode(404)
            }
        })

        aggregation.refreshProviderAccounts { result ->
            assertEquals(Result.Status.SUCCESS, result.status)
            assertNull(result.error)

            val testObserver = aggregation.fetchProviderAccounts().test()
            testObserver.awaitValue()
            val models = testObserver.value().data
            assertNotNull(models)
            assertEquals(4, models?.size)
        }

        val request = mockServer.takeRequest()
        assertEquals(AggregationAPI.URL_PROVIDER_ACCOUNTS, request.trimmedPath)

        wait(3)

        tearDown()
    }

    @Test
    fun testRefreshProviderAccountByID() {
        initSetup()

        val body = readStringFromJson(app, R.raw.provider_account_id_123)
        mockServer.setDispatcher(object: Dispatcher() {
            override fun dispatch(request: RecordedRequest?): MockResponse {
                if (request?.trimmedPath == "aggregation/provideraccounts/123") {
                    return MockResponse()
                            .setResponseCode(200)
                            .setBody(body)
                }
                return MockResponse().setResponseCode(404)
            }
        })

        aggregation.refreshProviderAccount(123L) { result ->
            assertEquals(Result.Status.SUCCESS, result.status)
            assertNull(result.error)

            val testObserver = aggregation.fetchProviderAccount(123L).test()
            testObserver.awaitValue()
            val model = testObserver.value().data
            assertNotNull(model)
            assertEquals(123L, model?.providerAccountId)
        }

        val request = mockServer.takeRequest()
        assertEquals("aggregation/provideraccounts/123", request.trimmedPath)

        wait(3)

        tearDown()
    }

    @Test
    fun testCreateProviderAccount() {
        initSetup()

        val body = readStringFromJson(app, R.raw.provider_account_id_123)
        mockServer.setDispatcher(object: Dispatcher() {
            override fun dispatch(request: RecordedRequest?): MockResponse {
                if (request?.trimmedPath == AggregationAPI.URL_PROVIDER_ACCOUNTS) {
                    return MockResponse()
                            .setResponseCode(200)
                            .setBody(body)
                }
                return MockResponse().setResponseCode(404)
            }
        })

        aggregation.createProviderAccount(providerId = 4078, loginForm = loginFormFilledData()) { result ->
            assertEquals(Result.Status.SUCCESS, result.status)
            assertNull(result.error)

            val testObserver = aggregation.fetchProviderAccounts().test()
            testObserver.awaitValue()
            val models = testObserver.value().data
            assertNotNull(models)
            assertEquals(1, models?.size)
            assertEquals(123L, models?.get(0)?.providerAccountId)
        }

        val request = mockServer.takeRequest()
        assertEquals(AggregationAPI.URL_PROVIDER_ACCOUNTS, request.trimmedPath)

        wait(3)

        tearDown()
    }

    @Test
    fun testDeleteProviderAccount() {
        initSetup()

        mockServer.setDispatcher(object: Dispatcher() {
            override fun dispatch(request: RecordedRequest?): MockResponse {
                if (request?.trimmedPath == "aggregation/provideraccounts/12345") {
                    return MockResponse()
                            .setResponseCode(204)
                }
                return MockResponse().setResponseCode(404)
            }
        })

        val data = testProviderAccountResponseData(providerAccountId = 12345)
        database.providerAccounts().insert(data.toProviderAccount())

        aggregation.deleteProviderAccount(12345) { result ->
            assertEquals(Result.Status.SUCCESS, result.status)
            assertNull(result.error)

            wait(1)

            val testObserver = aggregation.fetchProviderAccount(12345).test()
            testObserver.awaitValue()
            val model = testObserver.value().data
            assertNull(model)
        }

        val request = mockServer.takeRequest()
        assertEquals("aggregation/provideraccounts/12345", request.trimmedPath)

        wait(3)

        tearDown()
    }

    @Test
    fun testUpdateProviderAccount() {
        initSetup()

        val body = readStringFromJson(app, R.raw.provider_account_id_123)
        mockServer.setDispatcher(object: Dispatcher() {
            override fun dispatch(request: RecordedRequest?): MockResponse {
                if (request?.trimmedPath == "aggregation/provideraccounts/123") {
                    return MockResponse()
                            .setResponseCode(200)
                            .setBody(body)
                }
                return MockResponse().setResponseCode(404)
            }
        })

        aggregation.updateProviderAccount(loginForm = loginFormFilledData(), providerAccountId = 123) { result ->
            assertEquals(Result.Status.SUCCESS, result.status)
            assertNull(result.error)

            val testObserver = aggregation.fetchProviderAccounts().test()
            testObserver.awaitValue()
            val models = testObserver.value().data
            assertNotNull(models)
            assertEquals(1, models?.size)
            assertEquals(123L, models?.get(0)?.providerAccountId)
        }

        val request = mockServer.takeRequest()
        assertEquals("aggregation/provideraccounts/123", request.trimmedPath)

        wait(3)

        tearDown()
    }

    @Test
    fun testProviderAccountsFetchMissingProviders() {
        initSetup()

        mockServer.setDispatcher(object: Dispatcher() {
            override fun dispatch(request: RecordedRequest?): MockResponse {
                if (request?.trimmedPath == AggregationAPI.URL_PROVIDER_ACCOUNTS) {
                    return MockResponse()
                            .setResponseCode(200)
                            .setBody(readStringFromJson(app, R.raw.provider_accounts_valid))
                } else if (request?.trimmedPath == "aggregation/providers/12345") {
                    return MockResponse()
                            .setResponseCode(200)
                            .setBody(readStringFromJson(app, R.raw.provider_id_12345))
                }
                return MockResponse().setResponseCode(404)
            }
        })

        aggregation.refreshProviderAccounts { result ->
            assertEquals(Result.Status.SUCCESS, result.status)
            assertNull(result.error)

            val testObserver = aggregation.fetchProviderAccounts().test()
            testObserver.awaitValue()
            val models = testObserver.value().data
            assertNotNull(models)
            assertEquals(4, models?.size)
        }

        wait(3)

        val testObserver2 = aggregation.fetchProviders().test()
        testObserver2.awaitValue()
        val models2 = testObserver2.value().data
        assertNotNull(models2)
        assertEquals(1, models2?.size)
        assertEquals(12345L, models2?.get(0)?.providerId)

        tearDown()
    }

    // Account Tests

    @Test
    fun testFetchAccountByID() {
        initSetup()

        val data = testAccountResponseData()
        val list = mutableListOf(testAccountResponseData(), data, testAccountResponseData())
        database.accounts().insertAll(*list.map { it.toAccount() }.toList().toTypedArray())

        val testObserver = aggregation.fetchAccount(data.accountId).test()
        testObserver.awaitValue()
        assertNotNull(testObserver.value().data)
        assertEquals(data.accountId, testObserver.value().data?.accountId)

        tearDown()
    }

    @Test
    fun testFetchAccounts() {
        initSetup()

        val data1 = testAccountResponseData()
        val data2 = testAccountResponseData()
        val data3 = testAccountResponseData()
        val data4 = testAccountResponseData()
        val list = mutableListOf(data1, data2, data3, data4)

        database.accounts().insertAll(*list.map { it.toAccount() }.toList().toTypedArray())

        val testObserver = aggregation.fetchAccounts().test()
        testObserver.awaitValue()
        assertNotNull(testObserver.value().data)
        assertEquals(4, testObserver.value().data?.size)

        tearDown()
    }

    @Test
    fun testFetchAccountsByProviderAccountId() {
        initSetup()

        val data1 = testAccountResponseData(providerAccountId = 1)
        val data2 = testAccountResponseData(providerAccountId = 2)
        val data3 = testAccountResponseData(providerAccountId = 1)
        val data4 = testAccountResponseData(providerAccountId = 1)
        val list = mutableListOf(data1, data2, data3, data4)

        database.accounts().insertAll(*list.map { it.toAccount() }.toList().toTypedArray())

        val testObserver = aggregation.fetchAccountsByProviderAccountId(providerAccountId = 1).test()
        testObserver.awaitValue()
        assertNotNull(testObserver.value().data)
        assertEquals(3, testObserver.value().data?.size)

        tearDown()
    }

    @Test
    fun testFetchAccountByIDWithRelation() {
        initSetup()

        database.providers().insert(testProviderResponseData(providerId = 123).toProvider())
        database.providerAccounts().insert(testProviderAccountResponseData(providerAccountId = 234, providerId = 123).toProviderAccount())
        database.accounts().insert(testAccountResponseData(accountId = 345, providerAccountId = 234).toAccount())
        database.transactions().insert(testTransactionResponseData(transactionId = 456, accountId = 345).toTransaction())
        database.transactions().insert(testTransactionResponseData(transactionId = 457, accountId = 345).toTransaction())

        val testObserver = aggregation.fetchAccountWithRelation(accountId = 345).test()
        testObserver.awaitValue()

        val model = testObserver.value().data

        assertEquals(345L, model?.account?.accountId)
        assertEquals(234L, model?.providerAccount?.providerAccount?.providerAccountId)
        assertEquals(2, model?.transactions?.size)
        assertEquals(456L, model?.transactions?.get(0)?.transactionId)
        assertEquals(457L, model?.transactions?.get(1)?.transactionId)

        tearDown()
    }

    @Test
    fun testFetchAccountsWithRelation() {
        initSetup()

        database.providers().insert(testProviderResponseData(providerId = 123).toProvider())
        database.providerAccounts().insert(testProviderAccountResponseData(providerAccountId = 234, providerId = 123).toProviderAccount())
        database.accounts().insert(testAccountResponseData(accountId = 345, providerAccountId = 234).toAccount())
        database.transactions().insert(testTransactionResponseData(transactionId = 456, accountId = 345).toTransaction())
        database.transactions().insert(testTransactionResponseData(transactionId = 457, accountId = 345).toTransaction())

        val testObserver = aggregation.fetchAccountsWithRelation().test()
        testObserver.awaitValue()
        assertNotNull(testObserver.value().data)
        assertEquals(1, testObserver.value().data?.size)

        val model = testObserver.value().data?.get(0)

        assertEquals(345L, model?.account?.accountId)
        assertEquals(234L, model?.providerAccount?.providerAccount?.providerAccountId)
        assertEquals(2, model?.transactions?.size)
        assertEquals(456L, model?.transactions?.get(0)?.transactionId)
        assertEquals(457L, model?.transactions?.get(1)?.transactionId)

        tearDown()
    }

    @Test
    fun testFetchAccountsByProviderAccountIdWithRelation() {
        initSetup()

        database.providers().insert(testProviderResponseData(providerId = 123).toProvider())
        database.providerAccounts().insert(testProviderAccountResponseData(providerAccountId = 234, providerId = 123).toProviderAccount())
        database.accounts().insert(testAccountResponseData(accountId = 345, providerAccountId = 234).toAccount())
        database.transactions().insert(testTransactionResponseData(transactionId = 456, accountId = 345).toTransaction())
        database.transactions().insert(testTransactionResponseData(transactionId = 457, accountId = 345).toTransaction())
        database.accounts().insert(testAccountResponseData(accountId = 346, providerAccountId = 234).toAccount())
        database.transactions().insert(testTransactionResponseData(transactionId = 458, accountId = 346).toTransaction())
        database.transactions().insert(testTransactionResponseData(transactionId = 459, accountId = 346).toTransaction())

        val testObserver = aggregation.fetchAccountsByProviderAccountIdWithRelation(providerAccountId = 234).test()
        testObserver.awaitValue()
        assertNotNull(testObserver.value().data)
        assertEquals(2, testObserver.value().data?.size)

        val model1 = testObserver.value().data?.get(0)

        assertEquals(345L, model1?.account?.accountId)
        assertEquals(234L, model1?.providerAccount?.providerAccount?.providerAccountId)
        assertEquals(2, model1?.transactions?.size)
        assertEquals(456L, model1?.transactions?.get(0)?.transactionId)
        assertEquals(457L, model1?.transactions?.get(1)?.transactionId)

        val model2 = testObserver.value().data?.get(1)

        assertEquals(346L, model2?.account?.accountId)
        assertEquals(234L, model2?.providerAccount?.providerAccount?.providerAccountId)
        assertEquals(2, model2?.transactions?.size)
        assertEquals(458L, model2?.transactions?.get(0)?.transactionId)
        assertEquals(459L, model2?.transactions?.get(1)?.transactionId)

        tearDown()
    }

    @Test
    fun testRefreshAccounts() {
        initSetup()

        val body = readStringFromJson(app, R.raw.accounts_valid)
        mockServer.setDispatcher(object: Dispatcher() {
            override fun dispatch(request: RecordedRequest?): MockResponse {
                if (request?.trimmedPath == AggregationAPI.URL_ACCOUNTS) {
                    return MockResponse()
                            .setResponseCode(200)
                            .setBody(body)
                }
                return MockResponse().setResponseCode(404)
            }
        })

        aggregation.refreshAccounts { result ->
            assertEquals(Result.Status.SUCCESS, result.status)
            assertNull(result.error)

            val testObserver = aggregation.fetchAccounts().test()
            testObserver.awaitValue()
            val models = testObserver.value().data
            assertNotNull(models)
            assertEquals(4, models?.size)
        }

        val request = mockServer.takeRequest()
        assertEquals(AggregationAPI.URL_ACCOUNTS, request.trimmedPath)

        wait(3)

        tearDown()
    }

    @Test
    fun testRefreshAccountByID() {
        initSetup()

        val body = readStringFromJson(app, R.raw.account_id_542)
        mockServer.setDispatcher(object: Dispatcher() {
            override fun dispatch(request: RecordedRequest?): MockResponse {
                if (request?.trimmedPath == "aggregation/accounts/542") {
                    return MockResponse()
                            .setResponseCode(200)
                            .setBody(body)
                }
                return MockResponse().setResponseCode(404)
            }
        })

        aggregation.refreshAccount(542L) { result ->
            assertEquals(Result.Status.SUCCESS, result.status)
            assertNull(result.error)

            val testObserver = aggregation.fetchAccount(542L).test()
            testObserver.awaitValue()
            val model = testObserver.value().data
            assertNotNull(model)
            assertEquals(542L, model?.accountId)
        }

        val request = mockServer.takeRequest()
        assertEquals("aggregation/accounts/542", request.trimmedPath)

        wait(3)

        tearDown()
    }

    @Test
    fun testUpdateAccountValid() {
        initSetup()

        val body = readStringFromJson(app, R.raw.account_id_542)
        mockServer.setDispatcher(object: Dispatcher() {
            override fun dispatch(request: RecordedRequest?): MockResponse {
                if (request?.trimmedPath == "aggregation/accounts/542") {
                    return MockResponse()
                            .setResponseCode(200)
                            .setBody(body)
                }
                return MockResponse().setResponseCode(404)
            }
        })

        aggregation.updateAccount(
                accountId = 542,
                hidden = false,
                included = true,
                favourite = randomBoolean(),
                accountSubType = AccountSubType.SAVINGS,
                nickName = randomUUID()) { result ->

            assertEquals(Result.Status.SUCCESS, result.status)
            assertNull(result.error)

            val testObserver = aggregation.fetchAccounts().test()
            testObserver.awaitValue()
            val models = testObserver.value().data
            assertNotNull(models)
            assertEquals(1, models?.size)
            assertEquals(542L, models?.get(0)?.accountId)
            assertEquals(867L, models?.get(0)?.providerAccountId)
        }

        val request = mockServer.takeRequest()
        assertEquals("aggregation/accounts/542", request.trimmedPath)

        wait(3)

        tearDown()
    }

    @Test
    fun testUpdateAccountInvalid() {
        initSetup()

        val body = readStringFromJson(app, R.raw.account_id_542)
        mockServer.setDispatcher(object: Dispatcher() {
            override fun dispatch(request: RecordedRequest?): MockResponse {
                if (request?.trimmedPath == "aggregation/accounts/542") {
                    return MockResponse()
                            .setResponseCode(200)
                            .setBody(body)
                }
                return MockResponse().setResponseCode(404)
            }
        })

        aggregation.updateAccount(
                accountId = 542,
                hidden = true,
                included = true,
                favourite = randomBoolean(),
                accountSubType = AccountSubType.SAVINGS,
                nickName = randomUUID()) { result ->

            assertEquals(Result.Status.ERROR, result.status)
            assertNotNull(result.error)
            assertTrue(result.error is DataError)
            assertEquals(DataErrorType.API, (result.error as DataError).type)
            assertEquals(DataErrorSubType.INVALID_DATA, (result.error as DataError).subType)
        }

        wait(3)

        tearDown()
    }

    // Transaction Tests

    @Test
    fun testFetchTransactionByID() {
        initSetup()

        val data = testTransactionResponseData()
        val list = mutableListOf(testTransactionResponseData(), data, testTransactionResponseData())
        database.transactions().insertAll(*list.map { it.toTransaction() }.toList().toTypedArray())

        val testObserver = aggregation.fetchTransaction(data.transactionId).test()
        testObserver.awaitValue()
        assertNotNull(testObserver.value().data)
        assertEquals(data.transactionId, testObserver.value().data?.transactionId)

        tearDown()
    }

    @Test
    fun testFetchTransactions() {
        initSetup()

        val data1 = testTransactionResponseData()
        val data2 = testTransactionResponseData()
        val data3 = testTransactionResponseData()
        val data4 = testTransactionResponseData()
        val list = mutableListOf(data1, data2, data3, data4)

        database.transactions().insertAll(*list.map { it.toTransaction() }.toList().toTypedArray())

        val testObserver = aggregation.fetchTransactions().test()
        testObserver.awaitValue()
        assertNotNull(testObserver.value().data)
        assertEquals(4, testObserver.value().data?.size)

        tearDown()
    }

    @Test
    fun testFetchTransactionByIds() {
        initSetup()

        val data1 = testTransactionResponseData(transactionId = 100)
        val data2 = testTransactionResponseData(transactionId = 101)
        val data3 = testTransactionResponseData(transactionId = 102)
        val data4 = testTransactionResponseData(transactionId = 103)
        val list = mutableListOf(data1, data2, data3, data4)

        database.transactions().insertAll(*list.map { it.toTransaction() }.toList().toTypedArray())

        val testObserver = aggregation.fetchTransactions(longArrayOf(101,103)).test()
        testObserver.awaitValue()
        assertNotNull(testObserver.value().data)
        assertEquals(2, testObserver.value().data?.size)

        tearDown()
    }

    @Test
    fun testFetchTransactionsByAccountId() {
        initSetup()

        val data1 = testTransactionResponseData(accountId = 1)
        val data2 = testTransactionResponseData(accountId = 2)
        val data3 = testTransactionResponseData(accountId = 1)
        val data4 = testTransactionResponseData(accountId = 1)
        val list = mutableListOf(data1, data2, data3, data4)

        database.transactions().insertAll(*list.map { it.toTransaction() }.toList().toTypedArray())

        val testObserver = aggregation.fetchTransactionsByAccountId(accountId = 1).test()
        testObserver.awaitValue()
        assertNotNull(testObserver.value().data)
        assertEquals(3, testObserver.value().data?.size)

        tearDown()
    }

    @Test
    fun testFetchTransactionByIDWithRelation() {
        initSetup()

        database.transactions().insert(testTransactionResponseData(transactionId = 123, accountId = 234, categoryId = 567, merchantId = 678).toTransaction())
        database.accounts().insert(testAccountResponseData(accountId = 234, providerAccountId = 345).toAccount())
        database.providerAccounts().insert(testProviderAccountResponseData(providerAccountId = 345, providerId = 456).toProviderAccount())
        database.providers().insert(testProviderResponseData(providerId = 456).toProvider())
        database.transactionCategories().insert(testTransactionCategoryResponseData(transactionCategoryId = 567).toTransactionCategory())
        database.merchants().insert(testMerchantResponseData(merchantId = 678).toMerchant())

        val testObserver = aggregation.fetchTransactionWithRelation(transactionId = 123).test()
        testObserver.awaitValue()

        val model = testObserver.value().data

        assertEquals(123L, model?.transaction?.transactionId)
        assertEquals(678L, model?.merchant?.merchantId)
        assertEquals(567L, model?.transactionCategory?.transactionCategoryId)
        assertEquals(234L, model?.account?.account?.accountId)

        tearDown()
    }

    @Test
    fun testFetchTransactionsWithRelation() {
        initSetup()

        database.transactions().insert(testTransactionResponseData(transactionId = 123, accountId = 234, categoryId = 567, merchantId = 678).toTransaction())
        database.accounts().insert(testAccountResponseData(accountId = 234, providerAccountId = 345).toAccount())
        database.providerAccounts().insert(testProviderAccountResponseData(providerAccountId = 345, providerId = 456).toProviderAccount())
        database.providers().insert(testProviderResponseData(providerId = 456).toProvider())
        database.transactionCategories().insert(testTransactionCategoryResponseData(transactionCategoryId = 567).toTransactionCategory())
        database.merchants().insert(testMerchantResponseData(merchantId = 678).toMerchant())

        val testObserver = aggregation.fetchTransactionsWithRelation().test()
        testObserver.awaitValue()

        assertNotNull(testObserver.value().data)
        assertEquals(1, testObserver.value().data?.size)

        val model = testObserver.value().data?.get(0)

        assertEquals(123L, model?.transaction?.transactionId)
        assertEquals(678L, model?.merchant?.merchantId)
        assertEquals(567L, model?.transactionCategory?.transactionCategoryId)
        assertEquals(234L, model?.account?.account?.accountId)

        tearDown()
    }

    @Test
    fun testFetchTransactionByIdsWithRelation() {
        initSetup()

        database.transactions().insert(testTransactionResponseData(transactionId = 122, accountId = 234, categoryId = 567, merchantId = 678).toTransaction())
        database.transactions().insert(testTransactionResponseData(transactionId = 123, accountId = 234, categoryId = 567, merchantId = 678).toTransaction())
        database.accounts().insert(testAccountResponseData(accountId = 234, providerAccountId = 345).toAccount())
        database.providerAccounts().insert(testProviderAccountResponseData(providerAccountId = 345, providerId = 456).toProviderAccount())
        database.providers().insert(testProviderResponseData(providerId = 456).toProvider())
        database.transactionCategories().insert(testTransactionCategoryResponseData(transactionCategoryId = 567).toTransactionCategory())
        database.merchants().insert(testMerchantResponseData(merchantId = 678).toMerchant())

        val testObserver = aggregation.fetchTransactionsWithRelation(transactionIds = longArrayOf(122, 123)).test()
        testObserver.awaitValue()

        assertNotNull(testObserver.value().data)
        assertEquals(2, testObserver.value().data?.size)

        val model1 = testObserver.value().data?.get(0)

        assertEquals(122L, model1?.transaction?.transactionId)
        assertEquals(678L, model1?.merchant?.merchantId)
        assertEquals(567L, model1?.transactionCategory?.transactionCategoryId)
        assertEquals(234L, model1?.account?.account?.accountId)

        val model2 = testObserver.value().data?.get(1)

        assertEquals(123L, model2?.transaction?.transactionId)
        assertEquals(678L, model2?.merchant?.merchantId)
        assertEquals(567L, model2?.transactionCategory?.transactionCategoryId)
        assertEquals(234L, model2?.account?.account?.accountId)

        tearDown()
    }

    @Test
    fun testFetchTransactionsByAccountIdWithRelation() {
        initSetup()

        database.transactions().insert(testTransactionResponseData(transactionId = 122, accountId = 234, categoryId = 567, merchantId = 678).toTransaction())
        database.transactions().insert(testTransactionResponseData(transactionId = 123, accountId = 234, categoryId = 567, merchantId = 678).toTransaction())
        database.accounts().insert(testAccountResponseData(accountId = 234, providerAccountId = 345).toAccount())
        database.providerAccounts().insert(testProviderAccountResponseData(providerAccountId = 345, providerId = 456).toProviderAccount())
        database.providers().insert(testProviderResponseData(providerId = 456).toProvider())
        database.transactionCategories().insert(testTransactionCategoryResponseData(transactionCategoryId = 567).toTransactionCategory())
        database.merchants().insert(testMerchantResponseData(merchantId = 678).toMerchant())

        val testObserver = aggregation.fetchTransactionsByAccountIdWithRelation(accountId = 234).test()
        testObserver.awaitValue()

        assertNotNull(testObserver.value().data)
        assertEquals(2, testObserver.value().data?.size)

        val model1 = testObserver.value().data?.get(0)

        assertEquals(122L, model1?.transaction?.transactionId)
        assertEquals(678L, model1?.merchant?.merchantId)
        assertEquals(567L, model1?.transactionCategory?.transactionCategoryId)
        assertEquals(234L, model1?.account?.account?.accountId)

        val model2 = testObserver.value().data?.get(1)

        assertEquals(123L, model2?.transaction?.transactionId)
        assertEquals(678L, model2?.merchant?.merchantId)
        assertEquals(567L, model2?.transactionCategory?.transactionCategoryId)
        assertEquals(234L, model2?.account?.account?.accountId)

        tearDown()
    }

    @Test
    fun testRefreshTransactions() {
        initSetup()

        val body = readStringFromJson(app, R.raw.transactions_2018_08_01_valid)
        mockServer.setDispatcher(object: Dispatcher() {
            override fun dispatch(request: RecordedRequest?): MockResponse {
                if (request?.trimmedPath == "${AggregationAPI.URL_TRANSACTIONS}?from_date=2018-06-01&to_date=2018-08-08&skip=0&count=200") {
                    return MockResponse()
                            .setResponseCode(200)
                            .setBody(body)
                }
                return MockResponse().setResponseCode(404)
            }
        })

        aggregation.refreshTransactions(fromDate = "2018-06-01", toDate = "2018-08-08") { result ->
            assertEquals(Result.Status.SUCCESS, result.status)
            assertNull(result.error)

            val testObserver = aggregation.fetchTransactions().test()
            testObserver.awaitValue()
            val models = testObserver.value().data
            assertNotNull(models)
            assertEquals(179, models?.size)
        }

        val request = mockServer.takeRequest()
        assertEquals("${AggregationAPI.URL_TRANSACTIONS}?from_date=2018-06-01&to_date=2018-08-08&skip=0&count=200", request.trimmedPath)

        wait(3)

        tearDown()
    }

    @Test
    fun testRefreshPaginatedTransactions() {
        initSetup()

        mockServer.setDispatcher(object: Dispatcher() {
            override fun dispatch(request: RecordedRequest?): MockResponse {
                if (request?.trimmedPath == "${AggregationAPI.URL_TRANSACTIONS}?from_date=2018-08-01&to_date=2018-08-31&skip=0&count=200") {
                    return MockResponse()
                            .setResponseCode(200)
                            .setBody(readStringFromJson(app, R.raw.transactions_2018_12_04_count_200_skip_0))
                } else if (request?.trimmedPath == "${AggregationAPI.URL_TRANSACTIONS}?from_date=2018-08-01&to_date=2018-08-31&skip=200&count=200") {
                    return MockResponse()
                            .setResponseCode(200)
                            .setBody(readStringFromJson(app, R.raw.transactions_2018_12_04_count_200_skip_200))
                }
                return MockResponse().setResponseCode(404)
            }
        })

        aggregation.refreshTransactions(fromDate = "2018-08-01", toDate = "2018-08-31") { result ->
            assertEquals(Result.Status.SUCCESS, result.status)
            assertNull(result.error)

            val testObserver = aggregation.fetchTransactions().test()
            testObserver.awaitValue()
            val models = testObserver.value().data
            assertNotNull(models)
            assertEquals(315, models?.size)
        }

        wait(3)

        tearDown()
    }

    @Test
    fun testRefreshTransactionByID() {
        initSetup()

        val body = readStringFromJson(app, R.raw.transaction_id_99703)
        mockServer.setDispatcher(object: Dispatcher() {
            override fun dispatch(request: RecordedRequest?): MockResponse {
                if (request?.trimmedPath == "aggregation/transactions/99703") {
                    return MockResponse()
                            .setResponseCode(200)
                            .setBody(body)
                }
                return MockResponse().setResponseCode(404)
            }
        })

        aggregation.refreshTransaction(99703L) { result ->
            assertEquals(Result.Status.SUCCESS, result.status)
            assertNull(result.error)

            val testObserver = aggregation.fetchTransaction(99703L).test()
            testObserver.awaitValue()
            val model = testObserver.value().data
            assertNotNull(model)
            assertEquals(99703L, model?.transactionId)
        }

        val request = mockServer.takeRequest()
        assertEquals("aggregation/transactions/99703", request.trimmedPath)

        wait(3)

        tearDown()
    }

    @Test
    fun testRefreshTransactionsByIds() {
        initSetup()

        val body = readStringFromJson(app, R.raw.transactions_2018_08_01_valid)
        mockServer.setDispatcher(object: Dispatcher() {
            override fun dispatch(request: RecordedRequest?): MockResponse {
                if (request?.trimmedPath == "${AggregationAPI.URL_TRANSACTIONS}?transaction_ids=1,2,3,4,5") {
                    return MockResponse()
                            .setResponseCode(200)
                            .setBody(body)
                }
                return MockResponse().setResponseCode(404)
            }
        })

        aggregation.refreshTransactions(longArrayOf(1, 2, 3, 4, 5)) { result ->
            assertEquals(Result.Status.SUCCESS, result.status)
            assertNull(result.error)

            val testObserver = aggregation.fetchTransactions().test()
            testObserver.awaitValue()
            val models = testObserver.value().data
            assertNotNull(models)
            assertEquals(179, models?.size)
        }

        val request = mockServer.takeRequest()
        assertEquals("${AggregationAPI.URL_TRANSACTIONS}?transaction_ids=1,2,3,4,5", request.trimmedPath)

        wait(3)

        tearDown()
    }

    @Test
    fun testExcludeTransaction() {
        initSetup()

        val body = readStringFromJson(app, R.raw.transaction_id_99703_excluded)
        mockServer.setDispatcher(object: Dispatcher() {
            override fun dispatch(request: RecordedRequest?): MockResponse {
                if (request?.trimmedPath == "aggregation/transactions/99703") {
                    return MockResponse()
                            .setResponseCode(200)
                            .setBody(body)
                }
                return MockResponse().setResponseCode(404)
            }
        })

        val transaction = testTransactionResponseData(transactionId = 99703, included = true).toTransaction()
        database.transactions().insert(transaction)

        aggregation.excludeTransaction(transactionId = 99703, excluded = true, applyToAll = true) { result ->
            assertEquals(Result.Status.SUCCESS, result.status)
            assertNull(result.error)

            val testObserver = aggregation.fetchTransactions().test()
            testObserver.awaitValue()
            val models = testObserver.value().data
            assertNotNull(models)
            assertEquals(1, models?.size)
            assertEquals(99703L, models?.get(0)?.transactionId)
            assertTrue(models?.get(0)?.included == false)
        }

        val request = mockServer.takeRequest()
        assertEquals("aggregation/transactions/99703", request.trimmedPath)

        wait(3)

        tearDown()
    }

    @Test
    fun testRecategoriseTransaction() {
        initSetup()

        val body = readStringFromJson(app, R.raw.transaction_id_99703)
        mockServer.setDispatcher(object: Dispatcher() {
            override fun dispatch(request: RecordedRequest?): MockResponse {
                if (request?.trimmedPath == "aggregation/transactions/99703") {
                    return MockResponse()
                            .setResponseCode(200)
                            .setBody(body)
                }
                return MockResponse().setResponseCode(404)
            }
        })

        val transaction = testTransactionResponseData(transactionId = 99703, categoryId = 123).toTransaction()
        database.transactions().insert(transaction)

        aggregation.recategoriseTransaction(transactionId = 99703, transactionCategoryId = 81, applyToAll = true) { result ->
            assertEquals(Result.Status.SUCCESS, result.status)
            assertNull(result.error)

            val testObserver = aggregation.fetchTransactions().test()
            testObserver.awaitValue()
            val models = testObserver.value().data
            assertNotNull(models)
            assertEquals(1, models?.size)
            assertEquals(99703L, models?.get(0)?.transactionId)
            assertEquals(81L, models?.get(0)?.categoryId)
        }

        val request = mockServer.takeRequest()
        assertEquals("aggregation/transactions/99703", request.trimmedPath)

        wait(3)

        tearDown()
    }

    @Test
    fun testUpdateTransaction() {
        initSetup()

        val body = readStringFromJson(app, R.raw.transaction_id_99703)
        mockServer.setDispatcher(object: Dispatcher() {
            override fun dispatch(request: RecordedRequest?): MockResponse {
                if (request?.trimmedPath == "aggregation/transactions/99703") {
                    return MockResponse()
                            .setResponseCode(200)
                            .setBody(body)
                }
                return MockResponse().setResponseCode(404)
            }
        })

        val transaction = testTransactionResponseData().toTransaction()

        aggregation.updateTransaction(99703, transaction) { result ->
            assertEquals(Result.Status.SUCCESS, result.status)
            assertNull(result.error)

            val testObserver = aggregation.fetchTransactions().test()
            testObserver.awaitValue()
            val models = testObserver.value().data
            assertNotNull(models)
            assertEquals(1, models?.size)
            assertEquals(99703L, models?.get(0)?.transactionId)
            assertEquals(543L, models?.get(0)?.accountId)
        }

        val request = mockServer.takeRequest()
        assertEquals("aggregation/transactions/99703", request.trimmedPath)

        wait(3)

        tearDown()
    }

    @Test
    fun testTransactionsFetchMissingMerchants() {
        initSetup()

        mockServer.setDispatcher(object: Dispatcher() {
            override fun dispatch(request: RecordedRequest?): MockResponse {
                if (request?.trimmedPath == "${AggregationAPI.URL_TRANSACTIONS}?from_date=2018-06-01&to_date=2018-08-08&skip=0&count=200") {
                    return MockResponse()
                            .setResponseCode(200)
                            .setBody(readStringFromJson(app, R.raw.transactions_2018_08_01_valid))
                } else if (request?.trimmedPath?.contains(AggregationAPI.URL_MERCHANTS) == true) {
                    return MockResponse()
                            .setResponseCode(200)
                            .setBody(readStringFromJson(app, R.raw.merchants_by_id))
                }
                return MockResponse().setResponseCode(404)
            }
        })

        aggregation.refreshTransactions(fromDate = "2018-06-01", toDate = "2018-08-08") { result ->
            assertEquals(Result.Status.SUCCESS, result.status)
            assertNull(result.error)

            val testObserver = aggregation.fetchTransactions().test()
            testObserver.awaitValue()
            val models = testObserver.value().data
            assertNotNull(models)
            assertEquals(179, models?.size)
        }

        wait(3)

        val testObserver2 = aggregation.fetchMerchants().test()
        testObserver2.awaitValue()
        val models2 = testObserver2.value().data
        assertNotNull(models2)
        assertEquals(5, models2?.size)
        assertEquals(1L, models2?.get(0)?.merchantId)

        tearDown()
    }

    // Transaction Summary Tests

    @Test
    fun testFetchTransactionsSummary() {
        initSetup()

        val body = readStringFromJson(app, R.raw.transactions_summary_valid)
        mockServer.setDispatcher(object: Dispatcher() {
            override fun dispatch(request: RecordedRequest?): MockResponse {
                if (request?.trimmedPath == "${AggregationAPI.URL_TRANSACTIONS_SUMMARY}?from_date=2018-06-01&to_date=2018-08-08") {
                    return MockResponse()
                            .setResponseCode(200)
                            .setBody(body)
                }
                return MockResponse().setResponseCode(404)
            }
        })

        aggregation.fetchTransactionsSummary(fromDate = "2018-06-01", toDate = "2018-08-08") { resource ->
            assertEquals(Resource.Status.SUCCESS, resource.status)
            assertNull(resource.error)

            assertNotNull(resource.data)
            assertEquals(166L, resource.data?.count)
            assertEquals((-1039.0).toBigDecimal(), resource.data?.sum)
        }

        val request = mockServer.takeRequest()
        assertEquals("${AggregationAPI.URL_TRANSACTIONS_SUMMARY}?from_date=2018-06-01&to_date=2018-08-08", request.trimmedPath)

        wait(3)

        tearDown()
    }

    @Test
    fun testFetchTransactionsSummaryByIDs() {
        initSetup()

        val body = readStringFromJson(app, R.raw.transactions_summary_valid)
        mockServer.setDispatcher(object: Dispatcher() {
            override fun dispatch(request: RecordedRequest?): MockResponse {
                if (request?.trimmedPath == "${AggregationAPI.URL_TRANSACTIONS_SUMMARY}?transaction_ids=1,2,3,4,5") {
                    return MockResponse()
                            .setResponseCode(200)
                            .setBody(body)
                }
                return MockResponse().setResponseCode(404)
            }
        })

        aggregation.fetchTransactionsSummary(transactionIds = longArrayOf(1, 2, 3, 4, 5)) { resource ->
            assertEquals(Resource.Status.SUCCESS, resource.status)
            assertNull(resource.error)

            assertNotNull(resource.data)
            assertEquals(166L, resource.data?.count)
            assertEquals((-1039.0).toBigDecimal(), resource.data?.sum)
        }

        val request = mockServer.takeRequest()
        assertEquals("${AggregationAPI.URL_TRANSACTIONS_SUMMARY}?transaction_ids=1,2,3,4,5", request.trimmedPath)

        wait(3)

        tearDown()
    }

    // Transaction Category Tests

    @Test
    fun testFetchTransactionCategoryByID() {
        initSetup()

        val data = testTransactionCategoryResponseData()
        val list = mutableListOf(testTransactionCategoryResponseData(), data, testTransactionCategoryResponseData())
        database.transactionCategories().insertAll(*list.map { it.toTransactionCategory() }.toList().toTypedArray())

        val testObserver = aggregation.fetchTransactionCategory(data.transactionCategoryId).test()
        testObserver.awaitValue()
        assertNotNull(testObserver.value().data)
        assertEquals(data.transactionCategoryId, testObserver.value().data?.transactionCategoryId)

        tearDown()
    }

    @Test
    fun testFetchTransactionCategories() {
        initSetup()

        val data1 = testTransactionCategoryResponseData()
        val data2 = testTransactionCategoryResponseData()
        val data3 = testTransactionCategoryResponseData()
        val data4 = testTransactionCategoryResponseData()
        val list = mutableListOf(data1, data2, data3, data4)

        database.transactionCategories().insertAll(*list.map { it.toTransactionCategory() }.toList().toTypedArray())

        val testObserver = aggregation.fetchTransactionCategories().test()
        testObserver.awaitValue()
        assertNotNull(testObserver.value().data)
        assertEquals(4, testObserver.value().data?.size)

        tearDown()
    }

    @Test
    fun testRefreshTransactionCategories() {
        initSetup()

        val body = readStringFromJson(app, R.raw.transaction_categories_valid)
        mockServer.setDispatcher(object: Dispatcher() {
            override fun dispatch(request: RecordedRequest?): MockResponse {
                if (request?.trimmedPath == AggregationAPI.URL_TRANSACTION_CATEGORIES) {
                    return MockResponse()
                            .setResponseCode(200)
                            .setBody(body)
                }
                return MockResponse().setResponseCode(404)
            }
        })

        aggregation.refreshTransactionCategories { result ->
            assertEquals(Result.Status.SUCCESS, result.status)
            assertNull(result.error)

            val testObserver = aggregation.fetchTransactionCategories().test()
            testObserver.awaitValue()
            val models = testObserver.value().data
            assertNotNull(models)
            assertEquals(43, models?.size)
        }

        val request = mockServer.takeRequest()
        assertEquals(AggregationAPI.URL_TRANSACTION_CATEGORIES, request.trimmedPath)

        wait(3)

        tearDown()
    }

    // Merchant Tests

    @Test
    fun testFetchMerchantByID() {
        initSetup()

        val data = testMerchantResponseData()
        val list = mutableListOf(testMerchantResponseData(), data, testMerchantResponseData())
        database.merchants().insertAll(*list.map { it.toMerchant() }.toList().toTypedArray())

        val testObserver = aggregation.fetchMerchant(data.merchantId).test()
        testObserver.awaitValue()
        assertNotNull(testObserver.value().data)
        assertEquals(data.merchantId, testObserver.value().data?.merchantId)

        tearDown()
    }

    @Test
    fun testFetchMerchants() {
        initSetup()

        val data1 = testMerchantResponseData()
        val data2 = testMerchantResponseData()
        val data3 = testMerchantResponseData()
        val data4 = testMerchantResponseData()
        val list = mutableListOf(data1, data2, data3, data4)

        database.merchants().insertAll(*list.map { it.toMerchant() }.toList().toTypedArray())

        val testObserver = aggregation.fetchMerchants().test()
        testObserver.awaitValue()
        assertNotNull(testObserver.value().data)
        assertEquals(4, testObserver.value().data?.size)

        tearDown()
    }

    @Test
    fun testRefreshMerchants() {
        initSetup()

        val body = readStringFromJson(app, R.raw.merchants_valid)
        mockServer.setDispatcher(object: Dispatcher() {
            override fun dispatch(request: RecordedRequest?): MockResponse {
                if (request?.trimmedPath == AggregationAPI.URL_MERCHANTS) {
                    return MockResponse()
                            .setResponseCode(200)
                            .setBody(body)
                }
                return MockResponse().setResponseCode(404)
            }
        })

        aggregation.refreshMerchants { result ->
            assertEquals(Result.Status.SUCCESS, result.status)
            assertNull(result.error)

            val testObserver = aggregation.fetchMerchants().test()
            testObserver.awaitValue()
            val models = testObserver.value().data
            assertNotNull(models)
            assertEquals(1200, models?.size)
        }

        val request = mockServer.takeRequest()
        assertEquals(AggregationAPI.URL_MERCHANTS, request.trimmedPath)

        wait(3)

        tearDown()
    }

    @Test
    fun testRefreshMerchantByID() {
        initSetup()

        val body = readStringFromJson(app, R.raw.merchant_id_197)
        mockServer.setDispatcher(object: Dispatcher() {
            override fun dispatch(request: RecordedRequest?): MockResponse {
                if (request?.trimmedPath == "aggregation/merchants/197") {
                    return MockResponse()
                            .setResponseCode(200)
                            .setBody(body)
                }
                return MockResponse().setResponseCode(404)
            }
        })

        aggregation.refreshMerchant(197L) { result ->
            assertEquals(Result.Status.SUCCESS, result.status)
            assertNull(result.error)

            val testObserver = aggregation.fetchMerchant(197L).test()
            testObserver.awaitValue()
            val model = testObserver.value().data
            assertNotNull(model)
            assertEquals(197L, model?.merchantId)
        }

        val request = mockServer.takeRequest()
        assertEquals("aggregation/merchants/197", request.trimmedPath)

        wait(3)

        tearDown()
    }

    @Test
    fun testRefreshMerchantsByIds() {
        initSetup()

        val body = readStringFromJson(app, R.raw.merchants_by_id)
        mockServer.setDispatcher(object: Dispatcher() {
            override fun dispatch(request: RecordedRequest?): MockResponse {
                if (request?.trimmedPath == "${AggregationAPI.URL_MERCHANTS}?merchant_ids=22,30,31,106,691") {
                    return MockResponse()
                            .setResponseCode(200)
                            .setBody(body)
                }
                return MockResponse().setResponseCode(404)
            }
        })

        aggregation.refreshMerchants(longArrayOf(22, 30, 31, 106, 691)) { result ->
            assertEquals(Result.Status.SUCCESS, result.status)
            assertNull(result.error)

            val testObserver = aggregation.fetchMerchants().test()
            testObserver.awaitValue()
            val models = testObserver.value().data
            assertNotNull(models)
            assertEquals(5, models?.size)
        }

        val request = mockServer.takeRequest()
        assertEquals("${AggregationAPI.URL_MERCHANTS}?merchant_ids=22,30,31,106,691", request.trimmedPath)

        wait(3)

        tearDown()
    }

    @Test
    fun testLinkingRemoveCachedCascade() {
        initSetup()

        val body = readStringFromJson(app, R.raw.providers_valid)
        mockServer.setDispatcher(object: Dispatcher() {
            override fun dispatch(request: RecordedRequest?): MockResponse {
                if (request?.trimmedPath == AggregationAPI.URL_PROVIDERS) {
                    return MockResponse()
                            .setResponseCode(200)
                            .setBody(body)
                }
                return MockResponse().setResponseCode(404)
            }
        })

        database.providers().insert(testProviderResponseData(providerId = 123).toProvider())
        database.providerAccounts().insert(testProviderAccountResponseData(providerAccountId = 234, providerId = 123).toProviderAccount())
        database.accounts().insert(testAccountResponseData(accountId = 345, providerAccountId = 234).toAccount())
        database.transactions().insert(testTransactionResponseData(transactionId = 456, accountId = 345).toTransaction())

        val testObserver1 = aggregation.fetchProvider(providerId = 123).test()
        testObserver1.awaitValue()
        assertEquals(123L, testObserver1.value().data?.providerId)

        val testObserver2 = aggregation.fetchProviderAccount(providerAccountId = 234).test()
        testObserver2.awaitValue()
        assertEquals(234L, testObserver2.value().data?.providerAccountId)

        val testObserver3 = aggregation.fetchAccount(accountId = 345).test()
        testObserver3.awaitValue()
        assertEquals(345L, testObserver3.value().data?.accountId)

        val testObserver4 = aggregation.fetchTransaction(transactionId = 456).test()
        testObserver4.awaitValue()
        assertEquals(456L, testObserver4.value().data?.transactionId)

        aggregation.refreshProviders { result ->
            assertEquals(Result.Status.SUCCESS, result.status)
            assertNull(result.error)

            val testObserver5 = aggregation.fetchProviders().test()
            testObserver5.awaitValue()
            val models = testObserver5.value().data
            assertNotNull(models)
            assertEquals(311, models?.size)

            val testObserver6 = aggregation.fetchProvider(providerId = 123).test()
            testObserver6.awaitValue()
            assertNull(testObserver6.value().data)

            val testObserver7 = aggregation.fetchProviderAccount(providerAccountId = 234).test()
            testObserver7.awaitValue()
            assertNull(testObserver7.value().data)

            val testObserver8 = aggregation.fetchAccount(accountId = 345).test()
            testObserver8.awaitValue()
            assertNull(testObserver8.value().data)

            val testObserver9 = aggregation.fetchTransaction(transactionId = 456).test()
            testObserver9.awaitValue()
            assertNull(testObserver9.value().data)
        }

        val request = mockServer.takeRequest()
        assertEquals(AggregationAPI.URL_PROVIDERS, request.trimmedPath)

        wait(3)

        tearDown()
    }
}