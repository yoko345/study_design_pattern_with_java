# Singleton（シングルトン）パターン ― インスタンスをただ 1 つにする

次のような経験をしたことはありませんか？

> あるクラスを複数の場所で `new` して使っていたら、それぞれの設定がバラバラになってしまい、どの設定が正しいのかわからなくなった

この記事では、注文管理システムへのログ機能追加というシナリオを通して、Singleton パターンがこの問題をどのように解決するかを学びます。

## 目次

- [【具体例】](#具体例)
    - [シナリオ](#シナリオ)
    - [既存コードの仕様](#既存コードの仕様)
    - [`Logger` クラスの仕様](#Loggerクラスの仕様)
- [好ましくない実装](#好ましくない実装)
- [正しい実装](#正しい実装)
    - [補足（`Logger` クラスのインスタンスが生成されるタイミング）](#正しい実装の補足)
- [まとめ](#まとめ)
- [【深堀り①】`private` コンストラクタが必要な理由](#深堀り1)
- [【深堀り②】Singleton をスレッドセーフにする](#深堀り2)
    - [シナリオ](#深堀り2シナリオ)
    - [好ましくない実装（遅延初期化）](#好ましくない実装遅延初期化)
    - [正しい実装（早期初期化）](#正しい実装早期初期化)
    - [補足（synchronized）](#補足synchronized)
- [【深堀り③】あらかじめ決めた n 個のインスタンスに制限する](#深堀り3)
- [【深堀り④】`enum` （列挙型）を使った Singleton](#深堀り4)
    - [`enum` の特徴](#enumの特徴)
    - [Singleton との共通点](#Singletonとの共通点)
    - [`Logger` クラスを `enum` で実装する](#LoggerをenumでImplementする)
- [【深堀り⑤】GoF デザインパターンとの位置づけ](#深堀り5)

---

## 【具体例】

### シナリオ

> あなたは EC サイトの開発チームに所属しています。<br>
> 注文管理システムの開発が進む中、PM から「各処理の実行状況をログに残したい」という方針が出ました。<br>
> あなたはログを出力するクラスを実装し、各サービスクラスから使えるようにします。

### 既存コードの仕様

- `OrderService`（既存クラス）

注文の受け付け処理を管理するクラスです。

| メソッド     | 戻り値の型 | 説明             |
| ------------ | ---------- | ---------------- |
| `placeOrder` | `void`     | 注文を受け付ける |

```Java:OrderService.java
public class OrderService {
    public void placeOrder(String orderId) {
        System.out.println("注文を受け付けました: " + orderId);
    }
}
```

<br>

- `PaymentService`（既存クラス）

決済の処理を管理するクラスです。

| メソッド         | 戻り値の型 | 説明               |
| ---------------- | ---------- | ------------------ |
| `processPayment` | `void`     | 決済処理を開始する |

```Java:PaymentService.java
public class PaymentService {
    public void processPayment(String paymentId) {
        System.out.println("決済処理を開始します: " + paymentId);
    }
}
```

<br>
<a id="既存コードの実行クラス"></a>

- `Main`（実行クラス）

```Java:Main.java
public class Main {
    public static void main(String[] args) {
        OrderService orderService = new OrderService();
        PaymentService paymentService = new PaymentService();

        orderService.placeOrder("order_001");
        paymentService.processPayment("pay_001");
    }
}
```

**実行結果**

```
注文を受け付けました: order_001
決済処理を開始します: pay_001
```

<a id="Loggerクラスの仕様"></a>

### `Logger` クラスの仕様

本記事の主題に集中できるよう、`Logger` クラスの仕様を下記に示します。

| フィールド | 型       | 説明                             |
| ---------- | -------- | -------------------------------- |
| `logLevel` | `String` | ログの出力レベル（例: `"INFO"`） |

| メソッド | 戻り値の型 | 説明                                 |
| -------- | ---------- | ------------------------------------ |
| `log`    | `void`     | ログメッセージをコンソールへ出力する |

```Java:Logger.java
public class Logger {
    private String logLevel;

    public Logger(String logLevel) {
        System.out.println("Logger を生成しました。[logLevel=" + logLevel + "]");
        this.logLevel = logLevel;
    }

    public void log(String message) {
        System.out.println("[" + logLevel + "] " + message);
    }
}
```

※ここで一旦読むのを止めて、ご自身でコーディングを行なってみてください。その後で、続きを読んでください。

## 好ましくない実装

では、シナリオに従って各サービスクラスから使えるように実装をしていきましょう。

「各サービスでログが出力されればよい」と考え、次のような実装をするのではないでしょうか？

```Java:OrderService.java
public class OrderService {
    private Logger logger = new Logger("DEBUG"); // ←ここを追加

    public void placeOrder(String orderId) {
        System.out.println("注文を受け付けました: " + orderId); // 本来ここは様々な処理（DB の操作など）が入るので、わざと残している
        logger.log("注文を受け付けました: " + orderId); // ←ここを追加
    }
}
```

```Java:PaymentService.java
public class PaymentService {
    private Logger logger = new Logger("INFO"); // ←ここを追加

    public void processPayment(String paymentId) {
        System.out.println("決済処理を開始します: " + paymentId); // 本来ここは様々な処理（DB の操作など）が入るので、わざと残している
        logger.log("決済処理を開始しました: " + paymentId); // ←ここを追加
    }
}
```

[実行クラス](#既存コードの実行クラス)は変更なし。

**実行結果**

```
Logger を生成しました。[logLevel=DEBUG]
Logger を生成しました。[logLevel=INFO]
注文を受け付けました: order_001
[DEBUG] 注文を受け付けました: order_001
決済処理を開始します: pay_001
[INFO] 決済処理を開始しました: pay_001
```

コンパイルエラーがなく結果が出力されていることから、実装・動作確認ともに問題ないことがわかります。

しかし、この実装には以下の問題点があります。

- 各クラスが独立してインスタンスを生成すると、実装者により設定がバラバラになりやすいため、設計者の意図と異なる設定となってしまう（実行結果を見てわかるように `[DEBUG]` と `[INFO]` が混在していて、アプリ全体で統一した設定になっていない）
- 仕様変更のたびに、全クラスへの修正が必要になる
    - その結果、追加実装時はクラスが増えるので、仕様変更に伴う修正漏れのリスクが高くなる
- 同じ設定のインスタンスが、意図せず複数存在してしまう場合がある
    - 今回のようなログの設定は、基本的にアプリ全体で統一されるべきなので、各クラスが個別に持つことは好ましくない
    - 将来ログをファイルに書き込むようにした場合、複数のインスタンスが同じファイルに同時に書き込もうとして競合が起きるリスクがある

## 正しい実装

では、好ましくない実装で触れた問題点を解決するにはどうすればよいのでしょうか？

これらの問題を解決するのが **Singleton パターン**です。<br>
まずは、次のコードを見てください。

```Java:Logger.java
public class Logger {
    private static Logger logger = new Logger("INFO"); // ←ここを追加
    private String logLevel;

    private Logger(String logLevel) { // ←ここを修正
        System.out.println("Logger を生成しました。[logLevel=" + logLevel + "]");
        this.logLevel = logLevel;
    }

    /* ここを追加（ここから） */
    public static Logger getInstance() {
        return logger;
    }
    /* ここを追加（ここまで） */

    public void log(String message) {
        System.out.println("[" + logLevel + "] " + message);
    }
}
```

上記を見ると次のことがわかります。

1. `Logger` クラスのインスタンスを 1 つだけ生成している `static` フィールドの変数 `logger` が設定されている
2. コンストラクタのアクセス修飾子が `private` になっている
3. 修飾子 `static` が付いている `getInstance` メソッドにアクセスすることで `Logger` のインスタンスが取得できる

1 つ目より、JVM 全体で `Logger` クラスのインスタンスが 1 つしか存在しないことが読み取れます。<br>
2 つ目と 3 つ目より、`Logger` クラスの外からのインスタンス生成が禁止され、インスタンスの取得は `getInstance` メソッド経由に限られることが読み取れます。

このように、「インスタンスが 1 個しか存在しないことをプログラム上で表現」しつつ、「そのクラスのインスタンスが絶対に 1 個しか存在しないことを保証」するパターンを **Singleton パターン**といいます。

次に、`Logger` を利用するクラスの実装を見てください。

```Java:OrderService.java
public class OrderService {
    public void placeOrder(String orderId) {
        System.out.println("注文を受け付けました: " + orderId); // 本来ここは様々な処理（DB の操作など）が入るので、わざと残している
        Logger.getInstance().log("注文を受け付けました: " + orderId); // ←ここを追加
    }
}
```

```Java:PaymentService.java
public class PaymentService {
    public void processPayment(String paymentId) {
        System.out.println("決済処理を開始します: " + paymentId); // 本来ここは様々な処理（DB の操作など）が入るので、わざと残している
        Logger.getInstance().log("決済処理を開始しました: " + paymentId); // ←ここを追加
    }
}
```

[実行クラス](#既存コードの実行クラス)は変更なし。

**実行結果**

```
注文を受け付けました: order_001
Logger を生成しました。[logLevel=INFO]
[INFO] 注文を受け付けました: order_001
決済処理を開始します: pay_001
[INFO] 決済処理を開始しました: pay_001
```

実行結果から、「Logger を生成しました。[logLevel=INFO]」が **1 回だけ**出力されていることがわかります。<br>
つまり、インスタンスは 1 つしか存在しないということです。

試しに `getInstance` メソッドを 2 回呼び出し、同一のインスタンスかどうか確認してみましょう。

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

実行結果から、インスタンスは 1 つしか存在しないことがわかりました。

このように、Singleton パターンを適用すると以下のメリットがあります。

- インスタンスは 1 個しか存在しないことが保証されているため、設計者が意図した設定になる（実行結果から `[INFO]` のみであることがわかるので、アプリ全体で統一した設定になっている）
- 同じ設定のインスタンスが、複数存在しなくなるため、[好ましくない実装](#好ましくない実装)の問題点にあったファイル書き込み時の競合が起きない
- 仕様変更があった際は、共通で呼び出しているクラスを修正するだけでよいので、修正漏れのリスクが低い

<a id="正しい実装の補足"></a>

### 補足（`Logger` クラスのインスタンスが生成されるタイミング）

ここで、`Logger` クラスのインスタンスが生成されるタイミングに関して深堀りをします。<br>
生成のタイミングがわかっている場合は飛ばしてください。

1. `main` メソッドが実行される
2. `new OrderService()` → `Logger` はまだロードされない（`OrderService` のフィールドに `Logger` がない）
3. `new PaymentService()` → 上記と同様
4. `orderService.placeOrder("order_001")` の中で、まず `System.out.println` が実行され、続いて `Logger.getInstance()` が呼ばれる → このタイミングで初めて `Logger` クラスがロードされ、`static` フィールドが初期化されてインスタンスが生成される → 「Logger を生成しました。[logLevel=INFO]」が出力される
5. 以降は `Logger.getInstance()` を何回呼んでも `static` フィールドの値を参照して既存インスタンスを返すだけ

上記の流れより、`Logger.getInstance()` が初めて呼ばれたタイミングで `Logger` クラスのインスタンスが生成されます。

## まとめ

正しい実装を見ると、インスタンスが 1 つに絞られています。<br>
そのため、同じ設定のインスタンスが複数存在することによる、思いがけないバグが生じなくなります。

また、クラスの外からインスタンスを取得するための窓口が絞られており、そのインスタンスは `static` フィールドで管理されています。<br>
これにより、クラス外からの勝手なインスタンス生成がなくなり、アプリ全体で共有したいリソースを 1 つだけにできるので、安全に管理ができるようになります。

なお、本記事の `Logger` クラスは `enum` を使って実装することもできます（[【深堀り④】参照](#深堀り4)）。

本記事の内容はここまでとなります。

以降は「もう少し深く知りたい」という方向けの補足となります。今回学んだパターンに繋がる設計原則や、実務で役立つ背景知識について触れています。

---

<a id="深堀り1"></a>

## 【深堀り①】`private` コンストラクタが必要な理由

正しい実装では、`Logger` クラスのコンストラクタのアクセス修飾子が `private` になっていました。<br>
なぜ `public`（もしくは修飾子なし）ではなく `private` にしているのでしょうか？

ここでは、Singleton パターンにおいて、コンストラクタを `private` にしないといけないことについて学びます。

次のコードのように、コンストラクタを `public`（もしくは修飾子なし）にすると、クラスの外から自由にインスタンスを生成できるようになります。

```Java:Logger.java
public class Logger {
    private static Logger logger = new Logger("INFO");
    private String logLevel;

    // コンストラクタに public をつけている
    public Logger(String logLevel) {
        System.out.println("Logger を生成しました。[logLevel=" + logLevel + "]");
        this.logLevel = logLevel;
    }

    public static Logger getInstance() {
        return logger;
    }

    public void log(String message) {
        System.out.println("[" + logLevel + "] " + message);
    }
}
```

```Java:Main.java
public class Main {
    public static void main(String[] args) {
        Logger logger1 = Logger.getInstance();
        Logger logger2 = new Logger("ERROR"); // クラスの外からインスタンスの生成ができる
    }
}
```

Singleton パターンは、「そのクラスのインスタンスが絶対に 1 個しか存在しないことを保証」するパターンだと説明しました。<br>
コンストラクタに `public` をつけるとこの目的が達成できません。<br>
そのため、コンストラクタを `private` にして、インスタンスの生成をクラス内部に閉じ込める実装をしているのです。また、インスタンスを取得したい場合は、メソッド（ここでは、`getInstance` メソッドに当たる）経由でのみアクセスできるように実装します。

<a id="深堀り2"></a>

## 【深堀り②】Singleton をスレッドセーフにする

EC サイトでは、同時に複数のリクエストをさばくために、処理を**複数のスレッド**で並行実行するケースがよくあります。<br>
このとき、`Logger` クラスの実装の仕方によっては、思いがけない問題が起きます。

ここでは、アプリ起動直後に複数のリクエストが同時に届いたというシナリオを通して、発生する問題と対応方法を学びます。

※スレッドセーフ：複数のスレッドから同時にアクセスされても、意図した通りに動作することを保証する性質のこと。

<a id="深堀り2シナリオ"></a>

### シナリオ

> あなたは `Logger` クラスを実装する際、「アプリ起動時にインスタンスを生成するより、最初に呼ばれたときに生成すれば十分」と考え、遅延初期化で実装しました。<br>
> ところが、アプリ起動直後、`OrderService` と `PaymentService` がほぼ同時に最初のリクエストを受け付けた際、`Logger` のインスタンスが複数生成されるバグが発生しました。

※遅延初期化（Lazy Initialization）：フィールドの値が必要になるまで初期化を遅らせる方法（ここでは `getInstance` メソッドが初めて呼ばれたタイミングでインスタンスを生成する実装に当たる）。

### 好ましくない実装（遅延初期化）

遅延初期化で実装されたコードを確認しましょう（説明の都合、レースコンディションを起きやすくするための遅延処理が挟まれています）。<br>
※レースコンディション（競合状態）：複数のスレッドが同じリソースにほぼ同時にアクセスし、処理の順序によって結果が変わってしまう状態のこと。

```Java:Logger.java
public class Logger {
    private static Logger logger = null;
    private String logLevel;

    private Logger(String logLevel) {
        System.out.println("Logger を生成しました。[logLevel=" + logLevel + "]");
        this.logLevel = logLevel;
    }

    public static Logger getInstance() {
        if (logger == null) {                    // ①
            try {
                Thread.sleep(1000);              // レースコンディションを起きやすくするための遅延
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            logger = new Logger("INFO");         // ②
        }

        return logger;
    }

    public void log(String message) {
        System.out.println("[" + logLevel + "] " + message);
    }
}
```

```Java:Main.java
public class Main {
    public static void main(String[] args) {
        Thread threadA = new Thread(() -> {
            Logger logger = Logger.getInstance();
            System.out.println("スレッドA（OrderService）が取得したインスタンス: " + logger);
        });

        Thread threadB = new Thread(() -> {
            Logger logger = Logger.getInstance();
            System.out.println("スレッドB（PaymentService）が取得したインスタンス: " + logger);
        });

        threadA.start();
        threadB.start();
    }
}
```

**実行結果**

※レースコンディションが発生した場合の例で、レースコンディションが発生しなければ、インスタンスの生成は 1 つだけになる

```
Logger を生成しました。[logLevel=INFO]
Logger を生成しました。[logLevel=INFO]
スレッドB（PaymentService）が取得したインスタンス: Logger@c2390e4
スレッドA（OrderService）が取得したインスタンス: Logger@18615390
```

実行結果を見ると、確かにインスタンスのアドレス値が「Logger@c2390e4」「Logger@18615390」と異なっているので、インスタンスが複数生成されたことがわかります。

なぜこのようなことが起こるのか、処理の流れを確認してみましょう。

1. スレッド A が ① を実行し、`logger == null` が `true` と判定される
2. スレッド A が 1000ms 一時停止する
3. スレッド A が ② を実行する前に、スレッド B が ① を実行し、`logger == null` が `true` と判定される
4. スレッド B が 1000ms 一時停止する
5. スレッド A またはスレッド B が ② を実行し、インスタンスを生成する
6. 他方のスレッドが ② を実行し、さらにもう 1 つインスタンスを生成する

上記から、Singleton パターンで実装したはずなのに、2 つのインスタンスが生成されてしまうのです。

### 正しい実装（早期初期化）

では、この問題を防ぐにはどのような実装をすればよいのでしょうか？

この問題を解決するのが、本記事で扱った**早期初期化**です。<br>
※早期初期化（Eager Initialization）：クラスがロードされるタイミングでインスタンスを生成する方法（ここでは `static` フィールドの宣言時にインスタンスを生成する実装に当たる）。

```Java:Logger.java
public class Logger {
    private static Logger logger = new Logger("INFO"); // ←ここを修正
    private String logLevel;

    private Logger(String logLevel) {
        System.out.println("Logger を生成しました。[logLevel=" + logLevel + "]");
        this.logLevel = logLevel;
    }

    public static Logger getInstance() {
        /* ここを修正（ここから） */
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        /* ここを修正（ここまで） */

        return logger;
    }

    public void log(String message) {
        System.out.println("[" + logLevel + "] " + message);
    }
}
```

`Main` クラスは変更なし。

**実行結果**

```
Logger を生成しました。[logLevel=INFO]
スレッドB（PaymentService）が取得したインスタンス: Logger@2d4088ab
スレッドA（OrderService）が取得したインスタンス: Logger@2d4088ab
```

実行結果を見ると、インスタンスのアドレス値が「Logger@2d4088ab」で同じであることから、インスタンスが 1 つだけ生成されたことがわかります。

これは、JVM がクラスをロードした時に `static` フィールドの初期化をスレッドセーフに行うため、複数のスレッドが同時にアクセスしても、インスタンスの生成は 1 回だけとなるからです。

このように、早期初期化を採用することで `synchronized` などの排他制御を使わずにスレッドセーフな Singleton を実現できます。

### 補足（synchronized）

実務では、遅延初期化をどうしても使いたい場合があります。<br>
この解決策の一つが `synchronized` です。

```Java:Logger.java
public class Logger {
    private static Logger logger = null;
    private String logLevel;

    private Logger(String logLevel) {
        System.out.println("Logger を生成しました。[logLevel=" + logLevel + "]");
        this.logLevel = logLevel;
    }

    // getInstance メソッドに synchronized をつける
    public static synchronized Logger getInstance() {
        if (logger == null) {                    // ①
            try {
                Thread.sleep(1000);              // レースコンディションを起きやすくするための遅延
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            logger = new Logger("INFO");         // ②
        }

        return logger;
    }

    public void log(String message) {
        System.out.println("[" + logLevel + "] " + message);
    }
}
```

`Main` クラスは変更なし。

**実行結果**

```
Logger を生成しました。[logLevel=INFO]
スレッドA（OrderService）が取得したインスタンス: Logger@ad2e819
スレッドB（PaymentService）が取得したインスタンス: Logger@ad2e819
```

`getInstance` メソッドに `synchronized` を付与すると、同時に 1 つのスレッドしかそのメソッドを実行できなくなります。<br>
これにより、複数スレッドが同時に `logger == null` の判定をすることがなくなり、インスタンスの二重生成を防げます。

ただし、`synchronized` はロックの取得・解放にコストがかかるため、パフォーマンスを重視する場面では早期初期化の方が有利です。<br>
また、デッドロックが発生するリスクもあるためお勧めできません。（デッドロックの詳細は本記事の範囲を超えるため、興味のある方は別途調べてみてください。）

<a id="深堀り3"></a>

## 【深堀り③】あらかじめ決めた n 個のインスタンスに制限する

本記事で扱ったコードでは、ログレベルを `INFO` に固定することしかできませんでした。<br>
しかし、実務では `WARNING` や `ERROR` などのレベルも設定できるようにしたいはずです。

このような時、Singleton パターンを応用することで、インスタンス数を **n 個に限定する**ことができます。<br>
ここでは、本記事のコードを参考にしつつ、実装者側でログレベルを決定できるようにします。

次のコードを見てください。

```Java:Logger.java
public class Logger {
    private static Map<String, Logger> map = new HashMap<>(); // 変更
    private String logLevel;

    /* 変更（ここから） */
    static {
        String[] logLevels = {"INFO", "WARNING", "ERROR"};
        Arrays.stream(logLevels).forEach(logLevel -> map.put(logLevel, new Logger(logLevel)));
    }
    /* 変更（ここまで） */

    private Logger(String logLevel) {
        System.out.println("Logger を生成しました。[logLevel=" + logLevel + "]");
        this.logLevel = logLevel;
    }

    /* 変更（ここから） */
    public static Logger getInstance(String logLevel) {
        return map.get(logLevel);
    }
    /* 変更（ここまで） */

    public void log(String message) {
        System.out.println("[" + logLevel + "] " + message);
    }
}
```

コードを見ると、本記事からの変更点が 3 つあることがわかります。

- 単一インスタンスを保持していた `static` フィールドが `HashMap` に変わっている
- `static` 初期化子で `logLevels` 配列の各要素に対応するインスタンスを生成し、`HashMap` に格納している
- `getInstance` が引数（ログレベル名）を受け取れるようになっている

それぞれの変更点について詳しく見ていきましょう。

`HashMap` は、ログレベル名をキーに、対応する `Logger` インスタンスを値として保持するデータ構造となっています。<br>
クラスがロードされると、`static` 初期化子により、`logLevels` 配列の要素数（**n 個**）分のインスタンスが一括で生成され、`HashMap` へ格納されます。<br>
この結果、配列の要素を変えることで、制限するインスタンスの数や種類を設計時に決めることができます。<br>
また、`getInstance` に引数を受け取れるようにしたことで、呼び出し側が取得したいログレベルのインスタンスを指定できるようになります。

では、呼び出し側の実装を見ていきましょう。

```Java:OrderService.java
public class OrderService {
    public void placeOrder(String orderId) {
        System.out.println("注文を受け付けました: " + orderId); // 本来ここは様々な処理（DB の操作など）が入るので、わざと残している

        /* ここを追加（ここから） */
        if (orderId.equals("warning_id")) {
            Logger.getInstance("WARNING").log("注文を受け付けました: " + orderId);
        } else {
            Logger.getInstance("INFO").log("注文を受け付けました: " + orderId);
        }
        /* ここを追加（ここまで） */
    }
}
```

```Java:PaymentService.java
public class PaymentService {
    public void processPayment(String paymentId) {
        System.out.println("決済処理を開始します: " + paymentId); // 本来ここは様々な処理（DB の操作など）が入るので、わざと残している

        /* ここを追加（ここから） */
        if (paymentId.equals("error_id")) {
            Logger.getInstance("ERROR").log("決済処理を開始しました: " + paymentId);
        } else {
            Logger.getInstance("INFO").log("決済処理を開始しました: " + paymentId);
        }
        /* ここを追加（ここまで） */
    }
}
```

```Java:Main.java
public class Main {
    public static void main(String[] args) {
        OrderService orderService = new OrderService();
        PaymentService paymentService = new PaymentService();

        orderService.placeOrder("order_001");
        orderService.placeOrder("warning_id"); // ←ここを追加
        paymentService.processPayment("pay_001");
        paymentService.processPayment("error_id"); // ←ここを追加
    }
}
```

**実行結果**

```
注文を受け付けました: order_001
Logger を生成しました。[logLevel=INFO]
Logger を生成しました。[logLevel=WARNING]
Logger を生成しました。[logLevel=ERROR]
[INFO] 注文を受け付けました: order_001
注文を受け付けました: warning_id
[WARNING] 注文を受け付けました: warning_id
決済処理を開始します: pay_001
[INFO] 決済処理を開始しました: pay_001
決済処理を開始します: error_id
[ERROR] 決済処理を開始しました: error_id
```

実行結果を見ると、ログレベルごとに「`Logger を生成しました。`」が 1 回だけ出力されていることがわかります（`[INFO]` が複数箇所で使われていますが、インスタンスの生成は 1 回だけです）。

ちなみに、`Logger.getInstance("INFO1")` のように設計者が意図していないログレベルを指定すると `NullPointerException` が発生するので、設計者の意図通りの実装を強制することができます。

<a id="深堀り4"></a>

## 【深堀り④】`enum` （列挙型）を使った Singleton

本記事の `Logger` クラスを `enum` で実装する場合を見ていく前に、`enum` の特徴から確認していきましょう。

<a id="enumの特徴"></a>

### `enum` の特徴

`enum` は、次のような特徴を持つ特殊なクラスです。

- 定数を宣言できる
    - クラスの先頭に宣言された定数は、自クラスのインスタンスであり、`public static final` なフィールドとして扱われる
- 通常のクラスと同様にフィールドやメソッドを持てる
- コンストラクタは `private`（または修飾子なし）しか指定できない
    - アクセス修飾子がない場合、実質的に `private` として扱われる

<a id="Singletonとの共通点"></a>

### Singleton との共通点

ここで、Singleton パターンは次の 2 点の特徴がありました。

1. インスタンスを 1 つに保証する
    - `static` フィールドで唯一のインスタンスを保持することで実現
2. クラス外からのインスタンス生成を禁止する
    - `private` コンストラクタにより実現

上記と `enum` の特徴とを照らし合わせると、この 2 点がいずれも言語仕様として組み込まれていることがわかります。<br>
そのため、`enum` を使うことで `private` コンストラクタや `getInstance` メソッドを自分で書かなくても、Singleton パターンを実現することができます。

<a id="LoggerをenumでImplementする"></a>

### `Logger` クラスを `enum` で実装する

では、本記事の `Logger` クラスを `enum` で実装してみましょう。

```Java:Logger.java
public enum Logger {
    INSTANCE("INFO");

    private final String logLevel;

    Logger(String logLevel) {
        System.out.println("Logger を生成しました。[logLevel=" + logLevel + "]");
        this.logLevel = logLevel;
    }

    public void log(String message) {
        System.out.println("[" + logLevel + "] " + message);
    }
}
```

`INSTANCE` という定数が、列挙型 `Logger` の唯一のインスタンスとなります。<br>
`Logger.INSTANCE` で呼び出すことで、インスタンスを取得することができます。

```Java:OrderService.java
public class OrderService {
    public void placeOrder(String orderId) {
        System.out.println("注文を受け付けました: " + orderId);
        Logger.INSTANCE.log("注文を受け付けました: " + orderId);
    }
}
```

```Java:PaymentService.java
public class PaymentService {
    public void processPayment(String paymentId) {
        System.out.println("決済処理を開始します: " + paymentId);
        Logger.INSTANCE.log("決済処理を開始しました: " + paymentId);
    }
}
```

[実行クラス](#既存コードの実行クラス)は変更なし。

**実行結果**

```
注文を受け付けました: order_001
Logger を生成しました。[logLevel=INFO]
[INFO] 注文を受け付けました: order_001
決済処理を開始します: pay_001
[INFO] 決済処理を開始しました: pay_001
```

実行結果から、本記事と同様の結果を得られていることがわかります。

なお、[【深堀り③】](#深堀り3) で扱った「あらかじめ決めた n 個のインスタンスに制限する」実装も、`enum` で表現できます。

```Java:Logger.java
public enum Logger {
    INFO("INFO"),
    WARNING("WARNING"),
    ERROR("ERROR");

    // 以降は先ほどと同じ
}
```

```Java:OrderService.java
public class OrderService {
    public void placeOrder(String orderId) {
        System.out.println("注文を受け付けました: " + orderId);
        if (orderId.equals("warning_id")) {
            Logger.WARNING.log("注文を受け付けました: " + orderId);
        } else {
            Logger.INFO.log("注文を受け付けました: " + orderId);
        }
    }
}
```

コードの簡潔さと堅牢性が求められる場面では、`enum` Singleton が有力な選択肢になります。

<a id="深堀り5"></a>

## 【深堀り⑤】GoF デザインパターンとの位置づけ

今回使った Singleton パターンは、GoF（Gang of Four）の 23 のデザインパターンのうち「生成パターン」に分類されます。<br>
詳しくは「GoF」で検索してみてください。
