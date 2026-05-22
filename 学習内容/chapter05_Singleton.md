# Singleton パターン ― インスタンスをただひとつにする

次のような経験をしたことはありませんか？

> あるクラスを複数の場所で `new` して使っていたら、それぞれの設定がバラバラになってしまい、どの設定が正しいのかわからなくなった

この記事では、注文管理システムへのログ機能追加というシナリオを通して、Singleton パターンがこの問題をどのように解決するかを学びます。

## 目次

- [【具体例】](#具体例)
    - [シナリオ](#シナリオ)
    - [既存コードの仕様](#既存コードの仕様)
- [好ましくない実装](#好ましくない実装)
- [正しい実装](#正しい実装)
- [まとめ](#まとめ)
- [【深堀り①】private コンストラクタが必要な理由](#深堀り1)
- [【深堀り②】Singleton をスレッドセーフにする](#深堀り2)
- [【深堀り③】インスタンス数を n 個に制限する](#深堀り3)
- [【深堀り④】GoF デザインパターンとの位置づけ](#深堀り4)

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

### `Logger` クラスの仕様

今回は、本記事の主題から脱線しないようにするために、`Logger` クラスの仕様を下記に示します。

| フィールド | 型       | 説明                                        |
| ---------- | -------- | ------------------------------------------- |
| `logLevel` | `String` | ログの出力レベル（例: `"INFO"`、`"ERROR"`） |

| メソッド | 戻り値の型 | 説明                                 |
| -------- | ---------- | ------------------------------------ |
| `log`    | `void`     | ログメッセージをコンソールへ出力する |

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

では、シナリオに従い追加実装をしていきましょう。

「各サービスでログを出力できればよい」と考え、次のような実装をするのではないでしょうか？

```Java:OrderService.java
public class OrderService {
    private Logger logger = new Logger("DEBUG"); // ←ここを追加

    public void placeOrder(String orderId) {
        System.out.println("注文を受け付けました: " + orderId); // 本来ここは DB の登録処理などが入るので、わざと残している
        logger.log("注文を受け付けました: " + orderId); // ←ここを追加
    }
}
```

```Java:PaymentService.java
public class PaymentService {
    private Logger logger = new Logger("INFO"); // ←ここを追加

    public void processPayment(String paymentId) {
        System.out.println("決済処理を開始します: " + paymentId); // 本来ここは DB の登録処理などが入るので、わざと残している
        logger.log("決済処理を開始しました: " + paymentId); // ←ここを追加
    }
}
```

実行クラスは変更なし。

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

- 各クラスが独立してインスタンスを生成すると、設計者が同じ設定をすることを望んでいても、実装者により設定がバラバラになりやすい（実行結果を見てわかるように `[DEBUG]` と `[INFO]` が混在していて、アプリ全体で統一した設定になっていない）
- 仕様変更のたびに、全クラスへの修正が必要になる
    - その結果、追加実装時にクラスが増えるので、仕様変更に伴う修正の漏れが発生するリスクが高くなる
- 同じ設定のインスタンスが複数存在する
    - ログの設定は基本的にアプリ全体で統一されるべきなので、各クラスが個別に持つことは好ましくない
    - 将来ログをファイルに書き込むようにした場合、複数のインスタンスが同じファイルに同時に書き込もうとして競合が起きるリスクがある

## 正しい実装

では、好ましくない実装で触れた問題点を解決するにはどうすればよいのでしょうか？

これらの問題を解決するのが **Singleton パターン**です。<br>
まずは、次のコードを見てください。

```Java:Logger.java
public class Logger {
    private static Logger logger = new Logger("INFO");
    private String logLevel;

    private Logger(String logLevel) {
        this.logLevel = logLevel;
        System.out.println("Logger を生成しました。[logLevel=" + logLevel + "]");
    }

    public static Logger getInstance() {
        return logger;
    }

    public void log(String message) {
        System.out.println("[" + logLevel + "] " + message);
    }
}
```

`Logger` クラスを見ると次のことがわかります。

- `static` フィールドの変数 `logger` が設定されていて、その変数に `Logger` のインスタンスを 1 つだけ生成し、代入している
- コンストラクタのアクセス修飾子が `private` になっている
- `static` な `getInstance` メソッドにアクセスすることで変数 `logger` が取得できる

`Logger` のインスタンスを 1 つだけ生成した変数 `logger` が `static` フィールドに設定されているので、JVM 全体で 1 つしか存在しないことが読み取れます。<br>
また、コンストラクタのアクセス修飾子が `private` になっているため、`Logger` クラスの外から `new Logger(...)` することができなくなっていることが読み取れます。<br>
さらに、インスタンスを生成した変数 `logger` は `getInstance` メソッドにアクセスしないと取得できないため、`Logger` クラスの外からインスタンスを取得する方法が制限されていることがわかります。

このように、「インスタンスが 1 個しか存在しないことをプログラム上で表現」しつつ、「そのクラスのインスタンスが絶対に 1 個しか存在しないことを保証」するパターンを **Singleton パターン**といいます。

次に、`Logger` を利用するクラスの実装を見てください。

```Java:OrderService.java
public class OrderService {
    public void placeOrder(String orderId) {
        System.out.println("注文を受け付けました: " + orderId); // 本来ここは DB の登録処理などが入るので、わざと残している
        Logger.getInstance().log("注文を受け付けました: " + orderId); // ←ここを追加
    }
}
```

```Java:PaymentService.java
public class PaymentService {
    public void processPayment(String paymentId) {
        System.out.println("決済処理を開始します: " + paymentId); // 本来ここは DB の登録処理などが入るので、わざと残している
        Logger.getInstance().log("決済処理を開始しました: " + paymentId); // ←ここを追加
    }
}
```

実行クラスは変更なし。

**実行結果**

```
注文を受け付けました: order_001
Logger を生成しました。[logLevel=INFO]
[INFO] 注文を受け付けました: order_001
決済処理を開始します: pay_001
[INFO] 決済処理を開始しました: pay_001
```

実行結果から、「Logger を生成しました。[logLevel=INFO]」が **1 回だけ**出力されていることがわかります。<br>
つまり、インスタンスが 1 つしか存在しないということです。

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

実行結果から、インスタンスが 1 つしか存在しないことがわかりました。

このように、Singleton パターンを適用すると以下のメリットがあります。

- インスタンスは 1 個しか存在しないことが保証されているため、設計者が意図した実装になる（実行結果を見てわかるように `[INFO]` のみが指定されていて、アプリ全体で統一した設定になっている）
    - 同じ設定のインスタンスが複数存在することがなくなるため、[好ましくない実装](#好ましくない実装)の問題点にあったファイル書き込み時の競合が起きない
- 仕様変更があった際は、共通で呼び出しているクラスを修正するだけでよいので、修正の漏れが発生するリスクが低くなる

### 補足（`Logger` クラスのインスタンスが生成されるタイミング）

`Logger` クラスのインスタンスが生成されるタイミングに関して深堀りします。<br>
生成のタイミングがわかっている場合は飛ばしてください。

1. `main()` 開始
2. `new OrderService()` → `Logger` はまだロードされない（`OrderService` のフィールドに `Logger` がない）
3. `new PaymentService()` → 上記と同様
4. `orderService.placeOrder("order_001")` の中で、まず `System.out.println` が実行され、続いて `Logger.getInstance()` が呼ばれる → このタイミングで `Logger` クラスが初めてロードされ、`static` フィールドが初期化されてインスタンスが生成される → 「Logger を生成しました。[logLevel=INFO]」が出力される
5. 以降は `Logger.getInstance()` を何回呼んでも既存インスタンスを返すだけ

上記の流れより、初めて `Logger.getInstance()` が呼ばれたタイミングで `Logger` クラスのインスタンスが生成されます。

## まとめ

正しい実装を見ると、インスタンスが 1 つに絞られています。<br>
そのため、同じ設定のインスタンスが複数存在することによる、思いがけないバグが生じなくなります。

また、クラスの外からインスタンスを取得するための窓口を絞り、インスタンスを `static` フィールドで管理しています。<br>
これにより、アプリ全体で共有したいリソースを 1 つだけにできるので、安全に管理できるようになります。

本記事の内容はここまでとなります。

以降は「もう少し深く知りたい」という方向けの補足となります。今回学んだパターンに繋がる設計原則や、実務で役立つ背景知識について触れています。

---

<a id="深堀り1"></a>

## 【深堀り①】private コンストラクタが必要な理由

正しい実装では、`Logger` クラスのコンストラクタのアクセス修飾子が `private` になっていました。<br>
なぜ `public`（もしくは修飾子なし）ではなく `private` にしているのでしょうか？

ここでは、Singleton パターンにおいて、コンストラクタを `private` にしないといけないことについて学びます。

結論から述べると、コンストラクタを `public`（もしくは修飾子なし）にすると、クラスの外から自由にインスタンスを生成できてしまうからです。<br>
つまり、次のコードのコンパイルが通ってしまいます。

```Java:Logger.java
public class Logger {
    private static Logger logger = new Logger("INFO");
    private String logLevel;

    public Logger(String logLevel) {
        this.logLevel = logLevel;
        System.out.println("Logger を生成しました。[logLevel=" + logLevel + "]");
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
        Logger logger2 = new Logger("ERROR");
    }
}
```

Singleton パターンは、「そのクラスのインスタンスが絶対に 1 個しか存在しないことを保証」するパターンだと説明しました。

`public` のままでは、この目的が達成できません。<br>
そのため、コンストラクタを `private` にして、インスタンスの生成をクラス内部に閉じ込め、`getInstance` メソッド経由でのみインスタンスにアクセスできるようにしているのです。

<a id="深堀り2"></a>

## 【深堀り②】Singleton をスレッドセーフにする

EC サイトでは、同時に複数のリクエストをさばくために、処理を**複数のスレッド**で並行実行するケースがよくあります。<br>
このとき、`Logger` クラスの実装の仕方によっては、思いがけない問題が起きます。

ここでは、アプリ起動直後に複数のリクエストが同時に届いたというシナリオを通して、生じる問題と対応方法を学びます。

※スレッドセーフ：複数のスレッドから同時にアクセスされても、意図した通りに動作することを保証する性質のこと。

### シナリオ

> あなたは `Logger` クラスを実装する際、「アプリ起動時にインスタンスを生成するより、最初に呼ばれたときに生成すれば十分」と考え、遅延初期化で実装しました。<br>
> ところが、アプリ起動直後に `OrderService` と `PaymentService` がほぼ同時に最初のリクエストを受け付けた際、`Logger` のインスタンスが複数生成されるバグが発生しました。

※遅延初期化（Lazy Initialization）：フィールドの値が必要になるまで初期化を遅らせる方法（ここでは `getInstance` メソッドが初めて呼ばれたタイミングでインスタンスを生成する方法に当たる）。

### 好ましくない実装（遅延初期化）

遅延初期化で実装されたコードを確認しましょう（説明の都合、レースコンディションを起きやすくするための遅延処理が挟まれています）。<br>
※レースコンディション（競合状態）：複数のスレッドが同じリソースにほぼ同時にアクセスし、処理の順序によって結果が変わってしまう状態のこと。

```Java:Logger.java
public class Logger {
    private static Logger logger = null;
    private String logLevel;

    private Logger(String logLevel) {
        this.logLevel = logLevel;
        System.out.println("Logger を生成しました。[logLevel=" + logLevel + "]");
    }

    public static Logger getInstance() {
        if (logger == null) {                    // ①
            try {
                Thread.sleep(1000);               // レースコンディションを起きやすくするための遅延
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

```
Logger を生成しました。[logLevel=INFO]
Logger を生成しました。[logLevel=INFO]
スレッドB（PaymentService）が取得したインスタンス: Logger@c2390e4
スレッドA（OrderService）が取得したインスタンス: Logger@18615390
```

※レースコンディションが発生した場合の例（←レースコンディションが発生しなければ、インスタンスの生成は 1 つだけになる）

実行結果を見ると、確かにインスタンスのアドレス値が「Logger@c2390e4」「Logger@18615390」と異なっているので、インスタンスが複数生成されていることがわかります。<br>
なぜこのようなことが起こるのか、処理の流れを確認してみましょう。

1. スレッド A が①を実行し、`logger == null` が `true` と判定される
2. スレッド A が 1000ms 一時停止する
3. スレッド A が②を実行する前に、スレッド B が①を実行し、こちらも `logger == null` が `true` と判定される
4. スレッド B が 1000ms 一時停止する
5. スレッド A またはスレッド B が②を実行し、インスタンスを生成する
6. 他方のスレッドが②を実行し、さらにもう 1 つインスタンスを生成する

上記から、Singleton パターンで実装したはずなのに、2 つのインスタンスが生成されてしまうわけです。

### 正しい実装（早期初期化）

では、この問題を防ぐにはどのような実装にすればよいのでしょうか？

この問題を解決する実装が、本記事で扱った**早期初期化**です。<br>
※早期初期化（Eager Initialization）：クラスがロードされるタイミングでインスタンスを生成する方法（ここでは `static` フィールドの宣言時にインスタンスを生成する方法に当たる）。

```Java:Logger.java
public class Logger {
    private static Logger logger = new Logger("INFO"); // ←ここを修正
    private String logLevel;

    private Logger(String logLevel) {
        this.logLevel = logLevel;
        System.out.println("Logger を生成しました。[logLevel=" + logLevel + "]");
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

実行クラスは変更なし。

**実行結果**

```
Logger を生成しました。[logLevel=INFO]
スレッドB（PaymentService）が取得したインスタンス: Logger@2d4088ab
スレッドA（OrderService）が取得したインスタンス: Logger@2d4088ab
```

実行結果を見ると、インスタンスのアドレス値が「Logger@2d4088ab」で同じであることから、インスタンスが 1 つだけ生成されていることがわかります。

これは、`static` フィールドの初期化を JVM がクラスをロードした時にスレッドセーフに行うため、複数のスレッドが同時にアクセスしても、インスタンスの生成は 1 回だけに保証されているからです。

このように、早期初期化を採用することで `synchronized` などの排他制御を使わずにスレッドセーフな Singleton を実現できます。

### 補足（synchronized）

遅延初期化を使いたい場合の解決策の一つが `synchronized` です。

```Java:Logger.java
public class Logger {
    private static Logger logger = null;
    private String logLevel;

    private Logger(String logLevel) {
        this.logLevel = logLevel;
        System.out.println("Logger を生成しました。[logLevel=" + logLevel + "]");
    }

    public static synchronized Logger getInstance() {
        if (logger == null) {                    // ①
            try {
                Thread.sleep(1000);               // レースコンディションを起きやすくするための遅延
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

実行クラスは変更なし。

**実行結果**

```
Logger を生成しました。[logLevel=INFO]
スレッドA（OrderService）が取得したインスタンス: Logger@ad2e819
スレッドB（PaymentService）が取得したインスタンス: Logger@ad2e819
```

`getInstance` メソッドに `synchronized` を付与すると、同時に 1 つのスレッドしかそのメソッドを実行できなくなります。<br>
これにより、複数スレッドが同時に `logger == null` の判定をすることがなくなり、インスタンスの二重生成を防げます。

ただし、`synchronized` はロックの取得・解放にコストがかかるため、パフォーマンスを重視する場面では早期初期化の方が有利です。<br>
また、デッドロックが発生するリスクもあるためお勧めできません。<br>
デッドロックの詳細は本記事の範囲を超えるため、興味のある方は別途調べてみてください。

<a id="深堀り3"></a>

## 【深堀り③】インスタンス数を n 個に制限する

本記事で扱ったコードでは、ログレベルを `INFO` に固定することしかできませんでした。<br>
しかし、実務では `WARNING` や `ERROR` などのレベルも設定できるようにしたいはずです。

Singleton パターンを応用することで、インスタンス数を **n 個に限定する**ことができます。<br>
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
        this.logLevel = logLevel;
        System.out.println("Logger を生成しました。[logLevel=" + logLevel + "]");
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
- `getInstance` が引数（ログレベル名）を受け取るようになっている

それぞれの変更がどのように機能するかを見ていきましょう。

`HashMap` は、ログレベル名をキーに、対応する `Logger` インスタンスを値として保持するデータ構造です。<br>
クラスがロードされると、`logLevels` 配列の要素数（**n 個**）分のインスタンスが一括で生成され、`HashMap` へ格納されます。<br>
これにより、配列の要素を変えるだけで、制限するインスタンス数や種類を自由に設計することができるようになります。<br>
また、`getInstance` に引数を追加したことで、呼び出し側が取得したいログレベルのインスタンスを指定できるようになります。

では、呼び出し側の実装を見ていきましょう。

```Java:OrderService.java
public class OrderService {
    public void placeOrder(String orderId) {
        System.out.println("注文を受け付けました: " + orderId); // 本来ここは DB の登録処理などが入るので、わざと残している

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
        System.out.println("決済処理を開始します: " + paymentId); // 本来ここは DB の登録処理などが入るので、わざと残している

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
        orderService.placeOrder("warning_id");
        paymentService.processPayment("pay_001");
        paymentService.processPayment("error_id");
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

実行結果を見ると、`Logger を生成しました` はログレベルごとに 1 度しか出力されていないことがわかります（`[INFO]` が複数箇所で使われていますが、インスタンスの生成は 1 度だけです）。

ちなみに、`Logger.getInstance("INFO1")` のように設計者が意図していないログレベルを指定すると `NullPointerException` が発生するので、設計者の意図通りの実装を強制することができます。

<a id="深堀り4"></a>

## 【深堀り④】GoF デザインパターンとの位置づけ

今回使った Singleton パターンは、GoF（Gang of Four）の 23 のデザインパターンのうち「生成パターン」に分類されます。<br>
詳しくは「GoF」で検索してみてください。
