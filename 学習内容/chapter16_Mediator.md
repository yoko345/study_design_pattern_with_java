# Mediator（メディエーター）パターン ― オブジェクト同士のやり取りを 1 つの仲介役に集約する

次のような経験をしたことはありませんか？

> 複数のオブジェクトがお互いを直接参照し合う実装にしていたところ、後から新しいオブジェクトを追加することになり、既存のオブジェクトすべてに新しいオブジェクトとのやり取りを追加する羽目になった。オブジェクトの数が増えるたびに確認しなければならない組み合わせも増えていき、追加漏れによる思わぬ不具合を生んでしまうこともあった。

この記事では、倉庫内搬送ロボットの衝突回避システムを増強するシナリオを通して、Mediator パターンがこの問題をどのように解決するかを紹介します。

## 目次

- [【具体例】](#具体例)
    - [シナリオ](#シナリオ)
    - [既存コードの仕様](#既存コードの仕様)
- [好ましくない実装](#好ましくない実装)
- [正しい実装](#正しい実装)
- [まとめ](#まとめ)
- [【深堀り①】Mediator が肥大化するリスク（God Object 化）](#深堀り1)
- [【深堀り②】デメテルの法則との関係](#深堀り2)
- [【深堀り③】SRP（単一責任の原則）](#深堀り3)
- [【深堀り④】GoF デザインパターンとの位置づけ](#深堀り4)

---

## 【具体例】

### シナリオ

> あなたは物流倉庫の搬送システムの開発チームに所属しています。<br>
> 現在、倉庫内には 2 台の自動搬送ロボットが、棚から取り出した荷物を出荷エリアまで運んでいる状況です。棚と出荷エリアの間は 2 本のレーンでつながっており、ロボットは荷物を取り出した棚の位置に応じて、どちらのレーンを使うかが決まります。レーンは一本道のため、同じレーンに 2 台が同時に入るとすれ違えず衝突してしまう危険があります。そこで各ロボットはもう 1 台のロボットを直接参照し、目的のレーンにもう 1 台がいないかを確認してから進入する、というシンプルな仕組みで運用している状態です。<br>
> ある日、荷物量の増加に伴い、倉庫内のレーンを 3 本に増設し、搬送ロボットも 5 台に増やすことになりました。<br>
> あなたは、既存の「ロボット同士が直接確認し合う」仕組みを、5 台のロボットにも対応できる形に拡張することになりました。

※実際の搬送ロボットでは、駆動輪モーターの制御やセンサーによる障害物検知などの実装が必要ですが、本記事では Mediator パターンの解説に集中するため、コンソールへの文字列出力のみとします。

### 既存コードの仕様

※実務では、次の `TransportRobot` のようなエンティティクラスは `entity` パッケージなど専用のディレクトリに切り出すのが一般的です。しかし、本記事ではパッケージ構成を主題としないため `example` パッケージ直下にまとめています。

- `TransportRobot`（既存クラス）

搬送ロボット 1 台を表すクラスです。<br>
名前と現在のレーン番号、衝突を確認する相手ロボットへの参照を保持します。

| フィールド    | 型               | 説明                                     |
| ------------- | ---------------- | ---------------------------------------- |
| `name`        | `String`         | ロボット名                               |
| `currentLane` | `int`            | 現在のレーン番号（0 は待機エリアを表す） |
| `partner`     | `TransportRobot` | 衝突を確認する相手ロボットへの参照       |

| メソッド         | 引数                     | 戻り値の型 | 説明                                                                       |
| ---------------- | ------------------------ | ---------- | -------------------------------------------------------------------------- |
| `setPartner`     | `TransportRobot partner` | `void`     | 衝突を確認する相手ロボットを設定する                                       |
| `getName`        | なし                     | `String`   | ロボット名を取得する                                                       |
| `getCurrentLane` | なし                     | `int`      | 現在のレーン番号を取得する                                                 |
| `enterLane`      | `int lane`               | `void`     | 相手ロボットが指定したレーンにいなければ進入し、結果をコンソールに出力する |

**`TransportRobot.java`**

```java
package example;

public class TransportRobot {
    private String name;
    private int currentLane;
    private TransportRobot partner;

    public TransportRobot(String name, int currentLane) {
        this.name = name;
        this.currentLane = currentLane;
    }

    public void setPartner(TransportRobot partner) {
        this.partner = partner;
    }

    public String getName() {
        return name;
    }

    public int getCurrentLane() {
        return currentLane;
    }

    public void enterLane(int lane) {
        if (partner.getCurrentLane() == lane) {
            System.out.println(name + ": レーン" + lane + "は" + partner.getName() + "が使用中のため待機します");

            return;
        }

        currentLane = lane;
        System.out.println(name + ": レーン" + lane + "に進入しました");
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
        TransportRobot robotA = new TransportRobot("ロボットA", 0);
        TransportRobot robotB = new TransportRobot("ロボットB", 0);
        robotA.setPartner(robotB);
        robotB.setPartner(robotA);

        robotA.enterLane(1);
        robotB.enterLane(1);
        robotB.enterLane(2);
    }
}
```

※ コンストラクタの第 2 引数に渡している `0` は、まだどのレーンにも進入していない待機状態を表す値です。倉庫内のレーンは 1 から始まる番号で管理されています。

**実行結果**

```
ロボットA: レーン1に進入しました
ロボットB: レーン1はロボットAが使用中のため待機します
ロボットB: レーン2に進入しました
```

※ここで一旦読むのを止めて、ご自身でコーディングを行なってみてください。その後で、続きを読んでください。

## 好ましくない実装

では、シナリオに従い追加実装をしていきましょう。

まず思いつくのは、相手ロボットへの参照が増えるので、`TransportRobot` クラスの `partner` フィールドをリスト化し、`enterLane` メソッド内にて、先のリストの中身を順番に確認する、という実装ではないでしょうか？

**`TransportRobot.java`**

```java
package example;

public class TransportRobot {
    private String name;
    private int currentLane;
    /* ここを追加（ここから） */
    private List<TransportRobot> otherRobots = new ArrayList<>();
    /* ここを追加（ここまで） */

    public TransportRobot(String name, int currentLane) {
        this.name = name;
        this.currentLane = currentLane;
    }

    /* ここを追加（ここから） */
    public void addOtherRobot(TransportRobot robot) {
        otherRobots.add(robot);
    }
    /* ここを追加（ここまで） */

    public String getName() {
        return name;
    }

    public int getCurrentLane() {
        return currentLane;
    }

    public void enterLane(int lane) {
        /* ここを追加（ここから） */
        for (TransportRobot other: otherRobots) {
            if (other.getCurrentLane() == lane) {
                System.out.println(name + ": レーン" + lane + "は" + other.getName() + "が使用中のため待機します");

                return;
            }
        }
        /* ここを追加（ここまで） */

        currentLane = lane;
        System.out.println(name + ": レーン" + lane + "に進入しました");
    }
}
```

**`Main.java`**

```java
package example;

public class Main {
    public static void main(String[] args) {
        TransportRobot robotA = new TransportRobot("ロボットA", 0);
        TransportRobot robotB = new TransportRobot("ロボットB", 0);
        TransportRobot robotC = new TransportRobot("ロボットC", 0);
        TransportRobot robotD = new TransportRobot("ロボットD", 0);
        TransportRobot robotE = new TransportRobot("ロボットE", 0);

        robotA.addOtherRobot(robotB);
        robotA.addOtherRobot(robotC);
        robotA.addOtherRobot(robotD);
        robotA.addOtherRobot(robotE);

        robotB.addOtherRobot(robotA);
        robotB.addOtherRobot(robotC);
        robotB.addOtherRobot(robotD);
        robotB.addOtherRobot(robotE);

        robotC.addOtherRobot(robotA);
        robotC.addOtherRobot(robotB);
        robotC.addOtherRobot(robotD);
        robotC.addOtherRobot(robotE);

        robotD.addOtherRobot(robotA);
        robotD.addOtherRobot(robotB);
        robotD.addOtherRobot(robotC);

        robotE.addOtherRobot(robotA);
        robotE.addOtherRobot(robotB);
        robotE.addOtherRobot(robotC);
        robotE.addOtherRobot(robotD);

        robotA.enterLane(1);
        robotB.enterLane(1);
        robotB.enterLane(2);

        System.out.println();

        robotE.enterLane(3);
        robotD.enterLane(3);
    }
}
```

**実行結果**

```
ロボットA: レーン1に進入しました
ロボットB: レーン1はロボットAが使用中のため待機します
ロボットB: レーン2に進入しました

ロボットE: レーン3に進入しました
ロボットD: レーン3に進入しました
```

実行結果を振り返ると、`ロボットA` と `ロボットB` においては `ロボットB` が `ロボットA` の存在を検知してレーン 1 への進入を待機しており、正しく衝突を回避できています。<br>
一方、`ロボットD` と `ロボットE` においては `ロボットD` が `ロボットE` のいるレーン 3 に同時に進入しているため、衝突の回避ができていません。

この原因は、`ロボットD` の登録処理にあります。`robotD.addOtherRobot(robotE)` の呼び出しが漏れているため、`robotA`・`robotB`・`robotC` の登録しかされていません。そのため、`ロボットD` は `ロボットE` の存在を知らないまま `enterLane` メソッドを呼び出すため、`ロボットE` がレーン 3 にいることを検知できず、そのまま進入してしまいました。

このように、この実装には、次のような問題点があります。

- ロボットの台数が増えるほど、相互登録の組み合わせが爆発的に増加する（本記事の 5 台では 20 通り）ため、登録漏れが起きやすく、かつ気づきにくい。
- `TransportRobot` クラスが本来保持するべき責務は、自身の名前・現在のレーンといった状態と、指定されたレーンに移動するといった振る舞いだけのはずである。しかし現状の `enterLane` メソッドには、他のロボットの状況を判定するロジックまで入り込んでしまっており、「移動する」という本来の責務に「他のロボットと調整する」という別の責務が混在している。今後「優先度の高いロボットを優先させる」のような判定ルールが増えるほど、この混在はさらに複雑になっていく。

## 正しい実装

では、好ましくない実装で挙げた問題点を解決するにはどうすればよいのでしょうか？

これらの問題を解決するのが **Mediator パターン**です。<br>
ロボット同士が直接確認し合うのをやめ、レーンの空き状況を一元管理する仲介役のクラスを新たに用意することで、ロボットの台数が増えても、既存のロボット同士の参照を配線し直す必要がなくなります。

まず、ロボット側に共通する振る舞いを定義するインターフェースから見ていきましょう。

**`Colleague.java`**

```java
package example;

public interface Colleague {
    void setMediator(Mediator mediator);

    String getName();

    int getCurrentLane();

    void onLaneGranted(int lane);

    void onLaneDenied(int lane, String occupiedBy);
}
```

`Colleague` は新たに追加したインターフェースで、`Mediator` インターフェースを設定する `setMediator` メソッドと、`Mediator` インターフェースから進入の可否を伝えられる `onLaneGranted`・`onLaneDenied` メソッドを持ちます。また、`getName`・`getCurrentLane` メソッドは、`Colleague` インターフェースを実装したクラスが `Colleague` インターフェースだけを通じてロボットの状態を参照する役割を持っています。

次に、インターフェース `Colleague` を実装したクラスを見ていきましょう。

**`TransportRobot.java`**

```java
package example;

public class TransportRobot implements Colleague {
    private String name;
    private int currentLane;
    private Mediator mediator;

    public TransportRobot(String name, int currentLane) {
        this.name = name;
        this.currentLane = currentLane;
    }

    @Override
    public void setMediator(Mediator mediator) {
        this.mediator = mediator;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getCurrentLane() {
        return currentLane;
    }

    public void requestEnterLane(int lane) {
        mediator.requestEnterLane(this, lane);
    }

    @Override
    public void onLaneGranted(int lane) {
        currentLane = lane;
        System.out.println(name + ": レーン" + lane + "に進入しました");
    }

    @Override
    public void onLaneDenied(int lane, String occupiedBy) {
        System.out.println(name + ": レーン" + lane + "は" + occupiedBy + "が使用中のため待機します");
    }
}
```

`TransportRobot` クラスを振り返ると、インターフェース `Colleague` を実装しています。これにより、`getName`・`getCurrentLane` メソッドはオーバーライドされています。また、既存の仕様にあった `partner` フィールドと `setPartner` メソッドが、`mediator` フィールドと `setMediator` メソッドに置き換わっています。さらに、衝突判定を行っていた `enterLane` メソッドがなくなり、レーンへの進入は `requestEnterLane` メソッドで `mediator` に要求するだけとなり、判定結果は `onLaneGranted`・`onLaneDenied` メソッドの引数として渡された内容だけを利用します。

次に、仲介役に共通する振る舞いを定義するインターフェースを見ていきましょう。

**`Mediator.java`**

```java
package example;

public interface Mediator {
    void requestEnterLane(Colleague colleague, int lane);
}
```

`Mediator` は新たに追加したインターフェースで、ロボットからレーンへの進入要求を受け取る `requestEnterLane` メソッドを 1 つだけ持ちます。

次に、インターフェース `Mediator` を実装したクラスを見ていきましょう。

**`RobotManager.java`**

```java
package example;

public class RobotManager implements Mediator {
    private final List<Colleague> robots = new ArrayList<>();

    public void addRobot(Colleague robot) {
        robots.add(robot);
        robot.setMediator(this);
    }

    @Override
    public void requestEnterLane(Colleague colleague, int lane) {
        for (Colleague robot: robots) {
            if (robot != colleague && robot.getCurrentLane() == lane) {
                colleague.onLaneDenied(lane, robot.getName());

                return;
            }
        }

        colleague.onLaneGranted(lane);
    }
}
```

`RobotManager` は新たに追加されたクラスで、`robots` フィールドに登録済みの全ロボットを保持します。<br>
`addRobot` メソッドでロボットを登録すると同時に、`robot.setMediator(this)` を呼び出し、自分自身をそのロボットの仲介役として設定します。<br>
`requestEnterLane` メソッドでは、要求元のロボット自身を除いて登録済みの全ロボットを確認し、指定したレーンを使用中のロボットが 1 台でもいれば `onLaneDenied` メソッドで待機を伝え、いなければ `onLaneGranted` メソッドで進入を許可します。

最後に、実行クラスを見てみましょう。

**`Main.java`**

```java
package example;

public class Main {
    public static void main(String[] args) {
        RobotManager robotManager = new RobotManager();

        TransportRobot robotA = new TransportRobot("ロボットA", 0);
        TransportRobot robotB = new TransportRobot("ロボットB", 0);
        TransportRobot robotC = new TransportRobot("ロボットC", 0);
        TransportRobot robotD = new TransportRobot("ロボットD", 0);
        TransportRobot robotE = new TransportRobot("ロボットE", 0);

        robotManager.addRobot(robotA);
        robotManager.addRobot(robotB);
        robotManager.addRobot(robotC);
        robotManager.addRobot(robotD);
        robotManager.addRobot(robotE);

        robotA.requestEnterLane(1);
        robotB.requestEnterLane(1);
        robotB.requestEnterLane(2);

        System.out.println();

        robotE.requestEnterLane(3);
        robotD.requestEnterLane(3);
    }
}
```

**実行結果**

```
ロボットA: レーン1に進入しました
ロボットB: レーン1はロボットAが使用中のため待機します
ロボットB: レーン2に進入しました

ロボットE: レーン3に進入しました
ロボットD: レーン3はロボットEが使用中のため待機します
```

`Main` クラスを振り返ると、`RobotManager` のインスタンスを 1 つ生成しています。また、既存の仕様にあった、ロボット同士が `setPartner` メソッドで互いを直接設定し合う処理がなくなり、`robotManager.addRobot(robotX)` を 1 回呼び出すだけで登録が完了するようになっています。さらに、レーンへの進入も `enterLane` メソッドの呼び出しから `requestEnterLane` メソッドの呼び出しに置き換わっています。

実行結果を振り返ると、`ロボットA` と `ロボットB` においては既存コードの結果と一致しています。また、`ロボットD` と `ロボットE` においては `ロボットD` が `ロボットE` の存在を正しく検知し、待機する結果になっています。これにより、好ましくない実装で発生していた、レーンへの同時進入という不具合が解消されています。

以上のような実装を行うと、以下のメリットがあります。

- ロボットの台数が増えても、`RobotManager` クラスに `addRobot` メソッドで登録するだけでよく、好ましくない実装の問題点にあった「相互登録」による不具合がすべて解消されている。
    - これは、`RobotManager` クラスがインターフェース `Colleague` の型を通じてロボットを扱うため、ロボットの具象クラスを一切知らずに済むためである。
- レーンの空き状況を判定するロジックが `RobotManager` クラスの `requestEnterLane` メソッドに集約されているため、`TransportRobot` クラスは自身の名前・現在のレーンといった状態と、指定されたレーンに移動するといった振る舞いだけの責務になっている。
    - その結果、判定ルールが変更（例えば「優先度の高いロボットを優先させる」）したとしても、`RobotManager` クラスの `requestEnterLane` メソッドの修正だけで済み、責務が混在して複雑になるということが生じない。
    - また、ロボット自身は `onLaneGranted`・`onLaneDenied` メソッドで判定結果を受け取るだけでよく、他のロボットの状態を自分で確認する必要もなくなる。

## まとめ

正しい実装を振り返ると、`TransportRobot` クラス同士が互いを直接参照することはなくなり、レーンの空き状況の判定はすべて `RobotManager` クラスに集約されています。<br>
このように Mediator パターンは、複数のオブジェクトが互いを直接参照し合う代わりに、そのやり取りを 1 つの仲介役のオブジェクトに集約する設計パターンです。

本記事の内容はここまでとなります。

以降は「もう少し深く知りたい」という方向けの補足となります。今回学んだパターンに繋がる設計原則や、実務で役立つ背景知識について触れています。

---

<a id="深堀り1"></a>

## 【深堀り①】Mediator が肥大化するリスク（God Object 化）

正しい実装を振り返ると、レーンの空き状況を判定するロジックが `RobotManager` クラスの `requestEnterLane` メソッド 1 箇所に集約されました。ロボットの台数やレーンの数が今後さらに増え、判定のルールが複雑化する（例えば「優先度の高い荷物を運ぶロボットを優先する」）と、`RobotManager` クラス 1 つに全てのルールが集中し、クラス自体が肥大化していく可能性があります。

このように、複数のオブジェクト間の調整ロジックを 1 箇所に集めることで、その集約先自身が巨大化してしまう問題は、「**God Object（神オブジェクト）**」と呼ばれるアンチパターンとして知られています。

Mediator パターンは「複数オブジェクト間の複雑な依存関係を 1 箇所にまとめる」ことでコードの見通しを良くしますが、まとめた先の仲介役自身が複雑になりすぎないようにする責任までは肩代わりしてくれません。判定ルールが増えてきた場合は、`RobotManager` クラスの中身をさらに小さなクラスに分割するといった設計判断が必要になります。

<a id="深堀り2"></a>

## 【深堀り②】デメテルの法則との関係

好ましくない実装を振り返ると、`TransportRobot` クラスは、`otherRobots` フィールドにより、衝突を確認するために他の全ロボットへの参照を直接保持する必要がありました。正しい実装では、`TransportRobot` クラスは `mediator` フィールドを通じて `Mediator` インターフェースだけを保持すればよく、他のロボットへの参照を一切持たなくてよくなっています。

この「やり取りするオブジェクトの数を減らす」という考え方は、「**デメテルの法則（Law of Demeter）**」と呼ばれる設計原則に沿っています。デメテルの法則は「最小知識の原則」とも呼ばれ、あるオブジェクトが直接やり取りするオブジェクトの範囲を必要最小限に留めるべきだという考え方です。

正しい実装の `TransportRobot` クラスがやり取りするオブジェクトは `Mediator` インターフェースだけになり、他の `TransportRobot` クラスのインスタンスの存在を知る必要がなくなりました。Mediator パターンは、このデメテルの法則を実践するための設計手段の一つと言えます。

詳しくは「デメテルの法則」や「最小知識の原則」で検索してみてください。

<a id="深堀り3"></a>

## 【深堀り③】SRP（単一責任の原則）

好ましくない実装を振り返ると、`TransportRobot` クラスの `enterLane` メソッドは、「自分がレーンに進入する」処理と「他のロボットとレーンが重複していないか判定する」処理を、1 つのクラスの中に併せ持っていました。正しい実装により、「自分がレーンに進入する」処理は `TransportRobot` クラスの `onLaneGranted` メソッドに、「他のロボットとレーンが重複していないか判定する」処理は `RobotManager` クラスの `requestEnterLane` メソッドにそれぞれ切り出すことで、1 つのクラスが担う責務を 1 つに絞っています。

この「1 つのクラスが持つ責務を 1 つに絞る」という考え方は、「**SRP（Single Responsibility Principle：単一責任の原則）**」と呼ばれる設計原則です。SRP は、あるクラスが変更される理由は 1 つだけであるべきだという考え方です。

正しい実装の `TransportRobot`・`RobotManager` クラスは、それぞれ「自分がレーンに進入する」処理と「他のロボットとの重複判定」処理という、単一の責務だけを持つようになりました。Mediator パターンは、この SRP を実践するための設計手段の一つと言えます。<br>
ただし、判定ルールの種類が今後さらに増えていくと、`RobotManager` クラス自身が抱える責務が増えていき、結果として 1 つのクラスに複数の責務が集まってしまう可能性があります（→ [Mediator が肥大化するリスク（God Object 化）](#深堀り1)）。

詳しくは「SRP」や「単一責任の原則」で検索してみてください。

<a id="深堀り4"></a>

## 【深堀り④】GoF デザインパターンとの位置づけ

今回使った Mediator パターンは、GoF（Gang of Four）の 23 のデザインパターンのうち「振る舞いパターン」に分類されます。<br>
詳しくは「GoF」で検索してみてください。
