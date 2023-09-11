/*
 * Copyright (c) 2010-2023 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.ui.welcome

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.databinding.WelcomeActivityBinding
import org.linphone.ui.assistant.AssistantActivity
import org.linphone.ui.welcome.fragment.WelcomePage1Fragment
import org.linphone.ui.welcome.fragment.WelcomePage2Fragment
import org.linphone.ui.welcome.fragment.WelcomePage3Fragment
import org.linphone.utils.AppUtils

class WelcomeActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "[Welcome Activity]"
        private const val PAGES = 3
    }

    private lateinit var binding: WelcomeActivityBinding

    private lateinit var viewPager: ViewPager2

    private val pageChangedCallback = PageChangedCallback()

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        super.onCreate(savedInstanceState)

        while (!coreContext.isReady()) {
            Thread.sleep(20)
        }

        binding = DataBindingUtil.setContentView(this, R.layout.welcome_activity)
        binding.lifecycleOwner = this

        viewPager = binding.pager
        val pagerAdapter = ScreenSlidePagerAdapter(this)
        viewPager.adapter = pagerAdapter

        binding.dotsIndicator.attachTo(viewPager)

        binding.setSkipClickListener {
            Log.i("$TAG User clicked on 'skip' button, going to last page")
            viewPager.currentItem = PAGES - 1
        }

        binding.setNextClickListener {
            if (viewPager.currentItem == PAGES - 1) {
                Log.i(
                    "$TAG User clicked on 'start' button, leaving activity and going into Assistant"
                )
                finish()
                val intent = Intent(this, AssistantActivity::class.java)
                startActivity(intent)
            } else {
                viewPager.currentItem = viewPager.currentItem + 1
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewPager.registerOnPageChangeCallback(pageChangedCallback)
    }

    override fun onPause() {
        viewPager.unregisterOnPageChangeCallback(pageChangedCallback)
        super.onPause()
    }

    private inner class ScreenSlidePagerAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
        override fun getItemCount(): Int = PAGES

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> WelcomePage1Fragment()
                1 -> WelcomePage2Fragment()
                else -> WelcomePage3Fragment()
            }
        }
    }

    private inner class PageChangedCallback : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            Log.i("$TAG Current page is [$position]")
            if (position == PAGES - 1) {
                binding.next.text = AppUtils.getString(R.string.start)
                binding.skip.visibility = View.INVISIBLE
            } else {
                binding.next.text = AppUtils.getString(R.string.next)
                binding.skip.visibility = View.VISIBLE
            }
        }
    }
}
