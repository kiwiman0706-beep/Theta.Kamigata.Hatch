# Changelog / バージョン履歴

Theta.Kamigata.Hatch（アプリ名：くるりヘア, package `jp.yaegaki.kururihair`）のリリース記録。
GitHub Releases: <https://github.com/kiwiman0706-beep/Theta.Kamigata.Hatch/releases>

## v1.1.0 — versionCode 10100 · stable（2026-10-03 提供、2026-10-17 GitHub 公開）
- 前開発スレッドで提供した署名済みAPKを再ビルドせずに公開（このリポジトリでの初回リリース）。
- カメラのプレビューが90°横向きになる不具合の修正コード、動画撮影／読み込みと手動フレーム切り出し。
- リポジトリ側：`build-apk.sh` の既定パスワードを削除、鍵がない時の新規鍵生成を明示指定制に（アプリ本体は無変更）。
- 詳細: [docs/release-notes/v1.1.0.md](docs/release-notes/v1.1.0.md)

## それ以前（このリポジトリ外で配布）
- 1.0.1（中間ビルド、未提供）— 回転・縦横比の計算を CameraGeometry に分離
- 1.0.0 — 多角度撮影・ガイド・QTVR風閲覧・画像埋め込みHTML・ZIPバックアップ
