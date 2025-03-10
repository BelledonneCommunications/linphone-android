/*
 * Copyright (c) 2010-2024 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.ui

import androidx.annotation.FontRes
import org.linphone.R

enum class NotoSansFont(@FontRes val fontRes: Int) {
    // NotoSansLight(R.font.noto_sans_light), // 300
    NotoSansRegular(R.font.noto_sans_regular), // 400
    NotoSansMedium(R.font.noto_sans_medium), // 500
    // NotoSansSemiBold(R.font.noto_sans_semi_bold), // 600
    NotoSansBold(R.font.noto_sans_bold), // 700
    NotoSansExtraBold(R.font.noto_sans_extra_bold) // 800
}
