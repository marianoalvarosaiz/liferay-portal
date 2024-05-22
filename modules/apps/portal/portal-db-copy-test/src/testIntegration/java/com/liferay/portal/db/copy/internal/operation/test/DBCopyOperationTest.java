/**
 * SPDX-FileCopyrightText: (c) 2024 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.portal.db.copy.internal.operation.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.portal.db.copy.internal.operation.test.helper.ConfigurationTestHelper;
import com.liferay.portal.db.copy.internal.operation.test.util.ConfigurationValidationTestUtil;
import com.liferay.portal.db.copy.internal.operation.test.util.DatabaseTestUtil;
import com.liferay.portal.db.copy.internal.operation.test.util.DatabaseValidationTestUtil;
import com.liferay.portal.kernel.dao.db.DBManagerUtil;
import com.liferay.portal.kernel.dao.db.DBType;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.rule.AssumeTestRule;
import com.liferay.portal.test.rule.Inject;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;

import org.apache.felix.cm.PersistenceManager;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.osgi.service.cm.ConfigurationAdmin;

/**
 * @author Mariano Álvaro Sáiz
 */
@RunWith(Arquillian.class)
public class DBCopyOperationTest {

	@ClassRule
	@Rule
	public static final AggregateTestRule aggregateTestRule =
		new AggregateTestRule(
			new AssumeTestRule("assume"), new LiferayIntegrationTestRule());

	public static void assume() {
		Assume.assumeTrue(DBManagerUtil.getDBType() == DBType.MYSQL);
	}

	@Before
	public void setUp() throws Exception {
		DatabaseTestUtil.createSchema(_COPY_SCHEMA_NAME);

		_configurationTestHelper = new ConfigurationTestHelper(
			_configurationAdmin, _persistenceManager);
	}

	@After
	public void tearDown() throws Exception {
		DatabaseTestUtil.dropSchema(_COPY_SCHEMA_NAME);

		_configurationTestHelper.deleteConfiguration();
	}

	@Test
	public void testCopyDatabaseConfiguration() throws Exception {
		_configurationTestHelper.deployConfiguration(_PID, _COPY_SCHEMA_NAME);

		DatabaseValidationTestUtil.assertDatabaseCopyIsEqualsToCurrent(
			_COPY_SCHEMA_NAME);

		ConfigurationValidationTestUtil.assertConfigurationIsDeletedAfterDeploy(
			_configurationTestHelper, _PID);
	}

	private static final String _COPY_SCHEMA_NAME = "copyschema";

	private static final String _PID =
		"com.liferay.portal.db.copy.internal.configuration.DBCopyConfiguration";

	@Inject
	private ConfigurationAdmin _configurationAdmin;

	private ConfigurationTestHelper _configurationTestHelper;

	@Inject
	private PersistenceManager _persistenceManager;

}