# Facade（ファサード）パターン ― 複数のサブシステムをまとめて呼び出す窓口を作る

次のような経験をしたことはありませんか？

> 失敗する可能性のある処理を 1 つのメソッドの中で複数箇所呼び出すコードを書いていたら、処理のたびに「途中の処理が失敗した場合は、それより前に成功していた処理を逆順に取り消す」という条件分岐を追加する必要が生じ、メソッドの中に成功パターンと失敗パターンの組み合わせがすべて書き連ねられていく状態になった。

この記事では、通販サイトの注文処理において「失敗時に途中までの処理を取り消す」機能を追加するシナリオを通して、Facade パターンがこの問題をどのように解決するかを紹介します。

## 目次

- [【具体例】](#具体例)
    - [シナリオ](#シナリオ)
    - [既存コードの仕様](#既存コードの仕様)
- [好ましくない実装](#好ましくない実装)
- [正しい実装](#正しい実装)
- [まとめ](#まとめ)
- [【深堀り①】Facade はサブシステムへの直接アクセスを禁止しない](#深堀り1)
- [【深堀り②】デメテルの法則との関係](#深堀り2)
- [【深堀り③】Java 標準ライブラリにおける Facade パターンの例](#深堀り3)
- [【深堀り④】GoF デザインパターンとの位置づけ](#深堀り4)

---

## 【具体例】

### シナリオ

> あなたは通販サイトの注文処理システムの開発チームに所属しています。<br>
> 担当している通販サイトは、急ピッチでプレリリースしたものです。<br>
> 現在、注文を受け付けると「在庫を確保し、決済処理を行い、配送手配をする」という一連の処理を、専用のクラスが順に呼び出す仕組みが実装されています。処理が失敗することもあり得ることを考慮して、失敗した場合はその時点で処理を中止する、という単純な作りでリリースしました。そのため、途中まで確保・決済済みの内容を元に戻す処理は実装していません。<br>
> ある日、配送手配が失敗した注文を調査したところ、在庫だけが確保されたまま、あるいは決済だけが完了したままになっているケースが複数件見つかりました。<br>
> 本格リリースに向けて、あなたは次の対応を担当することになりました。
>
> - 注文処理が途中で失敗した際に、それまでの処理を取り消す仕組み
> - 一時的な障害により失敗した注文に対して、通常の注文受付と同じ一連の処理と取り消し処理をまとめて繰り返し呼び出す仕組み

※実際の在庫確保・決済処理・配送手配では、それぞれ在庫管理システムや決済代行サービス、配送業者のシステムとの連携が必要ですが、本記事では Facade パターンの解説に集中するため、コンソールへの文字列出力のみとします。

### 既存コードの仕様

- `InventoryService`（既存クラス）

商品の在庫を確保するクラスです。

| メソッド  | 引数                               | 戻り値の型 | 説明                                                   |
| --------- | ---------------------------------- | ---------- | ------------------------------------------------------ |
| `reserve` | `String productName, int quantity` | `boolean`  | 指定した商品の在庫を確保し、結果をコンソールに出力する |

**`InventoryService.java`**

```java
package example;

public class InventoryService {
    private static final int MAX_RESERVABLE_QUANTITY = 10; // 1 回の注文で確保できる最大数量を表す定数

    public boolean reserve(String productName, int quantity) {
        if (quantity > MAX_RESERVABLE_QUANTITY) {
            System.out.println(productName + "の在庫が不足しているため、確保できませんでした。");
            return false;
        }
        System.out.println(productName + "の在庫を" + quantity + "個確保しました。");
        return true;
    }
}
```

<br>

- `PaymentService`（既存クラス）

決済処理を行うクラスです。

| メソッド | 引数                              | 戻り値の型 | 説明                                                     |
| -------- | --------------------------------- | ---------- | -------------------------------------------------------- |
| `charge` | `String customerName, int amount` | `boolean`  | 指定した金額の決済処理を行い、結果をコンソールに出力する |

**`PaymentService.java`**

```java
package example;

public class PaymentService {
    private static final int CREDIT_LIMIT = 1_000_000; // 1 回の決済で許容する利用限度額を表す定数

    public boolean charge(String customerName, int amount) {
        if (amount > CREDIT_LIMIT) {
            System.out.println(customerName + "様の決済に失敗しました（利用限度額を超えています）。");
            return false;
        }
        System.out.println(customerName + "様に" + amount + "円の決済処理を行いました。");
        return true;
    }
}
```

<br>

- `ShippingService`（既存クラス）

配送手配を行うクラスです。

| メソッド  | 引数                                      | 戻り値の型 | 説明                                                     |
| --------- | ----------------------------------------- | ---------- | -------------------------------------------------------- |
| `arrange` | `String customerName, String productName` | `boolean`  | 指定した商品の配送手配を行い、結果をコンソールに出力する |

**`ShippingService.java`**

```java
package example;

public class ShippingService {
    public boolean arrange(String customerName, String productName) {
        if ("特大家具".equals(productName)) {
            System.out.println(customerName + "様宛の" + productName + "は配送手配できませんでした（大型商品のため）。");
            return false;
        }
        System.out.println(customerName + "様宛に" + productName + "の配送手配を行いました。");
        return true;
    }
}
```

<br>

- `OrderProcessor`（既存クラス）

注文を受け付け、在庫確保・決済処理・配送手配を順に呼び出すクラスです。

| メソッド       | 引数                                                                | 戻り値の型 | 説明                                                                                 |
| -------------- | ------------------------------------------------------------------- | ---------- | ------------------------------------------------------------------------------------ |
| `processOrder` | `String customerName, String productName, int quantity, int amount` | `void`     | 受付ログを出力したうえで、在庫確保・決済処理・配送手配を順に呼び出し、注文を処理する |

**`OrderProcessor.java`**

```java
package example;

public class OrderProcessor {
    private InventoryService inventoryService = new InventoryService();
    private PaymentService paymentService = new PaymentService();
    private ShippingService shippingService = new ShippingService();

    public void processOrder(String customerName, String productName, int quantity, int amount) {
        System.out.println(customerName + "様の注文を受け付けました。");
        if (!inventoryService.reserve(productName, quantity)) {
            return;
        }
        if (!paymentService.charge(customerName, amount)) {
            return;
        }
        if (!shippingService.arrange(customerName, productName)) {
            return;
        }
        System.out.println("注文が完了しました。");
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
        OrderProcessor orderProcessor = new OrderProcessor();
        orderProcessor.processOrder("鈴木", "ノートパソコン", 1, 120000);
        System.out.println();
        orderProcessor.processOrder("田中", "文房具セット", 15, 3000);
        System.out.println();
        orderProcessor.processOrder("佐藤", "デジタルカメラ", 1, 2000000);
        System.out.println();
        orderProcessor.processOrder("高橋", "特大家具", 1, 45000);
    }
}
```

**実行結果**

```
鈴木様の注文を受け付けました。
ノートパソコンの在庫を1個確保しました。
鈴木様に120000円の決済処理を行いました。
鈴木様宛にノートパソコンの配送手配を行いました。
注文が完了しました。

田中様の注文を受け付けました。
文房具セットの在庫が不足しているため、確保できませんでした。

佐藤様の注文を受け付けました。
デジタルカメラの在庫を1個確保しました。
佐藤様の決済に失敗しました（利用限度額を超えています）。

高橋様の注文を受け付けました。
特大家具の在庫を1個確保しました。
高橋様に45000円の決済処理を行いました。
高橋様宛の特大家具は配送手配できませんでした（大型商品のため）。
```

※ここで一旦読むのを止めて、ご自身でコーディングを行なってみてください。その後で、続きを読んでください。

## 好ましくない実装

では、シナリオに従い追加実装をしていきましょう。

まず、途中まで確保・決済済みの内容を元に戻す処理が必要なため、`InventoryService` クラスに在庫を解放する `release` メソッド、`PaymentService` クラスに決済を取り消す `refund` メソッドを追加します。

**`InventoryService.java`**

```java
package example;

public class InventoryService {
    private static final int MAX_RESERVABLE_QUANTITY = 10; // 1 回の注文で確保できる最大数量を表す定数

    public boolean reserve(String productName, int quantity) {
        if (quantity > MAX_RESERVABLE_QUANTITY) {
            System.out.println(productName + "の在庫が不足しているため、確保できませんでした。");
            return false;
        }
        System.out.println(productName + "の在庫を" + quantity + "個確保しました。");
        return true;
    }

    /* ここを追加（ここから） */
    public void release(String productName, int quantity) {
        System.out.println(productName + "の確保していた在庫" + quantity + "個を解放しました。");
    }
    /* ここを追加（ここまで） */
}
```

**`PaymentService.java`**

```java
package example;

public class PaymentService {
    private static final int CREDIT_LIMIT = 1_000_000; // 1 回の決済で許容する利用限度額を表す定数

    public boolean charge(String customerName, int amount) {
        if (amount > CREDIT_LIMIT) {
            System.out.println(customerName + "様の決済に失敗しました（利用限度額を超えています）。");
            return false;
        }
        System.out.println(customerName + "様に" + amount + "円の決済処理を行いました。");
        return true;
    }

    /* ここを追加（ここから） */
    public void refund(String customerName, int amount) {
        System.out.println(customerName + "様への" + amount + "円の決済を取り消しました。");
    }
    /* ここを追加（ここまで） */
}
```

次に、`OrderProcessor` クラスの `processOrder` メソッドに、失敗時の取り消し処理を追記する必要がありますが、真っ先に思いつくのは、この取り消し処理をそのまま `processOrder` メソッドに書き加えていく、という実装ではないでしょうか？

**`OrderProcessor.java`**

```java
package example;

public class OrderProcessor {
    private InventoryService inventoryService = new InventoryService();
    private PaymentService paymentService = new PaymentService();
    private ShippingService shippingService = new ShippingService();

    public void processOrder(String customerName, String productName, int quantity, int amount) {
        System.out.println(customerName + "様の注文を受け付けました。");
        if (!inventoryService.reserve(productName, quantity)) {
            /* ここを追加（ここから） */
            System.out.println("在庫確保に失敗したため、注文を中止しました。");
            /* ここを追加（ここまで） */
            return;
        }
        if (!paymentService.charge(customerName, amount)) {
            /* ここを追加（ここから） */
            System.out.println("決済に失敗したため、注文を中止しました。");
            inventoryService.release(productName, quantity);
            /* ここを追加（ここまで） */
            return;
        }
        if (!shippingService.arrange(customerName, productName)) {
            /* ここを追加（ここから） */
            System.out.println("配送手配に失敗したため、注文を中止しました。");
            paymentService.refund(customerName, amount);
            inventoryService.release(productName, quantity);
            /* ここを追加（ここまで） */
            return;
        }
        System.out.println("注文が完了しました。");
    }
}
```

**`Main.java`**

```java
package example;

public class Main {
    public static void main(String[] args) {
        OrderProcessor orderProcessor = new OrderProcessor();
        orderProcessor.processOrder("鈴木", "ノートパソコン", 1, 120000);
        System.out.println();
        orderProcessor.processOrder("田中", "文房具セット", 15, 3000);
        System.out.println();
        orderProcessor.processOrder("佐藤", "デジタルカメラ", 1, 2000000);
        System.out.println();
        orderProcessor.processOrder("高橋", "特大家具", 1, 45000);
    }
}
```

**実行結果**

```
鈴木様の注文を受け付けました。
ノートパソコンの在庫を1個確保しました。
鈴木様に120000円の決済処理を行いました。
鈴木様宛にノートパソコンの配送手配を行いました。
注文が完了しました。

田中様の注文を受け付けました。
文房具セットの在庫が不足しているため、確保できませんでした。
在庫確保に失敗したため、注文を中止しました。

佐藤様の注文を受け付けました。
デジタルカメラの在庫を1個確保しました。
佐藤様の決済に失敗しました（利用限度額を超えています）。
決済に失敗したため、注文を中止しました。
デジタルカメラの確保していた在庫1個を解放しました。

高橋様の注文を受け付けました。
特大家具の在庫を1個確保しました。
高橋様に45000円の決済処理を行いました。
高橋様宛の特大家具は配送手配できませんでした（大型商品のため）。
配送手配に失敗したため、注文を中止しました。
高橋様への45000円の決済を取り消しました。
特大家具の確保していた在庫1個を解放しました。
```

コンパイルエラーがなく結果が出力されていることから、一見すると実装・動作確認ともに問題ないように見えます。

しかし、この実装には以下の問題点があります。

- `OrderProcessor` クラスの `processOrder` メソッドの中に、処理が通ったときの 3 手順に加えて、失敗パターンごとの取り消し処理（`release`・`refund` メソッドの呼び出しとその順序）がすべて書き込まれており、`processOrder` メソッドの中が長くなり読みにくくなる。しかも手順が後の方で失敗するほど取り消し処理の呼び出しは増えていくため、後半の条件分岐ほど中身が肥大化していく。
- 取り消しの順序についての判断基準が `OrderProcessor` クラス 1 つに集中しており、呼び出し側であるはずの `OrderProcessor` が、本来意識する必要のない注文処理の詳細な手順にまで責任を持ってしまっている。
- さらに、同じタイミングで追加する、一時的な障害により失敗した注文を再試行するバッチ処理でも、まったく同じ在庫確保・決済処理・配送手配の手順と取り消し処理が必要になる。好ましくない実装のままバッチ処理を実装すると、`OrderProcessor` クラスに書いた取り消しロジックと同じ内容を、バッチ処理のクラスにもコピーして書くことになってしまう。

## 正しい実装

では、好ましくない実装で挙げた問題点を解決するにはどうすればよいのでしょうか？

これらの問題を解決するのが **Facade パターン**です。<br>
在庫確保・決済処理・配送手配、及びそれらの失敗時の取り消し処理までを含めた一連の手順を「窓口」となる 1 つのクラスにまとめることで、呼び出し側は手順の複雑さを意識せず、窓口となるクラスのメソッドを呼び出すだけで済むようになります。

**`OrderFacade.java`**

```java
package example;

public class OrderFacade {
    private InventoryService inventoryService = new InventoryService();
    private PaymentService paymentService = new PaymentService();
    private ShippingService shippingService = new ShippingService();

    public void placeOrder(String customerName, String productName, int quantity, int amount) {
        if (!inventoryService.reserve(productName, quantity)) {
            System.out.println("在庫確保に失敗したため、注文を中止しました。");
            return;
        }
        if (!paymentService.charge(customerName, amount)) {
            System.out.println("決済に失敗したため、注文を中止しました。");
            inventoryService.release(productName, quantity);
            return;
        }
        if (!shippingService.arrange(customerName, productName)) {
            System.out.println("配送手配に失敗したため、注文を中止しました。");
            paymentService.refund(customerName, amount);
            inventoryService.release(productName, quantity);
            return;
        }
        System.out.println("注文が完了しました。");
    }
}
```

`OrderFacade` は新たに追加したクラスで、既存の `InventoryService`・`PaymentService`・`ShippingService` クラスをフィールドとして保持し、`placeOrder` メソッド 1 つに処理が通ったときの手順と取り消しの手順をまとめています。手順自体は好ましくない実装の `OrderProcessor` クラスにあったものと同じですが、この手順を知っているクラスが `OrderFacade` という 1 箇所に集約された点が異なります。

続いて、`OrderProcessor` クラスを見ていきましょう。

**`OrderProcessor.java`**

```java
package example;

public class OrderProcessor {
    private OrderFacade orderFacade = new OrderFacade();

    public void processOrder(String customerName, String productName, int quantity, int amount) {
        System.out.println(customerName + "様の注文を受け付けました。");
        orderFacade.placeOrder(customerName, productName, quantity, amount);
    }
}
```

最後に、実行クラスを見ていきましょう。

**`Main.java`**

```java
package example;

public class Main {
    public static void main(String[] args) {
        OrderProcessor orderProcessor = new OrderProcessor();
        orderProcessor.processOrder("鈴木", "ノートパソコン", 1, 120000);
        System.out.println();
        orderProcessor.processOrder("田中", "文房具セット", 15, 3000);
        System.out.println();
        orderProcessor.processOrder("佐藤", "デジタルカメラ", 1, 2000000);
        System.out.println();
        orderProcessor.processOrder("高橋", "特大家具", 1, 45000);
    }
}
```

**実行結果**

```
鈴木様の注文を受け付けました。
ノートパソコンの在庫を1個確保しました。
鈴木様に120000円の決済処理を行いました。
鈴木様宛にノートパソコンの配送手配を行いました。
注文が完了しました。

田中様の注文を受け付けました。
文房具セットの在庫が不足しているため、確保できませんでした。
在庫確保に失敗したため、注文を中止しました。

佐藤様の注文を受け付けました。
デジタルカメラの在庫を1個確保しました。
佐藤様の決済に失敗しました（利用限度額を超えています）。
決済に失敗したため、注文を中止しました。
デジタルカメラの確保していた在庫1個を解放しました。

高橋様の注文を受け付けました。
特大家具の在庫を1個確保しました。
高橋様に45000円の決済処理を行いました。
高橋様宛の特大家具は配送手配できませんでした（大型商品のため）。
配送手配に失敗したため、注文を中止しました。
高橋様への45000円の決済を取り消しました。
特大家具の確保していた在庫1個を解放しました。
```

`OrderProcessor` クラスを振り返ると、好ましくない実装では処理が通ったときの手順と取り消しの手順をすべて自身の `processOrder` メソッドに直接書いていましたが、正しい実装では `OrderFacade` クラスのインスタンスを 1 つ持ち、`placeOrder` メソッドを呼び出すだけになっています。

実行結果は、好ましくない実装とまったく同じになっています。

続いて、一時的な障害により失敗した注文を再試行するバッチ処理も見ていきましょう。

※実務では、次の `FailedOrder` のようなエンティティクラスは `entity` パッケージなど専用のディレクトリに切り出すのが一般的です。しかし、本記事ではパッケージ構成を主題としないため `example` パッケージ直下にまとめています。

**`FailedOrder.java`**

```java
package example;

public record FailedOrder(String customerName, String productName, int quantity, int amount) {
}
```

**`FailedOrderRetryBatch.java`**

```java
package example;

import java.util.List;

public class FailedOrderRetryBatch {
    private OrderFacade orderFacade = new OrderFacade();

    public void retryAll(List<FailedOrder> failedOrders) {
        for (FailedOrder failedOrder: failedOrders) {
            orderFacade.placeOrder(failedOrder.customerName(), failedOrder.productName(),
                    failedOrder.quantity(), failedOrder.amount());
        }
    }
}
```

`FailedOrderRetryBatch` クラスも `OrderFacade` クラスのインスタンスを 1 つ持ち、`placeOrder` メソッドを呼び出すだけです。在庫確保・決済処理・配送手配とその取り消しという複雑な手順を、`OrderProcessor` クラスとまったく同じコードで重複させることなく再利用できています。

**`RetryBatchMain.java`（実行クラス）**

```java
package example;

import java.util.List;

public class RetryBatchMain {
    public static void main(String[] args) {
        FailedOrderRetryBatch retryBatch = new FailedOrderRetryBatch();
        List<FailedOrder> failedOrders = List.of(
                new FailedOrder("田中", "文房具セット", 5, 3000),
                new FailedOrder("佐藤", "デジタルカメラ", 1, 1200000));
        retryBatch.retryAll(failedOrders);
    }
}
```

**実行結果**

```
文房具セットの在庫を5個確保しました。
田中様に3000円の決済処理を行いました。
田中様宛に文房具セットの配送手配を行いました。
注文が完了しました。
デジタルカメラの在庫を1個確保しました。
佐藤様の決済に失敗しました（利用限度額を超えています）。
決済に失敗したため、注文を中止しました。
デジタルカメラの確保していた在庫1個を解放しました。
```

`RetryBatchMain` クラスは `OrderProcessor` クラスを経由せず、`FailedOrderRetryBatch` クラスから直接 `OrderFacade` クラスを呼び出しています。バッチ処理は顧客からの新規注文ではないため受付ログは不要ですが、在庫確保・決済処理・配送手配とその取り消しという処理そのものは、通常の注文受付とまったく同じものが必要です。この手順を `OrderFacade` クラス 1 つに集約しておいたことで、`FailedOrderRetryBatch` クラスは取り消しロジックを一切書かずに済んでいます。

以上のような実装を行うと、以下のメリットがあります。

- 注文処理の手順が変わる場合、`OrderFacade` クラスの `placeOrder` メソッド 1 箇所を修正するだけで済み、`OrderProcessor` クラスは変更する必要がない。
- `OrderProcessor` クラスは在庫確保・決済処理・配送手配それぞれの成否や取り消しの順序を意識する必要がなくなり、手順が複雑になっても影響を受けにくくなる。
- `OrderProcessor` クラスと `FailedOrderRetryBatch` クラスのように、同じ手順を複数の呼び出し元から利用する場合でも、それぞれが `OrderFacade` クラスを呼び出すだけで済み、取り消しロジックを重複して実装する必要がない。

なお、`OrderFacade` クラスに手順の判断基準を集約したことで `OrderProcessor` クラスはその複雑さから解放されますが、新しい手順（例えば「配送手配の後にポイントを付与する」）を追加する際に `placeOrder` メソッドを読み解いて正しい位置に処理を挿入する手間そのものがなくなるわけではありません。Facade パターンが解決するのは、手順に関する判断基準を呼び出し側から切り離すことであり、手順自体の複雑さを軽減することではない点には注意が必要です。

## まとめ

正しい実装を振り返ると、`OrderFacade` クラスは在庫確保・決済処理・配送手配という処理が通ったときの手順と、失敗時の取り消し処理という複雑な手順をまとめて 1 つのメソッドに集約しました。`OrderProcessor` クラスと `FailedOrderRetryBatch` クラスは、どちらもその窓口を呼び出すだけの薄い実装になり、複雑な手順を重複して書く必要がなくなっています。<br>
このように、Facade パターンは、複数のクラスにまたがる複雑な手順に対して、利用者が扱いやすい単純な窓口を 1 つ提供するパターンです。

本記事の内容はここまでとなります。

以降は「もう少し深く知りたい」という方向けの補足となります。今回学んだパターンに繋がる設計原則や、実務で役立つ背景知識について触れています。

---

<a id="深堀り1"></a>

## 【深堀り①】Facade はサブシステムへの直接アクセスを禁止しない

正しい実装を振り返ると、`OrderProcessor` クラスは `OrderFacade` クラスの `placeOrder` メソッドだけを呼び出すようになりました。ただし、Facade パターンは「サブシステムクラスへの直接アクセスを禁止する」ものではありません。`OrderFacade` クラスを導入した後も、`InventoryService` クラスや `PaymentService` クラスは `public` のままであり、必要であれば呼び出し側がこれらのクラスを直接使うこともできます。

例えば、「決済せずに在庫だけを事前確認したい」という別の要件が出てきた場合、`OrderFacade` クラスに新しいメソッドを追加することもできますが、単純な在庫確認だけであれば `InventoryService` クラスを直接呼び出す方が素直な場合もあります。

Facade パターンが提供するのは、あくまで「よく使う一連の手順をまとめた便利な窓口」であり、細かい制御が必要な場面まで奪うものではありません。この柔軟性は、サブシステムクラスをパッケージ内に隠蔽して `Facade` 経由のアクセスに限定してしまう設計とは対照的であり、どちらを選ぶかはサブシステムクラスへの直接アクセスを許可する必要があるかどうかによって決まります。

<a id="深堀り2"></a>

## 【深堀り②】デメテルの法則との関係

好ましくない実装を振り返ると、`OrderProcessor` クラスは、`InventoryService`・`PaymentService`・`ShippingService` という 3 つのサブシステムクラスの存在と、その呼び出し順序や取り消しの手順を直接知っている必要がありました。正しい実装では、これらのクラスは `OrderFacade` クラス 1 つだけを知っていればよくなっています。同様に `FailedOrderRetryBatch` クラスも `OrderFacade` クラスだけを知っていればよく、複数の呼び出し元がそれぞれサブシステムクラスの詳細を知る必要がなくなっています。

この「やり取りするオブジェクトの数を減らす」という考え方は、「**デメテルの法則（Law of Demeter）**」と呼ばれる設計原則に沿っています。デメテルの法則は「最小知識の原則」とも呼ばれ、あるオブジェクトが直接やり取りするオブジェクトの範囲を必要最小限に留めるべきだという考え方です。

正しい実装の `OrderProcessor` クラスがやり取りするオブジェクトは `OrderFacade` クラスだけになり、`InventoryService` クラスなどのサブシステムクラスの存在を知る必要がなくなりました。Facade パターンは、このデメテルの法則を実践するための設計手段の一つと言えます。

詳しくは「デメテルの法則」や「最小知識の原則」で検索してみてください。

<a id="深堀り3"></a>

## 【深堀り③】Java 標準ライブラリにおける Facade パターンの例

Java 標準ライブラリにおける Facade パターンの例として、`javax.imageio` パッケージの `ImageIO` クラスによる画像読み込みの仕組みを見ていきましょう。

**`ImageIO.java`（一部抜粋）**

```java
public class ImageIO {
    public static BufferedImage read(File input) throws IOException {
        if (input == null) {
            throw new IllegalArgumentException("input == null!");
        }
        if (!input.canRead()) {
            throw new IIOException("Can't read input file!");
        }

        ImageInputStream stream = createImageInputStream(input);
        if (stream == null) {
            throw new IIOException("Can't create an ImageInputStream!");
        }
        BufferedImage bi = read(stream);
        if (bi == null) {
            stream.close();
        }
        return bi;
    }

    public static BufferedImage read(ImageInputStream stream)
        throws IOException {
        if (stream == null) {
            throw new IllegalArgumentException("stream == null!");
        }

        Iterator<ImageReader> iter = getImageReaders(stream);
        if (!iter.hasNext()) {
            return null;
        }

        ImageReader reader = iter.next();
        ImageReadParam param = reader.getDefaultReadParam();
        reader.setInput(stream, true, true);
        BufferedImage bi;
        try (stream) {
            bi = reader.read(0, param);
        } catch (RuntimeException e) {
            throw new IIOException(e.toString(), e);
        } finally {
            reader.dispose();
        }
        return bi;
    }
}
```

> 引用元: OpenJDK [ImageIO.java](https://github.com/openjdk/jdk/blob/master/src/java.desktop/share/classes/javax/imageio/ImageIO.java)

`read(File input)` メソッドの中身を振り返ると、渡された `File` クラスのインスタンスから `ImageInputStream`（画像データを読み込むための入力ストリーム）を生成し、実際の読み込み処理をもう一方の `read(ImageInputStream stream)` メソッドに委ねています。`read(ImageInputStream stream)` メソッドでは、`getImageReaders` メソッドによって、ストリームの内容を読み取れる `ImageReader`（PNG や JPEG など画像形式ごとのデコード処理を行うクラス）を登録済みの候補の中から探し出しています。見つかった `ImageReader` に読み込みパラメータや入力ストリームを設定した上で実際のデコード処理（`reader.read`）を行い、最後に `reader.dispose()` でリソースを解放しています。

利用する側は `ImageIO.read(file)` という 1 行を呼び出すだけで、画像形式ごとの `ImageReader` の候補探し・設定・デコード・リソース解放という一連の手順を意識せずに済みます。本記事の `OrderFacade` クラスが在庫確保・決済処理・配送手配とその取り消し処理という複雑な手順をまとめたのと同様に、`ImageIO` クラスも `ImageInputStream`・`ImageReader` などからなるサブシステムをまとめる窓口として機能しています。

<a id="深堀り4"></a>

## 【深堀り④】GoF デザインパターンとの位置づけ

今回使った Facade パターンは、GoF（Gang of Four）の 23 のデザインパターンのうち「構造パターン」に分類されます。<br>
詳しくは「GoF」で検索してみてください。
