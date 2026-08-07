# Composite（コンポジット）パターン ― 木構造の個別要素と複合要素を同一視する

次のような経験をしたことはありませんか？

> 要素の一覧に対して処理を行うコードを書いていたら、単体の要素と要素をまとめたグループが混在していることに気がついた。そのため、グループの中にグループが入れ子になっている部分が登場するたびに、型ごとに分岐する処理や同じような繰り返し処理をあちこちに書く羽目になった。

この記事では、社内プロジェクト管理システムのタスクをグループ化するシナリオを通して、Composite パターンがこの問題をどのように解決するかを紹介します。

## 目次

- [【具体例】](#具体例)
    - [シナリオ](#シナリオ)
    - [既存コードの仕様](#既存コードの仕様)
- [好ましくない実装](#好ましくない実装)
- [正しい実装](#正しい実装)
- [まとめ](#まとめ)
- [【深堀り①】透過性と安全性のトレードオフ](#深堀り1)
- [【深堀り②】再帰処理の考え方](#深堀り2)
- [【深堀り③】Iterator パターンとの関係](#深堀り3)
- [【深堀り④】GoF デザインパターンとの位置づけ](#深堀り4)

---

## 【具体例】

### シナリオ

> あなたは社内のプロジェクト管理システムの開発チームに所属しています。<br>
> 現在、タスクは一覧で管理され、見積工数の合計計算と、タスクを羅列した一覧表示が行える状態です。<br>
> ある日 PM から「タスクをグループ化して管理したい」という要望が来ました。あなたは以下を担当します。
>
> - グループとしてタスクをまとめ、グループの中にさらに小さなグループを入れられるようにする
> - グループ全体の見積工数の合計や一覧表示を、個別のタスクと同じ感覚で扱えるようにする

### 既存コードの仕様

※実務では、次の `Task` のようなエンティティクラスは `entity` パッケージなど専用のディレクトリに切り出すのが一般的です。しかし、本記事ではパッケージ構成を主題としないため `example` パッケージ直下にまとめています。

- `Task`（既存クラス）

タスク 1 件の情報を保持するクラスです。

| フィールド       | 型       | 説明             |
| ---------------- | -------- | ---------------- |
| `name`           | `String` | タスク名         |
| `estimatedHours` | `int`    | 見積工数（時間） |

| メソッド            | 戻り値の型 | 説明               |
| ------------------- | ---------- | ------------------ |
| `getName`           | `String`   | タスク名を取得する |
| `getEstimatedHours` | `int`      | 見積工数を取得する |

**`Task.java`**

```java
package example;

public class Task {
    private String name;
    private int estimatedHours;

    public Task(String name, int estimatedHours) {
        this.name = name;
        this.estimatedHours = estimatedHours;
    }

    public String getName() {
        return name;
    }

    public int getEstimatedHours() {
        return estimatedHours;
    }
}
```

<br>

- `TaskManager`（既存クラス）

登録されたタスクの一覧を管理し、見積工数の合計計算と一覧表示を行うクラスです。

| フィールド | 型           | 説明                 |
| ---------- | ------------ | -------------------- |
| `taskList` | `List<Task>` | 管理対象のタスク一覧 |

| メソッド                 | 戻り値の型 | 説明                                           |
| ------------------------ | ---------- | ---------------------------------------------- |
| `addTask`                | `void`     | タスクを追加する                               |
| `getTotalEstimatedHours` | `int`      | 登録されているタスクの見積工数の合計を取得する |
| `printTaskList`          | `void`     | タスク一覧をコンソールに出力する               |

**`TaskManager.java`**

```java
package example;

public class TaskManager {
    private List<Task> taskList = new ArrayList<>();

    public void addTask(Task task) {
        taskList.add(task);
    }

    public int getTotalEstimatedHours() {
        int total = 0;
        for (Task task: taskList) {
            total += task.getEstimatedHours();
        }
        return total;
    }

    public void printTaskList() {
        for (Task task: taskList) {
            System.out.println(task.getName() + "（" + task.getEstimatedHours() + "時間）");
        }
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
        TaskManager taskManager = new TaskManager();
        taskManager.addTask(new Task("要件定義", 8));
        taskManager.addTask(new Task("画面設計", 16));
        taskManager.addTask(new Task("API実装", 24));

        taskManager.printTaskList();
        System.out.println("合計見積工数：" + taskManager.getTotalEstimatedHours() + "時間");
    }
}
```

**実行結果**

```
要件定義（8時間）
画面設計（16時間）
API実装（24時間）
合計見積工数：48時間
```

※ここで一旦読むのを止めて、ご自身でコーディングを行なってみてください。その後で、続きを読んでください。

## 好ましくない実装

では、シナリオに従い追加実装をしていきましょう。

真っ先に思いつくのは、`Task` クラスを参考に、タスクのグループを表す新しいクラスを追加し、その中にタスクとサブグループの両方を保持させる、という実装ではないでしょうか？

新しいクラスは次のような実装になると思います。

**`TaskGroup.java`**

```java
package example;

public class TaskGroup {
    private String name;
    private List<Task> taskList = new ArrayList<>();
    private List<TaskGroup> taskGroupList = new ArrayList<>();

    public TaskGroup(String name) {
        this.name = name;
    }

    public void addTask(Task task) {
        taskList.add(task);
    }

    public void addTaskGroup(TaskGroup taskGroup) {
        taskGroupList.add(taskGroup);
    }

    public int getTotalEstimatedHours() {
        int total = 0;
        for (Task task: taskList) {
            total += task.getEstimatedHours();
        }
        for (TaskGroup taskGroup: taskGroupList) {
            total += taskGroup.getTotalEstimatedHours();
        }
        return total;
    }

    public void printTaskList() {
        printTaskList("");
    }

    private void printTaskList(String prefix) {
        for (Task task: taskList) {
            System.out.println(prefix + name + " / " + task.getName() + "（" + task.getEstimatedHours() + "時間）");
        }
        for (TaskGroup taskGroup: taskGroupList) {
            taskGroup.printTaskList(prefix + name + " / ");
        }
    }
}
```

`TaskManager` クラスは、タスク単体だけでなくタスクグループも受け取れるように変更する必要があるので次のような実装になると思います。

**`TaskManager.java`**

```java
package example;

public class TaskManager {
    private List<Task> taskList = new ArrayList<>();
    /* ここを追加（ここから） */
    private List<TaskGroup> taskGroupList = new ArrayList<>();
    /* ここを追加（ここまで） */

    public void addTask(Task task) {
        taskList.add(task);
    }

    /* ここを追加（ここから） */
    public void addTaskGroup(TaskGroup taskGroup) {
        taskGroupList.add(taskGroup);
    }
    /* ここを追加（ここまで） */

    public int getTotalEstimatedHours() {
        int total = 0;
        for (Task task: taskList) {
            total += task.getEstimatedHours();
        }
        /* ここを追加（ここから） */
        for (TaskGroup taskGroup: taskGroupList) {
            total += taskGroup.getTotalEstimatedHours();
        }
        /* ここを追加（ここまで） */
        return total;
    }

    public void printTaskList() {
        for (Task task: taskList) {
            System.out.println(task.getName() + "（" + task.getEstimatedHours() + "時間）");
        }
        /* ここを追加（ここから） */
        for (TaskGroup taskGroup: taskGroupList) {
            taskGroup.printTaskList();
        }
        /* ここを追加（ここまで） */
    }
}
```

**`Main.java`**

```java
package example;

public class Main {
    public static void main(String[] args) {
        TaskManager taskManager = new TaskManager();
        taskManager.addTask(new Task("要件定義", 8));

        /* ここを追加（ここから） */
        TaskGroup designGroup = new TaskGroup("設計");
        designGroup.addTask(new Task("画面設計", 16));
        designGroup.addTask(new Task("DB設計", 12));

        TaskGroup implGroup = new TaskGroup("実装");
        implGroup.addTask(new Task("API実装", 24));

        TaskGroup testGroup = new TaskGroup("テスト");
        testGroup.addTask(new Task("単体テスト", 10));
        testGroup.addTask(new Task("結合テスト", 14));

        implGroup.addTaskGroup(testGroup);

        taskManager.addTaskGroup(designGroup);
        taskManager.addTaskGroup(implGroup);
        /* ここを追加（ここまで） */

        taskManager.printTaskList();
        System.out.println("合計見積工数：" + taskManager.getTotalEstimatedHours() + "時間");
    }
}
```

**実行結果**

```
要件定義（8時間）
設計 / 画面設計（16時間）
設計 / DB設計（12時間）
実装 / API実装（24時間）
実装 / テスト / 単体テスト（10時間）
実装 / テスト / 結合テスト（14時間）
合計見積工数：84時間
```

コンパイルエラーがなく結果が出力されていることから、一見すると実装・動作確認ともに問題ないように見えます。

しかし、この実装には以下の問題点があります。

- 個別要素（`Task`）と複合要素（`TaskGroup`）が別の型であるため、両方を保持・処理する側は種類ごとに専用のリストとループを実装する必要がある状態となっている。
    - その結果、新しい階層の種類（例えば「マイルストーン」のような別種の要素）が増えるたび、`TaskManager` クラスだけでなく `TaskGroup` クラスにも同様のリストとループを追加しなければならなくなる。
- 仕様の追加により、複合要素（`TaskGroup`）の中にさらに複合要素（`TaskGroup`）を追加できるようになったため、1つ目の問題点と同じ重複構造（個別要素用のループと複合要素用のループを両方用意する構造）が、複合要素クラス自身の内部にも繰り返し現れる状態となってしまっている。
- 呼び出し側（`TaskManager`）は、個別要素（`Task`）と複合要素（`TaskGroup`）を同一の型として扱えないため、常にどちらの型を扱っているかを意識する必要があり、「集計・表示できる対象」として同じように扱うことができない。

## 正しい実装

では、好ましくない実装で挙げた問題点を解決するにはどうすればよいのでしょうか？

これらの問題を解決するのが **Composite パターン**です。<br>
個別の要素（葉）と、複数の要素をまとめた複合オブジェクト（枝）を共通のインターフェースで扱えるようにすることで、呼び出し側は両者を区別せずに再帰的に処理できるようになります。

まず、タスク（個別要素）とタスクグループ（複合要素）の共通の抽象クラスから見ていきましょう。

**`TaskComponent.java`**

```java
package example;

public abstract class TaskComponent {
    public abstract String getName();

    public abstract int getEstimatedHours();

    public void printTaskList() {
        printTaskList("");
    }

    protected abstract void printTaskList(String prefix);
}
```

`TaskComponent` は新たに追加した抽象クラスで、タスク・タスクグループ双方が実装すべき `getName`・`getEstimatedHours` を抽象メソッドとして定義しています。<br>
また、一覧表示の入り口となる `printTaskList` は具体メソッドとして固定し、実際の出力内容は `prefix` 付きの `printTaskList` メソッドに委ねています。<br>
なお、`add` メソッドのようなタスクグループ専用の操作はここには定義していません（→ [【深堀り①】透過性と安全性のトレードオフ](#深堀り1)）。

次に、抽象クラス `TaskComponent` の実装クラスを見ていきましょう。

**`Task.java`**

```java
package example;

public class Task extends TaskComponent {
    private String name;
    private int estimatedHours;

    public Task(String name, int estimatedHours) {
        this.name = name;
        this.estimatedHours = estimatedHours;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getEstimatedHours() {
        return estimatedHours;
    }

    @Override
    protected void printTaskList(String prefix) {
        System.out.println(prefix + name + "（" + estimatedHours + "時間）");
    }
}
```

**`TaskGroup.java`**

```java
package example;

public class TaskGroup extends TaskComponent {
    private String name;
    private List<TaskComponent> children = new ArrayList<>();

    public TaskGroup(String name) {
        this.name = name;
    }

    public void add(TaskComponent child) {
        children.add(child);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getEstimatedHours() {
        int total = 0;
        for (TaskComponent child: children) {
            total += child.getEstimatedHours();
        }
        return total;
    }

    @Override
    protected void printTaskList(String prefix) {
        for (TaskComponent child: children) {
            child.printTaskList(prefix + name + " / ");
        }
    }
}
```

`Task` クラスを振り返ると、`TaskComponent` クラスを継承しています。<br>
また、`TaskComponent` クラスの抽象メソッドの具体的な実装を行っています。<br>
一方、フィールドや `getName`・`getEstimatedHours` メソッドの処理内容自体は、既存の仕様から変更されていません。

`TaskGroup` クラスを振り返ると、個別要素（`Task`）と複合要素（`TaskGroup`）の型が `TaskComponent` 型に統合されたことにより、好ましくない実装で必要だった `taskList`・`taskGroupList` という 2 つのリストが `children` フィールド 1 つにまとまり、タスク・タスクグループの追加も `add` メソッド 1 つで完結するようになりました。<br>
また、`getEstimatedHours`・`printTaskList` メソッドでは、`children` の各要素に対して同じメソッドを呼び出しているだけで、実際に呼ばれる処理は要素の型（`Task` か `TaskGroup` か）によって決まるため、ネストがどれだけ深くなっても同じコードで対応できます（再帰呼び出しの詳しい流れは→ [【深堀り②】再帰処理の考え方](#深堀り2)）。

次に、呼び出し側を見ていきましょう。

**`TaskManager.java`**

```java
package example;

public class TaskManager {
    private List<TaskComponent> taskComponentList = new ArrayList<>();

    public void addComponent(TaskComponent taskComponent) {
        taskComponentList.add(taskComponent);
    }

    public int getTotalEstimatedHours() {
        int total = 0;
        for (TaskComponent taskComponent: taskComponentList) {
            total += taskComponent.getEstimatedHours();
        }
        return total;
    }

    public void printTaskList() {
        for (TaskComponent taskComponent: taskComponentList) {
            taskComponent.printTaskList();
        }
    }
}
```

`TaskManager` クラスを振り返ると、好ましくない実装で必要だった `taskList`・`taskGroupList` という 2 つのリストが、`TaskComponent` 型の `taskComponentList` フィールド 1 つに統合されています。<br>
`addComponent` メソッドにはタスク・タスクグループのどちらも渡せるため、`addTask`・`addTaskGroup` のようにメソッドを分ける必要もなくなっています。

最後に、実行クラスの実装を見ていきましょう。

**`Main.java`**

```java
package example;

public class Main {
    public static void main(String[] args) {
        TaskManager taskManager = new TaskManager();
        taskManager.addComponent(new Task("要件定義", 8));

        TaskGroup designGroup = new TaskGroup("設計");
        designGroup.add(new Task("画面設計", 16));
        designGroup.add(new Task("DB設計", 12));

        TaskGroup implGroup = new TaskGroup("実装");
        implGroup.add(new Task("API実装", 24));

        TaskGroup testGroup = new TaskGroup("テスト");
        testGroup.add(new Task("単体テスト", 10));
        testGroup.add(new Task("結合テスト", 14));

        implGroup.add(testGroup);

        taskManager.addComponent(designGroup);
        taskManager.addComponent(implGroup);

        taskManager.printTaskList();
        System.out.println("合計見積工数：" + taskManager.getTotalEstimatedHours() + "時間");
    }
}
```

**実行結果**

```
要件定義（8時間）
設計 / 画面設計（16時間）
設計 / DB設計（12時間）
実装 / API実装（24時間）
実装 / テスト / 単体テスト（10時間）
実装 / テスト / 結合テスト（14時間）
合計見積工数：84時間
```

`Main` クラスを振り返ると、型統合の効果はここにも表れており、`taskManager.addComponent(...)` には `Task` のインスタンスも `TaskGroup` のインスタンスもそのまま渡せています。また、`TaskGroup` クラスの `add` メソッドにも同様に両方を渡せています。<br>
正しい実装でも好ましくない実装とまったく同じ実行結果となっています。

以上のような実装を行うと、以下のメリットがあります。

- 個別要素（`Task`）と複合要素（`TaskGroup`）の型が `TaskComponent` 型に統合されたことにより、複合要素側は種類ごとに専用のリストとループを実装する必要がなくなる。
    - その結果、新しい階層の種類（例えば「マイルストーン」のような別種の要素）が増えても、抽象クラス `TaskComponent` を継承したクラスを作成するだけで済み、既存のクラス（`TaskManager`・`Task`・`TaskGroup`）に手を加える必要がない。
- 複合要素（`TaskGroup`）の `getEstimatedHours`・`printTaskList` メソッドは、`children` の各要素を同じ `TaskComponent` 型として扱うため、ネストがどれだけ深くなっても、好ましくない実装のように各階層で個別要素用・複合要素用の 2 つのループを繰り返し用意する必要がない。
- 個別要素（`Task`）と複合要素（`TaskGroup`）の型が `TaskComponent` 型に統合されたことにより、呼び出し側（`TaskManager`）は、どちらの型を扱っているかを意識する必要がなくなり「集計・表示できる対象」を同じように扱える。

## まとめ

正しい実装を振り返ると、葉（`Task`）と複合オブジェクト（`TaskGroup`）が共通の抽象クラス `TaskComponent` を継承したことで、呼び出し側（`TaskManager`）は両者を区別せず同じインターフェースとして扱えるようになりました。<br>
このように、Composite パターンは、個別の要素と、それらを束ねた複合オブジェクトを同一視することで、木構造がどれだけ深くネストしても、呼び出し側のコードを変えずに再帰的に処理できるようにするパターンです。

本記事の内容はここまでとなります。

以降は「もう少し深く知りたい」という方向けの補足となります。今回学んだパターンに繋がる設計原則や、実務で役立つ背景知識について触れています。

---

<a id="深堀り1"></a>

## 【深堀り①】透過性と安全性のトレードオフ

本記事の抽象クラス `TaskComponent` および継承したクラスを振り返ると、`add` メソッドは `TaskGroup` クラスにのみ定義されており、抽象クラスには定義されていません。そのため、`add` メソッドを使用したい場合は、対象が `TaskGroup` 型であることを知っていなければなりません。

これは、Composite パターンを設計する際によく議論される「**透過性（Transparency）**」と「**安全性（Safety）**」のトレードオフに関わる選択です。

- **透過性を重視する設計**：ある操作を抽象クラスにも定義する方法です。呼び出し側は葉か複合オブジェクトかを意識せずに同じメソッドを呼び出せます。ただし、その操作が葉にとって自然な意味を持たない場合は、葉側で呼び出すと実行時にエラー（例外）になるような実装にする必要があります。
- **安全性を重視する設計**：ある操作を複合オブジェクトのクラスにのみ定義する方法です。葉側でそのメソッドを呼び出すコード自体がコンパイルエラーになるため誤った呼び出しを実行前に防げますが、呼び出し側は対象が複合オブジェクトであることを知っている必要があります。

本記事ではこれらの設計を、操作ごとに使い分けています。<br>
複合オブジェクトにしか意味を持たない操作である `add` メソッドは、**安全性を重視する設計**を採用し、`TaskGroup` クラスにのみ定義しています。そのため、木構造を組み立てる `Main` クラスは、対象が `TaskGroup` 型であることを知った上で `add` メソッドを呼び出しています。<br>
一方、葉および複合オブジェクトどちらにとっても自然な意味を持つ操作である `getEstimatedHours`・`printTaskList` メソッドは、**透過性を重視する設計**を採用し、抽象クラス `TaskComponent` に定義しています。そのため、集計・表示を行う `TaskManager` クラスは、葉か複合オブジェクトかを意識せずに同じメソッドを呼び出せています。

このように、透過性と安全性のどちらを優先するかは、操作（メソッド）ごとに「葉に対してもその操作が意味を持つか」を基準に判断するとよいでしょう。葉にとって自然な意味を持つ操作は抽象クラスに定義して透過性を優先し、複合オブジェクトにしか意味を持たない操作は、その操作を持つクラスにのみ定義して安全性を優先する、という使い分けが実務でもよく採られます。

<a id="深堀り2"></a>

## 【深堀り②】再帰処理の考え方

本記事の `TaskGroup` クラスを振り返ると、2 つの再帰メソッド（`getEstimatedHours`・`printTaskList`）がありますが、`TaskGroup` クラス自身とその子とのデータのやり取りの方向がそれぞれ逆になっています。順番に見ていきましょう。

### 型①（戻り値を積み上げる再帰）

`getEstimatedHours` メソッドは、自分自身の子に対して同じ `getEstimatedHours` メソッドを呼び出しています。子が `TaskGroup` だった場合、その呼び出しの中でさらに `getEstimatedHours` メソッドが呼ばれる、という「**再帰呼び出し**」の構造になっています。

`Main` クラスの `implGroup.getEstimatedHours()` を例に、呼び出しの流れを追ってみましょう。

```
implGroup.getEstimatedHours()
├─ child = Task("API実装", 24)      → total += 24        （total = 24）
└─ child = testGroup（TaskGroup）   → total += testGroup.getEstimatedHours()
      testGroup.getEstimatedHours()
      ├─ child = Task("単体テスト", 10) → total += 10   （total = 10）
      └─ child = Task("結合テスト", 14) → total += 14   （total = 24）
      → return 24
   → total = 24 + 24 = 48
→ return 48
```

`testGroup.getEstimatedHours()` の呼び出しが終わって `24` という値が返ってくるまで、呼び出し元の `implGroup.getEstimatedHours()` の処理は一時停止した状態で待っています。値が返ってきた時点で、待っていた計算（`total += 24`）が再開され、最終的な合計値が確定します。<br>
このように、再帰呼び出しは「一番深い子まで潜っていき、そこから 1 段ずつ戻りながら計算を積み上げていく」という流れで進みます。ネストがどれだけ深くなっても、`TaskGroup` クラス自身が「自分の子の合計を求める」という同じ処理を繰り返すだけでよいため、ネストの深さに応じたコードを追加で書く必要がありません。

### 型②（引数で状態を渡す再帰）

`printTaskList` メソッドは、型①とは逆に、**親から子へ**データを渡しながら再帰しています。`prefix` 引数に「ここまでの経路」を積み増し、`child.printTaskList(prefix + name + " / ")` という形で子に渡していく構造です。

同じく `implGroup.printTaskList()` を例に、呼び出しの流れを追ってみましょう。

※型①と異なり `TaskGroup("実装")` のような生成時点も示している理由：`printTaskList` メソッドが `name` を `prefix` に連結して出力するため、名前の出どころを明示する必要があるから。

```
implGroup = TaskGroup("実装")
implGroup.printTaskList() → printTaskList("")
├─ child = Task("API実装", 24)    → child.printTaskList("実装 / ")（実体は Task の printTaskList）      → println("実装 / API実装（24時間）")
└─ child = testGroup（TaskGroup） → child.printTaskList("実装 / ")（実体は TaskGroup の printTaskList）
      testGroup = TaskGroup("テスト")
      testGroup.printTaskList("実装 / ")
      ├─ child = Task("単体テスト", 10) → child.printTaskList("実装 / テスト / ")（実体は Task の printTaskList） → println("実装 / テスト / 単体テスト（10時間）")
      └─ child = Task("結合テスト", 14) → child.printTaskList("実装 / テスト / ")（実体は Task の printTaskList） → println("実装 / テスト / 結合テスト（14時間）")
```

型①と異なり、`printTaskList` は戻り値を持たない `void` メソッドです。そのため、`println` が実行された時点でその呼び出しの役目は終わります。<br>
このように、再帰呼び出しは「どこまでの経路を子に渡すか」だけを組み立てて渡し、あとは子に任せきりにするという流れで進みます。

### まとめ

型①は子の戻り値を親が待って合算する「**子 → 親**」の再帰、型②は親が組み立てた状態を子に渡していく「**親 → 子**」の再帰です。<br>
データが流れる向きは逆ですが、どちらも「`TaskGroup` 自身が、自分の子に対して同じ処理を繰り返すだけでよい」という点は共通しており、これによってネストの深さに関わらず同じコードで対応できています。

<a id="深堀り3"></a>

## 【深堀り③】Iterator パターンとの関係

本記事の `printTaskList` メソッドは、`TaskGroup` クラス自身が子を再帰的にたどることで、木構造全体の一覧を出力しています。この「たどり方」は `TaskGroup` クラスの内部に隠れているため、呼び出し側（`TaskManager` クラス）は木構造をどうたどっているかをまったく意識せずに済んでいます。

一方、「木構造の要素を 1 つずつ順番に取り出して、その都度何か処理をしたい」といった要件が出てきた場合、この再帰的なたどり方をメソッドの中に固定してしまうと、処理内容を変えるたびに `TaskGroup` クラス自身を修正することになってしまいます。そこで、木構造をたどるロジックを専用の Iterator パターンのクラスとして切り出し、呼び出し側が `for` 文で 1 要素ずつ取り出せるようにする設計もよく使われます。

Iterator パターンについては chapter01 で扱っているので、興味があれば読み返してみてください。集約オブジェクトの内部構造を意識せずに走査できるようにするという点で、今回の Composite パターンとも相性の良い組み合わせです。

<a id="深堀り4"></a>

## 【深堀り④】GoF デザインパターンとの位置づけ

今回使った Composite パターンは、GoF（Gang of Four）の 23 のデザインパターンのうち「構造パターン」に分類されます。<br>
詳しくは「GoF」で検索してみてください。
