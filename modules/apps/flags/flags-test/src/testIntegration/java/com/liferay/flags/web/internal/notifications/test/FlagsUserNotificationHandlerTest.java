/**
 * SPDX-FileCopyrightText: (c) 2024 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.flags.web.internal.notifications.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.captcha.configuration.CaptchaConfiguration;
import com.liferay.flags.service.FlagsEntryService;
import com.liferay.message.boards.constants.MBCategoryConstants;
import com.liferay.message.boards.constants.MBMessageConstants;
import com.liferay.message.boards.model.MBCategory;
import com.liferay.message.boards.model.MBMessage;
import com.liferay.message.boards.model.MBThread;
import com.liferay.message.boards.service.MBCategoryLocalServiceUtil;
import com.liferay.message.boards.service.MBMessageLocalServiceUtil;
import com.liferay.portal.configuration.test.util.CompanyConfigurationTemporarySwapper;
import com.liferay.portal.configuration.test.util.ConfigurationTemporarySwapper;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.json.JSONUtil;
import com.liferay.portal.kernel.model.Layout;
import com.liferay.portal.kernel.model.LayoutConstants;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.model.UserNotificationEvent;
import com.liferay.portal.kernel.notifications.UserNotificationDefinition;
import com.liferay.portal.kernel.notifications.UserNotificationFeedEntry;
import com.liferay.portal.kernel.notifications.UserNotificationHandler;
import com.liferay.portal.kernel.portlet.bridges.mvc.MVCActionCommand;
import com.liferay.portal.kernel.security.permission.ResourceActionsUtil;
import com.liferay.portal.kernel.service.CompanyLocalService;
import com.liferay.portal.kernel.service.LayoutLocalServiceUtil;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.test.ReflectionTestUtil;
import com.liferay.portal.kernel.test.portlet.MockLiferayPortletActionRequest;
import com.liferay.portal.kernel.test.portlet.MockLiferayPortletActionResponse;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.test.util.ServiceContextTestUtil;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.test.util.UserTestUtil;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.HashMapDictionaryBuilder;
import com.liferay.portal.kernel.util.HtmlEscapableObject;
import com.liferay.portal.kernel.util.HtmlUtil;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.WebKeys;
import com.liferay.portal.model.impl.UserNotificationEventImpl;
import com.liferay.portal.test.rule.Inject;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;

import jakarta.portlet.ActionRequest;
import jakarta.portlet.ActionResponse;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.mock.web.MockHttpServletRequest;

/**
 * @author Alicia García
 */
@RunWith(Arquillian.class)
public class FlagsUserNotificationHandlerTest {

	@ClassRule
	@Rule
	public static final AggregateTestRule aggregateTestRule =
		new LiferayIntegrationTestRule();

	@Test
	public void testBodyShouldBeEscaped() throws Exception {
		UserNotificationEvent userNotificationEvent =
			new UserNotificationEventImpl();

		String userName = "'\"></option><img src=x onerror=alert(userName)>";
		long groupId = TestPropsValues.getGroupId();
		String content = "'\"></option><img src=x onerror=alert(content)>";

		ServiceContext serviceContext =
			ServiceContextTestUtil.getServiceContext(groupId);

		MBMessage mbMessage = MBMessageLocalServiceUtil.addMessage(
			null, TestPropsValues.getUserId(), userName, groupId,
			MBCategoryConstants.DEFAULT_PARENT_CATEGORY_ID, 0L,
			MBMessageConstants.DEFAULT_PARENT_MESSAGE_ID,
			StringUtil.randomString(), content,
			MBMessageConstants.DEFAULT_FORMAT, null, false, 0.0, false,
			serviceContext);

		MBThread mbThread = mbMessage.getThread();

		String siteName = "'\"></option><img src=x onerror=alert(siteName)>";

		userNotificationEvent.setPayload(
			JSONUtil.put(
				"className", MBThread.class.getName()
			).put(
				"classPK", mbThread.getThreadId()
			).put(
				"context",
				_getContext(
					content,
					ResourceActionsUtil.getModelResource(
						serviceContext.getLocale(), MBThread.class.getName()),
					siteName, userName)
			).put(
				"notificationType",
				UserNotificationDefinition.NOTIFICATION_TYPE_ADD_ENTRY
			).put(
				"portletId", "com_liferay_flags_web_portlet_FlagsPortlet"
			).toString());

		UserNotificationFeedEntry userNotificationFeedEntry =
			_userNotificationHandler.interpret(
				userNotificationEvent, serviceContext);

		String body = userNotificationFeedEntry.getBody();

		Assert.assertTrue(
			String.format("%s should be escaped", userName),
			body.contains(HtmlUtil.escape(userName)));
		Assert.assertTrue(
			String.format("%s should be escaped", content),
			body.contains(HtmlUtil.escape(content)));
		Assert.assertTrue(
			String.format("%s should be escaped", siteName),
			body.contains(HtmlUtil.escape(siteName)));
	}

	@Test
	public void testGetBody() throws Exception {
		UserNotificationEvent userNotificationEvent =
			new UserNotificationEventImpl();

		long groupId = TestPropsValues.getGroupId();
		String content = "#63;";

		ServiceContext serviceContext =
			ServiceContextTestUtil.getServiceContext(groupId);

		MBMessage mbMessage = MBMessageLocalServiceUtil.addMessage(
			null, TestPropsValues.getUserId(), StringUtil.randomString(),
			groupId, MBCategoryConstants.DEFAULT_PARENT_CATEGORY_ID, 0L,
			MBMessageConstants.DEFAULT_PARENT_MESSAGE_ID,
			StringUtil.randomString(), content,
			MBMessageConstants.DEFAULT_FORMAT, null, false, 0.0, false,
			serviceContext);

		MBThread mbThread = mbMessage.getThread();

		userNotificationEvent.setPayload(
			JSONUtil.put(
				"className", MBThread.class.getName()
			).put(
				"classPK", mbThread.getThreadId()
			).put(
				"context",
				_getContext(
					content,
					ResourceActionsUtil.getModelResource(
						serviceContext.getLocale(), MBThread.class.getName()),
					StringUtil.randomString(), StringUtil.randomString())
			).put(
				"notificationType",
				UserNotificationDefinition.NOTIFICATION_TYPE_ADD_ENTRY
			).put(
				"portletId", "com_liferay_flags_web_portlet_FlagsPortlet"
			).toString());

		UserNotificationFeedEntry userNotificationFeedEntry =
			_userNotificationHandler.interpret(
				userNotificationEvent, serviceContext);

		String body = userNotificationFeedEntry.getBody();

		Assert.assertTrue(
			String.format("%s should contain %s", body, content),
			body.contains(content));
	}

	@Test
	public void testIgnoreTamperedReporterAndReportedUser() throws Exception {
		long groupId = TestPropsValues.getGroupId();

		ServiceContext serviceContext =
			ServiceContextTestUtil.getServiceContext(groupId);

		User reporterUser = TestPropsValues.getUser();

		User reportedUser = UserTestUtil.addUser();

		MBCategory category = MBCategoryLocalServiceUtil.addCategory(
			null, reportedUser.getUserId(),
			MBCategoryConstants.DEFAULT_PARENT_CATEGORY_ID,
			RandomTestUtil.randomString(), RandomTestUtil.randomString(),
			serviceContext);

		MBMessage mbMessage = MBMessageLocalServiceUtil.addMessage(
			reportedUser.getUserId(), reportedUser.getFullName(), groupId,
			category.getCategoryId(), RandomTestUtil.randomString(),
			RandomTestUtil.randomString(), MBMessageConstants.DEFAULT_FORMAT,
			Collections.emptyList(), false, 0, false, serviceContext);

		MockLiferayPortletActionRequest mockLiferayPortletActionRequest =
			new MockLiferayPortletActionRequest();
		MockLiferayPortletActionResponse mockLiferayPortletActionResponse =
			new MockLiferayPortletActionResponse();

		ThemeDisplay themeDisplay = new ThemeDisplay();

		themeDisplay.setCompany(
			_companyLocalService.fetchCompany(TestPropsValues.getCompanyId()));

		MockHttpServletRequest mockHttpServletRequest =
			new MockHttpServletRequest();

		themeDisplay.setScopeGroupId(groupId);
		themeDisplay.setUser(reporterUser);

		mockLiferayPortletActionRequest.setAttribute(
			WebKeys.THEME_DISPLAY, themeDisplay);

		mockLiferayPortletActionRequest.setParameter(
			"className", MBMessage.class.getName());
		mockLiferayPortletActionRequest.setParameter(
			"classPK", String.valueOf(mbMessage.getMessageId()));
		mockLiferayPortletActionRequest.setParameter(
			"reason", "Inappropriate content");
		mockLiferayPortletActionRequest.setParameter(
			"contentTitle", mbMessage.getSubject());
		mockLiferayPortletActionRequest.setParameter(
			"contentURL", "http://localhost/test");
		mockLiferayPortletActionRequest.setParameter(
			"redirect", "http://localhost");

		mockLiferayPortletActionRequest.setParameter(
			"reporterEmailAddress", "attacker@evil.com");
		mockLiferayPortletActionRequest.setParameter(
			"reportedUserId", String.valueOf(RandomTestUtil.nextLong()));

		mockHttpServletRequest.setAttribute(
			WebKeys.THEME_DISPLAY, themeDisplay);

		List<Layout> layouts = LayoutLocalServiceUtil.getLayouts(
			groupId, false, LayoutConstants.TYPE_PORTLET);

		mockHttpServletRequest.setAttribute(WebKeys.LAYOUT, layouts.get(0));

		AtomicReference<String> reporterEmailAddressRef =
			new AtomicReference<>();
		AtomicLong reportedUserIdRef = new AtomicLong();

		FlagsEntryService flagsEntryService = new FlagsEntryService() {

			@Override
			public void addEntry(
					String className, long classPK, String reporterEmailAddress,
					long reportedUserId, String contentTitle, String contentURL,
					String reason, ServiceContext serviceContext)
				throws PortalException {

				reporterEmailAddressRef.set(reporterEmailAddress);
				reportedUserIdRef.set(reportedUserId);
			}

			@Override
			public String getOSGiServiceIdentifier() {
				return "";
			}

		};

		try (AutoCloseable autoCloseable =
				ReflectionTestUtil.setFieldValueWithAutoCloseable(
					_mvcActionCommand, "_flagsEntryService", flagsEntryService);
			CompanyConfigurationTemporarySwapper
				companyConfigurationTemporarySwapper =
					new CompanyConfigurationTemporarySwapper(
						TestPropsValues.getCompanyId(),
						CaptchaConfiguration.class.getName(),
						HashMapDictionaryBuilder.<String, Object>put(
							"maxChallenges", "-1"
						).build());
			ConfigurationTemporarySwapper configurationTemporarySwapper =
				new ConfigurationTemporarySwapper(
					CaptchaConfiguration.class.getName(),
					HashMapDictionaryBuilder.<String, Object>put(
						"maxChallenges", "-1"
					).build())) {

			ReflectionTestUtil.invoke(
				_mvcActionCommand, "doProcessAction",
				new Class<?>[] {ActionRequest.class, ActionResponse.class},
				mockLiferayPortletActionRequest,
				mockLiferayPortletActionResponse);
		}

		Assert.assertEquals(
			reporterUser.getEmailAddress(), reporterEmailAddressRef.get());

		Assert.assertEquals(reportedUser.getUserId(), reportedUserIdRef.longValue());
	}

	private Map<String, HtmlEscapableObject<String>> _getContext(
		String content, String contentType, String siteName, String userName) {

		return HashMapBuilder.<String, HtmlEscapableObject<String>>put(
			"[$CONTENT_TITLE$]", new HtmlEscapableObject<>(content)
		).put(
			"[$CONTENT_TYPE$]", new HtmlEscapableObject<>(contentType)
		).put(
			"[$CONTENT_URL$]",
			new HtmlEscapableObject<>(StringUtil.randomString())
		).put(
			"[$REASON|uri$]",
			new HtmlEscapableObject<>(StringUtil.randomString())
		).put(
			"[$REPORTER_USER_NAME$]", new HtmlEscapableObject<>(userName)
		).put(
			"[$SITE_NAME$]", new HtmlEscapableObject<>(siteName)
		).build();
	}

	@Inject
	private CompanyLocalService _companyLocalService;

	@Inject(
		filter = "component.name=com.liferay.flags.web.internal.portlet.action.EditEntryMVCActionCommand"
	)
	private MVCActionCommand _mvcActionCommand;

	@Inject(
		filter = "jakarta.portlet.name=com_liferay_flags_web_portlet_FlagsPortlet"
	)
	private UserNotificationHandler _userNotificationHandler;

}