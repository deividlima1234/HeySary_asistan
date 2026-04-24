package com.eddam.heysary

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.eddam.heysary.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: SaryPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = SaryPreferences.getInstance(this)
        setupUI()
    }

    private fun setupUI() {
        binding.apiEditText.setText(prefs.groqApiKey)

        val models = arrayOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant", "mixtral-8x7b-32768")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, models)
        binding.modelSpinner.adapter = adapter
        
        val currentModelIndex = models.indexOf(prefs.groqModel)
        if (currentModelIndex >= 0) {
            binding.modelSpinner.setSelection(currentModelIndex)
        }

        // Configuración de Volumen
        binding.volumeSeekbar.progress = (prefs.ambientVolume * 100).toInt()

        // Configuración de Notificaciones
        binding.readNotifSwitch.isChecked = prefs.readNotificationsEnabled
        binding.readNotifSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.readNotificationsEnabled = isChecked
        }

        binding.notifPermissionBtn.setOnClickListener {
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        }

        binding.saveConfigBtn.setOnClickListener {
            val newKey = binding.apiEditText.text.toString().trim()
            val newModel = binding.modelSpinner.selectedItem.toString()

            if (newKey.startsWith("gsk_")) {
                prefs.groqApiKey = newKey
                prefs.groqModel = newModel
                prefs.ambientVolume = binding.volumeSeekbar.progress / 100f
                Toast.makeText(this, "Configuración actualizada", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this, "Clave API inválida", Toast.LENGTH_SHORT).show()
            }
        }

        binding.bottomNav.selectedItemId = R.id.nav_settings
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    startActivity(Intent(this, DashboardActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_whitelist -> {
                    startActivity(Intent(this, WhitelistActivity::class.java))
                    finish()
                    true
                }
                else -> true
            }
        }
    }
}
