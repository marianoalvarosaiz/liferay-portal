/**
 * SPDX-FileCopyrightText: (c) 2000 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.portal.kernel.log;

import com.liferay.portal.kernel.internal.log4j.Log4jLogFactoryImpl;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author Brian Wing Shun Chan
 * @author Shuyang Zhou
 * @author Raymond Augé
 */
public class LogFactoryUtil {

	public static Log getLog(Class<?> c) {
		return getLog(c.getName());
	}

	public static Log getLog(String name) {

		// The following concurrent collection retrieve has the side effect of a
		// memory fence read. This will invalidate all dirty cache data if there
		// are any. If the LogWrapper swap happens before this, the new Log will
		// be visible to the current Thread.

		LogWrapper logWrapper = _logWrappers.get(name);

		if (logWrapper == null) {
			Log log = _logFactory.getLog(name);

			if (SanitizerLogWrapper.isEnabled()) {
				logWrapper = new SanitizerLogWrapper(log);
			}
			else if (log instanceof LogWrapper) {
				logWrapper = (LogWrapper)log;
			}
			else {
				logWrapper = new LogWrapper(_logFactory.getLog(name));
			}

			LogWrapper previousLogWrapper = _logWrappers.putIfAbsent(
				name, logWrapper);

			if (previousLogWrapper != null) {
				logWrapper = previousLogWrapper;
			}
		}

		return logWrapper;
	}

	public static LogFactory getLogFactory() {
		return _logFactory;
	}

	private static final LogFactory _logFactory = new Log4jLogFactoryImpl();
	private static final ConcurrentMap<String, LogWrapper> _logWrappers =
		new ConcurrentHashMap<>();

}