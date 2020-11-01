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

package com.liferay.portal.service.impl;

import com.liferay.petra.lang.HashUtil;
import com.liferay.portal.kernel.cache.MultiVMPool;
import com.liferay.portal.kernel.cache.PortalCache;
import com.liferay.portal.kernel.cache.index.IndexEncoder;
import com.liferay.portal.kernel.cache.index.PortalCacheIndexer;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.util.ServiceProxyFactory;

import java.io.Serializable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * @author Mariano Álvaro Sáiz
 */
public class GroupLocalServiceCache {

	public static void clearCache() {
		_portalCache.removeAll();
	}

	public static void clearCache(Group group) {
		Set<CompanyActiveGroupKey> keys = _groupIdsPortalCacheIndexer.getKeys(
			CompanyGroupKeyIndexEncoder.encode(group.getCompanyId()));

		Stream<CompanyActiveGroupKey> stream = keys.stream();

		stream.forEach(key -> _portalCache.remove(key));
	}

	public static List<Long> getGroupIds(long companyId, boolean active) {
		return (List<Long>)_portalCache.get(
			new CompanyActiveGroupKey(companyId, active));
	}

	public static void putGroupIds(
		long companyId, boolean active, List<Long> groupIds) {

		_portalCache.put(
			new CompanyActiveGroupKey(companyId, active),
			new ArrayList<>(groupIds));
	}

	private static final PortalCacheIndexer
		<String, CompanyActiveGroupKey, Serializable>
			_groupIdsPortalCacheIndexer;
	private static volatile MultiVMPool _multiVMPool =
		ServiceProxyFactory.newServiceTrackedInstance(
			MultiVMPool.class, GroupLocalServiceCache.class, "_multiVMPool",
			true);
	private static final PortalCache<CompanyActiveGroupKey, Serializable>
		_portalCache;

	private static class CompanyActiveGroupKey implements Serializable {

		@Override
		public boolean equals(Object object) {
			CompanyActiveGroupKey companyActiveGroupKey =
				(CompanyActiveGroupKey)object;

			if ((companyActiveGroupKey._companyId == _companyId) &&
				(companyActiveGroupKey._active == _active)) {

				return true;
			}

			return false;
		}

		@Override
		public int hashCode() {
			int hashCode = HashUtil.hash(0, _companyId);

			return HashUtil.hash(hashCode, _active);
		}

		private CompanyActiveGroupKey(long companyId, boolean active) {
			_companyId = companyId;
			_active = active;
		}

		private static final long serialVersionUID = 1L;

		private final boolean _active;
		private final long _companyId;

	}

	private static class CompanyGroupKeyIndexEncoder
		implements IndexEncoder<String, CompanyActiveGroupKey> {

		public static String encode(long companyId) {
			return String.valueOf(companyId);
		}

		@Override
		public String encode(CompanyActiveGroupKey companyActiveGroupKey) {
			return encode(companyActiveGroupKey._companyId);
		}

	}

	static {
		_portalCache =
			(PortalCache<CompanyActiveGroupKey, Serializable>)
				_multiVMPool.getPortalCache(
					GroupLocalServiceCache.class.getName());

		_groupIdsPortalCacheIndexer = new PortalCacheIndexer<>(
			new CompanyGroupKeyIndexEncoder(), _portalCache);
	}

}