/**
 * SPDX-FileCopyrightText: (c) 2024 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.portal.db.copy.internal.ddl;

import com.liferay.portal.db.DBResourceUtil;
import com.liferay.portal.kernel.dao.db.DB;
import com.liferay.portal.kernel.dao.db.DBManagerUtil;
import com.liferay.portal.kernel.module.util.SystemBundleUtil;
import com.liferay.portal.spring.hibernate.DialectDetector;
import com.liferay.portal.upgrade.release.SchemaCreator;

import java.sql.Connection;

import java.util.Collection;

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
	}

	public void copyTo(DataSource targetDataSource) throws Exception {
		_copyPortal(targetDataSource);
		_copyMiscellaneousSQL(targetDataSource);
		_copyModules(targetDataSource);
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

	private final DataSource _targetDataSource;
	private final DB _targetDB;

}