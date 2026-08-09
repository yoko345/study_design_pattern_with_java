# Decorator（デコレータ）パターン ― 機能をオブジェクトの外側から動的に組み合わせる

次のような経験をしたことはありませんか？

> 基本機能に対してオプション機能を追加する要件が来るたびに、組み合わせのパターンごとにクラスを増やしたり、フラグを増やして条件分岐を継ぎ足したりしてきた。その結果、追加したいオプションが増えるほど、クラスの数や条件分岐が雪だるま式に膨らんでしまった。

この記事では、社内の会議室予約システムにオプション設備を追加するシナリオを通して、Decorator パターンがこの問題をどのように解決するかを紹介します。

## 目次

- [【具体例】](#具体例)
    - [シナリオ](#シナリオ)
    - [既存コードの仕様](#既存コードの仕様)
- [好ましくない実装](#好ましくない実装)
- [正しい実装](#正しい実装)
- [まとめ](#まとめ)
- [【深堀り①】Decorator と継承の違い](#深堀り1)
- [【深堀り②】Java 標準ライブラリにおける Decorator パターンの例](#深堀り2)
- [【深堀り③】OCP（オープン・クローズドの原則）](#深堀り3)
- [【深堀り④】GoF デザインパターンとの位置づけ](#深堀り4)

---

## 【具体例】

### シナリオ

> あなたは社内の会議室予約システムの開発チームに所属しています。<br>
> 現在、会議室予約は会議室名・利用時間・基本料金の算出のみに対応している状態です。<br>
> ある日、総務部から「予約時にプロジェクター・ケータリング・録画設定といったオプション設備を追加できるようにしてほしい。オプションは会議の内容に応じて自由に組み合わせたいし、今後も種類が増える見込みがある」という要望が届きました。あなたは以下を担当します。
>
> - 予約にオプション設備を追加できるようにする（複数のオプションを自由に組み合わせられる）
> - オプションを追加した場合、合計料金と手配内容の一覧を確認できるようにする

※実際の会議室予約システムでは、予約情報をデータベースに保存し、オプション設備の手配（ケータリング業者への発注など）を行う実装が必要ですが、本記事では Decorator パターンの解説に集中するため、コンソールへの文字列出力のみとします。

### 既存コードの仕様

※実務では、次の `MeetingRoomReservation` のようなエンティティクラスは `entity` パッケージなど専用のディレクトリに切り出すのが一般的です。しかし、本記事ではパッケージ構成を主題としないため `example` パッケージ直下にまとめています。

- `MeetingRoomReservation`（既存クラス）

会議室の予約情報を保持するクラスです。会議室名・利用時間・1 時間あたりの基本料金から、予約にかかる料金と予約内容の説明を算出します。

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

真っ先に思いつくのは、`MeetingRoomReservation` クラスに、オプションごとの有無を表すフィールドを追加し、`getFee`・`getDetails` メソッドの中で分岐して加算・連結する、という実装ではないでしょうか？

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

最後に、実行クラスは次のようになると思います。

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

- 新しいオプション（例えば「Wi-Fi ルーター追加」）が増えるたびに、`MeetingRoomReservation` クラスのフィールド・コンストラクタ引数・`getFee`・`getDetails` メソッドのすべてに手を加える必要があり、オプションが増えるほど 1 つのクラスに条件分岐が積み重なっていく。
- `boolean` フィールドは「あり・なし」の 2 値しか表現できないため、例えば「プロジェクターを 2 台」のように、同じオプションを複数個手配したいケースに対応できない。
- コンストラクタの引数が `boolean` の羅列になっており、`new MeetingRoomReservation("第2会議室", 3, 4000, true, true, false)` のように、呼び出し側から見てどの引数がどのオプションに対応するか分かりにくい。

## 正しい実装

では、好ましくない実装で挙げた問題点を解決するにはどうすればよいのでしょうか？

これらの問題を解決するのが **Decorator パターン**です。<br>
オプションを既存クラスのフィールドとして持たせるのではなく、オプションごとに独立したクラスを用意し、予約オブジェクトを外側から包んでいく（委譲する）ことで、既存クラスを変更せずに自由な組み合わせを実現します。

まず、`MeetingRoomReservation` クラスと、後述するオプションクラスの両方が実装すべき、共通の抽象クラスから見ていきましょう。

**`Reservation.java`**

```java
package example;

public abstract class Reservation {
    public abstract int getFee();

    public abstract String getDetails();
}
```

次に、抽象クラス `Reservation` を継承した `MeetingRoomReservation` クラスを見ていきましょう。

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

`MeetingRoomReservation` クラスを振り返ると、好ましくない実装で追加した `boolean` フィールドや条件分岐がすべてなくなり、既存の仕様と同じ 3 つのフィールドだけを持つ形に戻っています。変更点は、抽象クラス `Reservation` を継承し、`getFee`・`getDetails` メソッドに `@Override` を付けたことだけです。

続いて、オプションの土台となる抽象クラスを見ていきましょう。

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

`ReservationOption` クラスは新たに追加した抽象クラスで、`Reservation` クラスを継承しつつ、内部に別の `Reservation` を `reservation` フィールドとして保持しています。この `reservation` フィールドには、`MeetingRoomReservation` のインスタンスだけでなく、後述するオプションクラス自身のインスタンスも渡せます。

この `ReservationOption` クラスを継承した、3 つのオプションクラスを見ていきましょう。

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

同様の考え方で、ケータリングと録画設定のオプションクラスも実装します。

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

3 つのオプションクラスを振り返ると、いずれも `getFee`・`getDetails` メソッドの内部で、まず `reservation.getFee()`・`reservation.getDetails()` を呼び出して、自分が包んでいる予約（元の `MeetingRoomReservation` かもしれないし、別のオプションが適用済みの予約かもしれない）の結果を取得し、そこに自分自身の加算・追記を行っているだけです。<br>
この「まず包んでいる相手に処理を委譲してから、自分の分を上乗せする」という実装により、オプションを何個・どんな順序で重ねても、それぞれのオプションクラスは自分が追加する内容だけを知っていればよくなります（継承ではなく委譲を使う理由は→ [【深堀り①】Decorator と継承の違い](#深堀り1)）。

次に、呼び出し側の実行クラスを見ていきましょう。

**`Main.java`**

```java
package example;

public class Main {
    public static void main(String[] args) {
        Reservation reservation1 = new MeetingRoomReservation("第1会議室", 2, 3000);
        System.out.println(reservation1.getDetails());
        System.out.println("料金：" + reservation1.getFee() + "円");

        Reservation reservation2 = new CateringOption(
                new ProjectorOption(new MeetingRoomReservation("第2会議室", 3, 4000)));
        System.out.println(reservation2.getDetails());
        System.out.println("料金：" + reservation2.getFee() + "円");

        Reservation reservation3 = new ProjectorOption(
                new ProjectorOption(new MeetingRoomReservation("第3会議室", 4, 3500)));
        System.out.println(reservation3.getDetails());
        System.out.println("料金：" + reservation3.getFee() + "円");
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

`Main` クラスを振り返ると、`reservation2`・`reservation3` のように、オプションクラスのコンストラクタへ別の `Reservation`（元の予約や、別のオプションを適用済みの予約）を渡すことで、好ましくない実装のようにコンストラクタへ大量の `boolean` 引数を並べることなく、必要なオプションだけを外側から重ねて組み立てられています。

`reservation1`・`reservation2` の実行結果は、好ましくない実装とまったく同じになっています。一方、`reservation3` は、同じ `ProjectorOption` を 2 回重ねることで「プロジェクターを 2 台手配する」という、好ましくない実装の `boolean` フィールドでは表現できなかった組み合わせを実現しています。

以上のような実装を行うと、以下のメリットがあります。

- 新しいオプション（例えば「Wi-Fi ルーター追加」）を増やす場合も、`ReservationOption` を継承した新しいクラスを追加するだけで済み、既存の `Reservation`・`MeetingRoomReservation` クラスや、追加済みの他のオプションクラスには一切手を加える必要がない。
- オプションはオブジェクトとして重ねる（委譲する）ため、`boolean` フィールドでは表現できなかった「同じオプションを複数回適用する」といった組み合わせにも対応できる。
- コンストラクタへオプションの有無を `boolean` の羅列で渡す必要がなくなり、`new CateringOption(new ProjectorOption(reservation))` のように、実際に適用したいオプションだけを、コード上でもそのまま読み取れる形で組み立てられる。

## まとめ

正しい実装を振り返ると、オプション（`ProjectorOption`・`CateringOption`・`RecordingOption`）は `MeetingRoomReservation` クラスに手を加えることなく、`Reservation` 型として外側から重ねて組み立てられるようになりました。<br>
このように、Decorator パターンは、機能を既存クラスの内部に追加するのではなく、既存クラスを変更せずに外側から包む専用のクラスとして追加することで、オプションの組み合わせや個数を実行時に自由に変えられるようにするパターンです。

本記事の内容はここまでとなります。

以降は「もう少し深く知りたい」という方向けの補足となります。今回学んだパターンに繋がる設計原則や、実務で役立つ背景知識について触れています。

---

<a id="深堀り1"></a>

## 【深堀り①】Decorator と継承の違い

好ましくない実装で挙げた `boolean` フィールドの代わりに、継承でオプションごとのサブクラスを作る方法（例えば `ProjectorReservation`・`CateringReservation`・両方を持つ `ProjectorCateringReservation` ……）を思いついた方もいるかもしれません。

この方法は、オプションが 1 種類だけなら問題になりませんが、オプションの種類が増えるほど組み合わせの数（2 のオプション数乗）に比例してクラス数が爆発的に増えてしまいます。今回のように 3 種類のオプションがあるだけでも、組み合わせは最大 7 通り（プロジェクターのみ・ケータリングのみ・……・全部乗せ）に達し、4 種類目のオプションが増えれば 15 通りまで膨れ上がります。

Decorator パターンは、この組み合わせをクラスの継承ではなく、オブジェクトを実行時に重ねる「委譲」によって表現します。オプションクラスは自分が追加する処理だけを知っていればよく、他のオプションと組み合わせるための専用クラスを別途用意する必要がありません。そのため、オプションの数が増えても、クラス数はオプションの種類数（今回なら 3 つ）に比例するだけで済みます。

<a id="深堀り2"></a>

## 【深堀り②】Java 標準ライブラリにおける Decorator パターンの例

Decorator パターンは、Java の標準ライブラリにも数多く使われています。代表的なのが `java.io` パッケージの入出力ストリームです。

```java
BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
```

このコードは、`System.in`（バイト単位の入力）を `InputStreamReader` クラスで文字単位の読み込みができるように変換し、さらにその結果を `BufferedReader` クラスで包むことで、内部にバッファを持たせて 1 行単位の読み込み（`readLine` メソッド）ができるようにしています。<br>
`InputStreamReader`・`BufferedReader` クラスは、いずれも本記事の `ReservationOption` クラスと同じように、内部に元のオブジェクトを保持し、そのオブジェクトの処理を呼び出しながら自分の機能を上乗せする形で実装されています。普段何気なく書いている入出力処理も、実は Decorator パターンの一例です。

<a id="深堀り3"></a>

## 【深堀り③】OCP（オープン・クローズドの原則）

正しい実装を振り返ると、新しいオプション（例えば「Wi-Fi ルーター追加」）を追加する場合、必要なのは抽象クラス `ReservationOption` を継承した新しいクラスを作成することだけで、既存の `Reservation`・`MeetingRoomReservation` クラスや、他のオプションクラスには一切手を加える必要がありません。<br>
呼び出し側が依存しているのは抽象クラス `Reservation` だけであるため、具体的な実装クラスがどう組み合わさっていても、料金計算・明細表示ともに対応できます。

この「既存コードを変えずに、新しいクラスを追加するだけで機能を拡張できる」という設計は、「**OCP（Open/Closed Principle：オープン・クローズドの原則）**」と呼ばれる設計原則の実践です。Decorator パターンは OCP を実現するための設計手段の一つと言えます。

詳しくは「OCP」や「オープン・クローズドの原則」で検索してみてください。

<a id="深堀り4"></a>

## 【深堀り④】GoF デザインパターンとの位置づけ

今回使った Decorator パターンは、GoF（Gang of Four）の 23 のデザインパターンのうち「構造パターン」に分類されます。<br>
詳しくは「GoF」で検索してみてください。
