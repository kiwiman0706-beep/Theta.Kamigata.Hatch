# アーキテクチャとデータ

## プロジェクト

編集する場所は `project/kururi-hair/`。
Gradle構成ではなく、`build-apk.sh` がAndroid SDKの道具を直接使う小規模なJavaプロジェクト。
Javaの標準Android APIと、同梱HTML / CSS / JavaScriptで構成する。外部のAndroidライブラリは使っていない。

| ファイル | 主な責務 |
| --- | --- |
| `MainActivity.java` | WebView、アセット配信、JavaScriptブリッジ、カメラ／動画・SAFファイル選択の起動、結果通知 |
| `CaptureActivity.java` | Camera2静止画、TextureViewプレビュー、方向説明・枠・撮影位置、撮影確認、前の写真重ね表示 |
| `CameraGeometry.java` | プレビューの回転・拡大率とJPEG向きの計算。AndroidなしでJVMテスト可能 |
| `VideoFrameActivity.java` | VideoView再生、時間選択、MediaMetadataRetrieverによる抽出、方向別の写真保存 |
| `VideoFilesProvider.java` | 外部の動画カメラへ一時出力先を渡す、限定されたContentProvider |
| `HairStore.java` | schema 1の検証、AtomicFileでの保存、写真正規化、ZIPバックアップ／復元、HTML出力 |
| `assets/app.js` | アルバム・作成編集・撮影方向・閲覧・位置合わせ・動画入口・設定のUI |
| `assets/core.js` | 方向プラン、角度キー、欠けた写真へのフォールバック、上下左右の移動、メタ情報検証 |
| `assets/app.css` | アプリUIの見た目とレスポンシブ配置 |
| `assets/export.html` | 画像を内包する独立した閲覧HTMLのテンプレート |

Javaファイルは `app/src/main/java/jp/yaegaki/kururihair/` 以下。

## WebViewと連携

`https://kururi.local/index.html` はアプリ内部で処理する仮想オリジン。
`/photos/名前.jpg` も内部の写真へ対応させている。公開Webサイトやサーバーはない。
マニフェストにINTERNET権限はなく、通常機能にアカウントやAPIキーは不要。

主なブリッジ：`load`、`save`、`capture`、`importPhoto`、`captureVideo`、`importVideo`、
`exportBackup`、`importBackup`、`exportView`、`exit`。
ネイティブの処理後は `window.nativeResult(...)` に結果を返し、画面が最新データを読み直す。
ブラウザ単独のデモや写真読み込み経路はあるが、APK向けの動画機能など全機能がWeb単体で動くわけではない。

## 方向とプラン

| plan | 表示名 | 周囲の分割 | 高さのリング | 真上 | 合計 |
| --- | --- | --- | --- | --- | --- |
| easy | かんたん | 8方向、45°刻み | 頭と同じ高さのみ | 1枚 | 9枚 |
| standard | ぐるぐる | 8方向、45°刻み | 同じ高さ＋約35°見下ろし | 1枚 | 17枚 |
| smooth | なめらか | 12方向、30°刻み | 同じ高さ＋約35°見下ろし | 1枚 | 25枚 |

角度キーは `r0_a0`、`r0_a1` …、高いリングが `r1_a0` …、真上は `top`。
azimuth=0は正面、90は子どもの右側、180は後ろ、270は子どもの左側。
通常リングのrow=0、斜め上がrow=1。真上のrowは各プランのリング数。
写真がない場所は撮影済みの角度へフォールバックする。
ビューアの回転ボタンは写真の自動切り替えであり、写真の自動撮影ではない。

## 保存形式

アプリ専用領域の `files/hair-records/records.json` と `files/hair-records/photos/*.jpg`。
メタ情報のトップレベルは `{ "schema": 1, "sessions": [...] }`。

```json
{
  "id": "sample-record",
  "plan": "standard",
  "child": "サンプル",
  "title": "髪型の記録",
  "date": "2026-10-03",
  "note": "メモ",
  "created": 0,
  "updated": 0,
  "frames": {
    "r0_a0": {
      "file": "photo-example.jpg",
      "updated": 0,
      "align": { "x": 0, "y": 0, "scale": 1, "rotation": 0 }
    }
  }
}
```

これは構造説明用の架空の例で、対応写真がないためそのまま復元には使えない。
`x/y` は位置合わせの値（-80〜80）、`scale` は0.5〜3、`rotation` は90°刻み。
記録は最大500件。名前40文字、タイトル80文字、日付20文字、メモ4000文字。
写真はEXIFの向きを解決し、長辺1800px以下のJPEGへ正規化する。
元画像のEXIF位置情報等を保存用JPEGへ引き継ぐ処理はない。
写真名は変更のたびに新規生成し、参照の更新後に不要な画像を削除する。

## バックアップとHTML

バックアップZIPには `records.json` と `photos/*.jpg` を入れる。
読み込みはファイル名・形式・件数・画像を検証し、展開後700MBまで。
同じ記録IDはバックアップ側の内容で置換し、別のIDは維持する。
画像は新しい名前へ展開してからメタ情報を切り替える設計。

HTML出力は記録ごとのメタ情報と写真を `data:image/jpeg;base64,...` で埋め込む。
テンプレートの `__PAYLOAD__` を置換し、単体ファイルでドラッグ閲覧できる。
HTMLは閲覧・共有用、ZIPは復元・再編集用。HTMLからの再取り込みは実装されていない。
Android上でHTMLを開けるかはファイルを開くアプリにも依存する。

## 動画

録画は端末の動画カメラへ `ACTION_VIDEO_CAPTURE` で委譲。
出力先URIを一時的に許可し、60秒・200MBの制限をヒントとして渡す。
機種のカメラがヒントをどの程度守るかは実機確認対象。
既存動画はSAFで選び、アプリのキャッシュへコピーする。受け入れ上限は400MB・5分。

抽出は `OPTION_CLOSEST`。API 27以上は `getScaledFrameAtTime(...,1800,1800)`、API 26は通常取得後に縮小。
VideoViewは音量0。停止したフレームを見ながら方向を手動指定し、HairStoreで通常写真として保存。
前後のコマ移動はフレーム数／長さ、撮影レート、または既定30fpsから計算する。
可変フレームレートで厳密な一コマ単位の連番移動を保証する方式ではない。
一時動画のコピーは完了時に削除する。読み込み元の動画には書き込まない。

## カメラの向き

1.0.0はTextureViewが扱うセンサー回転にさらに回転を加えていた。
現行コードはプレビューでは表示回転の補償と均等な拡大・中央切り取りを行う。
撮影JPEGの向き指定は、プレビューとは別にセンサー・画面・前後カメラから計算する。
この挙動の端末差や実際の保存写真の向きは、計算テストとは別に実機で確認する必要がある。
