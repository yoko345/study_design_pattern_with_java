# Abstract Factory（アブストラクトファクトリー）パターン ― 関連オブジェクトの組み合わせを丸ごと切り替える

次のような経験をしたことはありませんか？

> 複数の関連するオブジェクトを、状況に応じて一括で切り替えて使用していた。<br>
> しかし、ある時の作業中にそれぞれを別々の場所で生成をしてしまった影響で、一部だけ切り替えを忘れてしまい、一部の組み合わせが古い仕様のまま残ってしまった。

この記事では、EC サイトの注文出荷処理で、配送会社ごとに異なる書類一式（配送ラベルなど）を丸ごと切り替えるシナリオを通して、Abstract Factory パターンがこの問題をどのように解決するかを紹介します。

## 目次

- [【具体例】](#具体例)
    - [シナリオ](#シナリオ)
    - [既存コードの仕様](#既存コードの仕様)
- [好ましくない実装](#好ましくない実装)
- [正しい実装](#正しい実装)
- [まとめ](#まとめ)
- [【深堀り①】Factory Method パターンとの違い](#深堀り1)
- [【深堀り②】拡張の方向性によるトレードオフ](#深堀り2)
- [【深堀り③】OCP（オープン・クローズドの原則）](#深堀り3)
- [【深堀り④】GoF デザインパターンとの位置づけ](#深堀り4)

---

## 【具体例】

### シナリオ

> あなたは EC サイトの開発チームに所属しています。<br>
> 現在、商品の配送は外部の配送会社「ゴリラ運輸」のみに対応しており、出荷時にはゴリラ運輸所定のフォーマットで「配送ラベル」「納品書」「受領書」の 3 点を作成しています。<br>
> ある日、物流コスト削減のため、もう一社「ラクダ運輸」とも提携することになりました。<br>
> あなたは、ラクダ運輸で出荷する場合も、同じ 3 点の書類をラクダ運輸所定のフォーマットで作成できるようにする実装を担当します。

※実際の配送会社との連携では、各社が提供する API 等を呼び出すのが一般的ですが、本記事では Abstract Factory パターンの解説に集中するため、コンソールへの文字列出力のみとします。

### 既存コードの仕様

※実務では、次の `GoriraShippingLabel` のようなエンティティクラスは `entity` パッケージなど専用のディレクトリに切り出すのが一般的です。しかし、本記事ではパッケージ構成を主題としないため `example` パッケージ直下にまとめています。

- `GoriraShippingLabel`（既存クラス）

荷物に貼付する配送ラベルを表すクラスです。<br>
受取人の情報とお問い合わせ番号（トラッキングナンバー）を保持し、ラベルの印字内容を生成します。

| フィールド       | 型       | 説明             |
| ---------------- | -------- | ---------------- |
| `recipientName`  | `String` | 受取人名         |
| `address`        | `String` | 配送先住所       |
| `trackingNumber` | `String` | お問い合わせ番号 |

| メソッド | 戻り値の型 | 説明                       |
| -------- | ---------- | -------------------------- |
| `print`  | `String`   | ラベルの印字内容を生成する |

**`GoriraShippingLabel.java`**

```java
package example;

public class GoriraShippingLabel {
    private String recipientName;
    private String address;
    private String trackingNumber;

    public GoriraShippingLabel(String recipientName, String address, String trackingNumber) {
        this.recipientName = recipientName;
        this.address = address;
        this.trackingNumber = trackingNumber;
    }

    public String print() {
        return "[ゴリラ運輸 配送ラベル] お問い合わせ番号：" + trackingNumber + " / 宛先：" + recipientName + " " + address;
    }
}
```

<br>

- `GoriraDeliveryNote`（既存クラス）

荷物に同梱する納品書を表すクラスです。<br>
注文番号と商品の一覧を保持し、納品書の印字内容を生成します。

| フィールド | 型             | 説明         |
| ---------- | -------------- | ------------ |
| `orderId`  | `String`       | 注文番号     |
| `items`    | `List<String>` | 商品名の一覧 |

| メソッド | 戻り値の型 | 説明                       |
| -------- | ---------- | -------------------------- |
| `print`  | `String`   | 納品書の印字内容を生成する |

**`GoriraDeliveryNote.java`**

```java
package example;

public class GoriraDeliveryNote {
    private String orderId;
    private List<String> items;

    public GoriraDeliveryNote(String orderId, List<String> items) {
        this.orderId = orderId;
        this.items = items;
    }

    public String print() {
        return "[ゴリラ運輸 納品書] 注文番号：" + orderId + " / 商品：" + items;
    }
}
```

<br>

- `GoriraReceiptForm`（既存クラス）

荷物の受け取り確認に使用される受領書を表すクラスです。<br>
注文番号と受取人名を保持し、受領書の印字内容を生成します。

| フィールド      | 型       | 説明     |
| --------------- | -------- | -------- |
| `orderId`       | `String` | 注文番号 |
| `recipientName` | `String` | 受取人名 |

| メソッド | 戻り値の型 | 説明                       |
| -------- | ---------- | -------------------------- |
| `print`  | `String`   | 受領書の印字内容を生成する |

**`GoriraReceiptForm.java`**

```java
package example;

public class GoriraReceiptForm {
    private String orderId;
    private String recipientName;

    public GoriraReceiptForm(String orderId, String recipientName) {
        this.orderId = orderId;
        this.recipientName = recipientName;
    }

    public String print() {
        return "[ゴリラ運輸 受領書] 注文番号：" + orderId + " / 受取人：" + recipientName;
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
        GoriraShippingLabel label = new GoriraShippingLabel("田中 太郎", "東京都渋谷区サンプル町 1-2-3", "ゴリラ運輸-1");
        GoriraDeliveryNote note = new GoriraDeliveryNote("ORDER-1001", List.of("ノートPC", "マウス"));
        GoriraReceiptForm receipt = new GoriraReceiptForm("ORDER-1001", "田中 太郎");

        System.out.println(label.print());
        System.out.println(note.print());
        System.out.println(receipt.print());
    }
}
```

**実行結果**

```
[ゴリラ運輸 配送ラベル] お問い合わせ番号：ゴリラ運輸-1 / 宛先：田中 太郎 東京都渋谷区サンプル町 1-2-3
[ゴリラ運輸 納品書] 注文番号：ORDER-1001 / 商品：[ノートPC, マウス]
[ゴリラ運輸 受領書] 注文番号：ORDER-1001 / 受取人：田中 太郎
```

※ここで一旦読むのを止めて、ご自身でコーディングを行なってみてください。その後で、続きを読んでください。

## 好ましくない実装

では、シナリオに従い追加実装をしていきましょう。

真っ先に思いつくのは、既存コードの仕様で示したクラスを参考に、ラクダ運輸用のクラスをそれぞれ複製し、実行クラスで配送会社ごとに分岐させる、という実装ではないでしょうか？

**`RakudaShippingLabel.java`**

```java
package example;

public class RakudaShippingLabel {
    private String recipientName;
    private String address;
    private String trackingNumber;

    public RakudaShippingLabel(String recipientName, String address, String trackingNumber) {
        this.recipientName = recipientName;
        this.address = address;
        this.trackingNumber = trackingNumber;
    }

    public String print() {
        return "[ラクダ運輸 配送ラベル] お問い合わせ番号：" + trackingNumber + " / 宛先：" + recipientName + " " + address;
    }
}
```

**`RakudaDeliveryNote.java`**

```java
package example;

public class RakudaDeliveryNote {
    private String orderId;
    private List<String> items;

    public RakudaDeliveryNote(String orderId, List<String> items) {
        this.orderId = orderId;
        this.items = items;
    }

    public String print() {
        return "[ラクダ運輸 納品書] 注文番号：" + orderId + " / 商品：" + items;
    }
}
```

**`RakudaReceiptForm.java`**

```java
package example;

public class RakudaReceiptForm {
    private String orderId;
    private String recipientName;

    public RakudaReceiptForm(String orderId, String recipientName) {
        this.orderId = orderId;
        this.recipientName = recipientName;
    }

    public String print() {
        return "[ラクダ運輸 受領書] 注文番号：" + orderId + " / 受取人：" + recipientName;
    }
}
```

**`Main.java`**

```java
package example;

public class Main {
    public static void main(String[] args) {
        /* ここを追加（ここから） */
        if (args.length != 1) {
            throw new IllegalArgumentException("配送会社名を1つだけ指定してください");
        }

        if (args[0].equals("rakuda")) {
            RakudaShippingLabel label = new RakudaShippingLabel("田中 太郎", "東京都渋谷区サンプル町 1-2-3", "ラクダ運輸-1");
            GoriraDeliveryNote note = new GoriraDeliveryNote("ORDER-1001", List.of("ノートPC", "マウス"));
            RakudaReceiptForm receipt = new RakudaReceiptForm("ORDER-1001", "田中 太郎");

            System.out.println(label.print());
            System.out.println(note.print());
            System.out.println(receipt.print());

            return;
        }
        /* ここを追加（ここまで） */

        GoriraShippingLabel label = new GoriraShippingLabel("田中 太郎", "東京都渋谷区サンプル町 1-2-3", "ゴリラ運輸-1");
        GoriraDeliveryNote note = new GoriraDeliveryNote("ORDER-1001", List.of("ノートPC", "マウス"));
        GoriraReceiptForm receipt = new GoriraReceiptForm("ORDER-1001", "田中 太郎");

        System.out.println(label.print());
        System.out.println(note.print());
        System.out.println(receipt.print());
    }
}
```

**実行結果**

※ `java Main rakuda` のように引数に `rakuda` を指定して実行した結果です。

```
[ラクダ運輸 配送ラベル] お問い合わせ番号：ラクダ運輸-1 / 宛先：田中 太郎 東京都渋谷区サンプル町 1-2-3
[ゴリラ運輸 納品書] 注文番号：ORDER-1001 / 商品：[ノートPC, マウス]
[ラクダ運輸 受領書] 注文番号：ORDER-1001 / 受取人：田中 太郎
```

コンパイルエラーがなく結果が出力されていることから、一見すると実装・動作確認ともに問題ないように見えます。

しかし、出力結果をよく見ると、配送ラベルと受領書は「ラクダ運輸」表記になっている一方、納品書だけ「ゴリラ運輸」表記のままになっていて、3 点の書類フォーマットが揃っていません。<br>
これは、`GoriraDeliveryNote` を `RakudaDeliveryNote` に切り替え忘れたことが原因です。

このように、この実装には以下の問題点があります。

- 配送会社が増えるたびに、`if` の分岐と書類クラス（`RakudaShippingLabel`・`RakudaDeliveryNote`・`RakudaReceiptForm`）の複製がさらに増えていく。
    - その結果、`Main` クラスが配送会社の数に比例して肥大化していく。
- 複製時に、3 つの書類のうち 1 つだけ切り替えを書き忘れてもコンパイルエラーにならないため、今回のような不整合に気づきにくい。
- 書類クラスに共通の型がないため、呼び出し元のコードを分岐ごとに丸ごと複製する必要がある。
- お問い合わせ番号や注文番号は、`Main` クラス（呼び出し元）で個別に指定しているため、3 点の書類間で番号がずれてもコンパイルエラーにならず気づきにくい。

## 正しい実装

では、好ましくない実装で触れた問題点を解決するにはどうすればよいのでしょうか？

これらの問題を解決するのが **Abstract Factory パターン**です。<br>
配送ラベル・納品書・受領書という関連する 3 つの生成物を、それぞれ個別に切り替えるのではなく、「配送会社ごとの工場（Factory）」という 1 つの窓口にまとめ、その工場を 1 つ選ぶだけで 3 点すべてが連動して切り替わるようにします。

では、実装を見ていきましょう。<br>
※本記事では下記のクラス構成としています。

> ```
> example.shipping パッケージ（スーパークラス）
>   ├── ShippingLabel.java      抽象クラス：配送ラベルの共通インターフェース
>   ├── DeliveryNote.java       抽象クラス：納品書の共通インターフェース
>   ├── ReceiptForm.java        抽象クラス：受領書の共通インターフェース
>   └── ShippingFactory.java    抽象クラス：3 点の書類をまとめて生成する窓口
>
> example.gorira パッケージ（ゴリラ運輸向けの具体的な実装 / 既存の仕様から本実装に合わせて修正）
>   ├── GoriraShippingLabel.java
>   ├── GoriraDeliveryNote.java
>   ├── GoriraReceiptForm.java
>   └── GoriraShippingFactory.java
>
> example.rakuda パッケージ（ラクダ運輸向けの具体的な実装）
>   ├── RakudaShippingLabel.java
>   ├── RakudaDeliveryNote.java
>   ├── RakudaReceiptForm.java
>   └── RakudaShippingFactory.java
> ```

まず、スーパークラスから見ていきましょう。

**example.shipping パッケージ**

**`ShippingLabel.java`**

```java
package example.shipping;

public abstract class ShippingLabel {
    protected String recipientName;
    protected String address;
    protected String trackingNumber;

    public ShippingLabel(String recipientName, String address, String trackingNumber) {
        this.recipientName = recipientName;
        this.address = address;
        this.trackingNumber = trackingNumber;
    }

    public abstract String print();
}
```

**`DeliveryNote.java`**

```java
package example.shipping;

public abstract class DeliveryNote {
    protected String orderId;
    protected List<String> items;

    public DeliveryNote(String orderId, List<String> items) {
        this.orderId = orderId;
        this.items = items;
    }

    public abstract String print();
}
```

**`ReceiptForm.java`**

```java
package example.shipping;

public abstract class ReceiptForm {
    protected String orderId;
    protected String recipientName;

    public ReceiptForm(String orderId, String recipientName) {
        this.orderId = orderId;
        this.recipientName = recipientName;
    }

    public abstract String print();
}
```

`ShippingLabel`・`DeliveryNote`・`ReceiptForm` は新たに追加した抽象クラスで、各書類の共通のフィールドやメソッドを定義しています。<br>
また、印字内容を生成する `print` メソッドを抽象メソッドとすることで、具体的な印字内容をサブクラスに委ねています。

**`ShippingFactory.java`**

```java
package example.shipping;

public abstract class ShippingFactory {
    protected final int sequenceNumber;

    protected ShippingFactory(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public abstract ShippingLabel createShippingLabel(String recipientName, String address);

    public abstract DeliveryNote createDeliveryNote(List<String> items);

    public abstract ReceiptForm createReceiptForm(String recipientName);
}
```

`ShippingFactory` クラスを振り返ると、先程の 3 つの抽象クラス（`ShippingLabel`・`DeliveryNote`・`ReceiptForm`）をそれぞれ生成する抽象メソッドを記述しています。<br>
これにより、1 つの配送会社につき 3 つの抽象メソッド（`createShippingLabel`・`createDeliveryNote`・`createReceiptForm`）をまとめて実装しなければならなくなるので、3 点の書類が必ずセットで揃うことが保証されるようになります。

<br>

次に、配送会社ごとの具体的な実装を見ていきましょう。

**example.gorira パッケージ**

**`GoriraShippingLabel.java`**

```java
package example.gorira;

import example.shipping.ShippingLabel;

public class GoriraShippingLabel extends ShippingLabel {
    public GoriraShippingLabel(String recipientName, String address, String trackingNumber) {
        super(recipientName, address, trackingNumber);
    }

    @Override
    public String print() {
        return "[ゴリラ運輸 配送ラベル] お問い合わせ番号：" + trackingNumber + " / 宛先：" + recipientName + " " + address;
    }
}
```

**`GoriraDeliveryNote.java`**

```java
package example.gorira;

import example.shipping.DeliveryNote;

public class GoriraDeliveryNote extends DeliveryNote {
    public GoriraDeliveryNote(String orderId, List<String> items) {
        super(orderId, items);
    }

    @Override
    public String print() {
        return "[ゴリラ運輸 納品書] 注文番号：" + orderId + " / 商品：" + items;
    }
}
```

**`GoriraReceiptForm.java`**

```java
package example.gorira;

import example.shipping.ReceiptForm;

public class GoriraReceiptForm extends ReceiptForm {
    public GoriraReceiptForm(String orderId, String recipientName) {
        super(orderId, recipientName);
    }

    @Override
    public String print() {
        return "[ゴリラ運輸 受領書] 注文番号：" + orderId + " / 受取人：" + recipientName;
    }
}
```

`GoriraShippingLabel`・`GoriraDeliveryNote`・`GoriraReceiptForm` クラスを振り返ると、既存の仕様からスーパークラスを継承し、`print` メソッドをオーバーライドする変更が加えられています。また、フィールドはスーパークラスと共有しています。<br>
ちなみに、Java ではコンストラクタが継承されないため、スーパークラスのコンストラクタを `super()` により呼び出しています。

**`GoriraShippingFactory.java`**

```java
package example.gorira;

import example.shipping.DeliveryNote;
import example.shipping.ReceiptForm;
import example.shipping.ShippingFactory;
import example.shipping.ShippingLabel;

public class GoriraShippingFactory extends ShippingFactory {
    public GoriraShippingFactory(int sequenceNumber) {
        super(sequenceNumber);
    }

    @Override
    public ShippingLabel createShippingLabel(String recipientName, String address) {
        return new GoriraShippingLabel(recipientName, address, "ゴリラ運輸-" + sequenceNumber);
    }

    @Override
    public DeliveryNote createDeliveryNote(List<String> items) {
        return new GoriraDeliveryNote("ORDER-" + (1000 + sequenceNumber), items);
    }

    @Override
    public ReceiptForm createReceiptForm(String recipientName) {
        return new GoriraReceiptForm("ORDER-" + (1000 + sequenceNumber), recipientName);
    }
}
```

`GoriraShippingFactory` クラスを振り返ると、`createShippingLabel`・`createDeliveryNote`・`createReceiptForm` メソッドそれぞれの具体的な実装が記述されています。<br>
これにより、ゴリラ運輸用の 3 点の書類がセットで揃うことになります。

<br>

**example.rakuda パッケージ**

**`RakudaShippingLabel.java`**

```java
package example.rakuda;

import example.shipping.ShippingLabel;

public class RakudaShippingLabel extends ShippingLabel {
    public RakudaShippingLabel(String recipientName, String address, String trackingNumber) {
        super(recipientName, address, trackingNumber);
    }

    @Override
    public String print() {
        return "[ラクダ運輸 配送ラベル] お問い合わせ番号：" + trackingNumber + " / 宛先：" + recipientName + " " + address;
    }
}
```

**`RakudaDeliveryNote.java`**

```java
package example.rakuda;

import example.shipping.DeliveryNote;

public class RakudaDeliveryNote extends DeliveryNote {
    public RakudaDeliveryNote(String orderId, List<String> items) {
        super(orderId, items);
    }

    @Override
    public String print() {
        return "[ラクダ運輸 納品書] 注文番号：" + orderId + " / 商品：" + items;
    }
}
```

**`RakudaReceiptForm.java`**

```java
package example.rakuda;

import example.shipping.ReceiptForm;

public class RakudaReceiptForm extends ReceiptForm {
    public RakudaReceiptForm(String orderId, String recipientName) {
        super(orderId, recipientName);
    }

    @Override
    public String print() {
        return "[ラクダ運輸 受領書] 注文番号：" + orderId + " / 受取人：" + recipientName;
    }
}
```

**`RakudaShippingFactory.java`**

```java
package example.rakuda;

import example.shipping.DeliveryNote;
import example.shipping.ReceiptForm;
import example.shipping.ShippingFactory;
import example.shipping.ShippingLabel;

public class RakudaShippingFactory extends ShippingFactory {
    public RakudaShippingFactory(int sequenceNumber) {
        super(sequenceNumber);
    }

    @Override
    public ShippingLabel createShippingLabel(String recipientName, String address) {
        return new RakudaShippingLabel(recipientName, address, "ラクダ運輸-" + sequenceNumber);
    }

    @Override
    public DeliveryNote createDeliveryNote(List<String> items) {
        return new RakudaDeliveryNote("ORDER-" + (1000 + sequenceNumber), items);
    }

    @Override
    public ReceiptForm createReceiptForm(String recipientName) {
        return new RakudaReceiptForm("ORDER-" + (1000 + sequenceNumber), recipientName);
    }
}
```

追加実装のクラスもゴリラ運輸用に作成したクラスと同様の処理の流れになっています。

ちなみに実務では、お問い合わせの番号や注文の番号は DB の自動採番や配送会社の API から発行された値を使うことが多いため、基本的にアプリ側で管理しないことの方が多いです。<br>
そのため、本実装でもコンストラクタで受け取った `sequenceNumber` を元に、3 点の書類の番号を生成しています。

<br>

最後に、実行クラスの実装を見ていきましょう。

**`Main.java`**

```java
package example;

import example.gorira.GoriraShippingFactory;
import example.rakuda.RakudaShippingFactory;
import example.shipping.DeliveryNote;
import example.shipping.ReceiptForm;
import example.shipping.ShippingFactory;
import example.shipping.ShippingLabel;

public class Main {
    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("配送会社名を1つだけ指定してください");
        }

        // 実際は DB の自動採番（INSERT 時に発行される連番）などを使う想定
        int sequenceNumber = 1;

        ShippingFactory factory = new GoriraShippingFactory(sequenceNumber);
        if (args[0].equals("rakuda")) {
            factory = new RakudaShippingFactory(sequenceNumber);
        }

        ShippingLabel label = factory.createShippingLabel("田中 太郎", "東京都渋谷区サンプル町 1-2-3");
        DeliveryNote note = factory.createDeliveryNote(List.of("ノートPC", "マウス"));
        ReceiptForm receipt = factory.createReceiptForm("田中 太郎");

        System.out.println(label.print());
        System.out.println(note.print());
        System.out.println(receipt.print());
    }
}
```

**実行結果**

※ `java Main rakuda` のように引数に `rakuda` を指定して実行した結果です。

```
[ラクダ運輸 配送ラベル] お問い合わせ番号：ラクダ運輸-1 / 宛先：田中 太郎 東京都渋谷区サンプル町 1-2-3
[ラクダ運輸 納品書] 注文番号：ORDER-1001 / 商品：[ノートPC, マウス]
[ラクダ運輸 受領書] 注文番号：ORDER-1001 / 受取人：田中 太郎
```

**実行結果**

※ `java Main gorira` のように 1 つの引数を指定して実行した結果です。

```
[ゴリラ運輸 配送ラベル] お問い合わせ番号：ゴリラ運輸-1 / 宛先：田中 太郎 東京都渋谷区サンプル町 1-2-3
[ゴリラ運輸 納品書] 注文番号：ORDER-1001 / 商品：[ノートPC, マウス]
[ゴリラ運輸 受領書] 注文番号：ORDER-1001 / 受取人：田中 太郎
```

`Main` クラスを振り返ると、変数の型は全て抽象クラス（`ShippingFactory`・`ShippingLabel`・`DeliveryNote`・`ReceiptForm`）になっています。<br>
そのため、呼び出し元は具体クラスの内部実装を知らなくても、3 点の書類を生成・出力できます。

以上のような実装を行うと、以下のメリットがあります。

- 配送会社の切り替えだけで、3 点の配送会社専用の書類がすべて連動して切り替わるため、組み合わせの取り違えが構造的に発生しない。
- 配送会社を追加する際は、新しい具体クラス一式の追加と呼び出し元の修正をするだけでよく、既存のクラスを変更する必要がない。
    - また、呼び出す窓口（`ShippingFactory` を継承した具体クラス）を追加する修正だけになるため、呼び出し元（`Main` クラス）の肥大化を防げる。
- 抽象クラス `ShippingFactory` を継承した実装クラスのインスタンスを生成する際に、番号を渡すだけで 3 点の書類すべてに一貫して反映されるため、`create` メソッドをどの順番で呼んでも番号がずれたり重複したりする心配がない。
- 配送会社の選択方法を実務向け（例えば、注文データの配送会社コードの判定、設定ファイルでの指定など）に変更しても、条件分岐の条件式が変わるだけで済むため、呼び出し元のコードは具体的な配送会社を意識せずに済む。

## まとめ

正しい実装の `Main` クラスを振り返ると、`ShippingFactory` の具体クラスを切り替えるだけで、3 点の配送会社専用の書類がすべて連動して切り替わります。<br>
このように、Abstract Factory パターンは、関連する複数の生成物を 1 つの単位として扱い、組み合わせの一貫性を設計レベルで保証するパターンです。

また、配送会社を追加する際も、新しいパッケージを 1 つ追加し、呼び出し元（`Main` クラス）を切り替えるだけで済み、既存のクラスを変更する必要はありません。<br>
そのため、配送会社の種類が増えるほど、Abstract Factory パターンの効果を実感しやすくなります。

本記事の内容はここまでとなります。

以降は「もう少し深く知りたい」という方向けの補足となります。今回学んだパターンに繋がる設計原則や、実務で役立つ背景知識について触れています。

---

<a id="深堀り1"></a>

## 【深堀り①】Factory Method パターンとの違い

Factory Method パターンとは、1 つのインスタンスを生成するためのメソッドを持つ抽象クラスが「具体的にどのクラスのインスタンスを生成するか」をサブクラスに委ねるパターンです。サブクラスでは、先ほどの抽象クラスを継承した具体クラスのインスタンスを生成し、型は抽象クラスのまま返すため、呼び出し元はサブクラスが何であるかを知らずに、共通の型として扱うことができます。

本記事の抽象クラス `ShippingFactory` を振り返ると、抽象メソッド `createShippingLabel`・`createDeliveryNote`・`createReceiptForm` という 3 つの生成メソッドを持っています。この 1 つひとつのメソッドだけを取り出すと、先程の Factory Method パターンと同じ構造をしていることがわかります。<br>
つまり、Abstract Factory パターンは、関連する複数の Factory Method を 1 つの窓口（本記事の `ShippingFactory`）にまとめ、「どの具体的な組み合わせ（`GoriraShippingFactory`・`RakudaShippingFactory`）を使うか」を 1 か所のインスタンスを生成するところだけで切り替えられるようにしたパターンと言えます。

以上から、Factory Method パターンは、3 つの生成メソッド（`createShippingLabel`・`createDeliveryNote`・`createReceiptForm`）をそれぞれのクラスで定義することになるため、関連する生成物の組み合わせを一貫させる保証ができません。一方で、Abstract Factory パターンは、この「関連する生成物の組み合わせを一貫させる」という制約を設計レベルで保証するパターンとなります。

> ちなみに、もし本記事で Factory Method パターンを使用すると、ラベル用だけゴリラ運輸、納品書用だけラクダ運輸、というような取り違えが起きてしまう可能性があります。

<a id="深堀り2"></a>

## 【深堀り②】拡張の方向性によるトレードオフ

追加実装をする際、Abstract Factory パターンには、見落とされやすいトレードオフがあります。

ここでは、本記事を例にどのような点がトレードオフになるのかを確認します。

**配送会社を追加する場合**

ここでは具体例として、「パンダ運輸」を追加で実装する場合を考えてみましょう。<br>
この場合、ラクダ運輸と同様、`example.panda` パッケージを新設し、`PandaShippingLabel`・`PandaDeliveryNote`・`PandaReceiptForm`・`PandaShippingFactory` を追加し、実行クラス（`Main` クラス）で呼び出すための条件分岐を追加します。
一方、既存のクラスは 1 行も変更する必要がありません。

**書類の種類を追加する場合**

ここでは具体例として、「着払い控え」を追加で実装する場合を考えてみましょう。<br>
この場合、抽象クラス `ShippingFactory` に `createCashOnDeliverySlip` のような抽象メソッドを追加する必要があります。これに連動して、サブクラス（`GoriraShippingFactory`・`RakudaShippingFactory`）に `createCashOnDeliverySlip` メソッドの具体的な実装を追加する必要があります。そのため、既存のクラスの変更を行う必要が出てきます。

以上のように、Abstract Factory パターンは「関連する複数の生成物をまとめて追加すること（配送会社の追加）」には強い一方、「生成物自体の種類を追加すること（書類の種類の追加）」には弱いというトレードオフの関係があります。Abstract Factory パターンを用いる際は、要件の変化の方向性を見極めたうえで採用することが大切です。

### 補足（具象メソッドによるトレードオフ）

書類の種類を追加する際、抽象メソッドを追加すると既存クラスの修正をしなければなりませんでした。

しかし、実務ではどうしても書類の種類を追加したい場合があります。

その際は、デフォルトの処理を持つ具象メソッドを定義することを検討すべきでしょう。具象メソッドであれば、サブクラス側はオーバーライドが任意になるため、デフォルトの処理のままで問題ないサブクラスでは変更が不要になります。

例えば、抽象クラス `ShippingFactory` に次のような具象メソッドを追加したとします。

**`ShippingFactory.java`（一部抜粋）**

```java
public abstract class ShippingFactory {
    // 既存の抽象メソッドは省略

    public CashOnDeliverySlip createCashOnDeliverySlip(String recipientName) {
        return new GenericCashOnDeliverySlip(recipientName);
    }
}
```

この場合、`GoriraShippingFactory`・`RakudaShippingFactory` のどちらも `createCashOnDeliverySlip` のオーバーライドは任意のため、何も修正しなければ上記のコードがそのまま実行されます。そのため、もし専用の処理を行いたい場合はオーバーライドし、関係ない具象クラスでは修正の必要がありません。

ただし、この方法には代償があります。抽象メソッドであればオーバーライドを怠るとコンパイルエラーになりますが、具象メソッドの場合はオーバーライドを忘れてもコンパイルが通ってしまいます。そのため、専用の処理が必要なクラスでオーバーライドすることを忘れたまま気づかずにリリースしてしまうと、専用の処理を行いたい書類がデフォルトのフォーマットのままで出力されてしまうという不整合が生まれる可能性があります。

つまり具象メソッドの採用は、「変更が必要なサブクラスを減らせる」というメリットと引き換えに、3 点の書類の組み合わせをコンパイル時に保証するという Abstract Factory パターン本来の強み（→ [【深堀り①】Factory Method パターンとの違い](#深堀り1)）を弱めるトレードオフであり、書類の種類追加のデメリットを完全に相殺するものではない点に注意が必要です。

<a id="深堀り3"></a>

## 【深堀り③】OCP（オープン・クローズドの原則）

正しい実装を振り返ると、`GoriraShippingFactory` に加えて `RakudaShippingFactory` を追加する際に必要だったのは、新しいパッケージの追加と呼び出し元（`Main` クラス）での切り替えだけで、抽象クラス `ShippingFactory` や `GoriraShippingFactory` には一切手を加えていません。<br>
呼び出し元が依存しているのは抽象クラス（`ShippingFactory`・`ShippingLabel`・`DeliveryNote`・`ReceiptForm`）だけであるため、具体的な配送会社が何であっても対応できます。

この「既存コードを変えずに、新しいクラスを追加するだけで機能を拡張できる」という設計は、「**OCP（Open/Closed Principle：オープン・クローズドの原則）**」と呼ばれる設計原則の実践です。Abstract Factory パターンは OCP を実現するための設計手段の一つと言えます。

詳しくは「OCP」や「オープン・クローズドの原則」で検索してみてください。

<a id="深堀り4"></a>

## 【深堀り④】GoF デザインパターンとの位置づけ

今回使った Abstract Factory パターンは、GoF（Gang of Four）の 23 のデザインパターンのうち「生成パターン」に分類されます。<br>
詳しくは「GoF」で検索してみてください。
