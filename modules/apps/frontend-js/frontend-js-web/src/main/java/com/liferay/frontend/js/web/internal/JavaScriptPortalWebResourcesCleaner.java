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

package com.liferay.frontend.js.web.internal;

import com.liferay.frontend.js.loader.modules.extender.npm.JSBundle;

import java.util.Map;
import java.util.ResourceBundle;

import javax.servlet.ServletContext;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.util.tracker.BundleTracker;
import org.osgi.util.tracker.BundleTrackerCustomizer;

/**
 * @author Shuyang Zhou
 */
@Component(service = {})
public class JavaScriptPortalWebResourcesCleaner {

	@Activate
	protected void activate(BundleContext bundleContext)
		throws InvalidSyntaxException {

		_serviceListener = serviceEvent -> {
			ServiceReference<?> serviceReference =
				serviceEvent.getServiceReference();

			Bundle bundle = serviceReference.getBundle();

			_javaScriptPortalWebResources.updateLastModifed(
				bundle.getLastModified());
		};

		bundleContext.addServiceListener(
			_serviceListener,
			"(&(!(javax.portlet.name=*))(language.id=*)(objectClass=" +
				ResourceBundle.class.getName() + "))");

		_bundleTracker = new BundleTracker<>(
			bundleContext, Bundle.ACTIVE,
			new JavaScriptPortalWebResourcesBundleTrackerCustomizer());

		_bundleTracker.open();
	}

	@Deactivate
	protected void deactivate(BundleContext bundleContext) {
		bundleContext.removeServiceListener(_serviceListener);

		_bundleTracker.close();
	}

	private BundleTracker<JSBundle> _bundleTracker;

	@Reference
	private JavaScriptPortalWebResources _javaScriptPortalWebResources;

	private ServiceListener _serviceListener;

	@Reference(
		target = "(&(original.bean=true)(bean.id=javax.servlet.ServletContext))"
	)
	private ServletContext _servletContext;

	private class JavaScriptPortalWebResourcesBundleTrackerCustomizer
		implements BundleTrackerCustomizer<JSBundle> {

		@Override
		public JSBundle addingBundle(Bundle bundle, BundleEvent bundleEvent) {
			_javaScriptPortalWebResources.updateLastModifed(
				bundle.getLastModified());

			Map<Bundle, JSBundle> tracked = _bundleTracker.getTracked();

			return tracked.get(bundle);
		}

		@Override
		public void modifiedBundle(
			Bundle bundle, BundleEvent bundleEvent, JSBundle jsBundle) {
		}

		@Override
		public void removedBundle(
			Bundle bundle, BundleEvent bundleEvent, JSBundle jsBundle) {
		}

	}

}