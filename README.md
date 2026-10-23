# AIブラウザ v1.3.2

DeepSeekをAndroidブラウザへ統合した、個人利用向けの高機能AIブラウザです。Android System WebViewをレンダリングエンジンとして使い、通常のブラウザ機能に加えて、AI要約・DOM検査・スクレイピング・複数タブ分析・Vision解析・AI自動ブラウズ・ページ変更監視・音声操作・操作マクロを統合しています。v1.3.2ではv1.3.1を再監査し、Keystore互換性、シークレット別プロセス、タブのメモリ圧縮、DOM取得タイムアウト、シークレットダウンロード、巨大AI出力などを重点的に修正しています。

## v1.3.2 追加健康診断・安定化

v1.3.1をさらに全体監査し、機能追加よりも「止まらない・漏らさない・メモリを使いすぎない」を優先しました。

- APIキー / 設定
  - APIキー用Keystore鍵をv2へ移行し、Android 12〜14で問題になり得る`setUnlockedDeviceRequired(true)`依存を廃止
  - 旧v1鍵で復号できた場合はv2鍵へ自動再暗号化
  - 一般設定もSharedPreferencesのマルチプロセスキャッシュ依存をやめ、`noBackupFilesDir`の原子的JSONファイルへ移行
- シークレット
  - `IncognitoActivity`を`singleTask`化し、複数Activityが同じシークレットWebView領域を相互消去する競合を防止
  - シークレットダウンロードはDownloadManagerへCookieを渡さず、アプリ内OkHttpで直接Downloadsへ保存
  - リダイレクトごとにそのURLへ適用されるCookieを取得し直し、別ホストへCookieを転送しない
  - HTTPSからHTTPへのダウングレードリダイレクトをブロック
- タブ / メモリ
  - タブ上限20、同時に生かすページWebViewを原則8個へ制限
  - 古いバックグラウンドタブはURL/タイトルを残してレンダラを休止し、再選択時に復旧
  - Androidのメモリ圧迫通知時は背景WebViewをさらに2個まで縮小
- DOM / AI / マクロ
  - Snapshot、CSSスクレイピング、メディア解析、セキュリティAI、1手AI、自動ブラウズ、マクロ、要素検査にタイムアウトを追加
  - WebViewレンダラ停止時にDOMコールバックを永久待ちしない
  - AI操作の危険判定へ`name` / `placeholder` / `autocomplete` / `title` / submit系button valueを追加
  - パスワード、OTP、カード/銀行入力をJavaScript実行側でも再ブロック
- AI入出力
  - 一般AI設定の最大出力を65,536トークンへ上限化し、スマホUI/メモリの暴走を抑制
  - AI結果ダイアログ・サイドパネル履歴・複数タブスクレイピングに表示/総量上限を追加
  - AIリサーチのSearch APIは本文全取得を既定OFFにし、不要な入力肥大化を抑制
  - Visionスクリーンショットはフル解像度Bitmapを先に作らず、最大1600pxへ直接描画
- HTTPS / ナビゲーション
  - ホーム、履歴/ブックマーク、監視一覧、音声ホーム、レンダラ復旧など内部遷移を共通`loadBrowserUrl()`へ統一
- 不要処理
  - NoActionBarテーマで意味のなかったActionBar戻る処理を削除
  - 未使用のbackup/data-extraction XMLを削除

## v1.3.1 再監査・バグ修正

v1.3.0をベースに、機能追加ではなく「高機能化で生じた干渉・無駄・実機事故」を再監査しました。

- WebView / タブ
  - WebViewレンダラが落ちた場合に対象タブを再生成してURLを復旧
  - 画面回転を`configChanges`で処理し、回転だけでタブが消えにくい構成へ変更
  - 現在タブ破棄時は親Viewから外してから`destroy()`
  - ページ内検索の「検索 / 次へ」でダイアログが毎回閉じないよう修正
  - ページ監視通知や外部HTTP/HTTPS URLは`singleTop`の既存ブラウザへ新規タブとして受け取り、通知タップで既存タブ群を失いにくくした
  - 外部Intentから受け入れる初期URLをHTTP/HTTPSへ限定
  - PC表示User-Agentを固定Chrome番号ではなく端末の現在WebView UAから生成
- 権限競合
  - カメラ / マイク / 位置情報のサイト権限を「要求元タブ + 要求時ページOrigin」に固定
  - OS権限ダイアログ表示中にタブ移動・ページ遷移した場合は古い要求を拒否
  - WebサイトへはAndroid側権限があっても自動許可しない方針を維持
- AI通信 / トークン浪費
  - OkHttpをCoroutineキャンセルへ連動し、画面終了やキャンセル時にHTTPも停止
  - `CancellationException`を通常エラーとして飲み込まないよう修正
  - 内部APIの`tryResume`依存をやめ、公開されたCancellableContinuation APIへ変更
  - 自動ブラウズの「停止」で実行中DeepSeek通信もキャンセル
  - 閉じたタブのAIサイドパネル通信をキャンセル
  - タブ単位のAI処理中状態を分離し、別タブ完了で現在タブの送信ボタン状態が壊れないよう修正
  - AIサイドパネル履歴に文字数上限を追加し、小型スマホで長文履歴が際限なくメモリを消費しないよう制限
- DOM / JavaScript
  - JavaScript無効時に要素検査・スクレイピング・AIページ解析・AI自動操作等が黙って失敗せず、必要条件を表示して停止
  - 要素検査・1手AI・セキュリティAI・メディアページ解析を開始タブへ固定
  - 表 / リーダー / transcript候補 / スクレイピング結果に総量上限を追加し、巨大DOMでのメモリ膨張を抑制
- ページ監視
  - 即時監視WorkをUnique Work化し、重複起動を防止
  - 通知権限がないAndroid 13+ではネットワーク取得を行わず終了
  - WorkerのHTTP通信もWorkキャンセルへ連動
  - 旧版で保存済みの認証トークン付き監視URLをWorker側でも削除
- セキュリティ / 共有
  - AI画面が複数開いている時の`FLAG_SECURE`状態を一元管理
  - 設定から戻った時もAI画面が残っていればスクリーンショット保護を再評価
  - 履歴 / ブックマーク画面からの共有にも機密URLマスク警告を適用
  - CIで生成するDebug APKも`debuggable=false`に設定
- 重複 / 不要処理
  - 簡易トラッカーブロックはサイト破壊を避けるため新規既定値をOFFへ変更（機能は残す）
  - マクロ保存を最大50件、1記録を最大100操作に制限しSharedPreferencesの無制限肥大化を防止
  - private関数の同名重複・明白な未使用private関数を静的確認し、該当なし
- ビルド
  - GitHub ActionsをAGP 8.13系の基準に合わせJDK 17 / Gradle 8.13へ固定

## v1.3.0 コード健康診断・安定化

v1.2.1の全ソースを見直し、機能追加よりも「実際に動くこと」「安全に失敗すること」「同じ処理を重ねないこと」を優先して修正しました。

- シークレット
  - Android 9（API 28）以上へ最低対応を統一し、別WebViewデータディレクトリを確実に使用
  - シークレットプロセス起動時にもCookie / WebStorageを再消去し、強制終了後の残留を次回起動で掃除
  - シークレットではページ監視と操作マクロを永続保存しない
- DeepSeek APIキー
  - KeystoreのAES-256-GCM鍵は維持
  - 暗号化本文を`noBackupFilesDir/deepseek_api_key.enc`へ保存し、通常/シークレットの別プロセスから毎回読み直す方式へ変更
  - 旧v1.2.xの暗号化SharedPreferencesから自動移行
  - WebView / JavaScript / Cookie / ログへAPIキーを渡さない
- AIへ渡すWeb情報
  - `token` / `access_token` / `signature` / `session` / `x-amz-*` / `x-goog-*`など、URL内の機密クエリを送信前にマスク
  - 機密トークンを含む可能性のある画像URLはURLのままVisionへ送らない
- AI自動ブラウズ
  - 開始タブへ固定し、タブ切替・対象タブ終了で即停止
  - パスワード、OTP/2FA、金融情報、購入・決済・送信・削除、別ドメイン移動などは自動実行しない
  - 1手アシスタントでも危険判定が出た操作はAI経由で実行せず、手動操作へ戻す
- 操作マクロ
  - 入力内容（fill）を保存しない
  - 記録時ドメイン、実行時要素、危険操作を再検査
  - 旧形式マクロは再生しない
- 複数タブAI
  - 先頭タブだけで入力上限を使い切らないよう、各タブを均等な上限で圧縮してから統合
- AIサイドパネル
  - タブ単位の会話履歴を保持し、「さっきの回答」のような続きの質問に対応
- リーダーモード
  - `article` / `main`等から見出し・段落単位で抽出し、巨大な一段落になりにくい方式へ変更
- ページ変更監視
  - 監視対象が0件なら定期Workを停止
  - 広告・ナビ等をなるべく除外した本文寄りのテキストで比較
  - 3MB未満の通常ページでも失敗しないストリーム読み取りへ修正
  - 変更通知へ前回/現在の本文断片を表示
  - ログインページやJavaScript描画だけのページは対象外と明記
- アップロード
  - カメラ/動画撮影候補の一時URI取り違えを防止
  - キャンセル/未使用の一時ファイルを掃除
- ダウンロード
  - DownloadManagerの標準完了通知へ一本化し、二重通知を解消
- WebView設定
  - 設定画面から戻った時、JavaScript / Cookie / Popup / Mixed Content設定を全タブへ反映
  - HTTPS優先をアドレス入力だけでなくメインフレームのHTTPリンク遷移にも適用
  - 外部スキームはユーザー操作由来の場合だけ扱い、起動前に確認
- 閲覧データ削除
  - 一時WebViewだけでなく、実際に開いている全タブのキャッシュ/フォーム/SSL状態/必要時の戻る履歴を削除
- UI/重複整理
  - メインメニューに重複していたAI機能を「AIツール」へ一本化
  - 「動画/音声解析」は実態に合わせ「動画/音声ページ情報解析」へ改名
  - 旧バックアップソースと不要な独自Download receiverを削除
- 権限
  - 初回権限スキーマをv4へ更新
  - 実装で使用していない`ACCESS_WIFI_STATE` / `VIBRATE`の明示宣言を削除

## v1.2.1 スマホUI最適化

小さなスマートフォンでの利用を前提に、機能を削らず表示密度を見直しました。

- 画面幅360dp以下では上部の「進む」ボタンを自動的に隠し、URL/検索欄を広く確保
  - 「進む」はオーバーフローメニューから利用可能
- 上部ツールバーの余白を圧縮し、主要操作は44dp前後で統一
- 下部の「ホーム / AI / 保存」は48dpのバー内に収め、文字を12spへ調整
- AIサイドパネルはスマホでは全幅表示にし、固定340dpによるはみ出しを防止
  - 600dp以上の端末では360dpのサイドパネルとして表示
- AI入力欄は最大4行に抑え、Web表示領域を圧迫しにくい構成へ変更
- AI長文結果・HTML/テキスト表示は本文のみスクロールし、操作ボタンが画面外へ逃げにくい構成へ変更
- 設定画面・履歴/ブックマーク画面の余白とボタン高さを圧縮
- 機能、AIキー保護、権限設計はv1.2.0から維持

## v1.2.0で追加した主な機能

- 常駐AIサイドパネル
  - 現在ページを見ながらAIへ質問
  - AIへ渡す情報を「全文 / 本文 / 選択範囲 / 送信なし」で即切替
  - 音声入力対応
- 複数タブAI
  - 最大12タブの内容をまとめて比較
  - 複数タブを資料にブログ下書きを生成
  - AIでタブを用途別に自動分類し、タブ一覧へカテゴリ表示
- AIリサーチモード
  - DeepSeek Search APIから最大20件を収集
  - 共通点、食い違い、重要数値、出典URLをまとめる
- 複数タブ一括スクレイピング
  - 同じCSSセレクタを複数タブへ適用
  - JSONコピー / 共有 / AIによる表・CSV向け整理
- AI自動ブラウズ
  - click / fill / scroll / navigate を最大8ステップで反復
  - Webページ内の文章をAIへの命令として扱わないプロンプトインジェクション対策
  - パスワード、OTP/2FA、決済、購入確定、送信、削除、金融情報を自動実行しない
  - 別ドメインへの自動移動は停止してユーザー確認
- AI操作マクロ
  - AIが実行した安全なWeb操作を記録
  - 名前を付けて保存・再実行
  - 再実行時も危険操作チェックを再適用
- Vision / 画像解析
  - 現在のWebView表示範囲をスクリーンショット化してDeepSeek Visionへ送信
  - Web画像を長押しして画像URLをDeepSeekへ送り解析
  - 表、グラフ、UI、画像内文字などを解析
- 動画 / 音声ページ解析
  - video / audio要素、字幕track、ページ内transcript候補、本文を抽出
  - 実際のメディア内容が取得できない場合は推測せず制約を明示するようAIへ指示
- セキュリティAI
  - HTTPS、URL、Punycode、ログインフォーム、パスワード欄、ダウンロードリンクなどを補助評価
  - AI判定だけで「安全」と断定しない設計
- ページ変更監視
  - 公開HTTP/HTTPSページをWorkManagerで定期確認
  - 変更を検知すると通知
  - 初回登録時にベースラインを作成
  - ログイン必須ページのCookieセッションはバックグラウンド監視に持ち出さない
- 音声操作
  - 戻る / 進む / 更新 / ホーム / 新規タブ / タブを閉じる / 要約 / タブ比較 / ページ監視 / AI質問など
- Galaxy等の省電力対策
  - 「バックグラウンド動作設定」からAndroidのバッテリー最適化設定を開ける

## ブラウザ基本機能

- 複数タブ / タブ切替 / ポップアップを新規タブ化
- シークレットモード（Android 9以上、別プロセス + 別WebViewデータディレクトリ、起動時/終了時の再消去）
- 戻る / 進む / ホーム / 再読み込み
- アドレスバー兼検索
- Google / DuckDuckGo / Bing / Brave切替
- 履歴 / ブックマーク
- ダウンロード / Androidダウンロード一覧 / 完了通知
- 共有 / 印刷
- ページ内検索
- PC表示 / モバイル表示
- ピンチズーム / ズーム +/-
- ファイルアップロード
- Webカメラ / マイク / 位置情報
- 全画面動画
- 外部スキーム連携（ユーザー操作時のみ、起動前確認）
- リーダーモード
- HTMLソース表示
- 要素検査
- CSSセレクタ抽出
- 簡易トラッカー/広告ドメインブロック
- HTTPS優先モード
- Cookie / WebStorage / キャッシュ削除
- SSLエラー時は接続中断
- Safe Browsing有効
- WebViewからローカルファイルへの直接アクセスを禁止

## ダウンロード

通常のHTTP / HTTPSダウンロードに対応しています。

- 通常モードはAndroid DownloadManagerを使用
- シークレットはアプリ内OkHttpで直接保存し、WebView CookieをDownloadManagerへ渡さない
- Downloadsフォルダへ保存
- User-Agentを引き継ぎ
- 通常モードはWebViewのCookieを引き継ぐため、ログイン中サイトの一般的なダウンロードにも対応
- MIME type / Content-Dispositionからファイル名を推定
- モバイル回線でのダウンロードを許可
- ローミング時は既定で禁止
- ダウンロード完了通知

`blob:` URLのようにページ内部で生成される特殊な巨大ファイルは、DownloadManagerへ直接渡せない場合があります。

## アップロード

WebViewの `<input type="file">` に対応しています。

- 単一ファイル
- 複数ファイル
- 画像
- 動画
- 音声
- PDF / ZIP / Office文書など任意ファイル
- サイト側の `accept` 指定を反映
- カメラ撮影から直接画像添付
- 動画撮影から直接動画添付
- 音声指定時は外部録音アプリを候補表示

ファイル選択はAndroid Storage Access Frameworkを使用します。Samsung「マイファイル」、Google Files、クラウドストレージなど、Androidのファイル選択画面へ公開されたファイルを利用できます。Android 10以降では広いストレージ権限を要求しません。

## 初回起動時の権限

権限スキーマをv4へ更新しています。初回起動時に説明画面を必ず表示し、その端末/Androidバージョンで必要なランタイム権限をまとめて要求します。

### ランタイム権限

- CAMERA
  - Webカメラ / WebRTC
  - 撮影アップロード
- RECORD_AUDIO
  - WebRTC
  - 音声操作
- ACCESS_COARSE_LOCATION / ACCESS_FINE_LOCATION
  - Web Geolocation
- POST_NOTIFICATIONS（Android 13以降）
  - ページ変更監視など、アプリ自身が出す通知
- WRITE_EXTERNAL_STORAGE（Android 9（API 28）では）
  - Downloads保存

### Manifestへ宣言する通常権限

これらはAndroid仕様上、ランタイム権限ダイアログが出ません。

- INTERNET
- ACCESS_NETWORK_STATE
- MODIFY_AUDIO_SETTINGS
- WAKE_LOCK
- RECEIVE_BOOT_COMPLETED

`RECEIVE_BOOT_COMPLETED` はWorkManagerの定期監視を再スケジュールできるようにする土台です。

連絡先、SMS、通話履歴、全ファイルアクセス、Bluetooth、付近のデバイスなど、現在実装している機能と結び付かない強い権限は追加していません。権限を多く取るだけではWebView互換性は上がらず、攻撃面だけが増えるためです。

## Webサイト側の権限は別管理

Androidアプリ自体へカメラ・マイク・位置情報を許可していても、任意のWebサイトへ自動許可しません。

サイトが要求した場合は要求元ドメインを表示し、「今回だけ許可 / 拒否」を確認します。

## DeepSeek APIキーの保護

1. Android KeystoreにAES-256-GCM鍵を生成
2. APIキー本文は暗号化して`noBackupFilesDir`内の専用ファイルへ保存（通常/シークレットの別プロセスから都度読み直し）
3. 保存済みAPIキーはUIへ再表示しない
4. `allowBackup=false`
5. APIキー設定画面は `FLAG_SECURE`
6. APIキー入力欄はAutoFill / 状態保存 / IME学習を抑止
7. AI通信はWebViewとは別のOkHttpクライアント
8. AIクライアントは `https://api.deepseek.com` 以外を拒否
9. リダイレクトを追わない
10. Authorizationや本文をログ出力しない
11. APIキーをWebView / JavaScript / Cookie / 履歴 / クリップボードへ渡さない
12. `addJavascriptInterface` を使用しない

APIを利用する瞬間だけ、Bearer認証としてAPIキーがTLS経由でDeepSeek APIへ送信されます。WebページへAPIキーは渡りません。

## AIへの情報送信

AIサイドパネルでは送信範囲を即時切替できます。

- 全文: タイトル、URL、本文、見出し、リンク、表
- 本文: タイトル、URL、本文
- 選択: ユーザーが選択した文章のみ
- 送信なし: Webページ情報を送らない

Vision解析を選んだ場合は現在画面の画像、またはユーザーが選んだWeb画像URLがDeepSeekへ送られます。実行前に確認を表示します。

Cookie、APIキー、パスワード、フォームの現在値は通常のページスナップショットに含めません。

## プロンプトインジェクション対策

Webページ本文、ボタン文字、DOM属性は「信頼できないデータ」としてAIへ渡します。AIへのシステム命令として扱いません。

AI自動ブラウズでは、ページ本文そのものではなく操作可能な要素の限定メタデータを中心に渡し、以下を自動実行しません。

- パスワード
- OTP / 2FA
- クレジットカード / 銀行情報
- 購入確定
- 決済 / 送金
- アカウント削除 / 退会
- 投稿 / メッセージ送信など高影響操作
- 別ドメインへの無確認移動

## ページ変更監視について

WorkManagerを使い、ネットワーク接続時に登録URLを定期確認します。既定は30分間隔です。Androidの省電力やDozeによって実行時刻は遅れることがあります。

ログインCookieをバックグラウンドHTTPクライアントへコピーしないため、監視対象は公開ページ向けです。JavaScriptで後から生成されるSPA表示内容は監視できません。

## 既知の制限

- Chrome/ChromiumのGoogle同期、Chrome拡張、Password Manager同期、Cast等は含みません。
- `blob:`等、ページ内だけで生成される一部ダウンロードはDownloadManagerでは保存できません。
- ページ変更監視は公開ページの通常HTTP GETベースで、ログイン状態やJavaScript描画後のDOMは監視しません。
- 「動画/音声ページ情報解析」はDOM上のmedia要素・track・transcript候補・本文を解析する機能で、任意の動画音声を直接文字起こしする機能ではありません。
- Visionの「画面解析」は現在表示中のWebView範囲です。
- Android System WebViewを使用するため、実際のサイト互換性は端末に入っているWebView実装/バージョンにも依存します。
- 通常/シークレットの一般設定は原子的JSONファイルを都度読み直す方式へ変更していますが、すでに読み込み済みの画面表示は`onResume`等の反映タイミングまで変わらない場合があります。
- タブは最大20個で、古い背景タブはメモリ保護のためWebViewを休止します。休止タブのフォーム入力・スクロール位置・再生中メディアなどページ内の一時状態は完全には保持しません。
- MainActivityは約3,000行と依然大きく、今後さらに機能を追加する場合はBrowser/AI/Permission/Download controllerへの段階分割が推奨です。v1.3.2では大規模分割による回帰を避け、実害バグ除去を優先しています。

## ビルド

推奨:

- Android Studio
- Android 9（API 28）以上
- Android SDK 36
- JDK 17
- Android Gradle Plugin 8.13.2
- Gradle 8.13

Android Studioでプロジェクトを開き、Gradle Sync後に `app` をビルドしてください。

### GitHub Actions

`.github/workflows/android.yml` を同梱しています。GitHubへpushするとDebug APKをビルドし、Actions Artifactsへ `AIブラウザ-debug-apk` として出力します。APIキーをGitHub Secretsへ登録する必要はありません。

## Chromeとの差

これはChrome/Chromium自体をフォークしたものではなくAndroid System WebViewベースです。そのため、Chrome独自のGoogleアカウント同期、Chrome Password Manager同期、Chrome Web Store拡張、Cast、Chrome DevToolsそのもの等は含みません。
