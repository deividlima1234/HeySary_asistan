package com.eddam.heysary

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.eddam.heysary.databinding.ActivitySettingsBinding
import android.view.View
import android.widget.AdapterView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
        binding.whatsappIpEdit.setText(prefs.whatsappGatewayIp)
        binding.whatsappTokenEdit.setText(prefs.whatsappGatewayToken)

        val models = arrayOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant", "mixtral-8x7b-32768")
        val adapter = ArrayAdapter(this, R.layout.custom_spinner_item, models)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.modelSpinner.adapter = adapter
        
        val currentModelIndex = models.indexOf(prefs.groqModel)
        if (currentModelIndex >= 0) {
            binding.modelSpinner.setSelection(currentModelIndex)
        }

        // --- Configuración de TTS ---
        val ttsEngines = arrayOf(SaryPreferences.ENGINE_NATIVE, SaryPreferences.ENGINE_ELEVENLABS, SaryPreferences.ENGINE_RESEMBLE)
        val ttsAdapter = ArrayAdapter(this, R.layout.custom_spinner_item, ttsEngines)
        ttsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.ttsEngineSpinner.adapter = ttsAdapter
        binding.ttsEngineSpinner.setSelection(ttsEngines.indexOf(prefs.ttsEngine).coerceAtLeast(0))

        binding.ttsEngineSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedEngine = ttsEngines[position]
                if (selectedEngine == SaryPreferences.ENGINE_NATIVE) {
                    binding.ttsCloudContainer.visibility = View.GONE
                } else {
                    binding.ttsCloudContainer.visibility = View.VISIBLE
                    if (selectedEngine == SaryPreferences.ENGINE_ELEVENLABS) {
                        binding.ttsApiEditText.hint = "ElevenLabs API Key"
                        binding.ttsApiEditText.setText(prefs.elevenLabsApiKey ?: "")
                    } else if (selectedEngine == SaryPreferences.ENGINE_RESEMBLE) {
                        binding.ttsApiEditText.hint = "Resemble AI API Key"
                        binding.ttsApiEditText.setText(prefs.resembleApiKey ?: "")
                    }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.ttsLoadVoicesBtn.setOnClickListener {
            val selectedEngine = binding.ttsEngineSpinner.selectedItem.toString()
            val apiKey = binding.ttsApiEditText.text.toString().trim()
            
            if (apiKey.isEmpty()) {
                Toast.makeText(this, "Por favor, introduce una API Key antes de cargar", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Guardado temporal de la clave para que los clientes puedan usarla inmediatamente
            when (selectedEngine) {
                SaryPreferences.ENGINE_ELEVENLABS -> prefs.elevenLabsApiKey = apiKey
                SaryPreferences.ENGINE_RESEMBLE -> prefs.resembleApiKey = apiKey
            }

            loadVoicesForEngine(selectedEngine)
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

            val selectedTtsEngine = binding.ttsEngineSpinner.selectedItem.toString()
            val ttsKey = binding.ttsApiEditText.text.toString().trim()
            
            prefs.ttsEngine = selectedTtsEngine
            if (selectedTtsEngine == SaryPreferences.ENGINE_ELEVENLABS && ttsKey.isNotEmpty()) {
                prefs.elevenLabsApiKey = ttsKey
            } else if (selectedTtsEngine == SaryPreferences.ENGINE_RESEMBLE && ttsKey.isNotEmpty()) {
                prefs.resembleApiKey = ttsKey
            }
            
            prefs.whatsappGatewayIp = binding.whatsappIpEdit.text.toString().trim()
            prefs.whatsappGatewayToken = binding.whatsappTokenEdit.text.toString().trim()

            if (newKey.startsWith("gsk_")) {
                prefs.groqApiKey = newKey
                prefs.groqModel = newModel
                prefs.ambientVolume = binding.volumeSeekbar.progress / 100f
                Toast.makeText(this, "Configuración actualizada", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this, "Clave API de Groq inválida", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnWhatsappStatus.setOnClickListener {
            // Guardamos preferencias actuales por si fueron tipeadas antes de salir
            prefs.whatsappGatewayIp = binding.whatsappIpEdit.text.toString().trim()
            prefs.whatsappGatewayToken = binding.whatsappTokenEdit.text.toString().trim()
            
            startActivity(Intent(this, WhatsAppStatusActivity::class.java))
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

    private fun loadVoicesForEngine(engine: String) {
        binding.ttsLoadVoicesBtn.isEnabled = false
        binding.ttsLoadVoicesBtn.text = "CARGANDO..."

        lifecycleScope.launch {
            try {
                if (engine == SaryPreferences.ENGINE_ELEVENLABS) {
                    val client = ElevenLabsClient(this@SettingsActivity)
                    val voices = client.getVoices()
                    val voiceNames = voices.map { "${it.name} (${it.category})" }
                    
                    val adapter = ArrayAdapter(this@SettingsActivity, R.layout.custom_spinner_item, voiceNames)
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    binding.ttsVoiceSpinner.adapter = adapter
                    
                    val savedVoiceId = prefs.elevenLabsVoiceId
                    val index = voices.indexOfFirst { it.voiceId == savedVoiceId }
                    if (index >= 0) binding.ttsVoiceSpinner.setSelection(index)
                    
                    binding.ttsVoiceSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                            prefs.elevenLabsVoiceId = voices[position].voiceId
                        }
                        override fun onNothingSelected(parent: AdapterView<*>?) {}
                    }
                    
                } else if (engine == SaryPreferences.ENGINE_RESEMBLE) {
                    val client = ResembleAiClient(this@SettingsActivity)
                    val voices = client.getVoices()
                    val voiceNames = voices.map { "${it.name} (${it.status})" }
                    
                    val adapter = ArrayAdapter(this@SettingsActivity, R.layout.custom_spinner_item, voiceNames)
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    binding.ttsVoiceSpinner.adapter = adapter
                    
                    val savedVoiceUuid = prefs.resembleVoiceUuid
                    val index = voices.indexOfFirst { it.uuid == savedVoiceUuid }
                    if (index >= 0) binding.ttsVoiceSpinner.setSelection(index)
                    
                    binding.ttsVoiceSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                            prefs.resembleVoiceUuid = voices[position].uuid
                        }
                        override fun onNothingSelected(parent: AdapterView<*>?) {}
                    }
                }
                
                Toast.makeText(this@SettingsActivity, "Voces cargadas exitosamente", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, "Error al cargar: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.ttsLoadVoicesBtn.isEnabled = true
                binding.ttsLoadVoicesBtn.text = "CARGAR VOCES DISPONIBLES"
            }
        }
    }
}
