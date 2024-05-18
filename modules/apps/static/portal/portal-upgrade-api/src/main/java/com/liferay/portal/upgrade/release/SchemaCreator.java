/**
 * SPDX-FileCopyrightText: (c) 2023 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.portal.upgrade.release;

import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.upgrade.UpgradeException;

import javax.sql.DataSource;

/**
 * @author Shuyang Zhou
 */
public interface SchemaCreator {

	public void create() throws UpgradeException;

	public void createOn(DataSource externalDataSource) throws PortalException;

	public String getBundleSymbolicName();

	public String getSchemaVersion();

}