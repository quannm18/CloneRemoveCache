package com.quannm18.cloneremovecache.service

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityButtonController.AccessibilityButtonCallback
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.quannm18.cloneremovecache.BuildConfig
import com.quannm18.cloneremovecache.ListActivity
import com.quannm18.cloneremovecache.util.findNestedChildByClassName
import com.quannm18.cloneremovecache.util.getAllChild
import com.quannm18.cloneremovecache.util.performClick
import java.io.File


class AppCacheCleanerService : AccessibilityService() {

    private var mAccessibilityButtonController: AccessibilityButtonController? = null
    private var mAccessibilityButtonCallback: AccessibilityButtonCallback? = null
    private var mIsAccessibilityButtonAvailable: Boolean = false
    override fun onAccessibilityEvent(p0: AccessibilityEvent?) {
//        Log.e("Access", "onAccessibilityEvent")
//        val packageName = p0?.packageName.toString()
//        val packManager = this.packageManager
//        try {
//            val appInfo: ApplicationInfo = packManager.getApplicationInfo(packageName, 0)
//            val appLabel = packManager.getApplicationLabel(appInfo)
//            Log.e(TAG, "app name is $appLabel")
//        } catch (e: PackageManager.NameNotFoundException) {
//            e.printStackTrace()
//        }

        if (ListActivity.cleanCacheFinished.get()) return

        if (p0?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
            return
        val nodeInfo = p0?.source ?: return

        if (BuildConfig.DEBUG){
            Log.d("BuildConfig.DEBUG","===>>> TREE BEGIN <<<===")
            showTree(0, nodeInfo)
            Log.d("BuildConfig.DEBUG","===>>> TREE END <<<===")
        }
        if (ListActivity.cleanAppCacheFinished.get()){
            goBack(nodeInfo)
        }
    }

    private fun showTree(level: Int, nodeInfo: AccessibilityNodeInfo?){
        if (nodeInfo==null) return
        Log.d("ShowTree",">".repeat(level)+" ${nodeInfo.className} :" +
                "${nodeInfo.text} : ${nodeInfo.viewIdResourceName}")
        nodeInfo.getAllChild().forEach {
                childNode -> showTree(level+1,childNode)
        }
    }
    fun goBack(nodeInfo: AccessibilityNodeInfo) {
        findBackButton(nodeInfo)?.let { bacButton->
            Log.e("GoBack","Found back button")
            when(bacButton.performClick()){
                true  -> Log.d("True click","perform action click on back button")
                false -> Log.e("False click","no perform action click on back button")
                else  -> Log.e("True click","not found clickable view for back button")
            }
        }
        ListActivity.cleanCacheFinished.set(true)
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
    override fun onInterrupt() {
        Log.e("Access", "onInterrupt")
    }

    override fun onServiceConnected() {
        val info = AccessibilityServiceInfo()
        info.apply {
            eventTypes = AccessibilityEvent.TYPE_VIEW_CLICKED or AccessibilityEvent.TYPE_VIEW_FOCUSED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_SPOKEN
            // flags = AccessibilityServiceInfo.DEFAULT;
            notificationTimeout = 100
        }
        this.serviceInfo = info
        Log.e("Access", "onServiceConnected")
        super.onServiceConnected()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            registerButton()

    }


    override fun onDestroy() {
        if (BuildConfig.DEBUG)
            deleteLogFile()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            unregisterButton()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mLocalReceiver)
        super.onDestroy()
    }
    private val mLocalReceiver = object : BroadcastReceiver(){
        override fun onReceive(p0: Context?, p1: Intent?) {
            when(p1?.action){
                "disableSelf"->{
                    if (Build.VERSION.SDK_INT< Build.VERSION_CODES.N) return
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) unregisterButton()
                    disableSelf()
                }
                "addExtraSearchText" ->{
                    updateLocaleText(
                        p1?.getStringExtra("clear_storage"),
                        p1?.getStringExtra("storage")
                    )
                }
            }
        }

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
            object : AccessibilityButtonController.AccessibilityButtonCallback() {
                override fun onClicked(controller: AccessibilityButtonController) {
                    if (ListActivity.cleanCacheFinished.get()) return
                    Log.d("Accessibility","Accessibility button pressed!")
                    ListActivity.cleanCacheInterrupt.set(true)
                    ListActivity.waitAccessibility.open()
                }

                override fun onAvailabilityChanged(
                    controller: AccessibilityButtonController,
                    available: Boolean
                ) {
                    if (controller == mAccessibilityButtonController) {
                        Log.d("Accessibility","Accessibility button available = $available")
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
    companion object {
        private val TAG = this::class.java.simpleName

        private var arrayTextClearCacheButton = ArrayList<CharSequence>()
        private var arrayTextStorageAndCacheMenu = ArrayList<CharSequence>()
    }

    private fun createLogFile() {
        val logFile = File(cacheDir.absolutePath + "/log.txt")
        // force clean previous log
        logFile.writeText("")
    }

    private fun deleteLogFile() {
        val logFile = File(cacheDir.absolutePath + "/log.txt")
        logFile.delete()
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

}
