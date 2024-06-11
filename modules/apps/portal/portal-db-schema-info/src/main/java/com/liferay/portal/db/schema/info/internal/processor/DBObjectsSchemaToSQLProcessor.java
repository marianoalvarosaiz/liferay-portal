/**
 * SPDX-FileCopyrightText: (c) 2024 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.portal.db.schema.info.internal.processor;

import com.liferay.object.constants.ObjectRelationshipConstants;
import com.liferay.object.model.ObjectDefinition;
import com.liferay.object.model.ObjectRelationship;
import com.liferay.object.petra.sql.dsl.DynamicObjectDefinitionLocalizationTable;
import com.liferay.object.petra.sql.dsl.DynamicObjectDefinitionLocalizationTableFactory;
import com.liferay.object.petra.sql.dsl.DynamicObjectDefinitionTable;
import com.liferay.object.petra.sql.dsl.DynamicObjectDefinitionTableFactory;
import com.liferay.object.petra.sql.dsl.DynamicObjectRelationshipMappingTable;
import com.liferay.object.relationship.util.ObjectRelationshipUtil;
import com.liferay.object.service.ObjectDefinitionLocalService;
import com.liferay.object.service.ObjectFieldLocalService;
import com.liferay.object.service.ObjectRelationshipLocalService;
import com.liferay.portal.db.schema.info.internal.sql.FakeDBFactory;
import com.liferay.portal.db.schema.info.internal.sql.SQLRecorder;
import com.liferay.portal.kernel.dao.db.DB;
import com.liferay.portal.kernel.dao.db.DBType;
import com.liferay.portal.kernel.dao.db.IndexMetadata;
import com.liferay.portal.kernel.instance.PortalInstancePool;
import com.liferay.portal.kernel.util.InfrastructureUtil;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.workflow.WorkflowConstants;

import java.lang.reflect.Method;

import java.sql.Connection;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

/**
 * @author Mariano Álvaro Sáiz
 */
public class DBObjectsSchemaToSQLProcessor {

	public DBObjectsSchemaToSQLProcessor(
		DBType dbType,
		ObjectDefinitionLocalService objectDefinitionLocalService,
		ObjectFieldLocalService objectFieldLocalService,
		ObjectRelationshipLocalService objectRelationshipLocalService,
		SQLRecorder sqlRecorder) {

		_objectDefinitionLocalService = objectDefinitionLocalService;
		_objectFieldLocalService = objectFieldLocalService;
		_objectRelationshipLocalService = objectRelationshipLocalService;
		_sqlRecorder = sqlRecorder;

		_fakeDB = FakeDBFactory.getDB(dbType);
	}

	public void process() throws Exception {
		List<ObjectDefinition> objectDefinitions =
			_objectDefinitionLocalService.getObjectDefinitions(
				PortalInstancePool.getDefaultCompanyId(),
				WorkflowConstants.STATUS_APPROVED);

		for (ObjectDefinition objectDefinition : objectDefinitions) {
			_generateRegularTables(objectDefinition);

			_generateRelationshipTables(objectDefinition);
		}

		_generateIndexesSQL();
	}

	private void _generateIndexesSQL() throws Exception {
		DataSource dataSource = InfrastructureUtil.getDataSource();

		Method method = _getMethod(
			_fakeDB.getClass(), "getIndexes", Connection.class, String.class,
			String.class, boolean.class);

		try (Connection connection = dataSource.getConnection()) {
			for (String tableName : _tableNames) {
				for (IndexMetadata indexMetadata :
						(List<IndexMetadata>)method.invoke(
							_fakeDB, connection, tableName, null, false)) {

					_sqlRecorder.recordIndexesSQL(
						_fakeDB.buildSQL(indexMetadata.getCreateSQL(null)));
				}
			}
		}
	}

	private void _generateRegularTables(ObjectDefinition objectDefinition)
		throws Exception {

		DynamicObjectDefinitionLocalizationTable
			dynamicObjectDefinitionLocalizationTable =
				DynamicObjectDefinitionLocalizationTableFactory.create(
					objectDefinition, _objectFieldLocalService);

		if (dynamicObjectDefinitionLocalizationTable != null) {
			_recordTableSQL(
				dynamicObjectDefinitionLocalizationTable.getCreateTableSQL(),
				dynamicObjectDefinitionLocalizationTable.getTableName());
		}

		if (!objectDefinition.isUnmodifiableSystemObject()) {
			DynamicObjectDefinitionTable dynamicObjectDefinitionTable =
				DynamicObjectDefinitionTableFactory.create(
					objectDefinition, _objectFieldLocalService);

			_recordTableSQL(
				dynamicObjectDefinitionTable.getCreateTableSQL(),
				dynamicObjectDefinitionTable.getTableName());
		}

		DynamicObjectDefinitionTable dynamicObjectDefinitionTable =
			DynamicObjectDefinitionTableFactory.createExtensionTable(
				objectDefinition, _objectFieldLocalService);

		_recordTableSQL(
			dynamicObjectDefinitionTable.getCreateTableSQL(),
			dynamicObjectDefinitionTable.getTableName());
	}

	private void _generateRelationshipTables(ObjectDefinition objectDefinition)
		throws Exception {

		List<ObjectRelationship> objectRelationships =
			_objectRelationshipLocalService.getAllObjectRelationships(
				objectDefinition.getObjectDefinitionId());

		for (ObjectRelationship objectRelationship : objectRelationships) {
			if (!StringUtil.equalsIgnoreCase(
					objectRelationship.getType(),
					ObjectRelationshipConstants.TYPE_MANY_TO_MANY) ||
				_tableNames.contains(objectRelationship.getDBTableName())) {

				continue;
			}

			Map<String, String> pkObjectFieldDBColumnNames =
				ObjectRelationshipUtil.getPKObjectFieldDBColumnNames(
					_objectDefinitionLocalService.getObjectDefinition(
						objectRelationship.getObjectDefinitionId1()),
					_objectDefinitionLocalService.getObjectDefinition(
						objectRelationship.getObjectDefinitionId2()),
					false);

			String pkObjectFieldDBColumnName1 = pkObjectFieldDBColumnNames.get(
				"pkObjectFieldDBColumnName1");
			String pkObjectFieldDBColumnName2 = pkObjectFieldDBColumnNames.get(
				"pkObjectFieldDBColumnName2");

			DynamicObjectRelationshipMappingTable
				dynamicObjectRelationshipMappingTable =
					new DynamicObjectRelationshipMappingTable(
						pkObjectFieldDBColumnName1, pkObjectFieldDBColumnName2,
						objectRelationship.getDBTableName());

			_recordTableSQL(
				dynamicObjectRelationshipMappingTable.getCreateTableSQL(),
				dynamicObjectRelationshipMappingTable.getTableName());
		}
	}

	private Method _getMethod(
		Class<?> clazz, String methodName, Class<?>... parameterTypes) {

		while ((clazz != null) && (clazz != Object.class)) {
			try {
				Method method = clazz.getDeclaredMethod(
					methodName, parameterTypes);

				method.setAccessible(true);

				return method;
			}
			catch (NoSuchMethodException noSuchMethodException) {
				clazz = clazz.getSuperclass();
			}
		}

		return null;
	}

	private void _recordTableSQL(String sql, String tableName)
		throws Exception {

		_sqlRecorder.recordTablesSQL(_fakeDB.buildSQL(sql));
		_tableNames.add(tableName);
	}

	private final DB _fakeDB;
	private final ObjectDefinitionLocalService _objectDefinitionLocalService;
	private final ObjectFieldLocalService _objectFieldLocalService;
	private final ObjectRelationshipLocalService
		_objectRelationshipLocalService;
	private final SQLRecorder _sqlRecorder;
	private final Set<String> _tableNames = new HashSet<>();

}