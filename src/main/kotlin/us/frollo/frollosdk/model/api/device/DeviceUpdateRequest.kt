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

package us.frollo.frollosdk.model.api.device

import com.google.gson.annotations.SerializedName

internal data class DeviceUpdateRequest(
        @SerializedName("device_id") val deviceId: String,
        @SerializedName("device_type") val deviceType: String,
        @SerializedName("device_name") var deviceName: String,
        @SerializedName("notification_token") var notificationToken: String?,
        @SerializedName("timezone") var timezone: String?,
        @SerializedName("compliant") var compliant: Boolean?
)