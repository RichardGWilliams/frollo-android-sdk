/*
 * Copyright Mark McAvoy - www.bitethebullet.co.uk 2009
 *
 * This file is part of Android Token.
 *
 * Android Token is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Android Token is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Android Token.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package us.frollo.frollosdk.authentication.otp

internal interface IToken {
    fun getName(): String

    fun getSerialNumber(): String

    fun getTokenType(): Int

    fun generateOtp(): String

    fun getId(): Long

    fun setId(id: Long)

    fun getTimeStep(): Int
}