package com.noexcs.indolent.ui.interact

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.text.InputType
import android.view.Gravity
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.NumberPicker
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.noexcs.indolent.R
import com.noexcs.indolent.logging.Lumberjack
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class InteractDialogActivity : ComponentActivity() {

    private var resultSent = false
    private var requestId = ""
    private val speechLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            sendResult(requestId, matches?.firstOrNull()?.ifBlank { "(no speech)" } ?: "(no speech)")
        } else {
            sendResult(requestId, "cancelled")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window?.apply {
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.6f)
            setGravity(Gravity.CENTER)
        }

        requestId = intent.getStringExtra(EXTRA_REQUEST_ID) ?: return finish()
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Question"
        val type = intent.getStringExtra(EXTRA_TYPE) ?: "confirm"
        val values = intent.getStringExtra(EXTRA_VALUES) ?: ""
        val hint = intent.getStringExtra(EXTRA_HINT) ?: ""
        val range = intent.getStringExtra(EXTRA_RANGE) ?: ""
        val dateFormat = intent.getStringExtra(EXTRA_DATE_FORMAT) ?: "yyyy-MM-dd"
        val numeric = intent.getBooleanExtra(EXTRA_NUMERIC, false)
        val password = intent.getBooleanExtra(EXTRA_PASSWORD, false)
        val multiline = intent.getBooleanExtra(EXTRA_MULTILINE, false)

        Lumberjack.i("InteractDialog", "Showing $type dialog: $title (request=$requestId)")

        when (type.lowercase()) {
            "text"     -> showText(title, hint, numeric, password, multiline)
            "confirm"  -> showConfirm(title, hint)
            "checkbox" -> showCheckbox(title, values)
            "radio"    -> showRadio(title, values)
            "counter"  -> showCounter(title, range)
            "date"     -> showDate(title, dateFormat)
            "time"     -> showTime(title)
            "speech"   -> showSpeech(title)
            else -> {
                sendResult(requestId, "error: unknown type '$type'")
                finish()
            }
        }
    }

    // ── text ────────────────────────────────────────────────

    private fun showText(title: String, hint: String, numeric: Boolean, password: Boolean, multiline: Boolean) {
        val input = EditText(this).apply {
            this.hint = hint.ifBlank {
                when { numeric -> "Enter a number"; password -> "Enter value"; multiline -> "Enter text"; else -> "Type your answer" }
            }
            inputType = when {
                numeric -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED or InputType.TYPE_NUMBER_FLAG_DECIMAL
                password -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                multiline -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                else -> InputType.TYPE_CLASS_TEXT
            }
            if (multiline) { minLines = 3; maxLines = 6 }
            setSingleLine(!multiline)
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(48, 24, 48, 8); addView(input)
        }
        AlertDialog.Builder(this, R.style.Theme_LinkX_Dialog)
            .setTitle(title).setView(container)
            .setPositiveButton("OK") { _, _ -> sendResult(requestId, input.text.toString().trim().ifBlank { "(empty)" }) }
            .setNegativeButton("Cancel") { _, _ -> sendResult(requestId, "cancelled") }
            .setOnCancelListener { sendResult(requestId, "cancelled") }
            .show()
    }

    // ── confirm ─────────────────────────────────────────────

    private fun showConfirm(title: String, hint: String) {
        AlertDialog.Builder(this, R.style.Theme_LinkX_Dialog)
            .setTitle(title)
            .setMessage(hint.ifBlank { "Please confirm" })
            .setPositiveButton("Yes") { _, _ -> sendResult(requestId, "yes") }
            .setNegativeButton("No") { _, _ -> sendResult(requestId, "no") }
            .setOnCancelListener { sendResult(requestId, "cancelled") }
            .show()
    }

    // ── checkbox (multi-select) ─────────────────────────────

    private fun showCheckbox(title: String, values: String) {
        val items = parseValues(values) ?: run { sendResult(requestId, "error: values required"); finish(); return }
        val checked = BooleanArray(items.size)

        AlertDialog.Builder(this, R.style.Theme_LinkX_Dialog)
            .setTitle(title)
            .setMultiChoiceItems(items.toTypedArray<CharSequence>(), checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("OK") { _, _ ->
                val selected = items.filterIndexed { i, _ -> checked[i] }
                sendResult(requestId, if (selected.isEmpty()) "(none)" else selected.joinToString(", "))
            }
            .setNegativeButton("Cancel") { _, _ -> sendResult(requestId, "cancelled") }
            .setOnCancelListener { sendResult(requestId, "cancelled") }
            .show()
    }

    // ── radio (single-choice) ───────────────────────────────

    private var radioSelection = 0

    private fun showRadio(title: String, values: String) {
        val items = parseValues(values) ?: run { sendResult(requestId, "error: values required"); finish(); return }
        radioSelection = 0

        AlertDialog.Builder(this, R.style.Theme_LinkX_Dialog)
            .setTitle(title)
            .setSingleChoiceItems(items.toTypedArray<CharSequence>(), 0) { _, which ->
                radioSelection = which
            }
            .setPositiveButton("OK") { _, _ -> sendResult(requestId, items[radioSelection]) }
            .setNegativeButton("Cancel") { _, _ -> sendResult(requestId, "cancelled") }
            .setOnCancelListener { sendResult(requestId, "cancelled") }
            .show()
    }

    // ── counter (number picker) ─────────────────────────────

    private fun showCounter(title: String, range: String) {
        val (min, max) = parseRange(range)
        val picker = NumberPicker(this).apply {
            this.minValue = min; this.maxValue = max; value = (min + max) / 2; wrapSelectorWheel = false
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(48, 16, 48, 16); addView(picker)
            gravity = Gravity.CENTER
        }
        AlertDialog.Builder(this, R.style.Theme_LinkX_Dialog)
            .setTitle(title)
            .setMessage("$min – $max")
            .setView(container)
            .setPositiveButton("OK") { _, _ -> sendResult(requestId, picker.value.toString()) }
            .setNegativeButton("Cancel") { _, _ -> sendResult(requestId, "cancelled") }
            .setOnCancelListener { sendResult(requestId, "cancelled") }
            .show()
    }

    // ── date ────────────────────────────────────────────────

    private fun showDate(title: String, dateFormat: String) {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            this, R.style.Theme_LinkX_Dialog,
            { _, year, month, day ->
                cal.set(year, month, day)
                val fmt = try { SimpleDateFormat(dateFormat, Locale.US) } catch (_: Exception) {
                    SimpleDateFormat("yyyy-MM-dd", Locale.US)
                }
                sendResult(requestId, fmt.format(cal.time))
            },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
        ).apply {
            setTitle(title)
            setOnCancelListener { sendResult(requestId, "cancelled") }
        }.show()
    }

    // ── time ────────────────────────────────────────────────

    private fun showTime(title: String) {
        val cal = Calendar.getInstance()
        TimePickerDialog(
            this, R.style.Theme_LinkX_Dialog,
            { _, hour, minute ->
                sendResult(requestId, "%02d:%02d".format(hour, minute))
            },
            cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true
        ).apply {
            setTitle(title)
            setOnCancelListener { sendResult(requestId, "cancelled") }
        }.show()
    }

    // ── speech ──────────────────────────────────────────────

    private fun showSpeech(title: String) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, title)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
        try {
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            Lumberjack.e("InteractDialog", "Speech recognizer unavailable", e)
            sendResult(requestId, "error: speech recognizer not available on this device")
        }
    }

    // ── helpers ─────────────────────────────────────────────

    private fun parseValues(raw: String): List<String>? {
        val items = raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
        return items.ifEmpty { null }
    }

    private fun parseRange(raw: String): Pair<Int, Int> {
        val parts = raw.split(",").map { it.trim() }
        val min = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val max = parts.getOrNull(1)?.toIntOrNull() ?: 100
        return if (min < max) Pair(min, max) else Pair(max, min)
    }

    private fun sendResult(requestId: String, answer: String) {
        if (resultSent) return
        resultSent = true
        Lumberjack.i("InteractDialog", "Result: $answer (request=$requestId)")
        val intent = Intent(RESPONSE_ACTION).apply {
            setPackage(packageName)
            putExtra(EXTRA_REQUEST_ID, requestId)
            putExtra(EXTRA_ANSWER, answer)
        }
        sendBroadcast(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        val rid = intent.getStringExtra(EXTRA_REQUEST_ID)
        if (rid != null && !resultSent) {
            resultSent = true
            val intent = Intent(RESPONSE_ACTION).apply {
                setPackage(packageName)
                putExtra(EXTRA_REQUEST_ID, rid)
                putExtra(EXTRA_ANSWER, "dismissed")
            }
            sendBroadcast(intent)
        }
    }

    companion object {
        const val RESPONSE_ACTION = "com.noexcs.indolent.USER_RESPONSE"
        const val EXTRA_TITLE = "title"
        const val EXTRA_TYPE = "type"
        const val EXTRA_VALUES = "values"
        const val EXTRA_HINT = "hint"
        const val EXTRA_RANGE = "range"
        const val EXTRA_DATE_FORMAT = "date_format"
        const val EXTRA_NUMERIC = "numeric"
        const val EXTRA_PASSWORD = "password"
        const val EXTRA_MULTILINE = "multiline"
        const val EXTRA_REQUEST_ID = "request_id"
        const val EXTRA_ANSWER = "answer"
    }
}
