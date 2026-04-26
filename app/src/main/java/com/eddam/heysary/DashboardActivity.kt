package com.eddam.heysary

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.eddam.heysary.databinding.ActivityDashboardBinding
import java.text.SimpleDateFormat
import java.util.*
import android.text.Editable
import android.text.TextWatcher
import android.view.View

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private val handler = Handler(Looper.getMainLooper())
    
    private val clockRunnable = object : Runnable {
        override fun run() {
            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val date = SimpleDateFormat("EEEE, dd MMMM", Locale.getDefault()).format(Date()).uppercase()
            binding.clockText.text = time
            binding.dateText.text = date
            
            // Rotación de engranajes
            binding.outerGear.rotation += 1f
            binding.innerGear.rotation -= 1.5f
            
            handler.postDelayed(this, 30) // Actualización más rápida para suavidad
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            if (level != -1) {
                binding.batteryText.text = "$level%"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        startClock()
        registerBatteryReceiver()
    }

    private fun setupUI() {
        binding.bottomNav.selectedItemId = R.id.nav_dashboard
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                R.id.nav_whitelist -> {
                    startActivity(Intent(this, WhitelistActivity::class.java))
                    true
                }
                else -> true
            }
        }

        binding.jarvisCore.setOnClickListener {
            val intent = Intent(this, AssistantOverlayService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }

        binding.manageProtocolsBtn.setOnClickListener {
            val intent = Intent(this, ProtocolsActivity::class.java)
            startActivity(intent)
        }

        // WhatsApp AutoPilot UI
        val prefs = SaryPreferences.getInstance(this)
        
        binding.switchAutopilot.isChecked = prefs.isAutoPilotWhatsapp
        binding.etAutopilotContext.visibility = if (prefs.isAutoPilotWhatsapp) View.VISIBLE else View.GONE
        binding.etAutopilotContext.setText(prefs.whatsappAutoPilotContext)

        binding.switchAutopilot.setOnCheckedChangeListener { _, isChecked ->
            prefs.isAutoPilotWhatsapp = isChecked
            binding.etAutopilotContext.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        binding.etAutopilotContext.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                prefs.whatsappAutoPilotContext = s.toString()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun startClock() {
        handler.post(clockRunnable)
    }

    private fun registerBatteryReceiver() {
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(clockRunnable)
        unregisterReceiver(batteryReceiver)
    }
}
