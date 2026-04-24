package com.eddam.heysary

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.eddam.heysary.databinding.ActivityWhitelistBinding
import com.eddam.heysary.databinding.ItemAppWhitelistBinding
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppItem(
    val name: String,
    val packageName: String,
    val icon: Drawable,
    var isEnabled: Boolean
)

class WhitelistActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWhitelistBinding
    private lateinit var prefs: SaryPreferences
    private val allApps = mutableListOf<AppItem>()
    private val displayList = mutableListOf<Any>() // Puede ser String (Header) o AppItem
    private lateinit var adapter: AppAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWhitelistBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = SaryPreferences.getInstance(this)
        setupUI()
        loadApps()
    }

    private fun setupUI() {
        binding.appsRecycler.layoutManager = LinearLayoutManager(this)
        adapter = AppAdapter(displayList) { app ->
            prefs.togglePackage(app.packageName)
            // Actualizar el estado local y refrescar la vista categorizada
            app.isEnabled = !app.isEnabled
            updateDisplayList()
        }
        binding.appsRecycler.adapter = adapter

        binding.bottomNav.selectedItemId = R.id.nav_whitelist
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    startActivity(Intent(this, DashboardActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    finish()
                    true
                }
                else -> true
            }
        }
    }

    private fun loadApps() {
        binding.loadingBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) {
                val pm = packageManager
                val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                val whitelist = prefs.whitelistPackages

                packages.filter { appInfo ->
                    val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    val hasLauncher = pm.getLaunchIntentForPackage(appInfo.packageName) != null
                    !isSystem || hasLauncher || 
                    appInfo.packageName == "com.whatsapp" || 
                    appInfo.packageName == "org.telegram.messenger"
                }.map { appInfo ->
                    AppItem(
                        name = appInfo.loadLabel(pm).toString(),
                        packageName = appInfo.packageName,
                        icon = appInfo.loadIcon(pm),
                        isEnabled = whitelist.contains(appInfo.packageName)
                    )
                }.sortedBy { it.name.lowercase() }
            }

            allApps.clear()
            allApps.addAll(apps)
            updateDisplayList()
            binding.loadingBar.visibility = View.GONE
        }
    }

    private fun updateDisplayList() {
        displayList.clear()
        
        val allowed = allApps.filter { it.isEnabled }.sortedBy { it.name.lowercase() }
        val notAllowed = allApps.filter { !it.isEnabled }.sortedBy { it.name.lowercase() }

        if (allowed.isNotEmpty()) {
            displayList.add("PERMITIDAS (${allowed.size})")
            displayList.addAll(allowed)
        }

        if (notAllowed.isNotEmpty()) {
            displayList.add("NO PERMITIDAS (${notAllowed.size})")
            displayList.addAll(notAllowed)
        }

        adapter.notifyDataSetChanged()
    }

    class AppAdapter(
        private val items: List<Any>, 
        private val onToggle: (AppItem) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        companion object {
            private const val TYPE_HEADER = 0
            private const val TYPE_APP = 1
        }

        override fun getItemViewType(position: Int): Int {
            return if (items[position] is String) TYPE_HEADER else TYPE_APP
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == TYPE_HEADER) {
                val view = inflater.inflate(android.R.layout.simple_list_item_1, parent, false)
                HeaderViewHolder(view)
            } else {
                val binding = ItemAppWhitelistBinding.inflate(inflater, parent, false)
                AppViewHolder(binding)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder is HeaderViewHolder) {
                holder.bind(items[position] as String)
            } else if (holder is AppViewHolder) {
                holder.bind(items[position] as AppItem, onToggle)
            }
        }

        override fun getItemCount(): Int = items.size

        class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            fun bind(title: String) {
                (itemView as TextView).apply {
                    text = title
                    setTextColor(android.graphics.Color.parseColor("#FF0033")) // Cyberpunk Red
                    textSize = 14f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setPadding(48, 48, 48, 16)
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                }
            }
        }

        class AppViewHolder(val binding: ItemAppWhitelistBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(app: AppItem, onToggle: (AppItem) -> Unit) {
                binding.appName.text = app.name
                binding.packageName.text = app.packageName
                binding.appIcon.setImageDrawable(app.icon)
                
                // Remover el listener antes de setear el estado para evitar bucles
                binding.appSwitch.setOnCheckedChangeListener(null)
                binding.appSwitch.isChecked = app.isEnabled
                
                binding.appSwitch.setOnCheckedChangeListener { _, _ ->
                    onToggle(app)
                }
            }
        }
    }
}
