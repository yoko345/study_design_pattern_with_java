# CLAUDE.md - study_design_pattern

このファイルは Claude Code がこのリポジトリで作業する際の指針です。

## プロジェクトの概要

GoF デザインパターン（全 23 種）を Java で実装しながら学ぶプロジェクト。
各パターンの学習記事を `学習内容/` ディレクトリに Markdown で書き出す。
対象読者は実務経験 1 年程度のエンジニアを想定。

## ディレクトリ構造

```
study_design_pattern/
├── src/
│   └── chapterXX/           # Java ソースコード（書籍学習・練習問題を chapter 別に収録）
├── bin/
│   ├── chapterXX/           # コンパイル済みクラスファイル（自動生成・編集不要）
│   ├── fix_backtick_spaces.py
│   └── fix_english_japanese_spacing.py
├── 学習内容/
│   └── chapterXX_パターン名.md  # 学習記事の出力先
└── .claude/
    └── commands/
        └── pr-article.md    # /pr-article スラッシュコマンド定義
```

### src/ の構成例

各 chapter のソースは chapter 番号のディレクトリ以下に配置。
chapter によってはサブパッケージがあるため、コンパイル時はそのパスも含める。

## ビルド・実行方法

VS Code の Java Extension Pack を使用。`src/` がソースパス、`bin/` が出力先（`.vscode/settings.json` で設定済み）。

```bash
# コンパイル例（chapter01: サブパッケージなしの最小形）
javac -d bin src/chapter01/*.java

# 実行例
java -cp bin MainChapter01
```

## 記事執筆ワークフロー

### 出力先
- `学習内容/chapterXX_パターン名.md` に直接書き出す
- コミットや別途設計ドキュメントの作成は不要

### 記事の構成順序
1. 既存コード（シナリオ提示）
2. 追加要件（問題提起）
3. 好ましくない実装（アンチパターン）
4. パターンを使った正しい実装
5. メリット・深堀り
6. まとめ

### 記事の題名フォーマット
```
# 英語（カタカナ）パターン ― 説明
```
例: `# Iterator（イテレータ）パターン ― コレクションの走査を統一する`

### 目次
冒頭の導入文の直後に `## 目次` を必ず追加する。

### 好ましくない実装の直前に挿入する定型注記
```
※ここで一旦読むのを止めて、ご自身でコーディングを行なってみてください。その後で、続きを読んでください。
```
（chapter02 以降で統一。chapter01 のみ blockquote 形式を使用）

### GoF 深堀り④の定型文
「今回使った○○パターンは、GoF の 23 パターンのうち〜に分類されます」の 2 文を末尾に配置する。

### シナリオ方針
書籍コードの丸パクリを避け、各章で独自の業務シナリオを新たに考案する。

## ポストプロセススクリプト

章の記事が完成した後、以下の順で実行する。

```bash
# 1. インラインコード前後の日本語との間にスペースを挿入
python3 bin/fix_backtick_spaces.py 学習内容/chapterXX_xxx.md

# 2. 英字と日本語の間にスペースを挿入（実行後、目視で不要スペースを削除してフィードバックをもらう）
python3 bin/fix_english_japanese_spacing.py 学習内容/chapterXX_xxx.md
```

## PR 作成手順

`/pr-article` スラッシュコマンドを使う（`.claude/commands/pr-article.md` 参照）。

手順：pr-draft.md 作成 → push → `gh pr create` → pr-draft.md 削除

動作確認セクションは PR 本文に不要。
