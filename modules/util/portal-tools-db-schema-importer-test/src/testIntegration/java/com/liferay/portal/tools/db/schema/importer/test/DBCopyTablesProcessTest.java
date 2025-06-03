/**
 * SPDX-FileCopyrightText: (c) 2025 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.portal.tools.db.schema.importer.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.portal.kernel.dao.db.DB;
import com.liferay.portal.kernel.dao.db.DBManagerUtil;
import com.liferay.portal.kernel.dao.jdbc.DataAccess;
import com.liferay.portal.kernel.test.util.RandomTestUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Mariano Álvaro Sáiz
 */
@RunWith(Arquillian.class)
public class DBCopyTablesProcessTest {

	@After
	public void tearDown() throws Exception {
		_db.runSQL("DROP_TABLE_IF_EXISTS(TestTable)");
	}

	@Test
	public void testBigDecimalColumn() throws Exception {
		_createTable("BIGDECIMAL");

		Object[] values = new Object[5000];

		for (int i = 0; i < _TABLE_SIZE; i++) {
			values[i] = RandomTestUtil.nextDouble();
		}

		_insertValues(values);

		_assertValues(values);
	}

	private void _assertValues(Object[] expectedValues) throws Exception {
		int total = 0;

		try (Connection connection = DataAccess.getConnection();
			PreparedStatement preparedStatement = connection.prepareStatement(
				"select testColumn from TestTable order by id ASC")) {

			try (ResultSet resultSet = preparedStatement.executeQuery()) {
				while (resultSet.next()) {
					total++;

					Assert.assertEquals(
						expectedValues[total++], resultSet.getObject(1));
				}
			}
		}

		Assert.assertEquals(expectedValues.length, total);
	}

	private void _createTable(String columnType) throws Exception {
		_db.runSQL(
			"create table TestTable (id INTEGER, testColumn " + columnType +
				")");
	}

	private void _insertValues(Object[] values) throws Exception {
		try (Connection connection = DataAccess.getConnection();
			PreparedStatement preparedStatement = connection.prepareStatement(
				"insert into TestTable(testColumn) values(?, ?)")) {

			int id = 0;

			for (Object value : values) {
				preparedStatement.setInt(1, ++id);
				preparedStatement.setObject(2, value);
			}
		}
	}

	private static final int _TABLE_SIZE = 5000;

	private final DB _db = DBManagerUtil.getDB();

}