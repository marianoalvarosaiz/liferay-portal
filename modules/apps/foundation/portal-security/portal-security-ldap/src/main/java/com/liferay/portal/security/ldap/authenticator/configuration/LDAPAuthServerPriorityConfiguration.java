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
package com.liferay.portal.security.ldap.authenticator.configuration;

import com.liferay.portal.configuration.metatype.annotations.ExtendedObjectClassDefinition;
import com.liferay.portal.security.ldap.configuration.CompanyScopedConfiguration;

import aQute.bnd.annotation.metatype.Meta;

@ExtendedObjectClassDefinition(category = "foundation",
		factoryInstanceLabelAttribute = "companyId",
		scope = ExtendedObjectClassDefinition.Scope.COMPANY)
@Meta.OCD(factory = true,
		id = "com.liferay.portal.security.ldap.authenticator.configuration.LDAPAuthServerPriorityConfiguration",
		localization = "content/Language",
		name = "ldap.auth.server.priority.configuration.name")
public interface LDAPAuthServerPriorityConfiguration
		extends CompanyScopedConfiguration {
	
	@Meta.AD(deflt = "0", required = false)
	@Override
	public long companyId();
	
	@Meta.AD(deflt = "", description = "LDAP server priority order",
			required = false)
	public String authServerPriority();
}
