# Memento（メメント）パターン ― カプセル化を壊さずにオブジェクトの状態を保存・復元する

次のような経験をしたことはありませんか？

> オブジェクトを以前の状態に戻す機能を作るために、呼び出す側でオブジェクトの値を 1 つずつ取り出して退避しておき、戻すときに 1 つずつ書き戻す実装にした。そのせいで、本来は外から自由に書き換えられたくない値まで、どこからでも書き換えられるようにする羽目になった。おまけに、退避したはずの値の一部だけが元に戻らない不具合を生んでしまうこともあった。

この記事では、社内ポータルのお知らせ記事の編集画面に「元に戻す」機能を追加するシナリオを通して、Memento パターンがこの問題をどのように解決するかを紹介します。

## 目次

- [【具体例】](#具体例)
    - [シナリオ](#シナリオ)
    - [既存コードの仕様](#既存コードの仕様)
- [好ましくない実装](#好ましくない実装)
- [正しい実装](#正しい実装)
- [まとめ](#まとめ)
- [【深堀り①】Memento の中身を隠すもう 1 つの方法（ネストクラス）](#深堀り1)
- [【深堀り②】Undo の 2 つの実現方法（Memento と Command）](#深堀り2)
- [【深堀り③】履歴の上限とメモリ](#深堀り3)
- [【深堀り④】Memento をファイルに保存する](#深堀り4)
- [【深堀り⑤】GoF デザインパターンとの位置づけ](#深堀り5)

---

## 【具体例】

### シナリオ

> あなたは社内ポータルの開発チームに所属しています。<br>
> 社内ポータルのお知らせ記事の編集画面には、編集中の内容を下書き保存する機能はありますが、操作を元に戻す機能がありません。そのため、誤って本文や公開先の部署を書き換えてしまうと、記事の担当者が記憶を頼りに手で直すしかありませんでした。<br>
> 先日、公開先を変更している途中で操作を誤り、人事部向けの記事が全部署向けの設定のまま公開されかける、というヒヤリハットがありました。これを受けて、編集画面に「1 つ前の操作に戻す」機能と、「編集を破棄して、最後に下書き保存した時点に戻す」機能を追加することになりました。

※実際のお知らせ記事の編集画面では、画面への表示や、下書きをデータベースへ保存する処理を行う実装が必要ですが、本記事では Memento パターンの解説に集中するため、コンソールへの文字列出力のみとします。

### 既存コードの仕様

※実務では、次の `Article` のようなエンティティクラスは専用のパッケージに切り出すのが一般的です。本記事でも、`Article` クラスは `example.article` パッケージに配置し、編集画面を表す `ArticleEditor` クラスと実行クラスは `example` パッケージに配置しています。

> ```
> example.article パッケージ
>   └── Article.java          お知らせ記事
>
> example パッケージ
>   ├── ArticleEditor.java    お知らせ記事の編集画面
>   └── Main.java             実行クラス
> ```

- `Article`（既存クラス）

お知らせ記事 1 件を表すクラスです。<br>
記事の本文と公開先の部署を保持します。公開先の部署は 1 つ以上必要なため、最後の 1 つを削除しようとすると例外を投げます。<br>
本文や公開先は、`getSummary` メソッドで表示用の文字列としてのみ取り出せます。

| フィールド          | 型             | 説明         |
| ------------------- | -------------- | ------------ |
| `body`              | `String`       | 本文         |
| `targetDepartments` | `List<String>` | 公開先の部署 |

| メソッド                 | 引数                | 戻り値の型 | 説明                                                                                   |
| ------------------------ | ------------------- | ---------- | -------------------------------------------------------------------------------------- |
| `changeBody`             | `String body`       | `void`     | 本文を変更する                                                                         |
| `addTargetDepartment`    | `String department` | `void`     | 公開先に部署を追加する                                                                 |
| `removeTargetDepartment` | `String department` | `void`     | 公開先から部署を削除する。公開先が 1 つしかない場合は `IllegalStateException` を投げる |
| `getSummary`             | なし                | `String`   | 本文と公開先をまとめた表示用の文字列を返す                                             |

**`Article.java`**

```java
package example.article;

public class Article {
    private String body;
    private List<String> targetDepartments = new ArrayList<>();

    public Article(String body, String targetDepartment) {
        this.body = body;
        targetDepartments.add(targetDepartment);
    }

    public void changeBody(String body) {
        this.body = body;
    }

    public void addTargetDepartment(String department) {
        targetDepartments.add(department);
    }

    public void removeTargetDepartment(String department) {
        if (targetDepartments.size() == 1) {
            throw new IllegalStateException("公開先の部署を空にすることはできません");
        }
        targetDepartments.remove(department);
    }

    public String getSummary() {
        return "本文：" + body + "／公開先：" + targetDepartments;
    }
}
```

<br>

- `ArticleEditor`（既存クラス）

お知らせ記事の編集画面を表すクラスです。<br>
本文の変更、公開先の追加・削除、下書き保存の操作を受け付け、操作後の記事の内容をコンソールに出力します。

| フィールド | 型        | 説明                 |
| ---------- | --------- | -------------------- |
| `article`  | `Article` | 編集中のお知らせ記事 |

| メソッド                 | 引数                | 戻り値の型 | 説明                                                             |
| ------------------------ | ------------------- | ---------- | ---------------------------------------------------------------- |
| `changeBody`             | `String body`       | `void`     | 記事の本文を変更し、変更後の内容をコンソールに出力する           |
| `addTargetDepartment`    | `String department` | `void`     | 記事の公開先に部署を追加し、追加後の内容をコンソールに出力する   |
| `removeTargetDepartment` | `String department` | `void`     | 記事の公開先から部署を削除し、削除後の内容をコンソールに出力する |
| `saveDraft`              | なし                | `void`     | 記事を下書き保存し、保存した内容をコンソールに出力する           |

**`ArticleEditor.java`**

```java
package example;

import example.article.Article;

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
        System.out.println("[下書き保存] " + article.getSummary());
    }
}
```

<br>

- `Main`（実行クラス）

**`Main.java`**

```java
package example;

import example.article.Article;

public class Main {
    public static void main(String[] args) {
        Article article = new Article("新しい評価制度の検討を始めます。", "人事部");
        ArticleEditor editor = new ArticleEditor(article);

        editor.changeBody("来月から新しい評価制度の検討を始めます。");
        editor.addTargetDepartment("総務部");
        editor.saveDraft();
    }
}
```

**実行結果**

```
[本文を変更] 本文：来月から新しい評価制度の検討を始めます。／公開先：[人事部]
[公開先を追加] 本文：来月から新しい評価制度の検討を始めます。／公開先：[人事部, 総務部]
[下書き保存] 本文：来月から新しい評価制度の検討を始めます。／公開先：[人事部, 総務部]
```

※ここで一旦読むのを止めて、ご自身でコーディングを行なってみてください。その後で、続きを読んでください。

## 好ましくない実装

では、シナリオに従い追加実装をしていきましょう。

まず思いつくのは、編集画面（`ArticleEditor` クラス）で、各操作の直前に記事の本文と公開先を取り出して退避しておき、元に戻すときにそれらを記事へ書き戻す、という実装ではないでしょうか？<br>
ただし、`Article` クラスは本文や公開先を表示用の文字列としてしか取り出せないため、退避と書き戻しに使うメソッドを `Article` クラスに追加する必要があります。本文は既存の `changeBody` メソッドで書き戻せるので、追加するのは、本文を取り出す `getBody` メソッド、公開先を取り出す `getTargetDepartments` メソッド、公開先を書き戻す `setTargetDepartments` メソッドの 3 つです。

**`Article.java`**

```java
package example.article;

public class Article {
    private String body;
    private List<String> targetDepartments = new ArrayList<>();

    public Article(String body, String targetDepartment) {
        this.body = body;
        targetDepartments.add(targetDepartment);
    }

    public void changeBody(String body) {
        this.body = body;
    }

    public void addTargetDepartment(String department) {
        targetDepartments.add(department);
    }

    public void removeTargetDepartment(String department) {
        if (targetDepartments.size() == 1) {
            throw new IllegalStateException("公開先の部署を空にすることはできません");
        }
        targetDepartments.remove(department);
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

import example.article.Article;

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
        draftTargetDepartments = article.getTargetDepartments();
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
        /* ここを追加（ここから） */
        draftBody = article.getBody();
        draftTargetDepartments = article.getTargetDepartments();
        /* ここを追加（ここまで） */
        System.out.println("[下書き保存] " + article.getSummary());
    }

    /* ここを追加（ここから） */
    public void undo() {
        if (bodyHistory.isEmpty()) {
            System.out.println("[元に戻す] 戻せる操作がありません");
            return;
        }
        article.changeBody(bodyHistory.pop());
        article.setTargetDepartments(targetDepartmentsHistory.pop());
        System.out.println("[元に戻す] " + article.getSummary());
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
    /* ここを追加（ここまで） */
}
```

`ArticleEditor` クラスには、次の処理を追加しています。

- 1 つ前に戻すための履歴として、本文の履歴（`bodyHistory`）と公開先の履歴（`targetDepartmentsHistory`）の 2 つのフィールドを追加している。
    - どちらも `Deque` インターフェースをスタック（後から入れたものを先に取り出す入れ物）として使い、`push` メソッドで積んだ値を、`pop` メソッドで新しいものから順に取り出す。
- 下書き時点に戻すための値として、`draftBody`・`draftTargetDepartments` フィールドを追加している。
    - 編集画面を開いた時点（コンストラクタ）と、下書き保存した時点（`saveDraft` メソッド）で、記事の本文と公開先を退避する。
- 各編集操作の直前に `saveHistory` メソッドを呼び出し、操作前の本文と公開先を履歴に積んでいる。
- 1 つ前に戻す `undo` メソッドと、下書き時点に戻す `revertToDraft` メソッドを追加している。
    - `revertToDraft` メソッドは編集を破棄する操作のため、下書き時点に戻した後に履歴を空にしている。

**`Main.java`**

```java
package example;

import example.article.Article;

public class Main {
    public static void main(String[] args) {
        Article article = new Article("新しい評価制度の検討を始めます。", "人事部");
        ArticleEditor editor = new ArticleEditor(article);

        editor.changeBody("来月から新しい評価制度の検討を始めます。");
        editor.addTargetDepartment("総務部");
        editor.saveDraft();

        /* ここを追加（ここから） */
        editor.changeBody("来月から全社で新しい評価制度を導入します。");
        editor.addTargetDepartment("営業部");
        editor.undo();
        editor.revertToDraft();
        /* ここを追加（ここまで） */
    }
}
```

`Main` クラスでは、下書き保存の後に、本文を誤った内容に書き換え、公開先に営業部を誤って追加してから、「1 つ前に戻す」と「下書きに戻す」を順に実行しています。

**実行結果**

```
[本文を変更] 本文：来月から新しい評価制度の検討を始めます。／公開先：[人事部]
[公開先を追加] 本文：来月から新しい評価制度の検討を始めます。／公開先：[人事部, 総務部]
[下書き保存] 本文：来月から新しい評価制度の検討を始めます。／公開先：[人事部, 総務部]
[本文を変更] 本文：来月から全社で新しい評価制度を導入します。／公開先：[人事部, 総務部]
[公開先を追加] 本文：来月から全社で新しい評価制度を導入します。／公開先：[人事部, 総務部, 営業部]
[元に戻す] 本文：来月から全社で新しい評価制度を導入します。／公開先：[人事部, 総務部, 営業部]
[下書きに戻す] 本文：来月から新しい評価制度の検討を始めます。／公開先：[人事部, 総務部, 営業部]
```

実行結果の 6 行目では、「1 つ前に戻す」を実行したにもかかわらず、誤って追加した営業部が公開先に残ったままになっています。さらに 7 行目では、本文は下書き保存した時点に戻っているのに、公開先には営業部が残っています。シナリオのヒヤリハットと同じく、人事部向けの記事が、ほかの部署にも公開される設定のままになってしまいました。

原因は、`getTargetDepartments` メソッドが、`Article` クラスが保持しているリストそのもの（参照）を返していることにあります。`ArticleEditor` クラスが履歴や下書きに退避していたのは、その時点の公開先の中身ではなく、`Article` クラスと共有している 1 つのリストでした。そのため、営業部を追加すると、退避しておいたはずのリストにも営業部が追加されてしまいます。<br>
一方、本文の `String` クラスは中身を書き換えられない（不変な）クラスで、`changeBody` メソッドは本文を別の文字列に差し替えるだけです。そのため、退避しておいた本文は書き換わらず、正しく元に戻せています。

この不具合は、`getTargetDepartments` メソッドがリストのコピーを返すように直せば解消できます。しかし、不具合を直したとしても、この実装には以下の問題点が残ります。

- 値を正しく退避するには、編集画面である `ArticleEditor` クラスが、`Article` クラスの内部の作り（公開先をリストで保持し、そのリストをそのまま返していることなど）まで知っていなければならない。
    - 今回のように内部の作りを知らないまま退避すると、コンパイルエラーも例外も発生しないまま、一部の値だけが元に戻らない不具合が生まれてしまう。
- 退避と書き戻しのために追加した `getTargetDepartments`・`setTargetDepartments` メソッドは `public` のため、`ArticleEditor` クラス以外のどこからでも呼び出せてしまう。
    - 例えば、`setTargetDepartments` メソッドに空のリストを渡したり、`getTargetDepartments` メソッドで取り出したリストを直接空にしたりすれば、`removeTargetDepartment` メソッドのチェックを経由せずに、公開先を空にできてしまう。
- `Article` クラスにフィールド（例えば「公開日」）を追加するたびに、`ArticleEditor` クラスにも履歴用・下書き用のフィールドを追加し、コンストラクタと `saveDraft`・`undo`・`revertToDraft`・`saveHistory` メソッドをすべて修正しなければならない。
    - 修正が 1 か所でも漏れると、公開日だけが元に戻らない不具合が生まれるが、1 つ目の問題点と同じく、コンパイルエラーも例外も発生しないため気づきにくい。

## 正しい実装

では、好ましくない実装で挙げた問題点を解決するにはどうすればよいのでしょうか？

これらの問題を解決するのが **Memento パターン**です。<br>
編集画面が記事の値を 1 つずつ取り出して退避するのをやめ、記事自身に「ある時点の自分の状態」を 1 つのオブジェクトにまとめて作らせます。編集画面はそのオブジェクトを中身を見ないまま預かっておき、元に戻すときは、そのオブジェクトを記事に渡して、記事自身に状態を戻させます。<br>
この「ある時点の状態をまとめたオブジェクト」を、Memento（メメント：英語で「記念品」「形見」の意味）と呼びます。

では、実装を見ていきましょう。<br>
※本記事では下記のクラス構成としています。

> ```
> example.article パッケージ
>   ├── Article.java          お知らせ記事（既存の仕様から本実装に合わせて修正）
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

`ArticleMemento` は新たに追加したクラスで、ある時点の記事の本文と公開先を保持します。ポイントは次の 2 点です。

- コンストラクタと `getBody`・`getTargetDepartments` メソッドには、`public` などのアクセス修飾子を付けていない。
    - アクセス修飾子を付けないコンストラクタやメソッドは、同じパッケージ（`example.article`）のクラスからしか呼び出せない（パッケージ・プライベート）。そのため、`Article` クラスからは呼び出せる一方、`example` パッケージにある `ArticleEditor` クラスは、`ArticleMemento` クラスのインスタンスを変数に入れたり引数に渡したりすることはできても、中身を取り出すことも、新たに作ることもできない。
- コンストラクタで、受け取った公開先のリストをコピーしている。
    - 受け取ったリストをそのまま保持すると、好ましくない実装と同じく `Article` クラスとリストを共有してしまうため、コピーした新しいリストを保持している。
    - また、フィールドには `final` を付け、インスタンスを作った後でフィールドが別の値に差し替えられないようにしている。

次に、Memento を作る側であり、Memento を受け取って状態を戻す側でもある `Article` クラスを見ていきましょう。

**`Article.java`**

```java
package example.article;

public class Article {
    private String body;
    private List<String> targetDepartments = new ArrayList<>();

    public Article(String body, String targetDepartment) {
        this.body = body;
        targetDepartments.add(targetDepartment);
    }

    public void changeBody(String body) {
        this.body = body;
    }

    public void addTargetDepartment(String department) {
        targetDepartments.add(department);
    }

    public void removeTargetDepartment(String department) {
        if (targetDepartments.size() == 1) {
            throw new IllegalStateException("公開先の部署を空にすることはできません");
        }
        targetDepartments.remove(department);
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

- `createMemento` メソッドは、現在の本文と公開先から `ArticleMemento` クラスのインスタンスを作って返す。
- `restoreMemento` メソッドは、受け取った `ArticleMemento` クラスのインスタンスから本文と公開先を取り出し、自身の状態を元に戻す。
    - 公開先のリストは、ここでもコピーしてから保持している。これは、同じ下書きの Memento を使って 2 回以上戻す場合に備えるためである。Memento のリストをそのまま保持すると、戻した後の編集で Memento のリストまで書き換わり、2 回目に戻したときには下書き時点の状態に戻らなくなってしまう。

状態を取り出したり書き戻したりする処理はすべて `Article` クラスの中に収まっているため、好ましくない実装のような getter・setter を追加する必要はありません。<br>
また、`ArticleMemento` クラスのインスタンスは `example.article` パッケージの外では作れず、実際に作っているのは `Article` クラスの `createMemento` メソッドだけです。そのため、`restoreMemento` メソッドで、公開先が空のような、チェックを経由していない状態に戻されることもありません。

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
        /* ここを追加（ここから） */
        draft = article.createMemento();
        /* ここを追加（ここまで） */
        System.out.println("[下書き保存] " + article.getSummary());
    }

    /* ここを追加（ここから） */
    public void undo() {
        if (history.isEmpty()) {
            System.out.println("[元に戻す] 戻せる操作がありません");
            return;
        }
        article.restoreMemento(history.pop());
        System.out.println("[元に戻す] " + article.getSummary());
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
- 下書き時点に戻すための Memento として、`draft` フィールドを追加している。
    - 編集画面を開いた時点（コンストラクタ）と、下書き保存した時点（`saveDraft` メソッド）で、`createMemento` メソッドを呼び出して Memento を作っている。
- 1 つ前に戻す `undo` メソッドと、下書き時点に戻す `revertToDraft` メソッドを追加している。
    - どちらも、預かっていた Memento を `restoreMemento` メソッドに渡すだけで、記事の状態を戻している。

好ましくない実装と比べると、本文用・公開先用に分かれていた履歴と下書きのフィールドが、それぞれ `ArticleMemento` クラスの 1 つのフィールドにまとまっています。また、`ArticleEditor` クラスは Memento の中身（本文や公開先）に一切触れず、履歴と下書きという 2 つの用途で Memento を預かり、必要なときに `Article` クラスへ渡しているだけです。

最後に、実行クラスを見ていきましょう。

**`Main.java`**

```java
package example;

import example.article.Article;

public class Main {
    public static void main(String[] args) {
        Article article = new Article("新しい評価制度の検討を始めます。", "人事部");
        ArticleEditor editor = new ArticleEditor(article);

        editor.changeBody("来月から新しい評価制度の検討を始めます。");
        editor.addTargetDepartment("総務部");
        editor.saveDraft();

        /* ここを追加（ここから） */
        editor.changeBody("来月から全社で新しい評価制度を導入します。");
        editor.addTargetDepartment("営業部");
        editor.undo();
        editor.revertToDraft();
        /* ここを追加（ここまで） */
    }
}
```

`Main` クラスは、好ましくない実装と同じです。

**実行結果**

```
[本文を変更] 本文：来月から新しい評価制度の検討を始めます。／公開先：[人事部]
[公開先を追加] 本文：来月から新しい評価制度の検討を始めます。／公開先：[人事部, 総務部]
[下書き保存] 本文：来月から新しい評価制度の検討を始めます。／公開先：[人事部, 総務部]
[本文を変更] 本文：来月から全社で新しい評価制度を導入します。／公開先：[人事部, 総務部]
[公開先を追加] 本文：来月から全社で新しい評価制度を導入します。／公開先：[人事部, 総務部, 営業部]
[元に戻す] 本文：来月から全社で新しい評価制度を導入します。／公開先：[人事部, 総務部]
[下書きに戻す] 本文：来月から新しい評価制度の検討を始めます。／公開先：[人事部, 総務部]
```

実行結果の 6 行目では、「1 つ前に戻す」によって、誤って追加した営業部が公開先から外れています。また 7 行目では、本文・公開先ともに、下書き保存した時点の状態に戻っています。

以上のような実装を行うと、以下のメリットがあります。

- 状態を退避・復元する処理が `Article` クラスの中にまとまっているため、`ArticleEditor` クラスは `Article` クラスの内部の作りを知らなくても、記事を正しく元に戻せる。
    - リストのコピーのように、内部の作りを知っていなければ書けない処理は、`Article`・`ArticleMemento` クラスだけが受け持っている。
- 退避と書き戻しのための getter・setter を追加する必要がないため、`removeTargetDepartment` メソッドのチェックを経由せずに公開先を空にする、といった書き換えはできない。
    - `ArticleMemento` クラスの中身も、`example.article` パッケージの外からは取り出すことも作ることもできない。
- `Article` クラスにフィールド（例えば「公開日」）を追加しても、修正するのは同じパッケージにある `Article`・`ArticleMemento` クラスだけで、`ArticleEditor` クラスには一切手を加える必要がない。

## まとめ

正しい実装を振り返ると、`ArticleEditor` クラスは記事の状態の中身を一切知らないまま、`Article` クラスが作った Memento を預かり、必要なときに `Article` クラスへ渡して元に戻させるだけでよくなっています。<br>
このように Memento パターンは、状態の保存と復元をオブジェクト自身に任せ、保存した状態を中身の見えないオブジェクトとして外部に預けることで、カプセル化を壊さずにオブジェクトを以前の状態に戻せるようにする設計パターンです。

本記事の内容はここまでとなります。

以降は「もう少し深く知りたい」という方向けの補足となります。今回学んだパターンに繋がる設計原則や、実務で役立つ背景知識について触れています。

---

<a id="深堀り1"></a>

## 【深堀り①】Memento の中身を隠すもう 1 つの方法（ネストクラス）

正しい実装では、`ArticleMemento` クラスのコンストラクタと getter をパッケージ・プライベートにすることで、`example` パッケージにある `ArticleEditor` クラスから中身を隠しました。<br>
ただし、パッケージ・プライベートは、同じパッケージのクラスすべてに公開されます。そのため、例えば今後 `example.article` パッケージに記事を検索するクラスを追加すると、そのクラスからも `ArticleMemento` クラスの中身を取り出せてしまいます。

中身を見られるクラスを `Article` クラスだけに絞りたい場合は、Memento を `Article` クラスの中に、ネストクラス（クラスの中で宣言したクラス）として定義する方法があります。

**`Article.java`（一部抜粋）**

```java
package example.article;

public class Article {
    private String body;
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

※コンストラクタと既存のメソッド（`changeBody` メソッドなど）は省略しています。

ネストクラスの `Memento` クラスは、コンストラクタもフィールドも `private` です。しかし Java では、外側のクラスから、ネストクラスの `private` なメンバーにアクセスできます。そのため、`Article` クラスの `restoreMemento` メソッドでは `memento.body` のようにフィールドを直接読み出せる一方、`Article` クラスの外からは、同じパッケージのクラスであっても、中身を取り出すことも新たに作ることもできません。<br>
また、`static` を付けたネストクラスは、外側のクラスのインスタンスとは結びつかない、独立したクラスとして扱われます。`ArticleEditor` クラスからは `Article.Memento` という型名で参照でき、`Deque<Article.Memento>` のように、正しい実装と同じ形で Memento を預かれます。

なお、GoF の原典（C++ で書かれています）では、`friend` という仕組みを使い、Memento の中身を Originator（本記事の `Article` クラスにあたる、Memento を作って状態を戻す側のクラス）にだけ公開しています。Java には `friend` にあたる仕組みがないため、パッケージ・プライベートかネストクラスで代用します。<br>
パッケージ・プライベートは、Memento を独立したファイルのクラスとして扱える一方、同じパッケージのクラスには中身が見えてしまいます。ネストクラスは、中身を見られるクラスを `Article` クラスだけに絞れる一方、`Article` クラスのファイルに Memento の定義まで含まれるため、保存する状態が増えるほどファイルが長くなります。どこまで厳密に中身を隠したいかに応じて使い分けるとよいでしょう。

<a id="深堀り2"></a>

## 【深堀り②】Undo の 2 つの実現方法（Memento と Command）

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

- Memento の方式は、操作の種類に関係なく状態を丸ごと保存して戻すため、操作ごとに取り消し方を考える必要がない。一方、保存する状態が大きいほど、操作のたびに多くのメモリを使う（→ [【深堀り③】履歴の上限とメモリ](#深堀り3)）。
- Command の方式は、「どの部署を追加したか」のような操作の内容だけを記録するため、保存する量が少なくて済む。一方、操作の種類ごとに取り消す処理を書く必要があり、その処理を誤ると正しく元に戻らない。

実務では、両者を組み合わせることもあります。例えば、状態がどう変わるかが複雑で、取り消す処理を書きにくい操作では、Command に操作前の状態の Memento を持たせておき、`undo` メソッドでその Memento を使って戻す、という形です。<br>
詳しくは「Command パターン」で検索してみてください。

<a id="深堀り3"></a>

## 【深堀り③】履歴の上限とメモリ

正しい実装の `ArticleEditor` クラスは、編集操作のたびに `ArticleMemento` クラスのインスタンスを 1 つ作り、`history` フィールドに積んでいます。`revertToDraft` メソッドを呼び出さない限り履歴は空にならないため、編集を続けるほど、`history` フィールドが保持する Memento は増え続けます。

なお、本文の `String` クラスは不変なため、本文を変更していない Memento どうしは同じ文字列を共有しており、本文がその都度複製されるわけではありません。一方、公開先のリストは Memento を作るたびにコピーされます。保存する状態が大きいほど（例えば、大きな表や画像のデータなど）、Memento 1 つあたりのメモリも大きくなります。

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

それでもメモリが足りない場合は、状態を丸ごと保存するのではなく、Command の方式（→ [【深堀り②】Undo の 2 つの実現方法（Memento と Command）](#深堀り2)）のように、操作の内容だけを記録する方法を検討します。

<a id="深堀り4"></a>

## 【深堀り④】Memento をファイルに保存する

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

<a id="深堀り5"></a>

## 【深堀り⑤】GoF デザインパターンとの位置づけ

今回使った Memento パターンは、GoF（Gang of Four）の 23 のデザインパターンのうち「振る舞いパターン」に分類されます。<br>
詳しくは「GoF」で検索してみてください。
