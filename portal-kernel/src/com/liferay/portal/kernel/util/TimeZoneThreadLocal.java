/**
 * SPDX-FileCopyrightText: (c) 2000 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.portal.kernel.util;

import com.liferay.petra.lang.CentralizedThreadLocal;
import com.liferay.petra.lang.SafeCloseable;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.security.auth.CompanyThreadLocal;

import java.util.TimeZone;

/**
 * @author Brian Wing Shun Chan
 */
public class TimeZoneThreadLocal {

	public static TimeZone getDefaultTimeZone() {
		return _defaultTimeZone.get();
	}

	public static TimeZone getThemeDisplayTimeZone() {
		return _themeDisplayTimeZone.get();
	}

	public static void removeDefaultTimeZone() {
		_defaultTimeZone.remove();
	}

	public static SafeCloseable removeDefaultTimeZoneWithSafeCloseable() {
		SafeCloseable safeCloseable = () -> {
		};

		if (_initializedDefaultTimeZone.get()) {
			TimeZone defaultTimeZone = _defaultTimeZone.get();

			safeCloseable = () -> _defaultTimeZone.set(defaultTimeZone);
		}

		_defaultTimeZone.remove();

		return safeCloseable;
	}

	public static void setDefaultTimeZone(TimeZone timeZone) {
		_defaultTimeZone.set(timeZone);
	}

	public static void setThemeDisplayTimeZone(TimeZone timeZone) {
		_themeDisplayTimeZone.set(timeZone);
	}

	private static final CentralizedThreadLocal<TimeZone> _defaultTimeZone =
		new CentralizedThreadLocal<TimeZone>(
			LocaleThreadLocal.class + "._defaultTimeZone",
			() -> {
				User guestUser = CompanyThreadLocal.fetchGuestUser();

				if (guestUser == null) {
					return null;
				}

				return guestUser.getTimeZone();
			}) {

			@Override
			public void remove() {
				_initializedDefaultTimeZone.set(Boolean.FALSE);

				super.remove();
			}

			@Override
			public void set(TimeZone timeZone) {
				_initializedDefaultTimeZone.set(Boolean.TRUE);

				super.set(timeZone);
			}

		};

	private static final ThreadLocal<Boolean> _initializedDefaultTimeZone =
		new CentralizedThreadLocal<>(
			LocaleThreadLocal.class + "._initializedDefaultTimeZone",
			() -> Boolean.FALSE);
	private static final ThreadLocal<TimeZone> _themeDisplayTimeZone =
		new CentralizedThreadLocal<>(
			TimeZoneThreadLocal.class + "._themeDisplayTimeZone");

}