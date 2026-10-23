# Security notes - AIブラウザ v1.3.2

## DeepSeek API key

- `addJavascriptInterface` は使用しません。
- Android KeystoreにAES-256-GCM鍵を生成します。v1.3.2ではAndroid 12〜14で問題になり得る`setUnlockedDeviceRequired(true)`依存を廃止し、旧v1鍵からv2鍵へ自動移行します。
- 暗号化したAPIキー本文は `noBackupFilesDir/deepseek_api_key.enc` に保存します。
- v1.2.x以前の暗号化SharedPreferencesは初回読込時に移行します。
- 通常プロセスとシークレットプロセスは、SharedPreferencesのマルチプロセスキャッシュに依存せず、暗号化ファイルを都度読みます。
- 保存済みキーはUIへ再表示しません。
- `allowBackup=false`、かつキー本文はno-backup領域です。
- 設定画面は `FLAG_SECURE` を使用します。
- DeepSeek専用OkHttpクライアントはHTTPSかつ `api.deepseek.com` のみ許可し、リダイレクトを追いません。
- Authorizationヘッダー/本文のHTTPログを出しません。
- APIキーをWebView、JavaScript、Cookie、閲覧履歴、クリップボード、スクレイピング出力へ渡しません。

API利用時のみ、Bearer認証としてTLS経由でDeepSeekへキーを送ります。

## AIへ送るWeb情報

- ページ内容はユーザー操作時だけ `evaluateJavascript` で読みます。
- URL/JSON等に含まれる `token`、`access_token`、`signature`、`session`、`credential`、`x-amz-*`、`x-goog-*` 等を送信前にマスクします。
- 認証情報らしいクエリを含む画像URLは外部Vision URLとして送信しません。
- Cookie、Webフォームのパスワード値、APIキーは通常スナップショットへ含めません。
- 画面Visionは画面内の情報を画像として送るため、毎回確認を表示します。

## WebView / site permissions

- Androidアプリ権限とWebサイト権限を分離します。
- アプリへCAMERA / RECORD_AUDIO / locationを許可しても、Webサイトへ自動許可しません。
- Webサイトのカメラ/マイク/位置情報要求はoriginを表示し、その場で許可/拒否します。
- ファイルアップロードはStorage Access Frameworkを使用します。
- 撮影アップロードはcache-onlyのFileProvider URIです。
- `allowFileAccess=false`。
- SSL証明書エラーは続行せず中断します。
- Safe Browsingを有効化します。
- HTTP/HTTPS以外の外部スキームは、ユーザー操作由来の場合だけ確認後に外部アプリへ渡します。

## Incognito

- 最低Android 9（API 28）とし、シークレットは別プロセス/別WebViewデータディレクトリを使用します。
- シークレットプロセス開始時とActivity終了時にCookie/WebStorage等を再消去します。
- 強制終了で終了処理が走らなくても、次回シークレット起動時に再消去します。
- シークレットではページ監視と操作マクロを永続保存しません。
- シークレットActivityは`singleTask`とし、複数Activityが同じWebViewデータ領域を相互消去しないようにします。
- シークレットダウンロードはDownloadManagerへCookieを渡さず、アプリ内OkHttpで保存します。Cookieは同一ホストの各リダイレクトURLに対してCookieManagerから取り直し、別ホストへ転送しません。HTTPSからHTTPへのダウングレードも拒否します。

## Prompt injection / browser agent

Webページの本文・ボタン文字・属性は「信頼できないデータ」であり、AIへの命令ではありません。

自動操作では:

- 実行開始タブへ固定し、タブ切替/対象タブ終了で停止
- click / fill / scroll / navigate の限定操作
- パスワード、OTP/2FA、カード/銀行情報をブロック
- 購入、決済、送金、送信、投稿、削除、退会等の高影響操作をブロック
- 別ドメインへの自動移動をブロック
- 1手アシスタントでも安全判定に引っかかった操作はAI経由では実行しない

マクロはfill内容を保存せず、記録時ドメインと実行時DOMを再確認します。旧形式マクロは再生しません。

## Page monitoring

バックグラウンド監視はWebView Cookieを持ち出さない独立HTTPクライアントです。公開ページ向けで、ログイン状態やJavaScript描画後のDOMは監視しません。認証トークン等を含む可能性があるURLは監視保存を拒否します。

## Absolute secrecy is out of scope

- root権限を奪われた端末
- OS/kernelの侵害
- 十分な権限を持つFrida/Xposed等の実行時フック
- 悪意あるAccessibility service / keyboard / device-owner software
- 解除済み端末が物理的に侵害された場合
