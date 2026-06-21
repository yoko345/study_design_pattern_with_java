# Builder（ビルダー）パターン ― 複雑なオブジェクトの組み立て手順を統一する

次のような経験をしたことはありませんか？

> 複数の手順を経て完成するオブジェクトを、条件や用途に応じて作り分ける必要が生じた際、生成コードを各所に直書きしているうちに重複が膨らんでしまった。また、新しい種類を追加するたびに似たような変更を何箇所にも加えなければならなくなった。

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
> 現在は正常系テストが 1 件だけ用意されており、PM から「テストカバレッジを上げるため、境界値テストと異常系テストも追加してほしい」という要望が来ました。

### 既存コードの仕様

- `OrderService`（既存クラス）

注文を処理するクラスです。

| メソッド  | 戻り値の型 | 説明                       |
| --------- | ---------- | -------------------------- |
| `process` | `boolean`  | 注文処理を行い、成否を返す |

※テストを書くには、この `process` メソッドの引数となる `Order` と `Payment` のデータを用意する必要があります。

**`OrderService.java`**

```java
package example;

public class OrderService {
    public boolean process(Order order, Payment payment) {
        // メールアドレスは「@」必須
        if (!order.getCustomer().getEmail().contains("@")) return false;

        // 「注文明細」は 1 件以上
        if (order.getItems().isEmpty()) return false;

        // 「支払金額」は 0 以上
        if (payment.getAmount() < 0) return false;

        // 実務では、以下のような処理がここに入る（本記事の主題とは関係ないため省略）
        // ・在庫確認
        // ・決済処理
        // ・注文確定

        return true;
    }
}
```

<br>

次からは、注文に関わるエンティティクラスの仕様です。<br>
注文情報を保持する `Order` は顧客情報（`Customer`）や注文明細（`OrderItem`）を、支払い情報を保持する `Payment` は `Order` を、フィールドとして保持するクラスであることに留意してください。

> 一般的に、エンティティはリレーショナルデータベースの表を表現し、各エンティティ・インスタンスはその表の行に相当します。<br>
> 出典：Oracle TopLinkの理解「[エンティティの理解](https://docs.oracle.com/cd/F32751_01/toplink/14.1.1.0/concepts/understanding-entities.html)」

※実務では、これらのエンティティクラスは `entity` パッケージなど専用のディレクトリに切り出すのが一般的です。しかし、本記事ではパッケージ構成を主題としないため `example` パッケージ直下にまとめています。

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

<br>

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

<br>

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

<br>

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

<br>

最後に、テストクラスの仕様です。

- `OrderServiceTest`（既存クラス）

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
        Order order       = new Order(customer, List.of(new OrderItem("ノートPC", 100_000, 1)), LocalDate.of(2024, 1, 15));
        Payment payment   = new Payment(order, "クレジットカード", 100_000);

        assertTrue(orderService.process(order, payment));
    }
}
```

### テストケースの方針

テストは一般的に「正常系」「境界値系」「異常系」の確認を行います。<br>
今回のシナリオでは下記をテストすることにします。（他にも様々な観点がありますが、本記事の主題から外れるため扱いません。）

- **正常系**：有効な顧客情報と通常の注文内容
- **境界値系**：顧客名が最大文字数・注文数量が上限値
- **異常系**：不正なメールアドレス・空の注文リスト・負の金額

※境界値として使用する具体的な値は、次のシステム仕様に基づくこととします。

> - 顧客名の上限： 50 文字（顧客テーブルの `name` カラムが `VARCHAR(50)` 制約を持つと仮定しています。）
> - 注文数量の上限： 99 個（「同一商品を最大 99 個までしか 1 回の注文で頼めない」という業務ルールを仮定しています。）

<br>

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
        Order order       = new Order(customer, List.of(new OrderItem("ノートPC", 100_000, 1)), LocalDate.of(2024, 1, 15));
        Payment payment   = new Payment(order, "クレジットカード", 100_000);
        assertTrue(orderService.process(order, payment));
    }

    // 以降が追加部分
    /**
     * 境界値系 - 名前が最大文字数
     */
    @Test
    void 名前が最大文字数の顧客でも処理される() {
        Customer customer = new Customer("あ".repeat(50), "tanaka@example.com", "090-1234-5678"); // DB における制約
        Order order       = new Order(customer, List.of(new OrderItem("ノートPC", 100_000, 1)), LocalDate.of(2024, 1, 15));
        Payment payment   = new Payment(order, "クレジットカード", 100_000);
        assertTrue(orderService.process(order, payment));
    }

    /**
     * 境界値系 - 注文数量が上限値
     */
    @Test
    void 注文数量が上限値でも処理される() {
        Customer customer = new Customer("田中太郎", "tanaka@example.com", "090-1234-5678");
        Order order       = new Order(customer, List.of(new OrderItem("消耗品", 1, 99)), LocalDate.of(2024, 1, 15)); // 業務ルールより、注文数量の上限を指定
        Payment payment   = new Payment(order, "現金", 99);
        assertTrue(orderService.process(order, payment));
    }

    /**
     * 異常系 - 不正なメールアドレス
     */
    @Test
    void 不正なメールアドレスは処理に失敗する() {
        Customer customer = new Customer("田中太郎", "not-an-email", "090-1234-5678"); // メールアドレスに「@」がない
        Order order       = new Order(customer, List.of(new OrderItem("ノートPC", 100_000, 1)), LocalDate.of(2024, 1, 15));
        Payment payment   = new Payment(order, "クレジットカード", 100_000);
        assertFalse(orderService.process(order, payment));
    }

    /**
     * 異常系 - 空の注文リスト
     */
    @Test
    void 空の注文リストは処理に失敗する() {
        Customer customer = new Customer("田中太郎", "tanaka@example.com", "090-1234-5678");
        Order order       = new Order(customer, List.of(), LocalDate.of(2024, 1, 15)); // 「注文明細」が空
        Payment payment   = new Payment(order, "クレジットカード", 0); // 「注文明細」が空のため、「支払金額」が 0
        assertFalse(orderService.process(order, payment));
    }

    /**
     * 異常系 - 負の金額
     */
    @Test
    void 負の金額は処理に失敗する() {
        Customer customer = new Customer("田中太郎", "tanaka@example.com", "090-1234-5678");
        Order order       = new Order(customer, List.of(new OrderItem("ノートPC", 100_000, 1)), LocalDate.of(2024, 1, 15));
        Payment payment   = new Payment(order, "クレジットカード", -1); // 「支払金額」が負の値
        assertFalse(orderService.process(order, payment));
    }
}
```

テストを行うと全て成功します。<br>
しかし、各テストメソッドには `Customer`・`Order`・`Payment` を生成する 3 行が繰り返されています。<br>
現時点で 6 メソッド × 3 行 = 18 行の似たコードがあり、他のテスト観点を盛り込むたびにこの似たコードが増えてしまいます。

では、似たコードが増えないように共通化できればよいのだから「各テストの前に自動実行されるセットアップメソッドの `@BeforeEach` を用いればよいのではないか」と思うかもしれません。

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
        order    = new Order(customer, List.of(new OrderItem("ノートPC", 100_000, 1)), LocalDate.of(2024, 1, 15)); // customer に依存
        payment  = new Payment(order, "クレジットカード", 100_000); // order に依存
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
        customer = new Customer("あ".repeat(50), "tanaka@example.com", "090-1234-5678"); // 設定する値を変更する必要がある
        order    = new Order(customer, List.of(new OrderItem("ノートPC", 100_000, 1)), LocalDate.of(2024, 1, 15)); // customer に変更が入ったため、作り直しが必要
        payment  = new Payment(order, "クレジットカード", 100_000); // order に変更が入ったため、作り直しが必要
        assertTrue(orderService.process(order, payment));
    }

    /**
     * 境界値系 - 注文数量が上限値
     */
    @Test
    void 注文数量が上限値でも処理される() {
        order    = new Order(customer, List.of(new OrderItem("消耗品", 1, 99)), LocalDate.of(2024, 1, 15)); // 設定する値を変更する必要がある
        payment  = new Payment(order, "現金", 99); // order に変更が入ったため、作り直しが必要
        assertTrue(orderService.process(order, payment));
    }

    /**
     * 異常系 - 不正なメールアドレス
     */
    @Test
    void 不正なメールアドレスは処理に失敗する() {
        customer = new Customer("田中太郎", "not-an-email", "090-1234-5678"); // 設定する値を変更する必要がある
        order    = new Order(customer, List.of(new OrderItem("ノートPC", 100_000, 1)), LocalDate.of(2024, 1, 15)); // customer に変更が入ったため、作り直しが必要
        payment  = new Payment(order, "クレジットカード", 100_000); // order に変更が入ったため、作り直しが必要
        assertFalse(orderService.process(order, payment));
    }

    /**
     * 異常系 - 空の注文リスト
     */
    @Test
    void 空の注文リストは処理に失敗する() {
        order    = new Order(customer, List.of(), LocalDate.of(2024, 1, 15)); // 設定する値を変更する必要がある
        payment  = new Payment(order, "クレジットカード", 0); // order に変更が入ったため、作り直しが必要
        assertFalse(orderService.process(order, payment));
    }

    /**
     * 異常系 - 負の金額
     */
    @Test
    void 負の金額は処理に失敗する() {
        payment = new Payment(order, "クレジットカード", -1); // 設定する値を変更する必要がある
        assertFalse(orderService.process(order, payment));
    }
}
```

上記から、依存元を変更すると依存関係にあるものすべてを作り直す必要があります。（例えば、`Customer` を差し替えると、`Customer` に依存している `Order` と `Order` に依存している `Payment` も作り直さないといけません。）<br>
つまり、**依存関係がある限り、@BeforeEach により 1 箇所だけ変更を加えれば良いわけではなく、他の修正も行う必要があるのです。**<br>
結局、`Customer` を差し替えたいテストでは毎回 3 行を書き直すことになります。

以上からこの実装には、以下の問題点があります。

- 新しいテスト観点を追加するたびに、`Customer`・`Order`・`Payment` の生成コードが各テストメソッド内に増え続ける。
- `Customer` を変更した際に `Order`・`Payment` の再生成を書き忘れても、コンパイルエラーも例外も発生しないため、`setUp` メソッドで生成された古い `Order`・`Payment` を参照したままテストが実行されてしまう。
- テストで必要なパラメータの設定がテストメソッド自体に直接書き込まれているため、責務の分離ができない。

## 正しい実装

では、好ましくない実装で挙げた問題点を解決するにはどうすればよいのでしょうか？

これらの問題を解決するのが **Builder パターン**です。

まず、`TestData` クラス（新規実装）を見てください。<br>
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

次に、抽象クラス `TestDataBuilder`（新規実装）を見てください。<br>
本クラスにより、今回のテストで必要な `Customer`・`Order`・`Payment` のパラメータ設定を必須にできます。

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

※変数 `customer`・`order`・`payment` を `private` ではなく `protected` にしている理由は、`TestDataBuilder` を継承する各実装クラスが `buildCustomer`・`buildOrder`・`buildPayment` メソッドの中で、先のフィールドへ直接代入できるようにするためです。もし `private` にしてしまうと継承先からフィールドへアクセスできなくなり、フィールドごとに setter を用意する必要が生じてしまいます。

次に、`TestDataDirector` クラス（新規実装）を見てください。<br>
`construct` メソッドにより、`buildCustomer`・`buildOrder`・`buildPayment` メソッド（抽象クラス `TestDataBuilder` を継承したクラスでは実装必須）を **Customer → Order → Payment** の順で固定して呼び出せるようになります。<br>
これにより、依存関係を破壊することなく（`Order` は `Customer` に、`Payment` は `Order` に依存しているため）テストデータを設定できるようになります。

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

次に、`TestDataBuilder` を実装したそれぞれのクラス（新規実装）を見てください。<br>
正常系・境界値系・異常系それぞれの観点ごとに、個別のクラスとして分離しています。

- 正常系：`NormalCaseBuilder`
- 境界値系：`NameMaxLengthBuilder`・`MaxQuantityBuilder`
- 異常系：`InvalidEmailBuilder`・`EmptyOrderBuilder`・`NegativeAmountBuilder`

これらのクラスは抽象クラス `TestDataBuilder` を継承しているため、開発者はオーバーライドしたメソッド（`buildCustomer`・`buildOrder`・`buildPayment`）内で、テスト観点を満たすパラメータを設定するだけでよくなります。

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
        payment = new Payment(order, "クレジットカード", 100_000);
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
        payment = new Payment(order, "クレジットカード", 100_000);
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
        payment = new Payment(order, "現金", 99);
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
        payment = new Payment(order, "クレジットカード", 100_000);
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
        payment = new Payment(order, "クレジットカード", 0); // 「注文明細」が空であるため、「支払金額」が 0
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
        payment = new Payment(order, "クレジットカード", -1); // 「支払金額」が負の値
    }
}
```

最後に、テストクラス `OrderServiceTest` を見てください。<br>
`TestDataDirector` クラスの `construct` メソッドは、`buildCustomer`・`buildOrder`・`buildPayment` を `Customer` → `Order` → `Payment` の順で呼び出します。<br>
このことにより、`TestDataDirector` クラスの引数にテスト観点ごとに対応した実装クラスのインスタンスを渡すだけで、テストに最適な構築済みのテストデータを取得できるようになります。

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

- `TestDataBuilder` の各実装クラスの中に `Customer`・`Order`・`Payment` の生成コードを閉じ込めています。これにより、テストメソッド自体は `TestDataDirector` クラスの引数に渡す `new XXXBuilder()` を変更するだけで済むため、テスト観点を追加しても既存のテストメソッドは肥大化しなくなる。
- `TestDataDirector` クラスが `Customer`・`Order`・`Payment` の組み立て順序を定めています。これにより、`TestDataBuilder` の各実装クラスは「そのテスト観点にふさわしい値を設定する」ことだけに集中できる。
    - また、`TestDataBuilder` の各実装クラスは `buildCustomer`・`buildOrder`・`buildPayment` メソッドの実装が必須のため、1 つでも実装が漏れるとコンパイルエラーになるので、`Customer`・`Order`・`Payment` の生成を書き忘れたままにならない。
- テストで必要なパラメータの設定は、`TestDataBuilder` を実装したそれぞれのクラスで行うため、責務の分離ができる。

## まとめ

正しい実装では、`Customer`・`Order`・`Payment` の組み立て順序は `TestDataDirector` クラスが一手に担い、`TestDataBuilder` の各実装クラスはテスト観点に合わせた値の設定だけに集中しています。<br>
これにより、テストメソッドは `new XXXBuilder()` を切り替えるだけで、異なるテスト観点を扱えるようになります。

Builder パターンは、**複数のオブジェクトが依存関係を持ちながら段階的に組み立てる必要がある場面**で特に力を発揮します。<br>
なお、今回のようなテストデータ生成に Builder パターンを応用する手法は、**Test Data Builder** と呼ばれています。テスト系の記事や書籍でこの名前を見かけた際は、本記事の実装と同じ考え方だと思ってください。

> Test Data Builder：Growing Object-Oriented Software, Guided by Tests の著者 Nat Pryce が提唱したプラクティス

本記事の内容はここまでとなります。

以降は「もう少し深く知りたい」という方向けの補足となります。今回学んだパターンに繋がる設計原則や、実務で役立つ背景知識について触れています。

---

<a id="深堀り1"></a>

## 【深堀り①】OCP（オープン・クローズドの原則）

正しい実装を振り返ると、新しいテスト観点を追加することになった際、`TestDataBuilder` を継承した新しいクラスを追加し、テストクラスで先ほど追加したクラスのインスタンスを `TestDataDirector` クラスの引数に渡すだけで対応できます。このとき、既存の `TestDataBuilder` を継承した実装クラスには一切手を加える必要がありません。

この「既存コードを変えずに、新しいクラスを追加するだけで機能を拡張できる」という設計は、「**OCP（Open/Closed Principle：オープン・クローズドの原則）**」と呼ばれる設計原則の実践です。Builder パターンは OCP を実現するための設計手段の一つと言えます。

詳しくは「OCP」や「オープン・クローズドの原則」で検索してみてください。

---

<a id="深堀り2"></a>

## 【深堀り②】実行クラスでの型宣言 ― 抽象型 vs 具体型

正しい実装を振り返ると、`TestDataDirector` の引数には、`TestDataBuilder` の各実装クラスのインスタンスを直接渡していました。

ここで、「ギフト注文でも正常に処理されること」を確認したいという仕様が追加されたとしましょう。

この際、既存のテストメソッド「`正常な注文が処理される`」に対して、条件分岐を加えることで対応することが考えられます。<br>
このような修正を行うと、対応したテスト観点の実装クラスのインスタンスが代入された変数を `TestDataDirector` の引数に渡すことになります。<br>
その時の変数の型はどうすればよいでしょうか？

- `TestDataBuilder`（抽象型）にすべき？
- `TestDataBuilder` の各実装クラス（具体型）にすべき？

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
        payment = new Payment(order, "クレジットカード", 5_000);
    }
}
```

### 抽象型 vs 具体型

追加仕様が明らかになったので、注文種別を示す `isGiftOrder` フラグを使って、既存のテストメソッドに条件分岐を加えると次のようになります。

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

    // 以降は、本項目に関係ないため省略
}
```

抽象型 `TestDataBuilder` で `testDataBuilder` を宣言すると、`GiftOrderBuilder` クラス、`NormalCaseBuilder` クラスのどちらのインスタンスが代入されようが、条件分岐ブロック以降のコードは変更する必要がありません。

一方で、具体型 `NormalCaseBuilder` で `testDataBuilder` を宣言した次のコードを見てください。

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

    // 以降は、本項目に関係ないため省略
}
```

`NormalCaseBuilder` 型の変数に `GiftOrderBuilder` クラスのインスタンスを代入できないため、コンパイルエラーが発生してコードが動作しなくなります。

<a id="深堀り2-まとめ"></a>

### まとめ

具体型で宣言すると、別の実装クラスに切り替えるたびに変数の型変更が必要になります。<br>
そのため、抽象型で宣言することが好ましいです。その結果、**どの Builder を代入しても条件分岐ブロック以降のコードを一切修正せずに動作できます。**

### DI（依存性の注入）との関係

本項目の冒頭でも触れましたが、`TestDataDirector` は各実装クラスのインスタンスを引数で受け取っていました。<br>
このような、あるクラスが必要とする依存オブジェクト（`TestDataBuilder` を継承した実装クラス）を、内部で生成するのではなく外から渡す設計を **DI（Dependency Injection / 依存性の注入）** と呼びます。

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

この場合、別の `TestDataBuilder` の実装クラスに切り替えるには `TestDataDirector` 自体を修正しなければなりません。これは OCP に違反します。<br>
正しい実装のように、外から渡す設計（DI）にしておくことで、`TestDataDirector` は変更せずに `TestDataBuilder` の実装クラスを差し替えられます。<br>
なお、今回は `TestDataBuilder` の実装クラスのインスタンスをコンストラクタを通じて渡しているため、特に **コンストラクタインジェクション** と呼ばれます。

<a id="深堀り3"></a>

## 【深堀り③】Fluent Builder（メソッドチェーン型）

本記事では GoF 原典の Director クラスを介したスタイルを扱いましたが、実務では **Fluent Builder**（メソッドチェーン型）と呼ばれる別のスタイルもよく見かけます。

Fluent Builder は、各セッターが `this` を返すことでメソッドチェーンを実現し、最後の `build()` で完成したオブジェクトを受け取る形式です。<br>
Director クラスが不要となり、呼び出し側でメソッドを自由な順序で繋げられるのが特徴です。

では、氏名（`name`）と年齢（`age`）を持つ `Person` を組み立てる、シンプルな Fluent Builder の実装例を見ていきましょう。

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

Java の実務コードでは、このスタイルが多く登場します。<br>
例えば、文字結合によく使用する `StringBuilder` では、`append` メソッドが `this` を返しているため、Fluent Builder を採用していると言えます。また、Java 11 以降で HTTP リクエストを組み立てる際に用いる `HttpRequest.Builder` では、`HttpRequest.newBuilder().uri(...).GET().build()` のようなメソッドチェーンを用いた実装を行います。

本記事のスタイルとの違いは、**組み立て順序の保証を誰が担うか**です。Director スタイルは Director が順序を強制するのに対し、Fluent Builder は呼び出し側が自由に順序を決めます。<br>
本記事のように、`Customer` → `Order` → `Payment` と依存関係のある組み立てには Director スタイルが適しており、単純なフィールドの詰め込みが目的であれば Fluent Builder が適しています。

<a id="深堀り4"></a>

## 【深堀り④】GoF デザインパターンとの位置づけ

今回使った Builder パターンは、GoF（Gang of Four）の 23 のデザインパターンのうち「生成パターン」に分類されます。<br>
詳しくは「GoF」で検索してみてください。
