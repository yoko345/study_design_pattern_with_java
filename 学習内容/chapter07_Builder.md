# Builder（ビルダー）パターン ― 複雑なオブジェクトの組み立て手順を統一する

次のような経験をしたことはありませんか？

> 複数の手順を経て完成するオブジェクトを、条件や用途に応じて作り分ける必要が生じた。この際、生成コードを各所に直書きしているうちに重複が膨らんで、新しい種類を追加するたびに似たような変更を何箇所にも加えなければならなくなった。

この記事では、EC サイトの注文管理システムのテストというシナリオを通して、Builder パターンがこの問題をどのように解決するかを紹介します。

## 目次

- [【具体例】](#具体例)
    - [シナリオ](#シナリオ)
    - [既存コードの仕様](#既存コードの仕様)
    - [テストケースの方針](#テストケースの方針)
- [好ましくない実装](#好ましくない実装)
- [正しい実装](#正しい実装)
- [まとめ](#まとめ)
- [【深堀り①】Director の役割 ― 組み立て順序の強制](#深堀り1)
- [【深堀り②】OCP（オープン・クローズドの原則）](#深堀り2)
- [【深堀り③】実行クラスでの型宣言 ― 抽象型 vs 具体型](#深堀り3)
- [【深堀り④】GoF デザインパターンとの位置づけ](#深堀り4)

---

## 【具体例】

### シナリオ

> あなたは EC サイトの開発チームに所属しています。<br>
> リリースを間近に控え、注文管理システムの注文を処理するクラス（`OrderService` クラス）に対する単体テストの拡充を任されました。<br>
> 現在は正常系のテストが 1 件だけ用意されていますが、PM から「テストカバレッジを上げるため、境界値テストと異常系テストも追加してほしい」という要望が来ています。

### 既存コードの仕様

- `OrderService`（既存クラス）

注文を処理するクラスです。

| メソッド  | 戻り値の型 | 説明                       |
| --------- | ---------- | -------------------------- |
| `process` | `boolean`  | 注文処理を行い、成否を返す |

**`OrderService.java`**

```java
package example;

public class OrderService {
    public boolean process(Order order, Payment payment) {
        // 実際には在庫確認・決済処理・注文確定などの処理が行われるが、本記事の主題とは関係ないため省略
        return true; // 処理が省略されているため便宜上 true を返している
    }
}
```

※テストを書くには、この `process` メソッドの引数となる `Order` と `Payment` のデータを用意する必要があります。

次からは、注文に関わるエンティティクラスの仕様です。<br>
注文情報を保持する `Order` は顧客情報（`Customer`）や注文明細（`OrderItem`）を、支払い情報を保持する `Payment` は `Order` を、それぞれフィールドとして保持するクラスであることに留意してください。

※一般的に、エンティティはリレーショナル・データベースの表を表現し、各エンティティ・インスタンスはその表の行に相当します（→ [4 エンティティの理解](https://docs.oracle.com/cd/F32751_01/toplink/14.1.1.0/concepts/understanding-entities.html)）。

- `Customer`（既存クラス・注文に関わるエンティティ）

顧客情報を保持するクラスです。

| フィールド | 型       | 説明           |
| ---------- | -------- | -------------- |
| `name`     | `String` | 顧客名         |
| `email`    | `String` | メールアドレス |
| `tell`     | `String` | 電話番号       |

**`Customer.java`**

```java
package example;

public class Customer {
    private final String name;
    private final String email;
    private final String tell;

    public Customer(String name, String email, String tell) {
        this.name = name;
        this.email = email;
        this.tell = tell;
    }
    // 各フィールドに対応する getter メソッドは、本記事の主題とは関係ないため省略
}
```

- `OrderItem`（既存クラス・注文に関わるエンティティ）

注文明細（1 商品分）を保持するクラスです。

| フィールド    | 型       | 説明   |
| ------------- | -------- | ------ |
| `productName` | `String` | 商品名 |
| `unitPrice`   | `int`    | 単価   |
| `quantity`    | `int`    | 数量   |

**`OrderItem.java`**

```java
package example;

public class OrderItem {
    private final String productName;
    private final int unitPrice;
    private final int quantity;

    public OrderItem(String productName, int unitPrice, int quantity) {
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }
    // 各フィールドに対応する getter メソッドは、本記事の主題とは関係ないため省略
}
```

- `Order`（既存クラス・注文に関わるエンティティ）

注文情報を保持するクラスです。

| フィールド  | 型                | 説明             |
| ----------- | ----------------- | ---------------- |
| `customer`  | `Customer`        | 注文した顧客     |
| `items`     | `List<OrderItem>` | 注文明細のリスト |
| `orderDate` | `LocalDate`       | 注文日           |

**`Order.java`**

```java
package example;

public class Order {
    private final Customer customer;
    private final List<OrderItem> items;
    private final LocalDate orderDate;

    public Order(Customer customer, List<OrderItem> items, LocalDate orderDate) {
        this.customer = customer;
        this.items = items;
        this.orderDate = orderDate;
    }
    // 各フィールドに対応する getter メソッドは、本記事の主題とは関係ないため省略
}
```

- `Payment`（既存クラス・注文に関わるエンティティ）

支払い情報を保持するクラスです。

| フィールド | 型       | 説明       |
| ---------- | -------- | ---------- |
| `order`    | `Order`  | 対象の注文 |
| `method`   | `String` | 支払い方法 |
| `amount`   | `int`    | 支払い金額 |

**`Payment.java`**

```java
package example;

public class Payment {
    private final Order order;
    private final String method;
    private final int amount;

    public Payment(Order order, String method, int amount) {
        this.order = order;
        this.method = method;
        this.amount = amount;
    }
    // 各フィールドに対応する getter メソッドは、本記事の主題とは関係ないため省略
}
```

最後に、テストクラスの仕様です。

- `OrderServiceTest`（既存クラス・テストを行うためのクラス）

注文を処理するクラス `OrderService` の `process` メソッドの動作を検証するテストクラスです。<br>
現在は正常系のテストのみが実装されています。

| メソッド               | 戻り値の型 | 説明                                                                                     |
| ---------------------- | ---------- | ---------------------------------------------------------------------------------------- |
| 正常な注文が処理される | `void`     | 有効な顧客情報・注文内容を入力値とし、`process` が `true` を返す（正常系）ことを確認する |

**`OrderServiceTest.java`**

```java
package example;

class OrderServiceTest {
    private final OrderService orderService = new OrderService();

    /**
     * 正常系
     */
    @Test
    void 正常な注文が処理される() {
        Customer customer = new Customer("田中太郎", "tanaka@example.com", "090-1234-5678");
        Order order = new Order(customer, List.of(new OrderItem("ノートPC", 100_000, 1)),
                LocalDate.of(2024, 1, 15));
        Payment payment = new Payment(order, "credit_card", 100_000);

        assertTrue(orderService.process(order, payment));
    }
}
```

### テストケースの方針

テストは一般的に「正常系」「境界値系」「異常系」の確認を行います。<br>
今回のシナリオでは下記のようなことをテストすることを想定しています。

- **正常系**：有効な顧客情報と通常の注文内容
- **境界値系**：名前が最大文字数・メールアドレスが最短形式・注文数量が上限値 など
- **異常系**：不正なメールアドレス・空の注文リスト・負の金額 など

※ここで一旦読むのを止めて、ご自身でコーディングを行なってみてください。その後で、続きを読んでください。

## 好ましくない実装

では、シナリオに従って「境界値テスト」「異常系テスト」の実装をしていきましょう。

真っ先に思いつくのは、既存の正常系テストをコピペして、設定値だけを変えていく方法ではないでしょうか？

**`OrderServiceTest.java`**

```java
package example;

class OrderServiceTest {
    private final OrderService orderService = new OrderService();

    /**
     * 正常系
     */
    @Test
    void 正常な注文が処理される() {
        Customer customer = new Customer("田中太郎", "tanaka@example.com", "090-1234-5678");
        Order order = new Order(customer, List.of(new OrderItem("ノートPC", 100_000, 1)), LocalDate.of(2024, 1, 15));
        Payment payment = new Payment(order, "credit_card", 100_000);
        assertTrue(orderService.process(order, payment));
    }

    /**
     * 境界値系 - 名前が最大文字数
     */
    @Test
    void 名前が最大文字数の顧客でも処理される() {
        Customer customer = new Customer("あ".repeat(50), "tanaka@example.com", "090-1234-5678");
        Order order = new Order(customer, List.of(new OrderItem("ノートPC", 100_000, 1)), LocalDate.of(2024, 1, 15));
        Payment payment = new Payment(order, "credit_card", 100_000);
        assertTrue(orderService.process(order, payment));
    }

    /**
     * 境界値系 - メールアドレスが最短形式
     */
    @Test
    void メールアドレスが最短形式でも処理される() {
        Customer customer = new Customer("田中太郎", "a@b.c", "090-1234-5678");
        Order order = new Order(customer, List.of(new OrderItem("ノートPC", 100_000, 1)), LocalDate.of(2024, 1, 15));
        Payment payment = new Payment(order, "credit_card", 100_000);
        assertTrue(orderService.process(order, payment));
    }

    /**
     * 境界値系 - 注文数量が上限値
     */
    @Test
    void 注文数量が上限値でも処理される() {
        Customer customer = new Customer("田中太郎", "tanaka@example.com", "090-1234-5678");
        Order order = new Order(customer, List.of(new OrderItem("消耗品", 1, 99)), LocalDate.of(2024, 1, 15));
        Payment payment = new Payment(order, "cash", 99);
        assertTrue(orderService.process(order, payment));
    }

    /**
     * 異常系 - 不正なメールアドレス
     */
    @Test
    void 不正なメールアドレスは処理に失敗する() {
        Customer customer = new Customer("田中太郎", "not-an-email", "090-1234-5678");
        Order order = new Order(customer, List.of(new OrderItem("ノートPC", 100_000, 1)), LocalDate.of(2024, 1, 15));
        Payment payment = new Payment(order, "credit_card", 100_000);
        assertFalse(orderService.process(order, payment));
    }

    /**
     * 異常系 - 空の注文リスト
     */
    @Test
    void 空の注文リストは処理に失敗する() {
        Customer customer = new Customer("田中太郎", "tanaka@example.com", "090-1234-5678");
        Order order = new Order(customer, List.of(), LocalDate.of(2024, 1, 15));
        Payment payment = new Payment(order, "credit_card", 0);
        assertFalse(orderService.process(order, payment));
    }

    /**
     * 異常系 - 負の金額
     */
    @Test
    void 負の金額は処理に失敗する() {
        Customer customer = new Customer("田中太郎", "tanaka@example.com", "090-1234-5678");
        Order order = new Order(customer, List.of(new OrderItem("ノートPC", 100_000, 1)), LocalDate.of(2024, 1, 15));
        Payment payment = new Payment(order, "credit_card", -1);
        assertFalse(orderService.process(order, payment));
    }
}
```

テストは動きますが、`Customer`・`Order`・`Payment` を生成する 3 行が、テストメソッドのたびに繰り返されています。7 メソッド × 3 行 = 21 行が重複しており、テストケースが増えるほど膨らみます。

「では `@BeforeEach`（各テストの前に自動実行されるセットアップメソッド）で共通化すればよいのでは？」と思うかもしれません。しかし、ここには落とし穴があります。

```java
@BeforeEach
void setUp() {
    customer = new Customer("田中太郎", "tanaka@example.com", "090-1234-5678");
    order    = new Order(customer, ...);   // customer に依存
    payment  = new Payment(order, ...);    // order に依存
}

@Test
void 名前が最大文字数の顧客でも処理される() {
    customer = new Customer("あ".repeat(50), ...);  // customer を差し替えると…
    order    = new Order(customer, ...);             // Order は Customer に依存するため作り直しが必要
    payment  = new Payment(order, ...);              // Payment は Order に依存するため作り直しが必要
    // @BeforeEach の 3 行が無駄になり、結局また 3 行書く羽目になる
}
```

`Customer` を差し替えると、`Order`（`Customer` に依存）と `Payment`（`Order` に依存）も作り直さなければなりません。**依存チェーンがある限り、`@BeforeEach` で 1 箇所だけ変えることはできません。** 結局、すべてのテストメソッドで 3 行を書き直すことになります。

---

## 正しい実装

Builder パターンを使って解決します。登場するクラスは次の通りです。

| クラス                    | 役割                                           |
| ------------------------- | ---------------------------------------------- |
| `TestDataBuilder`（抽象） | テストデータ構築の手順（3 ステップ）を定義する |
| `TestDataDirector`        | 3 ステップを正しい順序で呼び出す               |
| `NormalCaseBuilder`       | 正常系データを組み立てる                       |
| `EdgeCaseBuilder`         | 境界値系データを組み立てる                     |
| `ErrorCaseBuilder`        | 異常系データを組み立てる                       |
| `TestData`                | 構築済みのテストデータを保持する               |

まず、構築済みのデータをまとめて返す `TestData` クラスを定義します。

**`TestData.java`**

```java
package example;

public class TestData {
    private final Customer customer;
    private final Order order;
    private final Payment payment;

    public TestData(Customer customer, Order order, Payment payment) {
        this.customer = customer;
        this.order = order;
        this.payment = payment;
    }
    // 各フィールドに対応する getter メソッドは、本記事の主題とは関係ないため省略
}
```

次に `TestDataBuilder` 抽象クラスです。構築手順を 3 つのメソッドとして定義します。

**`TestDataBuilder.java`**

```java
package example;

public abstract class TestDataBuilder {
    protected Customer customer;
    protected Order order;
    protected Payment payment;

    public abstract void buildCustomer();
    public abstract void buildOrder();
    public abstract void buildPayment();

    public TestData getTestData() {
        return new TestData(customer, order, payment);
    }
}
```

`TestDataDirector` は、**Customer → Order → Payment** の順序を固定して呼び出します。

**`TestDataDirector.java`**

```java
package example;

public class TestDataDirector {
    private final TestDataBuilder builder;

    public TestDataDirector(TestDataBuilder builder) {
        this.builder = builder;
    }

    public TestData construct() {
        builder.buildCustomer();
        builder.buildOrder();
        builder.buildPayment();
        return builder.getTestData();
    }
}
```

3 種類の Builder を実装します。

**`NormalCaseBuilder.java`**

```java
package example;

public class NormalCaseBuilder extends TestDataBuilder {
    @Override
    public void buildCustomer() {
        customer = new Customer(
            "田中太郎", "tanaka@example.com", "090-1234-5678");
    }

    @Override
    public void buildOrder() {
        order = new Order(
            customer,
            List.of(new OrderItem("ノートPC", 100_000, 1)),
            LocalDate.of(2024, 1, 15));
    }

    @Override
    public void buildPayment() {
        payment = new Payment(order, "credit_card", 100_000);
    }
}
```

**`EdgeCaseBuilder.java`**

```java
package example;

public class EdgeCaseBuilder extends TestDataBuilder {
    @Override
    public void buildCustomer() {
        customer = new Customer(
            "あ".repeat(50), "a@b.c", "000-0000-0000");
    }

    @Override
    public void buildOrder() {
        order = new Order(
            customer,
            List.of(new OrderItem("消耗品", 1, 99)),
            LocalDate.now());
    }

    @Override
    public void buildPayment() {
        payment = new Payment(order, "cash", 99);
    }
}
```

**`ErrorCaseBuilder.java`**

```java
package example;

public class ErrorCaseBuilder extends TestDataBuilder {
    @Override
    public void buildCustomer() {
        customer = new Customer("", "not-an-email", "");
    }

    @Override
    public void buildOrder() {
        order = new Order(customer, List.of(), LocalDate.now());
    }

    @Override
    public void buildPayment() {
        payment = new Payment(order, "unknown", -1);
    }
}
```

テストコードはこう変わります。

**`OrderServiceTest.java`**

```java
package example;

class OrderServiceTest {
    @Test
    void 正常な注文が処理される() {
        TestDataBuilder builder = new NormalCaseBuilder();
        TestData data = new TestDataDirector(builder).construct();

        boolean result = orderService.process(data.getOrder(), data.getPayment());

        assertTrue(result);
    }

    @Test
    void 境界値の注文が処理される() {
        TestDataBuilder builder = new EdgeCaseBuilder();
        TestData data = new TestDataDirector(builder).construct();
        // ...
    }

    @Test
    void 不正なデータで注文が拒否される() {
        TestDataBuilder builder = new ErrorCaseBuilder();
        TestData data = new TestDataDirector(builder).construct();
        // ...
    }
}
```

使う側は「どの Builder を渡すか」を選ぶだけで、組み立ての手順や順序を意識する必要がなくなりました。

---

## まとめ

Builder パターンの適用前後を整理します。

|                        | 好ましくない実装                                 | Builder パターン                |
| ---------------------- | ------------------------------------------------ | ------------------------------- |
| 種別の追加             | テストメソッドのセットアップをコピペして修正する | 新しい Builder クラスを追加する |
| 組み立て順序の保証     | 呼び出し側が順序を守る必要がある                 | Director が順序を強制する       |
| 種別ごとの生成ロジック | 各テストメソッドに重複して散在する               | Builder クラスごとに分離される  |

Builder パターンが特に力を発揮するのは、**複数のオブジェクトが依存関係を持ちながら段階的に組み立てる必要がある場合**です。今回のようなテストデータ生成のほか、複雑な設定オブジェクトの生成や環境ごとに異なる構成物を作る場面でも広く使われます。

---

[メモ]テスト実装に関して、簡単に深堀りを用意する。


<a id="深堀り1"></a>

## 【深堀り①】Director の役割 ― 組み立て順序の強制

`TestDataDirector` がなければ、テストコードは Builder のメソッドを直接呼ぶことになります。

```java
NormalCaseBuilder builder = new NormalCaseBuilder();
builder.buildCustomer();
builder.buildOrder();
builder.buildPayment();
TestData data = builder.getTestData();
```

一見問題ないように見えますが、呼び出し側がうっかり順序を間違えるリスクがあります。

```java
// 誤った順序で呼び出してしまうと...
builder.buildOrder();    // customer がまだ null → NullPointerException
builder.buildCustomer();
```

`Order` は `Customer` に依存しているため、`buildCustomer()` を先に呼ばなければ `buildOrder()` の中で `NullPointerException` が発生します。

Director はこの **「組み立てには正しい順序がある」という知識を一箇所に集め、使用者に意識させない** 役割を担っています。テストコードから見れば、「どの Builder を渡すか」だけを決めればよく、「どの順序で呼ぶか」は Director が責任を持ちます。

---

<a id="深堀り2"></a>

## 【深堀り②】OCP（オープン・クローズドの原則）

新たにパフォーマンステスト用のデータが必要になったとします。

好ましくない実装では、テストメソッドを 1 つ追加するたびに同じ 3 行のセットアップをコピペして修正するしかありませんでした。一方 Builder パターンなら、新しいクラスを追加するだけです。

**`StressTestBuilder.java`**

```java
package example;

public class StressTestBuilder extends TestDataBuilder {
    @Override
    public void buildCustomer() {
        customer = new Customer(
            "負荷テスト用", "stress@example.com", "000-0000-0000");
    }

    @Override
    public void buildOrder() {
        List<OrderItem> items = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            items.add(new OrderItem("商品" + i, 100, 1));
        }
        order = new Order(customer, items, LocalDate.now());
    }

    @Override
    public void buildPayment() {
        payment = new Payment(order, "credit_card", 100_000);
    }
}
```

既存の `NormalCaseBuilder`・`EdgeCaseBuilder`・`ErrorCaseBuilder`・`TestDataDirector` はどれも修正していません。**拡張に対して開いており、修正に対して閉じている**（OCP）状態が実現できています。

---

<a id="深堀り3"></a>

## 【深堀り③】実行クラスでの型宣言 ― 抽象型 vs 具体型

テストコードでの Builder の宣言をもう一度見てみます。

```java
TestDataBuilder builder = new NormalCaseBuilder();
```

これを具体型で宣言するとどうなるでしょうか。

```java
NormalCaseBuilder builder = new NormalCaseBuilder();
```

`TestDataDirector` のコンストラクタは `TestDataBuilder` 型を受け取るため、具体型のまま渡しても動作します。しかし、変数を抽象型で宣言しておくことには意味があります。

```java
// 条件によって Builder を切り替える場合
TestDataBuilder builder;
if (useStressTest) {
    builder = new StressTestBuilder();
} else {
    builder = new NormalCaseBuilder();
}
TestData data = new TestDataDirector(builder).construct();
```

抽象型で宣言しておくことで、**どの Builder を代入しても `Director` 以降のコードを一切修正せずに動作します。** 具体型で宣言してしまうと、別の Builder に切り替えるたびに変数の型も変更しなければならなくなります。

---

<a id="深堀り4"></a>

## 【深堀り④】GoF デザインパターンとの位置づけ

今回使った Builder パターンは、GoF（Gang of Four）の 23 のデザインパターンのうち「生成パターン」に分類されます。<br>
詳しくは「GoF」で検索してみてください。
