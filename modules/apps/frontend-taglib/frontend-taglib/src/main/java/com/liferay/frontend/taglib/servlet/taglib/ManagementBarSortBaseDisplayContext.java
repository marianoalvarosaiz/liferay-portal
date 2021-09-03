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

package com.liferay.frontend.taglib.servlet.taglib;

import com.liferay.portal.kernel.portlet.PortalPreferences;
import com.liferay.portal.kernel.portlet.PortletPreferencesFactoryUtil;
import com.liferay.portal.kernel.util.ParamUtil;
import com.liferay.portal.kernel.util.Validator;

import javax.servlet.http.HttpServletRequest;

/**
 * @author Mariano Álvaro Sáiz
 */
public class ManagementBarSortBaseDisplayContext {

	public ManagementBarSortBaseDisplayContext(
		HttpServletRequest httpServletRequest, String namespace) {

		this.httpServletRequest = httpServletRequest;
		_namespace = namespace;

		_portalPreferences = PortletPreferencesFactoryUtil.getPortalPreferences(
			httpServletRequest);
	}

	protected String getOrderByCol(String defaultCol) {
		if (Validator.isNotNull(_orderByCol)) {
			return _orderByCol;
		}

		String orderByCol = ParamUtil.getString(
			httpServletRequest, "orderByCol");

		if (Validator.isNotNull(orderByCol)) {
			_portalPreferences.setValue(_namespace, "order-by-col", orderByCol);
		}
		else {
			orderByCol = _portalPreferences.getValue(
				_namespace, "order-by-col", defaultCol);
		}

		_orderByCol = orderByCol;

		return _orderByCol;
	}

	protected String getOrderByType(String defaultType) {
		if (Validator.isNotNull(_orderByType)) {
			return _orderByType;
		}

		String orderByType = ParamUtil.getString(
			httpServletRequest, "orderByType");

		if (Validator.isNotNull(orderByType)) {
			_portalPreferences.setValue(
				_namespace, "order-by-type", orderByType);
		}
		else {
			orderByType = _portalPreferences.getValue(
				_namespace, "order-by-type", defaultType);
		}

		_orderByType = orderByType;

		return _orderByType;
	}

	protected final HttpServletRequest httpServletRequest;

	private final String _namespace;
	private String _orderByCol;
	private String _orderByType;
	private final PortalPreferences _portalPreferences;

}