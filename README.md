# Theta.Kamigata.Hatch — くるりヘア

子どもの髪型を前・後ろ・左右・斜め・真上から写真で残し、指でドラッグして角度を切り替えて見返すAndroidアプリ。
昔のQuickTime VRの **Object Movie** のイメージで、AIによる3D生成ではなく実際の写真を角度別に切り替えます。

- 最新版：**1.1.0**（versionCode 10100, package `jp.yaegaki.kururihair`）
- ダウンロード：[GitHub Releases](https://github.com/kiwiman0706-beep/Theta.Kamigata.Hatch/releases)
- 署名証明書 SHA-256：`081a9477c8c8a1997d6d3a376566fdd2102a984f89550095fe9eda760fce0d20`

## インストール
Android 8.0以降。Releases から `kururi-hair-x.y.z.apk` を取得し、「提供元不明のアプリ」を許可してインストール。
既に入っている端末は**アンインストールせず上書き更新**してください（記録は端末内にあり、削除すると消えます）。

## このリポジトリ
前スレッドの開発引き継ぎZIP（2026-10-17）を、公開してよい部分だけ登録したものです。

| 場所 | 内容 |
| --- | --- |
| [START_HERE-ja.md](START_HERE-ja.md)、[AGENTS.md](AGENTS.md) | 引き継ぎの入口と開発方針 |
| [docs/](docs/) | 要望原文、設計、履歴、ビルド手順、課題、実機受け入れ表 |
| [docs/09-theta-hatch-plan-ja.md](docs/09-theta-hatch-plan-ja.md) | **Theta.xxx.Hatch 要件に合わせる次の構想（案）** |
| `project/kururi-hair/` | 1.1.0 の全ソース・テスト・ビルドスクリプト |
| `releases/` | 提供済みの署名済みAPK（再ビルドなしのバイト列） |
| `.github/workflows/` | `release-apk`（証明書を照合して Release 公開）、`test`（コア・カメラ計算テスト） |

**公開しないもの**（`.gitignore` で除外）：署名鍵 `signing/`、`private/`（パスワード）、
`references/`（子どもが写った不具合報告画像）、`archives/`（鍵を含む元ソースZIP）。
これらは手元の非公開の引き継ぎZIPにだけ残してください。

## リリース手順（現行）
1. 手元で同じ鍵を使い `KURURI_SIGNING_PASSWORD=... bash project/kururi-hair/build-apk.sh` でビルド（versionCode を上げる）。
2. `releases/kururi-hair-x.y.z.apk` と `docs/release-notes/vx.y.z.md` を追加して push。
3. Actions の `release-apk` を `version: vx.y.z` で手動実行（またはタグ push）。証明書・package・versionName が一致しないと失敗します。

## テスト
```sh
cd project/kururi-hair
node --test tests/core.test.cjs   # コア7テスト
bash tests/test-camera.sh         # カメラ向き計算344項目（JDK 17）
python3 ../../tools/verify_bundle.py  # v1.1.0 スナップショットのハッシュ照合
```
計算・模擬ブリッジのテストは実機のカメラ・動画動作確認の代わりになりません。1.1.0 の実機確認は未実施です。
