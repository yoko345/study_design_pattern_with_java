# Prototype（プロトタイプ）パターン ― オブジェクトを複製して生成する

次のような経験をしたことはありませんか？

> 扱う機能が増えるたびに管理クラスを修正し続けなければならず、条件分岐がどんどん増えてしまった

この記事では、EC サイトの通知配信システムへの機能追加というシナリオを通して、Prototype パターンがこの問題をどのように解決するかを学びます。

## 目次

- [【具体例】](#具体例)
    - [シナリオ](#シナリオ)
    - [既存コードの仕様](#既存コードの仕様)
    - [通知フォーマットクラスの仕様](#通知フォーマットクラスの仕様)
- [好ましくない実装](#好ましくない実装)
- [正しい実装](#正しい実装)
    - [コピーコンストラクタとの比較](#コピーコンストラクタとの比較)
        - [`Cloneable` と `clone` に関して](#cloneable-と-clone-に関して)
        - [コピーコンストラクタを使った実装](#コピーコンストラクタを使った実装)
- [まとめ](#まとめ)
- [【深堀り①】浅いコピー（シャローコピー）と深いコピー（ディープコピー）](#深堀り1)
- [【深堀り②】OCP（オープン・クローズドの原則）](#深堀り2)
- [【深堀り③】実行クラスでの型宣言 ― 抽象型 vs 具体型](#深堀り3)
- [【深堀り④】GoF デザインパターンとの位置づけ](#深堀り4)

---

## 【具体例】

### シナリオ

> あなたは EC サイトの開発チームに所属しています。<br>
> 通知配信システムの基本機能はすでに稼働しています。<br>
> ある日、マーケティング部門から「クーポン配布・キャンペーン告知・定期メルマガなど、種類によって見た目を変えた通知を送りたい」という要望が届きました。<br>
> 各通知タイプはそれぞれ独自の HTML フォーマットを持ち、通知 API のレスポンスとして返却されます。

### 既存コードの仕様

- `NotificationSender`（既存クラス）

通知メッセージを文字列として返すクラスです。

| メソッド | 戻り値の型 | 説明                             |
| -------- | ---------- | -------------------------------- |
| `send`   | `String`   | 通知メッセージを文字列として返す |

```Java:NotificationSender.java
public class NotificationSender {
    public String send(String message) {
        return "[通知] " + message;
    }
}
```

<br>

- 実行クラス

```Java:Main.java
public class Main {
    public static void main(String[] args) {
        NotificationSender sender = new NotificationSender();
        System.out.println(sender.send("お知らせがあります。"));
    }
}
```

**実行結果**

```
[通知] お知らせがあります。
```

<br>

<a id="通知フォーマットクラスの仕様"></a>

### 通知フォーマットクラスの仕様

本記事の主題に集中できるよう、通知フォーマットを担う 2 つのクラス（`CardNotification`・`BannerNotification`）の仕様を先に示します。

- `CardNotification`

メッセージをカード形式の HTML 文字列に変換するクラスです。

| フィールド | 型       | 説明                        |
| ---------- | -------- | --------------------------- |
| `cssClass` | `String` | 追加で付与する CSS クラス名 |

| メソッド | 戻り値の型 | 説明                                   |
| -------- | ---------- | -------------------------------------- |
| `send`   | `String`   | カード形式の HTML 文字列を生成して返す |

```Java:CardNotification.java
public class CardNotification {
    private String cssClass;

    public CardNotification(String cssClass) {
        this.cssClass = cssClass;
    }

    public String send(String message) {
        return "<div class=\"card " + cssClass + "\"><p>" + message + "</p></div>";
    }
}
```

<br>

- `BannerNotification`

メッセージをバナー形式の HTML 文字列に変換するクラスです。

| フィールド | 型       | 説明                        |
| ---------- | -------- | --------------------------- |
| `cssClass` | `String` | 追加で付与する CSS クラス名 |

| メソッド | 戻り値の型 | 説明                                   |
| -------- | ---------- | -------------------------------------- |
| `send`   | `String`   | バナー形式の HTML 文字列を生成して返す |

```Java:BannerNotification.java
public class BannerNotification {
    private String cssClass;

    public BannerNotification(String cssClass) {
        this.cssClass = cssClass;
    }

    public String send(String message) {
        return "<div class=\"banner " + cssClass + "\"><strong>" + message + "</strong></div>";
    }
}
```

※ここで一旦読むのを止めて、ご自身でコーディングを行なってみてください。その後で、続きを読んでください。

## 好ましくない実装

では、シナリオに従って複数種類の通知を送れるように実装をしていきましょう。

真っ先に思いつくのは、既存の `NotificationSender` に型ごとの分岐を追加する方法ではないでしょうか？

```Java:NotificationSender.java
public class NotificationSender {
    public String send(String type, String message) {
        if (type.equals("coupon")) {
            CardNotification cardNotification = new CardNotification(type);
            return cardNotification.send(message);
        } else if (type.equals("campaign")) {
            BannerNotification bannerNotification = new BannerNotification(type);
            return bannerNotification.send(message);
        } else if (type.equals("newsletter")) {
            CardNotification cardNotification = new CardNotification(type);
            return cardNotification.send(message);
        }
        return "";
    }
}
```

```Java:Main.java
public class Main {
    public static void main(String[] args) {
        NotificationSender sender = new NotificationSender();
        System.out.println(sender.send("coupon", "クーポン: 10%OFF"));
        System.out.println(sender.send("campaign", "夏のキャンペーン開始！"));
        System.out.println(sender.send("newsletter", "7月のメルマガ"));
    }
}
```

**実行結果**

```
<div class="card coupon"><p>クーポン: 10%OFF</p></div>
<div class="banner campaign"><strong>夏のキャンペーン開始！</strong></div>
<div class="card newsletter"><p>7月のメルマガ</p></div>
```

コンパイルエラーがなく結果が出力されていることから、実装・動作確認ともに問題ないことがわかります。

しかし、この実装には以下の問題点があります。

- 通知タイプを追加するたびに既存コード（`NotificationSender`）の修正が必要になる
    - 条件分岐が長くなり、コードの見通しが悪くなる
- 呼び出し側のクラス（`NotificationSender`）が具体的な通知クラス（`CardNotification`・`BannerNotification`）をすべて把握しておく必要がある
- CSS クラス名（`"coupon"`、`"campaign"` など）が、呼び出し側のクラス（`NotificationSender`）にハードコードされているため、仕様変更や通知タイプを追加するたびに条件分岐と CSS クラス名の両方を修正・追加する必要がある
    - 修正箇所が複数に分散するため、追加ミスや修正漏れが起きやすい

## 正しい実装

では、好ましくない実装で挙げた問題点を解決するにはどうすればよいのでしょうか？

これらの問題を解決するのが **Prototype パターン**です。

まず、次のコードを見てください。

```Java:Notification.java
public abstract class Notification implements Cloneable {
    public abstract String send(String message);

    public Notification createCopy() {
        Notification notification = null;

        try {
            notification = (Notification) clone();
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }

        return notification;
    }
}
```

上記から次のことがわかります。

- 抽象クラス `Notification` は、[マーカーインタフェース](#cloneable-と-clone-に関して) `Cloneable` を実装している
- `Notification` を継承するクラスは、`send` メソッドをオーバーライドすることを強制される
- `createCopy` メソッドが呼ばれると、[`clone` メソッド](#cloneable-と-clone-に関して)が呼び出されて、`Notification` 型でキャストした値が返る

<br>

次に、抽象クラス `Notification` を継承したクラス（`CardNotification`・`BannerNotification`）を見てください。

```Java:CardNotification.java
public class CardNotification extends Notification {
    private String cssClass;

    public CardNotification(String cssClass) {
        this.cssClass = cssClass;
    }

    @Override
    public String send(String message) {
        return "<div class=\"card " + cssClass + "\"><p>" + message + "</p></div>";
    }
}
```

```Java:BannerNotification.java
public class BannerNotification extends Notification {
    private String cssClass;

    public BannerNotification(String cssClass) {
        this.cssClass = cssClass;
    }

    @Override
    public String send(String message) {
        return "<div class=\"banner " + cssClass + "\"><strong>" + message + "</strong></div>";
    }
}
```

上記を見ると、処理の実装は変わらず、次の点だけ変更したことがわかります。

- `Notification` を継承している
- `send` メソッドをオーバーライドしている

最後に、新たに追加実装する、通知管理を担う `NotificationManager` クラスを見てください。

```Java:NotificationManager.java
public class NotificationManager {
    private Map<String, Notification> map = new HashMap<>();

    public void register(String name, Notification prototype) {
        map.put(name, prototype);
    }

    public Notification create(String name) {
        Notification notification = map.get(name);
        return notification.createCopy();
    }
}
```

上記から次のことがわかります。

- `register` メソッドにより、任意の名前で `Notification` のサブクラスのインスタンスを管理できる
    - サブクラス（`CardNotification`・`BannerNotification`）が登場していないため、`NotificationManager` は抽象クラス `Notification` だけを知っていれば動作する
- `create` メソッドの引数に、`register` メソッドで登録した名前を渡すことで、`Notification` のサブクラスのインスタンスのコピーを取得できる

実行クラスと実行結果は次のようになります。

```Java:Main.java
public class Main {
    public static void main(String[] args) {
        NotificationManager manager = new NotificationManager();

        CardNotification couponCard = new CardNotification("coupon");
        BannerNotification campaignBanner = new BannerNotification("campaign");
        CardNotification newsletterCard = new CardNotification("newsletter");

        manager.register("クーポン", couponCard);
        manager.register("キャンペーン", campaignBanner);
        manager.register("メルマガ", newsletterCard);

        Notification notificationCoupon = manager.create("クーポン");
        System.out.println(notificationCoupon.send("クーポン: 10%OFF"));

        Notification notificationCampaign = manager.create("キャンペーン");
        System.out.println(notificationCampaign.send("夏のキャンペーン開始！"));

        Notification notificationNewsletter = manager.create("メルマガ");
        System.out.println(notificationNewsletter.send("7月のメルマガ"));
    }
}
```

**実行結果**

```
<div class="card coupon"><p>クーポン: 10%OFF</p></div>
<div class="banner campaign"><strong>夏のキャンペーン開始！</strong></div>
<div class="card newsletter"><p>7月のメルマガ</p></div>
```

以上のような実装を行うことで、次のメリットが得られます。

- 通知管理を担う `NotificationManager` クラスにより、通知タイプが増えても既存コードを変更することなく、実行クラスで `register` メソッドに登録するだけで良くなる
    - `NotificationSender` クラスに追加したときのような条件分岐がないので、コードの見通しが良い
- 具体的な通知クラス（`CardNotification`・`BannerNotification`）をすべて把握する必要のあった呼び出し側のクラス（`NotificationSender`）が不要となり、責務の分離ができる
    - `NotificationManager` がインスタンス管理と複製を担う
    - 実行クラスは登録と呼び出しだけに専念できる
- CSS クラス名（`"coupon"`、`"campaign"` など）をハードコードしていた呼び出し側のクラス（`NotificationSender`）が不要となったため、修正箇所が実行クラスのみとなり、追加ミスや修正漏れが起きにくい

今回の実装では `clone` メソッドを使ってインスタンスのコピーを行いました。<br>
インスタンスのコピーをする方法は他にもあります。<br>
以降では、`clone` メソッドを使う方法と、コピーコンストラクタを使う方法を比較してみます。

<a id="コピーコンストラクタとの比較"></a>

### コピーコンストラクタとの比較

コピーコンストラクタを使った実装を見る前に、`Cloneable` と `clone` に関して理解を深めていきましょう。

#### `Cloneable` と `clone` に関して

`Cloneable` はインターフェースで、以下の定義になっています。

```Java:Cloneable.java
package java.lang;

public interface Cloneable {
}
```

> 引用元: OpenJDK [Cloneable.java](https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/lang/Cloneable.java)

上記を見ると、メソッドが 1 つも宣言されていないことがわかります。<br>
これは、`Cloneable` を実装することで「このクラスは `clone` によるコピーを許可する」という印をつけるためのものだからです。<br>
このような印をつけるためのインタフェースを「**マーカーインタフェース（marker interface）**」と呼びます。<br>
つまり、`clone` メソッドはインターフェース `Cloneable` の中で宣言されているわけではないということです。

では、どこで宣言されているかというと `java.lang.Object` クラスの中で宣言されています。

```Java:Object.java
package java.lang;

public class Object {
    @IntrinsicCandidate
    protected native Object clone() throws CloneNotSupportedException;
}
```

> 引用元: OpenJDK [Object.java](https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/lang/Object.java)

`Object` クラスは Java のすべてのクラスが継承しているため、どのクラスでも使用できます。<br>
`clone` メソッドを見ると次のことがわかります。

1. 戻り値の型が `Object` 型である
2. `throws` 句で `CloneNotSupportedException` が指定されている

1 つ目より、`Notification` クラスの `createCopy` メソッドの処理において、`clone` メソッドを `Notification` 型でキャストしているわけです。
また、2 つ目より、`Cloneable` を実装していないクラスで `clone` メソッドを呼び出すと `CloneNotSupportedException` がスローされるため、例外処理を行っているわけです。

> ※ 親クラス `Notification` のサブクラス（`CardNotification`・`BannerNotification`）は、親クラスが `Cloneable` を実装しているため、サブクラスで実装をしなくても `clone` メソッドが正しく動きます。

ちなみに、`clone` メソッドは自身の浅いコピーを生成して返すメソッドです。

> ※ フィールドの内容をそのままコピーするだけで、フィールドの先にあるインスタンスの中身までは考慮しないということ。

以上から、`clone` メソッドを使った実装をする際には、次のようなことを意識しないといけない面倒さがあります。

- アクセス修飾子が `protected` のため、継承関係を意識する必要がある
- `throws` 句があるため、例外処理を行う必要がある
    > もしインターフェース `Cloneable` の中で `clone` が宣言されていれば、例外処理のことを意識する必要はなかった
- 浅いコピーしか行わないため、設計者が別途必要としている処理のコピーを行うには、`clone` メソッドをオーバーライドしたり、今回の実装では `createCopy` メソッドをオーバーライドして個別にコピーしたり、といった対応を取る必要がある（→ [【深堀り①】浅いコピーと深いコピー](#深堀り1)）

このような面倒さを解消した実装の 1 つが「コピーコンストラクタ」を使った実装になります。<br>

> ※ コピーコンストラクタ：同じクラスのインスタンスを引数に受け取り、インスタンス生成時にフィールドのコピーを行うコンストラクタのこと。

#### コピーコンストラクタを使った実装

では、実際にコードを見ていきましょう。

```Java:Notification.java
public abstract class Notification { // ← ここを修正
    public abstract String send(String message);

    public abstract Notification createCopy(); // ← ここを修正
}
```

```Java:CardNotification.java
public class CardNotification extends Notification {
    private String cssClass;

    public CardNotification(String cssClass) {
        this.cssClass = cssClass;
    }

    /* コピーコンストラクタの実装（ここから） */
    public CardNotification(CardNotification prototype) {
        this.cssClass = prototype.cssClass;
    }
    /* コピーコンストラクタの実装（ここまで） */

    @Override
    public String send(String message) {
        return "<div class=\"card " + cssClass + "\"><p>" + message + "</p></div>";
    }

    /* ここを追加（ここから） */
    @Override
    public Notification createCopy() {
        return new CardNotification(this);
    }
    /* ここを追加（ここまで） */
}
```

`BannerNotification` クラスは `CardNotification` クラスと同様の処理の流れとなるため、省略。

実行クラスの変更はなし。

**実行結果**

```
<div class="card coupon"><p>クーポン: 10%OFF</p></div>
<div class="banner campaign"><strong>夏のキャンペーン開始！</strong></div>
<div class="card newsletter"><p>7月のメルマガ</p></div>
```

実行結果を見てわかるように、同様の結果を得ることができました。

抽象クラス `Notification` では、`createCopy` メソッドを抽象メソッドとします。<br>
`CardNotification` クラスでは、コピーコンストラクタを実装し、`createCopy` メソッドの具体的な処理として、`this` を引数に渡して `CardNotification` クラスのインスタンスを生成します。<br>
この一連の処理により、インスタンスのコピーを行うことができます。

このように、継承関係を意識せずにインスタンスのコピーが行え、例外処理の実装もなくなります。また、設計者が別途必要としている処理は、コピーコンストラクタ内に記述するだけで済みます。そのため、実務ではこちらの実装を行うことが多いです。

## まとめ

正しい実装を見ると、通知の生成は `NotificationManager` が担っており、具体的なクラス名は一切登場しません。<br>
そのため、新しい通知タイプを追加しても `NotificationManager` を修正する必要がなく、既存コードに影響を与えません。

Prototype パターンは、事前に登録したオブジェクト（プロトタイプ）を複製することで新しいインスタンスを生成する仕組みです。<br>
これにより、呼び出し側は抽象クラスだけに依存した設計が可能になります。

本記事の内容はここまでとなります。

以降は「もう少し深く知りたい」という方向けの補足となります。今回学んだパターンに繋がる設計原則や、実務で役立つ背景知識について触れています。

---

<a id="深堀り1"></a>

## 【深堀り①】浅いコピー（シャローコピー）と深いコピー（ディープコピー）

本記事では、`clone` メソッドが「浅いコピー」しか行わないことに触れました。<br>
ここでは、「浅いコピー」と「深いコピー」の違いと、深いコピーが必要な場合の対応方法を学びます。

まずは、フィールドの型に対する「浅いコピー」と「深いコピー」の違いを見ていきましょう。

| フィールドの型                       | 浅いコピーの挙動                                                                       | 深いコピーの挙動                                                                       |
| ------------------------------------ | -------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------- |
| プリミティブ型（`char`、`int` など） | 値がそのままコピーされる                                                               | 値がそのままコピーされる                                                               |
| 参照型・不変（`String` など）        | 参照先のアドレスのみコピーされる（不変オブジェクトのため、コピー元と参照先を共有する） | 参照先のアドレスのみコピーされる（不変オブジェクトのため、コピー元と参照先を共有する） |
| 参照型・可変（配列、独自クラスなど） | 参照先のアドレスのみコピーされる（可変オブジェクトのため、コピー元と参照先を共有する） | 参照先のインスタンスも含めて新しくコピーされる（コピー元と独立した参照先を持つ）       |

正しい実装の `CardNotification`・`BannerNotification` は `String` 型（参照型・不変）のフィールドしか持たないため、「浅いコピー」である `clone` メソッドを呼び出すだけで問題はありませんでした。

しかし、フィールドに配列などの可変オブジェクト（参照型・可変）が含まれる場合は、「浅いコピー」だと参照先が同じになってしまうため、「深いコピー」の対応をする必要があります。

ここから、通知に複数のタグ（カテゴリ名など）を付与できる `TaggedNotification` クラスを追加する場合の実装を見ながら対応方法を見ていきましょう。

> ※下記で使用する `Notification` クラスはコピーコンストラクタを用いる前の抽象クラスです。

```Java:TaggedNotification.java
public class TaggedNotification extends Notification {
    private String cssClass;
    private String[] tags;

    public TaggedNotification(String cssClass, String[] tags) {
        this.cssClass = cssClass;
        this.tags = tags;
    }

    @Override
    public Notification createCopy() {
        TaggedNotification taggedNotification = (TaggedNotification) super.createCopy();
        taggedNotification.tags = tags.clone();
        return taggedNotification;
    }

    @Override
    public String send(String message) {
        String tagStr = String.join(", ", tags);
        return "<div class=\"tagged " + cssClass + "\"><p>[タグ: " + tagStr + "] " + message + "</p></div>";
    }
}
```

上記を見ると次のことがわかります。

- 親クラスの `createCopy` メソッドをオーバーライドしている
- 親クラスの `createCopy` メソッドを呼び出した値を `TaggedNotification` にキャストしている<br>
  → 親クラスの `createCopy` メソッドの戻り値の型は `Notification` のため、キャストしないと `tags` フィールドにアクセスできない
- コピーしたインスタンスの `tags` に対して、配列の `clone` メソッドを呼び出して個別にコピーしている

`String` 型のフィールドである `cssClass` は不変（immutable）オブジェクトのため、「浅いコピー」で問題ありません。<br>
しかし、`String[]` 型のフィールドである `tags` は可変（mutable）オブジェクトです。そのため、デフォルトの `clone` メソッドでは、コンストラクタ `TaggedNotification` を呼び出した際に設定した `tags` と同じ参照先になってしまいます。<br>
そこで、親クラスの `createCopy` メソッドをオーバーライドし、`tags` を別途 `clone` することで、コピー後のインスタンスが独立した配列を持てるようになります。

```Java:Main.java
public class Main {
    public static void main(String[] args) {
        NotificationManager manager = new NotificationManager();

        TaggedNotification tagged = new TaggedNotification("sale", new String[]{"セール", "夏"});
        manager.register("tagged", tagged);

        Notification taggedNotification = manager.create("tagged");
        System.out.println(taggedNotification.send("夏のセール開始！"));
    }
}
```

**実行結果**

```
<div class="tagged sale"><p>[タグ: セール, 夏] 夏のセール開始！</p></div>
```

上記を見ると実行クラスの処理の流れに変更はなく、コンパイルエラーもなく結果が出力されていることがわかります。

このように、参照型・可変のフィールドを持つクラスで Prototype パターンを使う場合は、別途コピーを行い、「深いコピー」にする必要があります。<br>
実装の際は、「浅いコピーで十分か」を意識することが、Prototype パターンを安全に使う上での重要なポイントになります。

> ちなみに、`TaggedNotification` を追加した際に `NotificationManager` の変更をしていなかったことに気がついたでしょうか？
>
> 変更する必要がなかった理由は、深堀り②で扱います（→ [【深堀り②】OCP（オープン・クローズドの原則）](#深堀り2)）。

### 補足（コピーコンストラクタを使った実装）

`TaggedNotification` クラスにて、コピーコンストラクタを用いた実装は以下のとおりです。<br>
しかし、次のように `tags` のような可変フィールドを持つ場合は、`tags.clone()` の記述があることからもわかるように別途対応が必要になるため、コピーコンストラクタによるメリットは薄いです。

> とはいえ、`TaggedNotification` クラス内にキャストの処理が無くなるメリットはそれなりに大きいと思います。

```Java:TaggedNotification.java
public class TaggedNotification extends Notification {
    private String cssClass;
    private String[] tags;

    public TaggedNotification(String cssClass, String[] tags) {
        this.cssClass = cssClass;
        this.tags = tags;
    }

    public TaggedNotification(TaggedNotification prototype) {
        this.cssClass = prototype.cssClass;
        this.tags = prototype.tags.clone();
    }

    @Override
    public Notification createCopy() {
        return new TaggedNotification(this);
    }

    @Override
    public String send(String message) {
        String tagStr = String.join(", ", tags);
        return "<div class=\"tagged " + cssClass + "\"><p>[タグ: " + tagStr + "] " + message + "</p></div>";
    }
}
```

<a id="深堀り2"></a>

## 【深堀り②】OCP（オープン・クローズドの原則）

本記事のパターンを使った実装では、深堀り①で登場した `TaggedNotification` を追加した際も、`NotificationManager` には一切手を加えていません。<br>
`NotificationManager` が依存しているのは抽象クラス `Notification` だけであるため、具体的なサブクラスが何であっても、登録・複製ともに対応できます。<br>
そのため、通知パターンを追加する際は、実行クラスでサブクラスのインスタンスを生成し、`NotificationManager` の `register` メソッドを呼び出すだけで済みます。

この「既存コードを変えずに、新しいクラスを追加するだけで機能を拡張できる」という設計は、「**OCP（Open/Closed Principle：オープン・クローズドの原則）**」と呼ばれる設計原則の実践です。Prototype パターンは OCP を実現するための設計手段の一つと言えます。

詳しくは「OCP」や「オープン・クローズドの原則」で検索してみてください。

<a id="深堀り3"></a>

## 【深堀り③】実行クラスでの型宣言 ― 抽象型 vs 具体型

本記事の実行クラスのコードで、以下のような疑問を持った方もいるのではないでしょうか？

> `register` メソッドは `Notification` 型を受け取るので、<br>
> 下記のように変数 `coupon` の型を具体クラス `CardNotification` で宣言するのではなく、
>
> ```Java:Main.java
> public class Main {
>     public static void main(String[] args) {
>         NotificationManager manager = new NotificationManager();
>
>         CardNotification coupon = new CardNotification("coupon");
>
>         manager.register("coupon", coupon);
>
>         〜省略〜
>     }
> }
> ```
>
> 下記のように抽象クラス `Notification` で宣言してもよいのではないか？
>
> ```Java:Main.java
> public class Main {
>     public static void main(String[] args) {
>         NotificationManager manager = new NotificationManager();
>
>         Notification coupon = new CardNotification("coupon");
>
>         manager.register("coupon", coupon);
>
>         〜省略〜
>     }
> }
> ```
>
> ※ `BannerNotification` の登録処理は記載内容が変わらないため省略しています。

上記のコードは、どちらもコンパイルエラーがなく動作します。

ここでは、「具体クラス」と「抽象クラス」における宣言の使い分けに関して学びます。

結論から言うと、**その後の使い道**で使い分けます。

もし宣言した変数で、具体クラスの固有のメソッドを呼び出す必要がある場合は、「具体クラス」で宣言する必要があります。<br>
一方で、`register` に渡すだけなら、「この変数は `Notification` として扱う」という設計の意図が伝わるため、「抽象クラス」で宣言するほうが良いです。

実行クラスは、どのオブジェクトを組み合わせるかを決める唯一の場所（Composition Root）です。<br>
つまり、ここは具体型を知っていてよい場所であり、設計上「`NotificationManager` が具体型を知らない」ことのほうが重要です。<br>
変数の型宣言は、後続コードで何が必要かを考えて選ぶ習慣をつけると、コードの意図が読み手に伝わりやすくなります。

<a id="深堀り4"></a>

## 【深堀り④】GoF デザインパターンとの位置づけ

今回使った Prototype パターンは、GoF（Gang of Four）の 23 のデザインパターンのうち「生成パターン」に分類されます。<br>
詳しくは「GoF」で検索してみてください。
