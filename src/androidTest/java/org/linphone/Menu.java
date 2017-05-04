package org.linphone;

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
import org.junit.runner.RunWith;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.Espresso.pressBack;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withParent;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static org.linphone.Utils.waitUi;

/**
 * Created by ecroze on 04/05/17.
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class Menu {

    @Rule
    public ActivityTestRule<LinphoneLauncherActivity> mActivityTestRule = new ActivityTestRule<>(LinphoneLauncherActivity.class);

    @Before
    public void launchActivity() {
        waitUi(2000);
        if (!LinphoneActivity.isInstanciated())
            pressBack();
    }

    @Test
    public void MenuAudio() {
        waitUi(2000);

        LinphoneActivity.instance().displayDialer();

        onView(withId(R.id.side_menu_button)).perform(click());

        onView(Matchers.allOf(
                withId(R.id.item_name),
                withText(LinphoneActivity.instance().getString(R.string.menu_settings)),
                childAtPosition(
                        withId(R.id.item_list),
                        1),
                isDisplayed())).perform(click());

        onView(Matchers.allOf(
                childAtPosition(
                        Matchers.allOf(
                                withId(android.R.id.list),
                                withParent(withId(R.id.topLayout))),
                        4),
                isDisplayed())).perform(click());

        pressBack();
    }

    @Test
    public void MenuVideo() {
        waitUi(2000);

        LinphoneActivity.instance().displayDialer();

        onView(withId(R.id.side_menu_button)).perform(click());

        onView(Matchers.allOf(
                withId(R.id.item_name),
                withText(LinphoneActivity.instance().getString(R.string.menu_settings)),
                childAtPosition(
                        withId(R.id.item_list),
                        1),
                isDisplayed())).perform(click());

        onView(Matchers.allOf(
                childAtPosition(
                        Matchers.allOf(
                                withId(android.R.id.list),
                                withParent(withId(R.id.topLayout))),
                        5),
                isDisplayed())).perform(click());

        pressBack();
    }

    @Test
    public void MenuCall() {
        waitUi(2000);

        LinphoneActivity.instance().displayDialer();

        onView(withId(R.id.side_menu_button)).perform(click());

        onView(Matchers.allOf(
                withId(R.id.item_name),
                withText(LinphoneActivity.instance().getString(R.string.menu_settings)),
                childAtPosition(
                        withId(R.id.item_list),
                        1),
                isDisplayed())).perform(click());

        onView(Matchers.allOf(
                childAtPosition(
                        Matchers.allOf(
                                withId(android.R.id.list),
                                withParent(withId(R.id.topLayout))),
                        6),
                isDisplayed())).perform(click());

        pressBack();
    }

    @Test
    public void MenuChat() {
        waitUi(2000);

        LinphoneActivity.instance().displayDialer();

        onView(withId(R.id.side_menu_button)).perform(click());

        onView(Matchers.allOf(
                withId(R.id.item_name),
                withText(LinphoneActivity.instance().getString(R.string.menu_settings)),
                childAtPosition(
                        withId(R.id.item_list),
                        1),
                isDisplayed())).perform(click());

        onView(Matchers.allOf(
                childAtPosition(
                        Matchers.allOf(
                                withId(android.R.id.list),
                                withParent(withId(R.id.topLayout))),
                        7),
                isDisplayed())).perform(click());

        pressBack();
    }

    @Test
    public void MenuNetwork() {
        waitUi(2000);

        LinphoneActivity.instance().displayDialer();

        onView(withId(R.id.side_menu_button)).perform(click());

        onView(Matchers.allOf(
                withId(R.id.item_name),
                withText(LinphoneActivity.instance().getString(R.string.menu_settings)),
                childAtPosition(
                        withId(R.id.item_list),
                        1),
                isDisplayed())).perform(click());

        onView(Matchers.allOf(
                childAtPosition(
                        Matchers.allOf(
                                withId(android.R.id.list),
                                withParent(withId(R.id.topLayout))),
                        8),
                isDisplayed())).perform(click());

        pressBack();
    }

    @Test
    public void MenuAdvanced() {
        waitUi(2000);

        LinphoneActivity.instance().displayDialer();

        onView(withId(R.id.side_menu_button)).perform(click());

        onView(Matchers.allOf(
                withId(R.id.item_name),
                withText(LinphoneActivity.instance().getString(R.string.menu_settings)),
                childAtPosition(
                        withId(R.id.item_list),
                        1),
                isDisplayed())).perform(click());

        onView(Matchers.allOf(
                childAtPosition(
                        Matchers.allOf(
                                withId(android.R.id.list),
                                withParent(withId(R.id.topLayout))),
                        9),
                isDisplayed())).perform(click());

        pressBack();
    }

    @Test
    public void MenuAbout() {
        waitUi(2000);

        LinphoneActivity.instance().displayDialer();

        onView(withId(R.id.side_menu_button)).perform(click());

        onView(Matchers.allOf(
                withId(R.id.item_name),
                withText(LinphoneActivity.instance().getString(R.string.about)),
                childAtPosition(
                        withId(R.id.item_list),
                        2),
                isDisplayed())).perform(click());

        pressBack();
    }

    @Test
    public void MenuAssitant() {
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

        pressBack();
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
}
