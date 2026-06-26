# Abstract Factory（アブストラクトファクトリー）パターン ― 関連オブジェクトの組み合わせを丸ごと切り替える

次のような経験をしたことはありませんか？

> 複数の関連するオブジェクトを、状況に応じてセットで切り替えたいのに、それぞれを別々の場所で生成していた。<br>
> その結果、一部だけ切り替えを忘れてしまい、組み合わせの一部だけが古い仕様のまま残ってしまった。

実務では、関連するもの同士を一貫した組み合わせで扱いたいという要求は珍しくありません。<br>
しかし、それぞれの生成処理を個別に管理してしまうと、関連性を保証する仕組みがどこにもないため、組み合わせの一部だけ更新を忘れるリスクを常に抱えることになります。

そのための設計が **Abstract Factory パターン**です。関連する複数の生成物を「抽象的な工場（Abstract Factory）」としてひとまとめにし、生成物の組み合わせ自体を 1 つの単位として切り替えられるようにする、という役割を持つパターンです。

この記事では、EC サイトの配送業務で複数の配送会社に対応するというシナリオを通して、Abstract Factory パターンがこの問題をどのように解決するかを紹介します。

## 目次

- [【具体例】](#具体例)
    - [シナリオ](#シナリオ)
    - [既存コードの仕様](#既存コードの仕様)
- [好ましくない実装](#好ましくない実装)
- [正しい実装](#正しい実装)
- [まとめ](#まとめ)
- [【深堀り①】Factory Method パターンとの違い](#深堀り1)
- [【深堀り②】新しい「種類」を追加する際のトレードオフ](#深堀り2)
- [【深堀り③】ファクトリの選択方法と実務での扱い](#深堀り3)
- [【深堀り④】GoF デザインパターンとの位置づけ](#深堀り4)

---

## 【具体例】

### シナリオ

> あなたは EC サイトの開発チームに所属しています。<br>
> 現在、商品の配送は外部の配送会社「サンプルA運輸」のみに対応しており、出荷時には「配送ラベル」「納品書」「受領書」の 3 点をサンプルA運輸所定のフォーマットで作成しています。<br>
> ある日、物流コスト削減のため、もう一社「サンプルB運輸」とも提携することになりました。<br>
> あなたは、サンプルB運輸で出荷する場合も、同じ 3 点の書類をサンプルB運輸所定のフォーマットで作成できるようにする実装を担当します。

※実際の配送会社との連携では、各社が提供する API や SDK を呼び出すのが一般的ですが、本記事では Abstract Factory パターンの解説に集中するため、コンソールへの文字列出力のみとします。

### 既存コードの仕様

※実務では、`ShippingLabel` のようなエンティティクラスは `entity` パッケージなど専用のディレクトリに切り出すのが一般的です。しかし、本記事ではパッケージ構成を主題としないため `example` パッケージ直下にまとめています。

- `ShippingLabel`（既存クラス）

荷物に貼付する配送ラベルを表すクラスです。受取人の情報とお問い合わせ番号（トラッキング番号）を保持し、ラベルの印字内容を生成します。

| フィールド       | 型       | 説明             |
| ---------------- | -------- | ---------------- |
| `recipientName`  | `String` | 受取人名         |
| `address`        | `String` | 配送先住所       |
| `trackingNumber` | `String` | お問い合わせ番号 |

| メソッド | 戻り値の型 | 説明                       |
| -------- | ---------- | -------------------------- |
| `print`  | `String`   | ラベルの印字内容を生成する |

**`ShippingLabel.java`**

```java
package example;

public class ShippingLabel {
    private String recipientName;
    private String address;
    private String trackingNumber;

    public ShippingLabel(String recipientName, String address, String trackingNumber) {
        this.recipientName = recipientName;
        this.address = address;
        this.trackingNumber = trackingNumber;
    }

    public String print() {
        return "[配送ラベル] お問い合わせ番号：" + trackingNumber + " / 宛先：" + recipientName + " " + address;
    }
}
```

<br>

- `DeliveryNote`（既存クラス）

荷物に同梱する納品書を表すクラスです。注文番号と商品の一覧を保持し、納品書の印字内容を生成します。

| フィールド | 型             | 説明         |
| ---------- | -------------- | ------------ |
| `orderId`  | `String`       | 注文番号     |
| `items`    | `List<String>` | 商品名の一覧 |

| メソッド | 戻り値の型 | 説明                       |
| -------- | ---------- | -------------------------- |
| `print`  | `String`   | 納品書の印字内容を生成する |

**`DeliveryNote.java`**

```java
package example;

import java.util.List;

public class DeliveryNote {
    private String orderId;
    private List<String> items;

    public DeliveryNote(String orderId, List<String> items) {
        this.orderId = orderId;
        this.items = items;
    }

    public String print() {
        return "[納品書] 注文番号：" + orderId + " / 商品：" + items;
    }
}
```

<br>

- `ReceiptForm`（既存クラス）

荷物の受け取り確認に使用される受領書を表すクラスです。注文番号と受取人名を保持し、受領書の印字内容を生成します。

| フィールド      | 型       | 説明     |
| --------------- | -------- | -------- |
| `orderId`       | `String` | 注文番号 |
| `recipientName` | `String` | 受取人名 |

| メソッド | 戻り値の型 | 説明                       |
| -------- | ---------- | -------------------------- |
| `print`  | `String`   | 受領書の印字内容を生成する |

**`ReceiptForm.java`**

```java
package example;

public class ReceiptForm {
    private String orderId;
    private String recipientName;

    public ReceiptForm(String orderId, String recipientName) {
        this.orderId = orderId;
        this.recipientName = recipientName;
    }

    public String print() {
        return "[受領書] 注文番号：" + orderId + " / 受取人：" + recipientName;
    }
}
```

<br>

- `Main`（実行クラス）

**`Main.java`**

```java
package example;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        ShippingLabel label = new ShippingLabel("田中 太郎", "東京都渋谷区サンプル町 1-2-3", "A-001");
        DeliveryNote note = new DeliveryNote("ORDER-1001", List.of("ノートPC", "マウス"));
        ReceiptForm receipt = new ReceiptForm("ORDER-1001", "田中 太郎");

        System.out.println(label.print());
        System.out.println(note.print());
        System.out.println(receipt.print());
    }
}
```

**実行結果**

```
[配送ラベル] お問い合わせ番号：A-001 / 宛先：田中 太郎 東京都渋谷区サンプル町 1-2-3
[納品書] 注文番号：ORDER-1001 / 商品：[ノートPC, マウス]
[受領書] 注文番号：ORDER-1001 / 受取人：田中 太郎
```

※ここで一旦読むのを止めて、ご自身でコーディングを行なってみてください。その後で、続きを読んでください。

## 好ましくない実装

では、シナリオに従い追加実装をしていきましょう。

既存の `ShippingLabel`・`DeliveryNote`・`ReceiptForm` を参考に、サンプルB運輸用のクラスをそれぞれ複製し、呼び出し元で配送会社ごとに分岐させる、という実装をするのではないでしょうか？

**`SampleBShippingLabel.java`**

```java
package example;

public class SampleBShippingLabel {
    private String recipientName;
    private String address;
    private String trackingNumber;

    public SampleBShippingLabel(String recipientName, String address, String trackingNumber) {
        this.recipientName = recipientName;
        this.address = address;
        this.trackingNumber = trackingNumber;
    }

    public String print() {
        return "〔サンプルB運輸 配送ラベル〕問い合わせ番号：" + trackingNumber + " ｜宛先：" + recipientName + " " + address;
    }
}
```

**`SampleBReceiptForm.java`**

```java
package example;

public class SampleBReceiptForm {
    private String orderId;
    private String recipientName;

    public SampleBReceiptForm(String orderId, String recipientName) {
        this.orderId = orderId;
        this.recipientName = recipientName;
    }

    public String print() {
        return "〔サンプルB運輸 受領書〕注文番号：" + orderId + " ｜受取人：" + recipientName;
    }
}
```

**`Main.java`**

```java
package example;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        String carrierType = "sampleB";

        /* ここを追加（ここから） */
        if (carrierType.equals("sampleB")) {
            SampleBShippingLabel label = new SampleBShippingLabel("田中 太郎", "東京都渋谷区サンプル町 1-2-3", "B-001");
            DeliveryNote note = new DeliveryNote("ORDER-1001", List.of("ノートPC", "マウス"));
            SampleBReceiptForm receipt = new SampleBReceiptForm("ORDER-1001", "田中 太郎");

            System.out.println(label.print());
            System.out.println(note.print());
            System.out.println(receipt.print());
            return;
        }
        /* ここを追加（ここまで） */

        ShippingLabel label = new ShippingLabel("田中 太郎", "東京都渋谷区サンプル町 1-2-3", "A-001");
        DeliveryNote note = new DeliveryNote("ORDER-1001", List.of("ノートPC", "マウス"));
        ReceiptForm receipt = new ReceiptForm("ORDER-1001", "田中 太郎");

        System.out.println(label.print());
        System.out.println(note.print());
        System.out.println(receipt.print());
    }
}
```

**実行結果**

```
〔サンプルB運輸 配送ラベル〕問い合わせ番号：B-001 ｜宛先：田中 太郎 東京都渋谷区サンプル町 1-2-3
[納品書] 注文番号：ORDER-1001 / 商品：[ノートPC, マウス]
〔サンプルB運輸 受領書〕注文番号：ORDER-1001 ｜受取人：田中 太郎
```

コンパイルエラーがなく結果が出力されていることから、一見すると実装・動作確認ともに問題ないように見えます。

しかし、出力結果をよく見ると、配送ラベルと受領書は「サンプルB運輸」表記になっているのに対し、納品書だけ表記が変わっておらず、3 点の書類フォーマットが揃っていません。`DeliveryNote` をサンプルB運輸用に切り替える分岐を書き忘れたことが原因です。

この実装には以下の問題点があります。

- 配送会社が増えるたびに、`if` の分岐と書類クラスの複製がさらに増えていく
- 3 つの書類のうち 1 つだけ切り替えを書き忘れてもコンパイルエラーにならないため、今回のような不整合に気づきにくい
- `SampleBShippingLabel` と `ShippingLabel` に共通の型がないため、呼び出し元のコードを分岐ごとに丸ごと複製する必要がある
- 配送会社の追加・変更のたびに `Main` クラス（呼び出し元）を直接修正する必要があり、修正範囲が広がる

## 正しい実装

では、好ましくない実装で触れた問題点を解決するにはどうすればよいのでしょうか？

これらの問題を解決するのが **Abstract Factory パターン**です。<br>
配送ラベル・納品書・受領書という関連する 3 つの生成物を、それぞれ個別に切り替えるのではなく、「配送会社ごとの工場（Factory）」という 1 つの窓口にまとめ、その工場を 1 つ選ぶだけで 3 点すべてが連動して切り替わるようにします。

※本記事では下記のクラス構成としています。

> ```
> example.shipping パッケージ（スーパークラス）
>   ├── ShippingLabel.java      抽象クラス：配送ラベルの共通インターフェース
>   ├── DeliveryNote.java       抽象クラス：納品書の共通インターフェース
>   ├── ReceiptForm.java        抽象クラス：受領書の共通インターフェース
>   └── ShippingFactory.java    抽象クラス：3 点の書類をまとめて生成する窓口
>
> example.shipping.samplea パッケージ（サンプルA運輸向けの具体的な実装）
>   ├── SampleAShippingLabel.java
>   ├── SampleADeliveryNote.java
>   ├── SampleAReceiptForm.java
>   └── SampleAShippingFactory.java
>
> example.shipping.sampleb パッケージ（サンプルB運輸向けの具体的な実装）
>   ├── SampleBShippingLabel.java
>   ├── SampleBDeliveryNote.java
>   ├── SampleBReceiptForm.java
>   └── SampleBShippingFactory.java
> ```

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

import java.util.List;

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

**`ShippingFactory.java`**

```java
package example.shipping;

import java.util.List;

public abstract class ShippingFactory {
    public abstract ShippingLabel createShippingLabel(String recipientName, String address);

    public abstract DeliveryNote createDeliveryNote(String orderId, List<String> items);

    public abstract ReceiptForm createReceiptForm(String orderId, String recipientName);
}
```

`ShippingLabel`・`DeliveryNote`・`ReceiptForm` はいずれも抽象クラスとなり、印字内容を生成する `print` メソッドは具体的な実装を持たず、サブクラスに委ねられています。<br>
また `ShippingFactory` は、この 3 つの抽象クラスをそれぞれ生成する `createShippingLabel`・`createDeliveryNote`・`createReceiptForm` という 3 つの抽象メソッドを持っています。1 つの配送会社につき、この 3 つのメソッドをまとめて実装することになるため、3 点の書類が必ずセットで揃うことが保証されます。

次に、配送会社ごとの具体的な実装を見ていきましょう。

<br>

**example.shipping.samplea パッケージ**

**`SampleAShippingLabel.java`**

```java
package example.shipping.samplea;

import example.shipping.ShippingLabel;

public class SampleAShippingLabel extends ShippingLabel {

    public SampleAShippingLabel(String recipientName, String address, String trackingNumber) {
        super(recipientName, address, trackingNumber);
    }

    @Override
    public String print() {
        return "[サンプルA運輸 配送ラベル] お問い合わせ番号：" + trackingNumber + " / 宛先：" + recipientName + " " + address;
    }
}
```

**`SampleADeliveryNote.java`**

```java
package example.shipping.samplea;

import example.shipping.DeliveryNote;
import java.util.List;

public class SampleADeliveryNote extends DeliveryNote {

    public SampleADeliveryNote(String orderId, List<String> items) {
        super(orderId, items);
    }

    @Override
    public String print() {
        return "[サンプルA運輸 納品書] 注文番号：" + orderId + " / 商品：" + items;
    }
}
```

**`SampleAReceiptForm.java`**

```java
package example.shipping.samplea;

import example.shipping.ReceiptForm;

public class SampleAReceiptForm extends ReceiptForm {

    public SampleAReceiptForm(String orderId, String recipientName) {
        super(orderId, recipientName);
    }

    @Override
    public String print() {
        return "[サンプルA運輸 受領書] 注文番号：" + orderId + " / 受取人：" + recipientName;
    }
}
```

**`SampleAShippingFactory.java`**

```java
package example.shipping.samplea;

import example.shipping.DeliveryNote;
import example.shipping.ReceiptForm;
import example.shipping.ShippingFactory;
import example.shipping.ShippingLabel;
import java.util.List;

public class SampleAShippingFactory extends ShippingFactory {
    private int labelCount = 0;

    @Override
    public ShippingLabel createShippingLabel(String recipientName, String address) {
        return new SampleAShippingLabel(recipientName, address, "A-" + (++labelCount));
    }

    @Override
    public DeliveryNote createDeliveryNote(String orderId, List<String> items) {
        return new SampleADeliveryNote(orderId, items);
    }

    @Override
    public ReceiptForm createReceiptForm(String orderId, String recipientName) {
        return new SampleAReceiptForm(orderId, recipientName);
    }
}
```

<br>

**example.shipping.sampleb パッケージ**

**`SampleBShippingLabel.java`**

```java
package example.shipping.sampleb;

import example.shipping.ShippingLabel;

public class SampleBShippingLabel extends ShippingLabel {

    public SampleBShippingLabel(String recipientName, String address, String trackingNumber) {
        super(recipientName, address, trackingNumber);
    }

    @Override
    public String print() {
        return "〔サンプルB運輸 配送ラベル〕問い合わせ番号：" + trackingNumber + " ｜宛先：" + recipientName + " " + address;
    }
}
```

**`SampleBDeliveryNote.java`**

```java
package example.shipping.sampleb;

import example.shipping.DeliveryNote;
import java.util.List;

public class SampleBDeliveryNote extends DeliveryNote {

    public SampleBDeliveryNote(String orderId, List<String> items) {
        super(orderId, items);
    }

    @Override
    public String print() {
        return "〔サンプルB運輸 納品書〕注文番号：" + orderId + " ｜商品：" + items;
    }
}
```

**`SampleBReceiptForm.java`**

```java
package example.shipping.sampleb;

import example.shipping.ReceiptForm;

public class SampleBReceiptForm extends ReceiptForm {

    public SampleBReceiptForm(String orderId, String recipientName) {
        super(orderId, recipientName);
    }

    @Override
    public String print() {
        return "〔サンプルB運輸 受領書〕注文番号：" + orderId + " ｜受取人：" + recipientName;
    }
}
```

**`SampleBShippingFactory.java`**

```java
package example.shipping.sampleb;

import example.shipping.DeliveryNote;
import example.shipping.ReceiptForm;
import example.shipping.ShippingFactory;
import example.shipping.ShippingLabel;
import java.util.List;

public class SampleBShippingFactory extends ShippingFactory {
    private int labelCount = 0;

    @Override
    public ShippingLabel createShippingLabel(String recipientName, String address) {
        return new SampleBShippingLabel(recipientName, address, "B-" + (++labelCount));
    }

    @Override
    public DeliveryNote createDeliveryNote(String orderId, List<String> items) {
        return new SampleBDeliveryNote(orderId, items);
    }

    @Override
    public ReceiptForm createReceiptForm(String orderId, String recipientName) {
        return new SampleBReceiptForm(orderId, recipientName);
    }
}
```

`SampleAShippingFactory`・`SampleBShippingFactory` を見ると、それぞれ `ShippingFactory` を継承し、3 つの生成メソッドの中で、対応する配送会社専用の具体クラス（`SampleAShippingLabel` など）を生成していることがわかります。<br>
お問い合わせ番号も、呼び出し元から渡すのではなく `labelCount` フィールドで各工場が自分で管理しています。そのため、配送会社をまたいで番号が混ざったり、採番が重複したりする心配がありません。

実行クラスは次のコードとなります。

<br>

**`Main.java`**

```java
package example;

import example.shipping.DeliveryNote;
import example.shipping.ReceiptForm;
import example.shipping.ShippingFactory;
import example.shipping.ShippingLabel;
import example.shipping.samplea.SampleAShippingFactory;
import example.shipping.sampleb.SampleBShippingFactory;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        ShippingFactory factory = new SampleAShippingFactory();
        // ShippingFactory factory = new SampleBShippingFactory();

        ShippingLabel label = factory.createShippingLabel("田中 太郎", "東京都渋谷区サンプル町 1-2-3");
        DeliveryNote note = factory.createDeliveryNote("ORDER-1001", List.of("ノートPC", "マウス"));
        ReceiptForm receipt = factory.createReceiptForm("ORDER-1001", "田中 太郎");

        System.out.println(label.print());
        System.out.println(note.print());
        System.out.println(receipt.print());
    }
}
```

**実行結果**

```
[サンプルA運輸 配送ラベル] お問い合わせ番号：A-1 / 宛先：田中 太郎 東京都渋谷区サンプル町 1-2-3
[サンプルA運輸 納品書] 注文番号：ORDER-1001 / 商品：[ノートPC, マウス]
[サンプルA運輸 受領書] 注文番号：ORDER-1001 / 受取人：田中 太郎
```

`Main` クラスを見ると、変数の型は全て抽象クラス（`ShippingFactory`・`ShippingLabel`・`DeliveryNote`・`ReceiptForm`）です。そのため、呼び出し元は `SampleAShippingFactory` や `SampleAShippingLabel` といった具体クラスの内部実装を知らなくても、3 点の書類を生成・出力できます。

ここで `ShippingFactory factory = new SampleAShippingFactory();` の行をコメントアウトしている `new SampleBShippingFactory()` に変更してみましょう。`createShippingLabel`・`createDeliveryNote`・`createReceiptForm` の呼び出し方は 1 行も変えていないにもかかわらず、3 点の書類すべてがサンプルB運輸仕様の出力に切り替わります。好ましくない実装で起きていた「納品書だけ切り替えを忘れる」というミスは、この構造では起こり得ません。

このような Abstract Factory パターンの実装を行うと、以下のメリットがあります。

- 配送会社を 1 つ選ぶだけで、関連する 3 点の書類がすべて連動して切り替わるため、組み合わせの取り違えが構造的に発生しない
- 配送会社を追加する際は、新しい具体クラス一式とパッケージを追加するだけでよく、既存のクラスを変更する必要がない
- お問い合わせ番号の采番が各 `ShippingFactory` の実装に閉じているため、採番ミスや重複が発生しない

## まとめ

正しい実装の `Main` クラスを見ると、`ShippingFactory` の具体クラスを 1 つ選ぶだけで、`ShippingLabel`・`DeliveryNote`・`ReceiptForm` の 3 つすべてが連動して切り替わります。<br>
そのため、好ましくない実装で起きていたような「一部だけ切り替えを忘れる」というミスは構造的に起こりません。<br>
つまり、Abstract Factory パターンは、関連する複数の生成物を 1 つの単位として扱い、組み合わせの一貫性を設計レベルで保証するパターンです。

また、配送会社を追加する際も、新しいパッケージを 1 つ追加するだけで済み、既存のクラスを変更する必要はありません。<br>
そのため、関連するファミリーの種類が増えるほど、Abstract Factory パターンの効果を実感しやすくなります。

本記事の内容はここまでとなります。

以降は「もう少し深く知りたい」という方向けの補足となります。今回学んだパターンに繋がる設計原則や、実務で役立つ背景知識について触れています。

---

<a id="深堀り1"></a>

## 【深堀り①】Factory Method パターンとの違い

Factory Method パターンを思い出してみましょう。`Factory` 抽象クラスが `createManagement` のような 1 つの生成メソッドを持ち、サブクラスが 1 種類の抽象生成物（`Management`）をどう作るかを決める、という構造でした。

本記事の `ShippingFactory` を見ると、`createShippingLabel`・`createDeliveryNote`・`createReceiptForm` という 3 つの生成メソッドを持っています。この 1 つひとつのメソッドだけを取り出すと、それぞれ単体で Factory Method パターンと同じ構造（抽象クラスが生成方法を定義し、サブクラスが具体的な生成を行う）をしていることがわかります。

つまり、Abstract Factory パターンは、関連する複数の Factory Method を 1 つの窓口（`ShippingFactory`）にまとめ、「どの具体的な組み合わせ（サンプルA運輸 or サンプルB運輸）を使うか」を 1 か所の `new` だけで切り替えられるようにしたパターンと言えます。

`ShippingFactory factory = new SampleAShippingFactory();` の 1 行を変更するだけで、`createShippingLabel`・`createDeliveryNote`・`createReceiptForm` の 3 つすべてが連動してサンプルB運輸仕様に切り替わります。これは Factory Method パターンを 3 つ別々に使った場合には保証できません。仮に 3 つの `Factory` を個別に持っていた場合、ラベル用だけサンプルA運輸、納品書用だけサンプルB運輸、というような取り違えが起きてしまう可能性があります。Abstract Factory パターンは、この「関連する生成物の組み合わせを一貫させる」という制約を設計レベルで保証する点が、Factory Method パターンとの違いです。

<a id="深堀り2"></a>

## 【深堀り②】新しい「種類」を追加する際のトレードオフ

Abstract Factory パターンには、見落とされやすいトレードオフがあります。

例えば、配送会社をもう 1 社（サンプルC運輸など）追加したくなったとします。この場合は `example.shipping.samplec` パッケージを新設し、`SampleCShippingLabel`・`SampleCDeliveryNote`・`SampleCReceiptForm`・`SampleCShippingFactory` を追加するだけで済みます。既存のクラスは 1 行も変更する必要がありません。

一方で、書類の「種類」を増やしたい場合（例えば「着払い控え」を新たに追加したい場合）はどうでしょうか。この場合、抽象クラス `ShippingFactory` に `createCashOnDeliverySlip` のような抽象メソッドを追加する必要があり、それに連動して `SampleAShippingFactory`・`SampleBShippingFactory` の両方に実装を追加しなければなりません。

つまり、Abstract Factory パターンは「ファミリー（配送会社）の追加」には強い一方、「生成物の種類（書類の種類）の追加」には弱いという非対称な性質を持っています。新しい配送会社が増えやすいのか、新しい書類の種類が増えやすいのか、要件の変化の方向性を見極めたうえで採用することが大切です。

<a id="深堀り3"></a>

## 【深堀り③】ファクトリの選択方法と実務での扱い

正しい実装の `Main` クラスでは、コメントアウトで `SampleAShippingFactory` と `SampleBShippingFactory` を切り替えています。しかし実務では、これをソースコードのコメントアウトで切り替えることはありません。

例えば、注文データに保持されている「配送会社コード」を読み取り、`if` 文や `switch` 式で対応する `ShippingFactory` を選択する、設定ファイル（`application.properties` など）で配送会社を指定し、起動時にどちらの `ShippingFactory` を使うかを決定する、といった方法が考えられます。

どの方法であっても、生成方法を切り替えるための条件分岐が 1 か所（ファクトリを選ぶ部分）に閉じ込められ、`ShippingLabel`・`DeliveryNote`・`ReceiptForm` を使う側のコードは具体的な配送会社を意識せずに済む、という点は変わりません。

<a id="深堀り4"></a>

## 【深堀り④】GoF デザインパターンとの位置づけ

今回使った Abstract Factory パターンは、GoF（Gang of Four）の 23 のデザインパターンのうち「生成パターン」に分類されます。<br>
詳しくは「GoF」で検索してみてください。
