# Decorator（デコレータ）パターン ― 機能をオブジェクトの外側から動的に組み合わせる

次のような経験をしたことはありませんか？

> 基本機能に対してオプション機能を追加する要件が来るたびに、組み合わせのパターンごとにクラスを増やしたり、フラグを増やして条件分岐を継ぎ足したりしてきた。その結果、追加したいオプションが増えるほど、クラスの数や条件分岐が雪だるま式に膨らんでしまった。

この記事では、貸し会議室サービスの予約システムにオプション設備を追加するシナリオを通して、Decorator パターンがこの問題をどのように解決するかを紹介します。

## 目次

- [【具体例】](#具体例)
    - [シナリオ](#シナリオ)
    - [既存コードの仕様](#既存コードの仕様)
- [好ましくない実装](#好ましくない実装)
- [正しい実装](#正しい実装)
- [まとめ](#まとめ)
- [【深堀り①】継承ではなく委譲を使う理由](#深堀り1)
- [【深堀り②】Composite パターンとの違い](#深堀り2)
- [【深堀り③】Java 標準ライブラリにおける Decorator パターンの例](#深堀り3)
- [【深堀り④】OCP（オープン・クローズドの原則）](#深堀り4)
- [【深堀り⑤】GoF デザインパターンとの位置づけ](#深堀り5)

---

## 【具体例】

### シナリオ

> あなたは貸し会議室サービスの開発チームに所属しています。<br>
> 現在、会議室予約は会議室名・利用時間・基本料金の算出のみに対応している状態です。<br>
> ある日、営業部から「顧客から、予約時にプロジェクター・ケータリング・録画設定といったオプション設備を追加できるようにしてほしいとの要望が増えている。オプションは会議の内容に応じて自由に組み合わせたいという声もあり、今後も種類が増える見込みがある」という要望が届きました。<br>
> あなたは、予約にオプション設備を追加できるようにしつつ、追加した場合の合計料金と手配内容の一覧を確認できるようにする実装を担当することになりました。

※実際の会議室予約システムでは、会議室名に応じた基本料金を API から取得したうえで、予約情報をデータベースに保存し、オプション設備の手配（ケータリング業者への発注など）を行う実装が必要です。Decorator パターンの解説に集中するため、以降のコード例では API から渡された値を使用していることを前提とし、処理はコンソールへの文字列出力のみとします。

### 既存コードの仕様

※実務では、次の `MeetingRoomReservation` のようなエンティティクラスは `entity` パッケージなど専用のディレクトリに切り出すのが一般的です。しかし、本記事ではパッケージ構成を主題としないため `example` パッケージ直下にまとめています。

- `MeetingRoomReservation`（既存クラス）

会議室の予約情報を保持するクラスです。<br>
会議室名・利用時間・1 時間あたりの基本料金から、予約にかかる料金と予約内容の説明を算出します。

| フィールド   | 型       | 説明                   |
| ------------ | -------- | ---------------------- |
| `roomName`   | `String` | 会議室名               |
| `hours`      | `int`    | 利用時間（時間）       |
| `feePerHour` | `int`    | 1 時間あたりの基本料金 |

| メソッド     | 戻り値の型 | 説明                                     |
| ------------ | ---------- | ---------------------------------------- |
| `getFee`     | `int`      | 料金（`feePerHour` × `hours`）を取得する |
| `getDetails` | `String`   | 会議室名と利用時間を含む説明を取得する   |

**`MeetingRoomReservation.java`**

```java
package example;

public class MeetingRoomReservation {
    private String roomName;
    private int hours;
    private int feePerHour;

    public MeetingRoomReservation(String roomName, int hours, int feePerHour) {
        this.roomName = roomName;
        this.hours = hours;
        this.feePerHour = feePerHour;
    }

    public int getFee() {
        return feePerHour * hours;
    }

    public String getDetails() {
        return roomName + "（" + hours + "時間）";
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
        MeetingRoomReservation reservation = new MeetingRoomReservation("第1会議室", 2, 3000);
        System.out.println(reservation.getDetails());
        System.out.println("料金：" + reservation.getFee() + "円");
    }
}
```

**実行結果**

```
第1会議室（2時間）
料金：6000円
```

※ここで一旦読むのを止めて、ご自身でコーディングを行なってみてください。その後で、続きを読んでください。

## 好ましくない実装

では、シナリオに従い追加実装をしていきましょう。

真っ先に思いつくのは、`MeetingRoomReservation` クラスに、オプションごとの有無を表すフィールドを追加し、既存メソッドの中で分岐して加算・連結する、という実装ではないでしょうか？

**`MeetingRoomReservation.java`**

```java
package example;

public class MeetingRoomReservation {
    private static final int PROJECTOR_FEE = 2000;
    private static final int CATERING_FEE = 5000;
    private static final int RECORDING_FEE = 3000;
    private String roomName;
    private int hours;
    private int feePerHour;
    private boolean hasProjector;
    private boolean hasCatering;
    private boolean hasRecording;

    public MeetingRoomReservation(String roomName, int hours, int feePerHour,
            boolean hasProjector, boolean hasCatering, boolean hasRecording) {
        this.roomName = roomName;
        this.hours = hours;
        this.feePerHour = feePerHour;
        this.hasProjector = hasProjector;
        this.hasCatering = hasCatering;
        this.hasRecording = hasRecording;
    }

    public int getFee() {
        int fee = feePerHour * hours;
        if (hasProjector) {
            fee += PROJECTOR_FEE;
        }
        if (hasCatering) {
            fee += CATERING_FEE;
        }
        if (hasRecording) {
            fee += RECORDING_FEE;
        }
        return fee;
    }

    public String getDetails() {
        String details = roomName + "（" + hours + "時間）";
        if (hasProjector) {
            details += " + プロジェクター";
        }
        if (hasCatering) {
            details += " + ケータリング";
        }
        if (hasRecording) {
            details += " + 録画設定";
        }
        return details;
    }
}
```

実行クラスは次のようになると思います。

**`Main.java`**

```java
package example;

public class Main {
    public static void main(String[] args) {
        MeetingRoomReservation reservation1 =
                new MeetingRoomReservation("第1会議室", 2, 3000, false, false, false);
        System.out.println(reservation1.getDetails());
        System.out.println("料金：" + reservation1.getFee() + "円");

        MeetingRoomReservation reservation2 =
                new MeetingRoomReservation("第2会議室", 3, 4000, true, true, false);
        System.out.println(reservation2.getDetails());
        System.out.println("料金：" + reservation2.getFee() + "円");
    }
}
```

**実行結果**

```
第1会議室（2時間）
料金：6000円
第2会議室（3時間） + プロジェクター + ケータリング
料金：19000円
```

コンパイルエラーがなく結果が出力されていることから、一見すると実装・動作確認ともに問題ないように見えます。

しかし、この実装には以下の問題点があります。

- 新しいオプション（例えば「無線 LAN 追加」）が増えるたびに、`MeetingRoomReservation` クラスのフィールド、コンストラクタ引数、`getFee`・`getDetails` メソッドのすべての修正が必要で、1 つのクラスに条件分岐が積み重なっていく。
- コンストラクタの引数が `boolean` の羅列になっているため、呼び出し側から見てどの引数がどのオプションに対応するのか分かりにくい。

## 正しい実装

では、好ましくない実装で挙げた問題点を解決するにはどうすればよいのでしょうか？

これらの問題を解決するのが **Decorator パターン**です。<br>
オプションを既存クラスのフィールドとして持たせるのではなく、オプションごとに独立したクラスを用意します。そのために、既存クラスとオプションクラスの両方に共通の型を持たせることで、予約情報が格納されたオブジェクトを外側から包んでいく（委譲する）構造にします。

まず、共通の抽象クラスから見ていきましょう。

**`Reservation.java`**

```java
package example;

public abstract class Reservation {
    public abstract int getFee();

    public abstract String getDetails();
}
```

`Reservation` クラスは新たに追加した抽象クラスで、フィールドや具体的な処理は一切持たず、`getFee`・`getDetails` という 2 つの抽象メソッドのみを定義しています。

次に、抽象クラス `Reservation` を継承した既存のクラスを見ていきましょう。

**`MeetingRoomReservation.java`**

```java
package example;

public class MeetingRoomReservation extends Reservation {
    private String roomName;
    private int hours;
    private int feePerHour;

    public MeetingRoomReservation(String roomName, int hours, int feePerHour) {
        this.roomName = roomName;
        this.hours = hours;
        this.feePerHour = feePerHour;
    }

    @Override
    public int getFee() {
        return feePerHour * hours;
    }

    @Override
    public String getDetails() {
        return roomName + "（" + hours + "時間）";
    }
}
```

`MeetingRoomReservation` クラスを振り返ると、抽象クラス `Reservation` を継承し、`getFee`・`getDetails` メソッドに `@Override` を付けています。一方、フィールドや処理の内容自体は、既存の仕様から変更されていません。

次に、抽象クラス `Reservation` を継承したオプションの共通の抽象クラスを見ていきましょう。

**`ReservationOption.java`**

```java
package example;

public abstract class ReservationOption extends Reservation {
    protected Reservation reservation;

    protected ReservationOption(Reservation reservation) {
        this.reservation = reservation;
    }
}
```

`ReservationOption` クラスは新たに追加した抽象クラスで、`Reservation` クラスを継承し、内部に別の `Reservation` 型のフィールドを保持しています。

次に、抽象クラス `ReservationOption` を継承したオプションクラスを見ていきましょう。

**`ProjectorOption.java`**

```java
package example;

public class ProjectorOption extends ReservationOption {
    private static final int FEE = 2000;

    public ProjectorOption(Reservation reservation) {
        super(reservation);
    }

    @Override
    public int getFee() {
        return reservation.getFee() + FEE;
    }

    @Override
    public String getDetails() {
        return reservation.getDetails() + " + プロジェクター";
    }
}
```

**`CateringOption.java`**

```java
package example;

public class CateringOption extends ReservationOption {
    private static final int FEE = 5000;

    public CateringOption(Reservation reservation) {
        super(reservation);
    }

    @Override
    public int getFee() {
        return reservation.getFee() + FEE;
    }

    @Override
    public String getDetails() {
        return reservation.getDetails() + " + ケータリング";
    }
}
```

**`RecordingOption.java`**

```java
package example;

public class RecordingOption extends ReservationOption {
    private static final int FEE = 3000;

    public RecordingOption(Reservation reservation) {
        super(reservation);
    }

    @Override
    public int getFee() {
        return reservation.getFee() + FEE;
    }

    @Override
    public String getDetails() {
        return reservation.getDetails() + " + 録画設定";
    }
}
```

`ProjectorOption`・`CateringOption`・`RecordingOption` クラスを振り返ると、いずれも `getFee`・`getDetails` メソッドの内部で、まず `reservation` フィールドの `getFee`・`getDetails` メソッドを呼び出しています。この呼び出しで、`reservation` フィールドに渡された `Reservation` 型のインスタンスが持つ予約結果（料金・説明文）を取得します。次に、その戻り値に対して、オプションクラス自身の追加料金（`FEE`）や追加内容を表す文字列を上乗せしています。

ここで注目して欲しいことは、「まず委譲先に処理を任せてから、次に自分自身の追加分を上乗せする」という実装によって、オプションを何個・どんな順序で包んでも、それぞれのオプションクラスは自分自身の追加分だけを知っていればよくなる点です（→ [継承ではなく委譲を使う理由](#深堀り1)）。

最後に、実行クラス（呼び出し側）を見ていきましょう。

**`Main.java`**

```java
package example;

public class Main {
    public static void main(String[] args) {
        Reservation reservation1 = new MeetingRoomReservation("第1会議室", 2, 3000);
        System.out.println(reservation1.getDetails());
        System.out.println("料金：" + reservation1.getFee() + "円");

        /* ここを追加（ここから） */
        Reservation reservation2 = new MeetingRoomReservation("第2会議室", 3, 4000);
        reservation2 = new ProjectorOption(reservation2);
        reservation2 = new CateringOption(reservation2);
        System.out.println(reservation2.getDetails());
        System.out.println("料金：" + reservation2.getFee() + "円");

        Reservation reservation3 = new MeetingRoomReservation("第3会議室", 4, 3500);
        reservation3 = new ProjectorOption(reservation3);
        reservation3 = new ProjectorOption(reservation3);
        System.out.println(reservation3.getDetails());
        System.out.println("料金：" + reservation3.getFee() + "円");
        /* ここを追加（ここまで） */
    }
}
```

**実行結果**

```
第1会議室（2時間）
料金：6000円
第2会議室（3時間） + プロジェクター + ケータリング
料金：19000円
第3会議室（4時間） + プロジェクター + プロジェクター
料金：18000円
```

`Main` クラスを振り返ると、`reservation2`・`reservation3` は、次のような流れで必要なオプションだけを外側から包んで組み立てています。

1. `MeetingRoomReservation` クラスを生成する。
2. 生成したものをオプションクラスのコンストラクタへ渡す。
3. 2 の結果を同じ変数へ上書きする。

実行結果を振り返ると、`reservation1`・`reservation2` の結果は、好ましくない実装とまったく同じになっています。また、`reservation3` は、同じ `ProjectorOption` クラスを 2 回包んだ結果になっています。

以上のような実装を行うと、以下のメリットがあります。

- 新しいオプション（例えば「無線 LAN 追加」）を増やす場合も、`ReservationOption` を継承した新しいクラスを追加するだけで済み、既存の `Reservation`・`MeetingRoomReservation` クラスや、追加済みの他のオプションクラスには一切手を加える必要がない。
- 実際に適用したいオプションだけをコード上でもそのまま読み取れる形で組み立てられるため、好ましくない実装のようなコンストラクタへオプションの有無を `boolean` の羅列で渡す必要がなくなる。
- `MeetingRoomReservation` クラスと各オプションクラスは共通の `Reservation` 型を継承しているため、呼び出し側は両者を同じ型として扱える。
    - その結果、オプションクラスのコンストラクタには `MeetingRoomReservation` クラスのインスタンスだけでなく、オプションクラス自身のインスタンスも渡せる。
- オプションはオブジェクトとして包む（委譲する）ため、`Main` クラスの `reservation3` のように「同じオプションを複数回適用する」という `boolean` フィールドでは表現できなかった組み合わせにも対応できる。

## まとめ

正しい実装を振り返ると、各オプションクラスは `Reservation` 型のフィールドに処理を委譲したうえで、自分が追加する料金・内容だけを上乗せしています。<br>
このように、Decorator パターンは、既存クラスと同じ型を持つクラスで外側から包み、処理を委譲してから自分自身の追加分を上乗せさせることで、既存クラスを変更せずにオプションの組み合わせや個数を実行時に自由に変えられるようにするパターンです。

本記事の内容はここまでとなります。

以降は「もう少し深く知りたい」という方向けの補足となります。今回学んだパターンに繋がる設計原則や、実務で役立つ背景知識について触れています。

---

<a id="深堀り1"></a>

## 【深堀り①】継承ではなく委譲を使う理由

本記事の好ましくない実装で挙げた `boolean` フィールドの代わりに、次のような継承を使った実装を思いついた方もいるかもしれません。

※なお、`MeetingRoomReservation` クラスは既存コードの仕様で示したものです。

**`ProjectorReservation.java`**

```java
package example;

public class ProjectorReservation extends MeetingRoomReservation {
    private static final int FEE = 2000;

    public ProjectorReservation(String roomName, int hours, int feePerHour) {
        super(roomName, hours, feePerHour);
    }

    @Override
    public int getFee() {
        return super.getFee() + FEE;
    }

    @Override
    public String getDetails() {
        return super.getDetails() + " + プロジェクター";
    }
}
```

**`CateringReservation.java`**

```java
package example;

public class CateringReservation extends MeetingRoomReservation {
    private static final int FEE = 5000;

    public CateringReservation(String roomName, int hours, int feePerHour) {
        super(roomName, hours, feePerHour);
    }

    @Override
    public int getFee() {
        return super.getFee() + FEE;
    }

    @Override
    public String getDetails() {
        return super.getDetails() + " + ケータリング";
    }
}
```

**`ProjectorCateringReservation.java`**

```java
package example;

public class ProjectorCateringReservation extends MeetingRoomReservation {
    private static final int PROJECTOR_FEE = 2000;
    private static final int CATERING_FEE = 5000;

    public ProjectorCateringReservation(String roomName, int hours, int feePerHour) {
        super(roomName, hours, feePerHour);
    }

    @Override
    public int getFee() {
        return super.getFee() + PROJECTOR_FEE + CATERING_FEE;
    }

    @Override
    public String getDetails() {
        return super.getDetails() + " + プロジェクター" + " + ケータリング";
    }
}
```

ではここで、録画オプションも選べるようにしてほしいという要望が来たとしましょう。

同様の要領で `RecordingReservation`・`ProjectorRecordingReservation`・`CateringRecordingReservation`・`ProjectorCateringRecordingReservation` クラスを追加することになると思います。これは、録画オプション単体のパターンに加えて、これまでの各オプションの組み合わせに録画オプションを加えたパターンについても、それぞれサブクラスを用意する必要があるからです。これにより、合計 3 + 4 = 7 つのサブクラスを用意しないといけなくなります。

以上から、継承を使った実装では、オプションの種類が増えるほど、用意しなければならないサブクラスの数が加速度的に増えていきます。<br>
具体的に示すと、オプションが 1 種類なら 1 通り、2 種類なら 1 + 2 = 3 通り、今回のように 3 種類なら 3 + 2 × 2 = 7 通り、4 種類目が増えれば 7 + 2 × 2 × 2 = 15 通り……というようにサブクラスの数が膨大になっていきます。

一方、本記事の実装である Decorator パターンでは、委譲を使った実装にしています。<br>
この実装にすると、オプションクラスは自分自身の追加分だけを知っていればよく（→ [正しい実装](#正しい実装)のメリットの 1 つ目）、他のオプションと組み合わせるための専用クラスを別途用意する必要はありません。そのため、先ほどのように「録画オプション」が増えても、オプションクラスの数は 2 + 1 = 3 だけにしかならず、「既存のクラス数 + オプション数」分のクラス数で済みます。

<a id="深堀り2"></a>

## 【深堀り②】Composite パターンとの違い

本記事の `ReservationOption` クラスを振り返ると、`Reservation` 型のフィールドを持ち、料金・説明の算出処理をそのインスタンスに委譲しています。この「共通の抽象型を継承したクラスが、同じ抽象型のインスタンスを内部に保持し、処理を委譲する」という構造は、「**Composite パターン**」の構造とよく似ています。

ただし、両者の目的は異なります。<br>
Composite パターンは、個別要素と複合要素（個別要素の集まり）を同一の型として扱うことで、木構造をどれだけ深くたどっても同じコードで処理できるようにすることを目的としています。<br>
一方、Decorator パターンは、既存のオブジェクトに新しい機能を動的に追加することを目的としています。そのため、`ReservationOption` クラスが内部に保持するのは複数の要素ではなく、機能を追加する対象となる単一のオブジェクトです。

つまり、Composite パターンは「複数の要素をまとめて同一視すること」が目的であるのに対し、Decorator パターンは「1 つのオブジェクトに機能を追加すること」が目的である、という点が異なります。

<a id="深堀り3"></a>

## 【深堀り③】Java 標準ライブラリにおける Decorator パターンの例

Decorator パターンは、Java の標準ライブラリにも数多く使われています。代表的なのが `java.io` パッケージの入出力ストリームです。

```java
BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
```

このコードは、`System.in`（バイト単位の入力）を `InputStreamReader` クラスで文字単位の読み込みができるように変換し、さらにその結果を `BufferedReader` クラスで包むことで、内部にバッファを持たせて 1 行単位の読み込み（`readLine` メソッド）ができるようにしています。<br>
`InputStreamReader`・`BufferedReader` クラスは、いずれも本記事の `ReservationOption` クラスと同じように、内部に元のオブジェクトを保持し、そのオブジェクトの処理を呼び出しながら自分の機能を上乗せする形で実装されています。普段何気なく書いている入出力処理も、実は Decorator パターンの一例です。

<a id="深堀り4"></a>

## 【深堀り④】OCP（オープン・クローズドの原則）

正しい実装を振り返ると、新しいオプション（例えば「無線 LAN 追加」）を追加する場合、必要なのは抽象クラス `ReservationOption` を継承した新しいクラスを作成することだけで、既存の `Reservation`・`MeetingRoomReservation` クラスや、他のオプションクラスには一切手を加える必要がありません。<br>
呼び出し側が依存しているのは抽象クラス `Reservation` だけであるため、具体的な実装クラスがどう組み合わさっていても、料金計算・明細表示ともに対応できます。

この「既存コードを変えずに、新しいクラスを追加するだけで機能を拡張できる」という設計は、「**OCP（Open/Closed Principle：オープン・クローズドの原則）**」と呼ばれる設計原則の実践です。Decorator パターンは OCP を実現するための設計手段の一つと言えます。

詳しくは「OCP」や「オープン・クローズドの原則」で検索してみてください。

<a id="深堀り5"></a>

## 【深堀り⑤】GoF デザインパターンとの位置づけ

今回使った Decorator パターンは、GoF（Gang of Four）の 23 のデザインパターンのうち「構造パターン」に分類されます。<br>
詳しくは「GoF」で検索してみてください。
