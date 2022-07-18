package com.quannm18.cloneremovecache

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ConditionVariable
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.quannm18.cloneremovecache.adapter.MyAdapter
import com.quannm18.cloneremovecache.model.PlaceholderContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class ListActivity : AppCompatActivity() {
    private val rcvMain: RecyclerView by lazy { findViewById<RecyclerView>(R.id.rcvMain) }
    private val floatingActionButton: FloatingActionButton by lazy { findViewById<FloatingActionButton>(R.id.floatingActionButton) }
    private val fabClear: FloatingActionButton by lazy { findViewById<FloatingActionButton>(R.id.fabClear) }
    private lateinit var myAdapter: MyAdapter
    private val checkedPkgList: HashSet<String> = HashSet()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        myAdapter = MyAdapter(PlaceholderContent.ITEMS)
        rcvMain.apply {
            layoutManager = LinearLayoutManager(this@ListActivity)
            adapter = myAdapter
            addItemDecoration(DividerItemDecoration(this@ListActivity,DividerItemDecoration.VERTICAL))
        }

        fabClear.setOnClickListener{
            PlaceholderContent.ITEMS.filter { it.checked }.forEach { checkedPkgList.add(it.name) }
            PlaceholderContent.ITEMS.filter { !it.checked }.forEach { checkedPkgList.remove(it.name) }

            CoroutineScope(Dispatchers.IO).launch {
                startCleanCache(PlaceholderContent.ITEMS.filter { it.checked }.map { it.name })
            }
        }
    }


    private suspend fun startCleanCache(pkgList: List<String>) {
        cleanCacheInterrupt.set(false)
        cleanCacheFinished.set(false)

        for (i in pkgList.indices) {
            startApplicationDetailsActivity(pkgList[i])
            cleanAppCacheFinished.set(false)

            delay(500L)
            waitAccessibility.block(5000L)
            delay(500L)

            // user interrupt process
            if (cleanCacheInterrupt.get()) break
        }
        cleanCacheFinished.set(true)

        runOnUiThread {
            val displayText = if (cleanCacheInterrupt.get())
                getText(R.string.text_clean_cache_interrupt)
            else getText(R.string.text_clean_cache_finish)

            // return back to Main Activity, sometimes not possible press Back from Settings
            if (pkgList.isNotEmpty() && Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                val intent = this.intent
                intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                intent.putExtra(ARG_DISPLAY_TEXT, displayText)
                startActivity(intent)
            }
        }
    }

    private fun startApplicationDetailsActivity(packageName: String) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            e.printStackTrace()
        }
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
        private val TAG = MyAdapter::class.java.simpleName
    }
}