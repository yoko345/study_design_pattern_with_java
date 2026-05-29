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
        System.out.println(sender.send("注文が確定しました"));
    }
}
```

**実行結果**

```
[通知] 注文が確定しました
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
| `use`    | `String`   | カード形式の HTML 文字列を生成して返す |

```Java:CardNotification.java
public class CardNotification {
    private String cssClass;

    public CardNotification(String cssClass) {
        this.cssClass = cssClass;
    }

    public String use(String message) {
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
| `use`    | `String`   | バナー形式の HTML 文字列を生成して返す |

```Java:BannerNotification.java
public class BannerNotification {
    private String cssClass;

    public BannerNotification(String cssClass) {
        this.cssClass = cssClass;
    }

    public String use(String message) {
        return "<div class=\"banner " + cssClass + "\"><strong>" + message + "</strong></div>";
    }
}
```

※ここで一旦読むのを止めて、ご自身でコーディングを行なってみてください。その後で、続きを読んでください。

## 好ましくない実装

では、シナリオに従って複数種類の通知を送れるように実装をしていきましょう。

「通知の種類に応じてフォーマットを切り替えればよい」と考え、次のような実装をするのではないでしょうか？

```Java:NotificationManager.java
public class NotificationManager {
    public void send(String type, String message) {
        if (type.equals("coupon")) {
            CardNotification n = new CardNotification("coupon");
            System.out.println(n.use(message));
        } else if (type.equals("campaign")) {
            BannerNotification n = new BannerNotification("campaign");
            System.out.println(n.use(message));
        } else if (type.equals("newsletter")) {
            CardNotification n = new CardNotification("newsletter");
            System.out.println(n.use(message));
        }
    }
}
```

```Java:Main.java
public class Main {
    public static void main(String[] args) {
        NotificationManager manager = new NotificationManager();
        manager.send("coupon", "クーポン: 10%OFF");
        manager.send("campaign", "夏のキャンペーン開始！");
        manager.send("newsletter", "7月のメルマガ");
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

- 通知タイプを追加するたびに `NotificationManager` の修正が必要になる
    - `"coupon"`、`"campaign"` などの文字列で種類を管理しているため、追加ミスや修正漏れが起きやすい
    - 通知タイプが増えるほど `if-else` が長くなり、見通しが悪くなる
- `NotificationManager` が `CardNotification`、`BannerNotification` という具体的なクラス名をすべて把握しておく必要がある
    - 新しいフォーマットクラスを追加しても、`NotificationManager` を変更しない限り使用できない
- フォーマット設定（`"coupon"`、`"campaign"` といった CSS クラス名）が `NotificationManager` にハードコードされており、呼び出し元で自由に設定できない

## 正しい実装

では、好ましくない実装で挙げた問題点を解決するにはどうすればよいのでしょうか？

これらの問題を解決するのが **Prototype パターン**です。<br>
Prototype パターンでは、「あらかじめ登録しておいたオブジェクト（プロトタイプ）を複製して、新しいインスタンスを生成する」という仕組みをとります。

まず、次のコードを見てください。

```Java:Notification.java
public abstract class Notification implements Cloneable {
    public abstract String use(String message);

    public Notification createCopy() {
        Notification n = null;

        try {
            n = (Notification) clone();
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }

        return n;
    }
}
```

`Notification` は抽象クラスで、`use()` と `createCopy()` の 2 つのメソッドを持ちます。

- `use(String message)`: サブクラスごとに定義するフォーマット生成の本体です。HTML文字列を返します。
- `createCopy()`: `clone()` を呼び出して自身のコピーを返します。これが Prototype パターンの核心部分です。

`CardNotification` と `BannerNotification` はこの `Notification` を継承するサブクラスとして定義し直します。

```Java:CardNotification.java
public class CardNotification extends Notification {
    private String cssClass;

    public CardNotification(String cssClass) {
        this.cssClass = cssClass;
    }

    @Override
    public String use(String message) {
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
    public String use(String message) {
        return "<div class=\"banner " + cssClass + "\"><strong>" + message + "</strong></div>";
    }
}
```

`use()` に `@Override` を追加した以外、`CardNotification` と `BannerNotification` の中身に変更はありません。`createCopy()` は `Notification` クラスに実装されているので、各サブクラスで定義し直す必要はありません。

次に、`NotificationManager` を書き直します。

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

`NotificationManager` には `CardNotification` も `BannerNotification` も import されていません。`Notification`（抽象クラス）だけを知っていれば動作します。

- `register(String name, Notification prototype)`: プロトタイプを名前付きで登録します。
- `create(String name)`: 登録済みのプロトタイプを `createCopy()` で複製して返します。

実行クラスは次のようになります。

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

        Notification n1 = manager.create("coupon");
        System.out.println(n1.use("クーポン: 10%OFF"));

        Notification n2 = manager.create("campaign");
        System.out.println(n2.use("夏のキャンペーン開始！"));

        Notification n3 = manager.create("newsletter");
        System.out.println(n3.use("7月のメルマガ"));
    }
}
```

**実行結果**

```
<div class="card coupon"><p>クーポン: 10%OFF</p></div>
<div class="banner campaign"><strong>夏のキャンペーン開始！</strong></div>
<div class="card newsletter"><p>7月のメルマガ</p></div>
```

> `use()` が返すHTML文字列がそのままAPIのレスポンスボディとなります。ここでは確認のため `System.out.println()` で出力しています。

実行結果は好ましくない実装と変わりませんが、設計が大きく改善されています。

仮に新しい通知タイプ（たとえば太字フォーマットの `BoldNotification`）を追加したい場合、`Notification` を継承した新クラスを作成し、`register()` で登録するだけです。`NotificationManager` は一切修正不要です。

<a id="コピーコンストラクタとの比較"></a>

### コピーコンストラクタとの比較

オブジェクトの複製手段として、`clone()` の代わりに「コピーコンストラクタ」を使う方法があります。コピーコンストラクタとは、同じクラスのインスタンスを引数に受け取り、フィールドをコピーするコンストラクタのことです。

```Java:CardNotification.java（コピーコンストラクタを追加した場合）
public class CardNotification extends Notification {
    private String cssClass;

    public CardNotification(String cssClass) {
        this.cssClass = cssClass;
    }

    // コピーコンストラクタ
    public CardNotification(CardNotification other) {
        this.cssClass = other.cssClass;
    }

    // ... use() は省略
}
```

しかし、コピーコンストラクタを使って `NotificationManager.create()` を実装しようとすると、具体的なクラス名を知る必要が生じます。

```Java:NotificationManager.java（コピーコンストラクタを使おうとした場合）
public Notification create(String name) {
    Notification n = map.get(name);

    if (n instanceof CardNotification) {
        return new CardNotification((CardNotification) n); // 具体型を知っている
    } else if (n instanceof BannerNotification) {
        return new BannerNotification((BannerNotification) n); // 具体型を知っている
    }

    throw new IllegalArgumentException("Unknown type: " + name);
}
```

新しい通知タイプを追加するたびに `NotificationManager` の修正が再び必要になり、好ましくない実装と同じ問題が発生します。

| 観点                                                            | `clone()` による実装 | コピーコンストラクタ |
| --------------------------------------------------------------- | -------------------- | -------------------- |
| `NotificationManager` が具体クラスを知る必要があるか            | なし                 | あり                 |
| 新しい通知タイプを追加したときの `NotificationManager` への影響 | なし（修正不要）     | あり（修正が必要）   |
| ポリモーフィズムの活用                                          | できる               | できない             |

`clone()` を `Notification` 抽象クラスに定義することで、`NotificationManager` は具体的なクラスを一切知らなくてよくなります。これが、コピーコンストラクタではなく `clone()` を採用する最大の理由です。

## まとめ

Prototype パターンをまとめると、以下のとおりです。

| 要素                                      | 役割                                                     |
| ----------------------------------------- | -------------------------------------------------------- |
| `Notification`（抽象クラス）              | 複製メソッド `createCopy()` を持つプロトタイプの基底     |
| `CardNotification` / `BannerNotification` | 具体的なフォーマットを実装したプロトタイプ               |
| `NotificationManager`                     | プロトタイプを名前付きで登録し、複製して返すマネージャー |

Prototype パターンを使うメリットは以下のとおりです。

- **OCP に従った設計**: 新しい通知タイプを追加しても `NotificationManager` を変更する必要がない
- **具体クラスへの依存をなくせる**: `NotificationManager` は抽象クラス `Notification` だけを知ればよい
- **設定済みのオブジェクトを再利用できる**: 一度設定したプロトタイプを複製するので、フォーマット設定のばらつきを防げる

好ましくない実装との比較：

| 観点                             | 好ましくない実装                   | Prototype パターン |
| -------------------------------- | ---------------------------------- | ------------------ |
| 新しい通知タイプの追加           | `NotificationManager` の修正が必要 | 新クラスの追加のみ |
| `NotificationManager` の依存関係 | 全具体クラスを知っている           | 抽象クラスのみ     |
| OCP への適合                     | ✗                                  | ✓                  |

<a id="深堀り1"></a>

## 【深堀り①】`clone()` の仕組み

`Object.clone()` を使いこなすには、まず `Cloneable` インターフェースを理解しておく必要があります。

`Cloneable` はメソッドを 1 つも持たない「マーカーインターフェース」です。実装することで「このクラスは複製を許可する」という意思表示になります。

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
    public String use(String message) {
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
| `NotificationManager` の修正       | 必要             | **不要**           |
| `register()` の呼び出し（Main 側） | 不要             | 必要               |

これは「開放/閉鎖原則（OCP: Open-Closed Principle）」の実践例です。`NotificationManager` は機能拡張（新しい通知タイプの追加）に対して開かれており、既存コードの変更に対しては閉じています。

<a id="深堀り4"></a>

## 【深堀り④】GoF デザインパターンとの位置づけ

今回使った Prototype パターンは、GoF（Gang of Four）の 23 のデザインパターンのうち「生成パターン」に分類されます。<br>
詳しくは「GoF」で検索してみてください。
