# Visitor（ビジター）パターン ― 処理をデータ構造の外側に追加する

次のような経験をしたことはありませんか？

> 複数の種類のオブジェクトが混在する構造に対して、新しい処理を追加するたびに、関係するクラスそれぞれに同じ処理を重複して実装する羽目になった。その結果、処理を 1 つ増やすだけなのに、直接は関係のないはずのクラスまで毎回修正することになり、変更のたびにクラス同士の結合が強まっていった。

この記事では、社内プロジェクト管理システムのタスク集計・検索機能を追加するシナリオを通して、Visitor パターンがこの問題をどのように解決するかを紹介します。

## 目次

- [【具体例】](#具体例)
    - [シナリオ](#シナリオ)
    - [既存コードの仕様](#既存コードの仕様)
- [好ましくない実装](#好ましくない実装)
- [正しい実装](#正しい実装)
- [まとめ](#まとめ)
- [【深堀り①】二重ディスパッチの仕組み](#深堀り1)
- [【深堀り②】なぜ TaskGroup は Iterable を実装するのか](#深堀り2)
- [【深堀り③】Composite パターンとの関係](#深堀り3)
- [【深堀り④】Java 標準ライブラリにおける Visitor パターンの例](#深堀り4)
- [【深堀り⑤】OCP（オープン・クローズドの原則）](#深堀り5)
- [【深堀り⑥】GoF デザインパターンとの位置づけ](#深堀り6)

---

## 【具体例】

### シナリオ

> あなたは社内のプロジェクト管理システムの開発チームに所属しています。<br>
> 現在、タスクはグループ化して管理でき、グループの中にさらに小さなグループを入れ子にした場合も含めて、見積工数の合計計算と一覧表示が行える状態です。各タスクには、名前・見積工数に加えて、進捗状況（未着手・進行中・完了）が記録されています。<br>
> ある日 PM から、進捗確認のために次の機能が欲しいという要望が来ました。あなたはこれらを担当することになりました。
>
> - 未着手のタスクだけを一覧で確認したい
> - 進捗状況を数値で把握したい（完了したタスクの見積工数を、全体の見積工数に対する割合で確認）
> - タスク名にキーワードを含むタスクを検索したい

### 既存コードの仕様

※実務では、次の `Task`・`TaskGroup` のようなエンティティクラスは `entity` パッケージなど専用のディレクトリに切り出すのが一般的です。しかし、本記事ではパッケージ構成を主題としないため `example` パッケージ直下にまとめています。

- `TaskComponent`（既存クラス）

タスク（個別要素）とタスクグループ（複合要素）に共通する振る舞いを定義する抽象クラスです。

| メソッド            | 引数            | 戻り値の型 | 説明                                             |
| ------------------- | --------------- | ---------- | ------------------------------------------------ |
| `getName`           | なし            | `String`   | 名前を取得する（抽象メソッド）                   |
| `getEstimatedHours` | なし            | `int`      | 見積工数を取得する（抽象メソッド）               |
| `printTaskList`     | なし            | `void`     | 自身を起点にタスク一覧を出力する（具象メソッド） |
| `printTaskList`     | `String prefix` | `void`     | 接頭辞付きでタスク一覧を出力する（抽象メソッド） |

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

<br>

- `Task`（既存クラス）

タスク 1 件の情報を保持するクラスです。

| フィールド       | 型       | 説明                             |
| ---------------- | -------- | -------------------------------- |
| `name`           | `String` | タスク名                         |
| `estimatedHours` | `int`    | 見積工数（時間）                 |
| `status`         | `String` | 進捗状況（未着手・進行中・完了） |

| メソッド            | 戻り値の型 | 説明               |
| ------------------- | ---------- | ------------------ |
| `getName`           | `String`   | タスク名を取得する |
| `getEstimatedHours` | `int`      | 見積工数を取得する |
| `getStatus`         | `String`   | 進捗状況を取得する |

**`Task.java`**

```java
package example;

public class Task extends TaskComponent {
    private String name;
    private int estimatedHours;
    private String status;

    public Task(String name, int estimatedHours, String status) {
        this.name = name;
        this.estimatedHours = estimatedHours;
        this.status = status;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getEstimatedHours() {
        return estimatedHours;
    }

    public String getStatus() {
        return status;
    }

    @Override
    protected void printTaskList(String prefix) {
        System.out.println(prefix + name + "（" + estimatedHours + "時間）");
    }
}
```

<br>

- `TaskGroup`（既存クラス）

複数のタスク・タスクグループをまとめて管理する複合要素です。

| フィールド | 型                    | 説明                               |
| ---------- | --------------------- | ---------------------------------- |
| `name`     | `String`              | グループ名                         |
| `children` | `List<TaskComponent>` | 管理対象の個別要素・複合要素の一覧 |

| メソッド            | 戻り値の型 | 説明                                     |
| ------------------- | ---------- | ---------------------------------------- |
| `add`               | `void`     | 個別要素・複合要素を追加する             |
| `getName`           | `String`   | グループ名を取得する                     |
| `getEstimatedHours` | `int`      | 配下の全タスクの見積工数の合計を取得する |

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

<br>

- `Main`（実行クラス）

**`Main.java`**

```java
package example;

public class Main {
    public static void main(String[] args) {
        TaskGroup designGroup = new TaskGroup("設計");
        designGroup.add(new Task("要件定義", 8, "完了"));
        designGroup.add(new Task("画面設計", 12, "進行中"));

        TaskGroup testGroup = new TaskGroup("テスト");
        testGroup.add(new Task("単体テスト", 10, "未着手"));
        testGroup.add(new Task("結合テスト", 6, "未着手"));

        TaskGroup implementationGroup = new TaskGroup("実装");
        implementationGroup.add(new Task("API実装", 20, "進行中"));
        implementationGroup.add(testGroup);

        TaskGroup rootGroup = new TaskGroup("プロジェクトA");
        rootGroup.add(designGroup);
        rootGroup.add(implementationGroup);

        rootGroup.printTaskList();
        System.out.println("合計見積工数：" + rootGroup.getEstimatedHours() + "時間");
    }
}
```

**実行結果**

```
プロジェクトA / 設計 / 要件定義（8時間）
プロジェクトA / 設計 / 画面設計（12時間）
プロジェクトA / 実装 / API実装（20時間）
プロジェクトA / 実装 / テスト / 単体テスト（10時間）
プロジェクトA / 実装 / テスト / 結合テスト（6時間）
合計見積工数：56時間
```

※ここで一旦読むのを止めて、ご自身でコーディングを行なってみてください。その後で、続きを読んでください。

## 好ましくない実装

では、シナリオに従い追加実装をしていきましょう。

真っ先に思いつくのは、`TaskComponent` クラスに新しい抽象メソッドを追加し、`Task`・`TaskGroup` クラス双方で具体的な処理を行う、という実装ではないでしょうか？

**`TaskComponent.java`**

```java
package example;

import java.util.List;

public abstract class TaskComponent {
    public abstract String getName();

    public abstract int getEstimatedHours();

    public void printTaskList() {
        printTaskList("");
    }

    protected abstract void printTaskList(String prefix);

    /* ここを追加（ここから） */
    public abstract void printIncompleteTaskList();

    public abstract int getCompletedEstimatedHours();

    public abstract List<Task> findTasksByKeyword(String keyword);
    /* ここを追加（ここまで） */
}
```

**`Task.java`**

```java
package example;

import java.util.ArrayList;
import java.util.List;

public class Task extends TaskComponent {
    private String name;
    private int estimatedHours;
    private String status;

    public Task(String name, int estimatedHours, String status) {
        this.name = name;
        this.estimatedHours = estimatedHours;
        this.status = status;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getEstimatedHours() {
        return estimatedHours;
    }

    public String getStatus() {
        return status;
    }

    @Override
    protected void printTaskList(String prefix) {
        System.out.println(prefix + name + "（" + estimatedHours + "時間）");
    }

    /* ここを追加（ここから） */
    @Override
    public void printIncompleteTaskList() {
        if ("未着手".equals(status)) {
            System.out.println(name + "（" + estimatedHours + "時間）");
        }
    }

    @Override
    public int getCompletedEstimatedHours() {
        return "完了".equals(status) ? estimatedHours : 0;
    }

    @Override
    public List<Task> findTasksByKeyword(String keyword) {
        List<Task> result = new ArrayList<>();
        if (name.contains(keyword)) {
            result.add(this);
        }
        return result;
    }
    /* ここを追加（ここまで） */
}
```

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

    /* ここを追加（ここから） */
    @Override
    public void printIncompleteTaskList() {
        for (TaskComponent child: children) {
            child.printIncompleteTaskList();
        }
    }

    @Override
    public int getCompletedEstimatedHours() {
        int total = 0;
        for (TaskComponent child: children) {
            total += child.getCompletedEstimatedHours();
        }
        return total;
    }

    @Override
    public List<Task> findTasksByKeyword(String keyword) {
        List<Task> result = new ArrayList<>();
        for (TaskComponent child: children) {
            result.addAll(child.findTasksByKeyword(keyword));
        }
        return result;
    }
    /* ここを追加（ここまで） */
}
```

実行クラスは次のようになると思います。

**`Main.java`**

```java
package example;

public class Main {
    public static void main(String[] args) {
        TaskGroup designGroup = new TaskGroup("設計");
        designGroup.add(new Task("要件定義", 8, "完了"));
        designGroup.add(new Task("画面設計", 12, "進行中"));

        TaskGroup testGroup = new TaskGroup("テスト");
        testGroup.add(new Task("単体テスト", 10, "未着手"));
        testGroup.add(new Task("結合テスト", 6, "未着手"));

        TaskGroup implementationGroup = new TaskGroup("実装");
        implementationGroup.add(new Task("API実装", 20, "進行中"));
        implementationGroup.add(testGroup);

        TaskGroup rootGroup = new TaskGroup("プロジェクトA");
        rootGroup.add(designGroup);
        rootGroup.add(implementationGroup);

        rootGroup.printTaskList();
        System.out.println("合計見積工数：" + rootGroup.getEstimatedHours() + "時間");

        /* ここを追加（ここから） */
        System.out.println();

        System.out.println("【未着手タスク一覧】");
        rootGroup.printIncompleteTaskList();

        int completedHours = rootGroup.getCompletedEstimatedHours();
        int totalHours = rootGroup.getEstimatedHours();
        System.out.println("進捗：" + completedHours + "時間 / " + totalHours + "時間（" + String.format("%.1f", completedHours * 100.0 / totalHours) + "%）");

        System.out.println("「テスト」を含むタスク：");
        for (Task task: rootGroup.findTasksByKeyword("テスト")) {
            System.out.println(task.getName());
        }
        /* ここを追加（ここまで） */
    }
}
```

**実行結果**

```
プロジェクトA / 設計 / 要件定義（8時間）
プロジェクトA / 設計 / 画面設計（12時間）
プロジェクトA / 実装 / API実装（20時間）
プロジェクトA / 実装 / テスト / 単体テスト（10時間）
プロジェクトA / 実装 / テスト / 結合テスト（6時間）
合計見積工数：56時間

【未着手タスク一覧】
単体テスト（10時間）
結合テスト（6時間）
進捗：8時間 / 56時間（14.3%）
「テスト」を含むタスク：
単体テスト
結合テスト
```

コンパイルエラーがなく結果が出力されていることから、一見すると実装・動作確認ともに問題ないように見えます。

しかし、この実装には以下の問題点があります。

- 新しい集計や検索機能などを 1 つ追加するたびに、`TaskComponent` クラスにおいて抽象メソッドを追加し、`Task`・`TaskGroup` クラス双方への実装が必要になる。
    - その結果、「未着手タスク一覧」「進捗集計」「キーワード検索」といった 1 つの機能のロジックが `Task`・`TaskGroup` クラス双方にまたがって書かれることになり、機能ごとの処理内容を横断的に把握するのが難しくなる。
- `Task`・`TaskGroup` クラスを同じ型として扱うための最小限の共通の振る舞い（名前や見積工数の取得、木構造をたどった一覧表示）を担う `TaskComponent` クラスに、集計・検索といった処理内容までもが混ざり込み、クラスの目的が曖昧になってしまっている。

## 正しい実装

では、好ましくない実装で挙げた問題点を解決するにはどうすればよいのでしょうか？

これらの問題を解決するのが **Visitor パターン**です。<br>
処理内容を `TaskComponent` クラスから切り離し、外部の専用クラスへ委ねることで、新しい処理を追加してもタスクの構造を表すクラス側には手を入れずに済むようになります。

まず、「Visitor を受け入れられる要素である」ことを示す共通の型から見ていきましょう。

**`Element.java`**

```java
package example;

public interface Element {
    void accept(Visitor visitor);
}
```

`Element` は新たに追加したインターフェースで、抽象メソッド `accept` を 1 つだけ持っています。`TaskComponent` クラスがこのインターフェースを実装することで、`Task`・`TaskGroup` クラスのどちらも「Visitor を受け入れられる要素である」という契約を型で表せるようになります。

次に、`TaskComponent` クラスを見ていきましょう。

**`TaskComponent.java`**

```java
package example;

public abstract class TaskComponent implements Element {
    public abstract String getName();

    public abstract int getEstimatedHours();

    public void printTaskList() {
        printTaskList("");
    }

    protected abstract void printTaskList(String prefix);
}
```

`TaskComponent` クラスを振り返ると、既存の仕様から `Element` インターフェースを実装する修正が加わっています。<br>
ここで、`accept` メソッドの実装が行われていませんが、こちらの実装は `Task`・`TaskGroup` クラスそれぞれに委ねます（理由は→ [【深堀り①】二重ディスパッチの仕組み](#深堀り1)）。

次に、処理内容を表す `Visitor` 側の抽象クラスを見ていきましょう。

**`Visitor.java`**

```java
package example;

public abstract class Visitor {
    public abstract void visit(Task task);

    public abstract void visit(TaskGroup taskGroup);
}
```

`Visitor` は新たに追加した抽象クラスで、`Task`・`TaskGroup` クラスの型をそれぞれ受け取れる抽象メソッド `visit` を持っています。

次に、`Task`・`TaskGroup` クラスを見ていきましょう。

**`Task.java`**

```java
package example;

public class Task extends TaskComponent {
    private String name;
    private int estimatedHours;
    private String status;

    public Task(String name, int estimatedHours, String status) {
        this.name = name;
        this.estimatedHours = estimatedHours;
        this.status = status;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getEstimatedHours() {
        return estimatedHours;
    }

    public String getStatus() {
        return status;
    }

    @Override
    protected void printTaskList(String prefix) {
        System.out.println(prefix + name + "（" + estimatedHours + "時間）");
    }

    /* ここを追加（ここから） */
    @Override
    public void accept(Visitor visitor) {
        visitor.visit(this);
    }
    /* ここを追加（ここまで） */
}
```

**`TaskGroup.java`**

```java
package example;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class TaskGroup extends TaskComponent implements Iterable<TaskComponent> {
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

    /* ここを追加（ここから） */
    @Override
    public void accept(Visitor visitor) {
        visitor.visit(this);
    }

    @Override
    public Iterator<TaskComponent> iterator() {
        return children.iterator();
    }
    /* ここを追加（ここまで） */
}
```

`Task`・`TaskGroup` クラスを振り返ると、どちらも `accept` メソッドの具体的な実装を行っています。その中身は抽象クラス `Visitor` の `visit` メソッドを呼んでいるのですが、引数に `this` を渡しているため、`Task` クラスの呼び出しでは `visit(Task)` メソッドが、`TaskGroup` クラスの呼び出しでは `visit(TaskGroup)` メソッドが、それぞれ正しく呼び分けられます（仕組みの詳細は→ [【深堀り①】二重ディスパッチの仕組み](#深堀り1)）。<br>
また、`TaskGroup` クラスではインターフェース `Iterable<TaskComponent>` を実装しています。これは、この後作成する抽象クラス `Visitor` を継承したクラスで、拡張 for 文を使って `children` の中身を辿れるようにするためです（理由の詳細は→ [【深堀り②】なぜ TaskGroup は Iterable を実装するのか](#深堀り2)）。

次に、`Visitor` のサブクラスを見ていきましょう。

**`IncompleteTaskListVisitor.java`**

```java
package example;

public class IncompleteTaskListVisitor extends Visitor {
    @Override
    public void visit(Task task) {
        if ("未着手".equals(task.getStatus())) {
            System.out.println(task.getName() + "（" + task.getEstimatedHours() + "時間）");
        }
    }

    @Override
    public void visit(TaskGroup taskGroup) {
        for (TaskComponent child: taskGroup) {
            child.accept(this);
        }
    }
}
```

**`CompletedHoursVisitor.java`**

```java
package example;

public class CompletedHoursVisitor extends Visitor {
    private int totalHours = 0;

    @Override
    public void visit(Task task) {
        if ("完了".equals(task.getStatus())) {
            totalHours += task.getEstimatedHours();
        }
    }

    @Override
    public void visit(TaskGroup taskGroup) {
        for (TaskComponent child: taskGroup) {
            child.accept(this);
        }
    }

    public int getTotalHours() {
        return totalHours;
    }
}
```

**`KeywordSearchVisitor.java`**

```java
package example;

import java.util.ArrayList;
import java.util.List;

public class KeywordSearchVisitor extends Visitor {
    private String keyword;
    private List<Task> foundTasks = new ArrayList<>();

    public KeywordSearchVisitor(String keyword) {
        this.keyword = keyword;
    }

    @Override
    public void visit(Task task) {
        if (task.getName().contains(keyword)) {
            foundTasks.add(task);
        }
    }

    @Override
    public void visit(TaskGroup taskGroup) {
        for (TaskComponent child: taskGroup) {
            child.accept(this);
        }
    }

    public List<Task> getFoundTasks() {
        return foundTasks;
    }
}
```

`IncompleteTaskListVisitor`・`CompletedHoursVisitor`・`KeywordSearchVisitor` は新たに追加したクラスで、抽象クラス `Visitor` の `visit` メソッドの具体的な実装を行っています。`visit(TaskGroup)` メソッドの中身はどのクラスも「子要素をループして `accept` メソッドを呼び直す」処理を行っています。一方、`visit(Task)` メソッドの中身はクラスごとに異なる処理を行っています。

最後に、実行クラスを見ていきましょう。

**`Main.java`**

```java
package example;

public class Main {
    public static void main(String[] args) {
        TaskGroup designGroup = new TaskGroup("設計");
        designGroup.add(new Task("要件定義", 8, "完了"));
        designGroup.add(new Task("画面設計", 12, "進行中"));

        TaskGroup testGroup = new TaskGroup("テスト");
        testGroup.add(new Task("単体テスト", 10, "未着手"));
        testGroup.add(new Task("結合テスト", 6, "未着手"));

        TaskGroup implementationGroup = new TaskGroup("実装");
        implementationGroup.add(new Task("API実装", 20, "進行中"));
        implementationGroup.add(testGroup);

        TaskGroup rootGroup = new TaskGroup("プロジェクトA");
        rootGroup.add(designGroup);
        rootGroup.add(implementationGroup);

        rootGroup.printTaskList();
        System.out.println("合計見積工数：" + rootGroup.getEstimatedHours() + "時間");

        /* ここを追加（ここから） */
        System.out.println();

        System.out.println("【未着手タスク一覧】");
        rootGroup.accept(new IncompleteTaskListVisitor());

        CompletedHoursVisitor completedHoursVisitor = new CompletedHoursVisitor();
        rootGroup.accept(completedHoursVisitor);
        int completedHours = completedHoursVisitor.getTotalHours();
        int totalHours = rootGroup.getEstimatedHours();
        System.out.println("進捗：" + completedHours + "時間 / " + totalHours + "時間（" + String.format("%.1f", completedHours * 100.0 / totalHours) + "%）");

        KeywordSearchVisitor keywordSearchVisitor = new KeywordSearchVisitor("テスト");
        rootGroup.accept(keywordSearchVisitor);
        System.out.println("「テスト」を含むタスク：");
        for (Task task: keywordSearchVisitor.getFoundTasks()) {
            System.out.println(task.getName());
        }
        /* ここを追加（ここまで） */
    }
}
```

**実行結果**

```
プロジェクトA / 設計 / 要件定義（8時間）
プロジェクトA / 設計 / 画面設計（12時間）
プロジェクトA / 実装 / API実装（20時間）
プロジェクトA / 実装 / テスト / 単体テスト（10時間）
プロジェクトA / 実装 / テスト / 結合テスト（6時間）
合計見積工数：56時間

【未着手タスク一覧】
単体テスト（10時間）
結合テスト（6時間）
進捗：8時間 / 56時間（14.3%）
「テスト」を含むタスク：
単体テスト
結合テスト
```

`Main` クラスを振り返ると、`IncompleteTaskListVisitor`・`CompletedHoursVisitor`・`KeywordSearchVisitor` クラスのいずれに対しても、`rootGroup.accept(visitor)` という同じ形の呼び出しになっています。

実行結果は、好ましくない実装とまったく同じになっています。

以上のような実装を行うと、以下のメリットがあります。

- 新しい集計・検索機能（例えば「見積工数が長いタスクの一覧」）を追加する場合も、抽象クラス `Visitor` を継承した新しいクラスを追加するだけで済み、既存の `TaskComponent`・`Task`・`TaskGroup` クラスや、追加済みの他の Visitor 側のクラスには一切手を加える必要がない。
    - その結果、「未着手タスク一覧」「進捗集計」「キーワード検索」といった機能ごとのロジックが、それぞれ 1 つの Visitor 側のクラスに集約されるため、機能ごとの処理内容を 1 つのファイルだけで把握できるようになる。
- `TaskComponent` クラスの責務が「タスクの構造を表すこと」に専念できるようになり、一覧表示・集計・検索といった処理内容と分離ができる。
- 処理内容が抽象クラス `Visitor` のサブクラスとしてオブジェクト化されているため、呼び出し側（`Main` クラス）は `accept(visitor)` という同じ形の呼び出しのまま、目的に応じた `Visitor` のサブクラスを選ぶだけで、二重ディスパッチにより一覧表示・集計・検索といった異なる処理をそれぞれ実行できる。

## まとめ

正しい実装を振り返ると、`TaskComponent`・`Task`・`TaskGroup` クラスは「タスクの構造をどう保持するか」だけに専念し、一覧表示・集計・検索といった具体的な処理内容は `Visitor` の各サブクラスに委ねられています。<br>
このように、Visitor パターンは、二重ディスパッチの仕組みにより、「データ構造（要素）」と「そのデータに対する処理（アルゴリズム）」を別々のクラスへ分離するパターンです。

本記事の内容はここまでとなります。

以降は「もう少し深く知りたい」という方向けの補足となります。今回学んだパターンに繋がる設計原則や、実務で役立つ背景知識について触れています。

---

<a id="深堀り1"></a>

## 【深堀り①】二重ディスパッチの仕組み

ここでは、正しい実装の `Task`・`TaskGroup` クラスが、どちらも `accept` メソッドの中で `visitor.visit(this)` を呼んでいるだけなのに、`visit(Task)`・`visit(TaskGroup)` メソッドの呼び出しが正しく振り分けられていることに関して説明します。ここには 2 段階の処理の振り分け（ディスパッチ）が関わっています。

1. **1 段目（実行時の型によるディスパッチ）**<br>
   `Main` クラスで `accept` メソッドを呼び出す際、実際に実行される `accept` メソッドは、呼び出し元の変数が指すオブジェクトの実行時の型（`Task` 型か `TaskGroup` 型か）によって決まります。<br>
   これは、通常のメソッドオーバーライドによるポリモーフィズムです。
2. **2 段目（静的な型によるディスパッチ）**<br>
   `Task`・`TaskGroup` クラスにおける `accept` メソッドの内部では `visitor.visit(this)` を呼んでいます。このときの `this` の型はコンパイル時に、`Task` クラスの `accept` メソッドでは `Task` 型、`TaskGroup` クラスの `accept` メソッドでは `TaskGroup` 型として常に確定します。

この「1 段目は実行時の型、2 段目はコンパイル時に確定する `this` の型」という 2 つの型情報を使って処理を振り分ける仕組みを「二重ディスパッチ」と呼びます。

### なぜ `accept` メソッドを `TaskComponent` クラスにまとめて実装しないのか

前提として、本記事では `Visitor` 側で `Task`・`TaskGroup` クラスを区別した専用の処理（`visit(Task)`・`visit(TaskGroup)`）を行う必要があります。このことを踏まえてもし `accept` メソッドを `TaskComponent` クラス側にまとめて 1 つだけ実装していた場合を考えてみましょう。

`accept` メソッドを `TaskComponent` クラス側にまとめると、`visitor.visit(this)` の `this` の型は常に `TaskComponent` 型になります。そのため `Visitor` クラス側も `visit(TaskComponent component)` という 1 つのメソッドしか用意できなくなり、`Task`・`TaskGroup` クラスを区別した処理を型によって自動的に振り分けることができなくなります。もし区別したい場合は、`visit` メソッドの内部で `component instanceof Task` のような型チェックを行う必要が生じてしまいます。これは、Visitor パターンが本来避けたい分岐処理が復活してしまうことになります。

以上から、`Task`・`TaskGroup` それぞれのクラスで個別に `accept` メソッドを実装することで `this` の型を保ったまま `visitor.visit(this)` を呼び出し、`Visitor` 側の `visit(Task)`・`visit(TaskGroup)` というオーバーロードの自動選択（＝二重ディスパッチ）を成立させているのです。

### 本記事を用いた具体的な処理の流れ

本記事の `rootGroup` は以下のような木構造になっています。

```
プロジェクトA（TaskGroup）
├─ 設計（TaskGroup）
│    ├─ 要件定義（Task）
│    └─ 画面設計（Task）
└─ 実装（TaskGroup）
     ├─ API実装（Task）
     └─ テスト（TaskGroup）
          ├─ 単体テスト（Task）
          └─ 結合テスト（Task）
```

この木構造のすべての経路を辿ると説明が煩雑になるため、ここでは `rootGroup.accept(visitor)` から「要件定義」タスクに到達するまでの 1 経路に絞って、処理の流れを見ていきます。

1. `accept` メソッドの呼び出し元 `rootGroup` は `TaskGroup` 型のため、`TaskGroup` クラスの `accept` メソッドで `visitor.visit(this)` が呼ばれる
2. `visitor.visit(this)` の `this` の型は `TaskGroup` 型のため、`Visitor` を継承したクラスでは `visit(TaskGroup taskGroup)` の処理が行われる
3. `visit(TaskGroup taskGroup)` 内のループ処理で、`rootGroup` の最初の子要素（`TaskGroup` 型）に対して `child.accept(this)` が呼ばれる
4. 呼び出し元が `TaskGroup` 型のため、1〜2 と同様の手順で `visit(TaskGroup taskGroup)` の処理が行われる
5. `visit(TaskGroup taskGroup)` 内のループ処理で、その最初の子要素である `Task` 型の「要件定義」に対して `child.accept(this)` が呼ばれる
6. `accept` メソッドの呼び出し元が `Task` 型のため、`Task` クラスの `accept` メソッドで `visitor.visit(this)` が呼ばれる
7. `visitor.visit(this)` の `this` の型は `Task` 型のため、`Visitor` を継承したクラスでは `visit(Task task)` の処理が行われる

<a id="深堀り2"></a>

## 【深堀り②】なぜ `TaskGroup` クラスはインターフェース `Iterable` を実装するのか

正しい実装の `Visitor` の各サブクラスに実装されている `visit(TaskGroup)` メソッドを振り返ると、拡張 for 文を使って子要素を辿っています。

ここで Java の仕様上の制約として、Java の拡張 for 文（`for (要素の型 変数名: 対象)`）は、対象が配列もしくは、`Iterable<T>` インターフェースを実装したクラスのインスタンスでないと使用できないというものがあります。

そのため、`TaskGroup` クラスは配列ではないことから、拡張 for 文を使用できるようにするために `Iterable<TaskComponent>` を実装しています。

### 補足

ここで `TaskGroup` クラスが外部に公開する必要があるのは、`children` を順に読み取るという操作だけです。<br>
`add` メソッドによるタスクの追加以外に、`children` の中身を書き換える操作は想定していません。

ではもし次のように、`children` の中身を外部から取得できるようなメソッドを追加した場合どうなるのでしょうか？

**`TaskGroup.java`（一部抜粋）**

```java
public class TaskGroup extends TaskComponent implements Iterable<TaskComponent> {
    // 既存のフィールド・メソッドは省略

    public List<TaskComponent> getChildren() {
        return children;
    }
}
```

`getChildren` メソッドは `children` フィールドと同じ `List` の参照をそのまま返します。そのため、次のコードのように、呼び出し側で `add` メソッドを経由せずに `children` の中身を直接書き換える操作ができてしまいます。

**`Main.java`（一部抜粋）**

```java
rootGroup.getChildren().clear();             // add を経由せず、子要素を全削除できてしまう
rootGroup.getChildren().remove(designGroup); // 特定の子要素だけを削除できてしまう
```

以上から、`private` 以外のアクセス修飾子で `List` 型である `children` をそのまま返すメソッドを実装すると、`TaskGroup` クラス自身が把握しないまま `children` の中身を書き換えられてしまい、`add` メソッドを唯一の変更経路として管理するという意図が崩れてしまいます。

本記事では、呼び出し側に許可する操作を「順に読み取ること」だけに絞り込みつつ、拡張 for 文を使用できるようにするために、`Iterable<TaskComponent>` を実装し、`iterator` メソッドで `Iterator<TaskComponent>` だけを返す設計にしています。

<a id="深堀り3"></a>

## 【深堀り③】Composite パターンとの関係

本記事の `TaskComponent`・`Task`・`TaskGroup` クラスを振り返ると、`TaskGroup` クラスが持つ `children` フィールドと `add` メソッドで子要素を保持し、`getEstimatedHours`・`printTaskList` メソッドの内部で子要素に同じ処理を再帰的に呼び出すこと（→ [既存コードの仕様](#既存コードの仕様)）で、個別要素（`Task`）と複合要素（`TaskGroup`）を同じ型として扱う Composite パターンの構造を持っています。<br>
Visitor パターンは、こうした既存の Composite 構造の上に処理を追加する形で使われることが多いパターンです。

ただし、正しい実装の `visit(TaskGroup)` メソッドを振り返ると、`Visitor` のサブクラスのどれもが「子要素をループして `accept` メソッドを呼び直す」という同じ走査処理を個別に持っています。既存の `getEstimatedHours`・`printTaskList` メソッドでは、この走査処理は `TaskGroup` クラス側に 1 つだけ実装されていたのに対し、Visitor パターンでは新しい `Visitor` のサブクラスを追加するたびにこの走査処理も一緒に複製されることになります。

これは「処理の重複を減らすこと」と「`Visitor` の各サブクラスが走査方法を自由に制御できること」のトレードオフです。<br>
走査処理を `TaskComponent` クラス側に「子要素へ `accept` を伝播させるデフォルト処理」としてまとめれば重複はなくなりますが、その代わりすべての `Visitor` のサブクラスが同じ順序・同じ範囲で子要素を辿ることになり、サブクラスごとに走査を途中で打ち切ったり、特定の子要素だけを辿ったりといった個別の制御ができなくなります。

本記事では、個別の制御ができることを優先し、走査処理を `Visitor` クラス側に委ねる設計を採用しています。

<a id="深堀り4"></a>

## 【深堀り④】Java 標準ライブラリにおける Visitor パターンの例

Java 標準ライブラリにおける Visitor パターンの例として、`java.nio.file` パッケージのファイルツリー走査を見ていきましょう。<br>
代表的なのが `FileVisitor` インターフェースと、その空実装を提供する `SimpleFileVisitor` クラスです。

**`SimpleFileVisitor.java`**

```java
package java.nio.file;

public class SimpleFileVisitor<T> implements FileVisitor<T> {
    protected SimpleFileVisitor() {}

    @Override
    public FileVisitResult preVisitDirectory(T dir, BasicFileAttributes attrs) throws IOException {
        Objects.requireNonNull(dir);
        Objects.requireNonNull(attrs);
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(T file, BasicFileAttributes attrs) throws IOException {
        Objects.requireNonNull(file);
        Objects.requireNonNull(attrs);
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFileFailed(T file, IOException exc) throws IOException {
        Objects.requireNonNull(file);
        throw exc;
    }

    @Override
    public FileVisitResult postVisitDirectory(T dir, IOException exc) throws IOException {
        Objects.requireNonNull(dir);
        if (exc != null)
            throw exc;
        return FileVisitResult.CONTINUE;
    }
}
```

> 引用元: OpenJDK [SimpleFileVisitor.java](https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/nio/file/SimpleFileVisitor.java)

`Files` クラスの `static` メソッド `walkFileTree` を例に見ていきましょう。

**`Main.java`**

```java
package example;

public class Main {
    public static void main(String[] args) {
        try {
            Files.walkFileTree(Path.of("."), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    System.out.println("file: " + file);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

このコードでは、`Files` クラスの `walkFileTree` メソッドが指定したディレクトリ以下を再帰的に走査し、ファイルを見つけるたびに、引数に渡した `SimpleFileVisitor` を継承したクラスの `visitFile` メソッドを呼び出しています（ディレクトリに入る前後には、それぞれ `preVisitDirectory`・`postVisitDirectory` メソッドが呼ばれます）。<br>
本記事の Visitor パターンでは、走査（`accept` メソッドの呼び出し）を `Task`・`TaskGroup` クラス側と `Visitor` の各サブクラス側の双方が分担していましたが、`walkFileTree` メソッドでは走査そのものを `java.nio.file` パッケージ側がすべて引き受け、呼び出し側は `FileVisitor` インターフェース側に「訪れた要素に対して何をするか」だけを実装すればよくなっています。<br>
実装の形は異なりますが、「構造をどう辿るか」と「辿った要素に対して何をするか」を分離するという考え方は、本記事の Visitor パターンと共通しています。

<a id="深堀り5"></a>

## 【深堀り⑤】OCP（オープン・クローズドの原則）

Visitor パターンは、OCP（オープン・クローズドの原則）に対して 2 つの異なる側面を持っています。

### 処理を追加する軸

もし `Visitor` を継承した処理クラス（例えば「見積工数が長いタスクの一覧」）を追加する場合、新しい `Visitor` のサブクラスを 1 つ追加するだけで済み、既存の `TaskComponent`・`Task`・`TaskGroup` クラスや、追加済みの他の `Visitor` のサブクラスを変更する必要はありません。

この「既存コードを変えずに、新しいクラスを追加するだけで機能を拡張できる」という設計は、「**OCP（Open/Closed Principle：オープン・クローズドの原則）**」と呼ばれる設計原則の実践です。

### 要素の種類を追加する軸

もし `TaskComponent` を継承した新しい要素クラス（例えば「マイルストーン」を表すクラス）を追加する場合、`Visitor` クラスに新しい `visit` メソッドを追加する必要があります。これに伴い、既存のすべての `Visitor` のサブクラスに新たに追加した `visit` メソッドの実装を行わなければなりません。

これは、「**OCP**」に違反しています。

### まとめ

以上から Visitor パターンは「処理の追加は容易だが、要素の種類の追加は困難」という二面性を持っています。そのため、要素の種類（今回でいう `Task`・`TaskGroup`）が安定していて、処理の種類（一覧表示・集計・検索など）が今後も増えていくことが見込まれる場面に向いたパターンだといえます。

<a id="深堀り6"></a>

## 【深堀り⑥】GoF デザインパターンとの位置づけ

今回使った Visitor パターンは、GoF（Gang of Four）の 23 のデザインパターンのうち「振る舞いパターン」に分類されます。<br>
詳しくは「GoF」で検索してみてください。
