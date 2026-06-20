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
- [【深堀り①】OCP（オープン・クローズドの原則）](#深堀り1)
- [【深堀り②】実行クラスでの型宣言 ― 抽象型 vs 具体型](#深堀り2)
    - [追加仕様](#追加仕様)
    - [抽象型 vs 具体型](#抽象型-vs-具体型)
    - [まとめ](#深堀り2-まとめ)
    - [DI（依存性の注入）との関係](#di依存性の注入との関係)
- [【深堀り③】Fluent Builder（メソッドチェーン型）](#深堀り3)
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
        // メールアドレスは「@」必須
        if (!order.getCustomer().getEmail().contains("@")) return false;

        // 「注文明細」は 1 件以上必須
        if (order.getItems().isEmpty()) return false;

        // 「支払金額」は 0 以上
        if (payment.getAmount() < 0) return false;

        // 実務では必ず実装される在庫確認・決済処理・注文確定は、本記事の主題とは関係ないため省略
        return true;
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
        Order order = new Order(customer, List.of(new OrderItem("ノートPC", 100_000, 1)), LocalDate.of(2024, 1, 15));
        Payment payment = new Payment(order, "credit_card", 100_000);

        assertTrue(orderService.process(order, payment));
    }
}
```

### テストケースの方針

テストは一般的に「正常系」「境界値系」「異常系」の確認を行います。<br>
今回のシナリオでは下記をテストすることにします。（他にも様々な観点がありますが、本記事の主題から外れるため扱いません。）

- **正常系**：有効な顧客情報と通常の注文内容
- **境界値系**：名前が最大文字数・メールアドレスが最短形式・注文数量が上限値
- **異常系**：不正なメールアドレス・空の注文リスト・負の金額

※境界値として使用する具体的な値は次のシステム仕様に基づくこととします。

> - 顧客名の上限： 50 文字（顧客テーブルの `name` カラムが `VARCHAR(50)` 制約を持つと仮定しています。）
> - 注文数量の上限： 99 個（「同一商品を最大 99 個までしか 1 回の注文で頼めない」という業務ルールを仮定しています。）

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
        Customer customer = new Customer("あ".repeat(50), "tanaka@example.com", "090-1234-5678"); // DB における制約
        Order order = new Order(customer, List.of(new OrderItem("ノートPC", 100_000, 1)), LocalDate.of(2024, 1, 15));
        Payment payment = new Payment(order, "credit_card", 100_000);
        assertTrue(orderService.process(order, payment));
    }

    /**
     * 境界値系 - メールアドレスが最短形式
     */
    @Test
    void メールアドレスが最短形式でも処理される() {
        Customer customer = new Customer("田中太郎", "a@b.c", "090-1234-5678"); // 最短でも有効と判定されるメールアドレス
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
        Order order = new Order(customer, List.of(new OrderItem("消耗品", 1, 99)), LocalDate.of(2024, 1, 15)); // 業務ルールより、注文数量の上限を指定
        Payment payment = new Payment(order, "cash", 99); // 業務ルールより、注文数量の上限を指定したことによる影響
        assertTrue(orderService.process(order, payment));
    }

    /**
     * 異常系 - 不正なメールアドレス
     */
    @Test
    void 不正なメールアドレスは処理に失敗する() {
        Customer customer = new Customer("田中太郎", "not-an-email", "090-1234-5678"); // メールアドレスにて「@」がない
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
        Order order = new Order(customer, List.of(), LocalDate.of(2024, 1, 15)); // 「注文明細」が空
        Payment payment = new Payment(order, "credit_card", 0); // 「注文明細」が空であることによる影響（「支払金額」が 0）
        assertFalse(orderService.process(order, payment));
    }

    /**
     * 異常系 - 負の金額
     */
    @Test
    void 負の金額は処理に失敗する() {
        Customer customer = new Customer("田中太郎", "tanaka@example.com", "090-1234-5678");
        Order order = new Order(customer, List.of(new OrderItem("ノートPC", 100_000, 1)), LocalDate.of(2024, 1, 15));
        Payment payment = new Payment(order, "credit_card", -1); // 「支払金額」が負の値
        assertFalse(orderService.process(order, payment));
    }
}
```

テストを行うと全て成功します。<br>
しかし、各テストメソッドには `Customer`・`Order`・`Payment` を生成する 3 行が繰り返されています。<br>
現時点で 7 メソッド × 3 行 = 21 行が重複しており、他の観点を盛り込むたびに似たコードが増えてしまいます。

では、共通化できればよいのだから「各テストの前に自動実行されるセットアップメソッドである `@BeforeEach` を用いればよい」のではないかと思うかもしれません。<br>
実際にコードを通して確認してみましょう。

**`OrderServiceTest.java`**

```java
package example;

class OrderServiceTest {
    private final OrderService orderService = new OrderService();

    private Customer customer;
    private Order order;
    private Payment payment;

    @BeforeEach
    void setUp() {
        customer = new Customer("田中太郎", "tanaka@example.com", "090-1234-5678");
        order    = new Order(customer, List.of(new OrderItem("ノートPC", 100_000, 1)), LocalDate.of(2024, 1, 15));   // customer に依存
        payment  = new Payment(order, "credit_card", 100_000);
    }

    /**
     * 正常系
     */
    @Test
    void 正常な注文が処理される() {
        assertTrue(orderService.process(order, payment)); // setUp メソッドにより、assertTrue のみとなる
    }

    /**
     * 境界値系 - 名前が最大文字数
     */
    @Test
    void 名前が最大文字数の顧客でも処理される() {
        customer = new Customer("あ".repeat(50), "tanaka@example.com", "090-1234-5678"); // setUp メソッドを上書きする必要がある
        order    = new Order(customer, List.of(new OrderItem("ノートPC", 100_000, 1)), LocalDate.of(2024, 1, 15)); // Order は Customer に依存するため作り直しが必要
        payment  = new Payment(order, "credit_card", 100_000); // Payment は Order に依存するため作り直しが必要
        assertTrue(orderService.process(order, payment));
    }

    /**
     * 境界値系 - メールアドレスが最短形式
     */
    @Test
    void メールアドレスが最短形式でも処理される() {
        customer = new Customer("田中太郎", "a@b.c", "090-1234-5678"); // setUp メソッドを上書きする必要がある
        order    = new Order(customer, List.of(new OrderItem("ノートPC", 100_000, 1)), LocalDate.of(2024, 1, 15)); // Order は Customer に依存するため作り直しが必要
        payment  = new Payment(order, "credit_card", 100_000); // Payment は Order に依存するため作り直しが必要
        assertTrue(orderService.process(order, payment));
    }

    /**
     * 境界値系 - 注文数量が上限値
     */
    @Test
    void 注文数量が上限値でも処理される() {
        order = new Order(customer, List.of(new OrderItem("消耗品", 1, 99)), LocalDate.of(2024, 1, 15)); // setUp メソッドを上書きする必要がある
        payment = new Payment(order, "cash", 99); // setUp メソッドを上書きする必要がある
        assertTrue(orderService.process(order, payment));
    }

    /**
     * 異常系 - 不正なメールアドレス
     */
    @Test
    void 不正なメールアドレスは処理に失敗する() {
        customer = new Customer("田中太郎", "not-an-email", "090-1234-5678"); // setUp メソッドを上書きする必要がある
        order    = new Order(customer, List.of(new OrderItem("ノートPC", 100_000, 1)), LocalDate.of(2024, 1, 15)); // Order は Customer に依存するため作り直しが必要
        payment  = new Payment(order, "credit_card", 100_000); // Payment は Order に依存するため作り直しが必要
        assertFalse(orderService.process(order, payment));
    }

    /**
     * 異常系 - 空の注文リスト
     */
    @Test
    void 空の注文リストは処理に失敗する() {
        order = new Order(customer, List.of(), LocalDate.of(2024, 1, 15)); // setUp メソッドを上書きする必要がある
        payment = new Payment(order, "credit_card", 0); // setUp メソッドを上書きする必要がある
        assertFalse(orderService.process(order, payment));
    }

    /**
     * 異常系 - 負の金額
     */
    @Test
    void 負の金額は処理に失敗する() {
        payment = new Payment(order, "credit_card", -1); // setUp メソッドを上書きする必要がある
        assertFalse(orderService.process(order, payment));
    }
}
```

上記から、`Customer` を差し替えると、`Order`（`Customer` に依存）と `Payment`（`Order` に依存）も作り直さなければなりません。<br>
つまり、**依存チェーンがある限り、@BeforeEach で 1 箇所だけ変えても、他の修正を省くことはできないのです。**<br>
結局、`Customer` を差し替えたいテストでは毎回 3 行を書き直すことになります。

以上からこの実装には、以下の問題点があります。

- 新しい観点を追加するごとに、`Customer`・`Order`・`Payment` の生成コードが各テストメソッド内に重複して増え続ける
- `Customer`・`Order`・`Payment` の依存関係により、依存チェーンの起点となる `Customer` を変更すると残りの 2 つの `Order` も `Payment` も修正が必要なため、共通化が機能しない
    - 今回の例で言えば、`@BeforeEach` を用いても簡略化できない

## 正しい実装

では、好ましくない実装で挙げた問題点を解決するにはどうすればよいのでしょうか？

これらの問題を解決するのが **Builder パターン**です。

まず、`TestData` クラスを見てください。<br>
本クラスを作成することにより、今回のテストで必要なパラメータである `Customer`・`Order`・`Payment` のテストデータを保持できるようになります。

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

    public Customer getCustomer() {
        return customer;
    }

    public Order getOrder() {
        return order;
    }

    public Payment getPayment() {
        return payment;
    }
}
```

次に、抽象クラス `TestDataBuilder` を見てください。<br>
本クラスにより、今回のテストで必要なパラメータである `Customer`・`Order`・`Payment` の設定を強制できるようになります。

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

次に、`TestDataDirector` クラスを見てください。<br>
本クラスの `construct` メソッドにより、抽象クラス `TestDataBuilder` の実装が必須である `buildCustomer`・`buildOrder`・`buildPayment` メソッドを **Customer → Order → Payment** の順序を固定して呼び出せるようになります。<br>
これにより、`Order` は `Customer` に、`Payment` は `Order` に依存しているため、依存チェーンを破壊することなく、テストデータを設定できるようになります。
もし `TestDataDirector` を使わず `buildOrder()` を `buildCustomer()` より先に呼び出してしまうと、`Order` の生成時に `Customer` がまだ `null` のため `NullPointerException` が発生します。

**`TestDataDirector.java`**

```java
package example;

public class TestDataDirector {
    private final TestDataBuilder testDataBuilder;

    public TestDataDirector(TestDataBuilder testDataBuilder) {
        this.testDataBuilder = testDataBuilder;
    }

    public TestData construct() {
        testDataBuilder.buildCustomer();
        testDataBuilder.buildOrder();
        testDataBuilder.buildPayment();
        return testDataBuilder.getTestData();
    }
}
```

次に、`TestDataBuilder` の各実装クラスを見てください。<br>
正常系・境界値系・異常系それぞれの観点ごとに、個別のクラスとして分離しています。

- 正常系：`NormalCaseBuilder`
- 境界値系：`NameMaxLengthBuilder`・`EmailMinFormatBuilder`・`MaxQuantityBuilder`
- 異常系：`InvalidEmailBuilder`・`EmptyOrderBuilder`・`NegativeAmountBuilder`

上記クラスは抽象クラス `TestDataBuilder` を継承しているため、開発側はオーバーライドしたメソッド内（`buildCustomer`・`buildOrder`・`buildPayment`）で、テスト観点を満たすパラメータを設定するだけでよくなります。

**`NormalCaseBuilder.java`**

```java
package example;

public class NormalCaseBuilder extends TestDataBuilder {
    @Override
    public void buildCustomer() {
        customer = new Customer("田中太郎", "tanaka@example.com", "090-1234-5678");
    }

    @Override
    public void buildOrder() {
        order = new Order(customer, List.of(new OrderItem("ノートPC", 100_000, 1)), LocalDate.of(2024, 1, 15));
    }

    @Override
    public void buildPayment() {
        payment = new Payment(order, "credit_card", 100_000);
    }
}
```

**`NameMaxLengthBuilder.java`**

```java
package example;

public class NameMaxLengthBuilder extends TestDataBuilder {
    @Override
    public void buildCustomer() {
        customer = new Customer("あ".repeat(50), "tanaka@example.com", "090-1234-5678"); // DB における制約
    }

    @Override
    public void buildOrder() {
        order = new Order(customer, List.of(new OrderItem("ノートPC", 100_000, 1)), LocalDate.of(2024, 1, 15));
    }

    @Override
    public void buildPayment() {
        payment = new Payment(order, "credit_card", 100_000);
    }
}
```

**`EmailMinFormatBuilder.java`**

```java
package example;

public class EmailMinFormatBuilder extends TestDataBuilder {
    @Override
    public void buildCustomer() {
        customer = new Customer("田中太郎", "a@b.c", "090-1234-5678"); // 最短でも有効と判定されるメールアドレス
    }

    @Override
    public void buildOrder() {
        order = new Order(customer, List.of(new OrderItem("ノートPC", 100_000, 1)), LocalDate.of(2024, 1, 15));
    }

    @Override
    public void buildPayment() {
        payment = new Payment(order, "credit_card", 100_000);
    }
}
```

**`MaxQuantityBuilder.java`**

```java
package example;

public class MaxQuantityBuilder extends TestDataBuilder {
    @Override
    public void buildCustomer() {
        customer = new Customer("田中太郎", "tanaka@example.com", "090-1234-5678");
    }

    @Override
    public void buildOrder() {
        order = new Order(customer, List.of(new OrderItem("消耗品", 1, 99)), LocalDate.of(2024, 1, 15)); // 業務ルールによる注文数量の上限
    }

    @Override
    public void buildPayment() {
        payment = new Payment(order, "cash", 99); // 業務ルールより、注文数量の上限を指定したことによる影響
    }
}
```

**`InvalidEmailBuilder.java`**

```java
package example;

public class InvalidEmailBuilder extends TestDataBuilder {
    @Override
    public void buildCustomer() {
        customer = new Customer("田中太郎", "not-an-email", "090-1234-5678"); // 「@」を含まない不正なメールアドレス
    }

    @Override
    public void buildOrder() {
        order = new Order(customer, List.of(new OrderItem("ノートPC", 100_000, 1)), LocalDate.of(2024, 1, 15));
    }

    @Override
    public void buildPayment() {
        payment = new Payment(order, "credit_card", 100_000);
    }
}
```

**`EmptyOrderBuilder.java`**

```java
package example;

public class EmptyOrderBuilder extends TestDataBuilder {
    @Override
    public void buildCustomer() {
        customer = new Customer("田中太郎", "tanaka@example.com", "090-1234-5678");
    }

    @Override
    public void buildOrder() {
        order = new Order(customer, List.of(), LocalDate.of(2024, 1, 15)); // 「注文明細」が空
    }

    @Override
    public void buildPayment() {
        payment = new Payment(order, "credit_card", 0); // 「注文明細」が空であることによる影響（「支払金額」が 0）
    }
}
```

**`NegativeAmountBuilder.java`**

```java
package example;

public class NegativeAmountBuilder extends TestDataBuilder {
    @Override
    public void buildCustomer() {
        customer = new Customer("田中太郎", "tanaka@example.com", "090-1234-5678");
    }

    @Override
    public void buildOrder() {
        order = new Order(customer, List.of(new OrderItem("ノートPC", 100_000, 1)), LocalDate.of(2024, 1, 15));
    }

    @Override
    public void buildPayment() {
        payment = new Payment(order, "credit_card", -1); // 支払金額が負の値
    }
}
```

最後に、テストクラス `OrderServiceTest` を見てください。<br>
`TestDataDirector` クラスの `construct` メソッドは、`buildCustomer`・`buildOrder`・`buildPayment` を `Customer` → `Order` → `Payment` の順に呼び出します。<br>
そのため、`TestDataDirector` クラスの引数に、テスト観点ごとに対応した実装クラスを指定することで、テストに最適な構築済みのテストデータを取得できるようになります。

**`OrderServiceTest.java`**

```java
package example;

class OrderServiceTest {
    private final OrderService orderService = new OrderService();

    @Test
    void 正常な注文が処理される() {
        TestData testData = new TestDataDirector(new NormalCaseBuilder()).construct();
        assertTrue(orderService.process(testData.getOrder(), testData.getPayment()));
    }

    @Test
    void 名前が最大文字数の顧客でも処理される() {
        TestData testData = new TestDataDirector(new NameMaxLengthBuilder()).construct();
        assertTrue(orderService.process(testData.getOrder(), testData.getPayment()));
    }

    @Test
    void メールアドレスが最短形式でも処理される() {
        TestData testData = new TestDataDirector(new EmailMinFormatBuilder()).construct();
        assertTrue(orderService.process(testData.getOrder(), testData.getPayment()));
    }

    @Test
    void 注文数量が上限値でも処理される() {
        TestData testData = new TestDataDirector(new MaxQuantityBuilder()).construct();
        assertTrue(orderService.process(testData.getOrder(), testData.getPayment()));
    }

    @Test
    void 不正なメールアドレスは処理に失敗する() {
        TestData testData = new TestDataDirector(new InvalidEmailBuilder()).construct();
        assertFalse(orderService.process(testData.getOrder(), testData.getPayment()));
    }

    @Test
    void 空の注文リストは処理に失敗する() {
        TestData testData = new TestDataDirector(new EmptyOrderBuilder()).construct();
        assertFalse(orderService.process(testData.getOrder(), testData.getPayment()));
    }

    @Test
    void 負の金額は処理に失敗する() {
        TestData testData = new TestDataDirector(new NegativeAmountBuilder()).construct();
        assertFalse(orderService.process(testData.getOrder(), testData.getPayment()));
    }
}
```

以上のような実装を行うことで、次のメリットが得られます。

- `Customer`・`Order`・`Payment` の生成コードは `TestDataBuilder` の各実装クラスの中に閉じ込められます。これにより、テストメソッド自体は `TestDataDirector` クラスの引数に渡す `new XXXBuilder()` を変更するだけで済むため、観点を追加しても既存のテストメソッドは肥大化しなくなります。
- `Customer`・`Order`・`Payment` の組み立て順序は `TestDataDirector` クラスが定めています。これにより、依存関係を考慮してパラメータを構築するため、`@BeforeEach` のように「`Customer` を上書きしたら残りも手動で作り直す」という連鎖が起きません。
    - また、`TestDataBuilder` の各実装クラスは「そのシナリオにふさわしい値を設定する」ことだけに集中できます。

## まとめ

正しい実装では、`Customer`・`Order`・`Payment` の組み立て順序は `TestDataDirector` クラスが一手に担い、`TestDataBuilder` の各実装クラスはテスト観点に合わせた値の設定だけに集中しています。<br>
これにより、テストメソッドは `new XXXBuilder()` を切り替えるだけで、異なる観点のテストシナリオを扱えるようになります。

Builder パターンは、**複数のオブジェクトが依存関係を持ちながら段階的に組み立てる必要がある場面**で特に力を発揮します。<br>
なお、今回のようなテストデータ生成に Builder パターンを応用する手法は、**Test Data Builder** と呼ばれています。テスト系の記事や書籍でこの名前を見かけた際は、本記事の実装と同じ考え方だと思ってください。

> Test Data Builder：`Growing Object-Oriented Software, Guided by Tests` の著者 Nat Pryce が提唱したプラクティス

本記事の内容はここまでとなります。

以降は「もう少し深く知りたい」という方向けの補足となります。今回学んだパターンに繋がる設計原則や、実務で役立つ背景知識について触れています。

---

<a id="深堀り1"></a>

## 【深堀り①】OCP（オープン・クローズドの原則）

正しい実装を振り返ると、新しいテスト観点を追加することになった場合、`TestDataBuilder` を継承した新しいクラスを追加し、`OrderServiceTest` クラスで先のクラスを `TestDataDirector` クラスの引数に渡すだけで対応できます。その際、既存の `TestDataBuilder` を継承した実装クラスには一切手を加える必要がありません。

この「既存コードを変えずに、新しいクラスを追加するだけで機能を拡張できる」という設計は、「**OCP（Open/Closed Principle：オープン・クローズドの原則）**」と呼ばれる設計原則の実践です。Builder パターンは OCP を実現するための設計手段の一つと言えます。

詳しくは「OCP」や「オープン・クローズドの原則」で検索してみてください。

---

<a id="深堀り2"></a>

## 【深堀り②】実行クラスでの型宣言 ― 抽象型 vs 具体型

正しい実装を振り返ると、`TestDataDirector` の引数には、`TestDataBuilder` の各実装クラスのインスタンスを直接渡していました。

ここで、「ギフト注文でも正常に処理されること」を確認したいという要件が追加されたとしましょう。

この際、既存のテスト `正常な注文が処理される` メソッドの中で条件分岐を加えることで対応することが考えられます。<br>
このような修正を行うと、`TestDataDirector` の引数には、対応したテスト観点の実装クラスのインスタンスが代入された変数を渡すことになります。<br>
その時の変数の型は `TestDataBuilder`（抽象型）にすべきでしょうか、それとも `TestDataBuilder` の各実装クラス（具体型）にすべきでしょうか。

ここでは、どちらの型で宣言するのが好ましいのかを学びます。

### 追加仕様

**`GiftOrderBuilder.java`**

```java
package example;

public class GiftOrderBuilder extends TestDataBuilder {
    @Override
    public void buildCustomer() {
        customer = new Customer("田中太郎", "tanaka@example.com", "090-1234-5678");
    }

    @Override
    public void buildOrder() {
        order = new Order(customer, List.of(new OrderItem("ギフトセット", 5_000, 1)), LocalDate.of(2024, 1, 15));
    }

    @Override
    public void buildPayment() {
        payment = new Payment(order, "credit_card", 5_000);
    }
}
```

### 抽象型 vs 具体型

追加仕様が明らかになったので、注文種別を示す `isGiftOrder` フラグを使って既存のテストメソッドに条件分岐を加えると次のようになります。

**`OrderServiceTest.java`**

```java
package example;

class OrderServiceTest {
    private final OrderService orderService = new OrderService();

    @Test
    void 正常な注文が処理される() {
        boolean isGiftOrder = true;
        TestDataBuilder testDataBuilder;
        if (isGiftOrder) {
            testDataBuilder = new GiftOrderBuilder();
        } else {
            testDataBuilder = new NormalCaseBuilder();
        }
        TestData testData = new TestDataDirector(testDataBuilder).construct();
        assertTrue(orderService.process(testData.getOrder(), testData.getPayment()));
    }

    // 以降は、本文に関係ないため省略
}
```

`testDataBuilder` を抽象型 `TestDataBuilder` で宣言すると、`GiftOrderBuilder` クラスのインスタンスが代入されようが、`NormalCaseBuilder` クラスのインスタンスが代入されようが、条件分岐ブロック以降のコードは変更する必要がありません。

一方で、`testDataBuilder` を `NormalCaseBuilder` 型で宣言した次のコードを見てください。

**`OrderServiceTest.java`**

```java
package example;

class OrderServiceTest {
    private final OrderService orderService = new OrderService();

    @Test
    void 正常な注文が処理される() {
        boolean isGiftOrder = true;
        NormalCaseBuilder builder = new NormalCaseBuilder(); // 具体型で宣言
        if (isGiftOrder) {
            builder = new GiftOrderBuilder(); // コンパイルエラーが発生
        }
        TestData testData = new TestDataDirector(builder).construct();
        assertTrue(orderService.process(testData.getOrder(), testData.getPayment()));
    }

    // 以降は、本文に関係ないため省略
}
```

`NormalCaseBuilder` 型の変数 `builder` に `GiftOrderBuilder` クラスのインスタンスを代入できないため、コンパイルエラーが発生してコードが動作しなくなります。

<a id="深堀り2-まとめ"></a>

### まとめ

具体型で宣言すると別の実装クラスに切り替えるたびに変数の型変更が必要になります。<br>
そのため、抽象型 `TestDataBuilder` で宣言することが好ましいです。<br>
その結果、**どの Builder を代入しても条件分岐ブロック以降のコードを一切修正せずに動作できます。**

### DI（依存性の注入）との関係

正しい実装のコードを振り返ると、`TestDataDirector` は使用する `TestDataBuilder` の各実装クラスのインスタンスをコンストラクタ引数で受け取っていました。<br>
このような、あるクラスが必要とする依存オブジェクト（`TestDataBuilder`）を、内部で生成するのではなく外から渡す設計を **DI（Dependency Injection / 依存性の注入）** と呼びます。

もし `TestDataDirector` クラスが Builder を内部で生成する場合、次のようになります。

**`TestDataDirector.java`（内部生成の場合）**

```java
public class TestDataDirector {
    private final TestDataBuilder testDataBuilder;

    public TestDataDirector() {
        this.testDataBuilder = new NormalCaseBuilder();
    }

    // construct メソッドに変更はないため省略
}
```

この場合、別の `TestDataBuilder` の実装クラスに切り替えるには `TestDataDirector` 自体を修正しなければなりません。<br>
これは OCP に違反します。<br>
正しい実装のように、外から渡す設計（DI）にしておくことで、`TestDataDirector` は変更せずに `TestDataBuilder` の実装クラスを差し替えられます。
なお、今回はコンストラクタを通じて渡しているため、特に **コンストラクタインジェクション** と呼ばれます。

<a id="深堀り3"></a>

## 【深堀り③】Fluent Builder（メソッドチェーン型）

本記事では GoF 原典の Director クラスを介したスタイルを扱いましたが、実務では **Fluent Builder**（メソッドチェーン型）と呼ばれる別スタイルもよく見かけます。

Fluent Builder は、各セッターが `this` を返すことでメソッドチェーンを実現し、最後の `build()` で完成したオブジェクトを受け取る形式です。<br>
Director クラスが不要となり、呼び出し側でメソッドを自由な順序で繋げられるのが特徴です。

では、Fluent Builder の実装例を見ていきましょう。

**`PersonBuilder.java`**

```java
package example;

public class PersonBuilder {
    private String name;
    private int age;

    public PersonBuilder name(String name) {
        this.name = name;
        return this;
    }

    public PersonBuilder age(int age) {
        this.age = age;
        return this;
    }

    public Person build() {
        return new Person(name, age);
    }
}
```

**`Main.java`**

```java
package example;

public class Main {
    public static void main(String[] args) {
        Person person = new PersonBuilder()
                .name("田中太郎")
                .age(30)
                .build();
    }
}
```

Java の実務コードでは、このスタイルが多く登場します。

例えば、文字結合によく使用する `StringBuilder` では、`append` メソッドが `this` を返しているため、Fluent Builder を採用していると言えます。<br>
また、Java 11 以降にはなるのですが、HTTP リクエストを組み立てる際に用いる `HttpRequest.Builder` では、`HttpRequest.newBuilder().uri(...).GET().build()` のようなメソッドチェーンを用いた実装を行います。

本記事のスタイルとの違いは、**組み立て順序の保証を誰が担うか**です。Director スタイルは Director が順序を強制するのに対し、Fluent Builder は呼び出し側が自由に順序を決めます。<br>
本記事のように、`Customer` → `Order` → `Payment` と依存関係のある組み立てには Director スタイルが適しており、単純なフィールドの詰め込みが目的であれば Fluent Builder が適しています。

<a id="深堀り4"></a>

## 【深堀り④】GoF デザインパターンとの位置づけ

今回使った Builder パターンは、GoF（Gang of Four）の 23 のデザインパターンのうち「生成パターン」に分類されます。<br>
詳しくは「GoF」で検索してみてください。
