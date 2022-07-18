package com.quannm18.cloneremovecache

import android.app.usage.StorageStats
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.ConditionVariable
import android.os.storage.StorageManager
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.quannm18.cloneremovecache.model.PlaceholderContent
import com.quannm18.cloneremovecache.model.PlaceholderContent.PlaceholderPackage
import com.quannm18.cloneremovecache.util.PermissionChecker.Companion.checkUsageStatsPermission
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity() {
    private val btnAccess: Button by lazy { findViewById<Button>(R.id.btnAccess) }
    private val btnUsage: Button by lazy { findViewById<Button>(R.id.btnUsage) }
    private val button3: Button by lazy { findViewById<Button>(R.id.button3) }
    private var mListDefault: MutableList<PlaceholderPackage> = ArrayList()
    private var mListInfo: MutableList<PackageInfo> = ArrayList()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnAccess.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            startActivity(intent)
        }

        btnUsage.setOnClickListener {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            startActivity(intent)

        }

        button3.setOnClickListener {
            if (PlaceholderContent.ITEMS.isEmpty()){
                Toast.makeText(applicationContext, "Empty!", Toast.LENGTH_SHORT).show()

            }else{
                Toast.makeText(applicationContext,"${PlaceholderContent.ITEMS.size}",Toast.LENGTH_SHORT).show()
                startActivity(Intent(this,ListActivity::class.java))
            }
        }

    }

    override fun onResume() {
        if (checkUsageStatsPermission(applicationContext)){
            Toast.makeText(applicationContext, "Granted", Toast.LENGTH_SHORT).show()
            mListDefault.clear()

            mListInfo.addAll(getListInstalledApps(systemOnly = false, userOnly = true))

            addPackageToPlaceholderContent()
        }else{
            Toast.makeText(applicationContext, "Can't", Toast.LENGTH_SHORT).show()

        }
        super.onResume()
    }

    private fun getListInstalledApps(
        systemOnly: Boolean,
        userOnly: Boolean
    ): ArrayList<PackageInfo> {
        val list = packageManager.getInstalledPackages(0)
        val pkgInfoList = ArrayList<PackageInfo>()
        for (i in list.indices) {
            val packageInfo = list[i]
            val flags = packageInfo!!.applicationInfo.flags
            val isSystemApp = (flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdatedSystemApp = (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            val addPkg = (systemOnly && (isSystemApp and !isUpdatedSystemApp)) or
                    (userOnly && (!isSystemApp or isUpdatedSystemApp))
            if (addPkg)
                pkgInfoList.add(packageInfo)
        }
        return pkgInfoList
    }

    private fun getStorageStats(packageName: String): StorageStats? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null

        try {
            val storageStatsManager =
                getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
            return storageStatsManager.queryStatsForPackage(
                StorageManager.UUID_DEFAULT, packageName,
                android.os.Process.myUserHandle()
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null
    }

    private fun addPackageToPlaceholderContent() {
        PlaceholderContent.ITEMS.clear()
        mListInfo.forEach { pkgInfo ->

            var localizedLabel: String? = null
            var icon: Drawable? = null
            packageManager?.let { pm ->
                try {
                    icon = pm.getApplicationIcon(pkgInfo.packageName)
                    val res = pm.getResourcesForApplication(pkgInfo.applicationInfo)
                    val resId = pkgInfo.applicationInfo.labelRes
                    if (resId != 0)
                        localizedLabel = res.getString(resId)
                } catch (e: PackageManager.NameNotFoundException) {}
            }
            val label = localizedLabel
                ?: pkgInfo.applicationInfo.nonLocalizedLabel?.toString()
                ?: pkgInfo.packageName

            val stats = getStorageStats(pkgInfo.packageName)

            PlaceholderContent.addItem(pkgInfo, label, icon,
                false, stats)
        }

        PlaceholderContent.sort()
    }
    companion object {

        const val ARG_DISPLAY_TEXT = "display-text"

        const val FRAGMENT_PACKAGE_LIST_TAG = "package-list"

        const val SETTINGS_CHECKED_PACKAGE_LIST_TAG = "package-list"
        const val SETTINGS_CHECKED_PACKAGE_TAG = "checked"

        val loadingPkgList = AtomicBoolean(false)
        val cleanAppCacheFinished = AtomicBoolean(false)
        val cleanCacheFinished = AtomicBoolean(true)
        val cleanCacheInterrupt = AtomicBoolean(false)
        val waitAccessibility = ConditionVariable()
        private val TAG = this::class.java.simpleName
    }
}
