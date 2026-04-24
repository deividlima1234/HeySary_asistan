package com.eddam.heysary

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.eddam.heysary.databinding.ActivityProtocolsBinding
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial

class ProtocolsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProtocolsBinding
    private lateinit var prefs: SaryPreferences
    private lateinit var protocolEngine: ProtocolEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProtocolsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = SaryPreferences.getInstance(this)
        protocolEngine = ProtocolEngine(this)

        setupUI()
        loadProtocols()
    }

    private fun setupUI() {
        binding.bottomNav.selectedItemId = R.id.nav_dashboard // O crear un ID nuevo
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    finish() // Volver al dashboard
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

    private fun loadProtocols() {
        val protocols = prefs.getProtocols()
        binding.protocolsContainer.removeAllViews()

        protocols.forEach { protocol ->
            addProtocolCard(protocol, protocols)
        }
    }

    private fun addProtocolCard(protocol: AutoProtocol, allProtocols: List<AutoProtocol>) {
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 32) }
            cardElevation = 8f
            radius = 16f
            setCardBackgroundColor(getColor(R.color.cyberpunk_black))
            strokeColor = getColor(if (protocol.isEnabled) R.color.cyberpunk_red else R.color.cyberpunk_red_glow)
            strokeWidth = 2
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val title = TextView(this).apply {
            text = protocol.name
            setTextColor(getColor(R.color.white))
            textSize = 18f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val switch = SwitchMaterial(this).apply {
            isChecked = protocol.isEnabled
            setOnCheckedChangeListener { _, isChecked ->
                protocol.isEnabled = isChecked
                prefs.saveProtocols(allProtocols)
                protocolEngine.scheduleProtocol(protocol)
                card.setStrokeColor(androidx.core.content.ContextCompat.getColorStateList(this@ProtocolsActivity, if (isChecked) R.color.cyberpunk_red else R.color.cyberpunk_red_glow))
            }
        }

        headerLayout.addView(title)
        headerLayout.addView(switch)

        val desc = TextView(this).apply {
            text = "${protocol.description}\nHora: ${protocol.currentTime}"
            setTextColor(getColor(R.color.gray_accent))
            textSize = 14f
            setPadding(0, 8, 0, 0)
            
            // Permitir cambiar la hora al hacer click
            setOnClickListener {
                val (h, m) = protocol.currentTime.split(":").map { it.toInt() }
                android.app.TimePickerDialog(this@ProtocolsActivity, { _, hour, minute ->
                    val newTime = String.format("%02d:%02d", hour, minute)
                    protocol.currentTime = newTime
                    text = "${protocol.description}\nHora: $newTime"
                    
                    prefs.saveProtocols(allProtocols)
                    if (protocol.isEnabled) {
                        protocolEngine.scheduleProtocol(protocol)
                    }
                }, h, m, true).show()
            }
        }

        layout.addView(headerLayout)
        layout.addView(desc)
        card.addView(layout)

        binding.protocolsContainer.addView(card)
    }
    
    // Extensión para simplificar tamaños
    private val Int.sp get() = this.toFloat()
}
