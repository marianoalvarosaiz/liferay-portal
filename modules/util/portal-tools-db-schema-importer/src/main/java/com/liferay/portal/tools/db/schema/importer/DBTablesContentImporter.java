/**
 * SPDX-FileCopyrightText: (c) 2024 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.portal.tools.db.schema.importer;

import com.liferay.petra.string.StringBundler;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.dao.jdbc.postgresql.PostgreSQLJDBCUtil;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.io.unsync.UnsyncBufferedReader;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.util.Base64;
import com.liferay.portal.kernel.util.DateUtil;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.tools.db.schema.importer.jdbc.AutoBatchPreparedStatementUtil;

import java.io.Reader;

import java.math.BigDecimal;

import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.sql.Types;

import java.text.DateFormat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.sql.DataSource;

/**
 * @author Mariano Álvaro Sáiz
 */
public class DBTablesContentImporter {

	public DBTablesContentImporter(
		DataSource sourceDataSource, DataSource targetDataSource) {

		_sourceDataSource = sourceDataSource;
		_targetDataSource = targetDataSource;
	}

	public void copyContent() throws Exception {
		List<Future<?>> futures = new ArrayList<>();

		ExecutorService executorService = Executors.newFixedThreadPool(5);

		_loadColumnsMetadata(
			_sourceDataSource, _sourceTableColumns, _sourceColumnsType);
		_loadColumnsMetadata(
			_targetDataSource, _targetTableColumns, _targetColumnsType);

		Set<String> sourceTables = _sourceTableColumns.keySet();
		Set<String> targetTables = _targetTableColumns.keySet();

		sourceTables.retainAll(targetTables);

		targetTables.retainAll(sourceTables);

		Iterator<String> sourceTablesIterator = sourceTables.iterator();
		Iterator<String> targetTablesIterator = targetTables.iterator();

		while (sourceTablesIterator.hasNext()) {
			String sourceTable = sourceTablesIterator.next();
			String targetTable = targetTablesIterator.next();

			futures.add(
				executorService.submit(
					() -> _safeCopyTableContent(sourceTable, targetTable)));
		}

		for (Future<?> future : futures) {
			future.get();
		}
	}

	private void _copyTableContent(
			String sourceTableName, String targetTableName,
			Connection sourceConnection, Connection targetConnection)
		throws Exception {

		List<String> sourceColumnsName = _sourceTableColumns.get(
			sourceTableName);

		List<String> targetColumnsName = _targetTableColumns.get(
			targetTableName);

		if (sourceColumnsName.size() > targetColumnsName.size()) {
			throw new IllegalStateException(
				StringBundler.concat(
					"Incorrect number of columns for table ", targetTableName,
					". Source has ", sourceColumnsName.size(),
					" and target has ", targetColumnsName.size(),
					StringPool.PERIOD));
		}
		else if (sourceColumnsName.size() < targetColumnsName.size()) {
			Set<String> sourceColumnsNameSet = new TreeSet<String>(
				String.CASE_INSENSITIVE_ORDER) {

				{
					addAll(sourceColumnsName);
				}
			};

			targetColumnsName.removeIf(
				columnName -> !sourceColumnsNameSet.contains(columnName));
		}

		String selectSQL = StringBundler.concat(
			"select ", StringUtil.merge(sourceColumnsName), " from ",
			sourceTableName);

		String insertSQL = StringBundler.concat(
			"insert into ", targetTableName, "(",
			StringUtil.merge(targetColumnsName), ") values (",
			StringUtil.merge(
				Collections.nCopies(targetColumnsName.size(), "?")),
			")");

		try (PreparedStatement preparedStatement1 =
				sourceConnection.prepareStatement(selectSQL);
			PreparedStatement preparedStatement2 =
				AutoBatchPreparedStatementUtil.concurrentAutoBatch(
					targetConnection, insertSQL)) {

			preparedStatement1.setFetchSize(_FETCH_SIZE);

			try (ResultSet resultSet = preparedStatement1.executeQuery()) {
				while (resultSet.next()) {
					for (int i = 0; i < sourceColumnsName.size(); i++) {
						String columnName = sourceColumnsName.get(i);

						_getAndSetColumn(
							columnName, i + 1, preparedStatement2, resultSet,
							_sourceColumnsType.get(
								sourceTableName + "." + columnName),
							_targetColumnsType.get(
								targetTableName + "." +
									targetColumnsName.get(i)));
					}

					preparedStatement2.addBatch();
				}
			}

			preparedStatement2.executeBatch();
		}
	}

	private void _getAndSetColumn(
			String columnName, int index, PreparedStatement preparedStatement,
			ResultSet resultSet, int sourceType, int targetType)
		throws Exception {

		String alternativeValue = null;

		if ((sourceType == Types.BIGINT) || (sourceType == Types.NUMERIC)) {
			if ((targetType == Types.BINARY) ||
				(targetType == Types.LONGVARBINARY) ||
				(targetType == Types.BLOB)) {

				preparedStatement.setBytes(
					index,
					PostgreSQLJDBCUtil.getLargeObject(resultSet, columnName));

				return;
			}

			long value = resultSet.getLong(columnName);

			if ((value == 0L) && resultSet.wasNull()) {
				preparedStatement.setNull(index, targetType);

				return;
			}

			if ((targetType == Types.BIGINT) || (targetType == Types.NUMERIC)) {
				preparedStatement.setLong(index, value);

				return;
			}

			alternativeValue = String.valueOf(value);
		}
		else if ((sourceType == Types.BINARY) ||
				 (sourceType == Types.LONGVARBINARY)) {

			byte[] value = resultSet.getBytes(columnName);

			if (value == null) {
				preparedStatement.setNull(index, targetType);

				return;
			}

			if ((targetType == Types.BINARY) ||
				(targetType == Types.LONGVARBINARY)) {

				preparedStatement.setBytes(index, value);

				return;
			}
			else if (targetType == Types.BIGINT) {

				// Although OID fields are meant to save binary
				// objects in PostgreSQL the field itself is an
				// identifier that points to the real object.

				PostgreSQLJDBCUtil.setLargeObject(
					preparedStatement, index, value);

				return;
			}

			alternativeValue = new String(value);
		}
		else if (sourceType == Types.BLOB) {
			Blob value = resultSet.getBlob(columnName);

			if (value == null) {
				preparedStatement.setNull(index, targetType);

				return;
			}

			if (targetType == Types.BLOB) {
				preparedStatement.setBlob(index, value);

				return;
			}
			else if (targetType == Types.BIGINT) {
				PostgreSQLJDBCUtil.setLargeObject(
					preparedStatement, index,
					value.getBytes(1, (int)value.length()));

				return;
			}

			alternativeValue = new String(
				value.getBytes(1, (int)value.length()));
		}
		else if ((sourceType == Types.BOOLEAN) || (sourceType == Types.BIT)) {
			boolean value = resultSet.getBoolean(columnName);

			if (!value && resultSet.wasNull()) {
				preparedStatement.setNull(index, targetType);

				return;
			}

			if ((targetType == Types.BOOLEAN) || (targetType == Types.BIT)) {
				preparedStatement.setBoolean(index, value);

				return;
			}

			alternativeValue = value ? "1" : "0";
		}
		else if (sourceType == Types.CLOB) {
			Clob value = resultSet.getClob(columnName);

			if (value == null) {
				preparedStatement.setNull(index, targetType);

				return;
			}

			if (targetType == Types.CLOB) {
				preparedStatement.setClob(index, value);

				return;
			}

			try (Reader reader = value.getCharacterStream();
				UnsyncBufferedReader unsyncBufferedReader =
					new UnsyncBufferedReader(reader)) {

				StringBundler sb = new StringBundler();

				String line = null;

				while ((line = unsyncBufferedReader.readLine()) != null) {
					if (sb.length() != 0) {
						sb.append("\n");
					}

					sb.append(line);
				}

				alternativeValue = sb.toString();
			}
		}
		else if (sourceType == Types.DECIMAL) {
			BigDecimal value = resultSet.getBigDecimal(columnName);

			if (value == null) {
				preparedStatement.setNull(index, targetType);

				return;
			}

			if (targetType == Types.DECIMAL) {
				preparedStatement.setBigDecimal(index, value);

				return;
			}

			alternativeValue = value.toString();
		}
		else if (sourceType == Types.DOUBLE) {
			double value = resultSet.getDouble(columnName);

			if ((value == 0.0) && resultSet.wasNull()) {
				preparedStatement.setNull(index, targetType);

				return;
			}

			if (targetType == Types.DOUBLE) {
				preparedStatement.setDouble(index, value);

				return;
			}

			alternativeValue = String.valueOf(value);
		}
		else if (sourceType == Types.FLOAT) {
			float value = resultSet.getFloat(columnName);

			if ((value == 0.0F) && resultSet.wasNull()) {
				preparedStatement.setNull(index, targetType);

				return;
			}

			if (targetType == Types.FLOAT) {
				preparedStatement.setFloat(index, value);

				return;
			}

			alternativeValue = String.valueOf(value);
		}
		else if (sourceType == Types.INTEGER) {
			int value = resultSet.getInt(columnName);

			if ((value == 0) && resultSet.wasNull()) {
				preparedStatement.setNull(index, targetType);

				return;
			}

			if (targetType == Types.INTEGER) {
				preparedStatement.setInt(index, value);

				return;
			}

			alternativeValue = String.valueOf(value);
		}
		else if ((sourceType == Types.LONGVARCHAR) ||
				 (sourceType == Types.VARCHAR)) {

			String value = resultSet.getString(columnName);

			if (value == null) {
				preparedStatement.setNull(index, targetType);

				return;
			}

			if ((targetType == Types.LONGNVARCHAR) ||
				(targetType == Types.VARCHAR)) {

				preparedStatement.setString(index, value);

				return;
			}

			alternativeValue = value;
		}
		else if (sourceType == Types.TIMESTAMP) {
			Timestamp value = resultSet.getTimestamp(columnName);

			if (value == null) {
				preparedStatement.setNull(index, targetType);

				return;
			}

			if (targetType == Types.TIMESTAMP) {
				preparedStatement.setTimestamp(index, value);

				return;
			}

			alternativeValue = value.toString();
		}
		else if ((sourceType == Types.TINYINT) ||
				 (sourceType == Types.SMALLINT)) {

			short value = resultSet.getShort(columnName);

			if ((value == 0) && resultSet.wasNull()) {
				preparedStatement.setNull(index, targetType);

				return;
			}

			if ((targetType == Types.TINYINT) ||
				(targetType == Types.SMALLINT)) {

				preparedStatement.setShort(index, value);

				return;
			}

			alternativeValue = String.valueOf(value);
		}
		else {
			throw new PortalException("Invalid type: " + sourceType);
		}

		_setColumn(index, preparedStatement, targetType, alternativeValue);
	}

	private TreeSet<String> _getViews(DataSource dataSource) throws Exception {
		TreeSet<String> treeSet = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

		try (Connection connection = dataSource.getConnection()) {
			DatabaseMetaData databaseMetaData = connection.getMetaData();

			try (ResultSet resultSet = databaseMetaData.getTables(
					connection.getCatalog(), connection.getSchema(), null,
					new String[] {"VIEW"})) {

				while (resultSet.next()) {
					treeSet.add(resultSet.getString("TABLE_NAME"));
				}
			}
		}

		return treeSet;
	}

	private void _loadColumnsMetadata(
			DataSource dataSource, Map<String, List<String>> tableColumns,
			Map<String, Integer> columnsType)
		throws Exception {

		Set<String> views = _getViews(dataSource);

		try (Connection connection = dataSource.getConnection()) {
			DatabaseMetaData databaseMetaData = connection.getMetaData();

			try (ResultSet resultSet = databaseMetaData.getColumns(
					connection.getCatalog(), connection.getSchema(), null,
					null)) {

				while (resultSet.next()) {
					String tableName = resultSet.getString("TABLE_NAME");

					if (views.contains(tableName)) {
						continue;
					}

					String columnName = resultSet.getString("COLUMN_NAME");

					List<String> columnsName = tableColumns.computeIfAbsent(
						tableName, key -> new ArrayList<>());

					columnsName.add(columnName);

					columnsType.put(
						tableName + "." + columnName,
						resultSet.getInt("DATA_TYPE"));
				}
			}
		}

		for (List<String> columnsName : tableColumns.values()) {
			Collections.sort(columnsName, String.CASE_INSENSITIVE_ORDER);
		}
	}

	private void _safeCopyTableContent(
		String sourceTableName, String targetTableName) {

		try (Connection sourceConnection = _sourceDataSource.getConnection();
			Connection targetConnection = _targetDataSource.getConnection()) {

			_copyTableContent(
				sourceTableName, targetTableName, sourceConnection,
				targetConnection);
		}
		catch (Exception exception) {
			_log.error(exception);
		}
	}

	private void _setColumn(
			int index, PreparedStatement preparedStatement, int targetType,
			String value)
		throws Exception {

		if ((targetType == Types.BIGINT) || (targetType == Types.NUMERIC)) {
			preparedStatement.setLong(index, GetterUtil.getLong(value));
		}
		else if ((targetType == Types.BIT) || (targetType == Types.BOOLEAN)) {
			preparedStatement.setBoolean(index, GetterUtil.getBoolean(value));
		}
		else if ((targetType == Types.BLOB) ||
				 (targetType == Types.LONGVARBINARY) ||
				 (targetType == Types.BINARY)) {

			preparedStatement.setBytes(index, Base64.decode(value));
		}
		else if ((targetType == Types.CLOB) ||
				 (targetType == Types.LONGVARCHAR) ||
				 (targetType == Types.VARCHAR)) {

			preparedStatement.setString(index, value);
		}
		else if (targetType == Types.DECIMAL) {
			preparedStatement.setBigDecimal(
				index, (BigDecimal)GetterUtil.get(value, BigDecimal.ZERO));
		}
		else if (targetType == Types.DOUBLE) {
			preparedStatement.setDouble(index, GetterUtil.getDouble(value));
		}
		else if (targetType == Types.FLOAT) {
			preparedStatement.setFloat(index, GetterUtil.getFloat(value));
		}
		else if (targetType == Types.INTEGER) {
			preparedStatement.setInt(index, GetterUtil.getInteger(value));
		}
		else if ((targetType == Types.SMALLINT) ||
				 (targetType == Types.TINYINT)) {

			preparedStatement.setShort(index, GetterUtil.getShort(value));
		}
		else if (targetType == Types.TIMESTAMP) {
			Date date = _dateFormat.parse(value);

			preparedStatement.setTimestamp(
				index, new Timestamp(date.getTime()));
		}
		else {
			throw new PortalException("Invalid type: " + targetType);
		}
	}

	private static final int _FETCH_SIZE = 2500;

	private static final Log _log = LogFactoryUtil.getLog(
		DBTablesContentImporter.class);

	private final DateFormat _dateFormat = DateUtil.getISOFormat();
	private final Map<String, Integer> _sourceColumnsType = new HashMap<>();
	private final DataSource _sourceDataSource;
	private final Map<String, List<String>> _sourceTableColumns = new TreeMap<>(
		String.CASE_INSENSITIVE_ORDER);
	private final Map<String, Integer> _targetColumnsType = new HashMap<>();
	private final DataSource _targetDataSource;
	private final Map<String, List<String>> _targetTableColumns = new TreeMap<>(
		String.CASE_INSENSITIVE_ORDER);

}