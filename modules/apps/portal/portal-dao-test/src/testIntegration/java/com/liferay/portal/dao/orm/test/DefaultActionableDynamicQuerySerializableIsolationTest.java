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

package com.liferay.portal.dao.orm.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.petra.lang.SafeCloseable;
import com.liferay.portal.kernel.dao.orm.ActionableDynamicQuery;
import com.liferay.portal.kernel.dao.orm.DefaultActionableDynamicQuery;
import com.liferay.portal.kernel.dao.orm.FinderCacheUtil;
import com.liferay.portal.kernel.model.Company;
import com.liferay.portal.kernel.service.CompanyLocalService;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.util.PropsValuesTestUtil;
import com.liferay.portal.kernel.transaction.Propagation;
import com.liferay.portal.kernel.transaction.TransactionConfig;
import com.liferay.portal.kernel.transaction.TransactionInvokerUtil;
import com.liferay.portal.test.rule.Inject;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Mariano Álvaro Sáiz
 */
@RunWith(Arquillian.class)
public class DefaultActionableDynamicQuerySerializableIsolationTest {

	@ClassRule
	@Rule
	public static final AggregateTestRule aggregateTestRule =
		new LiferayIntegrationTestRule();

	@Test
	public void testTransactionLocks() throws Throwable {
		try (SafeCloseable safeCloseable =
				PropsValuesTestUtil.swapWithSafeCloseable(
					"TRANSACTION_ISOLATION_PORTAL", 8)) {

			TransactionInvokerUtil.invoke(
				TransactionConfig.Factory.create(
					Propagation.REQUIRED, new Class<?>[] {Exception.class}),
				() -> {
					FinderCacheUtil.clearCache();

					_companyLocalService.getCompanies();

					ActionableDynamicQuery actionableDynamicQuery =
						_companyLocalService.getActionableDynamicQuery();

					actionableDynamicQuery.setPerformActionMethod(
						company -> _companyLocalService.updateCompany(
							(Company)company));

					actionableDynamicQuery.setTransactionConfig(
						DefaultActionableDynamicQuery.
							REQUIRES_NEW_TRANSACTION_CONFIG);

					actionableDynamicQuery.performActions();

					return null;
				});
		}
	}

	@Inject
	private CompanyLocalService _companyLocalService;

}