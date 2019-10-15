/*
 * Copyright (c) 2010-2019 Belledonne Communications SARL.
 *
 * This file is part of Linphone Android
 * (see https://gitlab.linphone.org/BC/public/linphone-android).
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
package org.linphone.settings.widget;

interface SettingListener {
    void onClicked();

    void onTextValueChanged(String newValue);

    void onBoolValueChanged(boolean newValue);

    void onListValueChanged(int position, String newLabel, String newValue);
}
