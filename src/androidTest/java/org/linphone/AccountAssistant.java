package org.linphone;

import android.support.test.espresso.ViewInteraction;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.LargeTest;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.Assert;
import org.junit.runner.RunWith;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneProxyConfig;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.Espresso.pressBack;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.action.ViewActions.closeSoftKeyboard;
import static android.support.test.espresso.action.ViewActions.replaceText;
import static android.support.test.espresso.action.ViewActions.scrollTo;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.isClickable;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withParent;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.linphone.Utils.waitUi;

/**
 * Created by ecroze on 04/05/17.
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class AccountAssistant {

    @Rule
    public ActivityTestRule<LinphoneLauncherActivity> mActivityTestRule = new ActivityTestRule<>(LinphoneLauncherActivity.class);

    @Before
    public void launchActivity() {
        waitUi(2000);
        if (!LinphoneActivity.isInstanciated())
            pressBack();
    }

    @Test
    public void LoginLinphone() {

        waitUi(2000);

        LinphoneActivity.instance().displayDialer();

        onView(withId(R.id.side_menu_button)).perform(click());

        onView(Matchers.allOf(
                withId(R.id.item_name),
                withText(LinphoneActivity.instance().getString(R.string.assistant)),
                childAtPosition(
                        withId(R.id.item_list),
                        0),
                isDisplayed())).perform(click());

        ViewInteraction button = onView(
                Matchers.allOf(withId(R.id.login_linphone),
                        withText(LinphoneActivity.instance().getString(R.string.assistant_login_linphone))));
        button.perform(scrollTo(), click());

        ViewInteraction checkBox = onView(
                Matchers.allOf(withId(R.id.use_username)));
        checkBox.perform(scrollTo(), click());

        ViewInteraction editText = onView(
                Matchers.allOf(withId(R.id.assistant_username),
                        withParent(withId(R.id.username_layout))));
        editText.perform(scrollTo(),
                replaceText(LinphoneActivity.instance().getString(R.string.account_linphone_login)),
                closeSoftKeyboard());

        ViewInteraction editText2 = onView(
                Matchers.allOf(withId(R.id.assistant_password),
                        withParent(withId(R.id.password_layout))));
        editText2.perform(scrollTo(),
                replaceText(LinphoneActivity.instance().getString(R.string.account_linphone_pwd)),
                closeSoftKeyboard());

        ViewInteraction button2 = onView(
                Matchers.allOf(withId(R.id.assistant_apply)));
        button2.perform(scrollTo(), click());

        waitUi(5000);

        ViewInteraction textView = onView(
                Matchers.allOf(withId(R.id.assistant_skip)));
        textView.perform(scrollTo(), click());

        //Delete account
        deleteDefaultAccount();
    }

    @Test
    public void LoginLinphoneUnknownAccount() {

        waitUi(2000);

        LinphoneActivity.instance().displayDialer();

        onView(withId(R.id.side_menu_button)).perform(click());

        onView(Matchers.allOf(
                withId(R.id.item_name),
                withText(LinphoneActivity.instance().getString(R.string.assistant)),
                childAtPosition(
                        withId(R.id.item_list),
                        0),
                isDisplayed())).perform(click());

        ViewInteraction button = onView(
                Matchers.allOf(withId(R.id.login_linphone),
                        withText(LinphoneActivity.instance().getString(R.string.assistant_login_linphone))));
        button.perform(scrollTo(), click());

        ViewInteraction checkBox = onView(
                Matchers.allOf(withId(R.id.use_username)));
        checkBox.perform(scrollTo(), click());

        ViewInteraction editText = onView(
                Matchers.allOf(withId(R.id.assistant_username),
                        withParent(withId(R.id.username_layout))));
        editText.perform(scrollTo(),
                replaceText("BadAccount"),
                closeSoftKeyboard());

        ViewInteraction editText2 = onView(
                Matchers.allOf(withId(R.id.assistant_password),
                        withParent(withId(R.id.password_layout))));
        editText2.perform(scrollTo(),
                replaceText("BadPassword"),
                closeSoftKeyboard());

        ViewInteraction button2 = onView(
                Matchers.allOf(withId(R.id.assistant_apply)));
        button2.perform(scrollTo(), click());

        waitUi(5000);

        ViewInteraction button3 = onView(
                allOf(withId(android.R.id.button3),
                        withText(LinphoneActivity.instance().getString(R.string.ok)),
                        isDisplayed()));
        button3.perform(click());
    }

    @Test
    public void LoginLinphoneBadCredentials() {

        waitUi(2000);

        LinphoneActivity.instance().displayDialer();

        onView(withId(R.id.side_menu_button)).perform(click());

        onView(Matchers.allOf(
                withId(R.id.item_name),
                withText(LinphoneActivity.instance().getString(R.string.assistant)),
                childAtPosition(
                        withId(R.id.item_list),
                        0),
                isDisplayed())).perform(click());

        ViewInteraction button = onView(
                Matchers.allOf(withId(R.id.login_linphone),
                        withText(LinphoneActivity.instance().getString(R.string.assistant_login_linphone))));
        button.perform(scrollTo(), click());

        ViewInteraction checkBox = onView(
                Matchers.allOf(withId(R.id.use_username)));
        checkBox.perform(scrollTo(), click());

        ViewInteraction editText = onView(
                Matchers.allOf(withId(R.id.assistant_username),
                        withParent(withId(R.id.username_layout))));
        editText.perform(scrollTo(),
                replaceText(LinphoneActivity.instance().getString(R.string.account_linphone_login)),
                closeSoftKeyboard());

        ViewInteraction editText2 = onView(
                Matchers.allOf(withId(R.id.assistant_password),
                        withParent(withId(R.id.password_layout))));
        editText2.perform(scrollTo(),
                replaceText("BadPassword"),
                closeSoftKeyboard());

        ViewInteraction button2 = onView(
                Matchers.allOf(withId(R.id.assistant_apply)));
        button2.perform(scrollTo(), click());

        waitUi(5000);

        ViewInteraction button3 = onView(
                allOf(withId(android.R.id.button2),
                        withText(LinphoneActivity.instance().getString(R.string.cancel)),
                        isDisplayed()));
        button3.perform(click());

        deleteDefaultAccount();
    }

    @Test
    public void LoginLinphoneWithAccountPreferenceChange() {

        waitUi(2000);

        LinphoneActivity.instance().displayDialer();

        onView(withId(R.id.side_menu_button)).perform(click());

        onView(Matchers.allOf(
                withId(R.id.item_name),
                withText(LinphoneActivity.instance().getString(R.string.assistant)),
                childAtPosition(
                        withId(R.id.item_list),
                        0),
                isDisplayed())).perform(click());

        ViewInteraction button = onView(
                Matchers.allOf(withId(R.id.login_linphone),
                        withText(LinphoneActivity.instance().getString(R.string.assistant_login_linphone))));
        button.perform(scrollTo(), click());

        ViewInteraction checkBox = onView(
                Matchers.allOf(withId(R.id.use_username)));
        checkBox.perform(scrollTo(), click());

        ViewInteraction editText = onView(
                Matchers.allOf(withId(R.id.assistant_username),
                        withParent(withId(R.id.username_layout))));
        editText.perform(scrollTo(),
                replaceText(LinphoneActivity.instance().getString(R.string.account_linphone_login)),
                closeSoftKeyboard());

        ViewInteraction editText2 = onView(
                Matchers.allOf(withId(R.id.assistant_password),
                        withParent(withId(R.id.password_layout))));
        editText2.perform(scrollTo(),
                replaceText("BadPassword"),
                closeSoftKeyboard());

        ViewInteraction button2 = onView(
                Matchers.allOf(withId(R.id.assistant_apply)));
        button2.perform(scrollTo(), click());

        waitUi(5000);

        ViewInteraction button3 = onView(
                allOf(withId(android.R.id.button1),
                        withText(LinphoneActivity.instance().getString(R.string.continue_text)),
                        isDisplayed()));
        button3.perform(click());

        onView(withId(R.id.side_menu_button)).perform(click());

        onView(Matchers.allOf(
                withId(R.id.item_name),
                withText(LinphoneActivity.instance().getString(R.string.menu_settings)),
                childAtPosition(
                        withId(R.id.item_list),
                        1),
                isDisplayed())).perform(click());

        //Go on account preference from settings
        ViewInteraction linearLayout = onView(
                allOf(childAtPosition(
                        allOf(withId(android.R.id.list),
                                withParent(withId(R.id.topLayout))),
                        1),
                        isDisplayed()));
        linearLayout.perform(click());

        pressBack();

        onView(withId(R.id.side_menu_button)).perform(click());

        //Directly go on account preference by menu
        ViewInteraction relativeLayout = onView(
                allOf(withId(R.id.default_account), isDisplayed()));
        relativeLayout.perform(click());

        ViewInteraction linearLayout2 = onView(
                allOf(childAtPosition(
                        allOf(withId(android.R.id.list),
                                withParent(withId(R.id.topLayout))),
                        3),
                        isDisplayed()));
        linearLayout2.perform(click());

        ViewInteraction editText5 = onView(
                allOf(withId(android.R.id.edit)));
        editText5.perform(scrollTo(),
                replaceText(LinphoneActivity.instance().getString(R.string.account_linphone_pwd)),
                closeSoftKeyboard());

        ViewInteraction button4 = onView(
                allOf(withId(android.R.id.button1),
                        withText(LinphoneActivity.instance().getString(R.string.ok)),
                        isDisplayed()));
        button4.perform(click());

        pressBack();

        waitUi(1000);

        //Check if register is ok
        LinphoneProxyConfig proxyCfg = LinphoneManager.getLc().getDefaultProxyConfig();

        Assert.assertEquals((proxyCfg.getState() == LinphoneCore.RegistrationState.RegistrationOk), true);

        deleteDefaultAccount();
    }

    @Test
    public void LoginGeneric() {

        waitUi(2000);

        LinphoneActivity.instance().displayDialer();

        onView(withId(R.id.side_menu_button)).perform(click());

        onView(Matchers.allOf(
                withId(R.id.item_name),
                withText(LinphoneActivity.instance().getString(R.string.assistant)),
                childAtPosition(
                        withId(R.id.item_list),
                        0),
                isDisplayed())).perform(click());

        ViewInteraction button = onView(
                Matchers.allOf(withId(R.id.login_generic),
                        withText(LinphoneActivity.instance().getString(R.string.assistant_login_generic))));
        button.perform(scrollTo(), click());

        ViewInteraction button2 = onView(
                Matchers.allOf(withId(R.id.assistant_apply)));

        //button2.check(matches(not(isClickable())));

        ViewInteraction editText = onView(
                Matchers.allOf(withId(R.id.assistant_username)));
        editText.perform(scrollTo(),
                replaceText(LinphoneActivity.instance().getString(R.string.account_linphone_login)),
                closeSoftKeyboard());

        //button2.check(matches(not(isClickable())));

        ViewInteraction editText2 = onView(
                Matchers.allOf(withId(R.id.assistant_password)));
        editText2.perform(scrollTo(),
                replaceText(LinphoneActivity.instance().getString(R.string.account_linphone_pwd)),
                closeSoftKeyboard());

        //button2.check(matches(not(isClickable())));

        ViewInteraction editText3 = onView(
                Matchers.allOf(withId(R.id.assistant_domain)));
        editText3.perform(scrollTo(),
                replaceText(LinphoneActivity.instance().getString(R.string.account_linphone_domain)),
                closeSoftKeyboard());

        button2.check(matches(isClickable()));

        ViewInteraction radioButton = onView(
                allOf(withId(R.id.transport_tcp),
                        withText(LinphoneActivity.instance().getString(R.string.pref_transport_tcp)),
                        withParent(withId(R.id.assistant_transports))));
        radioButton.perform(scrollTo(), click());

        button2.check(matches(isClickable()));

        ViewInteraction radioButton2 = onView(
                allOf(withId(R.id.transport_udp),
                        withText(LinphoneActivity.instance().getString(R.string.pref_transport_udp)),
                        withParent(withId(R.id.assistant_transports))));
        radioButton2.perform(scrollTo(), click());

        button2.check(matches(isClickable()));

        ViewInteraction radioButton3 = onView(
                allOf(withId(R.id.transport_tls),
                        withText(LinphoneActivity.instance().getString(R.string.pref_transport_tls)),
                        withParent(withId(R.id.assistant_transports))));
        radioButton3.perform(scrollTo(), click());

        button2.check(matches(isClickable()));

        button2.perform(scrollTo(), click());

        waitUi(5000);

        ViewInteraction textView = onView(
                Matchers.allOf(withId(R.id.assistant_skip)));
        textView.perform(scrollTo(), click());

        //Delete account
        deleteDefaultAccount();
    }

    private static Matcher<View> childAtPosition(
            final Matcher<View> parentMatcher, final int position) {

        return new TypeSafeMatcher<View>() {
            @Override
            public void describeTo(Description description) {
                description.appendText("Child at position " + position + " in parent ");
                parentMatcher.describeTo(description);
            }

            @Override
            public boolean matchesSafely(View view) {
                ViewParent parent = view.getParent();
                return parent instanceof ViewGroup && parentMatcher.matches(parent)
                        && view.equals(((ViewGroup) parent).getChildAt(position));
            }
        };
    }

    private void deleteDefaultAccount() {
        LinphoneProxyConfig proxyCfg = LinphoneManager.getLc().getDefaultProxyConfig();

        if (proxyCfg != null)
            LinphoneManager.getLc().removeProxyConfig(proxyCfg);
        if (LinphoneManager.getLc().getProxyConfigList().length == 0)
            LinphoneManager.getLc().setDefaultProxyConfig(null);
    }
}
