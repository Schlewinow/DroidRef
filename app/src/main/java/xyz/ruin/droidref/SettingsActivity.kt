package xyz.ruin.droidref

import android.os.Bundle
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children

class SettingsActivity : AppCompatActivity() {
    private var activateDownscaleCheckBox: CheckBox? = null
    private var activateMultiStepScaleCheckBox: CheckBox? = null
    private var maxResolutionRadioGroup: RadioGroup? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val closeButton = findViewById<ImageButton>(R.id.settingsCloseButton)
        closeButton.setOnClickListener {
            finish()
        }

        activateDownscaleCheckBox = findViewById(R.id.settingsActivateScalingCheckBox)
        activateDownscaleCheckBox?.isChecked = SettingsStorage.getActivateDownscaleOnImport()
        activateDownscaleCheckBox?.setOnClickListener {
            val activated = activateDownscaleCheckBox?.isChecked
            SettingsStorage.setActivateDownscaleOnImport(activated!!, this)
            activateImportSettings(activated)
        }

        activateMultiStepScaleCheckBox = findViewById(R.id.settingsMultiStepDownscalingCheckBox)
        activateMultiStepScaleCheckBox?.isChecked = SettingsStorage.getActivateMultiStepScaleOnImport()
        activateMultiStepScaleCheckBox?.setOnClickListener {
            SettingsStorage.setActivateMultiStepScaleOnImport(activateMultiStepScaleCheckBox?.isChecked!!, this)
        }

        maxResolutionRadioGroup = findViewById(R.id.settingsMaxResolutionRadioGroup)
        val maxResolutionRadioButton1 = findViewById<RadioButton>(R.id.settingsMaxResolutionRadioButton1)
        val maxResolutionRadioButton2 = findViewById<RadioButton>(R.id.settingsMaxResolutionRadioButton2)
        val maxResolutionRadioButton3 = findViewById<RadioButton>(R.id.settingsMaxResolutionRadioButton3)
        val maxResolutionRadioButton4 = findViewById<RadioButton>(R.id.settingsMaxResolutionRadioButton4)

        when (SettingsStorage.getMaxResolutionOnImportFlag()) {
            SettingsStorage.MaxResolutions.MAX1024 -> maxResolutionRadioButton1.isChecked = true
            SettingsStorage.MaxResolutions.MAX2048 -> maxResolutionRadioButton2.isChecked = true
            SettingsStorage.MaxResolutions.MAX3072 -> maxResolutionRadioButton3.isChecked = true
            SettingsStorage.MaxResolutions.MAX4096 -> maxResolutionRadioButton4.isChecked = true
        }

        maxResolutionRadioButton1.setOnClickListener {
            if (maxResolutionRadioButton1.isChecked) {
                SettingsStorage.setMaxResolutionOnImport(SettingsStorage.MaxResolutions.MAX1024, this)
            }
        }
        maxResolutionRadioButton2.setOnClickListener {
            if (maxResolutionRadioButton2.isChecked) {
                SettingsStorage.setMaxResolutionOnImport(SettingsStorage.MaxResolutions.MAX2048, this)
            }
        }
        maxResolutionRadioButton3.setOnClickListener {
            if (maxResolutionRadioButton3.isChecked) {
                SettingsStorage.setMaxResolutionOnImport(SettingsStorage.MaxResolutions.MAX3072, this)
            }
        }
        maxResolutionRadioButton4.setOnClickListener {
            if (maxResolutionRadioButton4.isChecked) {
                SettingsStorage.setMaxResolutionOnImport(SettingsStorage.MaxResolutions.MAX4096, this)
            }
        }

        activateImportSettings(SettingsStorage.getActivateDownscaleOnImport())
    }

    private fun activateImportSettings(activate: Boolean) {
        activateMultiStepScaleCheckBox?.isEnabled = activate
        maxResolutionRadioGroup?.isEnabled = activate
        for (radioChild in maxResolutionRadioGroup?.children!!) {
            radioChild.isEnabled = activate
        }
    }
}