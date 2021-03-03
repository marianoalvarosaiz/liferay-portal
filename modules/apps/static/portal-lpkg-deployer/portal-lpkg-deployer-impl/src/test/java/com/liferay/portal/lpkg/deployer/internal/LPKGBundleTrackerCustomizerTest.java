/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.portal.lpkg.deployer.internal;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author Mariano Álvaro Sáiz
 */
public class LPKGBundleTrackerCustomizerTest {

	@Test
	public void testExtractFileNameFromHotfix() throws Exception {
		String fileName =
			"com.liferay.reading.time.api-3.0.7.hotfix-22-7210.jar";

		String extracted = LPKGBundleTrackerCustomizer.extractFileName(
			fileName);

		Assert.assertEquals("com.liferay.reading.time.api.jar", extracted);
	}

	@Test
	public void testExtractFileNameFromJar() throws Exception {
		String fileName = "com.liferay.reading.time.api-1.0.19.jar";

		String extracted = LPKGBundleTrackerCustomizer.extractFileName(
			fileName);

		Assert.assertEquals("com.liferay.reading.time.api.jar", extracted);
	}

	@Test
	public void testExtractFileNameFromLPKG() throws Exception {
		String fileName =
			"file:/opt/liferay-dxp/osgi/marketplace/Liferay Collaboration - " +
				"Liferay Reading Time - API.lpkg!" +
					"/com.liferay.reading.time.api-1.0.19.jar";

		String extracted = LPKGBundleTrackerCustomizer.extractFileName(
			fileName);

		Assert.assertEquals("com.liferay.reading.time.api.jar", extracted);
	}

}