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

package com.liferay.portal.tools.service.builder.test.service.persistence.impl.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.portal.kernel.configuration.Filter;
import com.liferay.portal.kernel.dao.db.DB;
import com.liferay.portal.kernel.dao.db.DBManagerUtil;
import com.liferay.portal.kernel.dao.db.DBType;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.rule.AssumeTestRule;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.transaction.Propagation;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.PropsKeys;
import com.liferay.portal.kernel.util.PropsUtil;
import com.liferay.portal.test.rule.Inject;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;
import com.liferay.portal.test.rule.TransactionalTestRule;
import com.liferay.portal.tools.service.builder.test.model.VersionedEntry;
import com.liferay.portal.tools.service.builder.test.service.persistence.VersionedEntryPersistence;

import java.io.Serializable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.junit.After;
import org.junit.Assume;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Mariano Álvaro Sáiz
 */
@RunWith(Arquillian.class)
public class DatabaseInMaxParametersOracleTest {

	@ClassRule
	@Rule
	public static final AggregateTestRule aggregateTestRule =
		new AggregateTestRule(
			new AssumeTestRule("assume"), new LiferayIntegrationTestRule(),
			new TransactionalTestRule(
				Propagation.REQUIRED,
				"com.liferay.portal.tools.service.builder.test.service"));

	public static void assume() {
		DB db = DBManagerUtil.getDB();

		Assume.assumeTrue(db.getDBType() == DBType.ORACLE);
	}

	@After
	public void tearDown() throws Exception {
		Iterator<VersionedEntry> iterator = _versionedEntries.iterator();

		while (iterator.hasNext()) {
			_versionedEntryPersistence.remove(iterator.next());

			iterator.remove();
		}
	}

	@Test
	public void testPreferencesOwnedByCompany() throws Exception {
		Set<Serializable> primaryKeys = new HashSet<>();

		int databaseInMaxParamaters = GetterUtil.getInteger(
			PropsUtil.get(
				PropsKeys.DATABASE_IN_MAX_PARAMETERS, new Filter("oracle")));

		for (int i = 0; i <= (2 * databaseInMaxParamaters); i++) {
			VersionedEntry versionedEntry = addVersionedEntry();

			primaryKeys.add(versionedEntry.getPrimaryKey());
		}

		_versionedEntryPersistence.fetchByPrimaryKeys(primaryKeys);

		if (!primaryKeys.isEmpty()) {
			throw new NullPointerException();
		}
	}

	protected VersionedEntry addVersionedEntry() throws Exception {
		long pk = RandomTestUtil.nextLong();

		VersionedEntry versionedEntry = _versionedEntryPersistence.create(pk);

		versionedEntry.setMvccVersion(RandomTestUtil.nextLong());

		versionedEntry.setHeadId(RandomTestUtil.nextLong());

		versionedEntry.setGroupId(RandomTestUtil.nextLong());

		_versionedEntries.add(
			_versionedEntryPersistence.update(versionedEntry));

		return versionedEntry;
	}

	private final List<VersionedEntry> _versionedEntries = new ArrayList<>();

	@Inject
	private VersionedEntryPersistence _versionedEntryPersistence;

}