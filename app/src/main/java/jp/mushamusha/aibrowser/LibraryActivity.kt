package jp.mushamusha.aibrowser

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import jp.mushamusha.aibrowser.databinding.ActivityLibraryBinding
import java.text.DateFormat
import java.util.Date

class LibraryActivity : AppCompatActivity() {
    private lateinit var b: ActivityLibraryBinding
    private lateinit var db: BrowserDatabase
    private lateinit var table: String
    private var items: List<BrowserEntry> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityLibraryBinding.inflate(layoutInflater)
        setContentView(b.root)
        db = BrowserDatabase(this)
        table = intent.getStringExtra("table").takeIf { it == "bookmarks" } ?: "history"
        b.titleView.text = if (table == "bookmarks") "ブックマーク" else "履歴"
        title = b.titleView.text
        reload()

        b.listView.setOnItemClickListener { _, _, position, _ ->
            setResult(Activity.RESULT_OK, Intent().putExtra("url", items[position].url))
            finish()
        }
        b.listView.setOnItemLongClickListener { _, _, position, _ ->
            val item = items[position]
            AlertDialog.Builder(this)
                .setTitle(item.title)
                .setItems(arrayOf("開く", "URLを共有", "削除")) { _, which ->
                    when (which) {
                        0 -> {
                            setResult(Activity.RESULT_OK, Intent().putExtra("url", item.url)); finish()
                        }
                        1 -> shareUrlSafely(item.url)
                        2 -> {
                            db.writableDatabase.delete(table, "id=?", arrayOf(item.id.toString())); reload()
                        }
                    }
                }.show()
            true
        }
        b.clearButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("すべて削除")
                .setMessage(if (table == "bookmarks") "ブックマークをすべて削除しますか？" else "履歴をすべて削除しますか？")
                .setNegativeButton("キャンセル", null)
                .setPositiveButton("削除") { _, _ -> db.clear(table); reload() }
                .show()
        }
    }

    private fun shareUrlSafely(url: String) {
        if (!AiPrivacy.containsSensitiveUrlData(url)) {
            shareText(url)
            return
        }
        val safe = AiPrivacy.sanitizeUrl(url)
        AlertDialog.Builder(this)
            .setTitle("URLに機密情報が含まれる可能性があります")
            .setMessage("認証トークンや署名付きURLをそのまま共有すると、第三者がアクセスできる場合があります。通常はマスクしたURLを共有してください。")
            .setNegativeButton("キャンセル", null)
            .setPositiveButton("マスクして共有") { _, _ -> shareText(safe) }
            .setNeutralButton("そのまま共有") { _, _ -> shareText(url) }
            .show()
    }

    private fun shareText(text: String) {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }, "共有"))
    }

    private fun reload() {
        items = db.list(table)
        val fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        b.listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_2, android.R.id.text1, items.map {
            "${it.title}\n${it.url}\n${fmt.format(Date(it.timestamp))}"
        })
    }

    override fun onDestroy() {
        runCatching { db.close() }
        super.onDestroy()
    }
}
