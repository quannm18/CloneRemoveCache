package com.quannm18.cloneremovecache.adapter

import android.os.Build
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.quannm18.cloneremovecache.R
import com.quannm18.cloneremovecache.model.PlaceholderContent

class MyAdapter(val mList: MutableList<PlaceholderContent.PlaceholderPackage>) : RecyclerView.Adapter<MyAdapter.MyViewModel>() {

    inner class MyViewModel(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val packageIcon: ImageView by lazy { itemView.findViewById<ImageView>(R.id.packageIcon) }
        private val packageLabel: CheckBox by lazy { itemView.findViewById<CheckBox>(R.id.packageLabel) }
        private val packageName: TextView by lazy { itemView.findViewById<TextView>(R.id.packageName) }
        private val cacheSize: TextView by lazy { itemView.findViewById<TextView>(R.id.cacheSize) }

        fun bind(pkgInfo: PlaceholderContent.PlaceholderPackage) {
            packageIcon.setImageDrawable(pkgInfo.icon)
            packageLabel.setText(pkgInfo.label)
            packageLabel.isChecked = pkgInfo.checked
            packageLabel.setOnCheckedChangeListener(null)
            packageName.setText(pkgInfo.name)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && pkgInfo.stats != null) {
                cacheSize.setText(Formatter.formatShortFileSize(itemView.context, pkgInfo.stats.cacheBytes))
            }
            packageLabel.setOnCheckedChangeListener { _, checked ->
                pkgInfo.checked = checked
            }
//            itemView.setOnClickListener {
//                Log.e("packageName","${pkgInfo.name}")
//                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
//                intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
//                intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
//                intent.addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION)
//
//                intent.data = Uri.parse("package:${pkgInfo.name}")
//
//                itemView.context.startActivity(intent)
//            }
        }
    }

    override fun getItemCount(): Int {
        return mList.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewModel {
        return MyViewModel(LayoutInflater.from(parent.context).inflate(R.layout.fragment_package, parent, false))
    }

    override fun onBindViewHolder(holder: MyViewModel, position: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            holder.bind(mList[position])
        }
    }
}