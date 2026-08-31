/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.portal.verify;

import com.liferay.petra.function.transform.TransformUtil;
import com.liferay.petra.string.StringBundler;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.dao.db.DBInspector;
import com.liferay.portal.kernel.db.DBResourceUtil;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.Release;
import com.liferay.portal.kernel.model.ReleaseConstants;
import com.liferay.portal.kernel.security.auth.CompanyThreadLocal;
import com.liferay.portal.kernel.service.CompanyLocalServiceUtil;
import com.liferay.portal.kernel.service.ReleaseLocalServiceUtil;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.PropsValues;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * @author Mariano Álvaro Sáiz
 */
public class PostupgradeVerifyDatabaseState extends VerifyProcess {

	@Override
	protected void doVerify() throws Exception {
		Map<String, String> tableOwnerMap = DBResourceUtil.getTableOwnerMap();

		Set<String> expectedTableNames = _getCaseInsensitiveSet(
			tableOwnerMap.keySet());

		CompanyLocalServiceUtil.forEachCompanyId(
			companyId -> {
				try {
					expectedTableNames.addAll(
						DBResourceUtil.getNonserviceBuilderTableNames(
							companyId));
				}
				catch (PortalException portalException) {
					_log.error(
						"Unable to get table names for company " + companyId,
						portalException);
				}
			});

		if (expectedTableNames.isEmpty()) {
			return;
		}

		DBInspector dbInspector = new DBInspector(connection);

		Set<String> expectedViewNames = new TreeSet<>(
			String.CASE_INSENSITIVE_ORDER);

		if (!CompanyThreadLocal.isDefaultCompany()) {
			expectedViewNames.addAll(
				dbInspector.getControlTableNames(expectedTableNames));

			expectedTableNames.removeAll(expectedViewNames);
		}

		Set<String> databaseTableNames = _getCaseInsensitiveSet(
			dbInspector.getTableNames(null));
		Set<String> databaseViewNames = _getCaseInsensitiveSet(
			dbInspector.getViewNames(null));

		Set<String> missingTableNames = _getCaseInsensitiveSet(
			expectedTableNames);

		missingTableNames.removeAll(databaseTableNames);

		Set<String> missingViewNames = _getCaseInsensitiveSet(
			expectedViewNames);

		missingViewNames.removeAll(databaseViewNames);

		Map<String, List<String>> findingsMap = new TreeMap<>();

		_addFindings(
			findingsMap, tableOwnerMap,
			_getCaseInsensitiveSet(
				TransformUtil.transform(
					missingTableNames, dbInspector::normalizeName)),
			"Missing tables were detected");
		_addFindings(
			findingsMap, tableOwnerMap, missingViewNames,
			"Missing views were detected");

		Map<String, String> historicalTableOwnerMap =
			DBResourceUtil.getHistoricalServiceComponentTableNames(connection);

		Set<String> customerTableNames = _getCaseInsensitiveSet(
			databaseTableNames);

		customerTableNames.removeAll(expectedTableNames);

		Set<String> staleTableNames = _getCaseInsensitiveSet(
			customerTableNames);

		staleTableNames.retainAll(historicalTableOwnerMap.keySet());

		customerTableNames.removeAll(staleTableNames);

		Set<String> customerViewNames = _getCaseInsensitiveSet(
			databaseViewNames);

		customerViewNames.removeAll(expectedViewNames);

		Set<String> staleViewNames = _getCaseInsensitiveSet(customerViewNames);

		staleViewNames.retainAll(historicalTableOwnerMap.keySet());

		customerViewNames.removeAll(staleViewNames);

		_addFindings(
			findingsMap, historicalTableOwnerMap, staleTableNames,
			"Stale tables were detected");
		_addFindings(
			findingsMap, historicalTableOwnerMap, staleViewNames,
			"Stale views were detected");

		for (Map.Entry<String, List<String>> entry : findingsMap.entrySet()) {
			String owner = entry.getKey();

			if (!owner.isEmpty()) {
				_log.error(_getModuleMessage(owner));
			}

			for (String finding : entry.getValue()) {
				_log.error(finding);
			}
		}

		if (!_log.isInfoEnabled()) {
			return;
		}

		if (!customerTableNames.isEmpty()) {
			_log.info(
				_getFinding(
					"Customer or non-Liferay tables were detected",
					customerTableNames));
		}

		if (!customerViewNames.isEmpty()) {
			_log.info(
				_getFinding(
					"Customer or non-Liferay views were detected",
					customerViewNames));
		}
	}

	private void _addFindings(
		Map<String, List<String>> findingsMap, Map<String, String> ownerMap,
		Set<String> names, String prefix) {

		Map<String, Set<String>> ownerNamesMap = new TreeMap<>();

		for (String name : names) {
			ownerNamesMap.computeIfAbsent(
				GetterUtil.getString(ownerMap.get(name)),
				ownerName -> new TreeSet<>()
			).add(
				name
			);
		}

		for (Map.Entry<String, Set<String>> entry : ownerNamesMap.entrySet()) {
			findingsMap.computeIfAbsent(
				entry.getKey(), ownerName -> new ArrayList<>()
			).add(
				_getFinding(prefix, entry.getValue())
			);
		}
	}

	private Set<String> _getCaseInsensitiveSet(Collection<String> names) {
		Set<String> caseInsensitiveSet = new TreeSet<>(
			String.CASE_INSENSITIVE_ORDER);

		caseInsensitiveSet.addAll(names);

		return caseInsensitiveSet;
	}

	private String _getFinding(String prefix, Set<String> names) {
		if (PropsValues.DATABASE_PARTITION_ENABLED) {
			prefix = StringBundler.concat(
				prefix, " for company ",
				CompanyThreadLocal.getNonsystemCompanyId());
		}

		return StringBundler.concat(
			prefix, StringPool.COLON, StringPool.SPACE, names);
	}

	private String _getModuleMessage(String owner) {
		Release release = ReleaseLocalServiceUtil.fetchRelease(owner);

		if ((release == null) ||
			(release.getState() == ReleaseConstants.STATE_GOOD)) {

			return "Module " + owner + StringPool.COLON;
		}

		return StringBundler.concat(
			"Module ", owner, " (incomplete upgrade): release state ",
			(release.getState() == ReleaseConstants.STATE_UPGRADE_FAILURE) ?
				"upgrade failure" : "verify failure",
			", schema version ", release.getSchemaVersion(), ", last modified ",
			release.getModifiedDate());
	}

	private static final Log _log = LogFactoryUtil.getLog(
		PostupgradeVerifyDatabaseState.class);

}