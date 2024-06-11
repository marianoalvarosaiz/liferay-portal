/**
 * SPDX-FileCopyrightText: (c) 2024 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.portal.db.schema.info.internal.processor;

import com.liferay.object.service.ObjectDefinitionLocalService;
import com.liferay.object.service.ObjectFieldLocalService;
import com.liferay.object.service.ObjectRelationshipLocalService;
import com.liferay.portal.db.schema.info.internal.sql.SQLRecorder;
import com.liferay.portal.kernel.dao.db.DBType;
import com.liferay.portal.kernel.util.FileUtil;

import java.io.File;

/**
 * @author Mariano Álvaro Sáiz
 */
public class DBSchemaToFilesProcessor {

	public DBSchemaToFilesProcessor(
		DBType dbType,
		ObjectDefinitionLocalService objectDefinitionLocalService,
		ObjectFieldLocalService objectFieldLocalService,
		ObjectRelationshipLocalService objectRelationshipLocalService) {

		_dbType = dbType;
		_objectDefinitionLocalService = objectDefinitionLocalService;
		_objectFieldLocalService = objectFieldLocalService;
		_objectRelationshipLocalService = objectRelationshipLocalService;
	}

	public void processTo(String path) throws Exception {
		SQLRecorder sqlRecorder = new SQLRecorder();

		new DBSchemaToSQLProcessor(
			_dbType, sqlRecorder
		).process();

		new DBObjectsSchemaToSQLProcessor(
			_dbType, _objectDefinitionLocalService, _objectFieldLocalService,
			_objectRelationshipLocalService, sqlRecorder
		).process();

		FileUtil.write(
			new File(path, "indexes" + _DEFAULT_EXTENSION),
			sqlRecorder.getIndexesSQL());
		FileUtil.write(
			new File(path, "sequences" + _DEFAULT_EXTENSION),
			sqlRecorder.getSequencesSQL());
		FileUtil.write(
			new File(path, "tables" + _DEFAULT_EXTENSION),
			sqlRecorder.getTablesSQL());
	}

	private static final String _DEFAULT_EXTENSION = ".sql";

	private final DBType _dbType;
	private final ObjectDefinitionLocalService _objectDefinitionLocalService;
	private final ObjectFieldLocalService _objectFieldLocalService;
	private final ObjectRelationshipLocalService
		_objectRelationshipLocalService;

}