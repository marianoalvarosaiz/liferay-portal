/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.dynamic.data.mapping.internal.upgrade.v7_1_2;

import com.liferay.petra.string.StringBundler;
import com.liferay.portal.kernel.dao.jdbc.AutoBatchPreparedStatementUtil;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.upgrade.UpgradeProcess;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * @author Ankita Malik
 */
public class DDMFieldUpgradeProcess extends UpgradeProcess {

	@Override
	protected void doUpgrade() throws Exception {
		try (PreparedStatement selectPreparedStatement =
				connection.prepareStatement(
					StringBundler.concat(
						"select ctCollectionId, fieldId, storageId from ",
						"DDMField where fieldName is null or fieldName = '' ",
						"order by ctCollectionId, storageId, fieldId"));
			PreparedStatement deleteDDMFieldPreparedStatement =
				AutoBatchPreparedStatementUtil.autoBatch(
					connection,
					"delete from DDMField where ctCollectionId = ? and " +
						"fieldId = ?");
			PreparedStatement deleteDDMFieldAttributePreparedStatement =
				AutoBatchPreparedStatementUtil.autoBatch(
					connection,
					"delete from DDMFieldAttribute where ctCollectionId = ? " +
						"and fieldId = ?");
			ResultSet resultSet = selectPreparedStatement.executeQuery()) {

			long rootCtCollectionId = -1;
			long rootStorageId = -1;

			while (resultSet.next()) {
				long ctCollectionId = resultSet.getLong("ctCollectionId");
				long storageId = resultSet.getLong("storageId");

				if ((ctCollectionId != rootCtCollectionId) ||
					(storageId != rootStorageId)) {

					rootCtCollectionId = ctCollectionId;
					rootStorageId = storageId;

					continue;
				}

				long fieldId = resultSet.getLong("fieldId");

				if (_log.isWarnEnabled()) {
					_log.warn(
						StringBundler.concat(
							"Deleting duplicate root DDM field ", fieldId,
							" for storage ", storageId));
				}

				deleteDDMFieldPreparedStatement.setLong(1, ctCollectionId);
				deleteDDMFieldPreparedStatement.setLong(2, fieldId);

				deleteDDMFieldPreparedStatement.addBatch();

				deleteDDMFieldAttributePreparedStatement.setLong(
					1, ctCollectionId);
				deleteDDMFieldAttributePreparedStatement.setLong(2, fieldId);

				deleteDDMFieldAttributePreparedStatement.addBatch();
			}

			deleteDDMFieldPreparedStatement.executeBatch();

			deleteDDMFieldAttributePreparedStatement.executeBatch();
		}
	}

	private static final Log _log = LogFactoryUtil.getLog(
		DDMFieldUpgradeProcess.class);

}