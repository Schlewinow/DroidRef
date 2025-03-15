package xyz.ruin.droidref

import android.content.Context

/**
 * Global settings management and permanent storage.
 * Settings are automatically stored when their respective setter is being called.
  */
object SettingsStorage {
    enum class MaxResolutions {
        MAX1024, MAX2048, MAX3072, MAX4096
    }

    private const val SHARED_PREF_ACCESS = "BoardSettings"
    private const val DEFAULT_ACTIVATE_DOWNSCALE_ON_IMPORT = true
    private const val DEFAULT_ACTIVATE_MULTI_STEP_SCALE = true
    private const val MAX_RESOLUTION_1 = 1024 * 1024
    private const val MAX_RESOLUTION_2 = 2048 * 2048
    private const val MAX_RESOLUTION_3 = 3072 * 3072
    private const val MAX_RESOLUTION_4 = 4096 * 4096

    private var activateDownscaleOnImport = DEFAULT_ACTIVATE_DOWNSCALE_ON_IMPORT
    private var activateMultiStepScaleOnImport = DEFAULT_ACTIVATE_MULTI_STEP_SCALE
    private var maxResolutionOnImport = MAX_RESOLUTION_2

    fun getActivateDownscaleOnImport() : Boolean {
        return activateDownscaleOnImport
    }

    fun setActivateDownscaleOnImport(activate: Boolean, context: Context?) {
        activateDownscaleOnImport = activate
        saveSettings(context)
    }

    fun getActivateMultiStepScaleOnImport() : Boolean {
        return activateMultiStepScaleOnImport
    }

    fun setActivateMultiStepScaleOnImport(activate: Boolean, context: Context?) {
        activateMultiStepScaleOnImport = activate
        saveSettings(context)
    }

    fun getMaxResolutionOnImport() : Int {
        return maxResolutionOnImport
    }

    fun getMaxResolutionOnImportFlag() : MaxResolutions {
        when (maxResolutionOnImport) {
            MAX_RESOLUTION_1 -> return MaxResolutions.MAX1024
            MAX_RESOLUTION_2 -> return MaxResolutions.MAX2048
            MAX_RESOLUTION_3 -> return MaxResolutions.MAX3072
            MAX_RESOLUTION_4 -> return MaxResolutions.MAX4096
        }
        return MaxResolutions.MAX2048
    }

    fun setMaxResolutionOnImport(maxResolution: MaxResolutions, context: Context?) {
        maxResolutionOnImport = when (maxResolution) {
            MaxResolutions.MAX1024 -> MAX_RESOLUTION_1
            MaxResolutions.MAX2048 -> MAX_RESOLUTION_2
            MaxResolutions.MAX3072 -> MAX_RESOLUTION_3
            MaxResolutions.MAX4096 -> MAX_RESOLUTION_4
        }
        saveSettings(context)
    }

    fun restoreSettings(context: Context?) {
        if (context == null) {
            return
        }

        val prefs = context.getSharedPreferences(SHARED_PREF_ACCESS, Context.MODE_PRIVATE)
        activateDownscaleOnImport = prefs.getBoolean(SHARED_PREF_ACCESS + "ActivateDownscaleOnImport", DEFAULT_ACTIVATE_DOWNSCALE_ON_IMPORT)
        activateMultiStepScaleOnImport = prefs.getBoolean(SHARED_PREF_ACCESS + "ActivateMultiStepScaleOnImport", DEFAULT_ACTIVATE_MULTI_STEP_SCALE)
        maxResolutionOnImport = prefs.getInt(SHARED_PREF_ACCESS + "MaxResolutionOnImport", MAX_RESOLUTION_2)
    }

    private fun saveSettings(context: Context?) {
        if (context == null) {
            return
        }

        val prefs = context.getSharedPreferences(SHARED_PREF_ACCESS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putBoolean(SHARED_PREF_ACCESS + "ActivateDownscaleOnImport", activateDownscaleOnImport)
        editor.putBoolean(SHARED_PREF_ACCESS + "ActivateMultiStepScaleOnImport", activateMultiStepScaleOnImport)
        editor.putInt(SHARED_PREF_ACCESS + "MaxResolutionOnImport", maxResolutionOnImport)
        editor.apply()
    }
}