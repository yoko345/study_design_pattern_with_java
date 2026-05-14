# Singleton パターン ― インスタンスをただひとつに

こんな経験はありませんか？

> あるクラスを複数の場所で `new` して使っていたら、一方で変更した設定が他方に反映されず、原因の特定に時間がかかってしまった

同じクラスなのに「なぜか状態がバラバラ」――その原因のひとつが、**インスタンスが複数存在していること**です。

この記事では、注文管理システムへのログ機能追加というシナリオを通して、Singleton パターンがこの問題をどのように解決するかを学びます。

## 目次

- [【具体例】](#具体例)
    - [シナリオ](#シナリオ)
    - [既存コードの仕様](#既存コードの仕様)
- [好ましくない実装](#好ましくない実装)
- [正しい実装](#正しい実装)
- [まとめ](#まとめ)
- [【深堀り①】private コンストラクタが必要な理由](#深堀り1)
- [【深堀り②】スレッドセーフな Singleton](#深堀り2)
- [【深堀り③】インスタンス数を n 個に制限するパターン](#深堀り3)
- [【深堀り④】GoF デザインパターンとの位置づけ](#深堀り4)

---

## 【具体例】

### シナリオ

> あなたは EC サイトの開発チームに所属しています。<br>
> 注文管理システムの開発が進む中、テックリードから「各処理の実行状況をログに残したい」という方針が出ました。<br>
> あなたはログ出力を担当する `Logger` クラスを実装し、各サービスクラスから使えるようにします。

### 既存コードの仕様

- `OrderService`（既存クラス）

注文の受け付け処理を担うクラスです。

| メソッド | 戻り値の型 | 説明 |
| --- | --- | --- |
| `placeOrder` | `void` | 注文を受け付ける |

- `PaymentService`（既存クラス）

決済の処理を担うクラスです。

| メソッド | 戻り値の型 | 説明 |
| --- | --- | --- |
| `processPayment` | `void` | 決済処理を開始する |

```Java:OrderService.java
public class OrderService {
    public void placeOrder(String orderId) {
        System.out.println("注文を受け付けました: " + orderId);
    }
}
```

```Java:PaymentService.java
public class PaymentService {
    public void processPayment(String paymentId) {
        System.out.println("決済処理を開始します: " + paymentId);
    }
}
```

```Java:Main.java
public class Main {
    public static void main(String[] args) {
        OrderService orderService = new OrderService();
        PaymentService paymentService = new PaymentService();

        orderService.placeOrder("ORDER-001");
        paymentService.processPayment("PAY-001");
    }
}
```

**実行結果**

```
注文を受け付けました: ORDER-001
決済処理を開始します: PAY-001
```

シナリオの段階では `Logger` クラスはまだ存在しません。<br>
あなたは、ログ出力クラスとして `Logger` を以下の仕様で新たに作成します。

| フィールド | 型 | 説明 |
| --- | --- | --- |
| `logLevel` | `String` | ログの出力レベル（例： `"INFO"`、`"ERROR"`） |

| メソッド | 戻り値の型 | 説明 |
| --- | --- | --- |
| `log` | `void` | ログメッセージをコンソールへ出力する |

```Java:Logger.java
public class Logger {
    private String logLevel;

    public Logger(String logLevel) {
        this.logLevel = logLevel;
        System.out.println("Logger を生成しました。[logLevel=" + logLevel + "]");
    }

    public void log(String message) {
        System.out.println("[" + logLevel + "] " + message);
    }
}
```

## 好ましくない実装

では、シナリオに従い、`Logger` を各サービスクラスから使えるようにしましょう。

「各サービスが自分のログを出せるようにすればよい」と考え、次のような実装をするのではないでしょうか？

```Java:OrderService.java
public class OrderService {
    private Logger logger = new Logger("INFO");

    public void placeOrder(String orderId) {
        logger.log("注文を受け付けました: " + orderId);
    }
}
```

```Java:PaymentService.java
public class PaymentService {
    private Logger logger = new Logger("INFO");

    public void processPayment(String paymentId) {
        logger.log("決済処理を開始します: " + paymentId);
    }
}
```

```Java:Main.java
public class Main {
    public static void main(String[] args) {
        OrderService orderService = new OrderService();
        PaymentService paymentService = new PaymentService();

        orderService.placeOrder("ORDER-001");
        paymentService.processPayment("PAY-001");
    }
}
```

**実行結果**

```
Logger を生成しました。[logLevel=INFO]
Logger を生成しました。[logLevel=INFO]
注文を受け付けました: ORDER-001
決済処理を開始します: PAY-001
```

実行結果から、`Logger` が **2 つ生成されている**ことがわかります。

コンパイルエラーがなく結果も出力されていますが、この実装には以下の問題点があります。

- **ログレベルの変更が全体に反映されない**
    - 例えば「本番環境では `"ERROR"` レベルのみ出力したい」という方針変更があった場合、`OrderService` と `PaymentService` のそれぞれに修正が必要になる
    - サービスクラスが増えるほど、修正漏れのリスクが高まる
- **同じ設定のインスタンスが無駄に複数存在する**
    - ログの設定は本来アプリ全体で共有すべきものであり、クラスごとに個別管理する必要はない
    - 将来ログの書き込み先をファイルに変更した際、複数のインスタンスが同じファイルに書き込もうとして競合するリスクがある

## 正しい実装

では、好ましくない実装で触れた問題点を解決するにはどうすればよいのでしょうか？

これらの問題を解決するのが **Singleton パターン**です。<br>
まずは、次のコードを見てください。

```Java:Logger.java
public class Logger {
    private static Logger instance = new Logger("INFO");
    private String logLevel;

    private Logger(String logLevel) {
        this.logLevel = logLevel;
        System.out.println("Logger を生成しました。[logLevel=" + logLevel + "]");
    }

    public static Logger getInstance() {
        return instance;
    }

    public void log(String message) {
        System.out.println("[" + logLevel + "] " + message);
    }
}
```

`Logger` クラスに 3 つの変更を加えました。

1. `private static Logger instance = new Logger("INFO")`
    - クラスがロードされたタイミングで `Logger` のインスタンスを 1 つだけ生成し、`static` フィールドとして保持します
    - `static` フィールドはクラスに属するため、JVM 全体で 1 つしか存在しません
2. `private Logger(String logLevel)`（コンストラクタを `private` に変更）
    - クラスの外から `new Logger(...)` することができなくなります
    - インスタンスを取得する手段を `getInstance()` のみに限定するための変更です
3. `public static Logger getInstance()`
    - `static` フィールドに保持されているインスタンスを返すメソッドです
    - クラスの外からインスタンスを取得する唯一の入口となります

各サービスクラスは `Logger.getInstance()` を使うように変更します。

```Java:OrderService.java
public class OrderService {
    public void placeOrder(String orderId) {
        Logger.getInstance().log("注文を受け付けました: " + orderId);
    }
}
```

```Java:PaymentService.java
public class PaymentService {
    public void processPayment(String paymentId) {
        Logger.getInstance().log("決済処理を開始します: " + paymentId);
    }
}
```

`Main` クラスは変更不要です。

**実行結果**

```
Logger を生成しました。[logLevel=INFO]
注文を受け付けました: ORDER-001
決済処理を開始します: PAY-001
```

「Logger を生成しました」が **1 回だけ**出力されており、インスタンスが 1 つしか存在しないことが確認できます。

また、`Logger.getInstance()` が返すのは常に同じインスタンスです。試しに `getInstance()` を 2 回呼び出し、同一のインスタンスかどうか確認してみましょう。

```Java:Main.java
public class Main {
    public static void main(String[] args) {
        Logger logger1 = Logger.getInstance();
        Logger logger2 = Logger.getInstance();

        if (logger1 == logger2) {
            System.out.println("logger1 と logger2 は同じインスタンスです。");
        } else {
            System.out.println("logger1 と logger2 は同じインスタンスではありません。");
        }
    }
}
```

**実行結果**

```
Logger を生成しました。[logLevel=INFO]
logger1 と logger2 は同じインスタンスです。
```

このように、Singleton パターンを適用すると以下のメリットがあります。

- **ログ設定の変更が 1 か所で済む**
    - `Logger` クラスの `logLevel` を変更するだけで、全サービスクラスの出力レベルが一括で変わる
- **インスタンスの無駄な生成がなくなる**
    - 全サービスクラスが同じインスタンスを共有するため、設定の不整合が起きない

## まとめ

Singleton パターンは、以下の 3 要素で構成されます。

| 要素 | 内容 |
| --- | --- |
| `private static` フィールド | インスタンスを 1 つだけ生成し、クラス変数として保持する |
| `private` コンストラクタ | 外部からの `new` を禁止し、インスタンス取得を `getInstance()` のみに限定する |
| `public static getInstance()` | 唯一のインスタンスを返す入口 |

Singleton パターンは、次のような場面で適しています。

- アプリ全体で 1 つだけ存在すべきリソースを管理したいとき（ログ出力、アプリ設定、接続プールなど）
- 複数箇所から同じ状態を参照・更新する必要があるとき

本記事の内容はここまでとなります。

以降は「もう少し深く知りたい」という方向けの補足となります。今回学んだパターンに繋がる設計原則や、実務で役立つ背景知識について触れています。

---

<a id="深堀り1"></a>

## 【深堀り①】private コンストラクタが必要な理由

Singleton パターンにおいて、コンストラクタを `private` にするのはなぜでしょうか？

理由はシンプルで、コンストラクタを `public`（もしくは修飾子なし）のままにすると、クラスの外から自由に `new` できてしまうからです。

```java
// コンストラクタが public のままの場合
Logger logger1 = Logger.getInstance(); // OK
Logger logger2 = new Logger("ERROR");  // これもコンパイルが通ってしまう
```

`getInstance()` で管理しているインスタンスとは別に、`new` によるインスタンスが生成されてしまいます。<br>
これでは「インスタンスをひとつに絞る」というパターンの目的が達成できません。

コンストラクタを `private` にすることで、インスタンスの生成をクラス内部に閉じ込め、`getInstance()` だけを外部への窓口にすることができます。

<a id="深堀り2"></a>

## 【深堀り②】スレッドセーフな Singleton

EC サイトのような Web アプリケーションでは、同時に複数のリクエストをさばくために、処理を**複数のスレッド**で並行実行するケースがよくあります。<br>
`Logger` もマルチスレッド環境から呼び出されることを考えると、スレッドセーフな実装が求められます。

本記事の実装では、`static` フィールドの宣言時にインスタンスを生成しています（**早期初期化**と呼ばれる方式）。

```java
private static Logger instance = new Logger("INFO");
```

`static` フィールドの初期化は JVM によってクラスロード時に一度だけ実行されます。<br>
Java の仕様上、クラスロードはスレッドセーフに行われるため、この方式では**マルチスレッド環境においても複数インスタンスが生成される心配がありません**。

### 遅延初期化の問題

早期初期化に対して、`getInstance()` が初めて呼ばれたときにインスタンスを生成する方式を**遅延初期化**（Lazy Initialization）と言います。

```java
public class Logger {
    private static Logger instance = null; // まだ生成しない

    private Logger(String logLevel) { ... }

    public static Logger getInstance() {
        if (instance == null) {            // ①
            instance = new Logger("INFO"); // ②
        }
        return instance;
    }
}
```

しかしこの実装には問題があります。

スレッド A とスレッド B が同時に `getInstance()` を呼び出した場合を考えてみましょう。

1. スレッド A が①を実行し、`instance == null` と判定する
2. スレッド A が②を実行する前に、スレッド B が①を実行し、こちらも `instance == null` と判定する
3. スレッド A が②を実行し、インスタンスを生成する
4. スレッド B も②を実行し、さらにもう 1 つインスタンスを生成してしまう

結果として、Singleton であるはずなのに 2 つのインスタンスが存在することになります。

### synchronized による解決

この問題を解決する方法の一つが `synchronized` です。

```java
public static synchronized Logger getInstance() {
    if (instance == null) {
        instance = new Logger("INFO");
    }
    return instance;
}
```

`synchronized` をメソッドに付与すると、同時に 1 つのスレッドしかそのメソッドを実行できなくなります。<br>
これにより、複数スレッドが同時に `if (instance == null)` を通過することがなくなり、インスタンスの二重生成を防げます。

書籍の演習問題（`TicketMaker`）でも、同じ `synchronized` の仕組みが使われています。

```Java:TicketMaker.java
public class TicketMaker {
    private static TicketMaker ticketMaker = new TicketMaker();
    private int ticket = 1000;

    public static TicketMaker createTicket() {
        return ticketMaker;
    }

    public synchronized int getTicketNumber() {
        return ticket++;
    }
}
```

`getTicketNumber()` に `synchronized` が付いているのは、チケット番号のインクリメント（`ticket++`）がスレッドセーフでないためです。<br>
`ticket++` は「読み取り → 加算 → 書き込み」の 3 ステップで構成されており、マルチスレッドだと処理が割り込まれて番号が重複するリスクがあります。<br>
`synchronized` を付けることで、同時に 1 スレッドだけが番号を取得できるようになり、重複を防いでいます。

なお、実務では早期初期化（本記事の `Logger` の実装）を採用することでインスタンス生成自体の同期を避けるアプローチが一般的です。<br>
`synchronized` はロックの取得・解放にコストがかかるため、パフォーマンスを重視する場面では早期初期化の方が有利です。

<a id="深堀り3"></a>

## 【深堀り③】インスタンス数を n 個に制限するパターン

Singleton パターンは「インスタンスをひとつに限定する」パターンですが、「**n 個に限定する**」という発展形があります。

書籍の演習問題（`Triple`）がその例です。

```Java:Triple.java
public class Triple {
    private static Map<String, Triple> map = new HashMap<>();

    static {
        String[] names = {"ALPHA", "BETA", "GANMA"};
        Arrays.stream(names).forEach(str -> map.put(str, new Triple(str)));
    }

    private String instanceName;

    private Triple(String name) {
        System.out.println("インスタンス名：" + name + " のインスタンスを生成しました。");
        this.instanceName = name;
    }

    public static Triple getInstance(String name) {
        return map.get(name);
    }

    @Override
    public String toString() {
        return this.instanceName;
    }
}
```

`Triple` は「ALPHA」「BETA」「GANMA」という 3 種類のインスタンスのみ存在できます。

`static` ブロック（**static 初期化子**）を使って、クラスロード時に 3 つのインスタンスを一括で生成し `HashMap` に保持しています。<br>
`static` 初期化子は、`static` フィールドの初期化が複数行にわたる場合や、ループ処理が必要な場合に活用できます。

```java
static {
    // クラスロード時に 1 度だけ実行される
    String[] names = {"ALPHA", "BETA", "GANMA"};
    Arrays.stream(names).forEach(str -> map.put(str, new Triple(str)));
}
```

外部からは `Triple.getInstance("ALPHA")` のように名前を指定してインスタンスを取得します。

```java
Triple a1 = Triple.getInstance("ALPHA");
Triple a2 = Triple.getInstance("ALPHA");

if (a1 == a2) {
    System.out.println("a1 == a2 (" + a1 + ")"); // こちらが出力される
}
```

なお、`getInstance()` に存在しない名前を渡した場合、`HashMap.get()` が `null` を返します。<br>
実務で使う際はこのような不正な入力への対処（例外を投げるなど）も検討する必要があります。

Singleton（1 個）は「n 個に限定するパターン」の n = 1 の特殊ケースとも言えます。

<a id="深堀り4"></a>

## 【深堀り④】GoF デザインパターンとの位置づけ

GoF（Gang of Four）の 23 のデザインパターンは、次の 3 カテゴリに分類されます。

| カテゴリ | 概要 |
| --- | --- |
| 生成（Creational） | インスタンスの生成に関するパターン |
| 構造（Structural） | クラスやオブジェクトの構造に関するパターン |
| 振る舞い（Behavioral） | オブジェクト間の連携・責務の分担に関するパターン |

Singleton パターンは**生成（Creational）** カテゴリに属しています。<br>
「どのようにインスタンスを生成するか（あるいは生成を制限するか）」という関心事を扱うパターンです。

これまでの記事で扱ったパターンとの比較は以下のとおりです。

| パターン | カテゴリ | 概要 |
| --- | --- | --- |
| Iterator | 振る舞い | コレクションの走査方法を統一する |
| Adapter | 構造 | 既存のインターフェースを別のインターフェースに変換する |
| Template Method | 振る舞い | 処理の枠組みをスーパークラスに定め、詳細をサブクラスに委ねる |
| Factory Method | 生成 | インスタンスの生成をサブクラスに委ねる |
| **Singleton** | **生成** | **インスタンスをただひとつに限定する** |
