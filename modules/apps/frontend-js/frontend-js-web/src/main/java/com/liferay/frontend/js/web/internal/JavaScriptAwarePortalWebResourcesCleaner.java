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

import com.liferay.portal.kernel.servlet.JavaScriptAwarePortalWebResources;

import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import javax.servlet.ServletContext;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;

/**
 * @author Shuyang Zhou
 */
@Component(service = {})
public class JavaScriptAwarePortalWebResourcesCleaner {

	@Activate
	protected void activate(BundleContext bundleContext)
		throws InvalidSyntaxException {

		_serviceListener = serviceEvent -> {
			ServiceReference<?> serviceReference =
				serviceEvent.getServiceReference();

			Bundle bundle = serviceReference.getBundle();

			Stream<JavaScriptAwarePortalWebResources> stream =
				_javaScriptAwarePortalWebResourcesList.stream();

			stream.forEach(
				cacheable -> cacheable.updateLastModifed(
					bundle.getLastModified()));
		};

		bundleContext.addServiceListener(
			_serviceListener,
			"(&(!(javax.portlet.name=*))(language.id=*)(objectClass=" +
				ResourceBundle.class.getName() + "))");
	}

	@Deactivate
	protected void deactivate(BundleContext bundleContext) {
		bundleContext.removeServiceListener(_serviceListener);
	}

	@Reference(
		cardinality = ReferenceCardinality.MULTIPLE,
		policy = ReferencePolicy.DYNAMIC,
		policyOption = ReferencePolicyOption.GREEDY,
		unbind = "_removeJavaScriptAwarePortalWebResources"
	)
	private void _addJavaScriptAwarePortalWebResources(
		JavaScriptAwarePortalWebResources javaScriptAwarePortalWebResources) {

		_javaScriptAwarePortalWebResourcesList.add(
			javaScriptAwarePortalWebResources);
	}

	private void _removeJavaScriptAwarePortalWebResources(
		JavaScriptAwarePortalWebResources javaScriptAwarePortalWebResources) {

		_javaScriptAwarePortalWebResourcesList.remove(
			javaScriptAwarePortalWebResources);
	}

	private final List<JavaScriptAwarePortalWebResources>
		_javaScriptAwarePortalWebResourcesList = new CopyOnWriteArrayList<>();
	private ServiceListener _serviceListener;

	@Reference(
		target = "(&(original.bean=true)(bean.id=javax.servlet.ServletContext))"
	)
	private ServletContext _servletContext;

}