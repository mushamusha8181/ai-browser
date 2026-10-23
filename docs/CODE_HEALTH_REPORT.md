# AIブラウザ v1.3.2 コード健康診断

## 監査対象

- Kotlin全14ファイル
- AndroidManifest / layout / XML resources 全9ファイル
- WebViewライフサイクル、タブ、休止/復旧、回転、レンダラクラッシュ
- カメラ / マイク / 位置情報 / 初回権限
- アップロード / 通常・シークレットダウンロード / 印刷 / 共有
- DeepSeek Chat / Vision / Search / キャンセル / 会話履歴
- APIキーKeystore / 一般設定 / シークレット別プロセス
- 要素検査 / CSSスクレイピング / AI自動ブラウズ / マクロ
- ページ変更監視 / WorkManager / 通知
- 履歴 / ブックマーク / 閲覧データ削除
- 不要リソース / 明白な死んだprivate関数 / 危険API
- GitHub Actionsビルド設定

## v1.3.2で修正した重要項目

1. Keystoreの`setUnlockedDeviceRequired(true)`依存を廃止しv2鍵へ移行
2. 旧v1 APIキー暗号文を復号できる場合はv2鍵へ自動再暗号化
3. 通常/シークレット設定をSharedPreferencesキャッシュ依存から原子的JSONファイル方式へ変更
4. IncognitoActivityをsingleTask化し、複数シークレットActivityによる相互データ消去を防止
5. 内部URL遷移を共通HTTPS優先経路へ統一
6. シークレットダウンロードをDownloadManagerからアプリ内OkHttpへ分離
7. シークレットDL CookieをリダイレクトURLごとに取り直し、別ホストへ転送しない
8. HTTPS→HTTPのシークレットDLリダイレクトをブロック
9. ダウンロード中止を通常エラー表示しないようCancellationExceptionを再送出
10. タブ上限20・生存ページWebView原則8個を設定
11. 古い背景タブを休止し、選択時に再ロードするメモリ保護を追加
12. Androidメモリ圧迫時は背景WebViewをより積極的に休止
13. 複数タブAIで休止タブを一時復元し、取得後に再休止
14. DOMスナップショットへタイムアウト追加
15. CSSスクレイピング、メディア解析、セキュリティAIへタイムアウト追加
16. 1手AI、自動ブラウズ、マクロの要素取得へタイムアウト追加
17. 要素検査ポーリングへタイムアウト追加
18. AI操作の危険判定にname/placeholder/autocomplete/title/submit系button valueを追加
19. fill実行JavaScript側でもパスワード/OTP/カード/銀行入力を再ブロック
20. 巨大スクレイピング結果にタブ横断総量上限を追加
21. AI結果ダイアログとサイドパネル履歴の表示量を制限
22. DeepSeek最大出力設定を65,536トークンへ制限
23. Search APIの本文全取得を既定OFFにし、AIリサーチ入力の肥大化を抑制
24. Vision画面取得を最大1600pxのBitmapへ直接描画し、一時メモリ二重化を削減
25. NoActionBar環境で意味のなかったActionBar戻る処理を削除
26. 未使用backup/data-extraction XMLを削除

## 重複・不要コード監査

- 明白な「定義だけで参照されないprivate関数」候補: 0
- `addJavascriptInterface`: 使用なし
- TODO / FIXME / HACK: 検出なし
- 独自Download完了Receiver: なし
- 未使用だったbackup/data-extraction XML: 削除済み
- NoActionBarに対する不要な`supportActionBar`操作: 削除済み
- APIキー/Authorizationを出力するログ処理: なし

## 静的チェック結果

- Android XML / Manifest: 9ファイル、XML構文エラー0
- `PageScripts.kt`内の三重引用JavaScriptブロック: 13ブロック、Node.js `--check` 構文エラー0
- ViewBinding参照: XMLに存在しない実View IDなし（Bindingの`root`等自動生成参照を除く）
- Kotlin全14ファイルをASCIIパスへコピーしKotlinパーサ段階を確認し、今回追加分のunclosed/expecting/redeclaration等の構文エラー候補なし
  - Android SDK / AndroidX classpathがこの環境に無いため完全なAndroidコンパイルではない

## 権限監査

初回ランタイム要求:
- CAMERA
- RECORD_AUDIO
- ACCESS_COARSE_LOCATION / ACCESS_FINE_LOCATION
- POST_NOTIFICATIONS（Android 13+）
- WRITE_EXTERNAL_STORAGE（Android 9 / API 28のみ）

通常権限:
- INTERNET
- ACCESS_NETWORK_STATE
- MODIFY_AUDIO_SETTINGS
- WAKE_LOCK
- RECEIVE_BOOT_COMPLETED

連絡先、SMS、通話履歴、全ファイルアクセス、Bluetooth/付近デバイス等、現行機能に不要な強権限は入れていない。

## 実機で必ず確認すべき項目

この実行環境にはAndroid SDK / emulator / Galaxy実機が無いため、以下は静的監査だけでは保証できない。

- `assembleDebug` / APKインストール
- Galaxy実機のWebViewレンダラ復旧と背景タブ休止/復旧
- WebRTCカメラ/マイク
- Web Geolocation
- Android 9/12/13/16系の初回複数権限ダイアログ
- 撮影からの`<input type=file>`アップロード
- Samsung「マイファイル」/Google Files/クラウド選択
- 通常DownloadManagerとシークレット直接ダウンロード
- 印刷 / 全画面動画
- WorkManager + Samsung省電力/Doze
- 長時間DeepSeek通信/ダウンロードのキャンセル

## 既知の制限 / 残る設計課題

- Chromium本体ではなくAndroid System WebViewベース
- Chrome同期 / Chrome拡張 / Google Password Manager同期 / Cast / DevTools本体は含まない
- `blob:`等の一部ダウンロードは未対応
- ページ監視は公開HTTP GETで、ログインCookieやJSレンダリング後DOMは監視しない
- 動画/音声ページ情報解析はDOM/字幕候補の解析で、任意メディアの音声認識ではない
- 背景タブ休止後はURL/タイトルは復元するが、フォーム入力・スクロール位置・再生位置等の一時ページ状態は完全保存しない
- MainActivityは約3,000行と大きい。次の構造改善はBrowserController / AiController / PermissionController / DownloadController等へ段階分割するのが妥当

## 結論

v1.3.2では、Keystore互換性、別プロセス設定同期、シークレットダウンロード、タブメモリ、DOM待ちっぱなし、巨大AI/スクレイピング入力など、実機で事故につながりやすい境界を重点的に修正した。

静的検査上は明白な構文崩れ・大量の死にコード・危険なWebView JavaScript bridge・APIキーのログ出力は確認されていない。ただしAndroid SDKを用いた実ビルドとGalaxy実機テストを通すまでは「バグゼロ」とは断言しない。
