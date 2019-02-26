package us.frollo.frollosdk.network.api

import retrofit2.Call
import retrofit2.http.*
import us.frollo.frollosdk.network.NetworkHelper.Companion.API_VERSION_PATH
import us.frollo.frollosdk.model.api.device.DeviceUpdateRequest
import us.frollo.frollosdk.model.api.device.LogRequest

internal interface DeviceAPI {
    companion object {
        const val URL_DEVICE = "$API_VERSION_PATH/device/"
        const val URL_LOG = "$API_VERSION_PATH/device/log/"
    }

    @PUT(URL_DEVICE)
    fun updateDevice(@Body request: DeviceUpdateRequest): Call<Void>

    @POST(URL_LOG)
    fun createLog(@Body request: LogRequest): Call<Void>
}