package com.maliar.pro.dialogs

import android.app.AlertDialog
import android.content.Context
import android.media.MediaPlayer
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageButton
import android.widget.ListView
import android.widget.TextView
import com.maliar.pro.R
import com.maliar.pro.utils.ReminderSound

/**
 * Lets the person browse every built-in reminder voice, hear a quick preview of each one
 * (▶ toggles play/stop right in the row, doesn't close the dialog), and either confirm a
 * built-in sound or jump to the device's own ringtone/music picker - all in one place so
 * Add/Edit reminder dialogs don't each duplicate this UI.
 */
object SoundPickerDialog {

    fun show(
        context: Context,
        onDeviceAudioRequested: (onPicked: (String) -> Unit) -> Unit,
        onSelected: (String) -> Unit
    ) {
        val entries = ReminderSound.builtIns.map { it.label to it.value } +
            ("انتخاب فایل یا موسیقی از گوشی" to null)

        var mediaPlayer: MediaPlayer? = null
        var playingIndex: Int = -1

        fun stopPreview() {
            mediaPlayer?.let { runCatching { it.stop(); it.release() } }
            mediaPlayer = null
            playingIndex = -1
        }

        val listView = ListView(context)
        val adapter = object : BaseAdapter() {
            override fun getCount() = entries.size
            override fun getItem(position: Int) = entries[position]
            override fun getItemId(position: Int) = position.toLong()

            override fun getView(position: Int, convertViewIn: android.view.View?, parent: ViewGroup): android.view.View {
                val convertView = convertViewIn
                    ?: LayoutInflater.from(context).inflate(R.layout.item_sound_picker, parent, false)
                val label = convertView.findViewById<TextView>(R.id.soundLabelText)
                val preview = convertView.findViewById<ImageButton>(R.id.soundPreviewButton)
                val (text, value) = entries[position]
                label.text = text
                // Every real entry (including "هشدار پیش‌فرض گوشی") resolves to a playable
                // URI via ReminderSound.toUri - only the "انتخاب فایل..." placeholder row
                // (value == null, nothing chosen yet) has nothing to preview.
                preview.visibility = if (value != null) android.view.View.VISIBLE else android.view.View.INVISIBLE
                preview.setImageResource(
                    if (playingIndex == position) android.R.drawable.ic_media_pause
                    else android.R.drawable.ic_media_play
                )
                preview.setOnClickListener {
                    if (playingIndex == position) {
                        stopPreview()
                        notifyDataSetChanged()
                        return@setOnClickListener
                    }
                    stopPreview()
                    val uri = value?.let { ReminderSound.toUri(context, it) }
                    if (uri != null) {
                        try {
                            mediaPlayer = MediaPlayer().apply {
                                setDataSource(context, uri)
                                setOnCompletionListener { stopPreview(); notifyDataSetChanged() }
                                prepare()
                                start()
                            }
                            playingIndex = position
                        } catch (e: Exception) {
                            stopPreview()
                        }
                    }
                    notifyDataSetChanged()
                }
                return convertView
            }
        }
        listView.adapter = adapter

        val dialog = AlertDialog.Builder(context)
            .setTitle("انتخاب صدای یادآوری")
            .setView(listView)
            .setNegativeButton("انصراف", null)
            .setOnDismissListener { stopPreview() }
            .create()

        listView.setOnItemClickListener { _, _, position, _ ->
            stopPreview()
            val (_, value) = entries[position]
            dialog.dismiss()
            if (value != null) {
                onSelected(value)
            } else {
                onDeviceAudioRequested(onSelected)
            }
        }

        dialog.show()
    }
}
