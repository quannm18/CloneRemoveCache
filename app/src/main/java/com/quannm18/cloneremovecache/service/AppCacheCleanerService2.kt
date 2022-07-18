package com.quannm18.cloneremovecache.service

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityButtonController.AccessibilityButtonCallback
import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.quannm18.cloneremovecache.BuildConfig
import com.quannm18.cloneremovecache.MainActivity
import com.quannm18.cloneremovecache.util.findNestedChildByClassName
import com.quannm18.cloneremovecache.util.getAllChild
import com.quannm18.cloneremovecache.util.lowercaseCompareText
import com.quannm18.cloneremovecache.util.performClick
import java.io.File


class AppCacheCleanerService2 : AccessibilityService() {

    private var mAccessibilityButtonController: AccessibilityButtonController? = null
    private var mAccessibilityButtonCallback: AccessibilityButtonCallback? = null
    private var mIsAccessibilityButtonAvailable: Boolean = false

    private fun findClearCacheButton(nodeInfo: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        nodeInfo.getAllChild().forEach { childNode ->
            findClearCacheButton(childNode)?.let { return it }
        }

        return nodeInfo.takeIf {
            nodeInfo.viewIdResourceName?.matches("com.android.settings:id/.*button.*".toRegex()) == true
                    && arrayTextClearCacheButton.any { text -> nodeInfo.lowercaseCompareText(text) }
        }
    }

    private fun findStorageAndCacheMenu(nodeInfo: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        nodeInfo.getAllChild().forEach { childNode ->
            findStorageAndCacheMenu(childNode)?.let { return it }
        }

        return nodeInfo.takeIf {
            nodeInfo.viewIdResourceName?.contentEquals("android:id/title") == true
                    && arrayTextStorageAndCacheMenu.any { text -> nodeInfo.lowercaseCompareText(text) }
        }
    }

    private fun findBackButton(nodeInfo: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val actionBar = nodeInfo.findAccessibilityNodeInfosByViewId(
            "com.android.settings:id/action_bar").firstOrNull()
            ?: nodeInfo.findAccessibilityNodeInfosByViewId(
                "android:id/action_bar").firstOrNull()
            ?: return null

        // WORKAROUND: on some smartphones ActionBar Back button has ID "up"
        actionBar.findAccessibilityNodeInfosByViewId(
            "android:id/up").firstOrNull()?.let { return it }

        return actionBar.findNestedChildByClassName(
            arrayOf("android.widget.ImageButton", "android.widget.ImageView"))
    }

    private val mLocalReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "disableSelf" -> {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) unregisterButton()
                    disableSelf()
                }
                "addExtraSearchText" -> {
                    updateLocaleText(
                        intent.getStringExtra("clear_cache"),
                        intent.getStringExtra("storage"))
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG)
            createLogFile()
        updateLocaleText(null, null)
        val intentFilter = IntentFilter()
        intentFilter.addAction("disableSelf")
        intentFilter.addAction("addExtraSearchText")
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(mLocalReceiver, intentFilter)
    }

    private fun updateLocaleText(clearCacheText: CharSequence?, storageText: CharSequence?) {
        arrayTextClearCacheButton.clear()
        clearCacheText?.let { arrayTextClearCacheButton.add(it) }
        arrayTextClearCacheButton.add("getText(R.string.clear_cache_btn_text)")

        arrayTextStorageAndCacheMenu.clear()
        storageText?.let { arrayTextStorageAndCacheMenu.add(it) }
        arrayTextStorageAndCacheMenu.add("getText(R.string.storage_settings_for_app)")
        arrayTextStorageAndCacheMenu.add("getText(R.string.storage_label)")
    }

    override fun onDestroy() {
        if (BuildConfig.DEBUG)
            deleteLogFile()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            unregisterButton()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mLocalReceiver)
        super.onDestroy()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            registerButton()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun registerButton() {
        mAccessibilityButtonController = accessibilityButtonController

        // Accessibility Button is available on Android 30 and early
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
            if (mAccessibilityButtonController?.isAccessibilityButtonAvailable != true)
                return
        }

        mAccessibilityButtonCallback =
            object : AccessibilityButtonCallback() {
                override fun onClicked(controller: AccessibilityButtonController) {
                    if (MainActivity.cleanCacheFinished.get()) return
                    MainActivity.cleanCacheInterrupt.set(true)
                    MainActivity.waitAccessibility.open()
                }

                override fun onAvailabilityChanged(
                    controller: AccessibilityButtonController,
                    available: Boolean
                ) {
                    if (controller == mAccessibilityButtonController) {
                        mIsAccessibilityButtonAvailable = available
                    }
                }
            }

        mAccessibilityButtonCallback?.also {
            mAccessibilityButtonController?.registerAccessibilityButtonCallback(it)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun unregisterButton() {
        mAccessibilityButtonCallback?.let {
            mAccessibilityButtonController?.unregisterAccessibilityButtonCallback(it)
        }
    }

    private fun showTree(level: Int, nodeInfo: AccessibilityNodeInfo?) {
        if (nodeInfo == null) return
        Log.d(TAG,">".repeat(level) + " " + nodeInfo.className
                + ":" + nodeInfo.text+ ":" + nodeInfo.viewIdResourceName)
        nodeInfo.getAllChild().forEach { childNode ->
            showTree(level + 1, childNode)
        }
    }

    private fun goBack(nodeInfo: AccessibilityNodeInfo) {
        findBackButton(nodeInfo)?.let { backButton ->
            Log.d(TAG,"found back button")
            when (backButton.performClick()) {
                true  -> Log.d(TAG,"perform action click on back button")
                false -> Log.d(TAG,"no perform action click on back button")
                else  -> Log.d(TAG,"not found clickable view for back button")
            }
        }
        MainActivity.cleanAppCacheFinished.set(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (MainActivity.cleanCacheFinished.get()) return

        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
            return

        val nodeInfo = event.source ?: return

        if (BuildConfig.DEBUG) {
            Log.d(TAG,"===>>> TREE BEGIN <<<===")
            showTree(0, nodeInfo)
            Log.d(TAG,"===>>> TREE END <<<===")
        }

        if (MainActivity.cleanAppCacheFinished.get()) {
            goBack(nodeInfo)
            // notify main app go to another app
            MainActivity.waitAccessibility.open()
        } else {

            findClearCacheButton(nodeInfo)?.let { clearCacheButton ->
                Log.d(TAG,"found clean cache button")
                if (clearCacheButton.isEnabled) {
                    Log.d(TAG,"clean cache button is enabled")
                    when (clearCacheButton.performClick()) {
                        true  -> Log.d(TAG,"perform action click on clean cache button")
                        false -> Log.e(TAG,"no perform action click on clean cache button")
                        else  -> Log.e(TAG,"not found clickable view for clean cache button")
                    }
                }
                goBack(nodeInfo)
                return
            }

            findStorageAndCacheMenu(nodeInfo)?.let { storageAndCacheMenu ->
                Log.d(TAG,"found storage & cache button")
                if (storageAndCacheMenu.isEnabled) {
                    Log.d(TAG,"storage & cache button is enabled")
                    when (storageAndCacheMenu.performClick()) {
                        true  -> Log.d(TAG,"perform action click on storage & cache button")
                        false -> Log.e(TAG,"no perform action click on storage & cache button")
                        else  -> Log.e(TAG,"not found clickable view for storage & cache button")
                    }
                } else {
                    goBack(nodeInfo)
                    // notify main app go to another app
                    MainActivity.waitAccessibility.open()
                }
                return
            }

            goBack(nodeInfo)
            // notify main app go to another app
            MainActivity.waitAccessibility.open()
        }
    }

    override fun onInterrupt() {}

    private fun createLogFile() {
        val logFile = File(cacheDir.absolutePath + "/log.txt")
        // force clean previous log
        logFile.writeText("")
    }

    private fun deleteLogFile() {
        val logFile = File(cacheDir.absolutePath + "/log.txt")
        logFile.delete()
    }

    companion object {
        val TAG = AppCacheCleanerService2::class.java.simpleName

        private var arrayTextClearCacheButton = ArrayList<CharSequence>()
        private var arrayTextStorageAndCacheMenu = ArrayList<CharSequence>()
    }
}
