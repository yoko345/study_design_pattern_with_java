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

次に、抽象クラス `TestDataBuilder` を実装したクラスを見てください。<br>
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

- `Customer`・`Order`・`Payment` の生成コードは抽象クラス `TestDataBuilder` を実装したクラスの中に閉じ込められます。これにより、テストメソッド自体は `TestDataDirector` クラスの引数に渡す `new XXXBuilder()` を変更するだけで済むため、観点を追加しても既存のテストメソッドは肥大化しなくなります。
- `Customer`・`Order`・`Payment` の組み立て順序は `TestDataDirector` クラスが定めています。これにより、依存関係を考慮してパラメータを構築するため、`@BeforeEach` のように「`Customer` を上書きしたら残りも手動で作り直す」という連鎖が起きません。
    - また、各観点に対応した抽象クラス `TestDataBuilder` を実装したクラスは「そのシナリオにふさわしい値を設定する」ことだけに集中できます。

## まとめ

正しい実装では、`Customer`・`Order`・`Payment` の組み立て順序は `TestDataDirector` クラスが一手に担い、抽象クラス `TestDataBuilder` を実装したクラスはテスト観点に合わせた値の設定だけに集中しています。<br>
これにより、テストメソッドは `new XXXBuilder()` を切り替えるだけで、異なる観点のテストシナリオを扱えるようになります。

Builder パターンは、**複数のオブジェクトが依存関係を持ちながら段階的に組み立てる必要がある場面**で特に力を発揮します。<br>
なお、今回のようなテストデータ生成に Builder パターンを応用する手法は、**Test Data Builder** と呼ばれています。テスト系の記事や書籍でこの名前を見かけた際は、本記事の実装と同じ考え方だと思ってください。

> Test Data Builder：`Growing Object-Oriented Software, Guided by Tests` の著者 Nat Pryce が提唱したプラクティス

本記事の内容はここまでとなります。

以降は「もう少し深く知りたい」という方向けの補足となります。今回学んだパターンに繋がる設計原則や、実務で役立つ背景知識について触れています。

---

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
