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

package com.liferay.journal.service.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.asset.kernel.service.AssetEntryLocalServiceUtil;
import com.liferay.asset.kernel.service.AssetTagLocalServiceUtil;
import com.liferay.journal.model.JournalArticle;
import com.liferay.journal.test.util.JournalTestUtil;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.rule.DeleteAfterTestRun;
import com.liferay.portal.kernel.test.util.GroupTestUtil;
import com.liferay.portal.kernel.test.util.ServiceContextTestUtil;
import com.liferay.portal.kernel.transaction.Transactional;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;
import com.liferay.portal.test.rule.TransactionalTestRule;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Mariano Alvaro Saiz
 */
@RunWith(Arquillian.class)
public class JournalArticleLocalServiceAssetTagTest {

	@ClassRule
	@Rule
	public static final AggregateTestRule aggregateTestRule =
		new AggregateTestRule(
			new LiferayIntegrationTestRule(), TransactionalTestRule.INSTANCE);

	@Before
	public void setUp() throws Exception {
		_group = GroupTestUtil.addGroup();
		_journalArticle = _createJournalArticle();
	}

	@Test
	public void testAssetTagsCanBeRetrievedAfterDifferentTransactions()
		throws Exception {

		_journalArticle = _publishArticle(_journalArticle);

		_journalArticle = _saveArticle(_journalArticle);

		long tagId = _getTagId();

		_executeAndWait(
			() -> _ignoreException(
				() -> queryAssetTagEntriesUpdatingJournalMeanwhile(tagId)));

		_executeAndWait(
			() -> AssetEntryLocalServiceUtil.getAssetTagAssetEntries(tagId));
	}

	@Transactional
	protected void queryAssetTagEntriesUpdatingJournalMeanwhile(long tagId)
		throws Exception {

		AssetEntryLocalServiceUtil.getAssetTagAssetEntries(tagId);

		_executeAndWait(
			() -> _ignoreException(
				() -> _journalArticle = _publishArticle(_journalArticle)));

		AssetEntryLocalServiceUtil.getAssetTagAssetEntries(tagId);
	}

	private JournalArticle _createJournalArticle() throws Exception {
		ServiceContext serviceContext =
			ServiceContextTestUtil.getServiceContext(_group.getGroupId());

		serviceContext.setAssetTagNames(new String[] {_TAG});

		return JournalTestUtil.addArticleWithWorkflow(
			_group.getGroupId(), _FOLDER_ID, _TITLE, _CONTENT, _DRAFT,
			serviceContext);
	}

	private void _executeAndWait(Runnable runnable) throws Exception {
		Thread thread = new Thread(runnable);

		thread.start();
		thread.join();
	}

	private long _getTagId() {
		if (_tagId == 0) {
			long[] tagsId = AssetTagLocalServiceUtil.getTagIds(_TAG);

			_tagId = tagsId[0];
		}

		return _tagId;
	}

	private void _ignoreException(RunnableWithException runnable) {
		try {
			runnable.run();
		}
		catch (Exception e) {
		}
	}

	private JournalArticle _publishArticle(JournalArticle journalArticle)
		throws Exception {

		return JournalTestUtil.updateArticleWithWorkflow(
			journalArticle, _PUBLISH);
	}

	private JournalArticle _saveArticle(JournalArticle journalArticle)
		throws Exception {

		return JournalTestUtil.updateArticleWithWorkflow(
			journalArticle, _DRAFT);
	}

	private static final String _CONTENT = "false";

	private static final boolean _DRAFT = false;

	private static final long _FOLDER_ID = 0L;

	private static final boolean _PUBLISH = true;

	private static final String _TAG = "tag";

	private static final String _TITLE = "title";

	@DeleteAfterTestRun
	private Group _group;

	@DeleteAfterTestRun
	private JournalArticle _journalArticle;

	private long _tagId;

	@FunctionalInterface
	private interface RunnableWithException {

		public void run() throws Exception;

	}

}