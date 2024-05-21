/**
 * SPDX-FileCopyrightText: (c) 2024 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.portal.db.copy.internal.jdbc;

import com.liferay.petra.string.StringPool;
import com.liferay.portal.db.copy.internal.configuration.DBCopyConfiguration;
import com.liferay.portal.kernel.dao.jdbc.DataSourceFactoryUtil;

import javax.sql.DataSource;

/**
 * @author Mariano Álvaro Sáiz
 */
public class DBCopyDataSourceFactoryUtil {

	public static DataSource getDataSource(
			DBCopyConfiguration dBCopyConfiguration)
		throws Exception {

		return DataSourceFactoryUtil.initDataSource(
			dBCopyConfiguration.driverClassName(), dBCopyConfiguration.url(),
			dBCopyConfiguration.userName(), dBCopyConfiguration.password(),
			StringPool.BLANK);
	}

}