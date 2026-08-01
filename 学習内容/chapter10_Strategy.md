# Strategy（ストラテジー）パターン ― アルゴリズムを実行時に交換可能にする

次のような経験をしたことはありませんか？

> 「新しい処理を追加する」という要望が来るたびに、同じメソッドに条件分岐を継ぎ足していった。その結果、新しい処理を 1 つ追加するだけなのに、既存の分岐まで読み解いて修正しなければならなくなった。

この記事では、配車アプリの経路探索機能を通して、Strategy パターンがこの問題をどのように解決するかを紹介します。

## 目次

- [【具体例】](#具体例)
    - [シナリオ](#シナリオ)
    - [既存コードの仕様](#既存コードの仕様)
- [好ましくない実装](#好ましくない実装)
- [正しい実装](#正しい実装)
- [まとめ](#まとめ)
- [【深堀り①】Bridge パターンとの類似点](#深堀り1)
- [【深堀り②】Template Method パターンとの比較](#深堀り2)
- [【深堀り③】OCP（オープン・クローズドの原則）](#深堀り3)
- [【深堀り④】GoF デザインパターンとの位置づけ](#深堀り4)

---

## 【具体例】

### シナリオ

> あなたは配車アプリを開発するチームに所属しています。<br>
> 現在、目的地までの経路探索は「最短距離優先」のロジックのみに対応しています。<br>
> ある日、利用者から「急いでいるときは高速道路を使ってでも早く着きたい」「ギリギリまで高速道路を使いたいわけではないので、距離と時間のバランスが良いルートを選びたい」といった要望が相次いで寄せられました。<br>
> そこで、利用者が「最短距離優先」「最短時間優先」「バランス重視」の 3 つから探索方針を選べるように、経路探索機能を改善することになりました。

※実際の経路探索では地図 API や道路ネットワークデータとの連携、リアルタイムの渋滞状況の考慮などを行う実装が必要ですが、本記事では Strategy パターンの解説に集中するため、緯度・経度から簡易的に近似値を計算した処理とし、結果はコンソールへの文字列出力のみとします。

### 既存コードの仕様

※実務では、次の `Location` のような地点情報を表すエンティティクラスは `entity` パッケージなど専用のディレクトリに切り出すのが一般的です。しかし、本記事ではパッケージ構成を主題としないため `example` パッケージ直下にまとめています。

- `Location`（既存クラス）

出発地・目的地といった地点を表すクラスです。<br>
地点名と緯度・経度を保持し、他の地点までの直線距離を計算するメソッドを持ちます。

| フィールド  | 型       | 説明   |
| ----------- | -------- | ------ |
| `name`      | `String` | 地点名 |
| `latitude`  | `double` | 緯度   |
| `longitude` | `double` | 経度   |

| メソッド     | 戻り値の型 | 説明                                     |
| ------------ | ---------- | ---------------------------------------- |
| `distanceTo` | `double`   | 指定した地点までの直線距離（km）を求める |

**`Location.java`**

```java
package example;

public class Location {
    private String name;
    private double latitude;
    private double longitude;

    public Location(String name, double latitude, double longitude) {
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public double distanceTo(Location other) {
        double dx = (this.longitude - other.longitude) * 91.0;
        double dy = (this.latitude - other.latitude) * 111.0;
        return Math.sqrt(dx * dx + dy * dy);
    }
}
```

※`dx`、`dy` で登場した `91.0` と `111.0` は、それぞれ経度・緯度 1 度あたりの距離（km）の近似値です。`91.0` は東京・横浜付近（北緯 35 度前後）を前提にした近似値です（経度 1 度あたりの距離は緯度によって変わります）。`111.0` は地球上どこでも共通の値（地球の子午線の全周 約 40,000km ÷ 360 度）です。

<br>

- `Route`（既存クラス）

経路探索の結果を表すクラスです。<br>
距離・所要時間・有料道路の利用有無・道路種別を保持します。

| フィールド         | 型        | 説明                 |
| ------------------ | --------- | -------------------- |
| `distanceKm`       | `double`  | 距離（km）           |
| `estimatedMinutes` | `int`     | 所要時間（分）       |
| `usesTollRoad`     | `boolean` | 有料道路を利用するか |
| `roadType`         | `String`  | 道路種別             |

| メソッド   | 戻り値の型 | 説明                       |
| ---------- | ---------- | -------------------------- |
| `toString` | `String`   | 経路情報を文字列に変換する |

**`Route.java`**

```java
package example;

public class Route {
    private double distanceKm;
    private int estimatedMinutes;
    private boolean usesTollRoad;
    private String roadType;

    public Route(double distanceKm, int estimatedMinutes, boolean usesTollRoad, String roadType) {
        this.distanceKm = distanceKm;
        this.estimatedMinutes = estimatedMinutes;
        this.usesTollRoad = usesTollRoad;
        this.roadType = roadType;
    }

    @Override
    public String toString() {
        return String.format("%s経由 / 距離 %.1fkm / 所要時間 約%d分 / 有料道路 %s",
                roadType, distanceKm, estimatedMinutes, usesTollRoad ? "利用" : "利用なし");
    }
}
```

<br>

- `RouteNavigator`（既存クラス）

出発地・目的地を受け取り、経路探索を行うクラスです。<br>
現状は最短距離優先のロジックのみに対応しています。

| フィールド    | 型         | 説明   |
| ------------- | ---------- | ------ |
| `origin`      | `Location` | 出発地 |
| `destination` | `Location` | 目的地 |

| メソッド    | 戻り値の型 | 説明                         |
| ----------- | ---------- | ---------------------------- |
| `findRoute` | `Route`    | 最短距離優先で経路を探索する |

**`RouteNavigator.java`**

```java
package example;

public class RouteNavigator {
    private Location origin;
    private Location destination;

    public RouteNavigator(Location origin, Location destination) {
        this.origin = origin;
        this.destination = destination;
    }

    public Route findRoute() {
        double distanceKm = origin.distanceTo(destination);
        int estimatedMinutes = (int) Math.round(distanceKm / 40.0 * 60);
        return new Route(distanceKm, estimatedMinutes, false, "一般道路");
    }
}
```

※`40.0` は一般道路における想定平均速度（km/h）です。

<br>

- `Main`（実行クラス）

**`Main.java`**

```java
package example;

public class Main {
    public static void main(String[] args) {
        Location origin = new Location("東京駅", 35.681236, 139.767125);
        Location destination = new Location("横浜駅", 35.465685, 139.622239);

        RouteNavigator navigator = new RouteNavigator(origin, destination);
        Route route = navigator.findRoute();

        System.out.println("案内経路: " + route);
    }
}
```

**実行結果**

```
案内経路: 一般道路経由 / 距離 27.3km / 所要時間 約41分 / 有料道路 利用なし
```

※ここで一旦読むのを止めて、ご自身でコーディングを行なってみてください。その後で、続きを読んでください。

## 好ましくない実装

では、シナリオに従い追加実装をしていきましょう。

真っ先に思いつくのは、`RouteNavigator` クラスに探索モードを表すフィールドを追加し、`findRoute` メソッド内でモードに応じた条件分岐を書き足す、という実装ではないでしょうか？

**`RouteNavigator.java`**

```java
package example;

public class RouteNavigator {
    public static final int MODE_SHORTEST_DISTANCE = 0;
    public static final int MODE_SHORTEST_TIME = 1;
    public static final int MODE_BALANCED = 2;

    private Location origin;
    private Location destination;
    private int mode;

    public RouteNavigator(Location origin, Location destination, int mode) {
        this.origin = origin;
        this.destination = destination;
        this.mode = mode;
    }

    public Route findRoute() {
        double straightDistance = origin.distanceTo(destination);

        if (mode == MODE_SHORTEST_DISTANCE) {
            int estimatedMinutes = (int) Math.round(straightDistance / 40.0 * 60);
            return new Route(straightDistance, estimatedMinutes, false, "一般道路");
        } else if (mode == MODE_SHORTEST_TIME) {
            double distanceKm = straightDistance * 1.2;
            int estimatedMinutes = (int) Math.round(distanceKm / 80.0 * 60);
            return new Route(distanceKm, estimatedMinutes, true, "高速道路");
        } else {
            double distanceKm = straightDistance * 1.1;
            int estimatedMinutes = (int) Math.round(distanceKm / 60.0 * 60);
            return new Route(distanceKm, estimatedMinutes, true, "一般道路＋一部高速道路");
        }
    }
}
```

※高速道路はインターチェンジを経由する必要があるため、直線距離よりも実際の走行距離はやや長くなります。このことを考慮に入れて、最短時間優先は高速道路をメインに使うため `straightDistance` を `1.2` 倍、バランス重視は一部区間のみ高速道路を使い迂回が少ない分、より小さい `1.1` 倍としています。また、`80.0` は高速道路における想定平均速度（km/h）、`60.0` は一般道路＋一部高速道路（混在区間）における想定平均速度（km/h）です。

**`Main.java`**

```java
package example;

public class Main {
    public static void main(String[] args) {
        Location origin = new Location("東京駅", 35.681236, 139.767125);
        Location destination = new Location("横浜駅", 35.465685, 139.622239);

        RouteNavigator distanceNavigator = new RouteNavigator(origin, destination, RouteNavigator.MODE_SHORTEST_DISTANCE);
        System.out.println("[最短距離優先] " + distanceNavigator.findRoute());

        RouteNavigator timeNavigator = new RouteNavigator(origin, destination, RouteNavigator.MODE_SHORTEST_TIME);
        System.out.println("[最短時間優先] " + timeNavigator.findRoute());

        RouteNavigator balancedNavigator = new RouteNavigator(origin, destination, RouteNavigator.MODE_BALANCED);
        System.out.println("[バランス重視] " + balancedNavigator.findRoute());
    }
}
```

**実行結果**

```
[最短距離優先] 一般道路経由 / 距離 27.3km / 所要時間 約41分 / 有料道路 利用なし
[最短時間優先] 高速道路経由 / 距離 32.8km / 所要時間 約25分 / 有料道路 利用
[バランス重視] 一般道路＋一部高速道路経由 / 距離 30.1km / 所要時間 約30分 / 有料道路 利用
```

コンパイルエラーがなく結果が出力されていることから、一見すると実装・動作確認ともに問題ないように見えます。

しかし、この実装には以下の問題点があります。

- 新しい探索方針（例えば「車体の都合上、道幅の広い経路」など）を追加するたびに `findRoute` メソッド内の条件分岐を直接修正する必要があり、既存の分岐に影響を与えるリスクがある。
- 3 つの探索ロジック（最短距離優先・最短時間優先・バランス重視）が 1 つのメソッド内に同居しているため、個別にテストしたり、他の場所で再利用したりといったことがしにくくなる。

## 正しい実装

では、好ましくない実装で挙げた問題点を解決するにはどうすればよいのでしょうか？

これらの問題を解決するのが **Strategy パターン**です。

まず、新たに追加する探索ロジックの共通インターフェースを見ていきましょう。

**`RouteSearchStrategy.java`**

```java
package example;

public interface RouteSearchStrategy {
    Route find(Location origin, Location destination);
}
```

`RouteSearchStrategy` を振り返ると、出発地・目的地を受け取って経路を探索する抽象メソッド `find` を定義しています。

次に、インターフェース `RouteSearchStrategy` を実装した具体的な探索ロジックを見ていきましょう。

**`ShortestDistanceStrategy.java`**

```java
package example;

public class ShortestDistanceStrategy implements RouteSearchStrategy {
    @Override
    public Route find(Location origin, Location destination) {
        double distanceKm = origin.distanceTo(destination);
        int estimatedMinutes = (int) Math.round(distanceKm / 40.0 * 60);
        return new Route(distanceKm, estimatedMinutes, false, "一般道路");
    }
}
```

**`ShortestTimeStrategy.java`**

```java
package example;

public class ShortestTimeStrategy implements RouteSearchStrategy {
    @Override
    public Route find(Location origin, Location destination) {
        double distanceKm = origin.distanceTo(destination) * 1.2;
        int estimatedMinutes = (int) Math.round(distanceKm / 80.0 * 60);
        return new Route(distanceKm, estimatedMinutes, true, "高速道路");
    }
}
```

**`BalancedStrategy.java`**

```java
package example;

public class BalancedStrategy implements RouteSearchStrategy {
    @Override
    public Route find(Location origin, Location destination) {
        double distanceKm = origin.distanceTo(destination) * 1.1;
        int estimatedMinutes = (int) Math.round(distanceKm / 60.0 * 60);
        return new Route(distanceKm, estimatedMinutes, true, "一般道路＋一部高速道路");
    }
}
```

インターフェース `RouteSearchStrategy` を実装したクラス（`ShortestDistanceStrategy`・`ShortestTimeStrategy`・`BalancedStrategy`）を振り返ると、抽象メソッド `find` をオーバーライドし、それぞれ固有の処理のみを担っています。好ましくない実装のように条件分岐の中にロジックが記述されず、各クラスで独立した状態となっているため、他の探索ロジックの実装を意識する必要がありません。

次に、既存のクラス `RouteNavigator` を見ていきましょう。

**`RouteNavigator.java`**

```java
package example;

public class RouteNavigator {
    private Location origin;
    private Location destination;
    private RouteSearchStrategy strategy;

    public RouteNavigator(Location origin, Location destination, RouteSearchStrategy strategy) {
        this.origin = origin;
        this.destination = destination;
        this.strategy = strategy;
    }

    public Route findRoute() {
        return strategy.find(origin, destination);
    }
}
```

`RouteNavigator` クラスを振り返ると、既存の仕様に加えて `RouteSearchStrategy` 型のフィールドをコンストラクタで受け取るようにし、探索処理は `RouteSearchStrategy` を実装したクラスに委譲するだけになっています。

最後に、実行クラスを見ていきましょう。

**`Main.java`**

```java
package example;

public class Main {
    public static void main(String[] args) {
        Location origin = new Location("東京駅", 35.681236, 139.767125);
        Location destination = new Location("横浜駅", 35.465685, 139.622239);

        RouteNavigator distanceNavigator = new RouteNavigator(origin, destination, new ShortestDistanceStrategy());
        System.out.println("[最短距離優先] " + distanceNavigator.findRoute());

        RouteNavigator timeNavigator = new RouteNavigator(origin, destination, new ShortestTimeStrategy());
        System.out.println("[最短時間優先] " + timeNavigator.findRoute());

        RouteNavigator balancedNavigator = new RouteNavigator(origin, destination, new BalancedStrategy());
        System.out.println("[バランス重視] " + balancedNavigator.findRoute());
    }
}
```

**実行結果**

```
[最短距離優先] 一般道路経由 / 距離 27.3km / 所要時間 約41分 / 有料道路 利用なし
[最短時間優先] 高速道路経由 / 距離 32.8km / 所要時間 約25分 / 有料道路 利用
[バランス重視] 一般道路＋一部高速道路経由 / 距離 30.1km / 所要時間 約30分 / 有料道路 利用
```

`Main` クラスを振り返ると、`RouteNavigator` クラスのコンストラクタに具体的な探索ロジックを記述しているクラス（`ShortestDistanceStrategy`・`ShortestTimeStrategy`・`BalancedStrategy`）を渡すだけで、欲しい探索方針の情報が得られています。

以上のような実装を行うと、以下のメリットがあります。

- 新しい探索方針（例えば「車体の都合上、道幅の広い経路」など）を追加したい場合も、`RouteSearchStrategy` インターフェースを実装した新しいクラスを追加するだけで済み、`RouteNavigator`・`ShortestDistanceStrategy`・`ShortestTimeStrategy`・`BalancedStrategy` といった既存クラスの実装には一切手を加える必要がなくなる。
    - 好ましくない実装のように `findRoute` メソッド内の条件分岐を直接修正し、既存の分岐に影響を与えるリスクを負う必要がない。
- `RouteSearchStrategy` を実装した 3 つのクラス（`ShortestDistanceStrategy`・`ShortestTimeStrategy`・`BalancedStrategy`）がそれぞれ独立しているため、個別にテストしたり、他の場所で再利用したりすることが容易になる。
    - 好ましくない実装のように、3 つのロジックが 1 つのメソッド内に同居していない。

## まとめ

Strategy パターンは、アルゴリズムをインターフェースの背後に隠し、利用する側のコードを変更せずに切り替え可能にするパターンです。<br>
本記事では、経路探索の方針を `RouteNavigator` クラスから切り離したことで、探索ロジックの追加が既存コードに影響を与えなくなりました。

条件分岐によって処理内容を切り替えたくなったときは、分岐の中身がそれぞれ独立したアルゴリズムとして切り出せないかを意識すると、Strategy パターンとして実装に落とし込みやすくなります。

本記事の内容はここまでとなります。

以降は「もう少し深く知りたい」という方向けの補足となります。今回学んだパターンに繋がる設計原則や、実務で役立つ背景知識について触れています。

---

<a id="深堀り1"></a>

## 【深堀り①】Bridge パターンとの類似点

本記事の `RouteNavigator` クラスを振り返ると、`RouteSearchStrategy` 型のフィールドを持ち、経路探索の具体的な処理をそのインスタンスに委譲しています。この「処理を切り替え可能なオブジェクトに委譲する」という構造は、「**Bridge パターン**」の実装側の構造とよく似ています。

ただし、両者の目的は異なります。<br>
Bridge パターンは、抽象化（機能）と実装（手段）という 2 つの継承階層そのものを分離することを目的としており、実装側では複数のメソッドをまとめて 1 つの実装として切り替えます。<br>
一方、Strategy パターンは、アルゴリズム（処理内容）を切り替え可能にすることを目的としており、通常は 1 つの振る舞いを差し替えます。そのため、`RouteSearchStrategy` の実装クラスのように、1 つのメソッド（`find` メソッド）を差し替えます。

つまり、Strategy パターンは切り替える対象が「1 つのメソッド」であるのに対し、Bridge パターンは切り替える対象が「複数のメソッドからなる実装階層全体」である、という点が異なります。

<a id="深堀り2"></a>

## 【深堀り②】Template Method パターンとの比較

本記事の `RouteNavigator` クラスを振り返ると、`RouteSearchStrategy` インターフェースの実装クラスに経路探索の具体的な処理を委譲することで、既存コードに手を加えずにアルゴリズムを差し替えられるようにしています。この「既存コードに手を加えずにアルゴリズムを差し替え可能にする」ことは、「**Template Method パターン**」でも解決することができます。

例えば、本記事のシナリオを Template Method パターンで実装するとしたら以下のようになります。

> 1. `RouteNavigator` を抽象クラスにし、直線距離の計算から `Route` の生成までの処理の流れ自体を `final` メソッドとして固定する。
> 2. そのうえで、`final` メソッドの中から呼び出す「探索方針ごとに異なる部分（距離の倍率・想定平均速度・道路種別など）」は抽象メソッドとして宣言する。
> 3. 先程の抽象メソッドの具体的な処理は、サブクラスにそれぞれ実装させる。

ただし、両者は「何を差し替えの単位とするか」という点が異なります。<br>
Template Method パターンは、スーパークラスの `final` メソッドによりアルゴリズムの骨組みを固定し、サブクラスが各ステップに対応する抽象メソッドをオーバーライドする構成です。差し替えの単位は「サブクラス」のため、どのサブクラスを使うかはコンパイル時点で固定されます。<br>
一方、Strategy パターンは、本記事の `RouteNavigator` クラスのように、`find` メソッドの実装全体を `RouteSearchStrategy` インターフェースの実装クラスに委譲する構成です。差し替えの単位は「インスタンス」のため、コンストラクタに渡すインスタンスを変えるだけで実行時に自由に選択・変更できます。

つまり、「既存コードに手を加えずにアルゴリズムを差し替え可能にする」という目的は同じですが、Template Method パターンは継承によってサブクラスをコンパイル時に固定するのに対し、Strategy パターンは委譲によってインスタンスを実行時に選択する、という点が異なります。

<a id="深堀り3"></a>

## 【深堀り③】OCP（オープン・クローズドの原則）

正しい実装をもとに新しい探索方針を追加してみましょう。<br>
例えば「車体の都合上、道幅の広い経路を使いたい」という要望が新たに来たとします。

まず、インターフェース `RouteSearchStrategy` を実装した新しいクラスを追加します。

**`WideRoadStrategy.java`**

```java
package example;

public class WideRoadStrategy implements RouteSearchStrategy {
    @Override
    public Route find(Location origin, Location destination) {
        // 探索ロジックの中身はOCPの解説の本筋ではないため省略
    }
}
```

次に、`Main` クラス側では次のコードを追加します。

```java
RouteNavigator wideRoadNavigator = new RouteNavigator(origin, destination, new WideRoadStrategy());
System.out.println("[道幅優先] " + wideRoadNavigator.findRoute());
```

上記の修正のみで、新しい探索方針を利用できます。

ここで、`Main` のような呼び出し側のコードを除き、既存のクラスには一切手を加えていません。新しい探索方針を追加する際に必要だったのは、新しい `RouteSearchStrategy` の実装クラス（`WideRoadStrategy`）を追加し、`Main` クラスから利用するコードを書き足すことだけで済みます。これは、`RouteNavigator` クラスが依存しているのはインターフェース `RouteSearchStrategy` だけであるため、具体的な探索ロジックが何であっても対応できるからです。

この「既存コードを変えずに、新しいクラスを追加するだけで機能を拡張できる」という設計は、「**OCP（Open/Closed Principle：オープン・クローズドの原則）**」と呼ばれる設計原則の実践です。Strategy パターンは OCP を実現するための設計手段の一つと言えます。

詳しくは「OCP」や「オープン・クローズドの原則」で検索してみてください。

<a id="深堀り4"></a>

## 【深堀り④】GoF デザインパターンとの位置づけ

今回使った Strategy パターンは、GoF（Gang of Four）の 23 のデザインパターンのうち「振る舞いパターン」に分類されます。<br>
詳しくは「GoF」で検索してみてください。
