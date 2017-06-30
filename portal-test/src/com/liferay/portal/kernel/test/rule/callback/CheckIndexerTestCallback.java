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
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.util.StringBundler;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.registry.Registry;
import com.liferay.registry.RegistryUtil;

import org.junit.runner.Description;

/**
 * @author Mariano Alvaro Saiz
 */
public class CheckIndexerTestCallback extends BaseTestCallback<Object, Object> {

	public static final CheckIndexerTestCallback INSTANCE =
		new CheckIndexerTestCallback();

	@Override
	public void afterClass(Description description, Object c) throws Throwable {
		Registry registry = RegistryUtil.getRegistry();

		IndexerRegistry indexerRegistry = registry.getService(
			IndexerRegistry.class);

		Indexer<User> indexer = indexerRegistry.getIndexer(User.class);

		SearchContext searchContext = getSearchContext();

		searchContext.setKeywords(StringPool.BLANK);

		Hits hits = search(indexer, searchContext);

		int length = hits.getDocs().length;

		if (length > 0) {
			_log.error("Found too much users, " + length);

			StringBundler sb = new StringBundler();

			for (Document document : hits.getDocs()) {
				sb.append(document.get(Field.USER_ID));
				sb.append(", ");
			}

			throw new IllegalStateException("Found too much users, " + sb);
		}
	}

	protected SearchContext getSearchContext() throws Exception {
		SearchContext searchContext = new SearchContext();

		searchContext.setCompanyId(TestPropsValues.getCompanyId());
		searchContext.setGroupIds(new long[] {TestPropsValues.getGroupId()});

		return searchContext;
	}

	protected Hits search(Indexer<User> indexer, SearchContext searchContext)
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

	private static final Log _log = LogFactoryUtil.getLog(
		CheckIndexerTestCallback.class);

}