# ビルド、署名、テスト

## 必要な環境

- JDK 17（JREのみではなく、コンパイラーを含むもの）。
- Android SDK：`platforms;android-35` と `build-tools;35.0.0`。
- Bash、zip、基本的なUnixコマンド。
- コア／UIテスト用のNode.js。UIは追加でPlaywrightとChromium実行環境。

SDK・JDK・npmパッケージはこのZIPに同梱していない。
WindowsではWSLなどBashと対応するSDKツールを動かせる環境を使う想定。
Android Studio用Gradle構成・Gradle Wrapperは現時点では用意していない。

## 元の1.1.0をビルドする

```bash
cd project/kururi-hair
ANDROID_SDK_ROOT=/path/to/android-sdk bash build-apk.sh
```

出力は `dist/kururi-hair-1.1.0.apk`。
既存の `build/` は生成物。古いクラスを残さないため、クリーンビルドではこの生成物フォルダーを空にしてから実行する。

ビルドはaapt2 → JDKのjavacモジュール → jar → D8 → zipalign → apksignerの順。
スクリプトには `-source 8 -target 8`、min SDK 26、target SDK 35を指定している。

以前の環境ではbuild-tools内のd8.jarが壊れていたため、次の代替指定でビルドした。
これは環境の回避策であり、アプリの通常依存条件ではない。

```bash
ANDROID_SDK_ROOT=/path/to/android-sdk \
KURURI_D8_JAR=/path/to/android-sdk/cmdline-tools/latest/lib/r8.jar \
bash build-apk.sh
```

代替先には実在して正常に動くR8 / D8 JARが必要。存在しないパスをそのまま使わない。

## 次の更新APKを作る前に

1. `signing/kururi-personal.jks` があることを確認する。
2. `private/SIGNING_INFO-ja.txt` の情報で証明書を照合する。
3. パッケージ名を維持し、`build-apk.sh` のversionName / versionCodeを更新する。versionCodeは10100より大きくする。
4. `assets/app.js` の設定画面の表示バージョンとREADMEも一致させる。
5. 対象機能のテストを行い、SDKのapksignerで検証する。
6. 端末ではアンインストールせず上書き更新してデータ保持を確認する。

同梱スクリプトは鍵が存在しない場合に新しい鍵を生成する。
更新時にそれを使うと同じパッケージの既存アプリを上書きできなくなるため、既存鍵を失くした状態で更新ビルドを進めない。

```bash
keytool -list -v -keystore signing/kururi-personal.jks -alias kururi
/path/to/android-sdk/build-tools/35.0.0/apksigner verify --verbose --print-certs dist/kururi-hair-1.1.0.apk
```

キーのパスとパスワードは `KURURI_SIGNING_KEY` / `KURURI_SIGNING_PASSWORD` で上書き可能。
証明書SHA-256は `081a9477c8c8a1997d6d3a376566fdd2102a984f89550095fe9eda760fce0d20`。
秘密鍵とパスワードは公開Gitへ入れない。元プロジェクトの `.gitignore` は `/signing/` を除外している。

## 既存のテスト

```bash
node --test tests/core.test.cjs
bash tests/test-camera.sh
```

コア7テスト、カメラの計算344項目。今回の引き継ぎ作業でも復元した同じソースで成功した。
カメラの計算テストは回転式・縦横比の性質を検査するもので、カメラハードウェアを描画していない。

ブラウザUI：

```bash
npm install --no-save playwright @sparticuz/chromium
node tests/ui.test.cjs
```

テストは `require('playwright')` でPlaywrightを、`@sparticuz/chromium` で実行ファイルを探す。
ブラウザ用のネイティブ依存関係が必要な場合がある。
任意の環境変数 `KURURI_PLAYWRIGHT_MODULE`、`KURURI_CHROMIUM_MODULE`、`KURURI_QA_FONT_DIR` で調整できる。
日本語スクリーンショットのフォントを追加する場合は `@fontsource/noto-sans-jp` のディレクトリを指定する。
UIテストは写真・動画のネイティブ処理を模擬している。48項目成功の既存記録を同梱。
今回はブラウザ依存環境の再構築やUIテストの再実行は行っていない。

## 受け入れ確認

次のリリース前の実機確認は `docs/07-acceptance-checklist-ja.md` を使う。
テスト結果の説明では「今回実行」「以前の記録」「コードに実装」「実機未確認」を区別する。
