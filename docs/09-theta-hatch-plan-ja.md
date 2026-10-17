# Theta.xxx.Hatch 要件に合わせる次の構想（案）

作成：2026-10-17。対象：くるりヘア 1.1.0（versionCode 10100, package `jp.yaegaki.kururihair`）を
Theta.Kamigata.Hatch リポジトリで継続開発するための構想。
**これは提案です。** `02-user-requests-verbatim-ja.md` の確定要望ではありません。採否はユーザーが決めます。

## 1. 「Theta.xxx.Hatch」の要件をどう読み取ったか

明文化された要件書は見つからなかったため、同シリーズの先行作 **DeskHatch**（`kiwiman0706-beep/deskhatch`）と、
同日に同じ方式で整理された兄弟リポジトリ **Theta.Item.Hatch**（`docs/09_THETA_HATCH_PLAN.md`）から共通規約を抽出した。

| # | シリーズ共通の規約（DeskHatch の実態） | Theta.Kamigata.Hatch での要件（案） | 1.1.0 の現状 |
| --- | --- | --- | --- |
| T1 | 識別子 `Theta.DeskHatch`、Publisher `Theta` | 製品名を `KamigataHatch`（Theta.KamigataHatch）系に揃える | アプリ名「くるりヘア」 |
| T2 | 公開リポジトリ＋GitHub Releases で配布 | APK を GitHub Releases で配布 | **v1.1.0 で対応**（`release-apk` ワークフロー） |
| T3 | タグ or 手動実行で Actions がビルド→Release | CI でビルド・署名・公開まで自動化 | 署名済みAPKの検証・公開のみ |
| T4 | stable／beta（`-beta` タグ＝プレリリース） | 同じ2チャネル | ワークフローは `-` 付きタグをプレリリース扱い済み。運用は未開始 |
| T5 | アプリ内自動更新 | 新版の確認・取得・インストール | 未対応（※INTERNET権限なしの方針と衝突。§2-3） |
| T6 | winget／MSIX 登録 | Android 相当：Obtainium 対応、将来 F-Droid／Google Play を検討 | 未対応 |
| T7 | 日本語／英語 UI（自動判定・切替） | 日英 UI | 日本語のみ |
| T8 | README（バッジ・手順）、CHANGELOG、PUBLISHING.md | 同等の文書一式 | **README・CHANGELOG は今回作成**。PUBLISHING.md 未作成 |
| T9 | プライバシーポリシー、GitHub Pages のLP・ライブデモ | 同等。デモは書き出しHTMLをイラスト素材で作る | 未対応 |
| T10 | 単体＋E2E テストを CI で実行 | コア・カメラ計算・ブラウザUIテストを CI 化 | **コア7・カメラ344は今回 CI 化**（`test.yml`）。UIテストは未 |
| T11 | MIT License | ライセンスを明示 | 未設定（要判断） |
| T12 | 署名鍵を Secrets で受ける準備 | 鍵を Secrets に置き CI で同じ署名 | **鍵は手元にある**（非公開の引き継ぎZIP）。Secrets 未登録 |

兄弟の Theta.Item.Hatch は元の署名鍵が所在不明だが、こちらは **鍵・パスワード・証明書指紋がそろっている**。
そのため Android 版の Theta 化で一番難しい「同じ署名で CI から出し続ける」（T3/T12）が、Secrets 登録だけで実現できる。

## 2. 最初に決めること（ユーザー判断が必要）

1. **ブランド名**：推奨は package `jp.yaegaki.kururihair` を**維持**し、表示名だけ
   「KamigataHatch（くるりヘア）」等へ変える案。package を変えると別アプリ扱いになり、端末の記録を ZIP で移す必要が出る。
2. **署名鍵を GitHub Secrets に登録するか**：登録すれば以後のAPKを CI でビルド・署名できる。
   リポジトリの Settings → Secrets and variables → Actions に
   `KURURI_KEYSTORE_B64`（`base64 -w0 kururi-personal.jks` の出力）と `KURURI_SIGNING_PASSWORD` を登録する作業はユーザー側で行う。
   登録しない場合は、手元でビルドした署名済みAPKを `releases/` に置き、現行ワークフローで検証・公開する運用を続ける。
3. **ネットワーク方針**：現行は INTERNET 権限なし＝写真が外へ出ないことが強み。
   アプリ内更新（T5）を入れると INTERNET 権限が要る。推奨は **アプリ内更新は入れず、Obtainium（外部の更新確認アプリ）で代替**。
   入れる場合も「設定で手動確認のみ・GitHub の `releases/latest` だけに接続」と明記する。
4. **ライセンス**（DeskHatch に揃えて MIT か、個人用の非公開ライセンスか）。
   子どもの写真を扱うアプリなので、公開リポジトリには実データ・参考画像を入れない運用を README に明記済み。
5. **配布先の範囲**：GitHub Releases＋Obtainium で十分か、Google Play（開発者登録・審査・プライバシーポリシー必須）まで目指すか。

## 3. 段階的な構想

### Phase 0 — 済（今回）
- 引き継ぎ一式を公開リポジトリへ登録。署名鍵・パスワード・子どもの参考画像・鍵入りの元ソースZIPは除外（`.gitignore`）。
- `build-apk.sh` から既定パスワードを削除し、鍵がない時に**新しい鍵を黙って作らない**よう変更（`KURURI_ALLOW_NEW_KEY=1` の時だけ生成）。アプリ本体は無変更。
- `release-apk` ワークフロー：同梱APKを `apksigner` で検証し、証明書 SHA-256・package・versionName が一致した時だけ Release を作る。
- 1.1.0 APK と SHA256 を v1.1.0 Release として公開。`test.yml` でコア・カメラ計算テストを push ごとに実行。

### Phase 1 — 実機確認と CI ビルド（T3, T4, T10, T12）
- **最優先**：Release の 1.1.0 を実機に上書きインストールし、`07-acceptance-checklist-ja.md` の A01〜A18、
  特に A03／A04（カメラの向き）と A08〜A11（動画切り出し）の結果を記録する。ここで不具合が出たら 1.1.1 で直す。
- `build.yml`：push／PR ごとに SDK `platforms;android-35`・`build-tools;35.0.0` を入れて `build-apk.sh` でデバッグ署名のAPKを作る（ビルドが壊れていないかの確認用）。
- `release-apk.yml` を「ビルドして署名して公開」に拡張：Secrets から keystore を復元し、
  証明書ピン留めチェックは継続（鍵を取り違えたAPKは絶対に出さない）。
- versionCode はタグから自動算出。beta を正式版より前に並べるため、現行の `MMmmpp`（10100）を100倍して末尾2桁を使う案：
  `1.2.0-beta.3` → 1020003、`1.2.0` → 1020099。どちらも現行 10100 より大きく、beta→正式の順に必ず増える。
- Playwright のブラウザUIテスト（`tests/ui.test.cjs`、48項目）を CI 化。依存はロックファイルで固定する。
- 任意：Android Studio で開けるよう Gradle 構成を追加（ユーザー環境に Android Studio あり）。`build-apk.sh` は残す。

### Phase 2 — 撮影の自動化（R009：ユーザー発展要望）
ユーザーの要望「良いところで認識して勝手にシャッター」を、**ネット不要・端末内だけ**で段階的に実現する。
顔検出だけでは後頭部・つむじを判定できないため、「撮る時機」と「どの方向か」を分けて扱う。

1. **動画からの候補フレーム自動提案**（最も安全な第一歩）：切り出し画面で動画全体を間引き取得し、
   ぶれの少なさ（ラプラシアン分散）、前後フレームとの差分の小ささ（静止度）、明るさでスコア化して上位を候補表示。保存は今まで通り利用者が選ぶ。
2. **方向の推定にスマホのセンサーを使う**：最初に「正面」を撮った時の端末の向き（回転ベクトルセンサー）を基準に、
   撮る人が子どもの周りを回った角度と見下ろし角を推定し、ガイドの方向を自動で進める。AIではなく姿勢センサーの計算なので、後頭部・真上でも使える。
3. **自動シャッター**：上記の静止度・ぶれ・推定方向が揃った瞬間に撮影（オン／オフ切替、撮影音・連写防止・撮り直し可）。
   正面付近のみ端末内の顔検出で「枠内に頭があるか」を補助的に使う案もあるが、外部APIは使わない。

### Phase 3 — 配布と更新（T5, T6）
- **Obtainium** 用に APK 命名規則（`kururi-hair-x.y.z.apk`）と Release 形式を固定し、README に追加手順を書く。
- アプリ内更新は §2-3 の判断次第。入れる場合は SHA-256 検証付きで `PackageInstaller` を使う。

### Phase 4 — Theta ブランドと文書（T1, T8, T9, T11）
- README を DeskHatch 形式に：アイコン、バッジ（Release／Downloads／License）、スクリーンショット、インストール手順。
- `docs/PUBLISHING.md`（リリース手順・Secrets 登録）、`docs/privacy.html`（写真は端末内のみ、通信なし、を明記）。
- GitHub Pages：LP ＋ ライブデモ。デモは `export.html` に**人形・イラストの多角度素材**を埋め込んだもの
  （実際の子どもの写真は使わない）。ブラウザでドラッグでき、アプリの価値がそのまま伝わる。
- ハッシュ一覧（`SHA256SUMS.txt`）は v1.1.0 タグ時点のスナップショット検証用と位置付ける。

### Phase 5 — 日英対応（T7）
- `assets/app.js`・`index.html`・`export.html` の文言を `ja`／`en` の辞書に抽出し、端末ロケールで自動判定＋設定で切替。
- Java 側（`CaptureActivity`・`VideoFrameActivity` の方向説明など）は `values/strings.xml` と `values-en/`。

## 4. 回帰させないこと（Theta 化でも維持）
- QTVR Object 方式（角度別の実写写真の切り替え）。AI 3D 生成へ作り替えない。
- package `jp.yaegaki.kururihair`・既存署名鍵・versionCode の単調増加。アンインストールさせない更新。
- データ `schema: 1` と ZIP バックアップの互換。変更時は移行処理とバックアップ確認を伴う。
- オフライン動作と、写真を外部へ送らないこと。
- 署名鍵・パスワード・子どもの写真をリポジトリへ入れないこと。
- 「実装済み」と「実機確認済み」を区別して報告すること。
