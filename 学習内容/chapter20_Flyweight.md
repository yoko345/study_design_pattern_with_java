# Flyweight（フライウェイト）パターン ― 同じオブジェクトを共有してメモリを節約する

次のような経験をしたことはありませんか？

> 同じ内容のデータを持つオブジェクトを、処理のたびに作っていた。1 件ずつ作ってすぐに使い終わるうちは問題なかったが、大量のオブジェクトをまとめて持っておく必要が出てきた途端、中身がまったく同じデータがオブジェクトの数だけメモリに積み上がり、メモリ不足でプログラムが止まりかけてしまった。

この記事では、倉庫システムで出荷ラベルを配送エリアの順にまとめて印刷するシナリオを通して、Flyweight パターンがこの問題をどのように解決するかを紹介します。

## 目次

- [【具体例】](#具体例)
    - [シナリオ](#シナリオ)
    - [既存コードの仕様](#既存コードの仕様)
- [好ましくない実装](#好ましくない実装)
- [正しい実装](#正しい実装)
- [まとめ](#まとめ)
- [【深堀り①】共有したオブジェクトを書き換えてはいけない](#深堀り1)
- [【深堀り②】使用メモリを計測してみる](#深堀り2)
- [【深堀り③】作ってすぐ捨てれば、メモリは少なくて済むのでは？](#深堀り3)
- [【深堀り④】共有したオブジェクトは GC で回収されない](#深堀り4)
- [【深堀り⑤】Singleton パターンとの違い](#深堀り5)
- [【深堀り⑥】複数のスレッドから使う場合](#深堀り6)
- [【深堀り⑦】GoF デザインパターンとの位置づけ](#深堀り7)

---

## 【具体例】

### シナリオ

> あなたは通販会社の倉庫システムの開発チームに所属しています。<br>
> 現状の倉庫システムでは、受注が入るたびに出荷ラベルを 1 枚作成し、その場で印刷しています。出荷ラベルには、届け先の住所・氏名、配送エリアの番号、配送会社（サンプルA運輸・サンプルB運輸・サンプルC運輸の 3 社）のロゴ画像が載っています。倉庫の作業者は、印刷された出荷ラベルを 1 枚ずつ見ながら商品を集めて梱包し、出荷ラベルを貼り付けます。<br>
> ある日、出荷場の担当者から「出荷ラベルが受注の入った順に印刷されるので、梱包が終わった荷物の配送エリアがばらばらになる。トラックに積み込む前に、荷物を配送エリアごとに手作業で並べ替えていて手間がかかる」と相談がありました。<br>
> そこであなたは、受注が入った時点では出荷ラベルを作成して保持しておくだけにし、出荷作業の開始時刻に、その日の出荷ラベルをまとめて配送エリアの順に並べ替えてから印刷する機能を追加することになりました。出荷ラベルの順に梱包すれば、荷物は最初から配送エリアの順に並びます。<br>
> なお、1 日の出荷件数はおよそ 1 万件です。

※実際の出荷ラベルの印刷では、ロゴ画像をファイルから読み込んだり、ラベルプリンターへ印刷データを送信したりする実装が必要ですが、本記事では Flyweight パターンの解説に集中するため、コンソールへの文字列出力のみとします。

### 既存コードの仕様

※実務では、次の `ShippingLabel` のようなエンティティクラスは `entity` パッケージなど専用のディレクトリに切り出すのが一般的です。しかし、本記事ではパッケージ構成を主題としないため `example` パッケージ直下にまとめています。

- `CarrierLogo`（既存クラス）

配送会社のロゴ画像を表すクラスです。<br>
コンストラクターで、配送会社名に対応するロゴ画像のデータを読み込みます。

| フィールド    | 型       | 説明             |
| ------------- | -------- | ---------------- |
| `carrierName` | `String` | 配送会社名       |
| `imageData`   | `byte[]` | ロゴ画像のデータ |

| メソッド | 引数 | 戻り値の型 | 説明                           |
| -------- | ---- | ---------- | ------------------------------ |
| `print`  | なし | `void`     | ロゴ画像をコンソールに出力する |

**`CarrierLogo.java`**

```java
package example;

public class CarrierLogo {
    private String carrierName;
    private byte[] imageData;

    public CarrierLogo(String carrierName) {
        this.carrierName = carrierName;
        this.imageData = new byte[10 * 1024];
    }

    public void print() {
        System.out.println("[" + carrierName + "のロゴ]");
    }
}
```

※ロゴ画像の読み込みは、10KB（`10 * 1024` バイト）の空のバイト配列を作成することで代用しています。

<br>

- `ShippingLabel`（既存クラス）

出荷ラベル 1 枚を表すクラスです。<br>
コンストラクターで、配送会社名に対応する `CarrierLogo` オブジェクトを作成して保持します。

| フィールド      | 型            | 説明                                             |
| --------------- | ------------- | ------------------------------------------------ |
| `recipientName` | `String`      | 届け先の氏名                                     |
| `address`       | `String`      | 届け先の住所                                     |
| `areaCode`      | `int`         | 配送エリアの番号（トラックに積み込む順番を表す） |
| `logo`          | `CarrierLogo` | 配送会社のロゴ画像                               |

| メソッド | 引数 | 戻り値の型 | 説明                                                       |
| -------- | ---- | ---------- | ---------------------------------------------------------- |
| `print`  | なし | `void`     | ロゴ画像と、配送エリアの番号・届け先をコンソールに出力する |

**`ShippingLabel.java`**

```java
package example;

public class ShippingLabel {
    private String recipientName;
    private String address;
    private int areaCode;
    private CarrierLogo logo;

    public ShippingLabel(String recipientName, String address, int areaCode, String carrierName) {
        this.recipientName = recipientName;
        this.address = address;
        this.areaCode = areaCode;
        this.logo = new CarrierLogo(carrierName);
    }

    public void print() {
        logo.print();
        System.out.println("エリア" + areaCode + "：" + address + " " + recipientName + " 様");
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
        // 受注が入るたびに、出荷ラベルを作成してその場で印刷している
        ShippingLabel label1 = new ShippingLabel("山田 太郎", "東京都千代田区", 2, "サンプルA運輸");
        label1.print();
        ShippingLabel label2 = new ShippingLabel("鈴木 花子", "神奈川県横浜市", 1, "サンプルB運輸");
        label2.print();
        ShippingLabel label3 = new ShippingLabel("佐藤 次郎", "東京都新宿区", 2, "サンプルC運輸");
        label3.print();
        ShippingLabel label4 = new ShippingLabel("田中 美咲", "神奈川県川崎市", 1, "サンプルA運輸");
        label4.print();
    }
}
```

**実行結果**

```
[サンプルA運輸のロゴ]
エリア2：東京都千代田区 山田 太郎 様
[サンプルB運輸のロゴ]
エリア1：神奈川県横浜市 鈴木 花子 様
[サンプルC運輸のロゴ]
エリア2：東京都新宿区 佐藤 次郎 様
[サンプルA運輸のロゴ]
エリア1：神奈川県川崎市 田中 美咲 様
```

受注の入った順に印刷されるため、配送エリアの番号が「2・1・2・1」とばらばらに並んでいます。

※ここで一旦読むのを止めて、ご自身でコーディングを行なってみてください。その後で、続きを読んでください。

## 好ましくない実装

では、シナリオに従い追加実装をしていきましょう。

真っ先に思いつくのは、出荷ラベルをまとめて保持する `ShippingLabelBatch` クラスを新しく作成し、受注が入るたびに作成した `ShippingLabel` オブジェクトを追加しておき、出荷作業の開始時刻に配送エリアの番号の順に並べ替えてから印刷する、という実装ではないでしょうか？

まず、`ShippingLabel` クラスに、並べ替えに使う配送エリアの番号を取得するメソッドを追加します。

**`ShippingLabel.java`**

```java
package example;

public class ShippingLabel {
    private String recipientName;
    private String address;
    private int areaCode;
    private CarrierLogo logo;

    public ShippingLabel(String recipientName, String address, int areaCode, String carrierName) {
        this.recipientName = recipientName;
        this.address = address;
        this.areaCode = areaCode;
        this.logo = new CarrierLogo(carrierName);
    }

    /* ここを追加（ここから） */
    public int getAreaCode() {
        return areaCode;
    }
    /* ここを追加（ここまで） */

    public void print() {
        logo.print();
        System.out.println("エリア" + areaCode + "：" + address + " " + recipientName + " 様");
    }
}
```

次に、出荷ラベルをまとめて保持し、一括で印刷する `ShippingLabelBatch` クラスを作成します。

**`ShippingLabelBatch.java`**

```java
package example;

public class ShippingLabelBatch {
    private List<ShippingLabel> labels = new ArrayList<>();

    public void add(ShippingLabel label) {
        labels.add(label);
    }

    public void printAll() {
        labels.sort(Comparator.comparingInt(ShippingLabel::getAreaCode));
        for (ShippingLabel label: labels) {
            label.print();
        }
    }
}
```

`add` メソッドにより、受注が入るたびに作成された出荷ラベルを、出荷作業の開始時刻まで `labels` フィールドに保持しています。<br>
`printAll` メソッドでは、保持している出荷ラベルを配送エリアの番号の順に並べ替えてから、1 枚ずつ印刷しています。

最後に、`Main` クラスを、出荷ラベルをその場で印刷せずに `ShippingLabelBatch` オブジェクトに追加し、最後にまとめて印刷するように修正します。

**`Main.java`**

```java
package example;

public class Main {
    public static void main(String[] args) {
        /* ここを修正（ここから） */
        ShippingLabelBatch batch = new ShippingLabelBatch();
        // 受注が入るたびに、出荷ラベルを作成して保持しておく
        batch.add(new ShippingLabel("山田 太郎", "東京都千代田区", 2, "サンプルA運輸"));
        batch.add(new ShippingLabel("鈴木 花子", "神奈川県横浜市", 1, "サンプルB運輸"));
        batch.add(new ShippingLabel("佐藤 次郎", "東京都新宿区", 2, "サンプルC運輸"));
        batch.add(new ShippingLabel("田中 美咲", "神奈川県川崎市", 1, "サンプルA運輸"));
        // 出荷作業の開始時刻に、配送エリアの順に並べ替えてまとめて印刷する
        batch.printAll();
        /* ここを修正（ここまで） */
    }
}
```

**実行結果**

```
[サンプルB運輸のロゴ]
エリア1：神奈川県横浜市 鈴木 花子 様
[サンプルA運輸のロゴ]
エリア1：神奈川県川崎市 田中 美咲 様
[サンプルA運輸のロゴ]
エリア2：東京都千代田区 山田 太郎 様
[サンプルC運輸のロゴ]
エリア2：東京都新宿区 佐藤 次郎 様
```

配送エリアの番号が「1・1・2・2」の順に並び、同じ配送エリアの中では受注の入った順に印刷されています。

コンパイルエラーがなく結果が出力されていることから、一見すると実装・動作確認ともに問題ないように見えます。

しかし、この実装には以下の問題点があります。

- 配送会社は 3 社しかないにもかかわらず、`ShippingLabel` オブジェクトを作成するたびに `CarrierLogo` オブジェクトも新しく作成しているため、中身がまったく同じロゴ画像のデータが、出荷ラベルの枚数分だけメモリに載る。既存コードでは出荷ラベルを 1 枚ずつ印刷して使い終わっていたが、今回は `ShippingLabelBatch` オブジェクトが出荷作業の開始時刻まで出荷ラベルを保持し続けるため、その間はロゴ画像のデータもすべてメモリに残り続ける。
    - その結果、使用メモリが出荷件数に比例して増え続け、セールなどで出荷件数が増えた日には、メモリ不足（`OutOfMemoryError`）で一括印刷そのものが止まってしまうリスクがある。

1 日の出荷件数はおよそ 1 万件なので、出荷作業の開始時刻には、ロゴ画像のデータだけで 10KB × 1 万枚 ＝ 約 100MB がメモリに載ることになります（→ [【深堀り②】使用メモリを計測してみる](#深堀り2)）。

## 正しい実装

では、好ましくない実装で挙げた問題点を解決するにはどうすればよいのでしょうか？

この問題を解決するのが **Flyweight パターン**です。<br>
Flyweight パターンは、中身が同じで何度も使われるオブジェクトを 1 つだけ作り、それを使う側どうしで共有するパターンです。本記事では、配送会社のロゴ画像を表す `CarrierLogo` オブジェクトを、同じ配送会社の出荷ラベルどうしで共有します。

まず、作成済みの `CarrierLogo` オブジェクトを管理するクラスから見ていきましょう。

**`CarrierLogoFactory.java`**

```java
package example;

public class CarrierLogoFactory {
    private static CarrierLogoFactory singleton = new CarrierLogoFactory();
    private Map<String, CarrierLogo> pool = new HashMap<>();

    private CarrierLogoFactory() {
    }

    public static CarrierLogoFactory getInstance() {
        return singleton;
    }

    public CarrierLogo getLogo(String carrierName) {
        CarrierLogo logo = pool.get(carrierName);
        if (logo == null) {
            logo = new CarrierLogo(carrierName);
            pool.put(carrierName, logo);
        }
        return logo;
    }
}
```

`CarrierLogoFactory` は新たに追加したクラスで、`pool` フィールドに、配送会社名をキーとして作成済みの `CarrierLogo` オブジェクトを保持しています。<br>
`getLogo` メソッドでは、指定された配送会社のロゴ画像が `pool` フィールドにあればそれを返し、なければ新しく作成して `pool` フィールドに登録してから返しています。これにより、同じ配送会社名で何度呼び出しても、返される `CarrierLogo` オブジェクトは常に同じになります。<br>
また、コンストラクターを `private` にし、`getInstance` メソッドで唯一の `CarrierLogoFactory` オブジェクトを返すようにしています（Singleton パターン）。`CarrierLogoFactory` オブジェクトが複数作られると、`pool` フィールドもその数だけでき、それぞれの `pool` フィールドに同じ配送会社のロゴ画像が作られてしまうためです。

次に、`ShippingLabel` クラスを、`CarrierLogo` オブジェクトを直接作成せず、`CarrierLogoFactory` オブジェクトから取得するように修正します。

**`ShippingLabel.java`**

```java
package example;

public class ShippingLabel {
    private String recipientName;
    private String address;
    private int areaCode;
    private CarrierLogo logo;

    public ShippingLabel(String recipientName, String address, int areaCode, String carrierName) {
        this.recipientName = recipientName;
        this.address = address;
        this.areaCode = areaCode;
        this.logo = CarrierLogoFactory.getInstance().getLogo(carrierName); // ←ここを修正
    }

    public int getAreaCode() {
        return areaCode;
    }

    public void print() {
        logo.print();
        System.out.println("エリア" + areaCode + "：" + address + " " + recipientName + " 様");
    }
}
```

ここで、出荷ラベルの情報のうち、共有しているのはロゴ画像だけで、届け先の氏名・住所と配送エリアの番号は共有していません。<br>
ロゴ画像は、同じ配送会社の出荷ラベルであれば、どの出荷ラベルから見ても同じ内容なので共有できます。一方、届け先の氏名・住所と配送エリアの番号は出荷ラベルごとに異なるため、これまでどおり `ShippingLabel` オブジェクトがそれぞれ保持しています。<br>
Flyweight パターンでは、このように共有できる情報と使う側ごとに異なる情報を分け、共有できる情報だけを 1 つのオブジェクトにまとめて共有します。

※GoF では、共有できる情報を「内部状態（intrinsic state）」、使う側ごとに異なる情報を「外部状態（extrinsic state）」と呼びます。

`CarrierLogo`・`ShippingLabelBatch` クラスに変更はありません。<br>
`Main` クラスにも変更はなく、好ましくない実装と同じく出荷ラベルを `ShippingLabelBatch` オブジェクトに追加し、最後にまとめて印刷しています。

**`Main.java`**

```java
package example;

public class Main {
    public static void main(String[] args) {
        ShippingLabelBatch batch = new ShippingLabelBatch();
        // 受注が入るたびに、出荷ラベルを作成して保持しておく
        batch.add(new ShippingLabel("山田 太郎", "東京都千代田区", 2, "サンプルA運輸"));
        batch.add(new ShippingLabel("鈴木 花子", "神奈川県横浜市", 1, "サンプルB運輸"));
        batch.add(new ShippingLabel("佐藤 次郎", "東京都新宿区", 2, "サンプルC運輸"));
        batch.add(new ShippingLabel("田中 美咲", "神奈川県川崎市", 1, "サンプルA運輸"));
        // 出荷作業の開始時刻に、配送エリアの順に並べ替えてまとめて印刷する
        batch.printAll();
    }
}
```

**実行結果**

```
[サンプルB運輸のロゴ]
エリア1：神奈川県横浜市 鈴木 花子 様
[サンプルA運輸のロゴ]
エリア1：神奈川県川崎市 田中 美咲 様
[サンプルA運輸のロゴ]
エリア2：東京都千代田区 山田 太郎 様
[サンプルC運輸のロゴ]
エリア2：東京都新宿区 佐藤 次郎 様
```

実行結果は、好ましくない実装とまったく同じになっています。<br>
ただし、`getLogo` メソッドの動きは異なります。山田 太郎 様・鈴木 花子 様・佐藤 次郎 様の出荷ラベルを作成したときは、それぞれの配送会社のロゴ画像がまだ `pool` フィールドにないため、`CarrierLogo` オブジェクトが新しく作成されます。一方、4 枚目の田中 美咲 様の出荷ラベルを作成したときは、サンプルA運輸のロゴ画像がすでに `pool` フィールドにあるため、新しく作成されず、山田 太郎 様の出荷ラベルと同じ `CarrierLogo` オブジェクトが返されます。

出荷ラベルを 1 万枚保持しても、共有している `CarrierLogo` オブジェクトは 3 個だけなので、メモリに載るロゴ画像のデータは 10KB × 3 社分 ＝ 約 30KB で済みます。

以上のような実装を行うと、以下のメリットがあります。

- 同じ配送会社の出荷ラベルは、`CarrierLogoFactory` オブジェクトから取得した同じ `CarrierLogo` オブジェクトを共有しているため、出荷ラベルを何枚保持しても、メモリに載るロゴ画像のデータは配送会社の数（3 社分）だけで済む。
    - その結果、出荷件数が増えても、ロゴ画像のデータによって使用メモリが増え続けることはなくなり、メモリ不足で一括印刷が止まってしまうリスクを減らせる。

## まとめ

正しい実装を振り返ると、出荷ラベルごとに作成していた `CarrierLogo` オブジェクトは、`CarrierLogoFactory` クラスが配送会社ごとに 1 つだけ作成して管理するようになり、同じ配送会社の出荷ラベルはそのオブジェクトを共有しています。一方、出荷ラベルごとに異なる届け先の氏名・住所と配送エリアの番号は、これまでどおり `ShippingLabel` オブジェクトがそれぞれ保持しています。<br>
このように Flyweight パターンは、共有できる情報と使う側ごとに異なる情報を分け、共有できる情報を持つオブジェクトを 1 つだけ作って使い回すことで、大量のオブジェクトを同時に使う場面でもメモリの使用量を抑える設計パターンです。

本記事の内容はここまでとなります。

以降は「もう少し深く知りたい」という方向けの補足となります。今回学んだパターンに繋がる設計原則や、実務で役立つ背景知識について触れています。

---

<a id="深堀り1"></a>

## 【深堀り①】共有したオブジェクトを書き換えてはいけない

正しい実装では、サンプルA運輸の出荷ラベルは、すべて同じ `CarrierLogo` オブジェクトを共有しています。<br>
そのため、例えば「1 枚の出荷ラベルだけ、キャンペーン用のロゴ画像に差し替えたい」という要望に対して、`CarrierLogo` クラスにロゴ画像を差し替えるメソッドを追加し、次のように書き換えたとします。

```java
// 1 枚の出荷ラベルだけ、キャンペーン用のロゴ画像に差し替えたつもりで書き換えている
label.getLogo().setImageData(campaignImageData);
```

書き換えた `CarrierLogo` オブジェクトは、サンプルA運輸のすべての出荷ラベルが共有しているため、1 枚だけのつもりが、1 日分のサンプルA運輸の出荷ラベルがすべてキャンペーン用のロゴ画像で印刷されてしまいます。<br>
しかも、書き換えたコードと、ロゴ画像が変わってしまった出荷ラベルを作成・印刷するコードは離れた場所にあるため、原因を突き止めるのにも手間がかかります。

このように、共有するオブジェクトの中身を書き換えると、そのオブジェクトを共有しているすべての使う側に影響が及びます。そのため、Flyweight パターンで共有するオブジェクトには、作成した後に中身を変更するメソッドを用意しないようにします。今回の `CarrierLogo` クラスも、コンストラクターで配送会社名とロゴ画像のデータを設定した後は、それらを変更するメソッドを持っていません。<br>
1 枚の出荷ラベルだけ別のロゴ画像にしたい場合は、共有している `CarrierLogo` オブジェクトを書き換えるのではなく、キャンペーン用のロゴ画像を別の `CarrierLogo` オブジェクトとして `CarrierLogoFactory` オブジェクトから取得し、その出荷ラベルに持たせるようにしましょう。

<a id="深堀り2"></a>

## 【深堀り②】使用メモリを計測してみる

好ましくない実装と正しい実装では、メモリに載るロゴ画像のデータの量を、ロゴ画像 1 枚あたり 10KB として計算しました。<br>
ここでは、1 日の出荷件数にあたる 1 万枚の出荷ラベルを保持したときの使用メモリを、実際に計測してみます。

**`MemoryCheck.java`**

```java
package example;

public class MemoryCheck {
    public static void main(String[] args) {
        String[] carrierNames = {"サンプルA運輸", "サンプルB運輸", "サンプルC運輸"};
        ShippingLabelBatch batch = new ShippingLabelBatch();
        for (int i = 0; i < 10000; i++) {
            batch.add(new ShippingLabel("届け先" + i, "住所" + i, i % 10, carrierNames[i % 3]));
        }

        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        System.out.println("使用メモリ：" + usedMemory / (1024 * 1024) + "MB");
    }
}
```

`for` 文により、3 社の配送会社を順番に割り当てた 1 万枚の出荷ラベルを、`ShippingLabelBatch` オブジェクトに保持しています。出荷作業の開始時刻を待っている状態を再現するため、出荷ラベルは保持したまま計測しています。<br>
`for` 文の後では、ガベージコレクションを実行してから、使用メモリを計算しています。

Java では、どこからも参照されなくなったオブジェクトを JVM が自動で回収し、メモリを空けます。この仕組みをガベージコレクション（GC）と呼びます。例えば、`ShippingLabelBatch` オブジェクトに追加した出荷ラベルは、`labels` フィールドから参照されているため回収されません。一方、どこにも保持せずに捨てた出荷ラベルは、どこからも参照されていないため回収の対象になります。<br>
GC は通常、JVM が必要と判断したタイミングで実行されます。`runtime.gc()` は GC の実行を JVM に依頼するメソッドで、呼び出したからといって、使われていないオブジェクトがすべて回収されるとは限りません。それでも、計測の前に呼び出しておくことで、もう使われていないオブジェクトが計測値に含まれにくくなります。<br>
`runtime.totalMemory()` は JVM が確保しているメモリの量を、`runtime.freeMemory()` はそのうちまだ使われていないメモリの量を返します。その差を、使用メモリとして MB 単位で出力しています。

**実行結果（好ましくない実装）**

```
使用メモリ：101MB
```

**実行結果（正しい実装）**

```
使用メモリ：2MB
```

※使用メモリの値は、実行環境によって異なります。

好ましくない実装の約 101MB のうち、約 100MB は 1 万個の `CarrierLogo` オブジェクトが持つロゴ画像のデータで、計算どおりの結果になっています。<br>
正しい実装では、ロゴ画像のデータは 3 社分（約 30KB）しかありません。約 2MB の使用メモリのほとんどは、1 万個の `ShippingLabel` オブジェクトが保持している届け先の氏名・住所などのデータや、JVM 自体が使用しているメモリです。

<a id="深堀り3"></a>

## 【深堀り③】作ってすぐ捨てれば、メモリは少なくて済むのでは？

`MemoryCheck` クラスでは、1 万枚の出荷ラベルを `ShippingLabelBatch` オブジェクトに保持したまま、使用メモリを計測しました。<br>
では、出荷ラベルを保持せずに、作ってすぐ捨てた場合はどうなるでしょうか？ `MemoryCheck` クラスの `for` 文を、次のように書き換えて計測してみます。

```java
for (int i = 0; i < 10000; i++) {
    // 作成した出荷ラベルを、どこにも保持せずに捨てている
    new ShippingLabel("届け先" + i, "住所" + i, i % 10, carrierNames[i % 3]);
}
```

**実行結果（好ましくない実装）**

```
使用メモリ：1MB
```

**実行結果（正しい実装）**

```
使用メモリ：1MB
```

※使用メモリの値は、実行環境によって異なります。

ロゴ画像を共有していない好ましくない実装でも、正しい実装と同じく使用メモリは約 1MB になり、差がなくなりました。<br>
作成した `ShippingLabel` オブジェクトをどこからも参照していないため、`runtime.gc()` を呼び出した時点で、もう使われていないオブジェクトとして、`ShippingLabel` オブジェクトも、それが持つ `CarrierLogo` オブジェクトも、すべて回収されたからです。

この結果を見ると、「それなら、Flyweight パターンを使わなくても、出荷ラベルを作ってすぐ捨てればメモリは少なくて済むのでは？」と思うかもしれません。<br>
しかし、捨てた出荷ラベルはもう使えません。例えば、計測した後に出荷ラベルを印刷しようとしても、どの `ShippingLabel` オブジェクトも参照が残っていないため、`print` メソッドを呼び出すことができません。今回のシナリオでは、出荷ラベルを配送エリアの順に並べ替えるために、その日の出荷ラベルをすべて同時に保持しておく必要があります。つまり、作ってすぐ捨てる実装は、そもそも追加要件を満たしていません。<br>
既存コードのように 1 枚ずつ作って印刷し、すぐに使い終わる場合は、メモリに載るロゴ画像は常に 1 枚分程度なので、Flyweight パターンを使わなくてもメモリが問題になることはありません。

このように、Flyweight パターンの効果を比べるときは、大量のオブジェクトを「使っている最中」の使用メモリを比べる必要があります。計測した時点で、どのオブジェクトがまだ参照されているかによって、GC で回収されるオブジェクトが変わり、計測結果も変わります。使用メモリを計測するときは、計測する時点で、実際に使っている状態のオブジェクトが参照されたままになっているかを確認しましょう。

<a id="深堀り4"></a>

## 【深堀り④】共有したオブジェクトは GC で回収されない

正しい実装の `CarrierLogoFactory` クラスでは、`pool` フィールドが作成済みの `CarrierLogo` オブジェクトを保持し続けています。<br>
そのため、出荷ラベルをすべて印刷し終えて、`ShippingLabelBatch` オブジェクトや `ShippingLabel` オブジェクトが使われなくなっても、`CarrierLogo` オブジェクトは `pool` フィールドから参照されたままなので、GC で回収されません。例えば、サンプルC運輸との契約が終了し、そのロゴ画像が二度と使われなくなっても、プログラムが終了するまでメモリに残り続けます。

今回のシナリオでは、共有するロゴ画像は配送会社の数（3 社分、合計約 30KB）しかないため、メモリに残り続けても問題になりません。<br>
しかし、商品ごとの商品画像のように、共有するものの種類が数万にのぼる場合は、一度でも使われた種類がすべて `pool` フィールドに残り続けるため、使用メモリが増える一方になるリスクがあります。

このように、Flyweight パターンは、共有するものの種類が少なく、同じものが大量に使われる場面に向いています。共有するものの種類が多い場合は、しばらく使われていないものを `pool` フィールドから削除する仕組みを用意するなど、保持し続けるものを絞り込む工夫が必要になります。

<a id="深堀り5"></a>

## 【深堀り⑤】Singleton パターンとの違い

正しい実装では、`CarrierLogoFactory` クラスを Singleton パターンにしたうえで、`CarrierLogo` オブジェクトを Flyweight パターンで共有しました。<br>
どちらも「同じオブジェクトを使い回す」パターンなので混同しやすいですが、使い回す単位が異なります。

- Singleton パターンは、1 つのクラスにつき、オブジェクトを 1 つだけ作る。`CarrierLogoFactory` クラスのオブジェクトは、アプリケーション全体で 1 つだけ作られる。
- Flyweight パターンは、共有できる情報の種類ごとに、オブジェクトを 1 つだけ作る。`CarrierLogo` クラスのオブジェクトは、配送会社の数（3 社分）だけ作られる。

今回の実装でこの 2 つを組み合わせているのは、Flyweight パターンで共有するためには、作成済みのオブジェクトを管理する `pool` フィールドが、アプリケーション全体で 1 つでなければならないからです。`pool` フィールドが複数あると、同じ配送会社のロゴ画像がそれぞれの `pool` フィールドに作られ、共有が崩れてしまいます。

ただし、`pool` フィールドを 1 つにする手段は、Singleton パターンに限りません。実務では、Spring Framework などのフレームワークに `CarrierLogoFactory` オブジェクトを 1 つだけ作成・管理させ、それを使うクラスに渡す方法（DI：依存性の注入）もよく使われます。大切なのは Singleton パターンを使うこと自体ではなく、共有するオブジェクトを管理する `pool` フィールドが、アプリケーション全体で 1 つになっていることです。

<a id="深堀り6"></a>

## 【深堀り⑥】複数のスレッドから使う場合

正しい実装の `CarrierLogoFactory` クラスの `getLogo` メソッドでは、`pool` フィールドに配送会社のロゴ画像があるかを確認し、なければ新しく作成して登録しています。<br>
本記事の `Main` クラスは 1 つのスレッドで順番に実行されるため、この実装で問題ありません。しかし、実際の倉庫システムでは、通販サイトから同時に入った複数の受注を、それぞれ別のスレッドで並行して処理することがよくあります。`CarrierLogoFactory` オブジェクトはアプリケーション全体で 1 つだけなので、どのスレッドも同じ `pool` フィールドを使います。

例えば、サンプルA運輸の受注が 2 件同時に入り、2 つのスレッドがほぼ同時に `getLogo` メソッドを呼び出したとします。

1. スレッド 1 が `pool.get(carrierName)` を実行し、サンプルA運輸のロゴ画像はまだ登録されていないため `null` を受け取る
2. スレッド 1 が `CarrierLogo` オブジェクトを作成して登録する前に、スレッド 2 も `pool.get(carrierName)` を実行し、同じく `null` を受け取る
3. 2 つのスレッドが、それぞれサンプルA運輸の `CarrierLogo` オブジェクトを作成し、`pool` フィールドに登録する

その結果、サンプルA運輸の `CarrierLogo` オブジェクトが 2 つ作られ、共有されるはずのロゴ画像が重複してしまいます。さらに、`HashMap` クラスは複数のスレッドから同時に書き込まれることを想定していないため、`put` メソッドが同時に実行されると、`pool` フィールドの中身が壊れるリスクもあります。

これを防ぐには、`getLogo` メソッドに `synchronized` を付けます。

```java
public synchronized CarrierLogo getLogo(String carrierName) { // ←ここを修正
    CarrierLogo logo = pool.get(carrierName);
    if (logo == null) {
        logo = new CarrierLogo(carrierName);
        pool.put(carrierName, logo);
    }
    return logo;
}
```

`synchronized` は直訳すると「同期」ですが、「同期処理・非同期処理」の同期とは意味が異なります。`synchronized` を付けたメソッドには、同時に 1 つのスレッドしか入れません。あるスレッドがメソッドを実行している間、ほかのスレッドはそのスレッドがメソッドを抜けるまで待たされます（ロック）。<br>
これにより、スレッド 2 は、スレッド 1 がサンプルA運輸の `CarrierLogo` オブジェクトを `pool` フィールドに登録し終えてから `pool.get(carrierName)` を実行するため、登録済みの `CarrierLogo` オブジェクトを受け取れます。

なお、本記事の `Main` クラスのように 1 つのスレッドで実行する場合は、`synchronized` を付けても付けなくても結果は変わりません。`CarrierLogoFactory` クラスのように、共有するオブジェクトを管理するクラスを複数のスレッドから使う可能性がある場合は、`pool` フィールドの確認と登録が同時に行われないようにしておきましょう。

※`HashMap` クラスの代わりに `ConcurrentHashMap` クラスを使い、`computeIfAbsent` メソッドで確認と登録をまとめて行う方法もあります。

<a id="深堀り7"></a>

## 【深堀り⑦】GoF デザインパターンとの位置づけ

今回使った Flyweight パターンは、GoF（Gang of Four）の 23 のデザインパターンのうち「構造パターン」に分類されます。<br>
詳しくは「GoF」で検索してみてください。
