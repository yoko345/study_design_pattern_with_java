# Memento（メメント）パターン ― カプセル化を壊さずにオブジェクトの状態を保存・復元する

次のような経験をしたことはありませんか？

> 編集をキャンセルしたら編集前の状態に戻す、処理の途中で失敗したら元の状態に戻す、といった機能を作るために、呼び出す側でオブジェクトの値を 1 つずつ取り出して退避しておき、戻すときに 1 つずつ書き戻す実装にした。そのせいで、本来は外から自由に書き換えられたくない値まで、どこからでも書き換えられる状態になってしまった。おまけに、後から項目を追加したときに退避と書き戻しの処理を直し忘れて、その項目だけ元に戻らない不具合を生んだこともあった。

この記事では、社内ポータルのお知らせ記事の編集画面に「元に戻す」機能を追加するシナリオを通して、Memento パターンがこの問題をどのように解決するかを紹介します。

## 目次

- [【具体例】](#具体例)
    - [シナリオ](#シナリオ)
    - [既存コードの仕様](#既存コードの仕様)
- [好ましくない実装](#好ましくない実装)
- [正しい実装](#正しい実装)
- [まとめ](#まとめ)
- [【深堀り①】リストをコピーして退避する理由](#深堀り1)
- [【深堀り②】Memento の中身を隠すもう 1 つの方法（ネストクラス）](#深堀り2)
- [【深堀り③】Undo の 2 つの実現方法（Memento と Command）](#深堀り3)
- [【深堀り④】履歴の上限とメモリ](#深堀り4)
- [【深堀り⑤】Memento をファイルに保存する](#深堀り5)
- [【深堀り⑥】GoF デザインパターンとの位置づけ](#深堀り6)

---

## 【具体例】

### シナリオ

> あなたは社内ポータルの開発チームに所属しています。<br>
> 社内ポータルのお知らせ記事は、メールの宛先のように公開先の部署を 1 つずつ追加して、公開する範囲を決めます。<br>
> 現在、お知らせ記事の編集画面では、本文の編集や公開先の部署の設定に加えて、編集中の内容の下書き保存もできます。しかし、操作を元に戻す機能は実装されていないため、誤って本文や公開先の部署を書き換えてしまうと、記事の担当者が記憶を頼りに手動で直すしかない状態です。<br>
> 先日、人事部向けの記事の公開先を変更している途中で、誤って別の部署を追加してしまいました。記憶を頼りに手動で直したものの削除し忘れがあり、ほかの部署にも公開されかける、というヒヤリハットがありました。<br>
> これを受けて、あなたは編集画面に次の 2 つの機能を追加することになりました。
>
> - 「1 つ前に戻す」機能：直前の操作を取り消して、1 つ前の状態に戻す
> - 「下書きに戻す」機能：編集を破棄して、最後に下書き保存した時点に戻す

※実際のお知らせ記事の編集画面では、画面への表示や、下書きをデータベースへ保存する処理を行う実装が必要ですが、本記事では Memento パターンの解説に集中するため、コンソールへの文字列出力のみとします。

### 既存コードの仕様

※実務では、次の `Article` のようなエンティティクラスは `entity` パッケージなど専用のディレクトリに切り出すのが一般的です。しかし、本記事ではパッケージ構成を主題としないため `example` パッケージ直下にまとめています。

- `Article`（既存クラス）

お知らせ記事 1 件を表すクラスです。<br>
記事の本文と公開先の部署を保持します。<br>
公開先に部署を追加するときは、すでに追加されている部署ではないかをチェックしています。また、下書き保存の前に呼び出す `validate` メソッドで、本文が入力され、公開先の部署が 1 つ以上設定されているかをチェックしています。

| フィールド          | 型             | 説明         |
| ------------------- | -------------- | ------------ |
| `body`              | `String`       | 本文         |
| `targetDepartments` | `List<String>` | 公開先の部署 |

| メソッド                 | 引数                | 戻り値の型 | 説明                                                                                                                                               |
| ------------------------ | ------------------- | ---------- | -------------------------------------------------------------------------------------------------------------------------------------------------- |
| `changeBody`             | `String body`       | `void`     | 本文を変更する                                                                                                                                     |
| `addTargetDepartment`    | `String department` | `void`     | 公開先に部署を追加する<br>すでに追加されている部署の場合は `IllegalArgumentException` を投げる                                                     |
| `removeTargetDepartment` | `String department` | `void`     | 公開先から部署を削除する                                                                                                                           |
| `validate`               | なし                | `void`     | 下書き保存できる状態かをチェックする<br>本文が入力されていない、または公開先の部署が 1 つも設定されていない場合は `IllegalStateException` を投げる |
| `getSummary`             | なし                | `String`   | 本文と公開先をまとめた表示用の文字列を返す                                                                                                         |

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

    public void validate() {
        if (body.isBlank() || targetDepartments.isEmpty()) {
            throw new IllegalStateException("本文と公開先の部署をどちらも入力してから、下書き保存してください");
        }
    }

    public String getSummary() {
        return "本文：" + body + "／公開先：" + targetDepartments;
    }
}
```

<br>

- `ArticleEditor`（既存クラス）

お知らせ記事の編集画面を表すクラスです。<br>
記事の新規作成でも、この編集画面を使います。<br>
本文の変更、公開先の追加・削除、下書き保存の操作を受け付け、操作後の記事の内容をコンソールに出力します。

| フィールド | 型        | 説明                 |
| ---------- | --------- | -------------------- |
| `article`  | `Article` | 編集中のお知らせ記事 |

| メソッド                 | 引数                | 戻り値の型 | 説明                                                                                           |
| ------------------------ | ------------------- | ---------- | ---------------------------------------------------------------------------------------------- |
| `changeBody`             | `String body`       | `void`     | 記事の本文を変更し、変更後の内容をコンソールに出力する                                         |
| `addTargetDepartment`    | `String department` | `void`     | 記事の公開先に部署を追加し、追加後の内容をコンソールに出力する                                 |
| `removeTargetDepartment` | `String department` | `void`     | 記事の公開先から部署を削除し、削除後の内容をコンソールに出力する                               |
| `saveDraft`              | なし                | `void`     | 記事が下書き保存できる状態かをチェックしてから下書き保存し、保存した内容をコンソールに出力する |

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

    public void saveDraft() {
        article.validate();

        System.out.println("[下書き保存] " + article.getSummary());
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

        editor.changeBody("評価制度の見直しを検討しています。");
        editor.addTargetDepartment("人事部");
        editor.saveDraft();

        System.out.println();

        editor.changeBody("新しい評価制度の説明会を開催します。");
        editor.addTargetDepartment("総務部");
        editor.removeTargetDepartment("総務部");
        // 誤って削除してしまったため、手動で追加し直している
        editor.addTargetDepartment("総務部");
        editor.saveDraft();
    }
}
```

**実行結果**

```
[本文を変更] 本文：評価制度の見直しを検討しています。／公開先：[]
[公開先を追加] 本文：評価制度の見直しを検討しています。／公開先：[人事部]
[下書き保存] 本文：評価制度の見直しを検討しています。／公開先：[人事部]

[本文を変更] 本文：新しい評価制度の説明会を開催します。／公開先：[人事部]
[公開先を追加] 本文：新しい評価制度の説明会を開催します。／公開先：[人事部, 総務部]
[公開先を削除] 本文：新しい評価制度の説明会を開催します。／公開先：[人事部]
[公開先を追加] 本文：新しい評価制度の説明会を開催します。／公開先：[人事部, 総務部]
[下書き保存] 本文：新しい評価制度の説明会を開催します。／公開先：[人事部, 総務部]
```

※ここで一旦読むのを止めて、ご自身でコーディングを行なってみてください。その後で、続きを読んでください。

## 好ましくない実装

では、シナリオに従い追加実装をしていきましょう。

すぐに思いつくのは、編集画面のクラス（`ArticleEditor`）で、各操作の直前に記事の本文と公開先の部署を取り出して退避しておき、元に戻すときにそれらを記事へ書き戻す、という実装ではないでしょうか？

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

    public void validate() {
        if (body.isBlank() || targetDepartments.isEmpty()) {
            throw new IllegalStateException("本文と公開先の部署をどちらも入力してから、下書き保存してください");
        }
    }

    /* ここを追加（ここから） */
    public String getBody() {
        return body;
    }

    public List<String> getTargetDepartments() {
        return targetDepartments;
    }

    public void setTargetDepartments(List<String> targetDepartments) {
        this.targetDepartments = targetDepartments;
    }
    /* ここを追加（ここまで） */

    public String getSummary() {
        return "本文：" + body + "／公開先：" + targetDepartments;
    }
}
```

**`ArticleEditor.java`**

```java
package example;

public class ArticleEditor {
    private Article article;
    /* ここを追加（ここから） */
    private Deque<String> bodyHistory = new ArrayDeque<>();
    private Deque<List<String>> targetDepartmentsHistory = new ArrayDeque<>();
    private String draftBody;
    private List<String> draftTargetDepartments;
    /* ここを追加（ここまで） */

    public ArticleEditor(Article article) {
        this.article = article;
        /* ここを追加（ここから） */
        draftBody = article.getBody();
        draftTargetDepartments = new ArrayList<>(article.getTargetDepartments());
        /* ここを追加（ここまで） */
    }

    public void changeBody(String body) {
        /* ここを追加（ここから） */
        saveHistory();
        /* ここを追加（ここまで） */

        article.changeBody(body);

        System.out.println("[本文を変更] " + article.getSummary());
    }

    public void addTargetDepartment(String department) {
        /* ここを追加（ここから） */
        saveHistory();
        /* ここを追加（ここまで） */

        article.addTargetDepartment(department);

        System.out.println("[公開先を追加] " + article.getSummary());
    }

    public void removeTargetDepartment(String department) {
        /* ここを追加（ここから） */
        saveHistory();
        /* ここを追加（ここまで） */

        article.removeTargetDepartment(department);

        System.out.println("[公開先を削除] " + article.getSummary());
    }

    public void saveDraft() {
        article.validate();
        /* ここを追加（ここから） */

        draftBody = article.getBody();
        draftTargetDepartments = new ArrayList<>(article.getTargetDepartments());
        /* ここを追加（ここまで） */

        System.out.println("[下書き保存] " + article.getSummary());
    }

    /* ここを追加（ここから） */
    public void undo() {
        if (bodyHistory.isEmpty()) {
            System.out.println("[1つ前に戻す] 戻せる操作がありません");

            return;
        }

        article.changeBody(bodyHistory.pop());
        article.setTargetDepartments(targetDepartmentsHistory.pop());

        System.out.println("[1つ前に戻す] " + article.getSummary());
    }

    public void revertToDraft() {
        article.changeBody(draftBody);
        article.setTargetDepartments(new ArrayList<>(draftTargetDepartments));

        bodyHistory.clear();
        targetDepartmentsHistory.clear();

        System.out.println("[下書きに戻す] " + article.getSummary());
    }

    private void saveHistory() {
        bodyHistory.push(article.getBody());
        targetDepartmentsHistory.push(new ArrayList<>(article.getTargetDepartments()));
    }
    /* ここを追加（ここまで） */
}
```

※追加した `ArticleEditor` クラスについて、次の 4 点を補足します。

> - 1 つ前に戻すための履歴のフィールド（`bodyHistory`・`targetDepartmentsHistory`）には、各編集操作の直前の値を積んでいます。どちらも `Deque` インターフェースをスタック（後から入れたものを先に取り出す入れ物）として使っており、`push` メソッドで積んだ値を、`pop` メソッドで新しいものから順に取り出します。
> - 下書き用のフィールド（`draftBody`・`draftTargetDepartments`）には、編集画面を開いた時点（コンストラクタ）の値を退避しておき、下書き保存するたび（`saveDraft` メソッド）に、その時点の値で上書きしています。
> - 公開先の部署は、履歴や下書きに退避するときと、下書きから書き戻すときに、`new ArrayList<>(...)` で同じ部署が入った新しいリストを作っています。取り出したリストをそのまま退避すると、1 つ前に戻しても、下書きに戻しても、誤って追加した部署が公開先に残ったままになってしまいます。その原因と、本文はそのまま退避しても正しく戻せる理由は、深堀りで紹介します（→ [【深堀り①】リストをコピーして退避する理由](#深堀り1)）。
> - `revertToDraft` メソッドは編集を破棄する操作のため、下書き保存した時点に戻した後に履歴を空にしています。

**`Main.java`**

```java
package example;

public class Main {
    public static void main(String[] args) {
        ArticleEditor editor = new ArticleEditor(new Article());

        editor.changeBody("評価制度の見直しを検討しています。");
        editor.addTargetDepartment("人事部");
        editor.saveDraft();

        System.out.println();

        editor.changeBody("新しい評価制度の説明会を開催します。");
        editor.addTargetDepartment("総務部");
        editor.removeTargetDepartment("総務部");
        // 誤って削除してしまったため、手動で追加し直している
        editor.addTargetDepartment("総務部");
        editor.saveDraft();
        /* ここを追加（ここから） */

        System.out.println();

        editor.changeBody("営業部の今期の売上目標を更新しました。");
        editor.addTargetDepartment("営業部");
        editor.addTargetDepartment("経理部");
        // 経理部を誤って追加してしまったため、1 つ前に戻している
        editor.undo();
        // 別の記事を編集していたことに気づいたため、下書きに戻している
        editor.revertToDraft();
        /* ここを追加（ここまで） */
    }
}
```

**実行結果**

```
[本文を変更] 本文：評価制度の見直しを検討しています。／公開先：[]
[公開先を追加] 本文：評価制度の見直しを検討しています。／公開先：[人事部]
[下書き保存] 本文：評価制度の見直しを検討しています。／公開先：[人事部]

[本文を変更] 本文：新しい評価制度の説明会を開催します。／公開先：[人事部]
[公開先を追加] 本文：新しい評価制度の説明会を開催します。／公開先：[人事部, 総務部]
[公開先を削除] 本文：新しい評価制度の説明会を開催します。／公開先：[人事部]
[公開先を追加] 本文：新しい評価制度の説明会を開催します。／公開先：[人事部, 総務部]
[下書き保存] 本文：新しい評価制度の説明会を開催します。／公開先：[人事部, 総務部]

[本文を変更] 本文：営業部の今期の売上目標を更新しました。／公開先：[人事部, 総務部]
[公開先を追加] 本文：営業部の今期の売上目標を更新しました。／公開先：[人事部, 総務部, 営業部]
[公開先を追加] 本文：営業部の今期の売上目標を更新しました。／公開先：[人事部, 総務部, 営業部, 経理部]
[1つ前に戻す] 本文：営業部の今期の売上目標を更新しました。／公開先：[人事部, 総務部, 営業部]
[下書きに戻す] 本文：新しい評価制度の説明会を開催します。／公開先：[人事部, 総務部]
```

コンパイルエラーがなく結果が出力されていることから、一見すると実装・動作確認ともに問題ないように見えます。

しかし、この実装には以下の問題点があります。

- `ArticleEditor` クラスは、公開先の部署を `new ArrayList<>(...)` でコピーしてから退避している。これは、`Article` クラスが公開先の部署をリストで保持し、そのリストをそのまま返しているためである。つまり、`ArticleEditor` クラスが `Article` クラスの内部の作りまで知っていなければならない状態（密結合）になっている。
    - もしコピーを忘れて履歴や下書き（`targetDepartmentsHistory`・`draftTargetDepartments`）に退避すると、コンパイルエラーも例外も発生しないまま、公開先の部署だけが元に戻らない不具合が生まれてしまう（→ [【深堀り①】リストをコピーして退避する理由](#深堀り1)）。
- 退避と書き戻しのために追加した `getTargetDepartments`・`setTargetDepartments` メソッドは `public` のため、`ArticleEditor` クラス以外のどこからでも呼び出せてしまう。
    - 例えば、`setTargetDepartments` メソッドに同じ部署が重複したリストを渡したり、`getTargetDepartments` メソッドで取り出したリストに直接部署を追加したりすれば、`addTargetDepartment` メソッドのチェックを経由せずに、同じ部署を重複して登録できてしまう。
    - なお、同じく追加した `getBody` メソッドが返す `String` クラスのオブジェクトは中身を書き換えられない（不変な）ため、取り出した本文から記事を書き換えることはできない。また、本文の書き戻しには既存の `changeBody` メソッドを使っており、書き換えるためのメソッドを新たに追加していないため、本文ではこの問題は起きない。
- `Article` クラスにフィールド（例えば「公開日」）を追加するたびに、`ArticleEditor` クラスにも履歴用・下書き用のフィールドを追加し、コンストラクタと `saveDraft`・`undo`・`revertToDraft`・`saveHistory` メソッドをすべて修正しなければならない。
    - 例えば `undo`・`revertToDraft` メソッドの修正を忘れると、コンパイルエラーも例外も発生しないまま、公開日だけが元に戻らない不具合が生まれてしまう。

## 正しい実装

では、好ましくない実装で挙げた問題点を解決するにはどうすればよいのでしょうか？

これらの問題を解決するのが **Memento パターン**です。<br>
編集画面が記事の値を 1 つずつ取り出して退避するのをやめ、記事自身に「ある時点の自分の状態」を 1 つのオブジェクトにまとめて作らせます。編集画面はそのオブジェクトを中身を見ないまま預かっておき、元に戻すときは、そのオブジェクトを記事に渡して、記事自身に状態を戻させます。<br>
この「ある時点の状態をまとめたオブジェクト」を、Memento（メメント：英語で「記念品」「形見」の意味）と呼びます。

ただし、編集画面が Memento の中身を取り出したり、自分で作ったりできてしまうと、好ましくない実装と同じ問題が残ります。そこで本実装では、記事と Memento を `example.article` パッケージに移し、編集画面とは別のパッケージに分けます。こうすることで、記事からは Memento の中身を使えるようにしたまま、編集画面からは隠せるようになります（詳しい仕組みは後述します）。

では、実装を見ていきましょう。<br>
※本記事では下記のクラス構成としています。

> ```
> example.article パッケージ
>   ├── Article.java          お知らせ記事（example パッケージから移動し、本実装に合わせて修正）
>   └── ArticleMemento.java   ある時点の記事の状態をまとめたクラス
>
> example パッケージ
>   ├── ArticleEditor.java    お知らせ記事の編集画面（既存の仕様から本実装に合わせて修正）
>   └── Main.java             実行クラス（既存の仕様から本実装に合わせて修正）
> ```

まず、ある時点の記事の状態をまとめるクラスから見ていきましょう。

**`ArticleMemento.java`**

```java
package example.article;

public class ArticleMemento {
    private final String body;
    private final List<String> targetDepartments;

    ArticleMemento(String body, List<String> targetDepartments) {
        this.body = body;
        this.targetDepartments = new ArrayList<>(targetDepartments);
    }

    String getBody() {
        return body;
    }

    List<String> getTargetDepartments() {
        return targetDepartments;
    }
}
```

`ArticleMemento` は新たに追加したクラスで、ある時点の記事の本文と公開先の部署を保持します。ポイントは次の 2 点です。

- コンストラクタと `getBody`・`getTargetDepartments` メソッドには、`public` などのアクセス修飾子を付けていない。
    - アクセス修飾子を付けないコンストラクタやメソッドは、同じパッケージ（`example.article`）のクラスからしか呼び出せない（パッケージ・プライベート）。そのため、`Article` クラスからは呼び出せる一方、`example` パッケージにある `ArticleEditor` クラスは、`ArticleMemento` クラスのインスタンスを変数に入れたり引数に渡したりすることはできても、中身を取り出すことも、新たに作ることもできない。
- コンストラクタで、受け取った公開先の部署のリストをコピーしている。
    - 受け取ったリストをそのまま保持すると、`Article` クラスとリストを共有してしまうため（→ [【深堀り①】リストをコピーして退避する理由](#深堀り1)）、コピーした新しいリストを保持している。
    - また、フィールドには `final` を付け、インスタンスを作った後でフィールドが別の値に差し替えられないようにしている。

次に、Memento を作る側であり、Memento を受け取って状態を戻す側でもある `Article` クラスを見ていきましょう。

**`Article.java`**

```java
package example.article;

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

    public void validate() {
        if (body.isBlank() || targetDepartments.isEmpty()) {
            throw new IllegalStateException("本文と公開先の部署をどちらも入力してから、下書き保存してください");
        }
    }

    public String getSummary() {
        return "本文：" + body + "／公開先：" + targetDepartments;
    }

    /* ここを追加（ここから） */
    public ArticleMemento createMemento() {
        return new ArticleMemento(body, targetDepartments);
    }

    public void restoreMemento(ArticleMemento memento) {
        body = memento.getBody();
        targetDepartments = new ArrayList<>(memento.getTargetDepartments());
    }
    /* ここを追加（ここまで） */
}
```

`Article` クラスを振り返ると、既存の仕様から次の 2 つのメソッドが追加されています。

- `createMemento` メソッドは、現在の本文と公開先の部署から `ArticleMemento` クラスのインスタンスを作って返す。
- `restoreMemento` メソッドは、受け取った `ArticleMemento` クラスのインスタンスから本文と公開先の部署を取り出し、自身の状態を元に戻す。
    - 公開先の部署のリストは、ここでもコピーしてから保持している。これは、同じ下書きの Memento を使って 2 回以上戻す場合に備えるためである。Memento のリストをそのまま保持すると、戻した後の編集で Memento のリストまで書き換わり、2 回目に戻したときには下書き保存した時点の状態に戻らなくなってしまう。

状態を取り出したり書き戻したりする処理はすべて `Article` クラスの中に収まっているため、好ましくない実装のような getter・setter を追加する必要はありません。<br>
また、`ArticleMemento` クラスのインスタンスは `example.article` パッケージの外では作れず、実際に作っているのは `Article` クラスの `createMemento` メソッドだけです。そのため、`restoreMemento` メソッドで、同じ部署が重複しているような、チェックを経由していない状態に戻されることもありません。

次に、Memento を預かる側の `ArticleEditor` クラスを見ていきましょう。

**`ArticleEditor.java`**

```java
package example;

import example.article.Article;
import example.article.ArticleMemento;

public class ArticleEditor {
    private Article article;
    /* ここを追加（ここから） */
    private Deque<ArticleMemento> history = new ArrayDeque<>();
    private ArticleMemento draft;
    /* ここを追加（ここまで） */

    public ArticleEditor(Article article) {
        this.article = article;
        /* ここを追加（ここから） */
        draft = article.createMemento();
        /* ここを追加（ここまで） */
    }

    public void changeBody(String body) {
        /* ここを追加（ここから） */
        history.push(article.createMemento());
        /* ここを追加（ここまで） */
        article.changeBody(body);

        System.out.println("[本文を変更] " + article.getSummary());
    }

    public void addTargetDepartment(String department) {
        /* ここを追加（ここから） */
        history.push(article.createMemento());
        /* ここを追加（ここまで） */
        article.addTargetDepartment(department);

        System.out.println("[公開先を追加] " + article.getSummary());
    }

    public void removeTargetDepartment(String department) {
        /* ここを追加（ここから） */
        history.push(article.createMemento());
        /* ここを追加（ここまで） */
        article.removeTargetDepartment(department);

        System.out.println("[公開先を削除] " + article.getSummary());
    }

    public void saveDraft() {
        article.validate();
        /* ここを追加（ここから） */
        draft = article.createMemento();
        /* ここを追加（ここまで） */

        System.out.println("[下書き保存] " + article.getSummary());
    }

    /* ここを追加（ここから） */
    public void undo() {
        if (history.isEmpty()) {
            System.out.println("[1つ前に戻す] 戻せる操作がありません");
            return;
        }
        article.restoreMemento(history.pop());

        System.out.println("[1つ前に戻す] " + article.getSummary());
    }

    public void revertToDraft() {
        article.restoreMemento(draft);
        history.clear();

        System.out.println("[下書きに戻す] " + article.getSummary());
    }
    /* ここを追加（ここまで） */
}
```

`ArticleEditor` クラスを振り返ると、既存の仕様から次の点が変わっています。

- 1 つ前に戻すための履歴として、`ArticleMemento` クラスを積むスタックである `history` フィールドを追加している。
    - 各編集操作の直前に、`createMemento` メソッドで作った Memento を `history` フィールドに積んでいる。
- 下書き保存した時点に戻すための Memento として、`draft` フィールドを追加している。
    - 編集画面を開いた時点（コンストラクタ）で `createMemento` メソッドを呼び出して Memento を作り、下書き保存するたび（`saveDraft` メソッド）に、新しく作った Memento で上書きしている。
- 1 つ前に戻す `undo` メソッドと、下書きに戻す `revertToDraft` メソッドを追加している。
    - どちらも、預かっていた Memento を `restoreMemento` メソッドに渡すだけで、記事の状態を戻している。

好ましくない実装と比べると、本文用・公開先の部署用に分かれていた履歴と下書きのフィールドが、それぞれ `ArticleMemento` クラスの 1 つのフィールドにまとまっています。また、`ArticleEditor` クラスは Memento の中身（本文や公開先の部署）に一切触れず、履歴と下書きという 2 つの用途で Memento を預かり、必要なときに `Article` クラスへ渡しているだけです。

最後に、実行クラスを見ていきましょう。

**`Main.java`**

```java
package example;

import example.article.Article;

public class Main {
    public static void main(String[] args) {
        ArticleEditor editor = new ArticleEditor(new Article());

        editor.changeBody("評価制度の見直しを検討しています。");
        editor.addTargetDepartment("人事部");
        editor.saveDraft();

        System.out.println();

        editor.changeBody("新しい評価制度の説明会を開催します。");
        editor.addTargetDepartment("総務部");
        editor.removeTargetDepartment("総務部");
        // 誤って削除してしまったため、手動で追加し直している
        editor.addTargetDepartment("総務部");
        editor.saveDraft();
        /* ここを追加（ここから） */

        System.out.println();

        editor.changeBody("営業部の今期の売上目標を更新しました。");
        editor.addTargetDepartment("営業部");
        editor.addTargetDepartment("経理部");
        // 経理部を誤って追加してしまったため、1 つ前に戻している
        editor.undo();
        // 別の記事を編集していたことに気づいたため、下書きに戻している
        editor.revertToDraft();
        /* ここを追加（ここまで） */
    }
}
```

`Main` クラスは、`Article` クラスを `example.article` パッケージに移動したことによる import 文の追加以外は、好ましくない実装と同じです。

**実行結果**

```
[本文を変更] 本文：評価制度の見直しを検討しています。／公開先：[]
[公開先を追加] 本文：評価制度の見直しを検討しています。／公開先：[人事部]
[下書き保存] 本文：評価制度の見直しを検討しています。／公開先：[人事部]

[本文を変更] 本文：新しい評価制度の説明会を開催します。／公開先：[人事部]
[公開先を追加] 本文：新しい評価制度の説明会を開催します。／公開先：[人事部, 総務部]
[公開先を削除] 本文：新しい評価制度の説明会を開催します。／公開先：[人事部]
[公開先を追加] 本文：新しい評価制度の説明会を開催します。／公開先：[人事部, 総務部]
[下書き保存] 本文：新しい評価制度の説明会を開催します。／公開先：[人事部, 総務部]

[本文を変更] 本文：営業部の今期の売上目標を更新しました。／公開先：[人事部, 総務部]
[公開先を追加] 本文：営業部の今期の売上目標を更新しました。／公開先：[人事部, 総務部, 営業部]
[公開先を追加] 本文：営業部の今期の売上目標を更新しました。／公開先：[人事部, 総務部, 営業部, 経理部]
[1つ前に戻す] 本文：営業部の今期の売上目標を更新しました。／公開先：[人事部, 総務部, 営業部]
[下書きに戻す] 本文：新しい評価制度の説明会を開催します。／公開先：[人事部, 総務部]
```

実行結果の `[1つ前に戻す]` の行では、誤って追加した経理部が公開先から外れています。また `[下書きに戻す]` の行では、本文・公開先ともに、下書き保存した時点の状態に戻っています。

以上のような実装を行うと、以下のメリットがあります。

- 状態を退避・復元する処理が `Article` クラスの中にまとまっているため、`ArticleEditor` クラスは `Article` クラスの内部の作りを知らなくても、記事を正しく元に戻せる。
    - リストのコピーのように、内部の作りを知っていなければ書けない処理は、`Article`・`ArticleMemento` クラスだけが受け持っている。
- 退避と書き戻しのための getter・setter を追加する必要がないため、`addTargetDepartment` メソッドのチェックを経由せずに同じ部署を重複して登録する、といった書き換えはできない。
    - `ArticleMemento` クラスの中身も、`example.article` パッケージの外からは取り出すことも作ることもできない。
- `Article` クラスにフィールド（例えば「公開日」）を追加しても、修正するのは同じパッケージにある `Article`・`ArticleMemento` クラスだけで、`ArticleEditor` クラスには一切手を加える必要がない。
    - 退避と書き戻しの処理が、フィールドを追加する `Article` クラスの `createMemento`・`restoreMemento` メソッドにあるため、別のクラスにある処理の修正を忘れる、ということが起きにくい。

## まとめ

正しい実装を振り返ると、`ArticleEditor` クラスは記事の状態の中身を一切知らないまま、`Article` クラスが作った Memento を預かり、必要なときに `Article` クラスへ渡して元に戻させるだけでよくなっています。<br>
このように Memento パターンは、状態の保存と復元をオブジェクト自身に任せ、保存した状態を中身の見えないオブジェクトとして外部に預けることで、カプセル化を壊さずにオブジェクトを以前の状態に戻せるようにする設計パターンです。

本記事の内容はここまでとなります。

以降は「もう少し深く知りたい」という方向けの補足となります。今回学んだパターンに繋がる設計原則や、実務で役立つ背景知識について触れています。

---

<a id="深堀り1"></a>

## 【深堀り①】リストをコピーして退避する理由

好ましくない実装の `ArticleEditor` クラスでは、公開先の部署を退避するときに、`getTargetDepartments` メソッドが返したリストをそのまま退避せず、`new ArrayList<>(...)` で同じ部署が入った新しいリスト（コピー）を作って退避していました。

では、コピーを作らずに退避すると、どうなるのでしょうか？

好ましくない実装の `ArticleEditor` クラスを、コピーを作らないように変えて試してみます。

**`ArticleEditor.java`（一部抜粋）**

```java
package example;

public class ArticleEditor {
    private Article article;
    private Deque<String> bodyHistory = new ArrayDeque<>();
    private Deque<List<String>> targetDepartmentsHistory = new ArrayDeque<>();
    private String draftBody;
    private List<String> draftTargetDepartments;

    public ArticleEditor(Article article) {
        this.article = article;
        draftBody = article.getBody();
        draftTargetDepartments = article.getTargetDepartments();
    }

    public void saveDraft() {
        article.validate();

        draftBody = article.getBody();
        draftTargetDepartments = article.getTargetDepartments();

        System.out.println("[下書き保存] " + article.getSummary());
    }

    public void revertToDraft() {
        article.changeBody(draftBody);
        article.setTargetDepartments(draftTargetDepartments);

        bodyHistory.clear();
        targetDepartmentsHistory.clear();

        System.out.println("[下書きに戻す] " + article.getSummary());
    }

    private void saveHistory() {
        bodyHistory.push(article.getBody());
        targetDepartmentsHistory.push(article.getTargetDepartments());
    }
}
```

※変更のないメソッド（`changeBody` メソッドなど）は省略しています。また、`Article`・`Main` クラスは好ましくない実装から変更ありません。

**実行結果**

```
[本文を変更] 本文：評価制度の見直しを検討しています。／公開先：[]
[公開先を追加] 本文：評価制度の見直しを検討しています。／公開先：[人事部]
[下書き保存] 本文：評価制度の見直しを検討しています。／公開先：[人事部]

[本文を変更] 本文：新しい評価制度の説明会を開催します。／公開先：[人事部]
[公開先を追加] 本文：新しい評価制度の説明会を開催します。／公開先：[人事部, 総務部]
[公開先を削除] 本文：新しい評価制度の説明会を開催します。／公開先：[人事部]
[公開先を追加] 本文：新しい評価制度の説明会を開催します。／公開先：[人事部, 総務部]
[下書き保存] 本文：新しい評価制度の説明会を開催します。／公開先：[人事部, 総務部]

[本文を変更] 本文：営業部の今期の売上目標を更新しました。／公開先：[人事部, 総務部]
[公開先を追加] 本文：営業部の今期の売上目標を更新しました。／公開先：[人事部, 総務部, 営業部]
[公開先を追加] 本文：営業部の今期の売上目標を更新しました。／公開先：[人事部, 総務部, 営業部, 経理部]
[1つ前に戻す] 本文：営業部の今期の売上目標を更新しました。／公開先：[人事部, 総務部, 営業部, 経理部]
[下書きに戻す] 本文：新しい評価制度の説明会を開催します。／公開先：[人事部, 総務部, 営業部, 経理部]
```

実行結果を振り返ると、`[1つ前に戻す]` の行では、1 つ前に戻したにもかかわらず、誤って追加した経理部が公開先に残ったままになっています。<br>
また `[下書きに戻す]` の行では、本文は下書き保存した時点に戻っているのに、公開先には営業部と経理部が残っています。<br>
これでは、シナリオのヒヤリハットと同じく、公開する予定のない営業部や経理部にも公開される設定のままになってしまいます。

この原因は、`getTargetDepartments` メソッドが、`Article` クラスが保持しているリストそのもの（参照）を返していることにあります。`ArticleEditor` クラスが履歴や下書きに退避していたのは、その時点の公開先の部署ではなく、`Article` クラスと同じリストへの参照でした。そのため、営業部や経理部を追加すると、退避しておいたはずのリストにも同じ部署が追加され、1 つ前に戻しても、下書きに戻しても、追加した部署が公開先に残ったままになるわけです。<br>
一方、本文は `String` 型です。退避した時点では、公開先の部署と同じく、`draftBody` フィールドも `Article` クラスの本文と同じ文字列を参照しています。しかし、`String` クラスは中身を書き換えられない（不変な）クラスのため、`changeBody` メソッドにより、`Article` クラスの本文は別の文字列に差し替えられます。その結果、`Article` クラスの本文と `draftBody` フィールドは別々の文字列を参照することになり、`[下書きに戻す]` の行のように正しく元に戻すことができます。

そのため、好ましくない実装では、退避するときにリストのコピーを作り、`Article` クラスとは別のリストを退避しています。<br>
また、下書きから書き戻すとき（`revertToDraft` メソッド）にもコピーを渡しているのは、`Article` クラスと下書きが同じリストを参照しないようにするためです。同じリストを参照したままだと、下書きに戻した後の編集で下書きのリストまで書き換わり、もう一度下書きに戻したときに、下書き保存した時点の状態に戻らなくなってしまいます。<br>
正しい実装の `ArticleMemento` クラスのコンストラクタと、`Article` クラスの `restoreMemento` メソッドでリストをコピーしているのも、同じ理由です。

なお、好ましくない実装では、リストを受け取る `ArticleEditor` クラスの側でコピーを作っていますが、実務では、リストを持つクラス自身がコピーを作って渡すのが一般的です（防御的コピーと呼ばれます）。受け取る側でコピーする方法では、好ましくない実装の問題点で挙げたように、受け取る側が内部の作りを知っていなければならず、呼び出す箇所が増えるほどコピーし忘れるおそれも高まるためです。<br>
正しい実装で、リストを持つ `Article`・`ArticleMemento` クラス自身がコピーを作っているのも、この考え方に沿ったものです。

<a id="深堀り2"></a>

## 【深堀り②】Memento の中身を隠すもう 1 つの方法（ネストクラス）

正しい実装では、`ArticleMemento` クラスのコンストラクタと getter をパッケージ・プライベートにすることで、`example` パッケージにある `ArticleEditor` クラスから中身を隠しました。<br>
ただし、パッケージ・プライベートは、同じパッケージのクラスすべてに公開されます。そのため、例えば今後 `example.article` パッケージに記事を検索するクラスを追加すると、そのクラスからも `ArticleMemento` クラスの中身を取り出せてしまいます。

中身を見られるクラスを `Article` クラスだけに絞りたい場合は、Memento を `Article` クラスの中に、ネストクラス（クラスの中で宣言したクラス）として定義する方法があります。

**`Article.java`（一部抜粋）**

```java
package example.article;

public class Article {
    private String body = "";
    private List<String> targetDepartments = new ArrayList<>();

    public Memento createMemento() {
        return new Memento(body, targetDepartments);
    }

    public void restoreMemento(Memento memento) {
        body = memento.body;
        targetDepartments = new ArrayList<>(memento.targetDepartments);
    }

    public static class Memento {
        private final String body;
        private final List<String> targetDepartments;

        private Memento(String body, List<String> targetDepartments) {
            this.body = body;
            this.targetDepartments = new ArrayList<>(targetDepartments);
        }
    }
}
```

※既存のメソッド（`changeBody` メソッドなど）は省略しています。

ネストクラスの `Memento` クラスは、コンストラクタもフィールドも `private` です。しかし Java では、外側のクラスから、ネストクラスの `private` なメンバーにアクセスできます。そのため、`Article` クラスの `restoreMemento` メソッドでは `memento.body` のようにフィールドを直接読み出せる一方、`Article` クラスの外からは、同じパッケージのクラスであっても、中身を取り出すことも新たに作ることもできません。<br>
また、`static` を付けたネストクラスは、外側のクラスのインスタンスとは結びつかない、独立したクラスとして扱われます。`ArticleEditor` クラスからは `Article.Memento` という型名で参照でき、`Deque<Article.Memento>` のように、正しい実装と同じ形で Memento を預かれます。

なお、GoF の原典（C++ で書かれています）では、`friend` という仕組みを使い、Memento の中身を Originator（本記事の `Article` クラスにあたる、Memento を作って状態を戻す側のクラス）にだけ公開しています。Java には `friend` にあたる仕組みがないため、パッケージ・プライベートかネストクラスで代用します。<br>
パッケージ・プライベートは、Memento を独立したファイルのクラスとして扱える一方、同じパッケージのクラスには中身が見えてしまいます。ネストクラスは、中身を見られるクラスを `Article` クラスだけに絞れる一方、`Article` クラスのファイルに Memento の定義まで含まれるため、保存する状態が増えるほどファイルが長くなります。どこまで厳密に中身を隠したいかに応じて使い分けるとよいでしょう。

<a id="深堀り3"></a>

## 【深堀り③】Undo の 2 つの実現方法（Memento と Command）

本記事では、各操作の直前に記事の状態を丸ごと Memento として保存し、それを使って元に戻しました。<br>
Undo を実現する方法には、もう 1 つ、「行った操作」と「その操作を取り消す処理」を組にして記録する方法があります。操作そのものをオブジェクトとして扱うこの方法は、GoF の Command パターンにあたります。

本記事のコードを例にすると、次のようになります。

**`EditCommand.java`**

```java
package example;

public interface EditCommand {
    void execute();

    void undo();
}
```

**`AddTargetDepartmentCommand.java`**

```java
package example;

import example.article.Article;

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
}
```

`AddTargetDepartmentCommand` クラスは「公開先に部署を追加する」という操作を表すクラスで、`execute` メソッドで部署を追加し、`undo` メソッドで追加した部署を削除して取り消します。編集画面は、実行した操作を履歴に積んでおき、1 つ前に戻すときは、履歴から取り出した操作の `undo` メソッドを呼び出します。

2 つの方法には、それぞれ次のような特徴があります。

- Memento の方式は、操作の種類に関係なく状態を丸ごと保存して戻すため、操作ごとに取り消し方を考える必要がない。一方、保存する状態が大きいほど、操作のたびに多くのメモリを使う（→ [【深堀り④】履歴の上限とメモリ](#深堀り4)）。
- Command の方式は、「どの部署を追加したか」のような操作の内容だけを記録するため、保存する量が少なくて済む。一方、操作の種類ごとに取り消す処理を書く必要があり、その処理を誤ると正しく元に戻らない。

実務では、両者を組み合わせることもあります。例えば、状態がどう変わるかが複雑で、取り消す処理を書きにくい操作では、Command に操作前の状態の Memento を持たせておき、`undo` メソッドでその Memento を使って戻す、という形です。<br>
詳しくは「Command パターン」で検索してみてください。

<a id="深堀り4"></a>

## 【深堀り④】履歴の上限とメモリ

正しい実装の `ArticleEditor` クラスは、編集操作のたびに `ArticleMemento` クラスのインスタンスを 1 つ作り、`history` フィールドに積んでいます。`revertToDraft` メソッドを呼び出さない限り履歴は空にならないため、編集を続けるほど、`history` フィールドが保持する Memento は増え続けます。

なお、本文の `String` クラスは不変なため、本文を変更していない Memento どうしは同じ文字列を共有しており、本文がその都度複製されるわけではありません。一方、公開先の部署のリストは Memento を作るたびにコピーされます。保存する状態が大きいほど（例えば、大きな表や画像のデータなど）、Memento 1 つあたりのメモリも大きくなります。

そこで実務では、履歴に上限を設け、上限を超えたら古いものから捨てるのが一般的です。

**`ArticleEditor.java`（一部抜粋）**

```java
package example;

public class ArticleEditor {
    private static final int MAX_HISTORY = 50;

    private void saveHistory() {
        history.push(article.createMemento());
        if (history.size() > MAX_HISTORY) {
            history.removeLast();
        }
    }
}
```

※ `MAX_HISTORY` の 50 は説明のための値です。実際には、保存する状態の大きさや、何回まで戻せれば十分かに応じて決めます。

`push` メソッドで積んだ Memento は履歴の先頭に入るため、最も古い Memento は末尾にあります。上限（`MAX_HISTORY`）を超えたら `removeLast` メソッドで末尾の 1 つを捨てることで、履歴は常に新しいものから 50 件までに保たれます。各編集操作では、`history.push(article.createMemento())` の代わりに `saveHistory` メソッドを呼び出すようにします。

それでもメモリが足りない場合は、状態を丸ごと保存するのではなく、Command の方式（→ [【深堀り③】Undo の 2 つの実現方法（Memento と Command）](#深堀り3)）のように、操作の内容だけを記録する方法を検討します。

<a id="深堀り5"></a>

## 【深堀り⑤】Memento をファイルに保存する

正しい実装の Memento はメモリ上にしかないため、アプリを終了すると消えてしまいます。Memento をファイルに保存しておけば、アプリを再起動した後でも、保存した時点の状態に戻せます。

Java では、クラスに `Serializable` インターフェースを実装すると、そのインスタンスをバイト列に変換（シリアライズ）してファイルに書き出したり、ファイルから読み込んでインスタンスに戻したり（デシリアライズ）できます。`Serializable` インターフェースは抽象メソッドを 1 つも持たず、実装したクラスが「シリアライズしてよいクラス」であることを示す目印の役割だけを持ちます。

**`ArticleMemento.java`**

```java
package example.article;

public class ArticleMemento implements Serializable {
    /* ここを追加（ここから） */
    private static final long serialVersionUID = 1L;
    /* ここを追加（ここまで） */
    private final String body;
    private final List<String> targetDepartments;

    ArticleMemento(String body, List<String> targetDepartments) {
        this.body = body;
        this.targetDepartments = new ArrayList<>(targetDepartments);
    }

    String getBody() {
        return body;
    }

    List<String> getTargetDepartments() {
        return targetDepartments;
    }
}
```

`ArticleMemento` クラスを振り返ると、正しい実装からクラス宣言に `implements Serializable` を追加し、`serialVersionUID` という定数を宣言しています。<br>
`serialVersionUID` は、シリアライズしたときのクラスと、読み込むときのクラスが同じものかを確かめるための番号です。宣言しない場合は、クラスの内容から自動的に計算されるため、フィールドを追加するなどクラスを修正しただけで番号が変わり、修正前に保存したファイルを読み込めなくなる（`InvalidClassException` が発生する）ことがあります。

**`ArticleEditor.java`（一部抜粋）**

```java
package example;

import example.article.ArticleMemento;

public class ArticleEditor {
    private ArticleMemento draft;

    public void saveDraftToFile(Path path) throws IOException {
        try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(path))) {
            out.writeObject(draft);
        }
    }

    public void loadDraftFromFile(Path path) throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(path))) {
            draft = (ArticleMemento) in.readObject();
        }
    }
}
```

`saveDraftToFile` メソッドは、下書きの Memento を `ObjectOutputStream` クラスでファイルに書き出し、`loadDraftFromFile` メソッドは、ファイルから読み込んだ Memento を `draft` フィールドに戻します。`try (...)` の形（try-with-resources 文）で書くと、処理が終わったときにファイルが自動的に閉じられます。<br>
例えば、アプリを終了する前に `saveDraftToFile` メソッドを呼び出しておけば、再起動後に `loadDraftFromFile` メソッドと `revertToDraft` メソッドを順に呼び出すことで、下書き保存した時点の記事に戻せます。

ここで注目したいのは、`ArticleEditor` クラスが Memento の中身を知らないまま、ファイルへの保存と読み込みを行えている点です。Memento を中身の見えないオブジェクトとして預かるという関係は、保存先がメモリからファイルに変わっても崩れていません。

ただし、Java のシリアライズには注意点もあります。信頼できないデータをデシリアライズすると、細工されたデータによって意図しない処理を実行されるおそれがあり、書籍『Effective Java』でも、Java のシリアライズより JSON などの代替手段を優先するよう勧めています。<br>
実務で Memento をファイルなどに保存する場合は、JSON などの形式を使うことも検討するとよいでしょう。その場合も、JSON への変換は `ArticleMemento` クラスなど `example.article` パッケージの側に持たせ、`ArticleEditor` クラスが中身を知らずに済む形にするのがポイントです。<br>
詳しくは「Java シリアライズ 脆弱性」で検索してみてください。

<a id="深堀り6"></a>

## 【深堀り⑥】GoF デザインパターンとの位置づけ

今回使った Memento パターンは、GoF（Gang of Four）の 23 のデザインパターンのうち「振る舞いパターン」に分類されます。<br>
詳しくは「GoF」で検索してみてください。
