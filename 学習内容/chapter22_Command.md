# Command（コマンド）パターン ― 操作をオブジェクトとして表し、記録・取り消しを可能にする

次のような経験をしたことはありませんか？

> 操作を取り消せるようにするために、行った操作の種類と内容を記録しておき、取り消すときに操作の種類ごとに分岐して逆の処理を書いていたら、操作を 1 つ追加するたびに、記録する項目と、取り消すための分岐を書き足す羽目になった。おまけに、分岐を書き足し忘れて、取り消したはずの操作が実際には元に戻っていない不具合を生んでしまうこともあった。

この記事では、社内ポータルのお知らせ記事の編集画面に「1 つ前に戻す」機能を追加するシナリオを通して、Command パターンがこの問題をどのように解決するかを紹介します。

## 目次

- [【具体例】](#具体例)
    - [シナリオ](#シナリオ)
    - [既存コードの仕様](#既存コードの仕様)
- [好ましくない実装](#好ましくない実装)
- [正しい実装](#正しい実装)
- [まとめ](#まとめ)
- [【深堀り①】Memento パターンとの違い](#深堀り1)
    - [2 つのパターンを組み合わせる](#2-つのパターンを組み合わせる)
- [【深堀り②】Strategy パターンとの違い](#深堀り2)
- [【深堀り③】複数の操作をまとめて取り消す（Composite パターンとの組み合わせ）](#深堀り3)
- [【深堀り④】履歴が増え続けたときの上限](#深堀り4)
- [【深堀り⑤】Java 標準ライブラリにおける Command パターンの例](#深堀り5)
- [【深堀り⑥】GoF デザインパターンとの位置づけ](#深堀り6)

---

## 【具体例】

### シナリオ

> あなたは社内ポータルの開発チームに所属しています。<br>
> 社内ポータルのお知らせ記事は、メールの宛先のように公開先の部署を 1 つずつ追加して、公開する範囲を決めます。<br>
> 現在、お知らせ記事の編集画面では、本文の変更や公開先の部署の追加・削除ができます。しかし、操作を元に戻す機能は実装されていないため、誤って本文や公開先の部署を書き換えてしまうと、記事の担当者が記憶を頼りに手動で直すしかない状態です。<br>
> ある日、人事部向けの記事の公開先を変更している途中で、別の部署を追加してしまいました。記憶を頼りに手動で直したものの、誤って追加した部署を元からの公開先と勘違いして残してしまい、他の部署にも公開されかける、というヒヤリハットがありました。<br>
> これを受けて、あなたは編集画面に「1 つ前に戻す」機能を追加することになりました。直前の操作を取り消し、続けて使えばさらに前の操作も順に取り消せるようにします。また、どの操作を取り消したのかを担当者が確かめられるように、操作後の記事の内容と一緒に、取り消した操作の内容（「経理部の追加」など）を表示します。

※本記事のコードでは Command パターンの解説に集中するため、編集画面への表示をコンソールへの文字列出力で代用します。

### 既存コードの仕様

※実務では、次の `Article` のようなエンティティクラスは `entity` パッケージなど専用のディレクトリに切り出すのが一般的です。しかし、本記事ではパッケージ構成を主題としないため `example` パッケージ直下にまとめています。

- `Article`

お知らせ記事 1 件を表すクラスです。<br>
お知らせ記事の本文と公開先の部署を保持します。<br>
公開先に部署を追加するときは、すでに追加されている部署ではないかをチェックしています。

| フィールド          | 型             | 説明         |
| ------------------- | -------------- | ------------ |
| `body`              | `String`       | 本文         |
| `targetDepartments` | `List<String>` | 公開先の部署 |

| メソッド                 | 引数                | 戻り値の型     | 説明                                                                                           |
| ------------------------ | ------------------- | -------------- | ---------------------------------------------------------------------------------------------- |
| `changeBody`             | `String body`       | `void`         | 本文を変更する                                                                                 |
| `addTargetDepartment`    | `String department` | `void`         | 公開先に部署を追加する<br>すでに追加されている部署の場合は `IllegalArgumentException` を投げる |
| `removeTargetDepartment` | `String department` | `void`         | 公開先から部署を削除する                                                                       |
| `getBody`                | なし                | `String`       | 本文を取得する                                                                                 |
| `getTargetDepartments`   | なし                | `List<String>` | 公開先の部署の一覧を取得する                                                                   |
| `getSummary`             | なし                | `String`       | 本文と公開先をまとめた表示用の文字列を返す                                                     |

**`Article.java`**

```java
package example;

public class Article {
    private String body = "";
    private List<String> targetDepartments = new ArrayList<>();

    public void changeBody(String body) {
        this.body = body;
    }

    public void addTargetDepartment(String department) {
        if (targetDepartments.contains(department)) {
            throw new IllegalArgumentException("すでに公開先に追加されている部署です");
        }

        targetDepartments.add(department);
    }

    public void removeTargetDepartment(String department) {
        targetDepartments.remove(department);
    }

    public String getBody() {
        return body;
    }

    public List<String> getTargetDepartments() {
        return targetDepartments;
    }

    public String getSummary() {
        return "本文：" + body + "／公開先：" + targetDepartments;
    }
}
```

<br>

- `ArticleEditor`

お知らせ記事の編集画面を表すクラスです。<br>
本文の変更と、公開先の部署の追加・削除の操作を受け付け、操作後の記事の内容をコンソールに出力します。

| フィールド | 型        | 説明                 |
| ---------- | --------- | -------------------- |
| `article`  | `Article` | 編集中のお知らせ記事 |

| メソッド                 | 引数                | 戻り値の型 | 説明                                                             |
| ------------------------ | ------------------- | ---------- | ---------------------------------------------------------------- |
| `changeBody`             | `String body`       | `void`     | 記事の本文を変更し、変更後の内容をコンソールに出力する           |
| `addTargetDepartment`    | `String department` | `void`     | 記事の公開先に部署を追加し、追加後の内容をコンソールに出力する   |
| `removeTargetDepartment` | `String department` | `void`     | 記事の公開先から部署を削除し、削除後の内容をコンソールに出力する |

**`ArticleEditor.java`**

```java
package example;

public class ArticleEditor {
    private Article article;

    public ArticleEditor(Article article) {
        this.article = article;
    }

    public void changeBody(String body) {
        article.changeBody(body);

        System.out.println("[本文を変更] " + article.getSummary());
    }

    public void addTargetDepartment(String department) {
        article.addTargetDepartment(department);

        System.out.println("[公開先を追加] " + article.getSummary());
    }

    public void removeTargetDepartment(String department) {
        article.removeTargetDepartment(department);

        System.out.println("[公開先を削除] " + article.getSummary());
    }
}
```

<br>

- `Main`（実行クラス）

**`Main.java`**

```java
package example;

public class Main {
    public static void main(String[] args) {
        ArticleEditor editor = new ArticleEditor(new Article());

        editor.changeBody("新しい評価制度の説明会を開催します。");
        editor.addTargetDepartment("人事部");
        editor.addTargetDepartment("経理部");
        // 経理部を誤って追加してしまったため、手動で削除している
        editor.removeTargetDepartment("経理部");
        editor.addTargetDepartment("総務部");
    }
}
```

**実行結果**

```
[本文を変更] 本文：新しい評価制度の説明会を開催します。／公開先：[]
[公開先を追加] 本文：新しい評価制度の説明会を開催します。／公開先：[人事部]
[公開先を追加] 本文：新しい評価制度の説明会を開催します。／公開先：[人事部, 経理部]
[公開先を削除] 本文：新しい評価制度の説明会を開催します。／公開先：[人事部]
[公開先を追加] 本文：新しい評価制度の説明会を開催します。／公開先：[人事部, 総務部]
```

※ここで一旦読むのを止めて、ご自身でコーディングを行なってみてください。その後で、続きを読んでください。

## 好ましくない実装

では、シナリオに従い追加実装をしていきましょう。

真っ先に思いつくのは、編集画面のクラス（`ArticleEditor`）で、操作のたびに操作の種類と、取り消すために必要な情報（追加・削除した部署や、変更前の本文）を記録しておき、1 つ前に戻すときに、記録した操作の種類に応じて処理を分ける、という実装ではないでしょうか？

**`EditHistoryEntry.java`**

```java
package example;

public class EditHistoryEntry {
    // 操作の種類を表す文字列（"CHANGE_BODY"・"ADD_DEPARTMENT"・"REMOVE_DEPARTMENT"）が入る
    private String type;
    // 公開先の部署の追加・削除時に、追加・削除した部署が入る
    private String department;
    // 本文の変更時に、変更前の本文が入る
    private String oldBody;

    public EditHistoryEntry(String type, String department, String oldBody) {
        this.type = type;
        this.department = department;
        this.oldBody = oldBody;
    }

    public String getType() {
        return type;
    }

    public String getDepartment() {
        return department;
    }

    public String getOldBody() {
        return oldBody;
    }
}
```

**`ArticleEditor.java`**

```java
package example;

public class ArticleEditor {
    private Article article;
    private Deque<EditHistoryEntry> history = new ArrayDeque<>(); // ←ここを追加

    public ArticleEditor(Article article) {
        this.article = article;
    }

    public void changeBody(String body) {
        String oldBody = article.getBody(); // ←ここを追加
        article.changeBody(body);
        history.push(new EditHistoryEntry("CHANGE_BODY", null, oldBody)); // ←ここを追加

        System.out.println("[本文を変更] " + article.getSummary());
    }

    public void addTargetDepartment(String department) {
        article.addTargetDepartment(department);
        history.push(new EditHistoryEntry("ADD_DEPARTMENT", department, null)); // ←ここを追加

        System.out.println("[公開先を追加] " + article.getSummary());
    }

    public void removeTargetDepartment(String department) {
        article.removeTargetDepartment(department);
        history.push(new EditHistoryEntry("REMOVE_DEPARTMENT", department, null)); // ←ここを追加

        System.out.println("[公開先を削除] " + article.getSummary());
    }

    /* ここを追加（ここから） */
    public void undo() {
        if (history.isEmpty()) {
            System.out.println("[1つ前に戻す] 戻せる操作がありません");

            return;
        }

        EditHistoryEntry entry = history.pop();
        switch (entry.getType()) {
            case "CHANGE_BODY" -> article.changeBody(entry.getOldBody());
            case "ADD_DEPARTMENT" -> article.removeTargetDepartment(entry.getDepartment());
            case "REMOVE_DEPARTMENT" -> article.addTargetDepartment(entry.getDepartment());
        }

        System.out.println("[" + describe(entry) + "を1つ前に戻す] " + article.getSummary());
    }

    private String describe(EditHistoryEntry entry) {
        return switch (entry.getType()) {
            case "CHANGE_BODY" -> "本文の変更";
            case "ADD_DEPARTMENT" -> entry.getDepartment() + "の追加";
            case "REMOVE_DEPARTMENT" -> entry.getDepartment() + "の削除";
            default -> throw new IllegalArgumentException("表示する内容が定義されていない操作です：" + entry.getType());
        };
    }
    /* ここを追加（ここまで） */
}
```

> ※`history` フィールドは、`Deque` インターフェースをスタック（後から入れたものを先に取り出す入れ物）として使っており、`push` メソッドで積んだ記録を、`pop` メソッドで新しいものから順に取り除いて取得します。

**`Main.java`**

```java
package example;

public class Main {
    public static void main(String[] args) {
        ArticleEditor editor = new ArticleEditor(new Article());

        editor.changeBody("新しい評価制度の説明会を開催します。");
        editor.addTargetDepartment("人事部");
        editor.addTargetDepartment("経理部");
        /* ここを修正（ここから） */
        // 経理部を誤って追加してしまったため、1 つ前に戻している
        editor.undo();
        /* ここを修正（ここまで） */
        editor.addTargetDepartment("総務部");
        /* ここを追加（ここから） */
        editor.changeBody("新しい評価制度の説明会を4月に開催します。");
        // 開催月がまだ決まっていなかったため、1 つ前に戻している
        editor.undo();
        /* ここを追加（ここまで） */
    }
}
```

**実行結果**

```
[本文を変更] 本文：新しい評価制度の説明会を開催します。／公開先：[]
[公開先を追加] 本文：新しい評価制度の説明会を開催します。／公開先：[人事部]
[公開先を追加] 本文：新しい評価制度の説明会を開催します。／公開先：[人事部, 経理部]
[経理部の追加を1つ前に戻す] 本文：新しい評価制度の説明会を開催します。／公開先：[人事部]
[公開先を追加] 本文：新しい評価制度の説明会を開催します。／公開先：[人事部, 総務部]
[本文を変更] 本文：新しい評価制度の説明会を4月に開催します。／公開先：[人事部, 総務部]
[本文の変更を1つ前に戻す] 本文：新しい評価制度の説明会を開催します。／公開先：[人事部, 総務部]
```

1 つ前に戻すときに、操作後の記事の内容と一緒に、取り消した操作の内容が表示されるようになっています。

コンパイルエラーがなく結果が出力されていることから、一見すると実装・動作確認ともに問題ないように見えます。

しかし、この実装には以下の問題点があります。

- 取り消す処理と、操作の内容を表示する処理を、`undo`・`describe` メソッドの `switch` 文で、操作の種類ごとに分岐させて書いている。そのため、操作を 1 つ追加する（例えば「公開先の部署をすべて削除」）たびに、`EditHistoryEntry` クラスにその操作を取り消すために必要な情報（削除した部署のリストなど）のフィールドを追加したうえで、`undo`・`describe` メソッドの両方の `switch` 文に分岐を書き足す必要がある。
    - その結果、`undo` メソッドへの書き足しを忘れると、コンパイルエラーも例外も発生しないまま、取り消したと表示されるのに、記事の内容が元に戻らない不具合が生まれてしまう。

## 正しい実装

では、好ましくない実装で挙げた問題点を解決するにはどうすればよいのでしょうか？

この問題を解決するのが **Command パターン**です。<br>
Command パターンは、操作（命令）そのものを 1 つのオブジェクトとして表すパターンです。本記事では、本文の変更や公開先の部署の追加・削除といった編集操作を、それぞれ 1 つのクラスとして表します。

まず、編集操作を表すクラスで使用する共通のインターフェースから見ていきましょう。

**`EditCommand.java`**

```java
package example;

public interface EditCommand {
    void execute();

    void undo();

    String getDescription();
}
```

`EditCommand` は新たに追加したインターフェースで、操作を実行する抽象メソッド `execute`、操作を取り消す抽象メソッド `undo`、操作の内容を返す抽象メソッド `getDescription` を持ちます。

次に、共通のインターフェースを実装したクラスを見ていきましょう。

**`ChangeBodyCommand.java`**

```java
package example;

public class ChangeBodyCommand implements EditCommand {
    private Article article;
    private String newBody;
    private String oldBody;

    public ChangeBodyCommand(Article article, String newBody) {
        this.article = article;
        this.newBody = newBody;
    }

    @Override
    public void execute() {
        oldBody = article.getBody();
        article.changeBody(newBody);
    }

    @Override
    public void undo() {
        article.changeBody(oldBody);
    }

    @Override
    public String getDescription() {
        return "本文の変更";
    }
}
```

**`AddTargetDepartmentCommand.java`**

```java
package example;

public class AddTargetDepartmentCommand implements EditCommand {
    private Article article;
    private String department;

    public AddTargetDepartmentCommand(Article article, String department) {
        this.article = article;
        this.department = department;
    }

    @Override
    public void execute() {
        article.addTargetDepartment(department);
    }

    @Override
    public void undo() {
        article.removeTargetDepartment(department);
    }

    @Override
    public String getDescription() {
        return department + "の追加";
    }
}
```

**`RemoveTargetDepartmentCommand.java`**

```java
package example;

public class RemoveTargetDepartmentCommand implements EditCommand {
    private Article article;
    private String department;

    public RemoveTargetDepartmentCommand(Article article, String department) {
        this.article = article;
        this.department = department;
    }

    @Override
    public void execute() {
        article.removeTargetDepartment(department);
    }

    @Override
    public void undo() {
        article.addTargetDepartment(department);
    }

    @Override
    public String getDescription() {
        return department + "の削除";
    }
}
```

`ChangeBodyCommand`・`AddTargetDepartmentCommand`・`RemoveTargetDepartmentCommand` は新たに追加したクラスで、それぞれ本文を変更する操作、公開先に部署を追加する操作、公開先から部署を削除する操作を表します。<br>
いずれのクラスも、操作する記事を `article` フィールドに保持しています。そして、`execute` メソッドによりその記事に対して操作を実行し、`undo` メソッドによりその操作を取り消し、`getDescription` メソッドにより「1 つ前に戻す」で表示する操作の内容を返しています。<br>
それぞれのクラスは、取り消すために必要な情報をフィールドに保持しています。`ChangeBodyCommand` クラスは、`execute` メソッドで本文を変更する直前に、変更前の本文を `oldBody` フィールドに保持しています。`AddTargetDepartmentCommand`・`RemoveTargetDepartmentCommand` クラスは、追加・削除する部署を `department` フィールドに保持しています（→ [【深堀り②】Strategy パターンとの違い](#深堀り2)）。

次に、編集操作を表すオブジェクトを使う側のクラスを見ていきましょう。

**`ArticleEditor.java`**

```java
package example;

public class ArticleEditor {
    private Article article;
    private Deque<EditCommand> history = new ArrayDeque<>(); // ←ここを追加

    public ArticleEditor(Article article) {
        this.article = article;
    }

    public void changeBody(String body) {
        executeAndRecord(new ChangeBodyCommand(article, body)); // ←ここを修正

        System.out.println("[本文を変更] " + article.getSummary());
    }

    public void addTargetDepartment(String department) {
        executeAndRecord(new AddTargetDepartmentCommand(article, department)); // ←ここを修正

        System.out.println("[公開先を追加] " + article.getSummary());
    }

    public void removeTargetDepartment(String department) {
        executeAndRecord(new RemoveTargetDepartmentCommand(article, department)); // ←ここを修正

        System.out.println("[公開先を削除] " + article.getSummary());
    }

    /* ここを追加（ここから） */
    public void undo() {
        if (history.isEmpty()) {
            System.out.println("[1つ前に戻す] 戻せる操作がありません");

            return;
        }

        EditCommand command = history.pop();
        command.undo();

        System.out.println("[" + command.getDescription() + "を1つ前に戻す] " + article.getSummary());
    }

    private void executeAndRecord(EditCommand command) {
        command.execute();
        history.push(command);
    }
    /* ここを追加（ここまで） */
}
```

`ArticleEditor` クラスを振り返ると、既存の仕様から `history` フィールドと `undo`・`executeAndRecord` メソッドを追加しています。また、`changeBody`・`addTargetDepartment`・`removeTargetDepartment` メソッドでは、`Article` クラスのメソッドを直接呼び出す代わりに、編集操作を表すオブジェクトを作って `executeAndRecord` メソッドに渡すように修正しています。<br>
`history` フィールドでは、実行した操作をオブジェクトのまま積み重ねて保持できるようにしています（→ [【深堀り①】Memento パターンとの違い](#深堀り1)）。<br>
`executeAndRecord` メソッドでは、受け取ったオブジェクトの `execute` メソッドを呼び出して記事を編集する処理を委譲してから、そのオブジェクトを `history` フィールドに積んでいます。<br>
`undo` メソッドでは、最後に積んだオブジェクトを `history` フィールドから取り除いて取得し、そのオブジェクトの `undo` メソッドを呼び出して記事を元に戻す処理を委譲しています。表示する操作の内容は、そのオブジェクトの `getDescription` メソッドから取得しています。

なお、`history` フィールドに積んだ操作は、1 つ前に戻さない限り、操作のたびに増え続けます（→ [【深堀り④】履歴が増え続けたときの上限](#深堀り4)）。

最後に、実行クラスを見ていきましょう。

**`Main.java`**

```java
package example;

public class Main {
    public static void main(String[] args) {
        ArticleEditor editor = new ArticleEditor(new Article());

        editor.changeBody("新しい評価制度の説明会を開催します。");
        editor.addTargetDepartment("人事部");
        editor.addTargetDepartment("経理部");
        /* ここを修正（ここから） */
        // 経理部を誤って追加してしまったため、1 つ前に戻している
        editor.undo();
        /* ここを修正（ここまで） */
        editor.addTargetDepartment("総務部");
        /* ここを追加（ここから） */
        editor.changeBody("新しい評価制度の説明会を4月に開催します。");
        // 開催月がまだ決まっていなかったため、1 つ前に戻している
        editor.undo();
        /* ここを追加（ここまで） */
    }
}
```

**実行結果**

```
[本文を変更] 本文：新しい評価制度の説明会を開催します。／公開先：[]
[公開先を追加] 本文：新しい評価制度の説明会を開催します。／公開先：[人事部]
[公開先を追加] 本文：新しい評価制度の説明会を開催します。／公開先：[人事部, 経理部]
[経理部の追加を1つ前に戻す] 本文：新しい評価制度の説明会を開催します。／公開先：[人事部]
[公開先を追加] 本文：新しい評価制度の説明会を開催します。／公開先：[人事部, 総務部]
[本文を変更] 本文：新しい評価制度の説明会を4月に開催します。／公開先：[人事部, 総務部]
[本文の変更を1つ前に戻す] 本文：新しい評価制度の説明会を開催します。／公開先：[人事部, 総務部]
```

`Main` クラスは好ましくない実装と同じです。

実行結果は、好ましくない実装とまったく同じになっています。

以上のような実装を行うと、以下のメリットがあります。

- 実行・取り消し・操作の内容の表示の処理と、取り消すために必要な情報が、編集操作ごとに 1 つのクラスにまとまっている。また、それらのクラスは共通の `EditCommand` インターフェースを実装しているため、`ArticleEditor` クラスの `undo` メソッドでは、操作の種類に関係なく同じメソッドを呼び出すだけで済んでいる。そのため、操作を 1 つ追加する（例えば「公開先の部署をすべて削除」）場合でも、`EditCommand` インターフェースを実装したクラスを 1 つ追加すればよく、既存の編集操作を表すクラスや `undo` メソッドには手を加える必要がない。
    - その結果、追加したクラスに取り消す処理を書き忘れるとコンパイルエラーが発生するため、取り消したと表示されるのに、記事の内容が元に戻らない不具合が生まれることはなくなる。

## まとめ

正しい実装を振り返ると、本文の変更や公開先の部署の追加・削除といった編集操作が、それぞれ `EditCommand` インターフェースを実装した 1 つのクラスになっています。また、実行・取り消し・操作の内容の表示は、それぞれのクラスが担っています。そのため、`ArticleEditor` クラスは、実行した操作を表すオブジェクトを履歴に積んでおき、1 つ前に戻すときは、最後に積んだオブジェクトの `undo` メソッドを呼び出すだけでよくなっています。<br>
このように Command パターンは、操作そのものをオブジェクトとして表すことで、操作を履歴に残したり取り消したりといった扱いを、操作の種類に関係なく同じ形で行えるようにする設計パターンです。

本記事の内容はここまでとなります。

以降は「もう少し深く知りたい」という方向けの補足となります。今回学んだパターンに繋がる設計原則や、実務で役立つ背景知識について触れています。

---

<a id="深堀り1"></a>

## 【深堀り①】Memento パターンとの違い

正しい実装の `ArticleEditor` クラスを振り返ると、実行した操作を `history` フィールドに積んでおき、1 つ前に戻すときは、`history` フィールドから取り出した操作の `undo` メソッドを呼び出していました。<br>
この「履歴を積んでおき、取り出して元に戻す」という構造は、Memento パターンとよく似ています。

ただし、2 つのパターンでは、履歴に残すものが異なります。

Memento パターンでは、操作する前に、対象のオブジェクトが持つ状態をすべて残します。そのため、戻すときは、操作の種類に関係なく、残しておいた状態をそのまま書き戻します。<br>
例えば、本記事の編集画面で公開先の部署を追加する場合は、追加する前に、`Article` オブジェクトの本文と公開先の部署の両方を保持するオブジェクト（Memento）を作って、履歴に積みます。1 つ前に戻すときは、履歴から取り出した Memento の本文と公開先の部署を、そのまま `Article` オブジェクトに書き戻します。

Command パターンでは、行った操作の内容を残します。そのため、戻すときは、操作ごとに用意した取り消し方で戻します。<br>
例えば、本記事の `AddTargetDepartmentCommand` クラスは、取り消すための情報として、どの部署を追加したかだけを保持し、`undo` メソッドでその部署を削除して元に戻しています。

このように 2 つのパターンは、履歴を積んで元に戻す構造が似ていても、残すものが、状態なのか操作なのかで異なります。<br>
Memento パターンは、操作ごとに取り消し方を書く必要がないメリットがありますが、操作のたびに対象のオブジェクトの状態をすべて残すため、状態が大きいほど多くのメモリを使ってしまうデメリットがあります。<br>
Command パターンは、操作に関わる情報だけを残すため、多くの操作では使うメモリが少なくて済むメリットがありますが、操作ごとに取り消し方を書く必要があるデメリットがあります。

どちらのパターンを使うかは、取り消すときに操作の内容が必要かどうかと、残す状態の大きさで判断するとよいでしょう。<br>
本記事の編集画面では、1 つ前に戻すときに取り消す操作の内容を表示する必要があるうえ、本文が長いほど、操作のたびに残す状態が大きくなるため、Command パターンが向いています。<br>
反対に、取り消すときに操作の内容が必要なく、残す状態が小さい場合は、Memento パターンを検討するのが適切です。<br>
詳しくは「Memento パターン」で検索してみてください。

### 2 つのパターンを組み合わせる

Command パターンでは、逆の操作では元に戻せない操作もあります。<br>
例えば、テンプレートを選ぶと、本文と公開先の部署をまとめてテンプレートの内容に置き換える操作を追加したとします。置き換える前の内容が分からなければ元に戻せないため、取り消すには、置き換える前の本文と公開先の部署をすべて保持しておく必要があります。

そこで、編集画面全体を、状態を残すことができる Memento パターンに切り替えたとします。<br>
しかし、これだと公開先の部署を 1 つ追加するだけの操作でも、本文まで残すことになり、無駄が多くなります。

このような際に実務では、2 つのパターンを組み合わせることがあります。<br>
例えば、`EditCommand` インターフェースを実装した、テンプレートを適用する操作を表すクラスを作り、`execute` メソッドで置き換える前の状態を Memento として保持し、`undo` メソッドでその Memento を `Article` オブジェクトに書き戻します。<br>
これにより、状態をすべて残すのはテンプレートを適用する操作だけになり、他の操作は、これまでどおり操作に関わる情報だけを残せます。

<a id="深堀り2"></a>

## 【深堀り②】Strategy パターンとの違い

正しい実装の `ArticleEditor` クラスを振り返ると、`EditCommand` インターフェースを実装したオブジェクトを作り、記事を編集するときはそのオブジェクトの `execute` メソッドに、取り消すときは `undo` メソッドに処理を任せていました。<br>
この「共通のインターフェースを実装したクラスを用意し、処理を任せる」という構造は、Strategy パターンとよく似ています。

ただし、2 つのパターンでは、オブジェクトを作る単位が異なります。

Strategy パターンでは、アルゴリズム（処理のやり方）1 つにつき、オブジェクトを 1 つ作ります。そのため、オブジェクトごとに処理のやり方を持たせられます。使う側のクラスは、どれか 1 つのオブジェクトを保持し、別のやり方に差し替えるまでは、同じやり方で何度も処理を任せます。<br>
例えば、本記事の編集画面で、公開先の部署の並べ方を「追加した順」と「部署コード順」から選べるようにする場合、並べ方ごとにオブジェクトを作ります。`ArticleEditor` クラスでは、初期値としてどちらか一方のオブジェクトをフィールドとして保持します。公開先に部署を追加すると、どの部署を追加したかに関係なく、保持しているオブジェクトに並べ替えを任せます。並べ方を選び直したときは、保持しているオブジェクトを差し替えます。

Command パターンでは、担当者が行う操作 1 回につき、オブジェクトを 1 つ作ります。そのため、オブジェクトごとに行った操作の内容を持たせられます。使う側のクラスは、実行した後もそのオブジェクトを履歴に残しておくことで、後から取り消しを任せられます。<br>
例えば、本記事では、公開先に部署を追加するたびに `AddTargetDepartmentCommand` オブジェクトを作り、追加する部署を持たせています。`ArticleEditor` クラスは、どの部署を追加したかに関係なく、追加時に作ったオブジェクトを `history` フィールドに積んでおき、1 つ前に戻すときは、最後に積んだオブジェクトに取り消しを任せます。

このように 2 つのパターンは、共通のインターフェースを実装したクラスに処理を任せる構造が似ていても、オブジェクトを作る単位が、アルゴリズム 1 つなのか、担当者が行う操作 1 回なのかで異なります。

どちらのパターンを使うかは、オブジェクトに持たせたいものが、処理のやり方なのか、行った操作の内容なのかで判断するとよいでしょう。<br>
本記事の編集画面では、行った操作を履歴に残して取り消すため、Command パターンが向いています。<br>
反対に、やり方を差し替えながら何度も処理したい場合は、Strategy パターンを検討するのが適切です。<br>
詳しくは「Strategy パターン」で検索してみてください。

<a id="深堀り3"></a>

## 【深堀り③】複数の操作をまとめて取り消す（Composite パターンとの組み合わせ）

正しい実装の `ArticleEditor` クラスを振り返ると、1 つ前に戻すときは、最後に積んだオブジェクトを 1 つだけ `history` フィールドから取り除いて、その操作を取り消していました。そのため、1 回の「1 つ前に戻す」で取り消せるのは、1 回分の操作だけです。

ここで、「管理部門（人事部・総務部・経理部）」のように、いくつかの部署をまとめて公開先に追加する操作を足すとします。公開先に管理部門を追加する操作は 1 回で済むようになるため、1 つ前に戻すときも、1 回でまとめて取り消せるのが自然です。しかし、正しい実装のままでは、`ArticleEditor` クラスの `addTargetDepartment` メソッドを部署の数だけ呼び出して追加する必要があります。そのため、部署ごとに 1 つの操作として履歴に積まれ、「1 つ前に戻す」も部署の数だけ繰り返すことになります。<br>
そこで、`EditCommand` インターフェースを実装し、複数の操作をまとめて 1 つの操作として扱う `MacroCommand` クラスを追加します。

**`MacroCommand.java`**

```java
package example;

public class MacroCommand implements EditCommand {
    private String description;
    private List<EditCommand> commands = new ArrayList<>();

    public MacroCommand(String description) {
        this.description = description;
    }

    public void add(EditCommand command) {
        commands.add(command);
    }

    @Override
    public void execute() {
        for (EditCommand command: commands) {
            command.execute();
        }
    }

    @Override
    public void undo() {
        for (int i = commands.size() - 1; i >= 0; i--) {
            commands.get(i).undo();
        }
    }

    @Override
    public String getDescription() {
        return description;
    }
}
```

`MacroCommand` は新たに追加したクラスで、複数の操作をまとめた操作を表します。`description` フィールドに操作の内容を、`commands` フィールドにまとめる操作を保持します。<br>
`add` メソッドによりまとめる操作を追加し、`execute` メソッドによりまとめた操作を追加した順に実行しています。また、`undo` メソッドにより、`ArticleEditor` クラスの `undo` メソッドと同じく、まとめた操作を実行したときとは逆の順番で取り消し、`getDescription` メソッドにより、`description` フィールドに保持した操作の内容を返しています。

**`ArticleEditor.java`（一部抜粋）**

```java
package example;

public class ArticleEditor {
    private Article article;

    /* ここを追加（ここから） */
    public void addTargetDepartments(String groupName, List<String> departments) {
        MacroCommand command = new MacroCommand(groupName + "の追加");

        for (String department: departments) {
            command.add(new AddTargetDepartmentCommand(article, department));
        }
        executeAndRecord(command);

        System.out.println("[公開先をまとめて追加] " + article.getSummary());
    }
    /* ここを追加（ここまで） */
}
```

※正しい実装から追加した `addTargetDepartments` メソッドと、それが使う `article` フィールドのみを記載し、他のフィールド・メソッドは変更がないため省略しています。

`ArticleEditor` クラスを振り返ると、正しい実装から `addTargetDepartments` メソッドを追加しています。<br>
`addTargetDepartments` メソッドでは、まず、「管理部門の追加」のような操作の内容を持たせて、`MacroCommand` オブジェクトを作っています。次に、受け取った部署ごとに `AddTargetDepartmentCommand` オブジェクトを作って、`add` メソッドでまとめています。最後に、まとめたオブジェクトを 1 つの操作として `executeAndRecord` メソッドに渡しています。

**`Main.java`**

```java
package example;

public class Main {
    public static void main(String[] args) {
        ArticleEditor editor = new ArticleEditor(new Article());

        editor.changeBody("人事評価の面談日程をお知らせします。");
        editor.addTargetDepartments("管理部門", List.of("人事部", "総務部", "経理部"));
        // 人事部だけに公開する記事だったため、1 つ前に戻している
        editor.undo();
        editor.addTargetDepartment("人事部");
    }
}
```

**実行結果**

```
[本文を変更] 本文：人事評価の面談日程をお知らせします。／公開先：[]
[公開先をまとめて追加] 本文：人事評価の面談日程をお知らせします。／公開先：[人事部, 総務部, 経理部]
[管理部門の追加を1つ前に戻す] 本文：人事評価の面談日程をお知らせします。／公開先：[]
[公開先を追加] 本文：人事評価の面談日程をお知らせします。／公開先：[人事部]
```

`[管理部門の追加を1つ前に戻す]` の行では、1 回の「1 つ前に戻す」で、まとめて追加した 3 つの部署がすべて取り消されています。

管理部門をまとめて追加した直後に 1 つ前に戻すと、`ArticleEditor` クラスの `undo` メソッドは、最後に積んだ `MacroCommand` オブジェクトの `undo`・`getDescription` メソッドを呼び出します。`MacroCommand` クラスも `EditCommand` インターフェースを実装しており、他の編集操作を表すオブジェクトと同じように呼び出せるため、`ArticleEditor` クラスの `undo` メソッドには手を加える必要がありません。

この「1 つのものと、それらをまとめたものを、同じインターフェースで扱う」構造は、Composite パターンと呼ばれます。共通の型が `EditCommand` インターフェース、個別要素が `ChangeBodyCommand`・`AddTargetDepartmentCommand`・`RemoveTargetDepartmentCommand` クラス、複合要素が `MacroCommand` クラスにそれぞれあたります。また、`MacroCommand` クラスには `MacroCommand` オブジェクトもまとめられるため、まとめた操作をさらにまとめることもできます。<br>
このように、Command パターンの操作を表すクラスに Composite パターンを組み合わせることで、複数の操作を 1 回の「1 つ前に戻す」でまとめて取り消せるようになります。<br>
詳しくは「Composite パターン」で検索してみてください。

<a id="深堀り4"></a>

## 【深堀り④】履歴が増え続けたときの上限

正しい実装の `ArticleEditor` クラスを振り返ると、操作のたびに、その操作を表すオブジェクトを `history` フィールドに積んでいます。1 つ前に戻さない限り履歴は減らないため、編集を続けるほどメモリの消費は増えます。<br>
確かに、公開先の部署の追加・削除を表すオブジェクトは、操作する記事への参照の他に部署名を保持するだけなので、1 つあたりのメモリは小さく済みます。しかし、`ChangeBodyCommand` オブジェクトは、変更前と変更後の本文を丸ごと保持するため、本文が長いほど 1 つあたりのメモリは大きくなります。そのため、長い本文を何度も書き換えるほど、メモリの消費は大きく増えます。

そこで実務では、メモリの消費を抑えるためにいくつかの対策をとります。ここでは、その中でもよく使われる、履歴に上限を設けて、上限を超えたら古いものから捨てる方法を見ていきましょう。

**`ArticleEditor.java`（一部抜粋）**

```java
package example;

public class ArticleEditor {
    private static final int MAX_HISTORY = 50; // ←ここを追加
    private Deque<EditCommand> history = new ArrayDeque<>();

    private void executeAndRecord(EditCommand command) {
        command.execute();
        history.push(command);
        /* ここを追加（ここから） */

        if (history.size() > MAX_HISTORY) {
            history.removeLast();
        }
        /* ここを追加（ここまで） */
    }
}
```

※正しい実装から追加した `MAX_HISTORY` 定数と、修正した `executeAndRecord` メソッド、それが使う `history` フィールドのみを記載し、他のフィールド・メソッドは変更がないため省略しています。また、`MAX_HISTORY` の `50` は説明のための値です。実際には、何回まで戻せれば十分かに応じて決めます。

`Deque` インターフェースの `push` メソッドで積んだ操作は履歴の先頭に入るため、最も古い操作は末尾にあります。そのため、上限（`MAX_HISTORY`）を超えたら `Deque` インターフェースの `removeLast` メソッドで末尾の 1 つを捨てることにより、履歴は常に新しいものから 50 件までに保たれるようになります。<br>
デメリットとして、上限を超えて捨てた古い操作は、取り消せなくなることが挙げられます。

<a id="深堀り5"></a>

## 【深堀り⑤】Java 標準ライブラリにおける Command パターンの例

Command パターンは、Java の標準ライブラリにも使われています。代表的なのが、`javax.swing.undo` パッケージと、それを使う Swing（Java で画面を作るためのライブラリ）のテキスト入力欄です。

まず、取り消せる操作を表すインターフェースから見ていきましょう。

**`UndoableEdit.java`（一部抜粋）**

```java
package javax.swing.undo;

public interface UndoableEdit {

    public void undo() throws CannotUndoException;

    public void redo() throws CannotRedoException;

    public String getUndoPresentationName();

    public String getRedoPresentationName();
}
```

> 引用元: OpenJDK [UndoableEdit.java](https://github.com/openjdk/jdk/blob/master/src/java.desktop/share/classes/javax/swing/undo/UndoableEdit.java)

`UndoableEdit` インターフェースは、本記事の `EditCommand` インターフェースにあたり、操作を取り消すための抽象メソッド `undo` を持っています。その他の抽象メソッドは、画面に表示する操作の名前を返すものや、本記事では扱っていない「やり直す」のためのものです。

次に、このインターフェースを実装したクラスを見ていきましょう。

**`GapContent.java`（一部抜粋）**

```java
package javax.swing.text;

public class GapContent extends GapVector implements AbstractDocument.Content, Serializable {

    class InsertUndo extends AbstractUndoableEdit {
        protected InsertUndo(int offset, int length) {
            super();
            this.offset = offset;
            this.length = length;
        }

        public void undo() throws CannotUndoException {
            super.undo();
            try {
                // Get the Positions in the range being removed.
                posRefs = getPositionsInRange(null, offset, length);
                string = getString(offset, length);
                remove(offset, length);
            } catch (BadLocationException bl) {
              throw new CannotUndoException();
            }
        }

        protected int offset;
        protected int length;
    } // GapContent.InsertUndo
}
```

> 引用元: OpenJDK [GapContent.java](https://github.com/openjdk/jdk/blob/master/src/java.desktop/share/classes/javax/swing/text/GapContent.java)

テキスト入力欄の文字を管理する `GapContent` クラスの中にある `InsertUndo` クラスは、「文字を挿入する」操作を表すクラスです。本記事の `AddTargetDepartmentCommand` クラスにあたり、`UndoableEdit` インターフェースを実装した `AbstractUndoableEdit` クラスを継承しています。<br>
テキスト入力欄に文字を挿入するたびに、挿入した位置（`offset`）と長さ（`length`）を持たせた `InsertUndo` オブジェクトが作られます。そして、`undo` メソッドでは、保持している位置と長さの範囲の文字を `remove` メソッドで削除して、挿入を取り消しています。これは、`AddTargetDepartmentCommand` クラスが追加した部署を保持し、`undo` メソッドでその部署を削除するのと同じ流れです。どちらも、取り消すために必要な情報を、操作を表すオブジェクト自身が持っています。

なお、`posRefs`・`string` フィールドへの代入は、「やり直す」のために削除する文字などを残しておく処理です。

最後に、操作を表すオブジェクトを履歴に積んで取り消すクラスを見ていきましょう。

**`UndoManager.java`（一部抜粋）**

```java
package javax.swing.undo;

public class UndoManager extends CompoundEdit implements UndoableEditListener {

    protected void undoTo(UndoableEdit edit) throws CannotUndoException {
        boolean done = false;
        while (!done) {
            UndoableEdit next = edits.elementAt(--indexOfNextAdd);
            next.undo();
            done = next == edit;
        }
    }
}
```

> 引用元: OpenJDK [UndoManager.java](https://github.com/openjdk/jdk/blob/master/src/java.desktop/share/classes/javax/swing/undo/UndoManager.java)

`UndoManager` クラスは、本記事の `ArticleEditor` クラスにあたり、操作を表すオブジェクトを履歴（`edits` フィールド）に保持しています。<br>
`UndoManager` クラスの `undo` メソッドを呼び出したときに実行される `undoTo` メソッドでは、履歴から取得した操作を `UndoableEdit` 型として扱っているため、`ArticleEditor` クラスの `undo` メソッドと同じく、操作の種類に関係なく `undo` メソッドを呼び出して、取り消しを委譲しています。

なお、`while` ループは、取り消しの単位として数えない細かな操作（`isSignificant` メソッドが `false` を返す操作）があった場合に、それもまとめて取り消すためのものです。Command パターンの理解には関係しない部分なので、読み飛ばして構いません。

Swing のテキスト入力欄（`JTextArea` クラスなど）を使ってアプリを作る場合は、`textArea.getDocument().addUndoableEditListener(undoManager)` のように `UndoManager` オブジェクトを登録し、「元に戻す」ボタンなどから `undoManager.undo()` を呼び出すだけで、入力した文字を元に戻せるようになります。これは、文字を入力するたびに、テキスト入力欄が `InsertUndo` オブジェクトを含む `UndoableEdit` オブジェクトを作り、`UndoManager` クラスの履歴に積むためです。

このように、Command パターンは Java の標準ライブラリで使われているため、テキスト入力欄に「元に戻す」機能を付ける開発者は、意識しないうちにこのパターンを使っていることになります。

<a id="深堀り6"></a>

## 【深堀り⑥】GoF デザインパターンとの位置づけ

今回使った Command パターンは、GoF（Gang of Four）の 23 のデザインパターンのうち「振る舞いパターン」に分類されます。<br>
詳しくは「GoF」で検索してみてください。
