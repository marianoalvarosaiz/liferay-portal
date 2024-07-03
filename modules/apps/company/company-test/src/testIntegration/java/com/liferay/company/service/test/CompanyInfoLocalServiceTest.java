/**
 * SPDX-FileCopyrightText: (c) 2000 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.company.service.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.counter.kernel.service.CounterLocalService;
import com.liferay.petra.lang.SafeCloseable;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.cache.PortalCacheException;
import com.liferay.portal.kernel.cache.PortalCacheManager;
import com.liferay.portal.kernel.cache.PortalCacheManagerListener;
import com.liferay.portal.kernel.cache.PortalCacheManagerNames;
import com.liferay.portal.kernel.cache.PortalCacheManagerProvider;
import com.liferay.portal.kernel.dao.orm.EntityCache;
import com.liferay.portal.kernel.dao.orm.FinderCache;
import com.liferay.portal.kernel.encryptor.Encryptor;
import com.liferay.portal.kernel.model.Company;
import com.liferay.portal.kernel.model.CompanyConstants;
import com.liferay.portal.kernel.model.CompanyInfo;
import com.liferay.portal.kernel.security.auth.CompanyThreadLocal;
import com.liferay.portal.kernel.service.CompanyInfoLocalService;
import com.liferay.portal.kernel.service.CompanyLocalService;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.rule.DeleteAfterTestRun;
import com.liferay.portal.kernel.test.util.CompanyTestUtil;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.model.impl.CompanyInfoImpl;
import com.liferay.portal.test.rule.Inject;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;
import com.liferay.portal.test.rule.PermissionCheckerMethodTestRule;

import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Alberto Chaparro
 */
@RunWith(Arquillian.class)
public class CompanyInfoLocalServiceTest {

	@ClassRule
	@Rule
	public static final AggregateTestRule aggregateTestRule =
		new AggregateTestRule(
			new LiferayIntegrationTestRule(),
			PermissionCheckerMethodTestRule.INSTANCE);

	@Before
	public void setUp() throws Exception {
		_company = CompanyTestUtil.addCompany();
	}

	@Test
	public void testDeleteCompanyInfo() throws Exception {
		long companyId = _company.getCompanyId();

		CompanyInfo companyInfo = null;

		try (SafeCloseable safeCloseable =
				CompanyThreadLocal.setWithSafeCloseable(companyId)) {

			companyInfo = _companyInfoLocalService.fetchCompany(companyId);
		}

		PortalCacheManagerListener portalCacheManagerListener =
			new CacheRemovedPortalCacheManagerListener();

		_portalCacheManager.registerPortalCacheManagerListener(
			portalCacheManagerListener);

		_entityCacheRemoved = false;
		_finderCacheRemoved = false;

		try {
			_companyLocalService.deleteCompany(_company);
		}
		finally {
			_portalCacheManager.unregisterPortalCacheManagerListener(
				portalCacheManagerListener);
		}

		_company = null;

		try (SafeCloseable safeCloseable =
				CompanyThreadLocal.setWithSafeCloseable(
					CompanyConstants.SYSTEM)) {

			Assert.assertNull(
				_companyInfoLocalService.fetchCompanyInfo(
					companyInfo.getCompanyInfoId()));
		}

		Assert.assertTrue(_entityCacheRemoved);
		Assert.assertTrue(_finderCacheRemoved);
	}

	@Test
	public void testGetCompanyInfoKey() {
		try (SafeCloseable safeCloseable =
				CompanyThreadLocal.setWithSafeCloseable(
					_company.getCompanyId())) {

			CompanyInfo companyInfo = _companyInfoLocalService.fetchCompany(
				_company.getCompanyId());

			Assert.assertEquals(companyInfo.getKey(), _company.getKey());
		}
	}

	@Test
	public void testGetCompanyInfoKeyObj() {
		try (SafeCloseable safeCloseable =
				CompanyThreadLocal.setWithSafeCloseable(
					_company.getCompanyId())) {

			CompanyInfo companyInfo = _companyInfoLocalService.fetchCompany(
				_company.getCompanyId());

			Assert.assertEquals(
				_encryptor.deserializeKey(companyInfo.getKey()),
				_company.getKeyObj());
		}
	}

	@Test
	public void testUpdateCompanyInfoKey() {
		_company.setKey(RandomTestUtil.randomString());

		_company = _companyLocalService.updateCompany(_company);

		try (SafeCloseable safeCloseable =
				CompanyThreadLocal.setWithSafeCloseable(
					_company.getCompanyId())) {

			CompanyInfo companyInfo = _companyInfoLocalService.fetchCompany(
				_company.getCompanyId());

			Assert.assertEquals(companyInfo.getKey(), _company.getKey());
		}
	}

	@Test
	public void testUpdateCompanyInfoKeyObj() {
		_company.setKey(RandomTestUtil.randomString());

		_company = _companyLocalService.updateCompany(_company);

		try (SafeCloseable safeCloseable =
				CompanyThreadLocal.setWithSafeCloseable(
					_company.getCompanyId())) {

			CompanyInfo companyInfo = _companyInfoLocalService.fetchCompany(
				_company.getCompanyId());

			Assert.assertEquals(
				_encryptor.deserializeKey(companyInfo.getKey()),
				_company.getKeyObj());
		}
	}

	private static final String _ENTITY_CACHE_PREFIX =
		EntityCache.class.getName() + StringPool.PERIOD +
			CompanyInfoImpl.class.getName();

	private static final String _FINDER_CACHE_PREFIX =
		FinderCache.class.getName() + StringPool.PERIOD +
			CompanyInfoImpl.class.getName();

	@DeleteAfterTestRun
	private static Company _company;

	@Inject
	private static CompanyInfoLocalService _companyInfoLocalService;

	@Inject
	private static CompanyLocalService _companyLocalService;

	@Inject
	private static CounterLocalService _counterLocalService;

	@Inject
	private static Encryptor _encryptor;

	private static boolean _entityCacheRemoved;
	private static boolean _finderCacheRemoved;
	private static final PortalCacheManager<?, ?> _portalCacheManager =
		PortalCacheManagerProvider.getPortalCacheManager(
			PortalCacheManagerNames.MULTI_VM);

	private static class CacheRemovedPortalCacheManagerListener
		implements PortalCacheManagerListener {

		@Override
		public void dispose() throws PortalCacheException {
		}

		@Override
		public void init() throws PortalCacheException {
		}

		@Override
		public void notifyPortalCacheAdded(String portalCacheName) {
		}

		@Override
		public void notifyPortalCacheRemoved(String portalCacheName) {
			if (!StringUtil.endsWith(
					portalCacheName, String.valueOf(_company.getCompanyId()))) {

				return;
			}

			if (StringUtil.startsWith(portalCacheName, _ENTITY_CACHE_PREFIX)) {
				_entityCacheRemoved = true;
			}
			else if (StringUtil.startsWith(
						portalCacheName, _FINDER_CACHE_PREFIX)) {

				_finderCacheRemoved = true;
			}
		}

	}

}