# Command（コマンド）パターン ― 操作をオブジェクトとして表し、記録・取り消しを可能にする

次のような経験をしたことはありませんか？

> 操作を取り消せるようにするために、行った操作の種類と内容を記録しておき、取り消すときに操作の種類ごとに分岐して逆の処理を書いていたら、操作を 1 つ追加するたびに、記録する項目と、取り消すための分岐を書き足す羽目になった。おまけに、分岐を書き足し忘れて、取り消したはずの操作が実際には元に戻っていない不具合を生んだこともあった。

この記事では、社内ポータルのお知らせ記事の編集画面に「1 つ前に戻す」機能を追加するシナリオを通して、Command パターンがこの問題をどのように解決するかを紹介します。

## 目次

- [【具体例】](#具体例)
    - [シナリオ](#シナリオ)
    - [既存コードの仕様](#既存コードの仕様)
- [好ましくない実装](#好ましくない実装)
- [正しい実装](#正しい実装)
- [まとめ](#まとめ)
- [【深堀り①】Memento パターンとの違い](#深堀り1)
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
> 現在、お知らせ記事の編集画面では、本文の編集や公開先の部署の追加・削除ができます。しかし、操作を元に戻す機能は実装されていないため、誤って本文や公開先の部署を書き換えてしまうと、記事の担当者が記憶を頼りに手動で直すしかない状態です。<br>
> 先日、人事部向けの記事の公開先を変更している途中で、誤って別の部署を追加してしまいました。記憶を頼りに手動で直したものの削除し忘れがあり、他の部署にも公開されかける、というヒヤリハットがありました。<br>
> これを受けて、あなたは編集画面に「1 つ前に戻す」機能を追加することになりました。直前の操作を取り消し、続けて使うと、さらに前の操作も順に取り消せるようにします。<br>
> また、どの操作を取り消したのかを担当者が確かめられるように、操作後の記事の内容と一緒に、取り消した操作の内容（「公開先に経理部を追加」など）を表示します。

※実際のお知らせ記事の編集画面では、画面への表示や、記事をデータベース（DB）へ保存する処理を行う実装が必要ですが、本記事では Command パターンの解説に集中するため、コンソールへの文字列出力のみとします。

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

| メソッド                 | 引数                | 戻り値の型 | 説明                                                                                           |
| ------------------------ | ------------------- | ---------- | ---------------------------------------------------------------------------------------------- |
| `changeBody`             | `String body`       | `void`     | 本文を変更する                                                                                 |
| `addTargetDepartment`    | `String department` | `void`     | 公開先に部署を追加する<br>すでに追加されている部署の場合は `IllegalArgumentException` を投げる |
| `removeTargetDepartment` | `String department` | `void`     | 公開先から部署を削除する                                                                       |
| `getBody`                | なし                | `String`   | 本文を取得する                                                                                 |
| `getSummary`             | なし                | `String`   | 本文と公開先をまとめた表示用の文字列を返す                                                     |

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

経理部を誤って追加した場合、担当者は、何を追加したかを思い出して、自分で削除し直すしかありません。

※ここで一旦読むのを止めて、ご自身でコーディングを行なってみてください。その後で、続きを読んでください。

## 好ましくない実装

では、シナリオに従い追加実装をしていきましょう。

真っ先に思いつくのは、編集画面のクラス（`ArticleEditor`）で、操作のたびに操作の種類と内容（追加・削除した部署や、変更前の本文）を記録しておき、1 つ前に戻すときに、記録した操作の種類に応じて処理を分ける、という実装ではないでしょうか？

※`Article` クラスに変更はありません。

**`EditRecord.java`**

```java
package example;

public class EditRecord {
    private String type;
    private String department;
    private String oldBody;

    public EditRecord(String type, String department, String oldBody) {
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
    private Deque<EditRecord> history = new ArrayDeque<>(); // ←ここを追加

    public ArticleEditor(Article article) {
        this.article = article;
    }

    public void changeBody(String body) {
        String oldBody = article.getBody(); // ←ここを追加
        article.changeBody(body);
        history.push(new EditRecord("CHANGE_BODY", null, oldBody)); // ←ここを追加

        System.out.println("[本文を変更] " + article.getSummary());
    }

    public void addTargetDepartment(String department) {
        article.addTargetDepartment(department);
        history.push(new EditRecord("ADD_DEPARTMENT", department, null)); // ←ここを追加

        System.out.println("[公開先を追加] " + article.getSummary());
    }

    public void removeTargetDepartment(String department) {
        article.removeTargetDepartment(department);
        history.push(new EditRecord("REMOVE_DEPARTMENT", department, null)); // ←ここを追加

        System.out.println("[公開先を削除] " + article.getSummary());
    }

    /* ここを追加（ここから） */
    public void undo() {
        if (history.isEmpty()) {
            System.out.println("[1つ前に戻す] 戻せる操作がありません");

            return;
        }

        EditRecord record = history.pop();
        switch (record.getType()) {
            case "CHANGE_BODY" -> article.changeBody(record.getOldBody());
            case "ADD_DEPARTMENT" -> article.removeTargetDepartment(record.getDepartment());
            case "REMOVE_DEPARTMENT" -> article.addTargetDepartment(record.getDepartment());
        }

        System.out.println("[1つ前に戻す：" + describe(record) + "] " + article.getSummary());
    }

    private String describe(EditRecord record) {
        return switch (record.getType()) {
            case "CHANGE_BODY" -> "本文を変更";
            case "ADD_DEPARTMENT" -> "公開先に" + record.getDepartment() + "を追加";
            case "REMOVE_DEPARTMENT" -> "公開先から" + record.getDepartment() + "を削除";
            default -> throw new IllegalArgumentException("記録されていない操作です：" + record.getType());
        };
    }
    /* ここを追加（ここまで） */
}
```

※追加した処理について、次の 2 点を補足します。

> - `EditRecord` クラスの `type` フィールドには、操作の種類を表す文字列（`"CHANGE_BODY"`・`"ADD_DEPARTMENT"`・`"REMOVE_DEPARTMENT"`）が入ります。操作によって使うフィールドが異なり、本文の変更では `oldBody` フィールドを、公開先の部署の追加・削除では `department` フィールドを使います。
> - `history` フィールドは、`Deque` インターフェースをスタック（後から入れたものを先に取り出す入れ物）として使っており、`push` メソッドで積んだ記録を、`pop` メソッドで新しいものから順に取り出します。

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
[1つ前に戻す：公開先に経理部を追加] 本文：新しい評価制度の説明会を開催します。／公開先：[人事部]
[公開先を追加] 本文：新しい評価制度の説明会を開催します。／公開先：[人事部, 総務部]
[本文を変更] 本文：新しい評価制度の説明会を4月に開催します。／公開先：[人事部, 総務部]
[1つ前に戻す：本文を変更] 本文：新しい評価制度の説明会を開催します。／公開先：[人事部, 総務部]
```

1 つ前に戻すときに、操作後の記事の内容と一緒に、取り消した操作の内容が表示されるようになっています。

コンパイルエラーがなく結果が出力されていることから、一見すると実装・動作確認ともに問題ないように見えます。

しかし、この実装には以下の問題点があります。

- 1 つの操作に関する処理が、操作を行うメソッド（`changeBody` メソッドなど）と、取り消す `undo` メソッド、操作の内容を表示する `describe` メソッドに分かれて書かれている。そのため、操作を 1 つ追加する（例えば「公開先の部署をすべて削除」）たびに、操作を行うメソッドとは別に、`undo`・`describe` メソッドの switch 文にも分岐を書き足す必要がある。
    - その結果、`undo` メソッドへの書き足しを忘れると、コンパイルエラーも例外も発生しないまま、「1 つ前に戻す」と表示されるのに、公開先の部署が元に戻らない不具合が生まれてしまう。
- 操作ごとに必要な情報が異なるにもかかわらず、すべての操作の情報を `EditRecord` クラス 1 つで保持している。そのため、操作を追加するたびに、`EditRecord` クラスにフィールド（例えば「公開先の部署をすべて削除」であれば、削除した部署のリスト）を追加する必要がある。コンストラクターの引数も増えるため、既存の操作を記録している箇所もすべて修正しなければならず、各操作で使わないフィールドには `null` を渡すことになる。
    - その結果、コンストラクターに渡す引数の位置を間違えても（部署名を `oldBody` の位置に渡す、など）、どちらも `String` 型のためコンパイルエラーにならず、取り消したときに正しく元に戻らないリスクがある。

## 正しい実装

では、好ましくない実装で挙げた問題点を解決するにはどうすればよいのでしょうか？

これらの問題を解決するのが **Command パターン**です。<br>
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

`EditCommand` は新たに追加したインターフェースで、操作を実行する抽象メソッド `execute`、操作を取り消す抽象メソッド `undo`、操作の内容を表す文字列を返す抽象メソッド `getDescription` を持ちます。

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
        return "本文を変更";
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
        return "公開先に" + department + "を追加";
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
        return "公開先から" + department + "を削除";
    }
}
```

`ChangeBodyCommand`・`AddTargetDepartmentCommand`・`RemoveTargetDepartmentCommand` は新たに追加したクラスで、それぞれ本文を変更する操作、公開先に部署を追加する操作、公開先から部署を削除する操作を表します。<br>
いずれのクラスも、`execute` メソッドにより操作を実行し、`undo` メソッドによりその操作を取り消し、`getDescription` メソッドにより「1 つ前に戻す」で表示する操作の内容を返しています。<br>
取り消すために必要な情報は、それぞれのクラスがフィールドとして持っています。`ChangeBodyCommand` クラスは、`execute` メソッドで本文を変更する直前に、変更前の本文を `oldBody` フィールドに保持しています。`AddTargetDepartmentCommand`・`RemoveTargetDepartmentCommand` クラスは、追加・削除する部署を `department` フィールドに保持しています（→ [【深堀り②】Strategy パターンとの違い](#深堀り2)）。

次に、操作を表すオブジェクトを使う側のクラスを見ていきましょう。

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
        execute(new ChangeBodyCommand(article, body)); // ←ここを修正

        System.out.println("[本文を変更] " + article.getSummary());
    }

    public void addTargetDepartment(String department) {
        execute(new AddTargetDepartmentCommand(article, department)); // ←ここを修正

        System.out.println("[公開先を追加] " + article.getSummary());
    }

    public void removeTargetDepartment(String department) {
        execute(new RemoveTargetDepartmentCommand(article, department)); // ←ここを修正

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

        System.out.println("[1つ前に戻す：" + command.getDescription() + "] " + article.getSummary());
    }

    private void execute(EditCommand command) {
        command.execute();
        history.push(command);
    }
    /* ここを追加（ここまで） */
}
```

`ArticleEditor` クラスを振り返ると、既存の仕様から `history` フィールドと `undo`・`execute` メソッドを追加し、`changeBody`・`addTargetDepartment`・`removeTargetDepartment` メソッドでは、`Article` クラスのメソッドを直接呼び出す代わりに、操作を表すオブジェクトを作って `execute` メソッドに渡すように修正しています。<br>
`history` フィールドにより、実行した操作をオブジェクトのまま積み重ねて保持できるようにしています（→ [【深堀り①】Memento パターンとの違い](#深堀り1)）。<br>
`execute` メソッドにより、受け取った操作を実行してから `history` フィールドに積んでいます。<br>
`undo` メソッドにより、`history` フィールドから最後に積んだ操作を取り出し、その操作の `undo` メソッドで取り消しています。表示する操作の内容は、取り出した操作の `getDescription` メソッドから取得しています。

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
[1つ前に戻す：公開先に経理部を追加] 本文：新しい評価制度の説明会を開催します。／公開先：[人事部]
[公開先を追加] 本文：新しい評価制度の説明会を開催します。／公開先：[人事部, 総務部]
[本文を変更] 本文：新しい評価制度の説明会を4月に開催します。／公開先：[人事部, 総務部]
[1つ前に戻す：本文を変更] 本文：新しい評価制度の説明会を開催します。／公開先：[人事部, 総務部]
```

`Main` クラスは、好ましくない実装と同じです。

実行結果は、好ましくない実装とまったく同じになっています。

以上のような実装を行うと、以下のメリットがあります。

- 1 つの操作に関する処理（実行・取り消し・操作の内容の表示）が、その操作を表すクラスにまとまっており、`ArticleEditor` クラスの `undo` メソッドは、操作の種類に関係なく `EditCommand` インターフェースのメソッドを呼び出すだけになっている。そのため、操作を追加しても、`EditCommand` インターフェースを実装したクラスを 1 つ追加すればよく、`undo` メソッドには手を加える必要がない。
    - その結果、追加したクラスに取り消す処理を書き忘れるとコンパイルエラーになるため、「1 つ前に戻す」と表示されるのに元に戻らない不具合に気づかないまま動いてしまう、ということがなくなる。
- 各操作に必要な情報を、その操作を表すクラスだけがフィールドとして持っているため、操作を追加しても、既存のクラスにフィールドを追加したり、既存の操作を作っている箇所を修正したりする必要がない。また、使わないフィールドに `null` を渡すこともない。
    - その結果、引数の位置を間違えて、取り消したときに正しく元に戻らない、ということが起きにくい。

## まとめ

正しい実装を振り返ると、本文の変更や公開先の部署の追加・削除といった編集操作を、それぞれ `EditCommand` インターフェースを実装したクラスとして表し、実行・取り消し・操作の内容の表示を、そのクラス自身に持たせています。`ArticleEditor` クラスは、実行した操作のオブジェクトを履歴に積んでおき、1 つ前に戻すときは、操作の種類を気にせず `undo` メソッドを呼び出すだけでよくなっています。<br>
このように Command パターンは、操作そのものをオブジェクトとして表すことで、操作を履歴に残したり取り消したりといった扱いを、操作の種類に関係なく同じ形で行えるようにする設計パターンです。

本記事の内容はここまでとなります。

以降は「もう少し深く知りたい」という方向けの補足となります。今回学んだパターンに繋がる設計原則や、実務で役立つ背景知識について触れています。

---

<a id="深堀り1"></a>

## 【深堀り①】Memento パターンとの違い

正しい実装の `ArticleEditor` クラスを振り返ると、実行した操作を `history` フィールドに積んでおき、1 つ前に戻すときは、取り出した操作の `undo` メソッドを呼び出していました。<br>
この「履歴を積んでおき、取り出して元に戻す」という構造は、Memento パターンとよく似ています。

ただし、2 つのパターンでは、履歴に残すものが異なります。

Memento パターンでは、操作の直前の状態を丸ごと残します。そのため、戻すときは、操作の種類に関係なく、残しておいた状態をそのまま書き戻します。<br>
例えば、本記事の編集画面であれば、`Article` クラスに、本文と公開先の部署を保持するオブジェクト（Memento）を作る `createMemento` メソッドと、Memento から状態を書き戻す `restoreMemento` メソッドを追加します。そして、`ArticleEditor` クラスの各編集操作のメソッドで、操作の直前に `createMemento` メソッドで作った Memento を履歴に積み、1 つ前に戻すときは、履歴から取り出した Memento を `restoreMemento` メソッドに渡します。

Command パターンでは、行った操作の内容を残します。そのため、戻すときは、操作ごとに用意した取り消し方で戻します。<br>
例えば、本記事の `AddTargetDepartmentCommand` クラスは、どの部署を追加したかだけを保持し、`undo` メソッドでその部署を削除して元に戻しています。

このように 2 つのパターンは、履歴を積んで元に戻す構造が似ていても、残すものが、状態なのか操作なのかで異なります。<br>
Memento パターンは、状態を丸ごと残すため、操作ごとに取り消し方を書く必要がありません。ただし、残す状態が大きいほど、操作のたびに多くのメモリを使います。<br>
Command パターンは、操作の内容だけを残すため、多くの操作では残す量が少なくて済みます。ただし、操作ごとに取り消し方を書く必要があり、単純に逆の操作をするだけでは元に戻らない操作もあります。<br>
例えば、本文中の「2025 年」をすべて「2026 年」に置き換える操作を追加したとします。取り消すときに「2026 年」をすべて「2025 年」に置き換え直すと、置き換える前から本文にあった「2026 年」まで「2025 年」になってしまいます。正しく戻すには、変更前の本文を丸ごと保持しておく必要があります。

どちらのパターンを使うかは、残す状態の大きさと、操作ごとに取り消し方を書けるかどうかで判断するとよいでしょう。<br>
本記事の編集画面では、取り消した操作の内容を表示する必要があり、操作そのものを残しておけば `getDescription` メソッドでその内容を取得できるため、Command パターンが向いています。<br>
反対に、操作の種類が多く、取り消し方を 1 つずつ書くのが難しい場合は、Memento パターンを検討するのが適切です。<br>
また、実務では、2 つのパターンを組み合わせることもあります。例えば、先ほどの置き換える操作のように、取り消す処理を書きにくい操作では、Command に操作前の状態の Memento を持たせておき、`undo` メソッドでその Memento を使って戻す、という形です。<br>
詳しくは「Memento パターン」で検索してみてください。

<a id="深堀り2"></a>

## 【深堀り②】Strategy パターンとの違い

正しい実装の `ArticleEditor` クラスを振り返ると、`EditCommand` インターフェースを実装したクラスのオブジェクトを受け取り、そのオブジェクトの `execute`・`undo` メソッドに処理を任せていました。<br>
この「共通のインターフェースを実装したクラスを用意し、処理を任せる」という構造は、Strategy パターンとよく似ています。

ただし、2 つのパターンでは、オブジェクトが表すものが異なります。

Strategy パターンでは、オブジェクトは処理のやり方を表します。そのため、1 つのオブジェクトを保持し続け、同じやり方で何度も処理を任せます。<br>
例えば、お知らせ記事の一覧の並べ方として「新しい順」と「閲覧数の多い順」を、それぞれ `ArticleSorter` インターフェースを実装したクラスとして用意した場合は、一覧を表すクラスがどちらか一方のオブジェクトをフィールドに保持し、一覧を表示するたびに、そのオブジェクトの `sort` メソッドを呼び出します。

Command パターンでは、オブジェクトは 1 回分の操作を表します。そのため、操作のたびに新しいオブジェクトを作り、実行した後も履歴に残しておきます。<br>
例えば、本記事では、公開先に部署を追加するたびに `AddTargetDepartmentCommand` クラスのオブジェクトを作り、追加する部署を持たせています。人事部を追加したオブジェクトと経理部を追加したオブジェクトは別のもので、どちらも `history` フィールドに積まれます。

このように 2 つのパターンは、共通のインターフェースを実装したクラスに処理を任せる構造が似ていても、オブジェクトが表すものが、処理のやり方なのか、1 回分の操作なのかで異なります。<br>
どちらのパターンを使うかは、同じやり方で何度も処理したいのか、行った操作を後から取り消したり記録したりしたいのかで判断するとよいでしょう。<br>
本記事の編集画面では、行った操作を履歴に残して取り消すため、Command パターンが向いています。<br>
反対に、場面に応じて処理のやり方を差し替えたい場合は、Strategy パターンを検討するのが適切です。<br>
詳しくは「Strategy パターン」で検索してみてください。

<a id="深堀り3"></a>

## 【深堀り③】複数の操作をまとめて取り消す（Composite パターンとの組み合わせ）

正しい実装では、1 回の操作で公開先に追加できる部署は 1 つでした。ここでは、「管理部門」（人事部・総務部・経理部）のように、いくつかの部署をまとめて公開先に追加する操作を足すことを考えます。

まとめて追加した部署を 1 つずつ取り消すのでは、「1 つ前に戻す」を部署の数だけ繰り返すことになります。まとめて追加した操作は、1 回の「1 つ前に戻す」でまとめて取り消せるのが自然です。<br>
そこで、複数の操作をまとめて 1 つの操作として扱う `MacroCommand` クラスを追加します。

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
`add` メソッドにより、まとめる操作を追加しています。<br>
`execute` メソッドにより、まとめた操作を追加した順に実行しています。<br>
`undo` メソッドにより、まとめた操作を、実行したときとは逆の順番で取り消しています。逆の順番にしているのは、後から実行した操作が、先に実行した操作の結果を前提にしているためです。例えば、本文を 2 回変更する操作をまとめた場合、先に実行した操作から取り消すと、1 回目の取り消しで元の本文に戻した後、2 回目の取り消しで 1 回目の変更後の本文に戻してしまい、最終的に元の本文に戻りません。

**`ArticleEditor.java`（一部抜粋）**

```java
package example;

public class ArticleEditor {
    private Article article;

    /* ここを追加（ここから） */
    public void addTargetDepartments(String groupName, List<String> departments) {
        MacroCommand macroCommand = new MacroCommand("公開先に" + groupName + "を追加");
        for (String department: departments) {
            macroCommand.add(new AddTargetDepartmentCommand(article, department));
        }
        execute(macroCommand);

        System.out.println("[公開先をまとめて追加] " + article.getSummary());
    }
    /* ここを追加（ここまで） */
}
```

※正しい実装から追加した `addTargetDepartments` メソッドと、それが使う `article` フィールドのみを記載し、他のフィールド・メソッドは省略しています。

`ArticleEditor` クラスを振り返ると、正しい実装から `addTargetDepartments` メソッドを追加しています。<br>
`addTargetDepartments` メソッドにより、受け取った部署ごとに `AddTargetDepartmentCommand` クラスのオブジェクトを作って `MacroCommand` クラスのオブジェクトにまとめ、他の操作と同じく `execute` メソッドに渡しています。

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
[1つ前に戻す：公開先に管理部門を追加] 本文：人事評価の面談日程をお知らせします。／公開先：[]
[公開先を追加] 本文：人事評価の面談日程をお知らせします。／公開先：[人事部]
```

`[1つ前に戻す：公開先に管理部門を追加]` の行では、1 回の「1 つ前に戻す」で、まとめて追加した 3 つの部署がすべて取り消されています。

`MacroCommand` クラス自身も `EditCommand` インターフェースを実装しているため、`ArticleEditor` クラスの `undo` メソッドは、まとめた操作も 1 つの操作と区別せずに扱えます。実際に、`undo` メソッドには手を加えていません。<br>
この「1 つのものと、それらをまとめたものを、同じインターフェースで扱う」構造は、Composite パターンそのものです。`MacroCommand` クラスには `MacroCommand` クラスのオブジェクトもまとめられるため、まとめた操作をさらにまとめることもできます。<br>
詳しくは「Composite パターン」で検索してみてください。

※`MacroCommand` クラスの `execute` メソッドは、途中の操作で例外が発生すると（例えば、すでに公開先にある部署を含めてまとめて追加しようとした場合）、それまでに実行した操作が取り消されないまま残ります。実務では、例外が発生したら、それまでに実行した操作を逆の順番で取り消してから例外を投げ直す処理を加えます。

<a id="深堀り4"></a>

## 【深堀り④】履歴が増え続けたときの上限

正しい実装の `ArticleEditor` クラスを振り返ると、操作のたびに、その操作を表すオブジェクトを `history` フィールドに積んでいます。1 つ前に戻さない限り履歴は減らないため、編集を続けるほどメモリの消費は増え続けます。<br>
また、公開先の部署の追加・削除を表すオブジェクトが保持するのは部署名だけですが、`ChangeBodyCommand` クラスのオブジェクトは、変更前と変更後の本文を丸ごと保持しています。そのため、長い本文を何度も書き換えるほど、オブジェクト 1 つあたりのメモリも大きくなります。

そこで実務では、履歴に上限を設け、上限を超えたら古いものから捨てることがあります。

**`ArticleEditor.java`（一部抜粋）**

```java
package example;

public class ArticleEditor {
    private static final int MAX_HISTORY = 50; // ←ここを追加
    private Deque<EditCommand> history = new ArrayDeque<>();

    private void execute(EditCommand command) {
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

※正しい実装から追加した `MAX_HISTORY` 定数と、修正した `execute` メソッド、それが使う `history` フィールドのみを記載し、他のフィールド・メソッドは省略しています。また、`MAX_HISTORY` の `50` は説明のための値です。実際には、何回まで戻せれば十分かに応じて決めます。

`Deque` インターフェースの `push` メソッドで積んだ操作は履歴の先頭に入るため、最も古い操作は末尾にあります。そのため、上限（`MAX_HISTORY`）を超えたら `Deque` インターフェースの `removeLast` メソッドで末尾の 1 つを捨てることにより、履歴は常に新しいものから 50 件までに保たれるようになります。<br>
デメリットとして、上限を超えて捨てた古い操作は、取り消せなくなることが挙げられます。

<a id="深堀り5"></a>

## 【深堀り⑤】Java 標準ライブラリにおける Command パターンの例

Java の標準ライブラリでは、`javax.swing.undo` パッケージと、それを使う Swing（Java で画面を作るためのライブラリ）のテキスト入力欄が、Command パターンの具体例として挙げられます。

まず、取り消せる操作を表すインターフェースを見ていきましょう。

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

`UndoableEdit` インターフェースは、本記事の `EditCommand` インターフェースにあたります。抽象メソッド `undo` は本記事の抽象メソッド `undo` に、「元に戻す」で表示する操作の内容を返す抽象メソッド `getUndoPresentationName` は本記事の抽象メソッド `getDescription` にあたります。抽象メソッド `redo`・`getRedoPresentationName` は、本記事では扱っていない「やり直す」のためのものです。

次に、このインターフェースを実装したクラスを見ていきましょう。テキスト入力欄の文字を管理する `GapContent` クラスの中に、文字を挿入する操作を表す `InsertUndo` クラスがあります。

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

`InsertUndo` クラスは、`UndoableEdit` インターフェースを実装した `AbstractUndoableEdit` クラスを継承しており、本記事の `AddTargetDepartmentCommand` クラスにあたります。<br>
テキスト入力欄に文字を挿入するたびに、挿入した位置（`offset`）と長さ（`length`）を持たせた `InsertUndo` オブジェクトが作られます。そして、`undo` メソッドでは、保持している位置と長さの範囲の文字を `remove` メソッドで削除して、挿入を取り消しています。「追加した部署を保持し、`undo` メソッドでその部署を削除する」`AddTargetDepartmentCommand` クラスと同じく、取り消すために必要な情報を、操作を表すオブジェクト自身が持っています。

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

`UndoManager` クラスは、本記事の `ArticleEditor` クラスの `history` フィールドと `undo` メソッドにあたります。<br>
`undoTo` メソッドでは、履歴（`edits` フィールド）から操作を取り出し、その操作の `undo` メソッドを呼び出して取り消しています。取り出した操作は `UndoableEdit` 型として扱っており、具体的にどのクラスのオブジェクトなのかを区別していません。本記事の `ArticleEditor` クラスの `undo` メソッドと同じく、操作の種類を気にせず `undo` メソッドを呼び出すだけです。

Swing のテキスト入力欄（`JTextArea` クラスなど）では、`textArea.getDocument().addUndoableEditListener(undoManager)` のように `UndoManager` オブジェクトを登録するだけで、入力した文字を元に戻せるようになります。文字を入力するたびに、テキスト入力欄が `InsertUndo` オブジェクトを含む `UndoableEdit` オブジェクトを作り、`UndoManager` クラスの履歴に積むためです。<br>
このように、テキスト入力欄に「元に戻す」機能を付ける開発者は、意識しないうちに Command パターンを使っていることになります。

<a id="深堀り6"></a>

## 【深堀り⑥】GoF デザインパターンとの位置づけ

今回使った Command パターンは、GoF（Gang of Four）の 23 のデザインパターンのうち「振る舞いパターン」に分類されます。<br>
詳しくは「GoF」で検索してみてください。
