/**
 * SPDX-FileCopyrightText: (c) 2024 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.portal.db.copy.internal.operation;

import com.liferay.portal.configuration.metatype.bnd.util.ConfigurableUtil;
import com.liferay.portal.db.copy.internal.LiferayDBDuplicator;
import com.liferay.portal.db.copy.internal.configuration.DBCopyConfiguration;
import com.liferay.portal.db.copy.internal.jdbc.DBCopyDataSourceFactoryUtil;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.util.PropsValues;

import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Paths;

import java.util.Map;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;

/**
 * @author Mariano Álvaro Sáiz
 */
@Component(
	configurationPid = "com.liferay.portal.db.copy.internal.configuration.DBCopyConfiguration",
	configurationPolicy = ConfigurationPolicy.REQUIRE, service = {}
)
public class DBCopyOperation {

	@Activate
	protected void activate(Map<String, Object> properties) {
		_performCopy(properties);
	}

	private void _deleteConfiguration(String pid) {
		try {
			Files.deleteIfExists(
				Paths.get(
					PropsValues.MODULE_FRAMEWORK_CONFIGS_DIR,
					pid.concat(".config")));
		}
		catch (IOException ioException) {
			_log.error(ioException);
		}
	}

	private void _performCopy(Map<String, Object> properties) {
		if (_log.isInfoEnabled()) {
			_log.info("Starting database copy");
		}

		try {
			LiferayDBDuplicator.copyTo(
				DBCopyDataSourceFactoryUtil.getDataSource(
					ConfigurableUtil.createConfigurable(
						DBCopyConfiguration.class, properties)));

			if (_log.isInfoEnabled()) {
				_log.info("Database copy finished");
			}
		}
		catch (Exception exception) {
			_log.error("Unable to perform database copy", exception);
		}
		finally {
			_deleteConfiguration((String)properties.get("service.pid"));
		}
	}

	private static final Log _log = LogFactoryUtil.getLog(
		DBCopyOperation.class);

}