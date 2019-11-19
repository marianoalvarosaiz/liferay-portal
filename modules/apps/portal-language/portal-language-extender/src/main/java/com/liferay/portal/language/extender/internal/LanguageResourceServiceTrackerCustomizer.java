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

package com.liferay.portal.language.extender.internal;

import com.liferay.osgi.util.ServiceTrackerFactory;
import com.liferay.portal.kernel.util.CacheResourceBundleLoader;

import java.util.ResourceBundle;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

/**
 * @author Mariano Álvaro Sáiz
 */
@Component(immediate = true, service = {})
public class LanguageResourceServiceTrackerCustomizer
	implements ServiceTrackerCustomizer<ResourceBundle, ResourceBundle> {

	@Override
	public ResourceBundle addingService(
		ServiceReference<ResourceBundle> serviceReference) {

		ResourceBundle resourceBundle = _bundleContext.getService(
			serviceReference);

		CacheResourceBundleLoader.setResourceBundleLastModifiedTime(
			System.currentTimeMillis());

		return resourceBundle;
	}

	@Override
	public void modifiedService(
		ServiceReference<ResourceBundle> serviceReference,
		ResourceBundle resourceBundle) {
	}

	@Override
	public void removedService(
		ServiceReference<ResourceBundle> serviceReference,
		ResourceBundle resourceBundle) {

		CacheResourceBundleLoader.setResourceBundleLastModifiedTime(
			System.currentTimeMillis());
	}

	@Activate
	protected void activate(BundleContext bundleContext) {
		_bundleContext = bundleContext;

		_serviceTracker = ServiceTrackerFactory.open(
			_bundleContext,
			"(&(!(javax.portlet.name=*))(language.id=*)(objectClass=" +
				ResourceBundle.class.getName() + "))",
			this);
	}

	@Deactivate
	protected void deactivate() {
		_serviceTracker.close();
	}

	private BundleContext _bundleContext;
	private ServiceTracker<ResourceBundle, ResourceBundle> _serviceTracker;

}