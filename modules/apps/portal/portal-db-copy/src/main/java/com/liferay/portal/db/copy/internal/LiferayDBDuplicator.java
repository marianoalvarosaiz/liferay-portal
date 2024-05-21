/**
 * SPDX-FileCopyrightText: (c) 2024 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.portal.db.copy.internal;

import com.liferay.portal.db.copy.internal.ddl.LiferayDBStructureDuplicator;

import javax.sql.DataSource;

/**
 * @author Mariano Álvaro Sáiz
 */
public class LiferayDBDuplicator {

	public static void copyTo(DataSource targetDataSource) throws Exception {
		new LiferayDBStructureDuplicator(
			targetDataSource
		).copyTo(
			targetDataSource
		);
	}

}