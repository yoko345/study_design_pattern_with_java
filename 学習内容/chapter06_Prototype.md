# Prototype（プロトタイプ）パターン ― オブジェクトを複製して生成する

次のような経験をしたことはありませんか？

> 扱う機能が増えるたびに管理クラスを修正し続けなければならず、条件分岐がどんどん増えてしまった

この記事では、EC サイトの通知配信システムへの機能追加というシナリオを通して、Prototype パターンがこの問題をどのように解決するかを学びます。

## 目次

- [【具体例】](#具体例)
    - [シナリオ](#シナリオ)
    - [既存コードの仕様](#既存コードの仕様)
    - [`CardNotification` クラスと `BannerNotification` クラスの仕様](#通知フォーマットクラスの仕様)
- [好ましくない実装](#好ましくない実装)
- [正しい実装](#正しい実装)
    - [コピーコンストラクタとの比較](#コピーコンストラクタとの比較)
- [まとめ](#まとめ)
- [【深堀り①】`clone()` の仕組み](#深堀り1)
- [【深堀り②】浅いコピーと深いコピー](#深堀り2)
- [【深堀り③】`NotificationManager` がサブクラスを知らなくていい理由](#深堀り3)
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

- `Main`（実行クラス）

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

<a id="通知フォーマットクラスの仕様"></a>

### 通知フォーマットクラスの仕様

本記事の主題に集中できるよう、通知フォーマットを担う 2 つのクラス（`CardNotification`・`BannerNotification`）の仕様を先に示します。

<br>

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

ぱっと思いつくのは、既存の `NotificationSender` に型ごとの分岐を追加する方法ではないでしょうか？

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

- 通知タイプを追加するたびに既存コードの修正が必要になる
    - 特に、条件分岐が長くなり、コードの見通しが悪くなる
- 呼び出し側のクラス（`NotificationSender`）が具体的な通知クラス（`CardNotification`、`BannerNotification`）をすべて把握しておく必要がある
- CSS クラス名（`"coupon"`、`"campaign"` など）が、呼び出し側のクラス（`NotificationSender`）にハードコードされているため、通知タイプを追加するたびに条件分岐と CSS クラス名の両方を修正する必要がある
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

- 抽象クラス `Notification` は[マーカーインタフェース](#cloneable-と-clone-に関して) `Cloneable` を実装している
- `Notification` を継承するクラスは、`send` メソッドをオーバーライドしないといけない
- `createCopy` メソッドが呼ばれると、[`clone` メソッド](#cloneable-と-clone-に関して)が呼び出されて、`Notification` 型でキャストした値が返る

次に、抽象クラス `Notification` を継承したクラスを見てください。

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

上記から次のことを変更しただけで、処理の実装は変わっていないことがわかります。

- `Notification` を継承している
- `send` メソッドがオーバーライドされている

最後に、通知管理を担う `NotificationManager` クラスを見てください。

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

- `register` メソッドにより、任意の名前で `Notification` クラスのインスタンスを管理できる
    - `CardNotification` や `BannerNotification` が登場していないため、`NotificationManager` は抽象クラス `Notification` だけを知っていれば動作する
- `create` メソッドにより、`register` メソッドで登録した名前の `Notification` クラスのインスタンスのコピーを取得できる

実行クラスと実行結果は次のようになります。

```Java:Main.java
public class Main {
    public static void main(String[] args) {
        NotificationManager manager = new NotificationManager();

        CardNotification coupon = new CardNotification("coupon");
        BannerNotification campaign = new BannerNotification("campaign");
        CardNotification newsletter = new CardNotification("newsletter");

        manager.register("coupon", coupon);
        manager.register("campaign", campaign);
        manager.register("newsletter", newsletter);

        Notification notificationCoupon = manager.create("coupon");
        System.out.println(notificationCoupon.send("クーポン: 10%OFF"));

        Notification notificationCampaign = manager.create("campaign");
        System.out.println(notificationCampaign.send("夏のキャンペーン開始！"));

        Notification notificationNewsletter = manager.create("newsletter");
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
    - 条件分岐がないので、コードの見通しが良い
- 具体的な通知クラス（`CardNotification`、`BannerNotification`）をすべて把握する必要のあった呼び出し側のクラス（`NotificationSender`）が不要となり、責務の分離ができる
    - CSS クラス名（`"coupon"`、`"campaign"` など）をハードコードしていた呼び出し側のクラス（`NotificationSender`）が不要となったため、修正箇所が実行クラスのみとなり、追加ミスや修正漏れが起きにくい

<a id="コピーコンストラクタとの比較"></a>

### コピーコンストラクタとの比較

#### `Cloneable` と `clone` に関して

`Cloneable` はインターフェースで以下の定義になっています。

```Java:Cloneable.java
package java.lang;

public interface Cloneable {
}
```

> 引用元: OpenJDK [Cloneable.java](https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/lang/Cloneable.java)

上記を見ると、メソッドが 1 つも宣言されていないことがわかります。<br>
これは、`Cloneable` を実装することで「このクラスは `clone` によるコピーを許可する」という印をつけるためのものだからです。<br>
このような印をつけるインタフェースのことをマーカーインタフェース（marker interface）と呼びます。

つまり、`clone` メソッドはインターフェース `Cloneable` の中で宣言されているわけではないということです。<br>
では、どこで宣言されているかというと `java.lang.Object` クラスで宣言されています。

```Java:Object.java
package java.lang;

public class Object {
    @IntrinsicCandidate
    protected native Object clone() throws CloneNotSupportedException;
}
```

> 引用元: OpenJDK [Object.java](https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/lang/Object.java)

`Object` クラスは Java の全てのクラスが継承しているため、どのクラスでも使用できます。<br>
一方で、`clone` メソッドの戻り値は `Object` 型なので、`Notification` クラスの `createCopy` メソッドにおいて、`Notification` 型でキャストしているわけです。

`clone` メソッドは、自身の浅いコピー（フィールドの内容をそのままコピーするだけで、フィールドの先にあるインスタンスの中身までは考慮しない）を生成して返すメソッドです。<br>
また、`Cloneable` を実装していないクラスで呼び出すと `CloneNotSupportedException` がスローされます。

上記から、`clone` メソッドを使用することは次のような面倒さがあります。

- アクセス修飾子が `protected` のため、継承関係を意識する必要がある
- `throws CloneNotSupportedException` があるため、例外処理を行う必要がある
    > もしインターフェース `Cloneable` の中で `clone` が宣言されていれば、例外処理のことを意識する必要はなかった
- 浅いコピーしか行わないため、設計者が別途必要としている処理のコピーを行うにはオーバーライドする必要がある

このような面倒さを解消した実装の 1 つが「コピーコンストラクタ」を使った実装になります。<br>
ここで、コピーコンストラクタとは、同じクラスのインスタンスを引数に受け取り、インスタンス生成時にフィールドのコピーを行うコンストラクタのことです。

#### コピーコンストラクタを使った実装

では実際にコードを見ていきましょう。

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

BannerNotification.java は CardNotification.java と同様の実装のため、省略。

実行クラスの変更はなし。

**実行結果**

```
<div class="card coupon"><p>クーポン: 10%OFF</p></div>
<div class="banner campaign"><strong>夏のキャンペーン開始！</strong></div>
<div class="card newsletter"><p>7月のメルマガ</p></div>
```

同様の結果を得ることができました。

抽象クラス `Notification` の `createCopy` メソッドを抽象メソッドとし、コピーコンストラクタを実装した `CardNotification` クラスで `this` を引数に渡してインスタンスを生成することにより、コピーを行うことができていることがわかります。<br>
これより、継承関係を意識せずにインスタンスのコピーが行なえ、例外処理の実装もなくなります。また、設計者が別途必要としている処理は抽象クラス `Notification` を継承したクラス内に記述するだけでよくなります。

## まとめ

正しい実装を見ると、通知の生成は `NotificationManager` が担っており、具体的なクラス名は一切登場しません。<br>
そのため、新しい通知タイプを追加しても `NotificationManager` を修正する必要がなく、既存コードに影響を与えません。

Prototype パターンは、事前に登録したオブジェクト（プロトタイプ）を複製することで新しいインスタンスを生成する仕組みです。<br>
これにより、呼び出し側は抽象クラスだけに依存した設計が可能になります。

本記事の内容はここまでとなります。

以降は「もう少し深く知りたい」という方向けの補足となります。今回学んだパターンに繋がる設計原則や、実務で役立つ背景知識について触れています。

---

<a id="深堀り1"></a>

## 【深堀り①】`clone()` の仕組み

`Object.clone()` を使いこなすには、まず `Cloneable` インターフェースを理解しておく必要があります。

`Cloneable` はメソッドを 1 つも持たない「マーカーインタフェース」です。実装することで「このクラスは複製を許可する」という意思表示になります。

```Java:Cloneable の実態（java.lang パッケージ）
public interface Cloneable {
    // メソッドは何もない
}
```

`Object.clone()` は `protected` アクセス修飾子を持ちます。`Cloneable` を実装していないクラスで `clone()` を呼ぶと `CloneNotSupportedException` がスローされます。

`Notification.createCopy()` はこの例外を `try-catch` でラップし、呼び出し元に例外を伝播させない設計になっています。

```Java:Notification.java
public Notification createCopy() {
    Notification n = null;

    try {
        n = (Notification) clone(); // Object.clone() を呼び出す
    } catch (CloneNotSupportedException e) {
        e.printStackTrace();
    }

    return n;
}
```

`Notification` が `Cloneable` を実装しているため、そのサブクラス（`CardNotification`、`BannerNotification`）でも追加の実装なしに `clone()` が正しく動作します。

<a id="深堀り2"></a>

## 【深堀り②】浅いコピーと深いコピー

`Object.clone()` が行うのは「浅いコピー（シャローコピー）」です。

| フィールドの型                       | コピーの挙動                     |
| ------------------------------------ | -------------------------------- |
| プリミティブ型（`char`、`int` など） | 値がそのままコピーされる         |
| 参照型（配列、独自クラスなど）       | 参照先のアドレスのみコピーされる |

今回の `CardNotification` は `frameChar`（`char` 型）しか持たないため、浅いコピーで問題はありません。

しかし、フィールドに参照型が含まれる場合は注意が必要です。

```Java:ComplexNotification.java（参照型フィールドを持つ例）
public class ComplexNotification extends Notification {
    private String[] tags; // 参照型のフィールド

    public ComplexNotification(String[] tags) {
        this.tags = tags;
    }

    @Override
    public ComplexNotification createCopy() {
        ComplexNotification copy = (ComplexNotification) super.createCopy();
        copy.tags = tags.clone(); // 配列も別途コピーして深いコピーにする
        return copy;
    }

    @Override
    public String send(String message) {
        // ... 省略
        return "";
    }
}
```

デフォルトの `clone()` では `tags` が元のオブジェクトと共有されます（同じ配列を参照する）。`createCopy()` をオーバーライドし、参照型フィールドを個別にコピーすることで「深いコピー（ディープコピー）」になります。参照型フィールドを持つクラスで Prototype パターンを使う際は、浅いコピー・深いコピーの違いを意識しておきましょう。

<a id="深堀り3"></a>

## 【深堀り③】`NotificationManager` がサブクラスを知らなくていい理由

`NotificationManager` のコードを改めて確認してみましょう。

```Java:NotificationManager.java
import java.util.HashMap;
import java.util.Map;

public class NotificationManager {
    private Map<String, Notification> map = new HashMap<>();

    public void register(String name, Notification prototype) {
        map.put(name, prototype);
    }

    public Notification create(String name) {
        Notification n = map.get(name);
        return n.createCopy();
    }
}
```

このクラスが参照しているのは `Notification`（抽象クラス）だけです。`CardNotification` も `BannerNotification` も import されていません。

これが可能な理由は、`createCopy()` が `Notification` 抽象クラスに定義されており、どのサブクラスに対しても同じ呼び出し方で複製できるからです。`NotificationManager` は複製の方法を知らなくてよく、「複製してください」と依頼するだけです。

新しい通知タイプ（例: `BoldNotification`）を追加したい場合の作業を比較すると、違いが明確です。

| 作業                               | 好ましくない実装 | Prototype パターン |
| ---------------------------------- | ---------------- | ------------------ |
| `BoldNotification` クラスの作成    | 必要             | 必要               |
| `NotificationSender` の修正        | 必要             | **不要**           |
| `register()` の呼び出し（Main 側） | 不要             | 必要               |

これは「開放/閉鎖原則（OCP: Open-Closed Principle）」の実践例です。`NotificationManager` は機能拡張（新しい通知タイプの追加）に対して開かれており、既存コードの変更に対しては閉じています。

<a id="深堀り4"></a>

## 【深堀り④】GoF デザインパターンとの位置づけ

今回使った Prototype パターンは、GoF（Gang of Four）の 23 のデザインパターンのうち「生成パターン」に分類されます。<br>
詳しくは「GoF」で検索してみてください。
