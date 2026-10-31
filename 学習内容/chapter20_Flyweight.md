# Flyweight（フライウェイト）パターン ― 同じオブジェクトを共有してメモリを節約する

次のような経験をしたことはありませんか？

> 同じ内容のデータを持つオブジェクトを、使うたびに作っては捨てていた。ところが、それを大量にまとめて持つ必要が出てきた途端、同じデータがオブジェクトの数だけメモリに積み上がり、プログラムが止まりかけてしまった。

この記事では、倉庫システムで出荷ラベルを一括印刷するシナリオを通して、Flyweight パターンがこの問題をどのように解決するかを紹介します。

## 目次

- [【具体例】](#具体例)
    - [シナリオ](#シナリオ)
    - [既存コードの仕様](#既存コードの仕様)
- [好ましくない実装](#好ましくない実装)
- [正しい実装](#正しい実装)
- [まとめ](#まとめ)
- [【深堀り①】共有したオブジェクトを書き換えてはいけない](#深堀り1)
- [【深堀り②】使用メモリを計測してみる](#深堀り2)
    - [ガベージコレクションについて](#ガベージコレクションについて)
- [【深堀り③】作ってすぐ捨てれば、メモリは少なくて済むのでは？](#深堀り3)
- [【深堀り④】共有したオブジェクトは GC で回収されない](#深堀り4)
- [【深堀り⑤】Singleton パターンとの違い](#深堀り5)
- [【深堀り⑥】複数のスレッドから使う場合](#深堀り6)
- [【深堀り⑦】GoF デザインパターンとの位置づけ](#深堀り7)

---

## 【具体例】

### シナリオ

> あなたは通販会社の倉庫システムの開発チームに所属しています。<br>
> 現状の倉庫システムでは、受注が入るたびに、届け先や配送会社などの出荷データをデータベース（DB）に保存し、出荷ラベルを 1 枚作成した後すぐに印刷しています。出荷ラベルには、届け先の住所・氏名、配送エリア（同じトラックで配送する地域のまとまり）の番号、配送会社（サンプルA運輸・サンプルB運輸・サンプルC運輸の 3 社）のロゴ画像が載っています。倉庫は梱包する区域と出荷する区域が分かれています。毎朝、梱包を担当する作業者は、前日に印刷された約 1 万枚の出荷ラベルを、印刷された順に 1 枚ずつ取り、その注文の商品をまとめて箱に詰めて出荷ラベルを貼り付けます。梱包が終わった荷物から順に出荷する区域に運ばれます。<br>
> ある日、出荷する区域の担当者から「配送エリアの違う荷物が混ざって届くので、トラックに積み込む前に手作業で配送エリアごとに分けていて手間がかかる」という相談がありました。調べてみると、受注の入った順に出荷ラベルが印刷されるため、荷物も受注の入った順に梱包されて運ばれていることが原因であるとわかりました。<br>
> そこであなたは、受注が入った時点では印刷せず、梱包を始める前に、DB に保存された前日分の出荷データを配送エリアの順に並べて出荷ラベルを作成し、まとめて印刷する機能を追加することになりました。ただし、ラベルプリンターは送信を受けるたびに印刷の準備を行うため、1 枚ずつ送ると梱包を始めるまでに時間がかかってしまいます。そこで、並べ替えた出荷ラベルを 1 回でまとめて送信します。

※実際の出荷ラベルの印刷では、DB から出荷データを読み込んだり、ファイルからロゴ画像を読み込んだり、ラベルプリンターへ印刷データを送信したりする実装が必要ですが、本記事では Flyweight パターンの解説に集中するため、コンソールへの文字列出力のみとします。

### 既存コードの仕様

※実務では、次の `Shipment` のようなエンティティクラスは `entity` パッケージなど専用のディレクトリに切り出すのが一般的です。しかし、本記事ではパッケージ構成を主題としないため `example` パッケージ直下にまとめています。

- `Shipment`（既存クラス）

出荷データ 1 件を表すクラスです。

| フィールド      | 型       | 説明             |
| --------------- | -------- | ---------------- |
| `address`       | `String` | 届け先の住所     |
| `recipientName` | `String` | 届け先の氏名     |
| `areaCode`      | `int`    | 配送エリアの番号 |
| `carrierName`   | `String` | 配送会社名       |

| メソッド           | 引数 | 戻り値の型 | 説明                       |
| ------------------ | ---- | ---------- | -------------------------- |
| `getAddress`       | なし | `String`   | 届け先の住所を取得する     |
| `getRecipientName` | なし | `String`   | 届け先の氏名を取得する     |
| `getAreaCode`      | なし | `int`      | 配送エリアの番号を取得する |
| `getCarrierName`   | なし | `String`   | 配送会社名を取得する       |

**`Shipment.java`**

```java
package example;

public class Shipment {
    private String address;
    private String recipientName;
    private int areaCode;
    private String carrierName;

    public Shipment(String address, String recipientName, int areaCode, String carrierName) {
        this.address = address;
        this.recipientName = recipientName;
        this.areaCode = areaCode;
        this.carrierName = carrierName;
    }

    public String getAddress() {
        return address;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public int getAreaCode() {
        return areaCode;
    }

    public String getCarrierName() {
        return carrierName;
    }
}
```

<br>

- `ShipmentRepository`（既存クラス）

出荷データを DB に保存するクラスです。<br>
ただし、DB への保存は、`List` への追加で代用しています。

| フィールド  | 型               | 説明                       |
| ----------- | ---------------- | -------------------------- |
| `shipments` | `List<Shipment>` | 保存された出荷データの一覧 |

| メソッド | 引数                | 戻り値の型 | 説明                 |
| -------- | ------------------- | ---------- | -------------------- |
| `save`   | `Shipment shipment` | `void`     | 出荷データを保存する |

**`ShipmentRepository.java`**

```java
package example;

public class ShipmentRepository {
    private List<Shipment> shipments = new ArrayList<>();

    public void save(Shipment shipment) {
        shipments.add(shipment);
    }
}
```

<br>

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
コンストラクターで、出荷データから届け先の住所・氏名と配送エリアの番号を取り出して保持します。また、出荷データの配送会社名に対応する `CarrierLogo` オブジェクトを作成して保持します。

| フィールド      | 型            | 説明               |
| --------------- | ------------- | ------------------ |
| `address`       | `String`      | 届け先の住所       |
| `recipientName` | `String`      | 届け先の氏名       |
| `areaCode`      | `int`         | 配送エリアの番号   |
| `logo`          | `CarrierLogo` | 配送会社のロゴ画像 |

| メソッド | 引数 | 戻り値の型 | 説明                                                                   |
| -------- | ---- | ---------- | ---------------------------------------------------------------------- |
| `print`  | なし | `void`     | ロゴ画像と、配送エリアの番号・届け先の住所と氏名をコンソールに出力する |

**`ShippingLabel.java`**

```java
package example;

public class ShippingLabel {
    private String address;
    private String recipientName;
    private int areaCode;
    private CarrierLogo logo;

    public ShippingLabel(Shipment shipment) {
        this.address = shipment.getAddress();
        this.recipientName = shipment.getRecipientName();
        this.areaCode = shipment.getAreaCode();
        this.logo = new CarrierLogo(shipment.getCarrierName());
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
        ShipmentRepository repository = new ShipmentRepository();
        // 受注が入るたびに、その場で出荷ラベルを印刷している
        Shipment shipment1 = new Shipment("東京都千代田区", "山田 太郎", 2, "サンプルA運輸");
        repository.save(shipment1);
        new ShippingLabel(shipment1).print();
        Shipment shipment2 = new Shipment("神奈川県横浜市", "鈴木 花子", 1, "サンプルB運輸");
        repository.save(shipment2);
        new ShippingLabel(shipment2).print();
        Shipment shipment3 = new Shipment("埼玉県さいたま市", "佐藤 次郎", 3, "サンプルC運輸");
        repository.save(shipment3);
        new ShippingLabel(shipment3).print();
        Shipment shipment4 = new Shipment("東京都新宿区", "田中 美咲", 2, "サンプルA運輸");
        repository.save(shipment4);
        new ShippingLabel(shipment4).print();
        Shipment shipment5 = new Shipment("千葉県千葉市", "高橋 健一", 4, "サンプルA運輸");
        repository.save(shipment5);
        new ShippingLabel(shipment5).print();
        Shipment shipment6 = new Shipment("神奈川県川崎市", "伊藤 由美", 1, "サンプルB運輸");
        repository.save(shipment6);
        new ShippingLabel(shipment6).print();
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
エリア3：埼玉県さいたま市 佐藤 次郎 様
[サンプルA運輸のロゴ]
エリア2：東京都新宿区 田中 美咲 様
[サンプルA運輸のロゴ]
エリア4：千葉県千葉市 高橋 健一 様
[サンプルB運輸のロゴ]
エリア1：神奈川県川崎市 伊藤 由美 様
```

配送エリアの番号が「2・1・3・2・4・1」とばらばらに並んでいます。

※ここで一旦読むのを止めて、ご自身でコーディングを行なってみてください。その後で、続きを読んでください。

## 好ましくない実装

では、シナリオに従い追加実装をしていきましょう。

真っ先に思いつくのは、DB から前日分の出荷データを配送エリアの順に並べて取得し、そこから作成した出荷ラベルをリストに保持しておき、まとめて印刷する、という実装ではないでしょうか？

**`ShipmentRepository.java`**

```java
package example;

public class ShipmentRepository {
    private List<Shipment> shipments = new ArrayList<>();

    public void save(Shipment shipment) {
        shipments.add(shipment);
    }

    /* ここを追加（ここから） */
    public List<Shipment> findAllSortedByAreaCode() {
        List<Shipment> sorted = new ArrayList<>(shipments);
        sorted.sort(Comparator.comparingInt(Shipment::getAreaCode));
        return sorted;
    }
    /* ここを追加（ここまで） */
}
```

※DB での並べ替え（SQL の `ORDER BY` 句）は、`List` の並べ替えで代用しています。また、前日分への絞り込み（SQL の `WHERE` 句）は省略しています。

**`Main.java`**

```java
package example;

public class Main {
    public static void main(String[] args) {
        ShipmentRepository repository = new ShipmentRepository();
        /* ここを修正（ここから） */
        // 受注が入ったときは、出荷データを DB に保存するだけにしておく
        repository.save(new Shipment("東京都千代田区", "山田 太郎", 2, "サンプルA運輸"));
        repository.save(new Shipment("神奈川県横浜市", "鈴木 花子", 1, "サンプルB運輸"));
        repository.save(new Shipment("埼玉県さいたま市", "佐藤 次郎", 3, "サンプルC運輸"));
        repository.save(new Shipment("東京都新宿区", "田中 美咲", 2, "サンプルA運輸"));
        repository.save(new Shipment("千葉県千葉市", "高橋 健一", 4, "サンプルA運輸"));
        repository.save(new Shipment("神奈川県川崎市", "伊藤 由美", 1, "サンプルB運輸"));

        // 梱包を始める前に、配送エリアの順に並べた前日分の出荷データから出荷ラベルを作成し、まとめて送信するまで保持しておく
        List<ShippingLabel> labels = new ArrayList<>();
        for (Shipment shipment: repository.findAllSortedByAreaCode()) {
            labels.add(new ShippingLabel(shipment));
        }

        // まとめて印刷する
        for (ShippingLabel label: labels) {
            label.print();
        }
        /* ここを修正（ここまで） */
    }
}
```

※ラベルプリンターへのまとめての送信は、保持している出荷ラベルを順にコンソールへ出力することで代用しています。

**実行結果**

```
[サンプルB運輸のロゴ]
エリア1：神奈川県横浜市 鈴木 花子 様
[サンプルB運輸のロゴ]
エリア1：神奈川県川崎市 伊藤 由美 様
[サンプルA運輸のロゴ]
エリア2：東京都千代田区 山田 太郎 様
[サンプルA運輸のロゴ]
エリア2：東京都新宿区 田中 美咲 様
[サンプルC運輸のロゴ]
エリア3：埼玉県さいたま市 佐藤 次郎 様
[サンプルA運輸のロゴ]
エリア4：千葉県千葉市 高橋 健一 様
```

配送エリアの番号が「1・1・2・2・3・4」の順に並び、同じ配送エリアの中では受注の入った順に印刷されています。

コンパイルエラーがなく結果が出力されていることから、一見すると実装・動作確認ともに問題ないように見えます。

しかし、この実装には以下の問題点があります。

- 既存コードの 1 枚ずつ印刷する実装から、並べ替えた出荷ラベルを 1 回でまとめて送信する実装に修正したため、`labels` リストが前日分の出荷ラベルをすべて保持する必要がある。この `labels` リストに追加する `ShippingLabel` オブジェクトを作成するたびに、`CarrierLogo` オブジェクトも新しく作成しているため、配送会社は 3 社しかないにもかかわらず、中身がまったく同じロゴ画像のデータが、出荷ラベルの枚数分だけメモリに載るうえ、印刷するまですべて残り続ける（→ [【深堀り③】作ってすぐ捨てれば、メモリは少なくて済むのでは？](#深堀り3)）。
    - その結果、使用メモリが出荷件数に比例して増え続け、セールなどで出荷件数が増えた日には、メモリ不足（`OutOfMemoryError`）で一括印刷そのものが止まってしまうリスクがある。

ここで、1 日の出荷件数はおよそ 1 万件なので、まとめて印刷する時点では、ロゴ画像のデータだけで 10KB × 1 万枚 ＝ 約 100MB がメモリに載ることになります（→ [【深堀り②】使用メモリを計測してみる](#深堀り2)）。

## 正しい実装

では、好ましくない実装で挙げた問題点を解決するにはどうすればよいのでしょうか？

この問題を解決するのが **Flyweight パターン**です。<br>
Flyweight パターンは、中身が同じで何度も使われるオブジェクトを 1 つだけ作って、それを使う側同士で共有するパターンです。本記事では、配送会社のロゴ画像を表す `CarrierLogo` オブジェクトを、配送会社が同じである出荷ラベルの間で共有します。

まず、中身が同じで何度も使われるオブジェクト（`CarrierLogo`）を管理するクラスから見ていきましょう。

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
`getLogo` メソッドでは、指定された配送会社のロゴ画像が `pool` フィールドにあればそれを返し、なければ新しく作成して `pool` フィールドに登録してから返しています。これにより、引数で渡された配送会社名が同じであれば、常に同じ `CarrierLogo` オブジェクトが返されます。<br>
また、`CarrierLogoFactory` オブジェクトが複数作られてしまうと、`pool` フィールドもその数だけできます。その結果、同じ配送会社のロゴ画像がそれぞれの `pool` フィールドに登録され、出荷ラベルの間で共有できなくなります。そのため、コンストラクターを `private` にし、`getInstance` メソッドで唯一の `CarrierLogoFactory` オブジェクトを返す Singleton パターンにしています（→ [【深堀り⑤】Singleton パターンとの違い](#深堀り5)）。

次に、`CarrierLogo` オブジェクトを使う側のクラスを見ていきましょう。

**`ShippingLabel.java`**

```java
package example;

public class ShippingLabel {
    private String address;
    private String recipientName;
    private int areaCode;
    private CarrierLogo logo;

    public ShippingLabel(Shipment shipment) {
        this.address = shipment.getAddress();
        this.recipientName = shipment.getRecipientName();
        this.areaCode = shipment.getAreaCode();
        this.logo = CarrierLogoFactory.getInstance().getLogo(shipment.getCarrierName()); // ←ここを修正
    }

    public void print() {
        logo.print();
        System.out.println("エリア" + areaCode + "：" + address + " " + recipientName + " 様");
    }
}
```

`ShippingLabel` クラスを振り返ると、`CarrierLogoFactory` オブジェクトから `CarrierLogo` オブジェクトを取得することにより、配送会社が同じである出荷ラベルの間で、同じ `CarrierLogo` オブジェクトを共有しています。<br>
ロゴ画像は、同じ配送会社の出荷ラベルであれば同じ画像なので共有できます（→ [【深堀り①】共有したオブジェクトを書き換えてはいけない](#深堀り1)）。一方、届け先の住所・氏名と配送エリアの番号は出荷ラベルごとに異なるため共有せず、これまでどおり `ShippingLabel` オブジェクトがそれぞれ保持しています。

このように、Flyweight パターンでは、共有できる情報と使う側ごとに異なる情報を分け、共有できる情報だけを 1 つのオブジェクトにまとめて共有します。<br>
※GoF では、共有できる情報を「内部状態（intrinsic state）」、使う側ごとに異なる情報を「外部状態（extrinsic state）」と呼びます。

`Shipment`・`CarrierLogo` クラスに変更はありません。

最後に、`ShipmentRepository` クラスと実行クラスを見ていきましょう。

**`ShipmentRepository.java`**

```java
package example;

public class ShipmentRepository {
    private List<Shipment> shipments = new ArrayList<>();

    public void save(Shipment shipment) {
        shipments.add(shipment);
    }

    /* ここを追加（ここから） */
    public List<Shipment> findAllSortedByAreaCode() {
        List<Shipment> sorted = new ArrayList<>(shipments);
        sorted.sort(Comparator.comparingInt(Shipment::getAreaCode));
        return sorted;
    }
    /* ここを追加（ここまで） */
}
```

**`Main.java`**

```java
package example;

public class Main {
    public static void main(String[] args) {
        ShipmentRepository repository = new ShipmentRepository();
        /* ここを修正（ここから） */
        // 受注が入ったときは、出荷データを DB に保存するだけにしておく
        repository.save(new Shipment("東京都千代田区", "山田 太郎", 2, "サンプルA運輸"));
        repository.save(new Shipment("神奈川県横浜市", "鈴木 花子", 1, "サンプルB運輸"));
        repository.save(new Shipment("埼玉県さいたま市", "佐藤 次郎", 3, "サンプルC運輸"));
        repository.save(new Shipment("東京都新宿区", "田中 美咲", 2, "サンプルA運輸"));
        repository.save(new Shipment("千葉県千葉市", "高橋 健一", 4, "サンプルA運輸"));
        repository.save(new Shipment("神奈川県川崎市", "伊藤 由美", 1, "サンプルB運輸"));

        // 梱包を始める前に、配送エリアの順に並べた前日分の出荷データから出荷ラベルを作成し、まとめて送信するまで保持しておく
        List<ShippingLabel> labels = new ArrayList<>();
        for (Shipment shipment: repository.findAllSortedByAreaCode()) {
            labels.add(new ShippingLabel(shipment));
        }

        // まとめて印刷する
        for (ShippingLabel label: labels) {
            label.print();
        }
        /* ここを修正（ここまで） */
    }
}
```

**実行結果**

```
[サンプルB運輸のロゴ]
エリア1：神奈川県横浜市 鈴木 花子 様
[サンプルB運輸のロゴ]
エリア1：神奈川県川崎市 伊藤 由美 様
[サンプルA運輸のロゴ]
エリア2：東京都千代田区 山田 太郎 様
[サンプルA運輸のロゴ]
エリア2：東京都新宿区 田中 美咲 様
[サンプルC運輸のロゴ]
エリア3：埼玉県さいたま市 佐藤 次郎 様
[サンプルA運輸のロゴ]
エリア4：千葉県千葉市 高橋 健一 様
```

`ShipmentRepository`・`Main` クラスは好ましくない実装と同じです。

実行結果は、好ましくない実装とまったく同じになっています。

以上のような実装を行うと、以下のメリットがあります。

- `CarrierLogo` オブジェクトを新しく作成するのは、配送会社ごとに最初の出荷ラベルを作成するときだけになる。また、同じ配送会社の 2 枚目以降の出荷ラベルは、`CarrierLogoFactory` オブジェクトから返される作成済みの `CarrierLogo` オブジェクトを共有することになる。そのため、出荷ラベルを何枚保持しても、メモリに載るロゴ画像のデータは配送会社の数（3 社分）だけで済む。
    - その結果、出荷件数のみが増えたとしても、ロゴ画像のデータが使うメモリは一定のままなので、メモリ不足（`OutOfMemoryError`）により一括印刷が止まってしまうリスクを減らせる。

ここで、1 日の出荷件数がおよそ 1 万件でも、まとめて印刷する時点でメモリに載るロゴ画像のデータは、10KB × 3 社分 ＝ 約 30KB で済みます。

## まとめ

正しい実装を振り返ると、出荷ラベルごとに作成していたロゴ画像（`CarrierLogo` オブジェクト）は、`CarrierLogoFactory` クラスが配送会社ごとに 1 つだけ作成して管理するようになり、同じ配送会社の出荷ラベルはそのオブジェクトを共有しています。一方、出荷ラベルごとに異なる届け先の住所・氏名と配送エリアの番号は、これまでどおり `ShippingLabel` オブジェクトがそれぞれ保持しています。<br>
このように Flyweight パターンは、共有できる情報と使う側ごとに異なる情報を分け、共有できる情報を持つオブジェクトを 1 つだけ作って共有することで、大量のオブジェクトを同時に使う場面でもメモリの使用量を抑える設計パターンです。

本記事の内容はここまでとなります。

以降は「もう少し深く知りたい」という方向けの補足となります。今回学んだパターンに繋がる設計原則や、実務で役立つ背景知識について触れています。

---

<a id="深堀り1"></a>

## 【深堀り①】共有したオブジェクトを書き換えてはいけない

正しい実装を振り返ると、サンプルA運輸の出荷ラベルは、すべて同じ `CarrierLogo` オブジェクトを共有しています。

ではここで、次のような要望があったとします。

> サンプルA運輸のキャンペーンに当選した山田 太郎様の出荷ラベルだけ、キャンペーン用のロゴ画像で印刷したい

※キャンペーン用のロゴ画像は、「サンプルA運輸キャンペーン」という名前で読み込めるものとします。

このような要望に対しては、`CarrierLogo` クラスにロゴ画像を差し替えるメソッドを追加すればよいと思うかもしれません。<br>
実際にコードを修正してみましょう。

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

    /* ここを追加（ここから） */
    public void changeImage(String carrierName) {
        this.carrierName = carrierName;
        this.imageData = new byte[10 * 1024];
    }
    /* ここを追加（ここまで） */
}
```

`CarrierLogo` クラスを振り返ると、`changeImage` メソッドを追加することにより、コンストラクターと同じく、引数で渡された名前に対応するロゴ画像のデータを読み込み直せるようにしています。

**`ShippingLabel.java`**

```java
package example;

public class ShippingLabel {
    private String address;
    private String recipientName;
    private int areaCode;
    private CarrierLogo logo;

    public ShippingLabel(Shipment shipment) {
        this.address = shipment.getAddress();
        this.recipientName = shipment.getRecipientName();
        this.areaCode = shipment.getAreaCode();
        this.logo = CarrierLogoFactory.getInstance().getLogo(shipment.getCarrierName());
    }

    public void print() {
        logo.print();
        System.out.println("エリア" + areaCode + "：" + address + " " + recipientName + " 様");
    }

    /* ここを追加（ここから） */
    public CarrierLogo getLogo() {
        return logo;
    }
    /* ここを追加（ここまで） */
}
```

`ShippingLabel` クラスを振り返ると、`getLogo` メソッドを追加することにより、出荷ラベルが保持している `CarrierLogo` オブジェクトを取得できるようにしています。

**`Main.java`**

```java
package example;

public class Main {
    public static void main(String[] args) {
        ShipmentRepository repository = new ShipmentRepository();
        // 受注が入ったときは、出荷データを DB に保存するだけにしておく
        repository.save(new Shipment("東京都千代田区", "山田 太郎", 2, "サンプルA運輸"));
        repository.save(new Shipment("神奈川県横浜市", "鈴木 花子", 1, "サンプルB運輸"));
        repository.save(new Shipment("埼玉県さいたま市", "佐藤 次郎", 3, "サンプルC運輸"));
        repository.save(new Shipment("東京都新宿区", "田中 美咲", 2, "サンプルA運輸"));
        repository.save(new Shipment("千葉県千葉市", "高橋 健一", 4, "サンプルA運輸"));
        repository.save(new Shipment("神奈川県川崎市", "伊藤 由美", 1, "サンプルB運輸"));

        // 梱包を始める前に、配送エリアの順に並べた前日分の出荷データから出荷ラベルを作成し、まとめて送信するまで保持しておく
        List<ShippingLabel> labels = new ArrayList<>();
        for (Shipment shipment: repository.findAllSortedByAreaCode()) {
            labels.add(new ShippingLabel(shipment));
        }

        /* ここを追加（ここから） */
        // 山田 太郎様の出荷ラベル（配送エリアの順に並べた後の 3 枚目）だけ、キャンペーン用のロゴ画像に差し替えたつもりで書き換えている
        labels.get(2).getLogo().changeImage("サンプルA運輸キャンペーン");
        /* ここを追加（ここまで） */

        // まとめて印刷する
        for (ShippingLabel label: labels) {
            label.print();
        }
    }
}
```

**実行結果**

```
[サンプルB運輸のロゴ]
エリア1：神奈川県横浜市 鈴木 花子 様
[サンプルB運輸のロゴ]
エリア1：神奈川県川崎市 伊藤 由美 様
[サンプルA運輸キャンペーンのロゴ]
エリア2：東京都千代田区 山田 太郎 様
[サンプルA運輸キャンペーンのロゴ]
エリア2：東京都新宿区 田中 美咲 様
[サンプルC運輸のロゴ]
エリア3：埼玉県さいたま市 佐藤 次郎 様
[サンプルA運輸キャンペーンのロゴ]
エリア4：千葉県千葉市 高橋 健一 様
```

実行結果を振り返ると、山田 太郎様の出荷ラベルだけを差し替えたつもりが、同じ配送会社を利用している田中 美咲様・高橋 健一様の出荷ラベルまでキャンペーン用のロゴ画像で印刷されています。これは、サンプルA運輸の出荷ラベルのすべてが、書き換えた `CarrierLogo` オブジェクトを共有しているためです。<br>
しかも、田中 美咲様・高橋 健一様の出荷ラベルを作成・印刷するコードには何も手を加えていないため、原因を突き止めるのにも手間がかかります。

このように、共有するオブジェクトの中身を書き換えると、そのオブジェクトを使う側すべてに影響が出ます。そのため、Flyweight パターンで共有するオブジェクトには、作成した後に中身を変更するメソッドを用意しないようにします。本記事の `CarrierLogo` クラスの既存コードを振り返ると、コンストラクターで配送会社名とロゴ画像のデータを設定した後は、それらを変更するメソッドを持っていません。<br>
1 枚の出荷ラベルだけ別のロゴ画像にしたい場合は、共有している `CarrierLogo` オブジェクトを書き換えるのではなく、キャンペーン用のロゴ画像を別の `CarrierLogo` オブジェクトとして `CarrierLogoFactory` オブジェクトから取得し、その出荷ラベルを表す `ShippingLabel` オブジェクトの `logo` フィールドだけを差し替えるようにしましょう。`ShippingLabel` オブジェクトは出荷ラベルごとに作られ、共有されていないため、`logo` フィールドを差し替えても、他の出荷ラベルには影響しません。

<a id="深堀り2"></a>

## 【深堀り②】使用メモリを計測してみる

好ましくない実装と正しい実装では、メモリに載るロゴ画像のデータの量を、ロゴ画像 1 枚あたり 10KB として概算しました。<br>
ここでは、1 日の出荷件数にあたる 1 万枚の出荷ラベルを保持したときの使用メモリを、実際に計測してみます。

**`MemoryCheck.java`**

```java
package example;

public class MemoryCheck {
    public static void main(String[] args) {
        String[] carrierNames = {"サンプルA運輸", "サンプルB運輸", "サンプルC運輸"};
        List<ShippingLabel> labels = new ArrayList<>();
        for (int i = 0; i < 10000; i++) {
            // 配送エリアは 10 か所あるものと仮定している
            int areaCode = i % 10 + 1;
            Shipment shipment = new Shipment("住所" + i, "氏名" + i, areaCode, carrierNames[areaCode % 3]);
            labels.add(new ShippingLabel(shipment));
        }

        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        System.out.println("使用メモリ：約" + usedMemory / (1024 * 1024) + "MB");
    }
}
```

`MemoryCheck` クラスを振り返ると、`for` 文により、1 万件の出荷データから出荷ラベルを作成し、`labels` リストに保持しています。配送エリアの番号は 1〜10 を順番に割り当て、配送会社は配送エリアごとに 1 社に決まるよう、配送エリアの番号から選んでいます。まとめて印刷する直前の状態を再現するため、出荷ラベルは保持したまま計測しています。<br>
`for` 文の後の 4 行は、Java で使用メモリを計測するときによく使われる書き方で、ガベージコレクションを実行してから、使用メモリを計算しています（→ [【深堀り②】使用メモリを計測してみる（ガベージコレクションについて）](#ガベージコレクションについて)）。

**実行結果（好ましくない実装）**

```
使用メモリ：約101MB
```

**実行結果（正しい実装）**

```
使用メモリ：約2MB
```

※使用メモリの値は、実行環境によって異なります。

好ましくない実装の約 101MB のうち、約 100MB は 1 万個の `CarrierLogo` オブジェクトが持つロゴ画像のデータで、計算どおりの結果になっています。<br>
正しい実装では、ロゴ画像のデータは 3 社分（約 30KB）しかありません。約 2MB の使用メモリのほとんどは、1 万個の `ShippingLabel` オブジェクトが保持している届け先の住所・氏名などのデータや、JVM 自体が使用しているメモリです。

このように、実際に計測しても、Flyweight パターンでロゴ画像を共有することで、大量の出荷ラベルを保持している間の使用メモリを大きく抑えられることが確かめられました。

### ガベージコレクションについて

Java では、どこからも参照されなくなったオブジェクトを JVM が自動で回収し、メモリを空けます。この仕組みをガベージコレクション（GC）と呼びます。`MemoryCheck` クラスの `labels` リストに追加した出荷ラベルは、`labels` リストから参照されているため回収されません。一方、どこにも保持せずに捨てた出荷ラベルは、どこからも参照されていないため回収の対象になります。<br>
GC は通常、JVM が必要と判断したタイミングで実行されます。`runtime.gc()` は GC の実行を JVM に依頼するメソッドで、呼び出したからといって、使われていないオブジェクトがすべて回収されるとは限りません。それでも、計測の前に呼び出しておくことで、もう使われていないオブジェクトが計測値に含まれにくくなります。<br>
`runtime.totalMemory()` は JVM が確保しているメモリの量を返し、`runtime.freeMemory()` はそのうちまだ使われていないメモリの量を返します。`MemoryCheck` クラスでは、その差を使用メモリとして MB 単位で出力しています。

<a id="深堀り3"></a>

## 【深堀り③】作ってすぐ捨てれば、メモリは少なくて済むのでは？

深堀り②の `MemoryCheck` クラスを振り返ると、1 万枚の出荷ラベルを `labels` リストに保持したまま、使用メモリを計測していました。

では、出荷ラベルを保持せずに、作ってすぐ捨てた場合はどうなるでしょうか？

`MemoryCheck` クラスの `for` 文を、次のように書き換えて計測し直してみます。

**`MemoryCheck.java`（一部抜粋）**

```java
for (int i = 0; i < 10000; i++) {
    // 配送エリアは 10 か所あるものと仮定している
    int areaCode = i % 10 + 1;
    Shipment shipment = new Shipment("住所" + i, "氏名" + i, areaCode, carrierNames[areaCode % 3]);
    // 作成した出荷ラベルを、どこにも保持せずに捨てている
    new ShippingLabel(shipment);
}
```

**実行結果（好ましくない実装）**

```
使用メモリ：約1MB
```

**実行結果（正しい実装）**

```
使用メモリ：約1MB
```

※使用メモリの値は、実行環境によって異なります。

実行結果を振り返ると、好ましくない実装と正しい実装のどちらも使用メモリは約 1MB になり、差がありません。どちらの実装も、作成した `ShippingLabel` オブジェクトをどこからも参照していないため、`runtime.gc()` を呼び出した時点で、もう使われていないオブジェクトとして回収されたからです。<br>
好ましくない実装では、1 万個の `CarrierLogo` オブジェクトも一緒に回収されます。<br>
正しい実装の `CarrierLogo` オブジェクトは `pool` フィールドから参照されたままですが、3 社分（約 30KB）しかないため、差として表れません（→ [【深堀り④】共有したオブジェクトは GC で回収されない](#深堀り4)）。

この結果を見ると、「それなら、Flyweight パターンを使わなくても、出荷ラベルを作ってすぐ捨てればメモリは少なくて済むのでは？」と思うかもしれません。

しかし、捨てた出荷ラベルはもう使えません。<br>
例えば、計測した後に出荷ラベルを印刷しようとしても、どの `ShippingLabel` オブジェクトも参照が残っていないため、`print` メソッドを呼び出せません。本記事では、並べ替えた出荷ラベルを 1 回でまとめて送信するために、前日分の出荷ラベルをすべて同時に保持しておく必要があります。つまり、作ってすぐ捨てる実装は、そもそも追加要件を満たせません。<br>
ただし、既存コードのように 1 枚ずつ作って印刷しては捨てる場合は、同時に使うロゴ画像は常に 1 枚分なので、Flyweight パターンを使う必要はありません。

このように、Flyweight パターンの効果を比べるときは、大量のオブジェクトを「使っている最中」の使用メモリを比べる必要があります。計測した時点で、どのオブジェクトがまだ参照されているかによって、GC で回収されるオブジェクトが変わり、計測結果も変わります。実務でメモリ不足（`OutOfMemoryError`）の原因を調べる際も、処理が終わった後ではなく、大量のオブジェクトを保持している最中の使用メモリを確認しましょう。

<a id="深堀り4"></a>

## 【深堀り④】共有したオブジェクトは GC で回収されない

正しい実装の `CarrierLogoFactory` クラスを振り返ると、`pool` フィールドが作成済みの `CarrierLogo` オブジェクトを保持し続けています。<br>
そのため、出荷ラベルをすべて印刷し終えて、`labels` リストや `ShippingLabel` オブジェクトが使われなくなっても、`pool` フィールドから参照されたままの `CarrierLogo` オブジェクトは GC で回収されず、プログラムが終了するまでメモリに残り続けます。

本記事では、共有するロゴ画像は配送会社の数（3 社分、合計約 30KB）しかないため、メモリに残り続けても問題になりません。しかし、商品ごとの商品画像のように、共有するものの種類が数万にのぼる場合は、一度でも使われた商品画像がすべて `pool` フィールドに残り続けるため、使用メモリが増え続け、メモリ不足（`OutOfMemoryError`）になるリスクがあります。

このように、Flyweight パターンは、共有するものの種類が少なく、同じものが大量に使われる場面に向いています。<br>
共有するものの種類が多い場合は、`pool` フィールドに保持し続けるものを絞り込む必要があります。よく使われる方法には、次のようなものがあります。

- 保持する数に上限を決め、上限を超えたら、最も長い間使われていないものから削除する（LRU 方式）。
- 最後に使われてから一定時間が過ぎたものを削除する。
- `pool` フィールド以外のどこからも使われなくなったものを、GC で回収できるようにする（弱参照）。

実務では、これらの仕組みを備えたキャッシュライブラリ（Caffeine など）を使うのが一般的です。<br>
詳しくは「Java キャッシュ LRU」「Java 弱参照」などで検索してみてください。

<a id="深堀り5"></a>

## 【深堀り⑤】Singleton パターンとの違い

正しい実装を振り返ると、`CarrierLogoFactory` クラスを Singleton パターンにしたうえで、`CarrierLogo` オブジェクトを Flyweight パターンで共有しました。<br>
Singleton パターンと Flyweight パターンは、どちらも「同じオブジェクトを使い回す」という点でよく似ています。

ただし、2 つのパターンでは、使い回す単位が異なります。<br>
Singleton パターンでは、1 つのクラスにつき、オブジェクトを 1 つだけ作ります。例えば、`CarrierLogoFactory` クラスのオブジェクトは、アプリケーション全体で 1 つだけ作られます。<br>
一方、Flyweight パターンでは、共有できる情報の種類ごとに、オブジェクトを 1 つだけ作ります。例えば、`CarrierLogo` クラスのオブジェクトは、配送会社の数（3 社分）だけ作られます。

このように 2 つのパターンは、使い回す単位が異なるため、組み合わせて使うことができます。<br>
本記事では、Flyweight パターンで `CarrierLogo` オブジェクトを共有するために、作成済みの `CarrierLogo` オブジェクトを保持している、`CarrierLogoFactory` クラスの `pool` フィールドが、アプリケーション全体で 1 つでなければなりません。これは、複数の `pool` フィールドがあると、それぞれの `pool` フィールドに同じ配送会社のロゴ画像が登録され、共有が崩れてしまうからです。<br>
そこで、`CarrierLogoFactory` クラスを Singleton パターンにすることで、アプリケーション全体で `pool` フィールドが 1 つになるようにしています。<br>
詳しくは「Singleton パターン」で検索してみてください。

<a id="深堀り6"></a>

## 【深堀り⑥】複数のスレッドから使う場合

正しい実装の `CarrierLogoFactory` クラスの `getLogo` メソッドを振り返ると、`pool` フィールドに配送会社のロゴ画像があるかを確認し、なければ新しく作成して登録しています。<br>
実務では、本記事のように出荷ラベルが 1 万件にもなると作成に時間がかかるため、作成を複数の処理に分けて同時に進め、処理時間を短くすることがあります。

このように同時に進める処理の流れの 1 つ 1 つを「スレッド」と呼びます。`CarrierLogoFactory` オブジェクトはアプリケーション全体で 1 つだけなので、どのスレッドも同じ `pool` フィールドを使います。<br>
例えば、2 つのスレッドが、それぞれサンプルA運輸の出荷ラベルをほぼ同時に作成し、`getLogo` メソッドを呼び出した場合を考えてみましょう。

1. スレッド 1 が `pool.get(carrierName)` を実行する
2. サンプルA運輸のロゴ画像を取得するのは初めてで、`pool` フィールドにはまだ登録されていないため、スレッド 1 は `null` を受け取る
3. スレッド 1 が条件分岐の中で `CarrierLogo` オブジェクトを作成して登録する前に、スレッド 2 が `pool.get(carrierName)` を実行する
4. `pool` フィールドにはまだ登録されていないため、スレッド 2 も `null` を受け取る
5. 2 つのスレッドが、条件分岐の中でそれぞれサンプルA運輸の `CarrierLogo` オブジェクトを作成し、`pool` フィールドに登録する

このように、あるスレッドが `pool` フィールドを確認してから登録するまでの間に他のスレッドが割り込むと、共有されるはずのロゴ画像が重複して作られてしまいます。また、`HashMap` クラスは複数のスレッドから同時に書き込まれることを想定していないため、`put` メソッドが同時に実行されると、`pool` フィールドの中身が壊れてしまうリスクもあります。

これを防ぐ案として、`getLogo` メソッドに `synchronized` を付ける方法があります。

**`CarrierLogoFactory.java`（一部抜粋）**

```java
package example;

public class CarrierLogoFactory {
    public synchronized CarrierLogo getLogo(String carrierName) { // ←ここを修正
        CarrierLogo logo = pool.get(carrierName);

        if (logo == null) {
            logo = new CarrierLogo(carrierName);
            pool.put(carrierName, logo);
        }

        return logo;
    }
}
```

※フィールド・コンストラクター・`getInstance` メソッドは変更がないため省略しています。

`synchronized` を付けたメソッドには、同時に 1 つのスレッドしか入れません。つまり、スレッド 1 が `getLogo` メソッドを実行している間は、他のスレッドが入れないよう鍵がかかった状態（ロック）になり、スレッド 2 はスレッド 1 が `getLogo` メソッドを抜けるまで待たされます。<br>
これにより、スレッド 2 は、スレッド 1 がサンプルA運輸の `CarrierLogo` オブジェクトを `pool` フィールドに登録し終えてから `pool.get(carrierName)` を実行するため、登録済みの `CarrierLogo` オブジェクトを受け取れます。また、`put` メソッドが同時に実行されることもなくなるため、`pool` フィールドの中身が壊れることもありません。

> ※`synchronized` は直訳すると「同期」ですが、「同期処理・非同期処理」の同期とは意味が異なります。

他の方法として、`HashMap` クラスの代わりに `ConcurrentHashMap` クラスを使い、`computeIfAbsent` メソッドで確認と登録をまとめて行う方法もあります。

なお、本記事の `Main` クラスのように 1 つのスレッドで実行する場合は、他のスレッドが割り込むことがないため、`synchronized` を付けても付けなくても結果は変わりません。

正しい実装の `CarrierLogoFactory` クラスのように、共有するオブジェクトを管理するクラスを複数のスレッドから使う可能性がある場合は、`pool` フィールドを確認してから登録するまでの間に、他のスレッドが割り込めない設計にしましょう。<br>
詳しくは「Java スレッドセーフ」などで検索してみてください。

<a id="深堀り7"></a>

## 【深堀り⑦】GoF デザインパターンとの位置づけ

今回使った Flyweight パターンは、GoF（Gang of Four）の 23 のデザインパターンのうち「構造パターン」に分類されます。<br>
詳しくは「GoF」で検索してみてください。
