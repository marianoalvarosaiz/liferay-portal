/**
 * SPDX-FileCopyrightText: (c) 2024 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.portal.db;

import com.liferay.portal.dao.jdbc.util.ConnectionWrapper;
import com.liferay.portal.dao.jdbc.util.StatementWrapper;
import com.liferay.portal.kernel.dao.db.DB;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import javax.sql.DataSource;

/**
 * @author Mariano Álvaro Sáiz
 */
public class DBConcurrentExecutor {

	public DBConcurrentExecutor(DB db, Predicate<String> synchronousPredicate) {
		_db = db;
		_synchronousPredicate = synchronousPredicate;

		_executorService = Executors.newFixedThreadPool(5);
	}

	public void runSQLTemplateConcurrently(
			DataSource dataSource, String sqlTemplate)
		throws Exception {

		_db.runSQLTemplateString(_getConnectionWrapper(), sqlTemplate, true);

		List<Future<?>> futures = new ArrayList<>();

		for (String sql : _asyncSQLs) {
			futures.add(
				_executorService.submit(
					() -> {
						try (Connection connection =
								dataSource.getConnection()) {

							_db.runSQLTemplateString(connection, sql, true);
						}
						catch (Exception exception) {
							System.err.println(exception.getMessage());
						}
					}));
		}

		_asyncSQLs.clear();

		for (Future<?> future : futures) {
			future.get();
		}

		for (String sql : _syncSQLs) {
			try (Connection connection = dataSource.getConnection()) {
				_db.runSQLTemplateString(connection, sql, true);
			}
		}

		_syncSQLs.clear();
	}

	public void stop() throws Exception {
		_executorService.shutdownNow();
		_executorService.awaitTermination(10, TimeUnit.SECONDS);
	}

	private Connection _getConnectionWrapper() {
		return new ConnectionWrapper(null) {

			@Override
			public void close() {
			}

			@Override
			public Statement createStatement() throws SQLException {
				return _wrapStatement(null);
			}

		};
	}

	private Statement _wrapStatement(Statement statement) {
		return new StatementWrapper(statement) {

			@Override
			public void close() {
			}

			@Override
			public int executeUpdate(String sql) throws SQLException {
				if (_synchronousPredicate.test(sql)) {
					_syncSQLs.add(sql);
				}
				else {
					_asyncSQLs.add(sql);
				}

				return 0;
			}

		};
	}

	private final List<String> _asyncSQLs = new ArrayList<>();
	private final DB _db;
	private final ExecutorService _executorService;
	private final Predicate<String> _synchronousPredicate;
	private final List<String> _syncSQLs = new ArrayList<>();

}