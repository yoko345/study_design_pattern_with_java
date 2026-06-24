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
    ├── commands/
    │   └── pr-article.md    # /pr-article スラッシュコマンド定義
    └── skills/
        └── write-article/
            └── SKILL.md      # 記事執筆・校正ルール
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

## Java コードのフォーマット方針

Java コードのフォーマット（空行・括弧・インデント等）に関して判断に迷う場合は、Google Java Style Guide（https://google.github.io/styleguide/javaguide.html）を正式な参照先とする。同ガイドに明確な規定がない場合は、既存記事の確立済みスタイルとの一貫性を優先する。

このフォーマット方針は `src/chapterXX/*.java` のソースコードと、記事内の Java コードブロックの両方に適用する。

空行ルール（Google Java Style Guide 4.6.1 準拠）:

- フィールドの連続宣言は空行なしでまとめる
- コンストラクタ・メソッド・抽象メソッド宣言の間（インタフェースの抽象メソッドも含む）には必ず空行を入れる。フィールドブロックとコンストラクタ/メソッドの間にも空行を入れる
- クラス/インタフェース宣言の開始の `{` の直後（最初のメンバーの前）、および終了の `}` の直前（最後のメンバーの後）には空行を入れない

JDK ソースコードを引用する場合は原文のまま掲載し、上記ルールは適用しない。

## 記事の執筆・校正ルール

`学習内容/chapterXX_パターン名.md` の執筆・校正・編集には `write-article` skill（`.claude/skills/write-article/SKILL.md`）を使う。

## 通常のコミット&push手順

「pushして」と言われたら以下を実行する：

1. `git status` で変更ファイルを確認
2. 対象ファイルを `git add`
3. コミットメッセージは **簡潔な1行**（例: `chapter02: Adapterパターンの記事を追加（WIP）`）
4. `git push origin <current-branch>`

## PR 作成手順

`/pr-article` スラッシュコマンドを使う（`.claude/commands/pr-article.md` 参照）。

手順：pr-draft.md 作成 → push → `gh pr create` → pr-draft.md 削除

動作確認セクションは PR 本文に不要。

## ルール・フィードバックの配置方針

ルールや学びは次の3か所のいずれかに記録する。同じ内容を複数箇所に書かない。

- **CLAUDE.md**: タスクの種類に関わらず常に必要な内容（プロジェクト構成・ビルド・Java コードフォーマット・git/PR 手順）のみ
- **`write-article` skill**（`.claude/skills/write-article/SKILL.md`）: 記事執筆・校正タスクのときだけ必要な、確定済みの構成・文体・テンプレートルール
- **memory（フィードバック）**: まだ確定していない方針、特定章だけの例外判断、作業プロセス上の教訓、ユーザーの考え方の傾向

memory 上のフィードバックが「以後すべての章で同じ判断をする」という確定ルールになった時点で、その内容を CLAUDE.md か skill に書き込み、memory 側のファイルは削除する（要約・移動メモも残さない）。重複を残さないことを優先する。
