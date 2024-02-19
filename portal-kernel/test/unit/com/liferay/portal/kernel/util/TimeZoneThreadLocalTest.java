/**
 * SPDX-FileCopyrightText: (c) 2024 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.portal.kernel.util;

import com.liferay.petra.lang.CentralizedThreadLocal;
import com.liferay.petra.lang.SafeCloseable;
import com.liferay.portal.kernel.test.ReflectionTestUtil;

import java.util.TimeZone;
import java.util.function.Supplier;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author Mariano Álvaro Sáiz
 */
public class TimeZoneThreadLocalTest {

	@Test
	public void testRemoveDefaultTimeZoneWithSafeCloseable() {
		TimeZone initialValue = TimeZone.getTimeZone("GMT");

		TimeZone modifiedValue = TimeZone.getTimeZone("PST");

		TimeZoneThreadLocal.setDefaultTimeZone(modifiedValue);

		CentralizedThreadLocal<TimeZone> centralizedThreadLocal =
			ReflectionTestUtil.getFieldValue(
				TimeZoneThreadLocal.class, "_defaultTimeZone");

		ReflectionTestUtil.setFieldValue(
			centralizedThreadLocal, "_supplier",
			(Supplier<TimeZone>)() -> initialValue);

		try (SafeCloseable safeCloseable =
				TimeZoneThreadLocal.removeDefaultTimeZoneWithSafeCloseable()) {

			Assert.assertSame(
				initialValue, TimeZoneThreadLocal.getDefaultTimeZone());
		}

		Assert.assertSame(
			modifiedValue, TimeZoneThreadLocal.getDefaultTimeZone());
	}

}