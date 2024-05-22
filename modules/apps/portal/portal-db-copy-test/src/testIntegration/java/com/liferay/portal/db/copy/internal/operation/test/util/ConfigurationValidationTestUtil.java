/**
 * SPDX-FileCopyrightText: (c) 2024 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.portal.db.copy.internal.operation.test.util;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.portal.db.copy.internal.operation.test.helper.ConfigurationTestHelper;

import org.junit.Assert;
import org.junit.runner.RunWith;

/**
 * @author Mariano Álvaro Sáiz
 */
@RunWith(Arquillian.class)
public class ConfigurationValidationTestUtil {

	public static void assertConfigurationIsDeletedAfterDeploy(
			ConfigurationTestHelper configurationTestHelper, String pid)
		throws Exception {

		Assert.assertTrue(configurationTestHelper.isConfigurationFileDeleted());
		Assert.assertTrue(configurationTestHelper.isDictionaryNull(pid));
		Assert.assertTrue(
			configurationTestHelper.isListConfigurationsNull(pid));
	}

}