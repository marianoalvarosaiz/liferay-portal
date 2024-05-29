/**
 * SPDX-FileCopyrightText: (c) 2024 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.portal.db.copy.internal.ddl;

import com.liferay.petra.string.StringBundler;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.dao.db.BaseDB;
import com.liferay.portal.db.DBResourceUtil;
import com.liferay.portal.kernel.dao.db.DB;
import com.liferay.portal.kernel.dao.db.DBInspector;
import com.liferay.portal.kernel.dao.db.DBManagerUtil;
import com.liferay.portal.kernel.dao.jdbc.AutoBatchPreparedStatementUtil;
import com.liferay.portal.kernel.dao.jdbc.DataAccess;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.module.util.SystemBundleUtil;
import com.liferay.portal.kernel.util.InfrastructureUtil;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.spring.hibernate.DialectDetector;
import com.liferay.portal.upgrade.release.SchemaCreator;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.sql.DataSource;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

/**
 * @author Mariano Álvaro Sáiz
 */
public class LiferayDBStructureDuplicator {

	public LiferayDBStructureDuplicator(DataSource targetDataSource) {
		_targetDataSource = targetDataSource;

		_targetDB = DBManagerUtil.getDB(
			DialectDetector.getDialect(targetDataSource), targetDataSource);
		
		_baseDB = (BaseDB)_targetDB;
	}

	public void copyTo(DataSource targetDataSource) throws Exception {
		_copyPortal(targetDataSource);
		_copyMiscellaneousSQL(targetDataSource);
		_copyModules(targetDataSource);
		System.out.println("Start: " + new Date());
		_copyContent(targetDataSource);
		_baseDB.executeIndexes(_targetDataSource);
		System.out.println("End: " + new Date());
	}

	private void _copyContent(DataSource targetDataSource) throws Exception {
		List<String> sourceTables = new ArrayList<>();
		List<String> targetTables = new ArrayList<>();

		DataSource sourceDataSource = InfrastructureUtil.getDataSource();

		try (Connection sourceConnection = sourceDataSource.getConnection();
			Connection targetConnection = targetDataSource.getConnection()) {

			DatabaseMetaData databaseMetaData = sourceConnection.getMetaData();

			DBInspector sourceDBInspector = new DBInspector(sourceConnection);
			DBInspector targetDBInspector = new DBInspector(targetConnection);

			_sourceCatalog = sourceDBInspector.getCatalog();
			_sourceSchema = sourceDBInspector.getSchema();

			try (ResultSet resultSet = databaseMetaData.getTables(
					_sourceCatalog, _sourceSchema, null,
					new String[] {"TABLE"})) {

				while (resultSet.next()) {
					sourceTables.add(
						sourceDBInspector.normalizeName(
							resultSet.getString("TABLE_NAME")));
					targetTables.add(
						targetDBInspector.normalizeName(
							resultSet.getString("TABLE_NAME")));
				}
			}
		}

		List<Future<?>> futures = new ArrayList<>();

		ExecutorService executorService = Executors.newFixedThreadPool(5);

		for (int i = 0; i < targetTables.size(); i++) {
			String sourceTable = sourceTables.get(i);
			String targetTable = targetTables.get(i);

			futures.add(
				executorService.submit(
					() -> _safeCopyTableContent(
						sourceTable, targetTable)));
		}

		for (Future<?> future : futures) {
			future.get();
		}
	}

	private void _copyMiscellaneousSQL(DataSource targetDataSource)
		throws Exception {

		try (Connection connection = targetDataSource.getConnection()) {
			for (String sqlTemplate :
					DBResourceUtil.getMiscellaneousSQLTemplates()) {

				_targetDB.runSQLTemplateString(connection, sqlTemplate, true);
			}
		}
	}

	private void _copyModules(DataSource targetDataSource) throws Exception {
		BundleContext bundleContext = SystemBundleUtil.getBundleContext();

		Collection<ServiceReference<SchemaCreator>> serviceReferences =
			bundleContext.getServiceReferences(SchemaCreator.class, null);

		for (ServiceReference<SchemaCreator> serviceReference :
				serviceReferences) {

			SchemaCreator schemaCreator = bundleContext.getService(
				serviceReference);

			try {
				schemaCreator.createOn(targetDataSource);
			}
			finally {
				bundleContext.ungetService(serviceReference);
			}
		}
	}

	private void _copyPortal(DataSource targetDataSource) throws Exception {
		try (Connection connection = targetDataSource.getConnection()) {
			_targetDB.runSQLTemplateString(
				connection, DBResourceUtil.getPortalTablesSQL(), true);
			_targetDB.runSQLTemplateString(
				connection, DBResourceUtil.getPortalIndexesSQL(), true);
			_targetDB.runSQLTemplateString(
				connection, DBResourceUtil.getPortalSequencesSQL(), true);
		}
	}

	private void _copyTableContent(
			String sourceTableName, String targetTableName,
			Connection sourceConnection, Connection targetConnection,
			DatabaseMetaData databaseMetaData)
		throws Exception {

		if (sourceTableName.contains("_x_")) {
			return;
		}

		List<String> sourceTableColumns = new ArrayList<>();
		List<Integer> tableTypes = new ArrayList<>();
		List<String> targetTableColumns = new ArrayList<>();

		try (ResultSet resultSet = databaseMetaData.getColumns(
				_sourceCatalog, _sourceSchema, sourceTableName, null)) {

			while (resultSet.next()) {
				sourceTableColumns.add(resultSet.getString("COLUMN_NAME"));
				targetTableColumns.add(resultSet.getString("COLUMN_NAME"));
				tableTypes.add(resultSet.getInt("DATA_TYPE"));
			}
		}

		String selectSQL = StringBundler.concat(
			"select ", StringUtil.merge(sourceTableColumns), " from ",
			sourceTableName);

		String insertSQL = StringBundler.concat(
			"insert into ", targetTableName, StringPool.OPEN_PARENTHESIS,
			StringUtil.merge(targetTableColumns), ") values (",
			StringUtil.merge(
				Collections.nCopies(sourceTableColumns.size(), "?")),
			StringPool.CLOSE_PARENTHESIS);

		try (PreparedStatement preparedStatement1 =
				sourceConnection.prepareStatement(selectSQL);
			PreparedStatement preparedStatement2 =
				AutoBatchPreparedStatementUtil.concurrentAutoBatch(
					targetConnection, insertSQL);
			) {
			
			preparedStatement1.setFetchSize(10000);
			
			try (ResultSet resultSet = preparedStatement1.executeQuery()) {

				while (resultSet.next()) {
					for (int i = 0; i < sourceTableColumns.size(); i++) {
						String columnName = sourceTableColumns.get(i);
						int index = i + 1;
						int type = tableTypes.get(i);
	
						if ((type == Types.BIGINT) || (type == Types.NUMERIC)) {
							preparedStatement2.setLong(
								index, resultSet.getLong(columnName));
						}
						else if ((type == Types.BOOLEAN) || (type == Types.BIT)) {
							preparedStatement2.setBoolean(
								index, resultSet.getBoolean(columnName));
						}
						else if (type == Types.CLOB) {
							preparedStatement2.setClob(
								index, resultSet.getClob(columnName));
						}
						else if ((type == Types.LONGVARCHAR) ||
								 (type == Types.VARCHAR)) {
	
							preparedStatement2.setString(
								index, resultSet.getString(columnName));
						}
						else if (type == Types.BLOB) {
							preparedStatement2.setBlob(
								index, resultSet.getBlob(columnName));
						}
						else if (type == Types.BINARY) {
							preparedStatement2.setBytes(
								index, resultSet.getBytes(columnName));
						}
						else if (type == Types.LONGVARBINARY) {
							preparedStatement2.setBinaryStream(
								index, resultSet.getBinaryStream(columnName));
						}
						else if (type == Types.DECIMAL) {
							preparedStatement2.setBigDecimal(
								index, resultSet.getBigDecimal(columnName));
						}
						else if (type == Types.DOUBLE) {
							preparedStatement2.setDouble(
								index, resultSet.getDouble(columnName));
						}
						else if (type == Types.FLOAT) {
							preparedStatement2.setFloat(
								index, resultSet.getFloat(columnName));
						}
						else if (type == Types.INTEGER) {
							preparedStatement2.setInt(
								index, resultSet.getInt(columnName));
						}
						else if (type == Types.TINYINT) {
							preparedStatement2.setBoolean(
								index, resultSet.getInt(columnName) == 1);
						}
						else if (type == Types.SMALLINT) {
							preparedStatement2.setShort(
								index, resultSet.getShort(columnName));
						}
						else if (type == Types.TIMESTAMP) {
							preparedStatement2.setTimestamp(
								index, resultSet.getTimestamp(columnName));
						}
						else {
							throw new PortalException("Invalid type: " + type);
						}
					}
	
					preparedStatement2.addBatch();
				}
			}

			preparedStatement2.executeBatch();
		}
	}

	private void _safeCopyTableContent(
		String sourceTableName, String targetTableName) {
		
		DataSource sourceDataSource = InfrastructureUtil.getDataSource();
		
		Connection sourceConnection = null;
		Connection targetConnection = null;
		
		try {
			sourceConnection = sourceDataSource.getConnection();
			targetConnection = _targetDataSource.getConnection();

			DatabaseMetaData databaseMetaData = sourceConnection.getMetaData();
			
			_copyTableContent(
				sourceTableName, targetTableName, sourceConnection,
				targetConnection, databaseMetaData);
		}
		catch (Exception exception) {
			System.err.println(exception.getMessage());
		}
		finally {
			DataAccess.cleanUp(sourceConnection);
			DataAccess.cleanUp(targetConnection);
		}
	}
	
	private String _sourceCatalog;
	private String _sourceSchema;
	private final DataSource _targetDataSource;
	private final DB _targetDB;
	private final BaseDB _baseDB;

}