/**
 * SPDX-FileCopyrightText: (c) 2024 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.portal.db.schema.definition.internal.validation;

import com.liferay.petra.string.StringBundler;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.db.schema.definition.internal.partition.DBSchemaPartitionUtil;
import com.liferay.portal.kernel.db.partition.DBPartition;
import com.liferay.portal.kernel.instance.PortalInstancePool;
import com.liferay.portal.kernel.model.Release;
import com.liferay.portal.kernel.model.ReleaseConstants;
import com.liferay.portal.kernel.patcher.PatcherValues;
import com.liferay.portal.kernel.service.CompanyLocalServiceUtil;
import com.liferay.portal.kernel.service.ReleaseLocalServiceUtil;
import com.liferay.portal.kernel.util.DateUtil;
import com.liferay.portal.kernel.util.FileUtil;
import com.liferay.portal.kernel.util.InfrastructureUtil;
import com.liferay.portal.kernel.util.SetUtil;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Time;

import java.io.File;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

import javax.sql.DataSource;

/**
 * @author Mariano Álvaro Sáiz
 */
public class DBSchemaDefinitionExporterValidation {

	public static void validateSchemaExport(String path) throws Exception {
		Release release = ReleaseLocalServiceUtil.fetchRelease(
			ReleaseConstants.DEFAULT_SERVLET_CONTEXT_NAME);

		String message = StringBundler.concat(
			"***This is the database dump generated report***",
			StringPool.NEW_LINE, StringPool.NEW_LINE, "Generation time: ",
			_formatDate(new Date()), StringPool.NEW_LINE,
			"Portal schema version: ", release.getSchemaVersion(),
			StringPool.NEW_LINE, "Portal build number: ",
			release.getBuildNumber(), StringPool.NEW_LINE,
			"Portal build date: ", _formatDate(release.getBuildDate()),
			StringPool.NEW_LINE, "Installed patches: ",
			StringUtil.merge(
				PatcherValues.INSTALLED_PATCH_NAMES,
				StringPool.COMMA_AND_SPACE),
			_buildCompaniesTables(path));

		FileUtil.write(new File(path, _REPORT_INFO), message);
	}

	private static String _buildCompaniesTables(String path) throws Exception {
		StringBundler sb = new StringBundler(_buildDefaultCompanyTables(path));

		if (!DBPartition.isPartitionEnabled()) {
			return sb.toString();
		}

		CompanyLocalServiceUtil.forEachCompanyId(
			companyId -> {
				_buildCompanyTables(path, companyId, "tables");
				_buildCompanyTables(path, companyId, "views");
			});

		return sb.toString();
	}

	private static String _buildCompanyTables(
			String path, long companyId, String type)
		throws Exception {

		Set<String> dbTables =
			StringUtil.equals(type, "views") ? _getDBViews(companyId) :
				_getDBTables(companyId);
		Set<String> fileTables =
			StringUtil.equals(type, "views") ? _getFileViews(path, companyId) :
				_getFileTables(path, companyId);

		Set<String> tablesNotFound = SetUtil.asymmetricDifference(
			dbTables, fileTables);

		return StringBundler.concat(
			StringPool.NEW_LINE, "Database ", type, " (companyId ", companyId,
			"): ", dbTables.size(), StringPool.NEW_LINE, "File ", type,
			" (companyId ", companyId, "): ", fileTables.size(),
			StringPool.NEW_LINE, "Missing ", type, " (companyId ", companyId,
			"): ", StringUtil.merge(tablesNotFound, StringPool.COMMA_AND_SPACE),
			StringPool.NEW_LINE);
	}

	private static String _buildDefaultCompanyTables(String path)
		throws Exception {

		Set<String> dbTables = _getDBTables(
			PortalInstancePool.getDefaultCompanyId());
		Set<String> fileTables = _getFileTables(
			path, PortalInstancePool.getDefaultCompanyId());

		Set<String> tablesNotFound = SetUtil.asymmetricDifference(
			dbTables, fileTables);

		return StringBundler.concat(
			StringPool.NEW_LINE, "Database tables: ", dbTables.size(),
			StringPool.NEW_LINE, "File tables: ", fileTables.size(),
			StringPool.NEW_LINE, "Missing tables: ",
			StringUtil.merge(tablesNotFound, StringPool.COMMA_AND_SPACE),
			StringPool.NEW_LINE);
	}

	private static String _formatDate(Date date) {
		return Time.getSimpleDate(date, DateUtil.ISO_8601_PATTERN);
	}

	private static Set<String> _getDBTables(long companyId) throws Exception {
		return _getDBTablesByType(companyId, "TABLE");
	}

	private static Set<String> _getDBTablesByType(long companyId, String type)
		throws Exception {

		Set<String> tableNames = new HashSet<>();

		DataSource dataSource = InfrastructureUtil.getDataSource();

		try (Connection connection = dataSource.getConnection()) {
			DBSchemaPartitionUtil.setPartition(connection, companyId);

			DatabaseMetaData databaseMetaData = connection.getMetaData();

			try (ResultSet resultSet = databaseMetaData.getTables(
					connection.getCatalog(), connection.getSchema(), null,
					new String[] {type})) {

				while (resultSet.next()) {
					tableNames.add(
						StringUtil.toLowerCase(
							resultSet.getString("TABLE_NAME")));
				}
			}
		}

		return tableNames;
	}

	private static Set<String> _getDBViews(long companyId) throws Exception {
		return _getDBTablesByType(companyId, "VIEW");
	}

	private static Set<String> _getFileInfos(
			String path, long companyId, String filter,
			Function<String, String> function)
		throws Exception {

		Set<String> infoNames = new HashSet<>();

		String prefix = StringPool.BLANK;

		if (PortalInstancePool.getDefaultCompanyId() != companyId) {
			prefix = companyId + StringPool.UNDERLINE;
		}

		String fileContent = StringUtil.toLowerCase(
			FileUtil.read(new File(path, prefix + "tables.sql")));

		String[] lines = StringUtil.split(fileContent, StringPool.NEW_LINE);

		for (String line : lines) {
			if (StringUtil.startsWith(line, filter)) {
				infoNames.add(function.apply(line));
			}
		}

		return infoNames;
	}

	private static Set<String> _getFileTables(String path, long companyId)
		throws Exception {

		return _getFileInfos(
			path, companyId, "create table",
			line -> line.split(StringPool.SPACE)[2]);
	}

	private static Set<String> _getFileViews(String path, long companyId)
		throws Exception {

		return _getFileInfos(
			path, companyId, "create or replace view",
			line -> {
				String[] parts = line.split(StringPool.SPACE);

				return parts[4].substring(parts[4].indexOf(StringPool.COLON));
			});
	}

	private static final String _REPORT_INFO = "report.info";

}