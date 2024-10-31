/**
 * SPDX-FileCopyrightText: (c) 2000 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.portal.instances.service.impl;

import com.liferay.portal.aop.AopService;
import com.liferay.portal.instances.service.base.PortalInstancesLocalServiceBaseImpl;
import com.liferay.portal.kernel.cluster.Clusterable;
import com.liferay.portal.kernel.instance.PortalInstancePool;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.service.CompanyLocalService;
import com.liferay.portal.kernel.util.ArrayUtil;
import com.liferay.portal.kernel.util.ListUtil;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.util.PortalInstances;

import java.util.Arrays;
import java.util.List;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * @author Michael C. Han
 */
@Component(
	property = "model.class.name=com.liferay.portal.util.PortalInstances",
	service = AopService.class
)
public class PortalInstancesLocalServiceImpl
	extends PortalInstancesLocalServiceBaseImpl {

	@Override
	public long[] getCompanyIds() {
		return PortalInstancePool.getCompanyIds();
	}

	@Override
	public long getDefaultCompanyId() {
		return PortalInstancePool.getDefaultCompanyId();
	}

	@Clusterable
	@Override
	public void synchronizePortalInstances() {
		try {
			_log.error("Performing synchronization with default companyId: " + PortalInstancePool.getDefaultCompanyId());
			
			long[] initializedCompanyIds = _portal.getCompanyIds();

			List<Long> removeableCompanyIds = ListUtil.fromArray(
				initializedCompanyIds);

			_companyLocalService.forEachCompany(
				company -> {
					_log.error("For companyId: " + company.getCompanyId());
					
					removeableCompanyIds.remove(company.getCompanyId());

					if (ArrayUtil.contains(
							initializedCompanyIds, company.getCompanyId())) {
						
						_log.error("CompanyId: " + company.getCompanyId() + " already initialized");

						return;
					}
					
					_log.error("Init company: " + company.getCompanyId());

					PortalInstances.initCompany(company);
					
					_log.error("PortalInstances size: " + PortalInstancePool.getCompanyIds().length);
				});
			
			_log.error("Remove company: " + Arrays.toString(ArrayUtil.toLongArray(removeableCompanyIds)));

			_companyLocalService.forEachCompanyId(
				companyId -> PortalInstances.removeCompany(companyId),
				ArrayUtil.toLongArray(removeableCompanyIds));
		}
		catch (Exception exception) {
			_log.error(exception);
		}
	}

	private static final Log _log = LogFactoryUtil.getLog(
		PortalInstancesLocalServiceImpl.class);

	@Reference
	private CompanyLocalService _companyLocalService;

	@Reference
	private Portal _portal;

}