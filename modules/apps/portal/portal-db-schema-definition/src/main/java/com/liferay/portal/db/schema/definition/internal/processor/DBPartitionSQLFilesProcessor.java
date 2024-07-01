/**
 * SPDX-FileCopyrightText: (c) 2024 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.portal.db.schema.definition.internal.processor;

import com.liferay.petra.string.CharPool;
import com.liferay.petra.string.StringBundler;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.dao.db.DBInspector;
import com.liferay.portal.kernel.dao.db.DBType;
import com.liferay.portal.kernel.instance.PortalInstancePool;
import com.liferay.portal.kernel.service.CompanyLocalServiceUtil;
import com.liferay.portal.kernel.util.FileUtil;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.InfrastructureUtil;
import com.liferay.portal.kernel.util.PropsUtil;
import com.liferay.portal.kernel.util.StringUtil;

import java.io.File;

import java.sql.Connection;

import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

/**
 * @author Mariano Álvaro Sáiz
 */
public class DBPartitionSQLFilesProcessor extends SQLFilesProcessor {

	public DBPartitionSQLFilesProcessor(DBType dbType) throws Exception {
		super(dbType);
	}

	@Override
	public void writeFiles(File file) throws Exception {
		String indexesSQL = getIndexesSQL();
		String tablesSQL = getTablesSQL();

		_writeDefaultPartitionFiles(file, indexesSQL, tablesSQL);
		_writePartitionFiles(file, indexesSQL, tablesSQL);
	}

	private String _getCreatePartitionSQL(long companyId) {
		if (companyId == PortalInstancePool.getDefaultCompanyId()) {
			return StringPool.NEW_LINE;
		}

		if (db.getDBType() == DBType.MYSQL) {
			return StringBundler.concat(
				"create schema if not exists ",
				_DATABASE_PARTITION_SCHEMA_NAME_PREFIX, companyId,
				" character set utf8;", StringPool.NEW_LINE);
		}

		return StringBundler.concat(
			"create schema if not exists ",
			_DATABASE_PARTITION_SCHEMA_NAME_PREFIX, companyId,
			StringPool.SEMICOLON, StringPool.NEW_LINE);
	}

	private String _getViewsSQL(
		long companyId, List<String> controlTableNames) {

		StringBundler sb = new StringBundler();

		String partitionName =
			_DATABASE_PARTITION_SCHEMA_NAME_PREFIX + companyId;

		for (String controlTableName : controlTableNames) {
			sb.append(
				StringBundler.concat(
					"create or replace view ", partitionName, StringPool.PERIOD,
					controlTableName, " as select * from ", controlTableName,
					StringPool.SEMICOLON, StringPool.NEW_LINE));
		}

		return sb.toString();
	}

	private List<String> _removeControlTables(StringBundler tablesSQLSB)
		throws Exception {

		String[] createTableSQLs = StringUtil.split(
			tablesSQLSB.toString(), CharPool.SEMICOLON);

		List<String> controlTables = new ArrayList<>();

		tablesSQLSB.setIndex(0);

		try (Connection connection = _dataSource.getConnection()) {
			DBInspector dbInspector = new DBInspector(connection);

			for (String createTableSQL : createTableSQLs) {
				createTableSQL = StringUtil.trim(createTableSQL);

				if (StringUtil.startsWith(createTableSQL, "create table")) {
					String[] parts = createTableSQL.split(StringPool.SPACE);

					if (dbInspector.isControlTable(parts[2])) {
						controlTables.add(parts[2]);

						continue;
					}
				}

				tablesSQLSB.append(
					createTableSQL + StringPool.SEMICOLON +
						StringPool.NEW_LINE);
			}
		}

		return controlTables;
	}

	private String _removeControlTablesIndexes(
		String indexesSQL, List<String> controlTableNames) {

		StringBundler sb = new StringBundler();

		List<String> regexControlTableNames = new ArrayList<>(
			controlTableNames.size());

		for (String controlTableName : controlTableNames) {
			regexControlTableNames.add(
				" on " + StringUtil.toLowerCase(controlTableName));
		}

		outer:
		for (String line : StringUtil.split(indexesSQL, CharPool.SEMICOLON)) {
			line = StringUtil.trim(StringUtil.toLowerCase(line));

			for (String regexControlTableName : regexControlTableNames) {
				if (StringUtil.count(line, regexControlTableName) > 0) {
					continue outer;
				}
			}

			sb.append(line);
			sb.append(StringPool.SEMICOLON);
			sb.append(StringPool.NEW_LINE);
		}

		return sb.toString();
	}

	private void _writeDefaultPartitionFiles(
			File file, String indexesSQL, String tablesSQL)
		throws Exception {

		ObjectsSQLHelper objectsSQLHelper = new ObjectsSQLHelper(
			db, PortalInstancePool.getDefaultCompanyId());

		FileUtil.write(
			new File(file, "indexes.sql"),
			indexesSQL + StringPool.NEW_LINE +
				objectsSQLHelper.getIndexesSQL());

		FileUtil.write(
			new File(file, "tables.sql"),
			tablesSQL + StringPool.NEW_LINE + objectsSQLHelper.getTablesSQL());
	}

	private void _writePartitionFiles(
			File file, String indexesSQL, String tablesSQL)
		throws Exception {

		StringBundler tablesSQLSB = new StringBundler(tablesSQL);

		List<String> controlTableNames = _removeControlTables(tablesSQLSB);

		CompanyLocalServiceUtil.forEachCompanyId(
			companyId -> {
				if (companyId == PortalInstancePool.getDefaultCompanyId()) {
					return;
				}

				ObjectsSQLHelper objectsSQLHelper = new ObjectsSQLHelper(
					db, companyId);

				FileUtil.write(
					new File(file, companyId + "_indexes.sql"),
					StringBundler.concat(
						_removeControlTablesIndexes(
							indexesSQL, controlTableNames),
						StringPool.NEW_LINE, objectsSQLHelper.getIndexesSQL()));

				FileUtil.write(
					new File(file, companyId + "_tables.sql"),
					StringBundler.concat(
						_getCreatePartitionSQL(companyId), tablesSQLSB,
						StringPool.NEW_LINE,
						_getViewsSQL(companyId, controlTableNames),
						StringPool.NEW_LINE, objectsSQLHelper.getTablesSQL()));
			});
	}

	private static final String _DATABASE_PARTITION_SCHEMA_NAME_PREFIX =
		GetterUtil.get(
			PropsUtil.get("database.partition.schema.name.prefix"),
			"lpartition_");

	private final DataSource _dataSource = InfrastructureUtil.getDataSource();

}