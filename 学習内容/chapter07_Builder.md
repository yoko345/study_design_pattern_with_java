# Builder（ビルダー）パターン ― 複雑なオブジェクトの組み立て手順を統一する

このような経験はありませんか？

> 複数の手順を経て完成するオブジェクトを、条件や用途に応じて作り分ける必要が生じた。生成コードを各所に直書きしているうちに重複が膨らんで、新しい種類を追加するたびに似たような変更を何箇所にも加えなければならなくなっていた。

この記事では、EC サイトの注文管理システムのテストというシナリオを通して、Builder パターンがこの問題をどのように解決するかを紹介します。

## 目次

- [【具体例】](#具体例)
    - [シナリオ](#シナリオ)
    - [既存コードの仕様](#既存コードの仕様)
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

あなたは EC サイトの開発チームにいます。注文管理システムの `OrderService` に対して単体テストを書くことになりました。

`OrderService` は注文を処理するクラスで、`Order` と `Payment` を受け取り、在庫確認・決済処理・注文確定を行います。テストを書くには、その入力となる顧客・注文・支払いのデータを用意する必要があります。

### 既存コードの仕様

注文に関わるエンティティは次の 4 つです。

**`Customer.java`**

```java
package example;

// 顧客情報
public class Customer {
    private final String name;
    private final String email;
    private final String phone;

    public Customer(String name, String email, String phone) {
        this.name = name;
        this.email = email;
        this.phone = phone;
    }
    // getters 省略
}
```

**`OrderItem.java`**

```java
package example;

// 注文明細（1商品分）
public class OrderItem {
    private final String productName;
    private final int unitPrice;
    private final int quantity;

    public OrderItem(String productName, int unitPrice, int quantity) {
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }
    // getters 省略
}
```

**`Order.java`**

```java
package example;

// 注文（Customer に依存）
public class Order {
    private final Customer customer;
    private final List<OrderItem> items;
    private final LocalDate orderDate;

    public Order(Customer customer, List<OrderItem> items, LocalDate orderDate) {
        this.customer = customer;
        this.items = items;
        this.orderDate = orderDate;
    }
    // getters 省略
}
```

**`Payment.java`**

```java
package example;

// 支払い（Order に依存）
public class Payment {
    private final Order order;
    private final String method;
    private final int amount;

    public Payment(Order order, String method, int amount) {
        this.order = order;
        this.method = method;
        this.amount = amount;
    }
    // getters 省略
}
```

ここで着目してほしいのが、エンティティ間の **依存関係** です。

- `Order` は `Customer` がなければ作れない
- `Payment` は `Order` がなければ作れない

つまり、テストデータを作成するには **Customer → Order → Payment** の順で組み立てる必要があります。

現在のテストコードはこのように書かれています。

**`OrderServiceTest.java`**

```java
package example;

class OrderServiceTest {
    @Test
    void 正常な注文が処理される() {
        Customer customer = new Customer(
            "田中太郎", "tanaka@example.com", "090-1234-5678");
        Order order = new Order(
            customer,
            List.of(new OrderItem("ノートPC", 100_000, 1)),
            LocalDate.of(2024, 1, 15));
        Payment payment = new Payment(order, "credit_card", 100_000);

        boolean result = orderService.process(order, payment);

        assertTrue(result);
    }
}
```

3 つのエンティティを毎回手作業で組み立てています。正常系テストが数本なら問題ありませんが、テストケースが増えてくると事情が変わります。

---

## 追加要件

チームから次の要求が来ました。

> 「テストカバレッジを上げるため、正常系に加えて **境界値テスト** と **異常系テスト** も網羅してほしい。」

- **正常系**：有効な顧客情報と通常の注文内容
- **境界値系**：名前が最大文字数・注文数量が上限値・金額が最小値 など
- **異常系**：不正なメールアドレス・空の注文リスト・負の金額 など

テストケースがこの 3 種類に増えると、Customer → Order → Payment を組み立てるコードがテストファイル全体に散らばることになります。

---

## 好ましくない実装

※ここで一旦読むのを止めて、ご自身でコーディングを行なってみてください。その後で、続きを読んでください。

共通化しようとして、種別を文字列で受け取るファクトリメソッドを作ったとします。

**`TestDataFactory.java`**

```java
package example;

public class TestDataFactory {
    public static TestData create(String type) {
        if (type.equals("normal")) {
            Customer customer = new Customer(
                "田中太郎", "tanaka@example.com", "090-1234-5678");
            Order order = new Order(
                customer,
                List.of(new OrderItem("ノートPC", 100_000, 1)),
                LocalDate.of(2024, 1, 15));
            Payment payment = new Payment(order, "credit_card", 100_000);
            return new TestData(customer, order, payment);

        } else if (type.equals("edge")) {
            Customer customer = new Customer(
                "あ".repeat(50), "a@b.c", "000-0000-0000");
            Order order = new Order(
                customer,
                List.of(new OrderItem("消耗品", 1, 99)),
                LocalDate.now());
            Payment payment = new Payment(order, "cash", 99);
            return new TestData(customer, order, payment);

        } else if (type.equals("error")) {
            Customer customer = new Customer("", "not-an-email", "");
            Order order = new Order(customer, List.of(), LocalDate.now());
            Payment payment = new Payment(order, "unknown", -1);
            return new TestData(customer, order, payment);
        }

        throw new IllegalArgumentException("Unknown type: " + type);
    }
}
```

呼び出し側はシンプルになりました。

```java
TestData data = TestDataFactory.create("normal");
```

しかしこのコードには問題があります。**新しい種別が必要になるたびに `TestDataFactory` を修正しなければなりません。**

たとえばパフォーマンステスト用に大量の注文明細を持つデータが必要になった場合、`else if (type.equals("stress"))` を追加することになります。種別が増えるほどこのメソッドは肥大化し、テストケースごとの生成ロジックが一箇所に混在してしまいます。

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
    // getters 省略
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

|                        | 好ましくない実装                 | Builder パターン                |
| ---------------------- | -------------------------------- | ------------------------------- |
| 種別の追加             | `TestDataFactory` を修正する     | 新しい Builder クラスを追加する |
| 組み立て順序の保証     | 呼び出し側が順序を守る必要がある | Director が順序を強制する       |
| 種別ごとの生成ロジック | 1 つのメソッドに混在する         | Builder クラスごとに分離される  |

Builder パターンが特に力を発揮するのは、**複数のオブジェクトが依存関係を持ちながら段階的に組み立てる必要がある場合**です。今回のようなテストデータ生成のほか、複雑な設定オブジェクトの生成や環境ごとに異なる構成物を作る場面でも広く使われます。

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

好ましくない実装では `TestDataFactory` に `else if` を追加しなければなりませんでした。一方 Builder パターンなら、新しいクラスを追加するだけです。

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
