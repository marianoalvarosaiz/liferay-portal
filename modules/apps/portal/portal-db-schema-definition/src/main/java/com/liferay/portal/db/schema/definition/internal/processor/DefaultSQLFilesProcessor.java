/**
 * SPDX-FileCopyrightText: (c) 2024 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.portal.db.schema.definition.internal.processor;

import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.dao.db.DBType;
import com.liferay.portal.kernel.instance.PortalInstancePool;
import com.liferay.portal.kernel.util.FileUtil;

import java.io.File;

/**
 * @author Mariano Álvaro Sáiz
 */
public class DefaultSQLFilesProcessor extends SQLFilesProcessor {

	public DefaultSQLFilesProcessor(DBType dbType) throws Exception {
		super(dbType);

		_objectsSQLHelper = new ObjectsSQLHelper(
			db, PortalInstancePool.getDefaultCompanyId());
	}

	@Override
	public void writeFiles(File file) throws Exception {
		FileUtil.write(new File(file, "indexes.sql"), getIndexesSQL());

		FileUtil.write(new File(file, "indexes.sql"), getTablesSQL());
	}

	@Override
	protected String getIndexesSQL() {
		return super.getIndexesSQL() + StringPool.NEW_LINE +
			_objectsSQLHelper.getIndexesSQL();
	}

	@Override
	protected String getTablesSQL() {
		return super.getTablesSQL() + StringPool.NEW_LINE +
			_objectsSQLHelper.getTablesSQL();
	}

	private final ObjectsSQLHelper _objectsSQLHelper;

}