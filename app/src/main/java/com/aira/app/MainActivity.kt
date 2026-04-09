package com.aira.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.aira.app.data.model.CommandSource
import com.aira.app.databinding.ActivityMainBinding
import com.aira.app.ui.CommandHistoryAdapter
import com.aira.app.ui.MainViewModel
import com.aira.app.ui.PendingCommandConfirmation
import com.aira.app.voice.VoiceCommandManager
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), VoiceCommandManager.Callback {
    private lateinit var binding: ActivityMainBinding
    private lateinit var historyAdapter: CommandHistoryAdapter
    private lateinit var voiceCommandManager: VoiceCommandManager

    private var shownConfirmationId: Long? = null

    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory(application)
    }

    private val microphonePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            startVoiceCaptureForCurrentMode()
        } else {
            viewModel.onVoiceError("Microphone permission is required for voice control.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        voiceCommandManager = VoiceCommandManager(this, this)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                systemInsets.left,
                systemInsets.top,
                systemInsets.right,
                systemInsets.bottom,
            )
            insets
        }

        historyAdapter = CommandHistoryAdapter()
        binding.recyclerHistory.adapter = historyAdapter

        bindUi()
        observeUi()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshSystemState()
        if (viewModel.uiState.value.continuousListeningEnabled && hasMicrophonePermission()) {
            voiceCommandManager.setContinuousMode(true)
            if (!voiceCommandManager.isListening) {
                voiceCommandManager.startContinuousListening()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (!isChangingConfigurations) {
            voiceCommandManager.stopListening()
        }
    }

    override fun onDestroy() {
        voiceCommandManager.destroy()
        super.onDestroy()
    }

    private fun bindUi() {
        binding.buttonMic.setOnClickListener {
            if (voiceCommandManager.isListening) {
                voiceCommandManager.stopListening()
            } else {
                requestMicrophoneThenStart()
            }
        }

        binding.switchContinuous.setOnCheckedChangeListener { _, isChecked ->
            voiceCommandManager.setContinuousMode(isChecked)
            viewModel.onContinuousListeningChanged(isChecked)
            if (isChecked) {
                requestMicrophoneThenStart()
            } else {
                voiceCommandManager.stopListening()
            }
        }

        binding.buttonAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.buttonOverlay.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"),
            )
            startActivity(intent)
        }

        binding.buttonBattery.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:$packageName"),
            )
            startActivity(intent)
        }
    }

    private fun observeUi() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    render(state)
                }
            }
        }
    }

    private fun render(state: com.aira.app.ui.MainUiState) {
        binding.textStatusPill.text = state.phase.name.lowercase().replaceFirstChar { it.titlecase() }
        binding.textStatusHeadline.text = state.statusHeadline
        binding.textStatusBody.text = state.statusBody
        binding.textLastTranscript.text =
            state.lastTranscript.ifBlank { getString(R.string.last_transcript_placeholder) }
        binding.buttonMic.text = when {
            state.isListening -> getString(R.string.stop_listening)
            state.continuousListeningEnabled -> getString(R.string.start_hotword_mode)
            else -> getString(R.string.tap_to_speak)
        }

        if (binding.switchContinuous.isChecked != state.continuousListeningEnabled) {
            binding.switchContinuous.isChecked = state.continuousListeningEnabled
        }

        binding.buttonAccessibility.text = if (state.accessibilityEnabled) {
            getString(R.string.accessibility_ready)
        } else {
            getString(R.string.enable_accessibility)
        }
        binding.buttonOverlay.text = if (state.overlayEnabled) {
            getString(R.string.overlay_ready)
        } else {
            getString(R.string.enable_overlay)
        }
        binding.buttonBattery.text = if (state.batteryOptimizationIgnored) {
            getString(R.string.battery_ready)
        } else {
            getString(R.string.ignore_battery_optimization)
        }

        historyAdapter.submitList(state.history)
        renderShortcutChips(state)
        renderConfirmation(state.pendingConfirmation)
    }

    private fun renderShortcutChips(state: com.aira.app.ui.MainUiState) {
        binding.chipGroupSuggestions.removeAllViews()
        if (state.suggestions.isEmpty()) {
            val placeholderChip = Chip(this).apply {
                text = getString(R.string.suggestions_placeholder)
                isEnabled = false
            }
            binding.chipGroupSuggestions.addView(placeholderChip)
            return
        }

        state.suggestions.forEach { suggestion ->
            val chip = Chip(this).apply {
                text = "${suggestion.label} (${suggestion.usageCount})"
                isCheckable = false
                setOnClickListener {
                    viewModel.runShortcut(suggestion.utterance)
                }
            }
            binding.chipGroupSuggestions.addView(chip)
        }
    }

    private fun renderConfirmation(pending: PendingCommandConfirmation?) {
        if (pending == null) {
            shownConfirmationId = null
            return
        }
        if (shownConfirmationId == pending.commandId) return

        shownConfirmationId = pending.commandId
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.confirm_action_title)
            .setMessage(
                getString(
                    R.string.confirm_action_message,
                    pending.intent.displayTitle(),
                    pending.intent.reason.ifBlank { pending.intent.summary.ifBlank { pending.spokenText } },
                ),
            )
            .setCancelable(false)
            .setPositiveButton(R.string.confirm) { _, _ ->
                viewModel.confirmPendingAction()
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                viewModel.rejectPendingAction()
            }
            .show()
    }

    private fun requestMicrophoneThenStart() {
        if (hasMicrophonePermission()) {
            startVoiceCaptureForCurrentMode()
        } else {
            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startVoiceCaptureForCurrentMode() {
        if (binding.switchContinuous.isChecked) {
            voiceCommandManager.setContinuousMode(true)
            voiceCommandManager.startContinuousListening()
        } else {
            voiceCommandManager.setContinuousMode(false)
            voiceCommandManager.startManualListening()
        }
    }

    private fun hasMicrophonePermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    override fun onListeningStateChanged(isListening: Boolean) {
        viewModel.onListeningStateChanged(isListening)
    }

    override fun onTranscriptReady(text: String, source: CommandSource) {
        viewModel.onCommandCaptured(text, source)
    }

    override fun onVoiceHint(message: String) {
        viewModel.onVoiceHint(message)
    }

    override fun onVoiceError(message: String) {
        viewModel.onVoiceError(message)
    }
}
