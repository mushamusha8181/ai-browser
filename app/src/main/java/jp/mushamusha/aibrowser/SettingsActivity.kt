package jp.mushamusha.aibrowser

import android.os.Bundle
import android.text.InputType
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import jp.mushamusha.aibrowser.databinding.ActivitySettingsBinding
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {
    private lateinit var b: ActivitySettingsBinding
    private lateinit var prefs: BrowserPrefs
    private lateinit var secrets: SecretStore
    private val deepSeek = DeepSeekClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        b = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(b.root)
        title = "設定"

        prefs = BrowserPrefs(this)
        secrets = SecretStore(this)

        val models = listOf("deepseek-flash", "deepseek-v4-pro")
        val efforts = listOf("none", "low", "high", "max")
        val engines = listOf("Google", "DuckDuckGo", "Bing", "Brave")
        b.modelSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, models)
        b.reasoningSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, efforts)
        b.searchSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, engines)

        b.modelSpinner.setSelection(models.indexOf(prefs.model).coerceAtLeast(0))
        b.reasoningSpinner.setSelection(efforts.indexOf(prefs.reasoning).coerceAtLeast(0))
        b.searchSpinner.setSelection(engines.indexOf(prefs.searchEngine).coerceAtLeast(0))
        b.maxTokensInput.setText(prefs.maxTokens.toString())
        b.homePageInput.setText(prefs.homePage)
        b.javascriptCheck.isChecked = prefs.javaScript
        b.thirdPartyCookiesCheck.isChecked = prefs.thirdPartyCookies
        b.trackerBlockCheck.isChecked = prefs.trackerBlock
        b.httpsOnlyCheck.isChecked = prefs.httpsOnly
        b.secureAiCheck.isChecked = prefs.secureAiWindows
        b.desktopDefaultCheck.isChecked = prefs.desktopByDefault
        b.saveHistoryCheck.isChecked = prefs.saveHistory
        b.blockPopupsCheck.isChecked = prefs.blockPopups
        refreshKeyStatus()

        b.apiKeyInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD

        b.saveButton.setOnClickListener { saveAll() }
        b.deleteKeyButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("APIキーを削除")
                .setMessage("端末内に暗号化保存しているDeepSeek APIキーを削除します。")
                .setNegativeButton("キャンセル", null)
                .setPositiveButton("削除") { _, _ ->
                    secrets.clearApiKey()
                    b.apiKeyInput.text?.clear()
                    refreshKeyStatus()
                }.show()
        }
        b.testApiButton.setOnClickListener { testApi() }
    }

    private fun saveAll() {
        val typedKey = b.apiKeyInput.text?.toString()?.trim().orEmpty()
        if (typedKey.isNotEmpty()) {
            try {
                secrets.saveApiKey(typedKey)
            } catch (t: Throwable) {
                Toast.makeText(this, "APIキーを安全に保存できませんでした: ${t.message ?: "保存エラー"}", Toast.LENGTH_LONG).show()
                return
            }
        }
        prefs.model = b.modelSpinner.selectedItem.toString()
        prefs.reasoning = b.reasoningSpinner.selectedItem.toString()
        val requestedMaxTokens = b.maxTokensInput.text?.toString()?.toIntOrNull() ?: 8192
        prefs.maxTokens = requestedMaxTokens
        b.maxTokensInput.setText(prefs.maxTokens.toString())
        prefs.homePage = normalizeHomePage(b.homePageInput.text?.toString()?.trim().orEmpty())
        prefs.searchEngine = b.searchSpinner.selectedItem.toString()
        prefs.javaScript = b.javascriptCheck.isChecked
        prefs.thirdPartyCookies = b.thirdPartyCookiesCheck.isChecked
        prefs.trackerBlock = b.trackerBlockCheck.isChecked
        prefs.httpsOnly = b.httpsOnlyCheck.isChecked
        prefs.secureAiWindows = b.secureAiCheck.isChecked
        prefs.desktopByDefault = b.desktopDefaultCheck.isChecked
        prefs.saveHistory = b.saveHistoryCheck.isChecked
        prefs.blockPopups = b.blockPopupsCheck.isChecked
        b.apiKeyInput.text?.clear()
        refreshKeyStatus()
        Toast.makeText(this, "保存しました", Toast.LENGTH_SHORT).show()
    }

    private fun normalizeHomePage(raw: String): String {
        val value = raw.ifBlank { "https://www.google.com" }
        val candidate = if (value.startsWith("http://", true) || value.startsWith("https://", true)) value else "https://$value"
        val uri = runCatching { android.net.Uri.parse(candidate) }.getOrNull()
        return if (uri?.host.isNullOrBlank()) "https://www.google.com" else candidate
    }

    private fun testApi() {
        val typed = b.apiKeyInput.text?.toString()?.trim().orEmpty()
        val key = typed.takeIf { it.isNotEmpty() } ?: secrets.getApiKey()
        if (key.isNullOrBlank()) {
            Toast.makeText(this, "APIキーを入力してください", Toast.LENGTH_SHORT).show()
            return
        }
        b.testApiButton.isEnabled = false
        lifecycleScope.launch {
            val result = deepSeek.chat(
                key,
                b.modelSpinner.selectedItem.toString(),
                b.reasoningSpinner.selectedItem.toString(),
                256,
                "Reply briefly in Japanese.",
                "接続テストです。『接続OK』とだけ答えてください。"
            )
            b.testApiButton.isEnabled = true
            Toast.makeText(this@SettingsActivity, result.fold({ it.take(80) }, { it.message ?: "接続失敗" }), Toast.LENGTH_LONG).show()
        }
    }

    private fun refreshKeyStatus() {
        b.keyStatusView.text = if (secrets.hasApiKey()) "APIキー: 保存済み（内容は表示しません）" else "APIキー: 未登録"
    }
}
