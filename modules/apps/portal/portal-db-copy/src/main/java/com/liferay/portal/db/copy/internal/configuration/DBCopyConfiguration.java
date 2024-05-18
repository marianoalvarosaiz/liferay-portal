/**
 * SPDX-FileCopyrightText: (c) 2024 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.portal.db.copy.internal.configuration;

import aQute.bnd.annotation.metatype.Meta;

import com.liferay.portal.configuration.metatype.annotations.ExtendedObjectClassDefinition;

/**
 * @author Mariano Álvaro Sáiz
 */
@ExtendedObjectClassDefinition(generateUI = false)
@Meta.OCD(
	id = "com.liferay.portal.db.copy.internal.configuration.DBCopyConfiguration"
)
public interface DBCopyConfiguration {

	@Meta.AD
	public String driverClassName();

	@Meta.AD
	public String url();

	@Meta.AD
	public String userName();

	@Meta.AD(type = Meta.Type.Password)
	public String password();

}