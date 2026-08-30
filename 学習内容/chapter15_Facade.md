# Facade（ファサード）パターン ― 複数のサブシステムをまとめて呼び出す窓口を作る

次のような経験をしたことはありませんか？

> 1 つの処理を行うために複数の処理を順番に呼び出すコードを書いていたら、「途中で失敗したら、それまでの処理を取り消す」という条件分岐がどんどん増えていき、気づけば 1 つのメソッドの中に、成功パターンと失敗パターンの組み合わせがすべて書き連ねられていた。

この記事では、通販サイトの注文処理に「失敗時に途中までの処理を取り消す」機能を追加するシナリオを通して、Facade パターンがこの問題をどのように解決するかを紹介します。

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
> 現在、注文を受け付けると「在庫を確保し、決済処理を行い、配送手配をする」という一連の処理を、専用のクラスが順に呼び出す仕組みが実装されています。各処理は失敗する可能性があり、失敗した場合はその時点で処理を中止する、という単純な作りになっています。<br>
> ある日、カスタマーサポート部門から「注文の途中で失敗した場合、それまでに確保していた在庫や決済していた金額を、きちんと元に戻してほしい」という要望が来ました。あなたは、注文処理が途中で失敗した際に、それまでの処理を取り消す仕組みを追加することになりました。

※実際の在庫確保・決済処理・配送手配では、それぞれ在庫管理システムや決済代行サービス、配送業者のシステムとの連携が必要ですが、本記事では Facade パターンの解説に集中するため、コンソールへの文字列出力のみとします。

### 既存コードの仕様

- `InventoryService`（既存クラス）

商品の在庫を確保・解放するクラスです。

| メソッド   | 引数                             | 戻り値の型 | 説明                                                       |
| ---------- | -------------------------------- | ---------- | ---------------------------------------------------------- |
| `reserve`  | `String productName, int quantity` | `boolean`  | 指定した商品の在庫を確保し、結果をコンソールに出力する      |
| `release`  | `String productName, int quantity` | `void`     | 確保していた在庫を解放し、結果をコンソールに出力する        |

**`InventoryService.java`**

```java
package example;

public class InventoryService {
    public boolean reserve(String productName, int quantity) {
        if ("完売商品".equals(productName)) {
            System.out.println(productName + "の在庫が不足しているため、確保できませんでした。");
            return false;
        }
        System.out.println(productName + "の在庫を" + quantity + "個確保しました。");
        return true;
    }

    public void release(String productName, int quantity) {
        System.out.println(productName + "の確保していた在庫" + quantity + "個を解放しました。");
    }
}
```

<br>

- `PaymentService`（既存クラス）

決済処理を行い、必要に応じて取り消すクラスです。

| メソッド | 引数                          | 戻り値の型 | 説明                                                 |
| -------- | ----------------------------- | ---------- | ---------------------------------------------------- |
| `charge` | `String customerName, int amount` | `boolean`  | 指定した金額の決済処理を行い、結果をコンソールに出力する |
| `refund` | `String customerName, int amount` | `void`     | 決済を取り消し、結果をコンソールに出力する            |

**`PaymentService.java`**

```java
package example;

public class PaymentService {
    private static final int CREDIT_LIMIT = 1000000;

    public boolean charge(String customerName, int amount) {
        if (amount > CREDIT_LIMIT) {
            System.out.println(customerName + "様の決済に失敗しました（利用限度額を超えています）。");
            return false;
        }
        System.out.println(customerName + "様に" + amount + "円の決済処理を行いました。");
        return true;
    }

    public void refund(String customerName, int amount) {
        System.out.println(customerName + "様への" + amount + "円の決済を取り消しました。");
    }
}
```

※ `CREDIT_LIMIT` は 1 回の決済で許容する利用限度額を表す定数で、本記事では 100 万円としています。

<br>

- `ShippingService`（既存クラス）

配送手配を行うクラスです。

| メソッド  | 引数                                   | 戻り値の型 | 説明                                                 |
| --------- | -------------------------------------- | ---------- | ---------------------------------------------------- |
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

| メソッド        | 引数                                                              | 戻り値の型 | 説明                                                       |
| --------------- | ------------------------------------------------------------------ | ---------- | ---------------------------------------------------------- |
| `processOrder`  | `String customerName, String productName, int quantity, int amount` | `void`     | 在庫確保・決済処理・配送手配を順に呼び出し、注文を処理する |

**`OrderProcessor.java`**

```java
package example;

public class OrderProcessor {
    private InventoryService inventoryService = new InventoryService();
    private PaymentService paymentService = new PaymentService();
    private ShippingService shippingService = new ShippingService();

    public void processOrder(String customerName, String productName, int quantity, int amount) {
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
    }
}
```

**実行結果**

```
ノートパソコンの在庫を1個確保しました。
鈴木様に120000円の決済処理を行いました。
鈴木様宛にノートパソコンの配送手配を行いました。
注文が完了しました。
```

※ここで一旦読むのを止めて、ご自身でコーディングを行なってみてください。その後で、続きを読んでください。

## 好ましくない実装

では、シナリオに従い追加実装をしていきましょう。

真っ先に思いつくのは、`OrderProcessor` クラスの `processOrder` メソッドに、失敗時のロールバック処理をそのまま追記していく、という実装ではないでしょうか？

**`OrderProcessor.java`**

```java
package example;

public class OrderProcessor {
    private InventoryService inventoryService = new InventoryService();
    private PaymentService paymentService = new PaymentService();
    private ShippingService shippingService = new ShippingService();

    public void processOrder(String customerName, String productName, int quantity, int amount) {
        if (!inventoryService.reserve(productName, quantity)) {
            System.out.println("在庫確保に失敗したため、注文を中止しました。");
            return;
        }
        if (!paymentService.charge(customerName, amount)) {
            inventoryService.release(productName, quantity);
            System.out.println("決済に失敗したため、注文を中止しました。");
            return;
        }
        if (!shippingService.arrange(customerName, productName)) {
            paymentService.refund(customerName, amount);
            inventoryService.release(productName, quantity);
            System.out.println("配送手配に失敗したため、注文を中止しました。");
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
        orderProcessor.processOrder("佐藤", "デジタルカメラ", 1, 2000000);
        orderProcessor.processOrder("高橋", "特大家具", 1, 45000);
        orderProcessor.processOrder("田中", "完売商品", 1, 5000);
    }
}
```

**実行結果**

```
ノートパソコンの在庫を1個確保しました。
鈴木様に120000円の決済処理を行いました。
鈴木様宛にノートパソコンの配送手配を行いました。
注文が完了しました。
デジタルカメラの在庫を1個確保しました。
佐藤様の決済に失敗しました（利用限度額を超えています）。
デジタルカメラの確保していた在庫1個を解放しました。
決済に失敗したため、注文を中止しました。
特大家具の在庫を1個確保しました。
高橋様に45000円の決済処理を行いました。
高橋様宛の特大家具は配送手配できませんでした（大型商品のため）。
高橋様への45000円の決済を取り消しました。
特大家具の確保していた在庫1個を解放しました。
配送手配に失敗したため、注文を中止しました。
完売商品の在庫が不足しているため、確保できませんでした。
在庫確保に失敗したため、注文を中止しました。
```

コンパイルエラーがなく結果が出力されていることから、一見すると実装・動作確認ともに問題ないように見えます。

しかし、この実装には以下の問題点があります。

- `processOrder` メソッドの中に、正常系の 3 手順に加えて、失敗パターンごとのロールバック処理（`release` メソッド・`refund` メソッドの呼び出しとその順序）がすべて書き込まれており、手順が増えるにつれてメソッドが急速に複雑化していく。
- ロールバックの順序（配送手配に失敗した場合は決済の取り消し→在庫の解放、決済に失敗した場合は在庫の解放のみ、というように手順を逆順に戻す必要がある）という知識が `OrderProcessor` クラス 1 つに集中しており、新しい手順（例えば「配送手配の後にポイントを付与する」）を追加するたびに、この長いメソッドをさらに読み解いて正しい位置に分岐を挿入しなければならない。

## 正しい実装

では、好ましくない実装で挙げた問題点を解決するにはどうすればよいのでしょうか？

これらの問題を解決するのが **Facade パターン**です。<br>
在庫確保・決済処理・配送手配、及びそれらの失敗時のロールバックまでを含めた一連の手順を「窓口」となる 1 つのクラスにまとめることで、呼び出し側は手順の複雑さを意識せず、窓口となるクラスのメソッドを呼び出すだけで済むようになります。

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
            inventoryService.release(productName, quantity);
            System.out.println("決済に失敗したため、注文を中止しました。");
            return;
        }
        if (!shippingService.arrange(customerName, productName)) {
            paymentService.refund(customerName, amount);
            inventoryService.release(productName, quantity);
            System.out.println("配送手配に失敗したため、注文を中止しました。");
            return;
        }
        System.out.println("注文が完了しました。");
    }
}
```

`OrderFacade` は新たに追加したクラスで、既存の `InventoryService`・`PaymentService`・`ShippingService` クラスをフィールドとして保持し、`placeOrder` メソッド 1 つに正常系の手順とロールバックの手順をまとめています。手順自体は好ましくない実装の `OrderProcessor` クラスにあったものと同じですが、この手順を知っているクラスが `OrderFacade` という 1 箇所に集約された点が異なります。

続いて、`OrderProcessor` クラスを見ていきましょう。

**`OrderProcessor.java`**

```java
package example;

public class OrderProcessor {
    private OrderFacade orderFacade = new OrderFacade();

    public void processOrder(String customerName, String productName, int quantity, int amount) {
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
        orderProcessor.processOrder("佐藤", "デジタルカメラ", 1, 2000000);
        orderProcessor.processOrder("高橋", "特大家具", 1, 45000);
        orderProcessor.processOrder("田中", "完売商品", 1, 5000);
    }
}
```

**実行結果**

```
ノートパソコンの在庫を1個確保しました。
鈴木様に120000円の決済処理を行いました。
鈴木様宛にノートパソコンの配送手配を行いました。
注文が完了しました。
デジタルカメラの在庫を1個確保しました。
佐藤様の決済に失敗しました（利用限度額を超えています）。
デジタルカメラの確保していた在庫1個を解放しました。
決済に失敗したため、注文を中止しました。
特大家具の在庫を1個確保しました。
高橋様に45000円の決済処理を行いました。
高橋様宛の特大家具は配送手配できませんでした（大型商品のため）。
高橋様への45000円の決済を取り消しました。
特大家具の確保していた在庫1個を解放しました。
配送手配に失敗したため、注文を中止しました。
完売商品の在庫が不足しているため、確保できませんでした。
在庫確保に失敗したため、注文を中止しました。
```

`OrderProcessor` クラスを振り返ると、好ましくない実装では正常系とロールバックの手順をすべて自身の `processOrder` メソッドに直接書いていましたが、正しい実装では `OrderFacade` クラスのインスタンスを 1 つ持ち、`placeOrder` メソッドを呼び出すだけになっています。

実行結果は、好ましくない実装とまったく同じになっています。

以上のような実装を行うと、以下のメリットがあります。

- 注文処理の手順が変わる場合、`OrderFacade` クラスの `placeOrder` メソッド 1 箇所を修正するだけで済み、`OrderProcessor` クラスは変更する必要がない。
- `OrderProcessor` クラスは在庫確保・決済処理・配送手配それぞれの成否やロールバックの順序を意識する必要がなくなり、手順が複雑になっても影響を受けにくくなる。

## まとめ

正しい実装を振り返ると、`OrderFacade` クラスは在庫確保・決済処理・配送手配という正常系の手順と、失敗時のロールバックという複雑な手順をまとめて 1 つのメソッドに集約し、`OrderProcessor` クラスはその窓口を呼び出すだけの薄い実装になりました。<br>
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

好ましくない実装を振り返ると、`OrderProcessor` クラスは、`InventoryService`・`PaymentService`・`ShippingService` という 3 つのサブシステムクラスの存在と、その呼び出し順序やロールバックの手順を直接知っている必要がありました。正しい実装では、これらのクラスは `OrderFacade` クラス 1 つだけを知っていればよくなっています。

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

`read(File input)` メソッドの中身を振り返ると、渡された `File` から `ImageInputStream`（画像データを読み込むための入力ストリーム）を生成し、実際の読み込み処理をもう一方の `read(ImageInputStream stream)` メソッドに委ねています。`read(ImageInputStream stream)` メソッドでは、`getImageReaders` メソッドによって、ストリームの内容を読み取れる `ImageReader`（PNG や JPEG など画像形式ごとのデコード処理を行うクラス）を登録済みの候補の中から探し出しています。見つかった `ImageReader` に読み込みパラメータや入力ストリームを設定した上で実際のデコード処理（`reader.read`）を行い、最後に `reader.dispose()` でリソースを解放しています。

利用する側は `ImageIO.read(file)` という 1 行を呼び出すだけで、画像形式ごとの `ImageReader` の候補探し・設定・デコード・リソース解放という一連の手順を意識せずに済みます。本記事の `OrderFacade` クラスが在庫確保・決済処理・配送手配とそのロールバックという複雑な手順をまとめたのと同様に、`ImageIO` クラスも `ImageInputStream` クラスや `ImageReader` クラスなどからなるサブシステムをまとめる窓口として機能しています。

<a id="深堀り4"></a>

## 【深堀り④】GoF デザインパターンとの位置づけ

今回使った Facade パターンは、GoF（Gang of Four）の 23 のデザインパターンのうち「構造パターン」に分類されます。<br>
詳しくは「GoF」で検索してみてください。
