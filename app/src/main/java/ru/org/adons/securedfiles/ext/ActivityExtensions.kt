package ru.org.adons.securedfiles.ext

import android.app.Activity
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentActivity

/**
 * Extensions for [Activity]l
 */
const val MAIN_FRAGMENT_TAG = "MAIN_FRAGMENT_TAG"

fun FragmentActivity.getFragment(tag: String): Fragment? = supportFragmentManager.findFragmentByTag(tag)

fun FragmentActivity.addFragment(containerId: Int, fragment: Fragment, tag: String) = supportFragmentManager
        .beginTransaction().add(containerId, fragment, tag).commit()