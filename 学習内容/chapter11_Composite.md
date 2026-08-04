# Composite（コンポジット）パターン ― 木構造の個別要素と複合要素を同一視する

次のような経験をしたことはありませんか？

> 個別の要素と、それらをまとめたグループを同じように扱いたいのに、グループの中にグループが入れ子になるたびに、あちこちに型ごとの分岐処理や重複したループを書く羽目になった。

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
> 現在、`TaskManager` はタスクをフラットな一覧として管理しており、各タスクの見積工数の合計と、タスク一覧の表示ができる状態です。<br>
> ある日 PM から、「関連するタスクをグループとしてまとめて管理したい。グループの中にさらに小さなグループを作れるようにもしてほしい。ただし、グループ全体の見積工数の合計や一覧表示は、これまでと同じ感覚で扱えるようにしてほしい」という要望が来ました。

### 既存コードの仕様

※実務では、次の `Task` のようなエンティティクラスは `entity` パッケージなど専用のディレクトリに切り出すのが一般的です。しかし、本記事ではパッケージ構成を主題としないため `example` パッケージ直下にまとめています。

- `Task`（既存クラス）

タスク 1 件の情報を保持するクラスです。

| フィールド        | 型       | 説明                 |
| ----------------- | -------- | -------------------- |
| `name`            | `String` | タスク名             |
| `estimatedHours`  | `int`    | 見積工数（時間）     |

| メソッド             | 戻り値の型 | 説明                     |
| -------------------- | ---------- | ------------------------ |
| `getName`            | `String`   | タスク名を取得する       |
| `getEstimatedHours`  | `int`      | 見積工数を取得する       |

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

| フィールド | 型          | 説明                     |
| ---------- | ----------- | ------------------------ |
| `tasks`    | `List<Task>` | 管理対象のタスク一覧    |

| メソッド                  | 戻り値の型 | 説明                                     |
| ------------------------- | ---------- | ---------------------------------------- |
| `addTask`                 | `void`     | タスクを追加する                         |
| `getTotalEstimatedHours`  | `int`      | 登録されているタスクの見積工数の合計を取得する |
| `printTaskList`           | `void`     | タスク一覧をコンソールに出力する         |

**`TaskManager.java`**

```java
package example;

import java.util.ArrayList;
import java.util.List;

public class TaskManager {
    private List<Task> tasks = new ArrayList<>();

    public void addTask(Task task) {
        tasks.add(task);
    }

    public int getTotalEstimatedHours() {
        int total = 0;
        for (Task task : tasks) {
            total += task.getEstimatedHours();
        }
        return total;
    }

    public void printTaskList() {
        for (Task task : tasks) {
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
        taskManager.addTask(new Task("要件ヒアリング", 8));
        taskManager.addTask(new Task("画面設計", 16));
        taskManager.addTask(new Task("API実装", 24));

        taskManager.printTaskList();
        System.out.println("合計見積工数：" + taskManager.getTotalEstimatedHours() + "時間");
    }
}
```

**実行結果**

```
要件ヒアリング（8時間）
画面設計（16時間）
API実装（24時間）
合計見積工数：48時間
```

※ここで一旦読むのを止めて、ご自身でコーディングを行なってみてください。その後で、続きを読んでください。

## 好ましくない実装

では、シナリオに従い追加実装をしていきましょう。

真っ先に思いつくのは、タスクのグループを表す新しいクラスを追加し、その中にタスクとサブグループの両方を保持させる、という実装ではないでしょうか？

**`TaskGroup.java`**

```java
package example;

import java.util.ArrayList;
import java.util.List;

public class TaskGroup {
    private String name;
    private List<Task> tasks = new ArrayList<>();
    private List<TaskGroup> subGroups = new ArrayList<>();

    public TaskGroup(String name) {
        this.name = name;
    }

    public void addTask(Task task) {
        tasks.add(task);
    }

    public void addSubGroup(TaskGroup subGroup) {
        subGroups.add(subGroup);
    }

    public int getTotalEstimatedHours() {
        int total = 0;
        for (Task task : tasks) {
            total += task.getEstimatedHours();
        }
        for (TaskGroup subGroup : subGroups) {
            total += subGroup.getTotalEstimatedHours();
        }
        return total;
    }

    public void printTaskList() {
        printTaskList("");
    }

    private void printTaskList(String prefix) {
        for (Task task : tasks) {
            System.out.println(prefix + name + " / " + task.getName() + "（" + task.getEstimatedHours() + "時間）");
        }
        for (TaskGroup subGroup : subGroups) {
            subGroup.printTaskList(prefix + name + " / ");
        }
    }
}
```

`TaskManager` クラスも、タスク単体だけでなくタスクグループも受け取れるように変更する必要があります。

**`TaskManager.java`**

```java
package example;

import java.util.ArrayList;
import java.util.List;

public class TaskManager {
    private List<Task> tasks = new ArrayList<>();
    /* ここを追加（ここから） */
    private List<TaskGroup> taskGroups = new ArrayList<>();
    /* ここを追加（ここまで） */

    public void addTask(Task task) {
        tasks.add(task);
    }

    /* ここを追加（ここから） */
    public void addTaskGroup(TaskGroup taskGroup) {
        taskGroups.add(taskGroup);
    }
    /* ここを追加（ここまで） */

    public int getTotalEstimatedHours() {
        int total = 0;
        for (Task task : tasks) {
            total += task.getEstimatedHours();
        }
        /* ここを追加（ここから） */
        for (TaskGroup taskGroup : taskGroups) {
            total += taskGroup.getTotalEstimatedHours();
        }
        /* ここを追加（ここまで） */
        return total;
    }

    public void printTaskList() {
        for (Task task : tasks) {
            System.out.println(task.getName() + "（" + task.getEstimatedHours() + "時間）");
        }
        /* ここを追加（ここから） */
        for (TaskGroup taskGroup : taskGroups) {
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
        taskManager.addTask(new Task("要件ヒアリング", 8));

        /* ここを追加（ここから） */
        TaskGroup designGroup = new TaskGroup("設計");
        designGroup.addTask(new Task("画面設計", 16));
        designGroup.addTask(new Task("DB設計", 12));

        TaskGroup implGroup = new TaskGroup("実装");
        implGroup.addTask(new Task("API実装", 24));

        TaskGroup testGroup = new TaskGroup("テスト");
        testGroup.addTask(new Task("単体テスト", 10));
        testGroup.addTask(new Task("結合テスト", 14));

        implGroup.addSubGroup(testGroup);

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
要件ヒアリング（8時間）
設計 / 画面設計（16時間）
設計 / DB設計（12時間）
実装 / API実装（24時間）
実装 / テスト / 単体テスト（10時間）
実装 / テスト / 結合テスト（14時間）
合計見積工数：84時間
```

コンパイルエラーがなく結果が出力されていることから、一見すると実装・動作確認ともに問題ないように見えます。

しかし、この実装には以下の問題点があります。

- `Task` と `TaskGroup` が別の型であるため、`TaskManager` クラスは `tasks`・`taskGroups` という 2 つのリストをそれぞれ持ち、`getTotalEstimatedHours`・`printTaskList` の両メソッドで、タスク用・タスクグループ用のループを重複して書く必要がある。
    - 新しい階層の種類（例えば「マイルストーン」のような別種の要素）を追加したくなった場合、`TaskManager` だけでなく `TaskGroup` 側にも、同様のリストとループを追加しなければならない。
- `TaskGroup` の中にさらに `TaskGroup` を追加できるようにした（`testGroup` を `implGroup` のサブグループとして追加した）ことで、`TaskGroup` クラス自身の `getTotalEstimatedHours`・`printTaskList` メソッドの中にも、タスク用のループとサブグループ用のループを重複して用意し、サブグループ側だけを再帰的に呼び出す、という実装になっている。
- タスク（`Task`）とタスクグループ（`TaskGroup`）を、呼び出し側（`TaskManager`）が常に型で区別しなければならず、「集計・表示できる対象」として同じように扱うことができていない。

## 正しい実装

では、好ましくない実装で挙げた問題点を解決するにはどうすればよいのでしょうか？

これらの問題を解決するのが **Composite パターン**です。<br>
個別の要素（葉）と、複数の要素をまとめた複合オブジェクト（枝）を共通のインターフェースで扱えるようにすることで、呼び出し側は両者を区別せずに再帰的に処理できるようになります。

まず、タスクとタスクグループの共通の抽象クラスから見ていきましょう。

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
また、一覧表示の入り口となる `printTaskList` は具体メソッドとして固定し、実際の出力内容は `prefix` 付きの `printTaskList` に委ねています。なお、`add` のようなタスクグループ専用の操作はここには定義していません（→ [【深堀り①】透過性と安全性のトレードオフ](#深堀り1)）。

次に、葉として振る舞う `Task` クラスを見ていきましょう。

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

`Task` クラスを振り返ると、`TaskComponent` クラスを継承した点が既存の仕様からの変更点です。<br>
しかし、フィールドや `getName`・`getEstimatedHours` メソッドの処理内容自体は、既存の仕様から変更されていません。

次に、複合オブジェクトとして振る舞う `TaskGroup` クラスを見ていきましょう。

**`TaskGroup.java`**

```java
package example;

import java.util.ArrayList;
import java.util.List;

public class TaskGroup extends TaskComponent {
    private String name;
    private List<TaskComponent> children = new ArrayList<>();

    public TaskGroup(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getEstimatedHours() {
        int total = 0;
        for (TaskComponent child : children) {
            total += child.getEstimatedHours();
        }
        return total;
    }

    public void add(TaskComponent child) {
        children.add(child);
    }

    @Override
    protected void printTaskList(String prefix) {
        for (TaskComponent child : children) {
            child.printTaskList(prefix + name + " / ");
        }
    }
}
```

`TaskGroup` クラスを振り返ると、好ましくない実装のように `tasks`・`subGroups` という 2 つのリストを持つのではなく、`TaskComponent` 型の子要素をまとめて持つ `children` フィールド 1 つだけで、タスクとタスクグループの両方を保持できるようになっています。<br>
`getEstimatedHours`・`printTaskList` メソッドも、子要素の型がタスクなのかタスクグループなのかを意識せず、`child.getEstimatedHours()`・`child.printTaskList(...)` を呼び出しているだけです。子がタスクグループだった場合はこの呼び出しが再び `TaskGroup` クラスの同名メソッドに入るため、ネストがどれだけ深くなっても同じコードで対応できます（再帰呼び出しの詳しい流れは→ [【深堀り②】再帰処理の考え方](#深堀り2)）。

次に、`TaskManager` クラスの変更点を見ていきましょう。

**`TaskManager.java`**

```java
package example;

import java.util.ArrayList;
import java.util.List;

public class TaskManager {
    private List<TaskComponent> components = new ArrayList<>();

    public void addComponent(TaskComponent component) {
        components.add(component);
    }

    public int getTotalEstimatedHours() {
        int total = 0;
        for (TaskComponent component : components) {
            total += component.getEstimatedHours();
        }
        return total;
    }

    public void printTaskList() {
        for (TaskComponent component : components) {
            component.printTaskList();
        }
    }
}
```

`TaskManager` クラスを振り返ると、好ましくない実装で必要だった `tasks`・`taskGroups` という 2 つのリストが、`TaskComponent` 型の `components` フィールド 1 つに統合されています。<br>
`addComponent` メソッドにはタスク・タスクグループのどちらも渡せるため、`addTask`・`addTaskGroup` のようにメソッドを分ける必要もなくなりました。

最後に、実行クラスの実装を見ていきましょう。

**`Main.java`**

```java
package example;

public class Main {
    public static void main(String[] args) {
        TaskManager taskManager = new TaskManager();
        taskManager.addComponent(new Task("要件ヒアリング", 8));

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
要件ヒアリング（8時間）
設計 / 画面設計（16時間）
設計 / DB設計（12時間）
実装 / API実装（24時間）
実装 / テスト / 単体テスト（10時間）
実装 / テスト / 結合テスト（14時間）
合計見積工数：84時間
```

`Main` クラスを振り返ると、`taskManager.addComponent(...)` には `Task` のインスタンスも `TaskGroup` のインスタンスもそのまま渡せており、`TaskGroup` クラスの `add` メソッドにも同様に両方を渡せています。呼び出し側は `TaskComponent` 型として扱っているだけで、実行結果は好ましくない実装とまったく同じです。

以上のような実装を行うと、以下のメリットがあります。

- `Task`・`TaskGroup` がどちらも `TaskComponent` 型として扱えるようになったため、`TaskManager` クラスは `components` という 1 つのリストだけを持てばよくなり、好ましくない実装で必要だった「タスク用」「タスクグループ用」の 2 つのリストとループが不要になった。
- `TaskGroup` の中にさらに `TaskGroup` を追加してネストを深くする場合も、`TaskGroup` クラス自身の `getEstimatedHours`・`printTaskList` メソッドが子要素に対して再帰的に処理を委ねるだけで対応でき、`TaskManager` クラス側のコードを変更する必要がない。
- 新しい階層の種類を追加したくなった場合も、`TaskComponent` を継承したクラスを 1 つ追加するだけで済み、既存の `TaskManager`・`Task`・`TaskGroup` クラスに手を加える必要がない。

## まとめ

正しい実装を振り返ると、葉（`Task`）と複合オブジェクト（`TaskGroup`）が共通の抽象クラス `TaskComponent` を継承したことで、呼び出し側（`TaskManager`）は両者を区別せず同じインターフェースとして扱えるようになりました。<br>
このように、Composite パターンは、個別の要素と、それらを束ねた複合オブジェクトを同一視することで、木構造がどれだけ深くネストしても、呼び出し側のコードを変えずに再帰的に処理できるようにするパターンです。

本記事の内容はここまでとなります。

以降は「もう少し深く知りたい」という方向けの補足となります。今回学んだパターンに繋がる設計原則や、実務で役立つ背景知識について触れています。

---

<a id="深堀り1"></a>

## 【深堀り①】透過性と安全性のトレードオフ

本記事の `TaskComponent` クラスを振り返ると、`add` メソッドは `TaskGroup` クラスにのみ定義されており、抽象クラス `TaskComponent` には定義していません。そのため、`Main` クラスで木構造を組み立てる際は、対象が `TaskGroup` 型であることを知っていなければ `add` を呼び出せません。

これは、Composite パターンを設計する際によく議論される「**透過性（Transparency）**」と「**安全性（Safety）**」のトレードオフに関わる選択です。

- **透過性を重視する設計**：`add`・`remove` のような子要素を操作するメソッドを `TaskComponent` クラスにも定義する方法です。こうすると、呼び出し側は `Task` か `TaskGroup` かをまったく意識せずに同じメソッドを呼び出せますが、`Task` クラス側で `add` を呼び出すと、実行時にエラー（例外）を起こすような実装にせざるを得ません。
- **安全性を重視する設計**：本記事のように、`add` を `TaskGroup` クラスにのみ定義する方法です。こうすると、`Task` クラスに対して `add` を呼び出すコード自体がコンパイルエラーになるため、誤った呼び出しを実行前に防げますが、木構造を組み立てる呼び出し側は、対象がタスクグループであることを知っている必要があります。

本記事では、木構造を組み立てる場面（`Main` クラス）と、組み立てた木構造を集計・表示する場面（`TaskManager` クラス）を分けて考え、後者では `Task`・`TaskGroup` の区別を完全になくす一方、前者では安全性を優先する設計を選んでいます。どちらを重視するかは、木構造をどのように使うシステムかによって判断が分かれる設計判断です。

<a id="深堀り2"></a>

## 【深堀り②】再帰処理の考え方

本記事の `TaskGroup` クラスの `getEstimatedHours` メソッドは、自分自身の子要素に対して同じ `getEstimatedHours` メソッドを呼び出しています。子要素が `TaskGroup` だった場合、その呼び出しの中でさらに `getEstimatedHours` メソッドが呼ばれる、という「**再帰呼び出し**」の構造になっています。

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
このように、再帰呼び出しは「一番深い子要素まで潜っていき、そこから 1 段ずつ戻りながら計算を積み上げていく」という流れで進みます。ネストがどれだけ深くなっても、`TaskGroup` クラス自身が「自分の子要素の合計を求める」という同じ処理を繰り返すだけでよいため、ネストの深さに応じたコードを追加で書く必要がありません。

<a id="深堀り3"></a>

## 【深堀り③】Iterator パターンとの関係

本記事の `printTaskList` メソッドは、`TaskGroup` クラス自身が子要素を再帰的にたどることで、木構造全体の一覧を出力しています。この「たどり方」は `TaskGroup` クラスの内部に隠れているため、呼び出し側（`TaskManager` クラス）は木構造をどうたどっているかをまったく意識せずに済んでいます。

一方、「木構造の要素を 1 つずつ順番に取り出して、その都度何か処理をしたい」といった要件が出てきた場合、この再帰的なたどり方をメソッドの中に固定してしまうと、処理内容を変えるたびに `TaskGroup` クラス自身を修正することになってしまいます。そこで、木構造をたどるロジックを専用の Iterator パターンのクラスとして切り出し、呼び出し側が `for` 文で 1 要素ずつ取り出せるようにする設計もよく使われます。

Iterator パターンについては chapter01 で扱っているので、興味があれば読み返してみてください。集約オブジェクトの内部構造を意識せずに走査できるようにするという点で、今回の Composite パターンとも相性の良い組み合わせです。

<a id="深堀り4"></a>

## 【深堀り④】GoF デザインパターンとの位置づけ

今回使った Composite パターンは、GoF（Gang of Four）の 23 のデザインパターンのうち「構造パターン」に分類されます。<br>
詳しくは「GoF」で検索してみてください。
