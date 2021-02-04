package com.simplemobiletools.musicplayer.activities

import android.annotation.SuppressLint
import android.media.MediaPlayer
import android.media.audiofx.Equalizer
import android.os.Bundle
import android.view.Menu
import android.widget.SeekBar
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.simplemobiletools.commons.dialogs.RadioGroupDialog
import com.simplemobiletools.commons.extensions.updateTextColors
import com.simplemobiletools.commons.models.RadioItem
import com.simplemobiletools.commons.views.MySeekBar
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.helpers.EQUALIZER_PRESET_CUSTOM
import com.simplemobiletools.musicplayer.services.MusicService
import kotlinx.android.synthetic.main.activity_equalizer.*
import kotlinx.android.synthetic.main.equalizer_band.view.*
import java.text.DecimalFormat
import java.util.*

class EqualizerActivity : SimpleActivity() {
    private var bands = HashMap<Short, Int>()
    private var bandSeekBars = ArrayList<MySeekBar>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_equalizer)
        updateTextColors(equalizer_holder)
        initMediaPlayer()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        updateMenuItemColors(menu)
        return super.onCreateOptionsMenu(menu)
    }

    @SuppressLint("SetTextI18n")
    private fun initMediaPlayer() {
        val player = MusicService.mPlayer ?: MediaPlayer()
        val equalizer = Equalizer(0, player.audioSessionId)
        equalizer.enabled = true
        setupBands(equalizer)
        setupPresets(equalizer)
    }

    private fun setupBands(equalizer: Equalizer) {
        val minValue = equalizer.bandLevelRange[0]
        val maxValue = equalizer.bandLevelRange[1]
        equalizer_label_right.text = "+${maxValue / 100}"
        equalizer_label_left.text = "${minValue / 100}"
        equalizer_label_0.text = (minValue + maxValue).toString()

        bandSeekBars.clear()
        equalizer_bands_holder.removeAllViews()

        val bandType = object : TypeToken<HashMap<Short, Int>>() {}.type
        bands = Gson().fromJson<HashMap<Short, Int>>(config.equalizerBands, bandType) ?: HashMap()

        for (band in 0 until equalizer.numberOfBands) {
            val frequency = equalizer.getCenterFreq(band.toShort()) / 1000
            val formatted = formatFrequency(frequency)

            layoutInflater.inflate(R.layout.equalizer_band, equalizer_bands_holder, false).apply {
                equalizer_bands_holder.addView(this)
                bandSeekBars.add(this.equalizer_band_seek_bar)
                this.equalizer_band_label.text = formatted
                this.equalizer_band_label.setTextColor(config.textColor)
                this.equalizer_band_seek_bar.max = maxValue - minValue

                this.equalizer_band_seek_bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                        if (fromUser) {
                            val newValue = progress + minValue
                            equalizer.setBandLevel(band.toShort(), newValue.toShort())
                            bands[band.toShort()] = newValue
                        }
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar) {
                        draggingStarted(equalizer)
                    }

                    override fun onStopTrackingTouch(seekBar: SeekBar) {
                        bands[band.toShort()] = this@apply.equalizer_band_seek_bar.progress
                        config.equalizerBands = Gson().toJson(bands)
                    }
                })
            }
        }
    }

    private fun draggingStarted(equalizer: Equalizer) {
        equalizer_preset.text = getString(R.string.custom)
        config.equalizerPreset = EQUALIZER_PRESET_CUSTOM
        for (band in 0 until equalizer.numberOfBands) {
            bands[band.toShort()] = bandSeekBars[band].progress
        }
    }

    private fun setupPresets(equalizer: Equalizer) {
        presetChanged(config.equalizerPreset, equalizer)
        equalizer_preset.setOnClickListener {
            val items = arrayListOf<RadioItem>()
            (0 until equalizer.numberOfPresets).mapTo(items) {
                RadioItem(it, equalizer.getPresetName(it.toShort()))
            }

            items.add(RadioItem(EQUALIZER_PRESET_CUSTOM, getString(R.string.custom)))
            RadioGroupDialog(this, items, config.equalizerPreset) { presetId ->
                config.equalizerPreset = presetId as Int
                presetChanged(presetId, equalizer)
            }
        }
    }

    private fun presetChanged(presetId: Int, equalizer: Equalizer) {
        if (presetId == EQUALIZER_PRESET_CUSTOM) {
            equalizer_preset.text = getString(R.string.custom)

            for (band in 0 until equalizer.numberOfBands) {
                val progress = if (bands.containsKey(band.toShort())) {
                    bands[band.toShort()]
                } else {
                    val minValue = equalizer.bandLevelRange[0]
                    val maxValue = equalizer.bandLevelRange[1]
                    (maxValue - minValue) / 2
                }

                bandSeekBars[band].progress = progress!!.toInt()
            }
        } else {
            val presetName = equalizer.getPresetName(presetId.toShort())
            if (presetName.isEmpty()) {
                config.equalizerPreset = EQUALIZER_PRESET_CUSTOM
                equalizer_preset.text = getString(R.string.custom)
            } else {
                equalizer_preset.text = presetName
            }

            equalizer.usePreset(presetId.toShort())

            val lowestBandLevel = equalizer.bandLevelRange?.get(0)
            for (band in 0 until equalizer.numberOfBands) {
                val level = equalizer.getBandLevel(band.toShort()).minus(lowestBandLevel!!)
                bandSeekBars[band].progress = level
            }
        }
    }

    // copypasted  from the file size formatter, should be simplified
    private fun formatFrequency(value: Int): String {
        if (value <= 0) {
            return "0 Hz"
        }

        val units = arrayOf("Hz", "kHz", "gHz")
        val digitGroups = (Math.log10(value.toDouble()) / Math.log10(1000.0)).toInt()
        return "${DecimalFormat("#,##0.#").format(value / Math.pow(1000.0, digitGroups.toDouble()))} ${units[digitGroups]}"
    }
}