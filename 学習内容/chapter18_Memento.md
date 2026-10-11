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
- [【深堀り①】新しいリストを作って退避する理由](#深堀り1)
- [【深堀り②】広い API と狭い API](#深堀り2)
- [【深堀り③】Memento の中身を見られるクラスをさらに絞る方法（ネストクラス）](#深堀り3)
- [【深堀り④】履歴が増え続けたときのメモリ対策](#深堀り4)
    - [履歴に上限を設ける](#履歴に上限を設ける)
    - [差分だけを保存する](#差分だけを保存する)
    - [データを圧縮する](#データを圧縮する)
    - [ファイルや DB に保存する](#ファイルや-db-に保存する)
- [【深堀り⑤】Memento をファイルや DB に保存する](#深堀り5)
    - [`Serializable` インターフェースを使わない理由](#serializable-インターフェースを使わない理由)
    - [Memento の賞味期限](#memento-の賞味期限)
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

※`ArticleEditor` クラスに追加した処理について、次の 4 点を補足します。

> - 1 つ前に戻すための履歴のフィールド（`bodyHistory`・`targetDepartmentsHistory`）には、各編集操作の直前の値を積んでいます。どちらも `Deque` インターフェースをスタック（後から入れたものを先に取り出す入れ物）として使っており、`push` メソッドで積んだ値を、`pop` メソッドで新しいものから順に取り出します。
> - 下書き用のフィールド（`draftBody`・`draftTargetDepartments`）には、編集画面を開いた時点（コンストラクタ）の値を退避しておき、下書き保存するたび（`saveDraft` メソッド）に、その時点の値で上書きしています。
> - 公開先の部署は、履歴や下書きに退避するときと、下書きから書き戻すときに、`new ArrayList<>(...)` で同じ部署が入った新しいリストを作っています。取り出したリストをそのまま退避すると、1 つ前に戻しても、下書きに戻しても、誤って追加した部署が公開先に残ったままになってしまいます。その原因と、本文はそのまま退避しても正しく戻せる理由は、深堀りで紹介します（→ [【深堀り①】新しいリストを作って退避する理由](#深堀り1)）。
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

- `ArticleEditor` クラスは、公開先の部署を `new ArrayList<>(...)` で新しいリストに作り直してから退避している。これは、`Article` クラスが公開先の部署をリストで保持し、そのリストをそのまま返しているためである。つまり、`ArticleEditor` クラスが `Article` クラスの内部の作りまで知っていなければならない状態（密結合）になっている。
    - もし新しいリストを作り忘れて履歴や下書き（`targetDepartmentsHistory`・`draftTargetDepartments`）に退避すると、コンパイルエラーも例外も発生しないまま、公開先の部署だけが元に戻らない不具合が生まれてしまう（→ [【深堀り①】新しいリストを作って退避する理由](#深堀り1)）。
- 退避と書き戻しのために追加した `getTargetDepartments`・`setTargetDepartments` メソッドは `public` のため、`ArticleEditor` クラス以外のどこからでも呼び出せてしまう。
    - 例えば、`setTargetDepartments` メソッドに同じ部署が重複したリストを渡したり、`getTargetDepartments` メソッドで取り出したリストに直接部署を追加したりすれば、`addTargetDepartment` メソッドのチェックを経由せずに、同じ部署を重複して登録できてしまう。
    - なお、同じく追加した `getBody` メソッドが返す `String` クラスのオブジェクトは中身を書き換えられない（不変な）ため、取り出した本文から記事を書き換えることはできない。また、本文の書き戻しには既存の `changeBody` メソッドを使っており、書き換えるためのメソッドを新たに追加していないため、本文ではこの問題は起きない。
- `Article` クラスにフィールド（例えば「公開日」）を追加するたびに、`ArticleEditor` クラスにも履歴用・下書き用のフィールドを追加し、コンストラクタと `saveDraft`・`undo`・`revertToDraft`・`saveHistory` メソッドをすべて修正しなければならない。
    - 例えば `undo`・`revertToDraft` メソッドの修正を忘れると、コンパイルエラーも例外も発生しないまま、公開日だけが元に戻らない不具合が生まれてしまう。

## 正しい実装

では、好ましくない実装で挙げた問題点を解決するにはどうすればよいのでしょうか？

これらの問題を解決するのが **Memento パターン**です。<br>
Memento パターンは、オブジェクト（`Article` クラスに該当）自身が、現在の状態を保存するために Memento（「記念品」「形見」という意味の英単語）を作り、復元するときはその Memento から状態を戻すパターンです。Memento を預かる側（`ArticleEditor` クラスに該当）は、中身を見ずに保持するだけになります。

では、実装を見ていきましょう。<br>
※本記事では下記のクラス構成としています。

> ```
> example.article パッケージ（Memento を作る側と Memento）
>   ├── Article.java          お知らせ記事（example パッケージから移動）
>   └── ArticleMemento.java   記事の状態を保存するための Memento
>
> example パッケージ（Memento を預かる側と実行クラス）
>   ├── ArticleEditor.java    お知らせ記事の編集画面
>   └── Main.java             実行クラス
> ```

**example.article パッケージ**

まず、記事の状態を保存するための Memento の実装から見ていきましょう。

**`ArticleMemento.java`**

```java
package example.article;

public class ArticleMemento {
    private String body;
    private List<String> targetDepartments;

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

`ArticleMemento` は新たに追加したクラスで、ある時点の記事の本文と公開先の部署を保持するためのフィールドを持っています。コンストラクタと 2 つの getter メソッド（`getBody`・`getTargetDepartments`）には、アクセス修飾子が付いていません。そのため、同じパッケージ（`example.article`）にある `Article` クラスからは呼び出せる一方、`example` パッケージにある `ArticleEditor` クラスは、`ArticleMemento` クラスのインスタンスを新たに作ることも、中身を取り出すこともできません。`ArticleEditor` クラスにできるのは、`Article` クラスが作ったインスタンスを受け取って保持しておき、元に戻すときに `Article` クラスへ渡すことだけです（→ [【深堀り②】広い API と狭い API](#深堀り2)）。<br>
また、コンストラクタで `targetDepartments` フィールドに値をセットする行では、受け取った公開先の部署のリストをそのまま保持せず、同じ部署が入った新しいリストを作って保持しています。これは、`Article` クラスとリストを共有して、Memento を作った後の編集で Memento のリストまで書き換わってしまうことを防ぐためです（→ [【深堀り①】新しいリストを作って退避する理由](#深堀り1)）。

次に、現在の状態を保存するために Memento を作り、復元するときはその Memento から状態を戻す `Article` クラスを見ていきましょう。

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

`Article` クラスを振り返ると、既存の仕様から `createMemento`・`restoreMemento` メソッドを追加しています。<br>
`createMemento` メソッドにより、現在の本文と公開先の部署を保存しています。保存する際は、本文と公開先の部署の保持を `ArticleMemento` クラスに委譲しています。また、作った `ArticleMemento` クラスのインスタンスを返すことで、`ArticleEditor` クラスに預けておき、後から元に戻すときに使えるようにしています。<br>
`restoreMemento` メソッドにより、受け取った Memento を作った時点の状態に戻しています。戻す際は、`ArticleMemento` クラスのインスタンスから本文と公開先の部署を取り出し、自身のフィールドに書き戻しています。

公開先の部署のリストに注目すると、`restoreMemento` メソッドでも `ArticleMemento` クラスのコンストラクタと同じく、Memento から取り出したリストをそのまま保持せず、新しいリストを作って保持しています。これは、戻した後の編集で Memento のリストまで書き換わらないようにするためです（→ [【深堀り①】新しいリストを作って退避する理由](#深堀り1)）。

パッケージの構成に注目すると、`example.article` パッケージに `Article`・`ArticleMemento` クラスを置き、`ArticleEditor`・`Main` クラスは別の `example` パッケージに置いています。これは、`ArticleMemento` クラスのインスタンスを作れるのを `Article` クラスの `createMemento` メソッドだけに、中身を取り出せるのを `restoreMemento` メソッドだけに限るためです。これにより、`restoreMemento` メソッドで戻せるのは、`addTargetDepartment` メソッドのチェックを経た状態から作られ、その後も書き換えられていない Memento だけになり、元に戻した後も公開先の部署が重複することはありません。

**example パッケージ**

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

    private void saveHistory() {
        history.push(article.createMemento());
    }
    /* ここを追加（ここまで） */
}
```

`ArticleEditor` クラスを振り返ると、既存の仕様から `history`・`draft` フィールドと、`undo`・`revertToDraft`・`saveHistory` メソッドを追加しています。<br>
`history` フィールドにより、1 つ前に戻すための Memento を積み重ねて保持できるようにしています。積む際は、各編集操作の直前に `saveHistory` メソッドを呼び出し、その時点の状態から Memento を作っています。<br>
`draft` フィールドにより、下書き保存した時点に戻すための Memento を保持できるようにしています。保持する際は、編集画面を開いた時点（コンストラクタ）で Memento を作り、下書き保存するたび（`saveDraft` メソッド）に新しく作った Memento で上書きしています。<br>
`undo` メソッドにより、1 つ前の状態に戻しています。戻す際は、`history` フィールドから最後に積んだ Memento を取り出し、`restoreMemento` メソッドに渡しています。<br>
`revertToDraft` メソッドにより、下書き保存した時点の状態に戻しています。戻す際は、`draft` フィールドで保持している Memento を `restoreMemento` メソッドに渡しています。

Memento の扱いに注目すると、`ArticleEditor` クラスは Memento に何が保存されているか（本文や公開先の部署）を知る必要がありません。Memento を預かっておき、必要なときに `Article` クラスへ渡すだけです。`Article` クラス自身ではなく `ArticleEditor` クラスが Memento を預かるのは、どの時点の状態をいくつ残しておくかが、記事そのものではなく編集画面の都合だからです。例えば、1 つ前に戻すための履歴は編集画面を開いている間だけ必要なもので、記事と一緒にデータベースへ保存するものではありません。もし Memento を `Article` クラスに持たせると、お知らせ記事を表すクラスに編集画面の都合が入り込み、履歴の上限のような、状態の残し方に関する機能を追加するたびに、`Article` クラスを修正することになってしまいます（→ [履歴に上限を設ける](#履歴に上限を設ける)）。

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

`Main` クラスを振り返ると、`Article` クラスを `example.article` パッケージに移したため、import 文を追加しています。それ以外は好ましくない実装と同じです。

実行結果は、好ましくない実装とまったく同じになっています。

以上のような実装を行うと、以下のメリットがあります。

- 新しいリストを作る処理を含め、退避と書き戻しの処理を `Article`・`ArticleMemento` クラスだけが受け持っているため、`ArticleEditor` クラスが `Article` クラスの内部の作りを知らなくてよい状態（疎結合）になっている。
    - その結果、新しいリストを作り忘れて公開先の部署だけが元に戻らない、ということが起きにくい。
- `Article` クラスが退避と書き戻しを行っているため、自身のフィールドを直接読み書きでき、公開先の部署を外から書き換えられる getter・setter を追加する必要がなくなっている。また、`ArticleMemento` クラスのコンストラクタと getter メソッドにはアクセス修飾子が付いていないため、`example.article` パッケージの外から Memento を作ることも、Memento が保持する公開先の部署を書き換えることもできない。
    - その結果、`addTargetDepartment` メソッドのチェックを経由せずに、同じ部署が重複して登録されることがない。
- 退避と書き戻しの処理が `Article` クラスの `createMemento`・`restoreMemento` メソッドにあるため、`Article` クラスにフィールド（例えば「公開日」）を追加しても、修正するのは同じパッケージにある `Article`・`ArticleMemento` クラスだけで、`ArticleEditor` クラスには一切手を加える必要がない。
    - その結果、公開日を追加する `Article` クラスの中で退避と書き戻しの処理も修正することになり、修正を忘れて公開日だけが元に戻らない、ということが起きにくい。

## まとめ

正しい実装を振り返ると、`ArticleEditor` クラスは、その時点の記事の状態を保存した Memento を `Article` クラスから受け取り、中身を一切知らないまま預かっておき、必要なときに `Article` クラスへ渡して元に戻させるだけでよくなっています。<br>
このように Memento パターンは、状態の保存と復元をオブジェクト自身に任せ、保存した状態を中身の見えないオブジェクトとして外部に預けることで、カプセル化を壊さずにオブジェクトを以前の状態に戻せるようにする設計パターンです。

本記事の内容はここまでとなります。

以降は「もう少し深く知りたい」という方向けの補足となります。今回学んだパターンに繋がる設計原則や、実務で役立つ背景知識について触れています。

---

<a id="深堀り1"></a>

## 【深堀り①】新しいリストを作って退避する理由

好ましくない実装の `ArticleEditor` クラスでは、公開先の部署を退避するときに、`getTargetDepartments` メソッドが返したリストをそのまま退避せず、`new ArrayList<>(...)` で同じ部署が入った新しいリスト（コピー）を作って退避していました。

ここでは、もしコピーを作らずに退避するとどうなるのかを確認していきましょう。

好ましくない実装の `ArticleEditor` クラスを、コピーを作らないように変更したのが次となります。

※好ましくない実装の `Article` クラスに変更はありません。

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

※`changeBody`・`addTargetDepartment`・`removeTargetDepartment`・`undo` メソッドは変更がないため省略しています。

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

        System.out.println();

        editor.changeBody("営業部の今期の売上目標を更新しました。");
        editor.addTargetDepartment("営業部");
        editor.addTargetDepartment("経理部");
        // 経理部を誤って追加してしまったため、1 つ前に戻している
        editor.undo();
        // 別の記事を編集していたことに気づいたため、下書きに戻している
        editor.revertToDraft();
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
[1つ前に戻す] 本文：営業部の今期の売上目標を更新しました。／公開先：[人事部, 総務部, 営業部, 経理部]
[下書きに戻す] 本文：新しい評価制度の説明会を開催します。／公開先：[人事部, 総務部, 営業部, 経理部]
```

実行結果を振り返ると、`[1つ前に戻す]` の行では、1 つ前に戻したにもかかわらず、誤って追加した経理部が公開先に残ったままになっています。<br>
また、`[下書きに戻す]` の行では、本文は下書き保存した時点に戻っているのに、公開先には営業部と経理部が残っています。<br>
これでは、シナリオのヒヤリハットと同じく、公開する予定のない営業部や経理部にも公開される設定のままになってしまいます。

この原因は、`getTargetDepartments` メソッドが、`Article` クラスが保持しているリストそのもの（参照）を返していることにあります。`ArticleEditor` クラスが履歴や下書きに退避していたのは、その時点の公開先の部署ではなく、`Article` クラスと同じリストへの参照でした。そのため、営業部や経理部を追加すると、退避しておいたはずのリストにも同じ部署が追加され、1 つ前に戻しても、下書きに戻しても、追加した部署が公開先に残ったままになるわけです。<br>
一方、本文は `String` 型です。退避した時点では、公開先の部署と同じく、`draftBody` フィールドも `Article` クラスの本文と同じ文字列を参照しています。しかし、`String` クラスは中身を書き換えられない（不変な）クラスのため、`changeBody` メソッドは文字列の中身を書き換えるのではなく、`Article` クラスの本文を別の文字列に差し替えます。その結果、`Article` クラスの本文と `draftBody` フィールドは別々の文字列を参照することになり、`[下書きに戻す]` の行のように正しく元に戻すことができます。

公開先の部署のリストの共有は、退避するときだけでなく、下書きから書き戻すときにも起きています。`revertToDraft` メソッドが `Article` クラスに渡していたのも、下書きのリストのコピーではなく、下書きのリストへの参照です。そのため、下書きに戻した後に公開先の部署を追加すると、下書きのリストにも同じ部署が追加されます。今回の実行結果には現れていませんが、もう一度下書きに戻しても、下書き保存した時点の状態には戻らなくなってしまいます。

以上から、好ましくない実装では、退避するときと下書きから書き戻すときにリストのコピーを作り、`Article` クラスと履歴・下書きが別々のリストを参照するようにしています。

なお、好ましくない実装では、リストを使う `ArticleEditor` クラスの側でコピーを作っていますが、実務では、リストを保持するクラス自身が、受け取るときや渡すときにコピーを作るのが一般的です（防御的コピーと呼ばれます）。リストを使う側でコピーする方法では、好ましくない実装の問題点で挙げたように、使う側が内部の作りを知っていなければならず、呼び出す箇所が増えるほどコピーし忘れるおそれが高まるためです。<br>
正しい実装では、この考え方に沿って、リストを保持するクラス自身がコピーを作っています。具体的には、`ArticleMemento` クラスのコンストラクタと、`Article` クラスの `restoreMemento` メソッドで、受け取ったリストのコピーを作っています。

<a id="深堀り2"></a>

## 【深堀り②】広い API と狭い API

正しい実装の `ArticleMemento` クラスでは、コンストラクタと getter メソッドをパッケージプライベート（アクセス修飾子を付けない状態）にすることで、Memento を作ることと中身を取り出すことを、同じパッケージにある `Article` クラスには許し、別のパッケージにある `ArticleEditor` クラスには許していません。<br>
このように Memento パターンでは、Memento が公開する API（コンストラクタやメソッド）を、できることの多さによって次の 2 つに分け、それぞれ公開する相手を変えます。

- 広い API（wide interface）：Memento を作ったり、中身を取り出したりできる API。
    - 公開する相手：Memento を作る側（`Article` クラス）だけ
    - できること：多い（Memento を作る、中身を取り出す）
- 狭い API（narrow interface）：Memento を作ったり、中身を取り出したりできない API。
    - 公開する相手：Memento を預かる側（`ArticleEditor` クラス）を含む、すべてのクラス
    - できること：少ない（Memento を作れず、中身にも触れられない）

※「広い」「狭い」は、API を通してできることの多さを表しており、公開する相手の多さではありません。公開する相手は、広い API より、狭い API のほうが多くなる点に注意してください。なお、英語名の interface は、Java の `interface` とは別物のため、本記事では API と呼んでいます。

正しい実装の `ArticleMemento` クラスは、広い API（パッケージプライベートにしたコンストラクタと 2 つの getter メソッド）だけを持ち、狭い API（`public` にしたメソッド）は 1 つも持っていません。`ArticleEditor` クラスに公開しているのは、`public` にしたクラスの型名だけです。<br>
そのため、`ArticleEditor` クラスは、`ArticleMemento` クラスのインスタンスを作ることも、中身を取り出すこともできず、`Article` クラスから受け取ったインスタンスを `history`・`draft` フィールドで保持しておき、元に戻すときに `Article` クラスへ渡すことしかできません。

ただし、Memento の中身に触れないものであれば、狭い API を用意することもできます。<br>
ここでは例として、今後、履歴の一覧に各時点の日時を表示する機能を追加する場合に備えて、Memento を作った日時を返す狭い API を用意します。

**`ArticleMemento.java`**

```java
package example.article;

public class ArticleMemento {
    private String body;
    private List<String> targetDepartments;
    private LocalDateTime createdAt; // ←ここを追加

    ArticleMemento(String body, List<String> targetDepartments) {
        this.body = body;
        this.targetDepartments = new ArrayList<>(targetDepartments);
        this.createdAt = LocalDateTime.now(); // ←ここを追加
    }

    String getBody() {
        return body;
    }

    List<String> getTargetDepartments() {
        return targetDepartments;
    }

    /* ここを追加（ここから） */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    /* ここを追加（ここまで） */
}
```

`ArticleMemento` クラスを振り返ると、正しい実装から `createdAt` フィールドと `getCreatedAt` メソッドを追加しています。<br>
`createdAt` フィールドにより、Memento を作った日時を保持できるようにしています。保持する際は、コンストラクタで現在の日時をセットしています。<br>
`getCreatedAt` メソッドにより、保持した日時を返しています。返した日時を使うことで、`ArticleEditor` クラスが履歴の一覧に Memento を作った日時を表示できるようにしています。

`getCreatedAt` メソッドは、`public` にして狭い API に加えています。Memento を作った日時は記事の状態（本文と公開先の部署）ではないため、返しても Memento の中身を明かすことにはならないからです。また、`LocalDateTime` クラスも `String` クラスと同じく不変なクラスのため、そのまま返しても Memento が保持する日時は書き換わりません。

このように、Memento の中身に触れない API は、狭い API として Memento を預かる側にも公開できます。一方、Memento を作ったり中身に触れたりする API は、広い API として Memento を作る側にだけ公開します。広い API を、同じパッケージのクラスすべてではなく、`Article` クラスだけに公開する方法は、深堀り③で紹介します（→ [【深堀り③】Memento の中身を見られるクラスをさらに絞る方法（ネストクラス）](#深堀り3)）。

<a id="深堀り3"></a>

## 【深堀り③】Memento の中身を見られるクラスをさらに絞る方法（ネストクラス）

正しい実装では、`ArticleMemento` クラスの広い API（コンストラクタと getter メソッド）をパッケージプライベートにすることで、`example` パッケージにある `ArticleEditor` クラスが、Memento を作ることも、中身を取り出すこともできないようにしました。<br>
ただし、パッケージプライベートは、同じパッケージのクラスすべてに公開されます。そのため、例えば今後 `example.article` パッケージに記事を検索するクラスを追加すると、そのクラスからも、`ArticleMemento` クラスのコンストラクタで Memento を作ったり、getter メソッドで中身を取り出したりできてしまいます。

中身を見られるクラスを `Article` クラスだけに絞りたい場合は、Memento を `Article` クラスの中に、ネストクラス（クラスの中で宣言したクラス）として定義する方法があります。

正しい実装の `Article` クラスを修正したのが次となります。

**`Article.java`**

```java
package example; // ←ここを修正

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

    /* ここを修正（ここから） */
    public Memento createMemento() {
        return new Memento(body, targetDepartments);
    }

    public void restoreMemento(Memento memento) {
        body = memento.body;
        targetDepartments = new ArrayList<>(memento.targetDepartments);
    }
    /* ここを修正（ここまで） */

    /* ここを追加（ここから） */
    public static class Memento {
        private String body;
        private List<String> targetDepartments;

        private Memento(String body, List<String> targetDepartments) {
            this.body = body;
            this.targetDepartments = new ArrayList<>(targetDepartments);
        }
    }
    /* ここを追加（ここまで） */
}
```

ネストクラスの `Memento` クラスでは、コンストラクタとフィールドが `private` になっています。ただし Java では、ネストクラスの `private` なメンバーであっても、そのネストクラスを定義しているクラス（ここでは `Article` クラス）からはアクセスできます。そのため、`Article` クラスの `restoreMemento` メソッドでは `memento.body` のようにフィールドを直接読み出せる一方、`Article` クラスの外からは、同じパッケージのクラスであっても、中身を取り出したり新たに作ったりすることはできません。<br>
また、`Memento` クラスには `static` を付けています。`static` を付けないネストクラスは、自分を作った `Article` クラスのインスタンスへの参照を内部に持ちます。Memento に必要なのは保存した状態だけなので、`static` を付けて、`Article` クラスのインスタンスとは結びつかない独立したクラスにしています。

このように、ネストクラスであれば、同じパッケージのクラスからも Memento の中身を隠せるため、パッケージを分ける必要がありません。そこで、`Article` クラスは好ましくない実装と同じ `example` パッケージに戻しています。また、Memento を `Article` クラスの中で定義したため、`ArticleMemento` クラスは削除しています。

`Article` クラスの修正に合わせて、`ArticleEditor` クラスを修正したのが次となります。

※ `ArticleEditor` クラスは、import 文とフィールド以外に変更はありません。`Main` クラスは import 文を削除し、好ましくない実装と同じになります。実行結果に変更はありません。

**`ArticleEditor.java`（一部抜粋）**

```java
package example;

public class ArticleEditor {
    private Article article;
    private Deque<Article.Memento> history = new ArrayDeque<>(); // ←ここを修正
    private Article.Memento draft; // ←ここを修正
}
```

`Article` クラスと同じパッケージになり、`ArticleMemento` クラスも削除したため、import 文は 2 つとも削除しています。また、`history`・`draft` フィールドでは、`ArticleMemento` クラスの代わりに、`Article.Memento` という型名で Memento を預かるように修正しています。<br>
`ArticleEditor` クラスは、Memento の中身を取り出すことも新たに作ることもできませんが、型名で参照することはできるため、正しい実装と同じ形で Memento を預かれます。

なお、GoF の原典（C++ で書かれています）では、`friend` という仕組みを使い、Memento の中身を Originator（Memento を作って状態を戻す側のクラス）にだけ公開しています。Java には `friend` にあたる仕組みがないため、パッケージプライベートかネストクラスで代用します。

2 つの方法を比べると、パッケージプライベートは、Memento を独立したファイルのクラスとして扱える一方、同じパッケージのクラスには中身が見えてしまいます。ネストクラスは、パッケージを分けなくても、中身を見られるクラスを `Article` クラスだけに絞れる一方、`Article` クラスのファイルに Memento の定義まで含まれるため、保存する状態が増えるほどファイルが長くなります。どこまで厳密に中身を隠したいかに応じて使い分けるとよいでしょう。

<a id="深堀り4"></a>

## 【深堀り④】履歴が増え続けたときのメモリ対策

Memento は、作るたびにその時点の状態を保持するため、メモリを消費します。<br>
正しい実装の `ArticleEditor` クラスを振り返ると、編集操作のたびに `ArticleMemento` クラスのインスタンスを 1 つ作り、`history` フィールドに積んでいます。1 つ前に戻すか、下書きに戻すかしない限り履歴は減らないため、編集を続けるほどメモリの消費は増え続けます。また、保存する状態が大きいほど（本文が長い場合や、今後の機能追加で表や画像のデータなどを保持するようになった場合など）、Memento 1 つあたりのメモリも大きくなります。

そこで実務では、次のような対策をとります。どの対策にもトレードオフがあるため、保存する状態の大きさや、どこまで戻せる必要があるかに応じて、選んだり組み合わせたりします。

### 履歴に上限を設ける

履歴に上限を設け、上限を超えたら古いものから捨てる方法です。

**`ArticleEditor.java`（一部抜粋）**

```java
package example;

import example.article.Article;
import example.article.ArticleMemento;

public class ArticleEditor {
    private static final int MAX_HISTORY = 50; // ←ここを追加
    private Article article;
    private Deque<ArticleMemento> history = new ArrayDeque<>();

    private void saveHistory() {
        history.push(article.createMemento());

        /* ここを追加（ここから） */
        if (history.size() > MAX_HISTORY) {
            history.removeLast();
        }
        /* ここを追加（ここまで） */
    }
}
```

※正しい実装から追加した `MAX_HISTORY` 定数と、修正した `saveHistory` メソッド、それが使う `article`・`history` フィールドのみを記載し、他のフィールド・メソッドは省略しています。

※ `MAX_HISTORY` の 50 は説明のための値です。実際には、保存する状態の大きさや、何回まで戻せれば十分かに応じて決めます。

`Deque` インターフェースの `push` メソッドで積んだ Memento は履歴の先頭に入るため、最も古い Memento は末尾にあります。そのため、上限（`MAX_HISTORY`）を超えたら `Deque` インターフェースの `removeLast` メソッドで末尾の 1 つを捨てることにより、履歴は常に新しいものから 50 件までに保たれるようになります。<br>
履歴を残す処理を `ArticleEditor` クラスに持たせ、積む処理を `saveHistory` メソッドにまとめているため、上限を設ける修正は `ArticleEditor` クラスの中だけで済み、`Article`・`ArticleMemento` クラスには手を加える必要がありません。<br>
デメリットとして、上限を超えて捨てた古い状態には戻せなくなることが挙げられます。

### 差分だけを保存する

状態を丸ごと保存するのではなく、1 つ前の状態から変わった部分だけを保存する方法です。<br>
例えば、長い本文の一部だけを書き換えた場合は、本文全体ではなく、書き換えた箇所だけを保存します。本文のどこが変わったかを求める処理（差分の計算）は、それだけでコードが長くなるため、ここではコード例を省略します。実務では、差分の計算にライブラリを使うのが一般的です。<br>
差分の計算と、差分から状態を組み立て直す処理は、記事の中身に触れるため、`Article`・`ArticleMemento` クラスなど `example.article` パッケージの側に持たせます。<br>
デメリットとして、次の 2 点が挙げられます。

- ある時点の状態に戻すには、差分を順にたどって状態を組み立て直す必要があるため、戻す処理が複雑になる。
- 途中の差分が 1 つでも欠けると、それより前の状態には正しく戻せなくなる。

### データを圧縮する

Memento に保存するデータを圧縮して、Memento 1 つあたりのメモリを小さくする方法です。<br>
例えば、本文では同じ語句の繰り返しが多くなるほど小さく圧縮することができます。<br>
圧縮の処理は Java の標準ライブラリ（`java.util.zip` パッケージ）で行え、その処理自体は Memento パターンとは直接関係しないため、ここではコード例を省略します。Memento パターンの観点で大切なのは、圧縮と展開をどのクラスで行うかです。`ArticleMemento` クラスの中で行えば、`ArticleEditor` クラスは中身を知らないまま、これまでどおり Memento を預かれます。<br>
デメリットとして、Memento を作るたびに圧縮し、戻すたびに展開するため、処理時間がかかることが挙げられます。

### ファイルや DB に保存する

Memento をメモリ上に持ち続けるのではなく、ファイルや DB に書き出し、戻すときはファイルや DB から読み込む方法です。また、メモリ上には直近の Memento だけを残し、古いものからファイルや DB に書き出すという組み合わせ方をすることもできます。<br>
ファイルに保存するコード例は、深堀り⑤で紹介します（→ [【深堀り⑤】Memento をファイルや DB に保存する](#深堀り5)）。<br>
デメリットとして、次の 2 点が挙げられます。

- ファイルや DB の読み書きは、メモリ上の読み書きより時間がかかる。
- アプリを終了してもファイルや DB に Memento が残り続けるため、メモリ上だけで扱っていたときにはなかった注意点が生まれる（→ [Memento の賞味期限](#memento-の賞味期限)）。

<a id="深堀り5"></a>

## 【深堀り⑤】Memento をファイルや DB に保存する

Memento をファイルや DB に保存すると、メモリを空けられるだけでなく、アプリを終了しても Memento が消えなくなります。<br>
例えば、下書きの Memento をファイルに保存しておけば、アプリを再起動した後でも、下書き保存した時点の状態に戻せます。

ここでは、下書きの Memento をファイルに保存し、アプリの再起動後に読み込んで戻す例を見ていきます。履歴の Memento を古いものから書き出してメモリを空ける場合も、ファイルへの保存と読み込みの仕組みは同じです。なお、DB を使うにはドライバーの導入やテーブルの作成などの準備が必要になるため、本記事ではファイルに保存する例のみとします。

**`ArticleMemento.java`**

```java
package example.article;

public class ArticleMemento {
    private String body;
    private List<String> targetDepartments;

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

    /* ここを追加（ここから） */
    public void saveToFile(Path path) throws IOException {
        Files.writeString(path, String.join("\n", targetDepartments) + "\n\n" + body);
    }

    public static ArticleMemento loadFromFile(Path path) throws IOException {
        String text = Files.readString(path);
        int separator = text.indexOf("\n\n");
        List<String> targetDepartments = text.substring(0, separator).lines().toList();
        String body = text.substring(separator + 2);

        return new ArticleMemento(body, targetDepartments);
    }
    /* ここを追加（ここまで） */
}
```

`ArticleMemento` クラスを振り返ると、正しい実装から `saveToFile`・`loadFromFile` メソッドを追加しています。<br>
`saveToFile` メソッドにより、Memento の中身をテキストにしてファイルに書き出しています。書き出す際は、先頭から公開先の部署を 1 行に 1 つずつ書き、空行を 1 行挟んで本文を書いています。本文は改行を含むことがあるため、最後に置くことで、空行より後ろをすべて本文として読み込めるようにしています。<br>
`loadFromFile` メソッドにより、ファイルを読み込み、最初の空行より前を公開先の部署、後ろを本文として、Memento を作り直しています。

追加した 2 つのメソッドは、`ArticleEditor` クラスから呼び出せるように `public` にしています。公開しているのはファイルとのやり取りだけのため、`ArticleEditor` クラスが Memento の中身を直接取り出したり、書き換えたりできないことに変わりはありません。<br>
ただし、`loadFromFile` メソッドは `Article` クラスの `createMemento` メソッド以外で Memento を作れる入り口になり、元になるファイルの中身は外から書き換えられるため、作った Memento が `addTargetDepartment` メソッドのチェックを経た状態とは限りません。実務では、読み込んだ内容が正しいか（本記事では公開先の部署が重複していないか）を確かめてから戻す必要があります。

**`ArticleEditor.java`（一部抜粋）**

```java
package example;

import example.article.ArticleMemento;

public class ArticleEditor {
    private ArticleMemento draft;

    public void saveDraftToFile(Path path) throws IOException {
        draft.saveToFile(path);
    }

    public void loadDraftFromFile(Path path) throws IOException {
        draft = ArticleMemento.loadFromFile(path);
    }
}
```

※正しい実装から追加した `saveDraftToFile`・`loadDraftFromFile` メソッドと、それらで使う `draft` フィールドのみを記載し、他のフィールド・メソッドは省略しています。

`ArticleEditor` クラスを振り返ると、正しい実装から `saveDraftToFile`・`loadDraftFromFile` メソッドを追加しています。<br>
`saveDraftToFile` メソッドにより、下書きの Memento をファイルに書き出しています。書き出す際は、`ArticleMemento` クラスの `saveToFile` メソッドに処理を任せています。<br>
`loadDraftFromFile` メソッドにより、ファイルから読み込んだ Memento を `draft` フィールドに戻しています。読み込む際は、`ArticleMemento` クラスの `loadFromFile` メソッドに処理を任せています。<br>
2 つのメソッドは、アプリの終了前と再起動後に呼び出して使います。アプリを終了する前に `saveDraftToFile` メソッドを呼び出しておけば、再起動後に `loadDraftFromFile` メソッドで Memento を `draft` フィールドに戻し、`revertToDraft` メソッドで記事に反映することで、下書き保存した時点の状態に戻せます。

ここで注目したいのは、`ArticleEditor` クラスが Memento の中身を知らないまま、ファイルへの保存と読み込みを行えている点です。テキストへの変換と、テキストから Memento を作り直す処理を `ArticleMemento` クラスに持たせているため、Memento を中身の見えないオブジェクトとして預かるという関係は、保存先がメモリからファイルに変わっても崩れていません。<br>
なお、変換を `Article` クラスではなく `ArticleMemento` クラスに持たせているのは、ファイルに保存したいのが今の記事の状態ではなく、Memento に保存した過去の時点の状態だからです。また、過去の時点の状態をどこに残しておくかは記事そのものではなく編集画面の都合のため、`Article` クラスに持たせると、お知らせ記事を表すクラスに編集画面の都合が入り込んでしまいます。

DB に保存する場合も同じく、DB とのやり取りを `example.article` パッケージの側に持たせれば、`ArticleEditor` クラスは中身を知らないまま Memento を預かれます。

### `Serializable` インターフェースを使わない理由

Java には、クラスに `Serializable` インターフェースを実装するだけで、そのインスタンスをバイト列に変換（シリアライズ）してファイルに書き出したり、ファイルから読み込んでインスタンスに戻したり（デシリアライズ）できる仕組みもあります。<br>
しかし、本記事では次の理由から使っていません。

- 信頼できないデータをデシリアライズすると、細工されたデータによって意図しない処理を実行されるおそれがある。
    - 書籍『Effective Java』でも、Java のシリアライズより JSON などの代替手段を優先するよう勧めている。
- 保存されるのは Java でしか読み込めないバイト列のため、人が中身を確かめたり、Java 以外のシステムから読み込んだりできない。

詳しくは「安全でないデシリアライゼーション」や「Java デシリアライズ 脆弱性」で検索してみてください。

実務では、JSON 形式で保存するのが一般的です。JSON は多くのプログラミング言語やシステムで扱える形式で、Java では Jackson や Gson などのライブラリを使って、オブジェクトと JSON を相互に変換できます。Java の標準ライブラリには JSON を扱う機能がないため、本記事では標準ライブラリだけで書けるテキスト形式で保存しました。<br>
JSON 形式で保存する場合も、JSON への変換は `ArticleMemento` クラスなど `example.article` パッケージの側に持たせ、`ArticleEditor` クラスが中身を知らずに済む形にするのがポイントです。

### Memento の賞味期限

メモリ上の Memento は、アプリを終了すると消えるため、作られてから使われるまでの期間が短く、その間に `Article` クラスの作りや社内の状況が変わることは考えにくいです。<br>
一方、ファイルや DB に保存した Memento は、数か月後や数年後に読み込まれることもあります。その間に状況が変わると、保存した時点では正しかった Memento が、戻す時点では正しく使えなくなります。つまり、ファイルや DB に保存した Memento には、正しく使える期間（賞味期限）があります。

賞味期限が切れる原因として、次の 2 つが挙げられます。

- クラスの変更
- 社内の状況の変化

クラスの変更による例として、記事に「公開日」を持たせるため、`Article`・`ArticleMemento` クラスにフィールドを追加し、`saveToFile` メソッドでファイルの先頭に公開日を書き出すように修正したとします。修正前に保存したファイルには公開日の行がないため、修正後の `loadFromFile` メソッドで読み込むと、先頭にある公開先の部署を公開日として読み取ろうとして、読み込みに失敗してしまいます。

社内の状況の変化による例として、公開先に「総務部」を設定した下書きを保存した後、組織変更で総務部が廃止されたとします。Memento は保存した時点の状態をそのまま戻すため、この下書きに戻すと、すでに存在しない総務部が公開先に戻ってしまいます。

こうした問題を防ぐため、実務では次のような対策をとります。

- Memento に、保存したときのクラスの版（バージョン）を持たせておき、読み込んだ Memento の版が古ければ、新しい形に変換するか、戻せないことを利用者に伝える。
- 戻す前に、Memento の状態が現在も正しいか（公開先の部署が存在するか、など）を確かめる。
- 保存期間を決めておき、期間を過ぎた Memento は捨てる。

これらの対策も、Memento パターンの役割分担に沿って置き場所を決めます。版の確認や、戻す前の中身の確認は、Memento の中身に触れる処理のため、`Article`・`ArticleMemento` クラスなど `example.article` パッケージの側で行います。一方、保存期間を過ぎたかどうかはファイルの更新日時などから判断でき、Memento の中身を知らなくても行えるため、Memento を預かる `ArticleEditor` クラスなど `example` パッケージの側で行えます。

<a id="深堀り6"></a>

## 【深堀り⑥】GoF デザインパターンとの位置づけ

今回使った Memento パターンは、GoF（Gang of Four）の 23 のデザインパターンのうち「振る舞いパターン」に分類されます。<br>
詳しくは「GoF」で検索してみてください。
