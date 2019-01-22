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

package com.liferay.portal.servlet.filters.secure;

import com.liferay.portal.kernel.cache.PortalCache;
import com.liferay.portal.kernel.cache.PortalCacheHelperUtil;
import com.liferay.portal.kernel.cache.PortalCacheListener;
import com.liferay.portal.kernel.cache.PortalCacheListenerScope;
import com.liferay.portal.kernel.cache.PortalCacheManagerNames;
import com.liferay.portal.kernel.cluster.ClusterMasterExecutorUtil;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.Company;
import com.liferay.portal.kernel.service.CompanyLocalServiceUtil;
import com.liferay.portal.kernel.util.Digester;
import com.liferay.portal.kernel.util.DigesterUtil;
import com.liferay.portal.kernel.util.MethodHandler;
import com.liferay.portal.kernel.util.MethodKey;
import com.liferay.portal.kernel.util.Time;
import com.liferay.portal.util.PropsValues;

import java.io.Serializable;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * @author Alexander Chow
 */
public class NonceUtil {

	public static String generate(long companyId, String remoteAddress) {
		String companyKey = null;

		try {
			Company company = CompanyLocalServiceUtil.getCompanyById(companyId);

			companyKey = company.getKey();
		}
		catch (Exception e) {
			throw new RuntimeException("Invalid companyId " + companyId, e);
		}

		long timestamp = System.currentTimeMillis();

		String nonce = DigesterUtil.digestHex(
			Digester.MD5, remoteAddress, String.valueOf(timestamp), companyKey);

		_noncePortalCache.put(nonce, new Nonce(nonce));

		return nonce;
	}

	public static boolean verify(String nonce) {
		Nonce nonceObject = _noncePortalCache.get(nonce);

		if ((nonceObject != null) && !nonceObject.isExpired()) {
			_noncePortalCache.remove(nonce);

			return true;
		}

		return false;
	}

	private static void _cacheBootstrap() {
		try {
			if (!ClusterMasterExecutorUtil.isEnabled() ||
				ClusterMasterExecutorUtil.isMaster()) {

				return;
			}

			MethodHandler methodHandler = new MethodHandler(
				_getNoncesMethodKey);

			Future<Set<Nonce>> future =
				ClusterMasterExecutorUtil.executeOnMaster(methodHandler);

			Set<Nonce> noncesRetrieved = future.get(_TIMEOUT, TimeUnit.SECONDS);

			_nonces.addAll(noncesRetrieved);

			for (Nonce nonce : _nonces) {
				PortalCacheHelperUtil.putWithoutReplicator(
					_noncePortalCache, nonce._nonce, nonce);
			}
		}
		catch (Exception exception) {
			_log.error("Unable to retrieve nonces from master", exception);
		}
		finally {
			_noncePortalCache.registerPortalCacheListener(
				new NonceDelayedPortalCacheListener(),
				PortalCacheListenerScope.ALL);
		}
	}

	private static Set<Nonce> _getNonces() {
		return _nonces;
	}

	private static final long _NONCE_EXPIRATION =
		PropsValues.WEBDAV_NONCE_EXPIRATION * Time.MINUTE;

	private static final long _TIMEOUT = 10L;

	private static final Log _log = LogFactoryUtil.getLog(NonceUtil.class);

	private static final MethodKey _getNoncesMethodKey = new MethodKey(
		NonceUtil.class, "_getNonces");
	private static final PortalCache<String, Nonce> _noncePortalCache =
		PortalCacheHelperUtil.getPortalCache(
			PortalCacheManagerNames.MULTI_VM, NonceUtil.class.getName());
	private static final Set<Nonce> _nonces = new HashSet<>();

	static {
		_cacheBootstrap();
	}

	private static class Nonce implements Serializable {

		public Nonce(String nonce) {
			if (nonce == null) {
				throw new NullPointerException("Nonce is null");
			}

			_createTime = System.currentTimeMillis();

			_expirationTime = _NONCE_EXPIRATION + _createTime;

			_nonce = nonce;
		}

		@Override
		public boolean equals(Object obj) {
			Nonce nonce = (Nonce)obj;

			if (_nonce.equals(nonce._nonce)) {
				return true;
			}

			return false;
		}

		@Override
		public int hashCode() {
			return _nonce.hashCode();
		}

		public boolean isExpired() {
			if (System.currentTimeMillis() > _expirationTime) {
				return true;
			}

			return false;
		}

		private final long _createTime;
		private final long _expirationTime;
		private final String _nonce;

	}

	private static class NonceDelayedPortalCacheListener
		implements PortalCacheListener<String, Nonce> {

		@Override
		public void dispose() {
		}

		@Override
		public void notifyEntryEvicted(
			PortalCache<String, Nonce> portalCache, String key, Nonce nonce,
			int timeToLive) {

			_nonces.remove(nonce);
		}

		@Override
		public void notifyEntryExpired(
			PortalCache<String, Nonce> portalCache, String key, Nonce nonce,
			int timeToLive) {

			_nonces.remove(nonce);
		}

		@Override
		public void notifyEntryPut(
			PortalCache<String, Nonce> portalCache, String key, Nonce nonce,
			int timeToLive) {

			_nonces.add(nonce);
		}

		@Override
		public void notifyEntryRemoved(
			PortalCache<String, Nonce> portalCache, String key, Nonce nonce,
			int timeToLive) {

			_nonces.remove(nonce);
		}

		@Override
		public void notifyEntryUpdated(
			PortalCache<String, Nonce> portalCache, String key, Nonce nonce,
			int timeToLive) {

			notifyEntryPut(portalCache, key, nonce, timeToLive);
		}

		@Override
		public void notifyRemoveAll(PortalCache<String, Nonce> portalCache) {
			_nonces.clear();
		}

	}

}