/**
 * SPDX-FileCopyrightText: (c) 2000 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.portal.upgrade.v7_0_0;

import com.liferay.petra.string.StringBundler;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.dao.db.DBInspector;
import com.liferay.portal.kernel.dao.db.DBManagerUtil;
import com.liferay.portal.kernel.dao.db.DBType;
import com.liferay.portal.kernel.dao.db.DBTypeToSQLMap;
import com.liferay.portal.kernel.dao.jdbc.AutoBatchPreparedStatementUtil;
import com.liferay.portal.kernel.dao.orm.WildcardMode;
import com.liferay.portal.kernel.upgrade.UpgradeException;
import com.liferay.portal.kernel.upgrade.UpgradeProcess;
import com.liferay.portal.kernel.util.ArrayUtil;
import com.liferay.portal.kernel.util.LoggingTimer;
import com.liferay.portal.kernel.util.StringUtil;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @author Preston Crary
 */
public class UpgradeKernelPackage extends UpgradeProcess {

	@Override
	protected void doUpgrade() throws UpgradeException {
		try {
			upgradeTable(
				"ClassName_", "value", getClassNames(), WildcardMode.SURROUND,
				new String[] {"value"});
			upgradeTable(
				"Counter", "name", getClassNames(), WildcardMode.SURROUND);
			upgradeTable(
				"Lock_", "className", getClassNames(), WildcardMode.SURROUND);
			upgradeTable(
				"ResourceAction", "name", getClassNames(),
				WildcardMode.SURROUND, new String[] {"name", "actionId"});
			upgradeTable(
				"ResourcePermission", "name", getClassNames(),
				WildcardMode.SURROUND);
			upgradeLongTextTable(
				"UserNotificationEvent", "payload", "userNotificationEventId",
				getClassNames(), WildcardMode.SURROUND);

			upgradeTable(
				"ListType", "type_", getClassNames(), WildcardMode.TRAILING);
			upgradeTable(
				"ResourceAction", "name", getResourceNames(),
				WildcardMode.LEADING, new String[] {"name", "actionId"});
			upgradeTable(
				"ResourcePermission", "name", getResourceNames(),
				WildcardMode.LEADING);
			upgradeLongTextTable(
				"UserNotificationEvent", "payload", "userNotificationEventId",
				getResourceNames(), WildcardMode.LEADING);

			DBInspector dbInspector = new DBInspector(connection);

			if (dbInspector.hasTable("ResourceBlock")) {
				upgradeTable(
					"ResourceBlock", "name", getClassNames(),
					WildcardMode.SURROUND);

				upgradeTable(
					"ResourceBlock", "name", getResourceNames(),
					WildcardMode.LEADING);
			}
		}
		catch (Exception exception) {
			throw new UpgradeException(exception);
		}
	}

	protected String[][] getClassNames() {
		return _CLASS_NAMES;
	}

	protected String[][] getResourceNames() {
		return _RESOURCE_NAMES;
	}

	protected void upgradeLongTextTable(
			String columnName, String primaryKeyColumnName, String selectSQL,
			String updateSQL, String[] name)
		throws SQLException {

		try (PreparedStatement preparedStatement1 = connection.prepareStatement(
				selectSQL);
			ResultSet resultSet = preparedStatement1.executeQuery();
			PreparedStatement preparedStatement2 =
				AutoBatchPreparedStatementUtil.autoBatch(
					connection, updateSQL)) {

			while (resultSet.next()) {
				preparedStatement2.setString(
					1,
					StringUtil.replace(
						resultSet.getString(columnName), name[0], name[1]));
				preparedStatement2.setLong(
					2, resultSet.getLong(primaryKeyColumnName));

				preparedStatement2.addBatch();
			}

			preparedStatement2.executeBatch();
		}
	}

	protected void upgradeLongTextTable(
			String tableName, String columnName, String primaryKeyColumnName,
			String[][] names, WildcardMode wildcardMode)
		throws Exception {

		if (DBManagerUtil.getDBType() != DBType.SYBASE) {
			upgradeTable(tableName, columnName, names, wildcardMode);

			return;
		}

		try (LoggingTimer loggingTimer = new LoggingTimer(
				getClass(), tableName)) {

			String updateSQL = StringBundler.concat(
				"update ", tableName, " set ", columnName, " = ? where ",
				primaryKeyColumnName, " = ?");

			String selectPrefix = StringBundler.concat(
				"select ", columnName, ", ", primaryKeyColumnName, " from ",
				tableName, " where ", columnName, " like '",
				wildcardMode.getLeadingWildcard());

			String selectPostfix =
				wildcardMode.getTrailingWildcard() + StringPool.APOSTROPHE;

			for (String[] name : names) {
				upgradeLongTextTable(
					columnName, primaryKeyColumnName,
					StringBundler.concat(selectPrefix, name[0], selectPostfix),
					updateSQL, name);
			}
		}
	}

	protected void upgradeTable(
			String tableName, String columnName, String[][] names,
			WildcardMode wildcardMode)
		throws Exception {

		try (LoggingTimer loggingTimer = new LoggingTimer(
				getClass(), tableName)) {

			_executeUpdate(tableName, columnName, names, wildcardMode);
		}
	}

	protected void upgradeTable(
			String tableName, String columnName, String[][] names,
			WildcardMode wildcardMode, String[] uniqueColumns)
		throws Exception {

		try (LoggingTimer loggingTimer = new LoggingTimer(
				getClass(), tableName)) {

			_executeDelete(
				tableName, columnName, names, wildcardMode, uniqueColumns);

			_executeUpdate(tableName, columnName, names, wildcardMode);
		}
	}

	private void _executeDelete(
			String tableName, String columnName, String[][] names,
			WildcardMode wildcardMode, String[] uniqueColumns)
		throws Exception {

		for (String[] name : names) {
			DBTypeToSQLMap dbTypeToSQLMap = new DBTypeToSQLMap(
				StringBundler.concat(
					"delete from ", tableName,
					_getWhereClause(
						tableName, columnName, name[1], wildcardMode),
					_getNotLikeClause(
						tableName, columnName,
						(String)ArrayUtil.getValue(name, 2), wildcardMode),
					_getExistsClause(
						tableName, columnName, name[0], wildcardMode,
						uniqueColumns)));

			String sql = StringBundler.concat(
				"delete t1 from ", tableName, " t1 inner join ", tableName,
				" t2 ",
				_getOnClause(columnName, name[0], wildcardMode, uniqueColumns),
				_getWhereClause("t1", columnName, name[1], wildcardMode),
				_getNotLikeClause(
					"t1", columnName, (String)ArrayUtil.getValue(name, 2),
					wildcardMode));

			dbTypeToSQLMap.add(DBType.MYSQL, sql);
			dbTypeToSQLMap.add(DBType.MARIADB, sql);

			runSQL(dbTypeToSQLMap);
		}
	}

	private void _executeUpdate(
			String tableName, String columnName, String[][] names,
			WildcardMode wildcardMode)
		throws Exception {

		String tableSQL = StringBundler.concat(
			"update ", tableName, " set ", columnName, " = replace(",
			_transformColumnName(columnName), ", '");

		StringBundler sb2 = new StringBundler(6);

		for (String[] name : names) {
			sb2.append(tableSQL);
			sb2.append(name[0]);
			sb2.append("', '");
			sb2.append(name[1]);
			sb2.append("') ");
			sb2.append(
				_getWhereClause(tableName, columnName, name[0], wildcardMode));

			runSQL(sb2.toString());

			sb2.setIndex(0);
		}
	}

	private String _getExistsClause(
		String tableName, String columnName, String columnValue,
		WildcardMode wildcardMode, String[] uniqueColumns) {

		StringBundler sb = new StringBundler(10 + (uniqueColumns.length * 6));

		sb.append(" and exists (select * from  ");
		sb.append(tableName);
		sb.append(" t1 where t1.");
		sb.append(columnName);
		sb.append(" like '");
		sb.append(wildcardMode.getLeadingWildcard());
		sb.append(columnValue);
		sb.append(wildcardMode.getTrailingWildcard());
		sb.append(StringPool.APOSTROPHE);

		for (String uniqueColumn : uniqueColumns) {
			if (StringUtil.equalsIgnoreCase(uniqueColumn, columnName)) {
				continue;
			}

			sb.append(" and t1.");
			sb.append(uniqueColumn);
			sb.append(" = ");
			sb.append(tableName);
			sb.append(StringPool.PERIOD);
			sb.append(uniqueColumn);
		}

		sb.append(StringPool.CLOSE_PARENTHESIS);

		return sb.toString();
	}

	private String _getNotLikeClause(
		String tableName, String columnName, String value,
		WildcardMode wildcardMode) {

		if (value == null) {
			return StringPool.BLANK;
		}

		return StringBundler.concat(
			" and ", tableName, StringPool.PERIOD, columnName, " not like '",
			wildcardMode.getLeadingWildcard(), value,
			wildcardMode.getTrailingWildcard(), StringPool.APOSTROPHE);
	}

	private String _getOnClause(
		String columnName, String columnValue, WildcardMode wildcardMode,
		String[] uniqueColumns) {

		StringBundler sb = new StringBundler(7 + (uniqueColumns.length * 4));

		sb.append(" on t2.");
		sb.append(columnName);
		sb.append(" like '");
		sb.append(wildcardMode.getLeadingWildcard());
		sb.append(columnValue);
		sb.append(wildcardMode.getTrailingWildcard());
		sb.append(StringPool.APOSTROPHE);

		for (String uniqueColumn : uniqueColumns) {
			if (StringUtil.equalsIgnoreCase(uniqueColumn, columnName)) {
				continue;
			}

			sb.append(" and t1.");
			sb.append(uniqueColumn);
			sb.append(" = t2.");
			sb.append(uniqueColumn);
		}

		return sb.toString();
	}

	private String _getWhereClause(
		String tableName, String columnName, String columnValue,
		WildcardMode wildcardMode) {

		return StringBundler.concat(
			" where ", tableName, StringPool.PERIOD, columnName, " like '",
			wildcardMode.getLeadingWildcard(), columnValue,
			wildcardMode.getTrailingWildcard(), StringPool.APOSTROPHE);
	}

	private String _transformColumnName(String columnName) {
		if (DBManagerUtil.getDBType() == DBType.SQLSERVER) {
			return "CAST_TEXT(" + columnName + ")";
		}

		return columnName;
	}

	private static final String[][] _CLASS_NAMES = {
		{
			"com.liferay.counter.model.Counter",
			"com.liferay.counter.kernel.model.Counter"
		},
		{
			"com.liferay.portal.kernel.mail.Account",
			"com.liferay.mail.kernel.model.Account"
		},
		{
			"com.liferay.portal.model.BackgroundTask",
			"com.liferay.portal.background.task.model.BackgroundTask"
		},
		{"com.liferay.portal.model.Lock", "com.liferay.portal.lock.model.Lock"},
		{"com.liferay.portal.model.", "com.liferay.portal.kernel.model."},
		{
			"com.liferay.portlet.announcements.model.",
			"com.liferay.announcements.kernel.model."
		},
		{"com.liferay.portlet.asset.model.", "com.liferay.asset.kernel.model."},
		{"com.liferay.portlet.blogs.model.", "com.liferay.blogs.kernel.model."},
		{
			"com.liferay.portlet.documentlibrary.model.",
			"com.liferay.document.library.kernel.model."
		},
		{
			"com.liferay.portlet.documentlibrary.util.",
			"com.liferay.document.library.kernel.util."
		},
		{
			"com.liferay.portlet.expando.model.",
			"com.liferay.expando.kernel.model."
		},
		{"com.liferay.portlet.journal.model.", "com.liferay.journal.model."},
		{
			"com.liferay.portlet.messageboards.model.",
			"com.liferay.message.boards.kernel.model."
		},
		{
			"com.liferay.portlet.mobiledevicerules.model.",
			"com.liferay.mobile.device.rules.model."
		},
		{
			"com.liferay.portlet.ratings.model.",
			"com.liferay.ratings.kernel.model."
		},
		{
			"com.liferay.portlet.social.model.",
			"com.liferay.social.kernel.model."
		},
		{"com.liferay.portlet.trash.model.", "com.liferay.trash.kernel.model."},
		{
			"com.liferay.socialnetworking.model.",
			"com.liferay.social.networking.model."
		}
	};

	private static final String[][] _RESOURCE_NAMES = {
		{"com.liferay.portlet.asset", "com.liferay.asset"},
		{"com.liferay.portlet.blogs", "com.liferay.blogs"},
		{"com.liferay.portlet.documentlibrary", "com.liferay.document.library"},
		{"com.liferay.portlet.journal", "com.liferay.journal"},
		{"com.liferay.portlet.messageboards", "com.liferay.message.boards"}
	};

}