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
- [【深堀り④】Observer パターンとの違い](#深堀り4)
- [【深堀り⑤】Java 標準ライブラリにおける Mediator パターンの例](#深堀り5)
- [【深堀り⑥】GoF デザインパターンとの位置づけ](#深堀り6)

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
        TransportRobot robotA = new TransportRobot("RobotA", 0);
        TransportRobot robotB = new TransportRobot("RobotB", 0);
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
RobotA: レーン1に進入しました
RobotB: レーン1はRobotAが使用中のため待機します
RobotB: レーン2に進入しました
```

※ここで一旦読むのを止めて、ご自身でコーディングを行なってみてください。その後で、続きを読んでください。

## 好ましくない実装

では、シナリオに従い追加実装をしていきましょう。

まず思いつくのは、相手ロボットへの参照が増えるので、`TransportRobot` クラスの `partner` フィールドをリスト化し、`enterLane` メソッド内にて、先のリストの中身を順番に確認する、という実装ではないでしょうか？

**`TransportRobot.java`**

```java
package example;

import java.util.ArrayList;
import java.util.List;

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
        TransportRobot robotA = new TransportRobot("RobotA", 0);
        TransportRobot robotB = new TransportRobot("RobotB", 0);
        TransportRobot robotC = new TransportRobot("RobotC", 0);
        TransportRobot robotD = new TransportRobot("RobotD", 0);
        TransportRobot robotE = new TransportRobot("RobotE", 0);

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
RobotA: レーン1に進入しました
RobotB: レーン1はRobotAが使用中のため待機します
RobotB: レーン2に進入しました

RobotE: レーン3に進入しました
RobotD: レーン3に進入しました
```

実行結果を振り返ると、`RobotA` と `RobotB` においては `RobotB` が `RobotA` の存在を検知してレーン 1 への進入を待機しており、正しく衝突を回避できています。<br>
一方、`RobotD` と `RobotE` においては `RobotD` が `RobotE` のいるレーン 3 に同時に進入しているため、衝突の回避ができていません。

この原因は、`RobotD` の登録処理にあります。`robotD.addOtherRobot(robotE)` の呼び出しだけが漏れているため、`robotA`・`robotB`・`robotC` の登録しかされていません。そのため、`RobotD` は `RobotE` の存在を知らないまま `enterLane` メソッドを呼び出すため、`RobotE` がレーン 3 にいることを検知できず、そのまま進入してしまいました。

このように、この実装には、次のような問題点があります。

- ロボットの台数が増えるほど、相互登録の組み合わせが爆発的に増加する（本記事の 5 台では 20 通り）ため、登録漏れが起きやすく、かつ気づきにくい。
- `TransportRobot` クラスの責務を考えると、本来保持すべきなのは自身の名前・現在のレーンといった状態と、指定されたレーンに移動するという振る舞いだけのはずである。ところが現状は、`enterLane` メソッドの中に他のロボットの状況を判定するロジックまで入り込んでおり、「移動する」という本来の責務に「他のロボットと調整する」という別の責務がすでに混在してしまっている。今後「優先度の高いロボットを優先させる」のような判定ルールが増えるほど、この混在はさらに複雑になっていく。

## 正しい実装

では、好ましくない実装で挙げた問題点を解決するにはどうすればよいのでしょうか？

これらの問題を解決するのが **Mediator パターン**です。ロボット同士が直接確認し合うのをやめ、レーンの空き状況を一元管理する仲介役のクラスを新たに用意することで、ロボットの台数が増えても、既存のロボット同士の参照を配線し直す必要がなくなります。

まず、ロボットと仲介役に共通する振る舞いを定義するインターフェースから見ていきましょう。

**`Mediator.java`**

```java
package example;

public interface Mediator {
    void requestEnterLane(Colleague colleague, int lane);
}
```

**`Colleague.java`**

```java
package example;

public interface Colleague {
    void setMediator(Mediator mediator);

    String getName();

    void onLaneGranted(int lane);

    void onLaneDenied(int lane, String occupiedBy);
}
```

インターフェース `Mediator` は、ロボットからレーンへの進入要求を受け取る `requestEnterLane` メソッドを 1 つだけ持ちます。<br>
インターフェース `Colleague` は、`Mediator` インターフェースを設定する `setMediator` メソッドと、`Mediator` インターフェースから進入の可否を伝えられる `onLaneGranted` メソッド・`onLaneDenied` メソッドを持ちます。ロボットはこの 2 つのメソッドで結果を受け取るだけでよく、他のロボットの状態を自分で確認する必要がなくなります。

次に、`Colleague` を実装した `TransportRobot` クラスを見ていきましょう。

**`TransportRobot.java`**

```java
package example;

public class TransportRobot implements Colleague {
    private final String name;
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

`TransportRobot` クラスを振り返ると、好ましくない実装にあった `otherRobots` フィールドと `addOtherRobot` メソッドがなくなりました。レーンへの進入は `requestEnterLane` メソッドで `mediator` に要求するだけで、判定結果は `onLaneGranted` メソッドまたは `onLaneDenied` メソッドとして受け取ります。

続いて、`Mediator` を実装した仲介役のクラスを見ていきましょう。

**`FleetController.java`**

```java
package example;

import java.util.ArrayList;
import java.util.List;

public class FleetController implements Mediator {
    private final List<TransportRobot> robots = new ArrayList<>();

    public void addRobot(TransportRobot robot) {
        robots.add(robot);
        robot.setMediator(this);
    }

    @Override
    public void requestEnterLane(Colleague colleague, int lane) {
        for (TransportRobot robot: robots) {
            if (robot != colleague && robot.getCurrentLane() == lane) {
                colleague.onLaneDenied(lane, robot.getName());
                return;
            }
        }
        colleague.onLaneGranted(lane);
    }
}
```

`FleetController` クラスは、登録された全ロボット（`robots` フィールド）を一元管理する仲介役です。`addRobot` メソッドでロボットを登録すると同時に、`robot.setMediator(this)` を呼び出し、自分自身をそのロボットの仲介役として設定します。<br>
`requestEnterLane` メソッドの中身を振り返ると、要求元のロボット自身（`colleague`）を除いた登録済みの全ロボットを確認し、指定したレーンを使用中のロボットが 1 台でもいれば `onLaneDenied` メソッドで待機を伝え、いなければ `onLaneGranted` メソッドで進入を許可します。好ましくない実装では各ロボットが個別に持っていた判定ロジックが、`FleetController` クラス 1 箇所に集約されています。

**`Main.java`**

```java
package example;

public class Main {
    public static void main(String[] args) {
        FleetController fleetController = new FleetController();

        TransportRobot robotA = new TransportRobot("RobotA", 0);
        TransportRobot robotB = new TransportRobot("RobotB", 0);
        TransportRobot robotC = new TransportRobot("RobotC", 0);
        TransportRobot robotD = new TransportRobot("RobotD", 0);
        TransportRobot robotE = new TransportRobot("RobotE", 0);

        fleetController.addRobot(robotA);
        fleetController.addRobot(robotB);
        fleetController.addRobot(robotC);
        fleetController.addRobot(robotD);
        fleetController.addRobot(robotE);

        robotE.requestEnterLane(3);
        robotD.requestEnterLane(3);
    }
}
```

**実行結果**

```
RobotE: レーン3に進入しました
RobotD: レーン3はRobotEが使用中のため待機します
```

`Main` クラスを振り返ると、ロボットの登録は `fleetController.addRobot(robotX)` を 1 回呼び出すだけで済み、好ましくない実装にあった、ロボット同士を総当たりで登録する処理がなくなりました。新しいロボットを追加する場合も、`FleetController` に登録する 1 行を追加するだけでよく、既存のロボットのクラスや `Main` クラスの他の部分を変更する必要はありません。<br>
実行結果を振り返ると、`RobotD` が `RobotE` の存在を正しく検知し、待機する結果になっています。好ましくない実装で発生していた、レーンへの同時進入という不具合が解消されました。

以上のような実装を行うと、以下のメリットがあります。

- ロボットの台数が増える場合、`FleetController` クラスに `addRobot` メソッドで登録するだけでよく、既存のロボットのクラス同士を相互に配線し直す必要がない。
- レーンの空き状況を判定するロジックが `FleetController` クラスの `requestEnterLane` メソッドに集約されているため、判定ルールを変更する場合も修正箇所が 1 箇所で済む。

## まとめ

正しい実装を振り返ると、`TransportRobot` クラスは、自分がレーンに進入したいという意思を `FleetController` クラスに伝えるだけで、他のロボットの状態を直接確認する処理を一切持っていません。<br>
このように、Mediator パターンは、複数のオブジェクトが互いを直接参照し合う代わりに、そのやり取りを 1 つの仲介役のオブジェクトに集約するパターンです。

本記事の内容はここまでとなります。

以降は「もう少し深く知りたい」という方向けの補足となります。今回学んだパターンに繋がる設計原則や、実務で役立つ背景知識について触れています。

---

<a id="深堀り1"></a>

## 【深堀り①】Mediator が肥大化するリスク（God Object 化）

正しい実装を振り返ると、レーンの空き状況を判定するロジックが `FleetController` クラスの `requestEnterLane` メソッド 1 箇所に集約されました。ロボットの台数やレーンの数が今後さらに増え、判定のルールが複雑化する（例えば「優先度の高い荷物を運ぶロボットを優先する」「特定のロボットは特定のレーンを使えない」など）と、`FleetController` クラス 1 つに全てのルールが集中し、クラス自体が肥大化していく可能性があります。

このように、複数のオブジェクト間の調整ロジックを 1 箇所に集めることで、その集約先自身が巨大化してしまう問題は、「**God Object（神オブジェクト）**」と呼ばれるアンチパターンとして知られています。

Mediator パターンは「複数オブジェクト間の複雑な依存関係を 1 箇所にまとめる」ことでコードの見通しを良くしますが、まとめた先の仲介役自身が複雑になりすぎないようにする責任までは肩代わりしてくれません。判定ルールが増えてきた場合は、`FleetController` クラスの中身をさらに小さなクラス（例えば、レーンの優先順位だけを判定するクラスなど）に分割するといった設計判断が必要になります。

<a id="深堀り2"></a>

## 【深堀り②】デメテルの法則との関係

好ましくない実装を振り返ると、`TransportRobot` クラスは、衝突を確認するために他の全ロボット（最大 4 台）への参照を直接保持する必要がありました。正しい実装により、`TransportRobot` クラスがやり取りする相手は `Mediator` インターフェース（実体は `FleetController` クラス）1 つだけになり、他のロボットの存在やインスタンスへの参照を一切持たなくてよくなっています。

この「やり取りするオブジェクトの数を減らす」という考え方は、「**デメテルの法則（Law of Demeter）**」と呼ばれる設計原則に沿っています。デメテルの法則は「最小知識の原則」とも呼ばれ、あるオブジェクトが直接やり取りするオブジェクトの範囲を必要最小限に留めるべきだという考え方です。

正しい実装の `TransportRobot` クラスがやり取りするオブジェクトは `Mediator` インターフェースだけになり、他の `TransportRobot` クラスのインスタンスの存在を知る必要がなくなりました。Mediator パターンは、このデメテルの法則を実践するための設計手段の一つと言えます。

詳しくは「デメテルの法則」や「最小知識の原則」で検索してみてください。

<a id="深堀り3"></a>

## 【深堀り③】SRP（単一責任の原則）

好ましくない実装の `TransportRobot` クラスを振り返ると、`enterLane` メソッドは「自分がレーンに進入する」処理と「他のロボットとレーンが重複していないか判定する」処理を、1 つのクラスの中に併せ持っていました。正しい実装では、前者を `TransportRobot` クラスの `onLaneGranted` メソッドなどに、後者を `FleetController` クラスの `requestEnterLane` メソッドにそれぞれ切り出し、1 つのクラスが担う責務を 1 つに絞っています。

この「1 つのクラスが持つ責務を 1 つに絞る」という考え方は、「**SRP（Single Responsibility Principle：単一責任の原則）**」と呼ばれる設計原則です。SRP は、あるクラスが変更される理由は 1 つだけであるべきだという考え方で、責務が複数混在していると、片方の都合による変更がもう片方に意図せず影響を及ぼすリスクが生まれます。

正しい実装では、レーンの判定ルールが変わっても `TransportRobot` クラスを変更する必要はなく、ロボット自身の振る舞いが変わっても `FleetController` クラスを変更する必要がありません。Mediator パターンは、複数のオブジェクト間の調整という責務を、各オブジェクト自身の責務から切り離して 1 つの仲介役に集約することで、SRP を実現する設計手段の一つと言えます。<br>
ただし、判定ルールの種類が今後さらに増えていくと、`FleetController` クラス自身が抱える責務が増えていき、結果として 1 つのクラスに複数の責務が集まってしまう可能性があります（→ [Mediator が肥大化するリスク（God Object 化）](#深堀り1)）。

詳しくは「SRP」や「単一責任の原則」で検索してみてください。

<a id="深堀り4"></a>

## 【深堀り④】Observer パターンとの違い

Mediator パターンは、「複数のオブジェクトの間で状態の変化を伝え合う」という点で Observer パターンと似ており、しばしば混同されます。両者を分けるのは、状態の変化を伝えた後、誰が判断を行うかという役割分担です。

Observer パターンは、Subject（状態を持つ側のオブジェクト）が Observer に対して「状態が変わったこと」を一律に通知するだけで、通知を受け取った後の判断はすべて Observer 自身に委ねられます。Subject は、通知先の Observer 同士の関係や、通知を受けてどう振る舞うべきかについては関知しません。<br>
一方 Mediator パターンでは、`FleetController` クラスの `requestEnterLane` メソッドのように、仲介役自身が `TransportRobot` クラスから受け取った情報をもとに判断を行い、`onLaneGranted` メソッドや `onLaneDenied` メソッドで個々の `TransportRobot` クラスに対して異なる結果を返します。

つまり、`TransportRobot` クラス同士の関係を調整する「判断のロジック」そのものを `FleetController` クラスが担っている点が、Observer パターンとの大きな違いです。

<a id="深堀り5"></a>

## 【深堀り⑤】Java 標準ライブラリにおける Mediator パターンの例

Java 標準ライブラリにおける Mediator パターンの例として、`java.util.Timer` クラスの `schedule` メソッドを見ていきましょう。

**`Timer.java`（一部抜粋）**

```java
public void schedule(TimerTask task, long delay) {
    if (delay < 0)
        throw new IllegalArgumentException("Negative delay.");
    sched(task, System.currentTimeMillis()+delay, 0);
}

private void sched(TimerTask task, long time, long period) {
    if (time < 0)
        throw new IllegalArgumentException("Illegal execution time.");

    // Constrain value of period sufficiently to prevent numeric
    // overflow while still being effectively infinitely large.
    if (Math.abs(period) > (Long.MAX_VALUE >> 1))
        period >>= 1;

    synchronized(queue) {
        if (!thread.newTasksMayBeScheduled)
            throw new IllegalStateException("Timer already cancelled.");

        synchronized(task.lock) {
            if (task.state != TimerTask.VIRGIN)
                throw new IllegalStateException(
                    "Task already scheduled or cancelled");
            task.nextExecutionTime = time;
            task.period = period;
            task.state = TimerTask.SCHEDULED;
        }

        queue.add(task);
        if (queue.getMin() == task)
            queue.notify();
    }
}
```

> 引用元: OpenJDK [Timer.java](https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/util/Timer.java)

`schedule` メソッドの中身を振り返ると、実際の処理は `sched` メソッドに委ねられています。`sched` メソッドは、渡された `TimerTask` のインスタンス（本記事の `Colleague` インターフェースの実装に相当）を `Timer` クラスが内部に持つ `queue` フィールドに追加しているだけで、他の `TimerTask` のインスタンスへの参照は一切登場しません。`queue` に追加されたタスクは、`Timer` クラスが内部で管理する専用のスレッドによって実行予定時刻が近い順に取り出され、順番に実行されます（この実行順序を管理する部分はスレッド間の同期処理が絡み複雑になるため、本記事では割愛します）。

本記事の `TransportRobot` クラスが `FleetController` クラスに進入を要求するだけでレーンの空き状況の判定を一切持たなかったのと同様に、`TimerTask` クラスも、自分がいつ実行されるか、他にどんなタスクが予約されているかを一切知る必要がなく、その調整はすべて `Timer` クラスに集約されています。

<a id="深堀り6"></a>

## 【深堀り⑥】GoF デザインパターンとの位置づけ

今回使った Mediator パターンは、GoF（Gang of Four）の 23 のデザインパターンのうち「振る舞いパターン」に分類されます。<br>
詳しくは「GoF」で検索してみてください。
