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
import com.liferay.portal.kernel.model.change.tracking.CTModel;
import com.liferay.portal.kernel.service.persistence.change.tracking.helper.CTPersistenceHelper;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.transaction.Propagation;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;
import com.liferay.portal.test.rule.TransactionalTestRule;
import com.liferay.portal.tools.service.builder.test.model.CacheMissEntry;
import com.liferay.portal.tools.service.builder.test.service.persistence.CacheMissEntryPersistence;
import com.liferay.portal.tools.service.builder.test.service.persistence.CacheMissEntryUtil;
import com.liferay.registry.Registry;
import com.liferay.registry.RegistryUtil;

import java.io.Serializable;

import java.util.Dictionary;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceRegistration;

/**
 * @author Minhchau Dang
 */
@RunWith(Arquillian.class)
public class CacheMissPersistenceImplTest {

	@ClassRule
	@Rule
	public static final AggregateTestRule aggregateTestRule =
		new AggregateTestRule(
			new LiferayIntegrationTestRule(),
			new TransactionalTestRule(
				Propagation.REQUIRED,
				"com.liferay.portal.tools.service.builder.test.service"));

	@BeforeClass
	public static void setUpClass() throws Exception {
		_serviceRegistration = _registerCTPersistenceHelper();
	}

	@AfterClass
	public static void tearDownClass() {
		_serviceRegistration.unregister();
	}

	@Before
	public void setUp() {
		_persistence = CacheMissEntryUtil.getPersistence();
	}

	@Test
	public void testCacheMissIfCTProductionModeDisabled() throws Throwable {
		_setProductionMode(false);

		Set<Serializable> primaryKeys = new HashSet<>();

		for (long pk = -1; pk > -2000; pk--) {
			primaryKeys.add(pk);
		}

		Map<Serializable, CacheMissEntry> cacheMissEntries =
			_persistence.fetchByPrimaryKeys(primaryKeys);

		Assert.assertTrue(cacheMissEntries.isEmpty());
	}

	@Test
	public void testCacheMissIfCTProductionModeEnabled() throws Throwable {
		_setProductionMode(true);

		Set<Serializable> primaryKeys = new HashSet<>();

		for (long pk = -1; pk > -2000; pk--) {
			primaryKeys.add(pk);
		}

		Map<Serializable, CacheMissEntry> cacheMissEntries =
			_persistence.fetchByPrimaryKeys(primaryKeys);

		Assert.assertTrue(cacheMissEntries.isEmpty());
	}

	private static ServiceRegistration<CTPersistenceHelper>
			_registerCTPersistenceHelper()
		throws Exception {

		Registry registry = RegistryUtil.getRegistry();

		CTPersistenceHelper ctPersistenceHelper = registry.getService(
			registry.getServiceReference(CTPersistenceHelper.class));

		Bundle bundle = FrameworkUtil.getBundle(ctPersistenceHelper.getClass());

		BundleContext bundleContext = bundle.getBundleContext();

		Dictionary<String, Object> properties = new Hashtable<>();

		properties.put("service.ranking", 100);

		return bundleContext.registerService(
			CTPersistenceHelper.class,
			new CTPersistenceHelperProductionModeAware(ctPersistenceHelper),
			properties);
	}

	private void _setProductionMode(boolean productionMode) {
		_productionMode = productionMode;
	}

	private static boolean _productionMode;
	private static ServiceRegistration<CTPersistenceHelper>
		_serviceRegistration;

	private CacheMissEntryPersistence _persistence;

	private static class CTPersistenceHelperProductionModeAware
		implements CTPersistenceHelper {

		public CTPersistenceHelperProductionModeAware(
			CTPersistenceHelper ctPersistenceHelper) {

			_ctPersistenceHelper = ctPersistenceHelper;
		}

		@Override
		public <T extends CTModel<T>> boolean isInsert(T ctModel) {
			return _ctPersistenceHelper.isInsert(ctModel);
		}

		@Override
		public <T extends CTModel<T>> boolean isProductionMode(
			Class<T> ctModelClass) {

			return _productionMode;
		}

		@Override
		public <T extends CTModel<T>> boolean isRemove(T ctModel) {
			return _ctPersistenceHelper.isRemove(ctModel);
		}

		private final CTPersistenceHelper _ctPersistenceHelper;

	}

}