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

package com.liferay.source.formatter;

import com.liferay.source.formatter.checks.BaseSourceCheck;

import org.junit.Test;

import org.mockito.Matchers;

import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;

/**
 * @author Alberto Chaparro
 */
public class JavaUpgradeVersionSourceProcessorTest
	extends BaseSourceProcessorTestCase {

	@Test
	public void testMajorUpgradeByAlterColumnName() throws Exception {
		test("MajorUpgradeByAlterColumnName.testjava", "2.0.0");
	}

	@Test
	public void testMajorUpgradeByAlterColumnType() throws Exception {
		//MockitoAnnotations.initMocks(this);

		
		BaseSourceCheck baseSourceCheck = PowerMockito.mock(BaseSourceCheck.class,
			Mockito.CALLS_REAL_METHODS);

		PowerMockito.doReturn(
			"create table MajorUpgradeByAlterColumnType (\n" +
			"\ttest VARCHAR(75) null,\n" +
			");"
		).when(
			baseSourceCheck, "_getPortalGitURL", Matchers.endsWith("tables.sql")
		);

		test("MajorUpgradeByAlterColumnType.testjava", "2.0.0");
	}

	@Test
	public void testMajorUpgradeByAlterColumnTypeToShorterVarchar()
		throws Exception {

		test("MajorUpgradeByAlterColumnTypeToShorterVarchar.testjava", "2.0.0");
	}

	@Test
	public void testMajorUpgradeByAlterTableDropColumn() throws Exception {
		test("MajorUpgradeByAlterTableDropColumn.testjava", "2.0.0");
	}

	@Test
	public void testMajorUpgradeByAlterTableDropColumnClause()
		throws Exception {

		test("MajorUpgradeByAlterTableDropColumnClause.testjava", "2.0.0");
	}

	@Test
	public void testMajorUpgradeByDropTable() throws Exception {
		test("MajorUpgradeByDropTable.testjava", "2.0.0");
	}

	/*@Test
	public void testMicroUpgradeByAlterColumnTypeFromVarcharToText()
		throws Exception {

		test("MajorUpgradeByAlterColumnType.testjava", "1.0.1");
	}

	@Test
	public void testMicroUpgradeByAlterColumnTypeToLongerVarchar()
		throws Exception {

		test("MajorUpgradeByAlterColumnType.testjava", "1.0.1");
	}

*/

	@Override
	protected void test(String fileName, String expectedSchemaVersion)
		throws Exception {

		String className = fileName.substring(0, fileName.indexOf("."));

		int lineNumber = 31;

		if (className.length() < 32) {
			lineNumber = 30;
		}

		super.test(
			fileName, "Expected new schema version: " + expectedSchemaVersion,
			lineNumber);
	}

	@Test
	public void testMinorUpgradeByAlterTableAddColumn() throws Exception {
		test("MinorUpgradeByAlterTableAddColumn.testjava", "1.1.0");
	}

	@Test
	public void testMinorUpgradeByAlterTableAddColumnClause() throws Exception {
		test("MinorUpgradeByAlterTableAddColumnClause.testjava", "1.1.0");
	}

}