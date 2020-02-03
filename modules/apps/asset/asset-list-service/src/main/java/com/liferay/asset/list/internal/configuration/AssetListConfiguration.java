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

package com.liferay.asset.list.internal.configuration;

import aQute.bnd.annotation.metatype.Meta;

import com.liferay.portal.configuration.metatype.annotations.ExtendedObjectClassDefinition;

/**
 * @author Ricardo Couso
 */
@ExtendedObjectClassDefinition(
	category = "assets",
	scope = ExtendedObjectClassDefinition.Scope.SYSTEM
)
@Meta.OCD(
	id = "com.liferay.asset.list.internal.configuration.AssetListConfiguration",
	localization = "content/Language",
	name = "asset-list-configuration-name"
)
public interface AssetListConfiguration {

	/**
	 * Set this to <code>true</code> to consider all segments a use belongs to
	 * when retrieving the asset entries an asset publisher should display.
	 *
	 * @return default display style.
	 */
	@Meta.AD(
		deflt = "false",
		description = "consider-all-segments-for-user-description",
		name = "consider-all-segments-for-user", required = false
	)
	public boolean considerAllSegmentsForUser();

}