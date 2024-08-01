/**
 * SPDX-FileCopyrightText: (c) 2024 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.portal.db.schema.definition.internal.exporter.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.object.model.ObjectDefinition;
import com.liferay.object.model.ObjectRelationship;
import com.liferay.object.service.ObjectDefinitionLocalServiceUtil;
import com.liferay.object.service.ObjectRelationshipLocalServiceUtil;
import com.liferay.object.test.util.ObjectDefinitionTestUtil;
import com.liferay.object.test.util.ObjectRelationshipTestUtil;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.db.schema.definition.internal.test.util.ConfigurationTestUtil;
import com.liferay.portal.db.schema.definition.internal.test.util.DatabaseTestUtil;
import com.liferay.portal.kernel.dao.db.DB;
import com.liferay.portal.kernel.dao.db.DBManagerUtil;
import com.liferay.portal.kernel.dao.db.DBType;
import com.liferay.portal.kernel.db.partition.DBPartition;
import com.liferay.portal.kernel.instance.PortalInstancePool;
import com.liferay.portal.kernel.model.Company;
import com.liferay.portal.kernel.service.CompanyLocalService;
import com.liferay.portal.kernel.test.ReflectionTestUtil;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.rule.AssumeTestRule;
import com.liferay.portal.kernel.test.util.CompanyTestUtil;
import com.liferay.portal.kernel.util.FileUtil;
import com.liferay.portal.kernel.util.InfrastructureUtil;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.test.log.LogCapture;
import com.liferay.portal.test.log.LogEntry;
import com.liferay.portal.test.log.LoggerTestUtil;
import com.liferay.portal.test.rule.Inject;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;

import java.io.File;

import java.nio.file.Files;

import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.apache.commons.lang3.StringUtils;
import org.apache.felix.cm.PersistenceManager;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.osgi.service.cm.ConfigurationAdmin;

/**
 * @author Mariano Álvaro Sáiz
 */
@RunWith(Arquillian.class)
public class DBSchemaDefinitionExporterDBPartitionTest {

	@ClassRule
	@Rule
	public static final AggregateTestRule aggregateTestRule =
		new AggregateTestRule(
			new AssumeTestRule("assume"), new LiferayIntegrationTestRule());

	public static void assume() {
		Assume.assumeTrue(DBPartition.isPartitionEnabled());

		DBType dbType = DBManagerUtil.getDBType();

		Assume.assumeTrue(
			(dbType == DBType.MYSQL) || (dbType == DBType.POSTGRESQL));
	}

	@BeforeClass
	public static void setUpClass() throws Exception {
		_company = CompanyTestUtil.addCompany();

		_databaseType = String.valueOf(DBManagerUtil.getDBType());
		_folder = FileUtil.createTempFolder();

		_objectDefinition1 = ObjectDefinitionTestUtil.addCustomObjectDefinition(
			ObjectDefinitionTestUtil.getRandomName(),
			ObjectDefinitionLocalServiceUtil.getService());
		_objectDefinition2 = ObjectDefinitionTestUtil.addCustomObjectDefinition(
			ObjectDefinitionTestUtil.getRandomName(),
			ObjectDefinitionLocalServiceUtil.getService());

		_objectRelationship = ObjectRelationshipTestUtil.addObjectRelationship(
			ObjectRelationshipLocalServiceUtil.getService(), _objectDefinition1,
			_objectDefinition2);
	}

	@AfterClass
	public static void tearDownClass() throws Exception {
		_companyLocalService.deleteCompany(_company);

		Files.deleteIfExists(ConfigurationTestUtil.getConfigurationPath(_PID));

		FileUtil.deltree(_folder);

		if (_objectRelationship != null) {
			ObjectRelationshipLocalServiceUtil.deleteObjectRelationship(
				_objectRelationship.getObjectRelationshipId());
		}

		if (_objectDefinition1 != null) {
			ObjectDefinitionLocalServiceUtil.deleteObjectDefinition(
				_objectDefinition1.getObjectDefinitionId());
		}

		if (_objectDefinition2 != null) {
			ObjectDefinitionLocalServiceUtil.deleteObjectDefinition(
				_objectDefinition2.getObjectDefinitionId());
		}
	}

	@Test
	public void testExportImportDBSchemaDefinition() throws Exception {
		try (LogCapture logCapture = LoggerTestUtil.configureLog4JLogger(
				"com.liferay.portal.db.schema.definition.internal.exporter." +
					"DBSchemaDefinitionExporter",
				LoggerTestUtil.INFO)) {

			ConfigurationTestUtil.deployConfiguration(
				_configurationAdmin, _databaseType, _folder.getAbsolutePath(),
				_PID);

			_companyLocalService.forEachCompanyId(
				companyId -> {
					String tablesSQLName = "tables.sql";
					String indexesSQLName = "indexes.sql";

					if (companyId != PortalInstancePool.getDefaultCompanyId()) {
						tablesSQLName =
							companyId + StringPool.UNDERLINE + tablesSQLName;
						indexesSQLName =
							companyId + StringPool.UNDERLINE + indexesSQLName;
					}

					_assertImportDBSchemaDefinition(
						companyId, new File(_folder, tablesSQLName),
						new File(_folder, indexesSQLName));
				});

			Assert.assertFalse(
				Files.exists(ConfigurationTestUtil.getConfigurationPath(_PID)));
			Assert.assertNull(
				_configurationAdmin.listConfigurations(
					"(service.pid=" + _PID + ")"));
			Assert.assertNull(
				ReflectionTestUtil.invoke(
					_persistenceManager, "_getDictionary",
					new Class<?>[] {String.class}, _PID));

			List<LogEntry> logEntries = logCapture.getLogEntries();

			Assert.assertEquals(logEntries.toString(), 2, logEntries.size());

			List<String> logMessages = new ArrayList<>();

			for (LogEntry entry : logEntries) {
				logMessages.add(entry.getMessage());
			}

			Assert.assertEquals(
				"Start database schema definition export", logMessages.get(0));
			Assert.assertEquals(
				"Finished database schema definition export to " +
					_folder.getAbsolutePath(),
				logMessages.get(1));
		}
	}

	@Test
	public void testExportImportReport() throws Exception {
		ConfigurationTestUtil.deployConfiguration(
			_configurationAdmin, _databaseType, _folder.getAbsolutePath(),
			_PID);

		String content = FileUtil.read(
			new File(_folder, "db_schema_definition_export_report.info"));

		Assert.assertTrue(content.endsWith("Default instance missing tables:"));
	}

	@Test
	public void testExportImportReportWithMissingTable() throws Exception {
		DB db = DBManagerUtil.getDB();

		db.runSQL("create table TestTable (testColumn bigint primary key)");

		try {
			ConfigurationTestUtil.deployConfiguration(
				_configurationAdmin, _databaseType, _folder.getAbsolutePath(),
				_PID);

			String content = FileUtil.read(
				new File(_folder, "db_schema_definition_export_report.info"));

			Assert.assertTrue(
				content.contains(
					"Default instance missing tables: " +
						StringUtil.toLowerCase("TestTable")));
		}
		finally {
			db.runSQL("DROP_TABLE_IF_EXISTS(TestTable)");
		}
	}

	private void _assertImportDBSchemaDefinition(
			long companyId, File tablesSQLFile, File indexesSQLFile)
		throws Exception {

		DatabaseTestUtil.createSchema(_COPY_DB_SCHEMA_NAME);

		DataSource copyDataSource = null;
		DataSource dataSource = null;

		try {
			copyDataSource = DatabaseTestUtil.initDataSource(
				_COPY_DB_SCHEMA_NAME);

			DatabaseTestUtil.importFile(tablesSQLFile, copyDataSource);

			if (companyId == PortalInstancePool.getDefaultCompanyId()) {
				dataSource = InfrastructureUtil.getDataSource();
			}
			else {
				dataSource = DatabaseTestUtil.initDataSource(
					DatabaseTestUtil.getPartitionName(companyId));
			}

			_assertTables(dataSource, copyDataSource);

			DatabaseTestUtil.importFile(indexesSQLFile, copyDataSource);

			_assertIndexes(dataSource, copyDataSource);
		}
		finally {
			DatabaseTestUtil.dropSchema(_COPY_DB_SCHEMA_NAME);

			if ((dataSource != null) &&
				(dataSource != InfrastructureUtil.getDataSource())) {

				DatabaseTestUtil.destroyDataSource(dataSource);
			}

			if (copyDataSource != null) {
				DatabaseTestUtil.destroyDataSource(copyDataSource);
			}
		}
	}

	private void _assertIndexes(
			DataSource dataSource, DataSource copyDataSource)
		throws Exception {

		List<String> copyIndexColumnNames =
			DatabaseTestUtil.getIndexColumnNames(copyDataSource);
		List<String> indexColumnNames = DatabaseTestUtil.getIndexColumnNames(
			dataSource);

		Assert.assertEquals(
			StringUtils.difference(
				copyIndexColumnNames.toString(), indexColumnNames.toString()),
			indexColumnNames.size(), copyIndexColumnNames.size());

		for (int i = 0; i < indexColumnNames.size(); i++) {
			Assert.assertEquals(
				indexColumnNames.get(i), copyIndexColumnNames.get(i));
		}
	}

	private void _assertTables(DataSource dataSource, DataSource copyDataSource)
		throws Exception {

		List<String> copyTableColumnNames =
			DatabaseTestUtil.getTableColumnNames(copyDataSource);
		List<String> tableColumnNames = DatabaseTestUtil.getTableColumnNames(
			dataSource);

		Assert.assertEquals(
			StringUtils.difference(
				copyTableColumnNames.toString(), tableColumnNames.toString()),
			tableColumnNames.size(), copyTableColumnNames.size());

		for (int i = 0; i < tableColumnNames.size(); i++) {
			Assert.assertEquals(
				tableColumnNames.get(i), copyTableColumnNames.get(i));
		}
	}

	private static final String _COPY_DB_SCHEMA_NAME = "testschema";

	private static final String _PID =
		"com.liferay.portal.db.schema.definition.internal.configuration." +
			"DBSchemaDefinitionExporterConfiguration";

	private static Company _company;

	@Inject
	private static CompanyLocalService _companyLocalService;

	private static String _databaseType;
	private static File _folder;
	private static ObjectDefinition _objectDefinition1;
	private static ObjectDefinition _objectDefinition2;
	private static ObjectRelationship _objectRelationship;

	@Inject
	private ConfigurationAdmin _configurationAdmin;

	@Inject
	private PersistenceManager _persistenceManager;

}