package io.legado.app.ui.book.read

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.databinding.DialogAiTextConfigBinding
import io.legado.app.help.ai.AiAndroidAccess
import io.legado.app.help.ai.AiApiKey
import io.legado.app.help.ai.AiEndpointPolicy
import io.legado.app.help.ai.AiKeyState
import io.legado.app.help.ai.AiProviderError
import io.legado.app.help.ai.AiProviderException
import io.legado.app.help.ai.AiReaderAccess
import io.legado.app.help.ai.AiTextConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.model.ReadBook
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.visible
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import io.legado.app.utils.setLayout
import splitties.init.appCtx

/** AI 正文净化（实验）全局配置：服务地址、模型、安全密钥、超时、单块上限、仅非计费网络、
 * 固定文本测试连接与清理 AI 缓存。
 * 安全项：密钥控件 saveEnabled=false (旋转不存状态) + importantForAutofill=noExcludeDescendants，
 * 关闭总开关清空密钥；不改动其他文件的密钥/授权/缓存逻辑。 */
class AiTextConfigDialog : BaseDialogFragment(R.layout.dialog_ai_text_config) {

    val binding by viewBinding(DialogAiTextConfigBinding::bind)

    override fun onStart() {
        super.onStart()
        setLayout(1f, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        loadConfig()
        upStats()
        viewLifecycleOwner.lifecycleScope.launch {
            AiReaderAccess.coordinator.stats.collect { upStats() }
        }
        binding.swAiEnabled.setOnCheckedChangeListener { _, isChecked ->
            if (!isChecked) {
                binding.etApiKey.text?.clear()
                AiReaderAccess.configs.disable()
                AiReaderAccess.settingsChanged()
            }
        }
        binding.btnTestConnection.setOnClickListener { testConnection() }
        binding.btnClearCache.setOnClickListener { clearCache() }
        binding.btnSave.setOnClickListener { save() }
    }

    private fun loadConfig() {
        val config = AiReaderAccess.configs.current().config
        binding.swAiEnabled.isChecked = config.enabled
        binding.etServiceUrl.setText(config.serviceUrl)
        binding.etModel.setText(config.model)
        binding.etTimeout.setText(config.timeoutSeconds.toString())
        binding.etMaxChunk.setText(config.maxChunkChars.toString())
        binding.swOnlyMetered.isChecked = AiReaderAccess.onlyNonMetered()
        when (val keyState = AiReaderAccess.keyStore.load()) {
            is AiKeyState.Available ->
                binding.etApiKey.hint = getString(R.string.ai_api_key) + "（已保存，留空保持不变）"
            is AiKeyState.Unsupported -> {
                appCtx.toastOnUi(getString(R.string.ai_key_unsupported))
                binding.swAiEnabled.isChecked = false
                binding.etApiKey.isEnabled = false
            }
            is AiKeyState.NeedsReentry -> appCtx.toastOnUi(getString(R.string.ai_key_needs_reentry))
            AiKeyState.Missing -> Unit
        }
    }

    private fun upStats() {
        val tv = binding.tvStats
        val stats = AiReaderAccess.coordinator.stats.value
        tv.text = getString(R.string.ai_stats, stats.requestCount, stats.sentChars)
        tv.visible()
    }

    // ---- 校验 ----

    private fun validateBasic(serviceUrl: String, model: String, timeout: Int?, maxChunk: Int?): Boolean {
        if (serviceUrl.isBlank()) {
            appCtx.toastOnUi(R.string.ai_url_invalid)
            return false
        }
        if (model.isBlank()) {
            appCtx.toastOnUi(R.string.ai_model_required)
            return false
        }
        if (timeout == null || timeout !in TIMEOUT_RANGE) {
            appCtx.toastOnUi(R.string.ai_timeout_range)
            return false
        }
        if (maxChunk == null || maxChunk !in MAX_CHUNK_RANGE) {
            appCtx.toastOnUi(R.string.ai_max_chunk_range)
            return false
        }
        return true
    }

    /** 用当前表单输入构造 config 并校验 URL（AiEndpointPolicy.chatCompletionsUrl），失败返回 null。 */
    private fun buildAndValidate(
        serviceUrl: String, model: String, timeout: Int, maxChunk: Int,
        allowInsecureLocalHttp: Boolean, enabled: Boolean
    ): AiTextConfig? {
        val previous = AiReaderAccess.configs.current().config
        val config = AiTextConfig(
            enabled = enabled,
            providerType = previous.providerType,
            serviceUrl = serviceUrl,
            model = model,
            timeoutSeconds = timeout,
            maxChunkChars = maxChunk,
            allowInsecureLocalHttp = allowInsecureLocalHttp
        )
        return try {
            AiEndpointPolicy.chatCompletionsUrl(config.toProviderConfig())
            config
        } catch (_: Exception) {
            appCtx.toastOnUi(R.string.ai_url_invalid)
            null
        }
    }

    // ---- 保存 ----

    private fun save() {
        val serviceUrl = binding.etServiceUrl.text?.toString()?.trim() ?: ""
        val model = binding.etModel.text?.toString()?.trim() ?: ""
        val timeout = binding.etTimeout.text?.toString()?.toIntOrNull()
        val maxChunk = binding.etMaxChunk.text?.toString()?.toIntOrNull()
        if (!validateBasic(serviceUrl, model, timeout, maxChunk)) return
        val isHttp = serviceUrl.startsWith("http://", ignoreCase = true)

        fun persist(allowInsecureLocalHttp: Boolean) {
            val config = buildAndValidate(serviceUrl, model, timeout!!, maxChunk!!, allowInsecureLocalHttp, binding.swAiEnabled.isChecked)
                ?: return
            persistConfig(config)
        }

        if (isHttp) {
            requireContext().alert(R.string.ai_consent_title) {
                setMessage(getString(R.string.ai_http_confirm))
                yesButton { persist(true) }
                noButton { }
                onCancelled { }
            }
        } else {
            persist(false)
        }
    }

    private fun persistConfig(config: AiTextConfig) {
        AiReaderAccess.settingsChanged()
        val oldUrl = AiReaderAccess.configs.current().config.serviceUrl
        val keyText = binding.etApiKey.text?.toString()?.trim()
        var keyState = AiReaderAccess.keyStore.load()
        if (!keyText.isNullOrEmpty()) {
            keyState = AiReaderAccess.keyStore.save(AiApiKey.from(keyText))
        }
        val enabledRequested = config.enabled

        if (keyState is AiKeyState.Unsupported) {
            appCtx.toastOnUi(R.string.ai_key_unsupported)
            val final = config.copy(enabled = false)
            AiReaderAccess.configs.update(final)
            AiReaderAccess.setOnlyNonMetered(binding.swOnlyMetered.isChecked)
            AiReaderAccess.settingsChanged()
            if (final.serviceUrl.trim() != oldUrl.trim()) AiReaderAccess.deviceConsent.clear()
            binding.etApiKey.text?.clear()
            upStats()
            dismiss()
            return
        }
        if (enabledRequested && keyState !is AiKeyState.Available) {
            appCtx.toastOnUi(R.string.ai_key_invalid)
            return
        }
        AiReaderAccess.configs.update(config)
        AiReaderAccess.setOnlyNonMetered(binding.swOnlyMetered.isChecked)
        AiReaderAccess.settingsChanged()
        if (config.serviceUrl.trim() != oldUrl.trim()) AiReaderAccess.deviceConsent.clear()
        binding.etApiKey.text?.clear()
        upStats()
        dismiss()
    }

    // ---- 测试连接 ----

    private fun testConnection() {
        fun executeTest(allowHttp: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            val serviceUrl = binding.etServiceUrl.text?.toString()?.trim() ?: ""
            val model = binding.etModel.text?.toString()?.trim() ?: ""
            val timeout = binding.etTimeout.text?.toString()?.toIntOrNull()
            val maxChunk = binding.etMaxChunk.text?.toString()?.toIntOrNull()
            if (!validateBasic(serviceUrl, model, timeout, maxChunk)) return@launch
            val config = buildAndValidate(serviceUrl, model, timeout!!, maxChunk!!, allowHttp, false)
                ?: return@launch
            val keyState = AiReaderAccess.keyStore.load()
            if (keyState is AiKeyState.Unsupported) {
                appCtx.toastOnUi(R.string.ai_key_unsupported)
                return@launch
            }
            val enteredKey = binding.etApiKey.text?.toString()?.trim().orEmpty()
            val apiKey = if (enteredKey.isNotEmpty()) AiApiKey.from(enteredKey)
                else (keyState as? AiKeyState.Available)?.key
            if (apiKey == null) {
                appCtx.toastOnUi(R.string.ai_key_invalid)
                return@launch
            }
            binding.btnTestConnection.isEnabled = false
            val result = try {
                runCatching { withContext(IO) { AiReaderAccess.testConnection(config, apiKey) } }
            } finally {
                if (view != null) binding.btnTestConnection.isEnabled = true
            }
            result.onSuccess {
                appCtx.toastOnUi(getString(R.string.ai_test_ok, it.model))
            }.onFailure { e ->
                if (e is CancellationException) throw e
                appCtx.toastOnUi(getString(R.string.ai_test_fail, categoryText(e)))
            }
        }
        }
        if (binding.etServiceUrl.text.toString().trim().startsWith("http://", true)) {
            requireContext().alert(R.string.ai_consent_title) {
                setMessage(getString(R.string.ai_http_confirm))
                yesButton { executeTest(true) }
                noButton { }
            }
        } else executeTest(false)
    }

    override fun onDestroyView() {
        binding.etApiKey.text?.clear()
        super.onDestroyView()
    }

    /** 只显示固定分类，绝不暴露 Throwable.message/正文/密钥。 */
    private fun categoryText(e: Throwable): String {
        val error = (e as? AiProviderException)?.error
        return error?.let(::category) ?: getString(R.string.ai_test_category_unknown)
    }

    private fun category(error: AiProviderError): String = when (error) {
        AiProviderError.unsafe_endpoint -> getString(R.string.ai_test_category_unsafe_endpoint)
        AiProviderError.unsafe_redirect -> getString(R.string.ai_test_category_unsafe_redirect)
        AiProviderError.authentication -> getString(R.string.ai_test_category_authentication)
        AiProviderError.rate_limited -> getString(R.string.ai_test_category_rate_limited)
        AiProviderError.server -> getString(R.string.ai_test_category_server)
        AiProviderError.http -> getString(R.string.ai_test_category_http)
        AiProviderError.timeout -> getString(R.string.ai_test_category_timeout)
        AiProviderError.network -> getString(R.string.ai_test_category_network)
        AiProviderError.response_too_large -> getString(R.string.ai_test_category_response_too_large)
        AiProviderError.protocol -> getString(R.string.ai_test_category_protocol)
    }

    // ---- 清理缓存 ----

    private fun clearCache() {
        lifecycleScope.launch {
            withContext(IO) { AiAndroidAccess.clearAll() }
            appCtx.toastOnUi(R.string.ai_cache_cleared)
            // 当前章回原文、从章首、阻止自动重处理
            ReadBook.resetAiDisplay()
            upStats()
        }
    }

    companion object {
        // 与 OpenAiCompatibleProvider.validateConfig 与功能书 §3.1 对照：timeout 5..120；maxChunk 256..6000 为安全上限。
        private val TIMEOUT_RANGE = 5..120
        private val MAX_CHUNK_RANGE = 256..6000
    }
}
