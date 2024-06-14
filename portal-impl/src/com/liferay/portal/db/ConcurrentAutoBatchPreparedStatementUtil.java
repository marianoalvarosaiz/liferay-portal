/**
 * SPDX-FileCopyrightText: (c) 2024 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.portal.db;

import com.liferay.portal.kernel.dao.jdbc.DataAccess;
import com.liferay.portal.kernel.util.ProxyUtil;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * @author Mariano Álvaro Sáiz
 */
public class ConcurrentAutoBatchPreparedStatementUtil {

	public static PreparedStatement concurrentAutoBatch(
			Connection connection, String sql)
		throws SQLException {

		return (PreparedStatement)ProxyUtil.newProxyInstance(
			ClassLoader.getSystemClassLoader(),
			new Class<?>[] {PreparedStatement.class},
			new ConcurrentBatchInvocationHandler(connection, sql));
	}

	public static void start() throws Exception {
		_executorService = Executors.newFixedThreadPool(10);
	}

	public static void stop() throws Exception {
		_executorService.shutdownNow();
		_executorService.awaitTermination(10, TimeUnit.SECONDS);
	}

	private static final int _BATCH_SIZE = 2500;

	private static ExecutorService _executorService;

	private static class ConcurrentBatchInvocationHandler
		implements InvocationHandler {

		@Override
		public Object invoke(Object proxy, Method method, Object[] args)
			throws Throwable {

			if (Objects.equals(method.getName(), "getConnection")) {
				return _connection;
			}

			if (Objects.equals(method.getName(), "close")) {
				doClose();

				return null;
			}

			if (Objects.equals(method.getName(), "addBatch")) {
				doAddBatch();

				return null;
			}

			if (Objects.equals(method.getName(), "executeBatch")) {
				return doExecuteBatch();
			}

			return method.invoke(getPreparedStatement(), args);
		}

		protected ConcurrentBatchInvocationHandler(
			Connection connection, String sql) {

			_connection = connection;
			_sql = sql;
		}

		protected void doAddBatch() throws SQLException {
			PreparedStatement localPreparedStatement = getPreparedStatement();

			localPreparedStatement.addBatch();

			if (++_count >= _BATCH_SIZE) {
				_count = 0;

				executeAsync(() -> _safeExecuteBatch(localPreparedStatement));
			}
		}

		protected void doClose() throws Throwable {
			for (Future<?> future : _futures) {
				try {
					future.get();
				}
				catch (Exception exception) {
					System.err.println(exception.getMessage());
				}
			}
		}

		protected int[] doExecuteBatch() throws SQLException {
			if (_count > 0) {
				_count = 0;

				PreparedStatement localPreparedStatement =
					getPreparedStatement();

				executeAsync(() -> _safeExecuteBatch(localPreparedStatement));
			}

			return new int[0];
		}

		protected void executeAsync(Runnable runnable) {
			_executorService.submit(runnable);

			_futures.add(_executorService.submit(runnable));

			preparedStatement = null;
		}

		protected PreparedStatement getPreparedStatement() throws SQLException {
			if (preparedStatement == null) {
				preparedStatement = _connection.prepareStatement(_sql);
			}

			return preparedStatement;
		}

		protected PreparedStatement preparedStatement;

		private void _safeExecuteBatch(PreparedStatement preparedStatement) {
			try {
				preparedStatement.executeBatch();
			}
			catch (Exception exception) {
				System.err.println(exception.getMessage());
			}
			finally {
				DataAccess.cleanUp(preparedStatement);
			}
		}

		private final Connection _connection;
		private int _count;
		private final Set<Future<?>> _futures = Collections.newSetFromMap(
			new ConcurrentHashMap<>());
		private final String _sql;

	}

}