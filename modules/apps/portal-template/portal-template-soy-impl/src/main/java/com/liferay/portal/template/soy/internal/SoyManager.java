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

package com.liferay.portal.template.soy.internal;

import com.liferay.osgi.util.ServiceTrackerFactory;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.cache.PortalCache;
import com.liferay.portal.kernel.cache.SingleVMPool;
import com.liferay.portal.kernel.template.Template;
import com.liferay.portal.kernel.template.TemplateConstants;
import com.liferay.portal.kernel.template.TemplateManager;
import com.liferay.portal.kernel.template.TemplateResource;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.template.BaseTemplateManager;
import com.liferay.portal.template.TemplateContextHelper;
import com.liferay.portal.template.soy.SoyTemplateResource;
import com.liferay.portal.template.soy.SoyTemplateResourceFactory;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.util.tracker.BundleTracker;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

/**
 * @author Bruno Basto
 */
@Component(
	immediate = true,
	property = "language.type=" + TemplateConstants.LANG_TYPE_SOY,
	service = {SoyManager.class, TemplateManager.class}
)
public class SoyManager extends BaseTemplateManager {

	@Override
	public void destroy() {
		templateContextHelper.removeAllHelperUtilities();
	}

	@Override
	public void destroy(ClassLoader classLoader) {
		templateContextHelper.removeHelperUtilities(classLoader);
	}

	/**
	 * @deprecated As of Mueller (7.2.x), with no direct replacement
	 */
	@Deprecated
	public List<TemplateResource> getAllTemplateResources() {
		return _soyCapabilityBundleTrackerCustomizer.getAllTemplateResources();
	}

	@Override
	public String getName() {
		return TemplateConstants.LANG_TYPE_SOY;
	}

	@Override
	public void init() {
	}

	@Reference(unbind = "-")
	public void setSingleVMPool(SingleVMPool singleVMPool) {
		_soyTofuCacheHandler = new SoyTofuCacheHandler(
			(PortalCache<String, SoyTofuCacheBag>)singleVMPool.getPortalCache(
				SoyTemplate.class.getName()));
	}

	@Override
	@Reference(service = SoyTemplateContextHelper.class, unbind = "-")
	public void setTemplateContextHelper(
		TemplateContextHelper templateContextHelper) {

		super.setTemplateContextHelper(templateContextHelper);
	}

	@Activate
	protected void activate(BundleContext bundleContext) {
		_bundleContext = bundleContext;

		int stateMask = ~Bundle.INSTALLED & ~Bundle.UNINSTALLED;

		_soyCapabilityBundleTrackerCustomizer =
			new SoyTemplateResourceBundleTrackerCustomizer(
				_soyTofuCacheHandler, _soyProviderCapabilityBundleRegister,
				_soyTemplateResourceFactory);

		_bundleTracker = new BundleTracker<>(
			bundleContext, stateMask, _soyCapabilityBundleTrackerCustomizer);

		_bundleTracker.open();

		_serviceTracker = ServiceTrackerFactory.open(
			_bundleContext,
			"(&(!(javax.portlet.name=*))(language.id=*)(objectClass=" +
				ResourceBundle.class.getName() + "))",
			new LanguageResourceServiceTrackerCustomizer());
	}

	@Deactivate
	protected void deactivate() {
		_bundleTracker.close();

		_serviceTracker.close();
	}

	@Override
	protected Template doGetTemplate(
		TemplateResource templateResource, boolean restricted,
		Map<String, Object> helperUtilities) {

		SoyTemplateResource soyTemplateResource = null;

		if (templateResource == null) {
			soyTemplateResource =
				_soyCapabilityBundleTrackerCustomizer.getSoyTemplateResource();
		}
		else if (templateResource instanceof SoyTemplateResource) {
			soyTemplateResource = (SoyTemplateResource)templateResource;
		}
		else {
			soyTemplateResource =
				_soyTemplateResourceFactory.createSoyTemplateResource(
					Collections.singletonList(templateResource));
		}

		return new SoyTemplate(
			soyTemplateResource, helperUtilities,
			(SoyTemplateContextHelper)templateContextHelper,
			_soyTofuCacheHandler, _soyTemplateResourceFactory, restricted);
	}

	@Reference(unbind = "-")
	protected void setSoyProviderCapabilityBundleRegister(
		SoyProviderCapabilityBundleRegister
			soyProviderCapabilityBundleRegister) {

		_soyProviderCapabilityBundleRegister =
			soyProviderCapabilityBundleRegister;
	}

	@Reference(unbind = "-")
	protected void setSoyTemplateBundleResourceParser(
		SoyTemplateBundleResourceParser soyTemplateBundleResourceParser) {
	}

	private BundleContext _bundleContext;
	private BundleTracker<List<TemplateResource>> _bundleTracker;
	private ServiceTracker<ResourceBundle, ResourceBundle> _serviceTracker;
	private SoyTemplateResourceBundleTrackerCustomizer
		_soyCapabilityBundleTrackerCustomizer;
	private SoyProviderCapabilityBundleRegister
		_soyProviderCapabilityBundleRegister;

	@Reference
	private SoyTemplateResourceFactory _soyTemplateResourceFactory;

	private SoyTofuCacheHandler _soyTofuCacheHandler;

	private class LanguageResourceServiceTrackerCustomizer
		implements ServiceTrackerCustomizer<ResourceBundle, ResourceBundle> {

		@Override
		public ResourceBundle addingService(
			ServiceReference<ResourceBundle> serviceReference) {

			String languageId = GetterUtil.getString(
				serviceReference.getProperty("language.id"));

			Locale locale;

			if (Validator.isNotNull(languageId)) {
				locale = LocaleUtil.fromLanguageId(languageId, true);
			}
			else {
				locale = new Locale(StringPool.BLANK);
			}

			_soyTofuCacheHandler.removeIfAny(locale);

			return _bundleContext.getService(serviceReference);
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

			addingService(serviceReference);
		}

	}

}