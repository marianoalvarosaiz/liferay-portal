/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.portal.kernel.test.rule.callback;

import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.search.Document;
import com.liferay.portal.kernel.search.Field;
import com.liferay.portal.kernel.search.Hits;
import com.liferay.portal.kernel.search.Indexer;
import com.liferay.portal.kernel.search.IndexerRegistry;
import com.liferay.portal.kernel.search.SearchContext;
import com.liferay.portal.kernel.service.UserLocalService;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.StringBundler;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.registry.Registry;
import com.liferay.registry.RegistryUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.runner.Description;

/**
 * @author Mariano Alvaro Saiz
 */
public class CheckIndexerTestCallback
	extends BaseTestCallback<List<Integer>, Object> {

	public static final CheckIndexerTestCallback INSTANCE =
		new CheckIndexerTestCallback();

	public void afterCla(Description description, Object c) throws Throwable {
		Registry registry = RegistryUtil.getRegistry();

		IndexerRegistry indexerRegistry = registry.getService(
			IndexerRegistry.class);

		Indexer<User> indexer = indexerRegistry.getIndexer(User.class);

		SearchContext searchContext = getSearchContext();

		searchContext.setKeywords(StringPool.BLANK);

		Hits hits = search(indexer, searchContext);

		int length = hits.getDocs().length;

		if (length > 1) {
			_log.error("Found too much users, " + length);

			UserLocalService userLocalService = registry.getService(
				UserLocalService.class);

			StringBundler sb = new StringBundler();

			for (Document document : hits.getDocs()) {
				sb.append(getUser(userLocalService, document).getScreenName());
				sb.append(", ");
			}

			throw new IllegalStateException("Found too much users, " + sb);
		}
	}

	@Override
	public void afterClass(Description description, List<Integer> beforeLengths)
		throws Throwable {

		List<Integer> afterLengths = _getIndexesLengths();

		StringBundler sb = new StringBundler();

		for (int i = 0; i < beforeLengths.size(); i++) {
			if (beforeLengths.get(i) < afterLengths.get(i)) {
				sb.append(" Not deleted: ");
				sb.append(i);
			}
		}

		if (sb.length() > 0) {
			throw new IllegalStateException(sb.toString());
		}
	}

	@Override
	public List<Integer> beforeClass(Description description) throws Throwable {
		return _getIndexesLengths();
	}

	protected SearchContext getSearchContext() throws Exception {
		SearchContext searchContext = new SearchContext();

		searchContext.setCompanyId(TestPropsValues.getCompanyId());
		searchContext.setGroupIds(new long[] {TestPropsValues.getGroupId()});

		return searchContext;
	}

	protected User getUser(UserLocalService userLocalService, Document document)
		throws Exception {

		long userId = GetterUtil.getLong(document.get(Field.USER_ID));

		return userLocalService.getUser(userId);
	}

	protected Hits search(Indexer<?> indexer, SearchContext searchContext)
		throws Exception {

		return indexer.search(searchContext);
	}

	protected Hits search(Indexer<User> indexer, String keywords)
		throws Exception {

		SearchContext searchContext = getSearchContext();

		searchContext.setKeywords(keywords);

		return search(indexer, searchContext);
	}

	private CheckIndexerTestCallback() {
	}

	private List<Integer> _getIndexesLengths() throws Exception {
		Registry registry = RegistryUtil.getRegistry();

		IndexerRegistry indexerRegistry = registry.getService(
			IndexerRegistry.class);

		List<Integer> lengths = new ArrayList<>();

		for (Class<?> clazz : _orderedClasses) {
			Indexer<?> indexer = indexerRegistry.getIndexer(clazz);

			SearchContext searchContext = getSearchContext();

			searchContext.setKeywords(StringPool.BLANK);

			Hits hits = search(indexer, searchContext);

			lengths.add(hits.getLength());
		}

		return lengths;
	}

	private static final Log _log = LogFactoryUtil.getLog(
		CheckIndexerTestCallback.class);

	private static final Set<Class<?>> _orderedClasses = new LinkedHashSet<>(
		Arrays.<Class<?>>asList(User.class));

}