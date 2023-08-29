/**
 * SPDX-FileCopyrightText: (c) 2000 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.portal.kernel.dao.orm;

import com.liferay.petra.lang.SafeCloseable;
import com.liferay.portal.kernel.backgroundtask.BackgroundTaskThreadLocal;
import com.liferay.portal.kernel.change.tracking.CTCollectionThreadLocal;
import com.liferay.portal.kernel.change.tracking.sql.CTSQLModeThreadLocal;
import com.liferay.portal.kernel.configuration.Filter;
import com.liferay.portal.kernel.dao.db.DB;
import com.liferay.portal.kernel.dao.db.DBManagerUtil;
import com.liferay.portal.kernel.dao.db.DBType;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.model.change.tracking.CTModel;
import com.liferay.portal.kernel.search.Document;
import com.liferay.portal.kernel.search.IndexWriterHelper;
import com.liferay.portal.kernel.search.background.task.ReindexStatusMessageSenderUtil;
import com.liferay.portal.kernel.util.ArrayUtil;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.ListUtil;
import com.liferay.portal.kernel.util.PropsKeys;
import com.liferay.portal.kernel.util.PropsUtil;
import com.liferay.portal.kernel.util.ServiceProxyFactory;

import java.lang.reflect.Method;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * @author Andrew Betts
 */
public class IndexableActionableDynamicQuery
	extends DefaultActionableDynamicQuery {

	public IndexableActionableDynamicQuery() {
		if (_databaseInMaxParameters == 0) {
			_initializeDatabaseInMaxParameters();
		}
	}

	public void addDocuments(Document... documents) throws PortalException {
		if (ArrayUtil.isEmpty(documents)) {
			return;
		}

		for (Document document : documents) {
			if (document != null) {
				_documents.add(document);
			}
		}

		long size = _documents.size();

		if (size >= getInterval()) {
			indexInterval();
		}
		else if ((size % _STATUS_INTERVAL) == 0) {
			sendStatusMessage(size);
		}
	}

	public void findBy(String columnName, Collection<Long> values) {
		_columnName = columnName;
		_values = ListUtil.fromCollection(values);

		if (_values.size() <= _databaseInMaxParameters) {
			setAddCriteriaMethod(
				dynamicQuery -> dynamicQuery.add(
					RestrictionsFactoryUtil.in(_columnName, values)));
		}
	}

	@Override
	public void performActions() throws PortalException {
		if (BackgroundTaskThreadLocal.hasBackgroundTask()) {
			_total = super.performCount();
		}

		try {
			if ((_values == null) ||
				(_values.size() <= _databaseInMaxParameters)) {

				super.performActions();
			}
			else {
				_performActions();
			}
		}
		finally {
			_count = _total;

			sendStatusMessage();
		}
	}

	public void setIndexWriterHelper(IndexWriterHelper indexWriterHelper) {
		_indexWriterHelper = indexWriterHelper;
	}

	@Override
	public void setParallel(boolean parallel) {
		if (isParallel() == parallel) {
			return;
		}

		super.setParallel(parallel);

		if (parallel) {
			_documents = new ConcurrentLinkedDeque<>();
		}
	}

	@Override
	protected void actionsCompleted() throws PortalException {
		_indexWriterHelper.commit(getCompanyId());
	}

	@Override
	protected long doPerformActions(long previousPrimaryKey)
		throws PortalException {

		try {
			return super.doPerformActions(previousPrimaryKey);
		}
		finally {
			indexInterval();
		}
	}

	protected void indexInterval() throws PortalException {
		if ((_documents == null) || _documents.isEmpty()) {
			return;
		}

		_indexWriterHelper.updateDocuments(
			getCompanyId(), new ArrayList<>(_documents), false);

		_count += _documents.size();

		_documents.clear();

		sendStatusMessage();
	}

	@Override
	protected void performAction(Object object) throws PortalException {
		long ctCollectionId = 0;

		if (object instanceof CTModel) {
			CTModel<?> ctModel = (CTModel<?>)object;

			ctCollectionId = ctModel.getCtCollectionId();
		}

		try (SafeCloseable safeCloseable1 =
				CTSQLModeThreadLocal.setCTSQLModeWithSafeCloseable(
					CTSQLModeThreadLocal.CTSQLMode.DEFAULT);
			SafeCloseable safeCloseable2 =
				CTCollectionThreadLocal.setCTCollectionIdWithSafeCloseable(
					ctCollectionId)) {

			super.performAction(object);
		}
	}

	protected void sendStatusMessage() {
		sendStatusMessage(0);
	}

	protected void sendStatusMessage(long documentIntervalCount) {
		if (!BackgroundTaskThreadLocal.hasBackgroundTask()) {
			return;
		}

		Class<?> modelClass = getModelClass();

		ReindexStatusMessageSenderUtil.sendStatusMessage(
			modelClass.getName(), _count + documentIntervalCount, _total);
	}

	private void _initializeDatabaseInMaxParameters() {
		DB db = DBManagerUtil.getDB();

		DBType dbType = db.getDBType();

		_databaseInMaxParameters = GetterUtil.getInteger(
			PropsUtil.get(
				PropsKeys.DATABASE_IN_MAX_PARAMETERS,
				new Filter(dbType.getName())),
			Integer.MAX_VALUE);
	}

	private void _performActions() throws PortalException {
		int size = _values.size();

		int start = 0;
		int end = _databaseInMaxParameters;

		while (start < size) {
			Class<?> clazz = baseLocalService.getClass();

			IndexableActionableDynamicQuery indexableActionableDynamicQuery =
				null;

			try {
				Method method = clazz.getMethod(
					"getIndexableActionableDynamicQuery");

				indexableActionableDynamicQuery =
					(IndexableActionableDynamicQuery)method.invoke(
						baseLocalService);
			}
			catch (Exception exception) {
				throw new SystemException(exception);
			}

			List<Long> partition = ListUtil.subList(_values, start, end);

			indexableActionableDynamicQuery.setAddCriteriaMethod(
				dynamicQuery -> dynamicQuery.add(
					RestrictionsFactoryUtil.in(_columnName, partition)));

			indexableActionableDynamicQuery.setCompanyId(getCompanyId());
			indexableActionableDynamicQuery.setPerformActionMethod(
				getPerformActionMethod());

			indexableActionableDynamicQuery.performActions();

			_count += indexableActionableDynamicQuery._count;
			_total += indexableActionableDynamicQuery._total;

			end += _databaseInMaxParameters;
			start += _databaseInMaxParameters;
		}
	}

	private static final long _STATUS_INTERVAL = 1000;

	private static int _databaseInMaxParameters;
	private static volatile IndexWriterHelper _indexWriterHelperProxy =
		ServiceProxyFactory.newServiceTrackedInstance(
			IndexWriterHelper.class, IndexableActionableDynamicQuery.class,
			"_indexWriterHelperProxy", false);

	private String _columnName;
	private long _count;
	private Collection<Document> _documents = new ArrayList<>();
	private IndexWriterHelper _indexWriterHelper = _indexWriterHelperProxy;
	private long _total;
	private List<Long> _values;

}