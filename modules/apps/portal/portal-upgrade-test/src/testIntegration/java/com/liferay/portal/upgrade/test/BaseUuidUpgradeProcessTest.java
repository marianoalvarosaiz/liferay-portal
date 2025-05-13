/**
 * SPDX-FileCopyrightText: (c) 2025 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.portal.upgrade.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.petra.string.StringBundler;
import com.liferay.portal.kernel.dao.db.DB;
import com.liferay.portal.kernel.dao.db.DBInspector;
import com.liferay.portal.kernel.dao.db.DBManagerUtil;
import com.liferay.portal.kernel.dao.jdbc.DataAccess;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.upgrade.BaseUuidUpgradeProcess;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Mariano Álvaro Sáiz
 */
@RunWith(Arquillian.class)
public class BaseUuidUpgradeProcessTest extends BaseUuidUpgradeProcess {

	@ClassRule
	@Rule
	public static final AggregateTestRule aggregateTestRule =
		new LiferayIntegrationTestRule();

	@Before
	public void setUp() throws Exception {
		_db.runSQL(
			StringBundler.concat(
				"create table TestTable1 (blobColumn BLOB, sBlobColumn SBLOB, ",
				"bigDecimalColumn BIGDECIMAL, booleanColumn BOOLEAN, ",
				"dateColumn DATE, doubleColumn DOUBLE, integerColumn INTEGER, ",
				"longColumn LONG, stringColumn STRING, textColumn TEXT, ",
				"varcharColumn VARCHAR(10))"));
	}

	@After
	public void tearDown() throws Exception {
		_db.runSQL("DROP_TABLE_IF_EXISTS(TestTable1)");
	}

	@Test
	public void testUuidUpgrade() throws Exception {
		try (Connection connection = DataAccess.getConnection()) {
			DBInspector dbInspector = new DBInspector(connection);
			DatabaseMetaData databaseMetaData = connection.getMetaData();

			try (ResultSet resultSet = databaseMetaData.getColumns(
					connection.getCatalog(), connection.getSchema(),
					dbInspector.normalizeName("TestTable1"), null)) {

				while (resultSet.next()) {
					String columnName = resultSet.getString("COLUMN_NAME");
					int dataType = resultSet.getInt("DATA_TYPE");

					System.out.println("Column Name: " + columnName);
					System.out.println("Data type: " + dataType);
				}
			}
		}
	}

	protected String[][] getTableAndPrimaryKeyColumnNames() {
		return null;
	}

	private final DB _db = DBManagerUtil.getDB();

}