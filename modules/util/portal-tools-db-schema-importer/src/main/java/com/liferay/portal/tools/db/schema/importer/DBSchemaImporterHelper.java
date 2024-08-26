/**
 * SPDX-FileCopyrightText: (c) 2024 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.portal.tools.db.schema.importer;

import com.liferay.petra.string.CharPool;
import com.liferay.petra.string.StringBundler;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.io.unsync.UnsyncBufferedReader;
import com.liferay.portal.kernel.io.unsync.UnsyncStringReader;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.tools.db.schema.importer.jdbc.AutoBatchPreparedStatementUtil;
import com.liferay.portal.tools.db.schema.importer.jdbc.DataSourceFactoryUtil;

import java.io.File;
import java.io.FileFilter;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import java.sql.Connection;
import java.sql.Statement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

/**
 * @author Mariano Álvaro Sáiz
 */
public class DBSchemaImporterHelper {

	public DBSchemaImporterHelper(
			String path, String sourceJDBCURL, String sourcePassword,
			String sourceUser, String targetJDBCURL, String targetPassword,
			String targetUser)
		throws Exception {

		_path = path;
		_sourceJDBCURL = sourceJDBCURL;
		_sourcePassword = sourcePassword;
		_sourceUser = sourceUser;
		_targetJDBCURL = targetJDBCURL;
		_targetPassword = targetPassword;
		_targetUser = targetUser;

		_sourceDataSource = DataSourceFactoryUtil.initDataSource(
			sourceJDBCURL, sourcePassword, sourceUser);

		_targetDataSource = DataSourceFactoryUtil.initDataSource(
			_targetJDBCURL, _targetPassword, _targetUser);
	}

	public void importDB() throws Exception {
		_createTables();

		_copyData();

		_createIndexes();

		_executorService.shutdownNow();
		_executorService.awaitTermination(10, TimeUnit.SECONDS);
	}

	private void _copyData() throws Exception {
		AutoBatchPreparedStatementUtil.start();

		Set<Future<?>> futures = Collections.newSetFromMap(
			new ConcurrentHashMap<>());

		futures.add(
			_executorService.submit(
				() -> {
					new DBTablesContentImporter(
						_sourceDataSource, _targetDataSource
					).copyContent();

					return null;
				}));

		for (String partitionName : _partitionNames) {
			futures.add(
				_executorService.submit(
					() -> {
						new DBTablesContentImporter(
							DataSourceFactoryUtil.initDataSource(
								_sourceJDBCURL, _sourcePassword, _sourceUser,
								partitionName),
							DataSourceFactoryUtil.initDataSource(
								_targetJDBCURL, _targetPassword, _targetUser,
								partitionName)
						).copyContent();

						return null;
					}));
		}

		for (Future<?> future : futures) {
			future.get();
		}

		AutoBatchPreparedStatementUtil.stop();
	}

	private void _createIndexes() throws Exception {
		_executeFilesSQL("indexes.sql");
	}

	private void _createTables() throws Exception {
		_runSQLTemplateConcurrently(
			_targetDataSource, _readFile(new File(_path, "tables.sql")));

		_executeFilesSQL("_tables.sql");
	}

	private void _executeFilesSQL(String fileFilter) throws Exception {
		File[] files = _listFiles(fileFilter);

		String sqlContent = StringPool.BLANK;

		int count = 0;

		for (File file : files) {
			sqlContent += _readFile(file);

			if ((++count % _COMPANY_BATCH_SIZE) == 0) {
				_runSQLTemplateConcurrently(_targetDataSource, sqlContent);

				sqlContent = StringPool.BLANK;
			}
		}

		if (Validator.isNotNull(sqlContent)) {
			_runSQLTemplateConcurrently(_targetDataSource, sqlContent);
		}
	}

	private File[] _listFiles(String filter) {
		File dir = new File(_path);

		return dir.listFiles(
			new FileFilter() {

				@Override
				public boolean accept(File file) {
					if (file.isDirectory()) {
						return false;
					}

					return StringUtil.endsWith(file.getName(), filter);
				}

			});
	}

	private void _preprocessSQL(String sqlTemplate) throws Exception {
		sqlTemplate = StringUtil.trim(sqlTemplate);

		if ((sqlTemplate == null) || sqlTemplate.isEmpty()) {
			return;
		}

		if (!sqlTemplate.endsWith(StringPool.SEMICOLON)) {
			sqlTemplate += StringPool.SEMICOLON;
		}

		try (UnsyncBufferedReader unsyncBufferedReader =
				new UnsyncBufferedReader(new UnsyncStringReader(sqlTemplate))) {

			StringBundler sb = new StringBundler();

			String line = null;

			while ((line = unsyncBufferedReader.readLine()) != null) {
				if (line.isEmpty() || line.startsWith("##")) {
					continue;
				}

				sb.append(line);
				sb.append(StringPool.NEW_LINE);

				if (line.endsWith(";")) {
					String sql = sb.toString();

					sb.setIndex(0);

					if (StringUtil.startsWith(sql, "create or replace rule")) {
						_syncFinalSQLs.add(sql);
					}
					else {
						_asyncSQLs.add(sql);
					}

					if (StringUtil.startsWith(
							sql, "create schema if not exists")) {

						_syncInitialSQLs.add(sql);

						String[] parts = StringUtil.split(sql, CharPool.SPACE);

						_partitionNames.add(
							StringUtil.removeChar(
								parts[5], CharPool.SEMICOLON));
					}
				}
			}
		}
	}

	private String _readFile(File file) throws Exception {
		return new String(
			Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
	}

	private void _runSQLTemplateConcurrently(
			DataSource dataSource, String sqlTemplate)
		throws Exception {

		_preprocessSQL(sqlTemplate);

		for (String sql : _syncInitialSQLs) {
			try (Connection connection = dataSource.getConnection();
				Statement statement = connection.createStatement()) {

				statement.executeUpdate(sql);
			}
		}

		_syncInitialSQLs.clear();

		List<Future<?>> futures = new ArrayList<>();

		for (String sql : _asyncSQLs) {
			futures.add(
				_executorService.submit(
					() -> {
						try (Connection connection = dataSource.getConnection();
							Statement statement =
								connection.createStatement()) {

							statement.executeUpdate(sql);
						}
						catch (Exception exception) {
							_log.error(exception);
						}
					}));
		}

		_asyncSQLs.clear();

		for (Future<?> future : futures) {
			future.get();
		}

		for (String sql : _syncFinalSQLs) {
			try (Connection connection = dataSource.getConnection();
				Statement statement = connection.createStatement()) {

				statement.executeUpdate(sql);
			}
		}

		_syncFinalSQLs.clear();
	}

	private static final int _COMPANY_BATCH_SIZE = 5;

	private static final Log _log = LogFactoryUtil.getLog(
		DBSchemaImporterHelper.class);

	private final List<String> _asyncSQLs = new ArrayList<>();
	private final ExecutorService _executorService =
		Executors.newFixedThreadPool(5);
	private final List<String> _partitionNames = new ArrayList<>();
	private final String _path;
	private final DataSource _sourceDataSource;
	private final String _sourceJDBCURL;
	private final String _sourcePassword;
	private final String _sourceUser;
	private final List<String> _syncFinalSQLs = new ArrayList<>();
	private final List<String> _syncInitialSQLs = new ArrayList<>();
	private final DataSource _targetDataSource;
	private final String _targetJDBCURL;
	private final String _targetPassword;
	private final String _targetUser;

}