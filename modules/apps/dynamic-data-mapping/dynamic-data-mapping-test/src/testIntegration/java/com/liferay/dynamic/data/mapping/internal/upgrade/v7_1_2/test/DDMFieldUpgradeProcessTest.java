/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.dynamic.data.mapping.internal.upgrade.v7_1_2.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.counter.kernel.service.CounterLocalService;
import com.liferay.dynamic.data.mapping.form.field.type.constants.DDMFormFieldTypeConstants;
import com.liferay.dynamic.data.mapping.model.DDMField;
import com.liferay.dynamic.data.mapping.model.DDMFieldAttribute;
import com.liferay.dynamic.data.mapping.service.DDMFieldLocalService;
import com.liferay.dynamic.data.mapping.service.persistence.DDMFieldAttributePersistence;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.cache.MultiVMPool;
import com.liferay.portal.kernel.dao.orm.EntityCache;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.upgrade.UpgradeProcess;
import com.liferay.portal.test.rule.Inject;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;
import com.liferay.portal.upgrade.registry.UpgradeStepRegistrator;
import com.liferay.portal.upgrade.test.util.UpgradeTestUtil;

import org.junit.After;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Ankita Malik
 */
@RunWith(Arquillian.class)
public class DDMFieldUpgradeProcessTest {

	@ClassRule
	@Rule
	public static final AggregateTestRule aggregateTestRule =
		new AggregateTestRule(new LiferayIntegrationTestRule());

	@After
	public void tearDown() throws Exception {
		_ddmFieldLocalService.deleteDDMFormValues(_storageId);
	}

	@Test
	public void testUpgradeProcessDeletesDuplicateRootDDMFields()
		throws Exception {

		_storageId = _counterLocalService.increment();

		DDMField rootDDMField = _addRootDDMField();

		DDMField ddmField = _addDDMField(
			RandomTestUtil.randomString(), RandomTestUtil.randomString(8));

		DDMField duplicateRootDDMField = _addRootDDMField();

		DDMFieldAttribute duplicateRootDDMFieldAttribute =
			_addDDMFieldAttribute(
				duplicateRootDDMField.getFieldId(), "defaultLanguageId");

		_runUpgrade();

		Assert.assertNotNull(
			_ddmFieldLocalService.fetchDDMField(rootDDMField.getFieldId()));
		Assert.assertNotNull(
			_ddmFieldLocalService.fetchDDMField(ddmField.getFieldId()));
		Assert.assertNull(
			_ddmFieldLocalService.fetchDDMField(
				duplicateRootDDMField.getFieldId()));
		Assert.assertNull(
			_ddmFieldAttributePersistence.fetchByPrimaryKey(
				duplicateRootDDMFieldAttribute.getFieldAttributeId()));
	}

	@Test
	public void testUpgradeProcessKeepsSingleRootDDMField() throws Exception {
		_storageId = _counterLocalService.increment();

		DDMField rootDDMField = _addRootDDMField();

		DDMField ddmField = _addDDMField(
			RandomTestUtil.randomString(), RandomTestUtil.randomString(8));

		_runUpgrade();

		Assert.assertNotNull(
			_ddmFieldLocalService.fetchDDMField(rootDDMField.getFieldId()));
		Assert.assertNotNull(
			_ddmFieldLocalService.fetchDDMField(ddmField.getFieldId()));
	}

	private DDMField _addDDMField(String fieldName, String instanceId) {
		DDMField ddmField = _ddmFieldLocalService.createDDMField(
			_counterLocalService.increment());

		ddmField.setParentFieldId(0);
		ddmField.setStorageId(_storageId);
		ddmField.setStructureVersionId(_counterLocalService.increment());
		ddmField.setFieldName(fieldName);
		ddmField.setFieldType(DDMFormFieldTypeConstants.TEXT);
		ddmField.setInstanceId(instanceId);
		ddmField.setLocalizable(false);
		ddmField.setPriority(0);

		return _ddmFieldLocalService.addDDMField(ddmField);
	}

	private DDMFieldAttribute _addDDMFieldAttribute(
		long fieldId, String attributeName) {

		DDMFieldAttribute ddmFieldAttribute =
			_ddmFieldAttributePersistence.create(
				_counterLocalService.increment());

		ddmFieldAttribute.setFieldId(fieldId);
		ddmFieldAttribute.setStorageId(_storageId);
		ddmFieldAttribute.setAttributeName(attributeName);
		ddmFieldAttribute.setLanguageId(StringPool.BLANK);
		ddmFieldAttribute.setAttributeValue(RandomTestUtil.randomString());

		return _ddmFieldAttributePersistence.update(ddmFieldAttribute);
	}

	private DDMField _addRootDDMField() {
		return _addDDMField(StringPool.BLANK, StringPool.BLANK);
	}

	private void _runUpgrade() throws Exception {
		UpgradeProcess upgradeProcess = UpgradeTestUtil.getUpgradeStep(
			_upgradeStepRegistrator, _CLASS_NAME);

		upgradeProcess.upgrade();

		_entityCache.clearCache();

		_multiVMPool.clear();
	}

	private static final String _CLASS_NAME =
		"com.liferay.dynamic.data.mapping.internal.upgrade.v7_1_2." +
			"DDMFieldUpgradeProcess";

	@Inject
	private CounterLocalService _counterLocalService;

	@Inject
	private DDMFieldAttributePersistence _ddmFieldAttributePersistence;

	@Inject
	private DDMFieldLocalService _ddmFieldLocalService;

	@Inject
	private EntityCache _entityCache;

	@Inject
	private MultiVMPool _multiVMPool;

	private long _storageId;

	@Inject(
		filter = "(&(component.name=com.liferay.dynamic.data.mapping.internal.upgrade.registry.DDMServiceUpgradeStepRegistrator))"
	)
	private UpgradeStepRegistrator _upgradeStepRegistrator;

}