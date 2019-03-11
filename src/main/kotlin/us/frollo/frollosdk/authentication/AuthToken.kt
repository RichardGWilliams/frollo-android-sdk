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

package us.frollo.frollosdk.authentication

import org.threeten.bp.LocalDateTime
import org.threeten.bp.ZoneOffset
import us.frollo.frollosdk.keystore.Keystore
import us.frollo.frollosdk.model.oauth.OAuthTokenResponse
import us.frollo.frollosdk.preferences.Preferences

internal class AuthToken(private val keystore: Keystore, private val pref: Preferences) {

    companion object {
        private var accessToken: String? = null
        private var refreshToken: String? = null
        private var accessTokenExpiry: Long = -1
    }

    fun getAccessToken(): String? {
        if (accessToken == null)
            accessToken = keystore.decrypt(pref.encryptedAccessToken)
        return accessToken
    }

    fun getRefreshToken(): String? {
        if (refreshToken == null)
            refreshToken = keystore.decrypt(pref.encryptedRefreshToken)
        return refreshToken
    }

    fun getAccessTokenExpiry(): Long {
        if (accessTokenExpiry == -1L)
            accessTokenExpiry = pref.accessTokenExpiry
        return accessTokenExpiry
    }

    fun saveTokens(tokenResponse: OAuthTokenResponse) {
        accessToken = tokenResponse.accessToken
        pref.encryptedAccessToken = keystore.encrypt(accessToken)

        // With OAuth you get refresh token only the very first time and it is for lifetime.
        // Subsequent token refresh responses does not contain refresh token.
        // Hence hold on to the old refresh token if the response does not has a refresh token.
        tokenResponse.refreshToken?.let {
            refreshToken = it
            pref.encryptedRefreshToken = keystore.encrypt(it)
        }

        val createdAt = LocalDateTime.ofEpochSecond(tokenResponse.createdAt, 0, ZoneOffset.UTC) ?: LocalDateTime.now(ZoneOffset.UTC)
        val tokenExpiry = createdAt.plusSeconds(tokenResponse.expiresIn).toEpochSecond(ZoneOffset.UTC)

        accessTokenExpiry = tokenExpiry
        pref.accessTokenExpiry = tokenExpiry
    }

    fun clearTokens() {
        accessToken = null
        pref.resetEncryptedAccessToken()
        refreshToken = null
        pref.resetEncryptedRefreshToken()
        accessTokenExpiry = -1
        pref.resetAccessTokenExpiry()
    }
}