package com.alphadot.app.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.alphadot.app.R
import com.alphadot.app.data.ProviderConfig
import com.alphadot.app.data.SettingsStore
import com.alphadot.app.provider.GeminiProvider
import com.alphadot.app.provider.OpenAICompatibleProvider

/**
 * Settings screen: choose an AI provider and supply user-owned credentials.
 * Nothing entered here is bundled with the APK or sent anywhere except the
 * provider the user selects.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var settings: SettingsStore
    private lateinit var radioLocal: RadioButton
    private lateinit var radioGemini: RadioButton
    private lateinit var radioOpenai: RadioButton
    private lateinit var apiKey: EditText
    private lateinit var baseUrl: EditText
    private lateinit var model: EditText
    private lateinit var systemPrompt: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
            .setNavigationOnClickListener { finish() }

        settings = SettingsStore(this)
        radioLocal = findViewById(R.id.radioLocal)
        radioGemini = findViewById(R.id.radioGemini)
        radioOpenai = findViewById(R.id.radioOpenai)
        apiKey = findViewById(R.id.apiKey)
        baseUrl = findViewById(R.id.baseUrl)
        model = findViewById(R.id.model)
        systemPrompt = findViewById(R.id.systemPrompt)
        val save = findViewById<Button>(R.id.save)

        bind(settings.load())

        save.setOnClickListener {
            settings.save(
                ProviderConfig(
                    type = selectedType(),
                    apiKey = apiKey.text.toString().trim(),
                    baseUrl = baseUrl.text.toString().trim(),
                    model = model.text.toString().trim(),
                    systemPrompt = systemPrompt.text.toString()
                )
            )
            Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun bind(config: ProviderConfig) {
        when (config.type) {
            SettingsStore.TYPE_GEMINI -> radioGemini.isChecked = true
            SettingsStore.TYPE_OPENAI -> radioOpenai.isChecked = true
            else -> radioLocal.isChecked = true
        }
        apiKey.setText(config.apiKey)
        baseUrl.setText(config.baseUrl)
        model.setText(config.model)
        systemPrompt.setText(config.systemPrompt)
    }

    private fun selectedType(): String = when {
        radioGemini.isChecked -> SettingsStore.TYPE_GEMINI
        radioOpenai.isChecked -> SettingsStore.TYPE_OPENAI
        else -> SettingsStore.TYPE_LOCAL
    }
}
