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

真っ先に思いつくのは、`TaskComponent` クラスに新しい抽象メソッドを追加し、`Task`・`TaskGroup` 双方で具体的な処理を実装する、という方法ではないでしょうか？

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

    public abstract void printIncompleteTaskList();

    public abstract int getCompletedEstimatedHours();

    public abstract List<Task> findTasksByKeyword(String keyword);
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

        System.out.println("【未着手タスク一覧】");
        rootGroup.printIncompleteTaskList();

        int completedHours = rootGroup.getCompletedEstimatedHours();
        int totalHours = rootGroup.getEstimatedHours();
        System.out.println("進捗：" + completedHours + "時間 / " + totalHours + "時間（" + String.format("%.1f", completedHours * 100.0 / totalHours) + "%）");

        System.out.println("「実装」を含むタスク：");
        for (Task task: rootGroup.findTasksByKeyword("実装")) {
            System.out.println(task.getName());
        }
    }
}
```

**実行結果**

```
【未着手タスク一覧】
単体テスト（10時間）
結合テスト（6時間）
進捗：8時間 / 56時間（14.3%）
「実装」を含むタスク：
API実装
```

コンパイルエラーがなく結果が出力されていることから、一見すると実装・動作確認ともに問題ないように見えます。

しかし、この実装には以下の問題点があります。

- 新しい集計・検索機能を 1 つ追加するたびに、`TaskComponent` クラスへの抽象メソッドの追加と、`Task`・`TaskGroup` クラス双方への実装が必要になり、修正対象のクラス数が常に 3 つずつ増えていく。
- `TaskComponent` クラスは本来「タスクの構造」を表すクラスですが、一覧表示・集計・検索といった処理内容までもがこのクラスの責務に混ざり込んでいき、クラスの目的が曖昧になっていく。
- `printIncompleteTaskList`・`getCompletedEstimatedHours`・`findTasksByKeyword` メソッドはいずれも「`children` を辿って再帰的に同じ処理を呼び出す」という同じ構造を持っており、新しい機能を追加するたびにこの走査処理そのものも複製されている。

## 正しい実装

では、好ましくない実装で挙げた問題点を解決するにはどうすればよいのでしょうか？

これらの問題を解決するのが **Visitor パターン**です。<br>
処理内容を `TaskComponent` 側から切り離し、外部の専用クラスへ委ねることで、新しい処理を追加してもタスクの構造を表すクラス側には手を入れずに済むようになります。

まず、「Visitor を受け入れられる要素である」ことを示す共通の型から見ていきましょう。

**`Element.java`**

```java
package example;

public interface Element {
    void accept(Visitor visitor);
}
```

`Element` インタフェースは、`accept` メソッド 1 つだけを持つシンプルな型です。`TaskComponent` クラスがこのインタフェースを実装することで、`Task`・`TaskGroup` のどちらも「Visitor を受け入れられる要素である」という契約を型で表せるようになります。

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

`TaskComponent` クラスを振り返ると、既存の仕様から `Element` インタフェースを実装する修正が加わっている一方、`accept` メソッドの具体的な実装はここでは行っていません。`accept` メソッドの実装は、`Task`・`TaskGroup` それぞれに委ねます（理由は→ [【深堀り①】二重ディスパッチの仕組み](#深堀り1)）。

次に、処理内容を表す `Visitor` 側の抽象クラスを見ていきましょう。

**`Visitor.java`**

```java
package example;

public abstract class Visitor {
    public abstract void visit(Task task);

    public abstract void visit(TaskGroup taskGroup);
}
```

`Visitor` クラスは、`Task` 用・`TaskGroup` 用の `visit` メソッドをオーバーロードで定義しているだけの抽象クラスです。具体的な処理内容は、この抽象クラスを継承した具象クラス側で実装します。

続いて、`Task`・`TaskGroup` それぞれに `accept` メソッドを追加します。

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

    @Override
    public void accept(Visitor visitor) {
        visitor.visit(this);
    }
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

    @Override
    public void accept(Visitor visitor) {
        visitor.visit(this);
    }

    @Override
    public Iterator<TaskComponent> iterator() {
        return children.iterator();
    }
}
```

`Task`・`TaskGroup` クラスを振り返ると、どちらも `accept` メソッドの中身は `visitor.visit(this)` を呼んでいるだけですが、`Task` 側の呼び出しでは `visit(Task)` が、`TaskGroup` 側の呼び出しでは `visit(TaskGroup)` が、それぞれ正しく呼び分けられます（仕組みの詳細は→ [【深堀り①】二重ディスパッチの仕組み](#深堀り1)）。<br>
また、`TaskGroup` クラスには `Iterable<TaskComponent>` を実装する修正も加わっています。これは、このあと作成する Visitor 側で拡張 for 文を使って `children` の中身を辿れるようにするためのものです（理由の詳細は→ [【深堀り②】なぜ TaskGroup は Iterable を実装するのか](#深堀り2)）。

準備が整ったので、具体的な処理を行う Visitor を 3 つ作成しましょう。

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

`IncompleteTaskListVisitor`・`CompletedHoursVisitor`・`KeywordSearchVisitor` クラスを振り返ると、いずれも `visit(TaskGroup)` の中身は「子要素をループして `accept` を呼び直す」という同じ形をしていますが、`visit(Task)` の中身はクラスごとにまったく異なる処理を行っています。処理内容ごとにクラスが分かれているため、`TaskComponent`・`Task`・`TaskGroup` クラス側を一切変更せずに、新しい処理を追加できるようになっています。

最後に、実行クラスの実装を見ていきましょう。

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

        System.out.println("【未着手タスク一覧】");
        rootGroup.accept(new IncompleteTaskListVisitor());

        CompletedHoursVisitor completedHoursVisitor = new CompletedHoursVisitor();
        rootGroup.accept(completedHoursVisitor);
        int completedHours = completedHoursVisitor.getTotalHours();
        int totalHours = rootGroup.getEstimatedHours();
        System.out.println("進捗：" + completedHours + "時間 / " + totalHours + "時間（" + String.format("%.1f", completedHours * 100.0 / totalHours) + "%）");

        KeywordSearchVisitor keywordSearchVisitor = new KeywordSearchVisitor("実装");
        rootGroup.accept(keywordSearchVisitor);
        System.out.println("「実装」を含むタスク：");
        for (Task task: keywordSearchVisitor.getFoundTasks()) {
            System.out.println(task.getName());
        }
    }
}
```

**実行結果**

```
【未着手タスク一覧】
単体テスト（10時間）
結合テスト（6時間）
進捗：8時間 / 56時間（14.3%）
「実装」を含むタスク：
API実装
```

`Main` クラスを振り返ると、`rootGroup.accept(visitor)` という同じ形の呼び出しだけで、一覧表示・集計・検索という異なる処理をそれぞれ実行できています。実行結果は、好ましくない実装とまったく同じになっています。

以上のような実装を行うと、以下のメリットがあります。

- 新しい集計・検索機能（例えば「優先度が高いタスクの一覧」）を追加する場合も、`Visitor` を継承した新しいクラスを追加するだけで済み、既存の `TaskComponent`・`Task`・`TaskGroup` クラスや、追加済みの他の Visitor クラスには一切手を加える必要がない。
- `TaskComponent` クラスの責務が「タスクの構造を表すこと」に専念できるようになり、一覧表示・集計・検索といった処理内容と分離されている。
- `Task` と `TaskGroup` は共通の `Element` 型として扱えるため、呼び出し側（`Main` クラス）はどちらの具象クラスに対しても同じ `accept(visitor)` 呼び出しだけで処理を任せられる。

## まとめ

正しい実装を振り返ると、`TaskComponent`・`Task`・`TaskGroup` クラスは「タスクの構造をどう保持するか」だけに専念し、一覧表示・集計・検索といった具体的な処理内容は `Visitor` クラスを継承した各クラスに委ねられています。<br>
このように、Visitor パターンは、データ構造を表すクラス群に `accept` メソッドだけを追加し、実際の処理内容を外部の Visitor クラスへ切り出すことで、新しい処理を追加するたびに構造側のクラスを変更する必要をなくすパターンです。

本記事の内容はここまでとなります。

以降は「もう少し深く知りたい」という方向けの補足となります。今回学んだパターンに繋がる設計原則や、実務で役立つ背景知識について触れています。

---

<a id="深堀り1"></a>

## 【深堀り①】二重ディスパッチの仕組み

正しい実装の `Task`・`TaskGroup` クラスは、どちらも `accept` メソッドの中で `visitor.visit(this)` を呼んでいるだけなのに、なぜ正しいオーバーロードへ振り分けられるのでしょうか。ここには 2 段階の処理の振り分け（ディスパッチ）が関わっています。

1. **1 段目（実行時の型によるディスパッチ）**<br>
   `rootGroup.accept(visitor)` のように `accept` メソッドを呼び出す際、実際に実行される `accept` メソッドは、呼び出し元の変数が指すオブジェクトの実行時の型（`Task` か `TaskGroup` か）によって決まります。これは、通常のメソッドオーバーライドによるポリモーフィズムです。
2. **2 段目（静的な型によるディスパッチ）**<br>
   各クラスの `accept` メソッドの内部では `visitor.visit(this)` を呼んでいますが、`Task` クラスの `accept` メソッドの中では `this` の型は常に `Task` 型、`TaskGroup` クラスの `accept` メソッドの中では `this` の型は常に `TaskGroup` 型として、コンパイル時に確定しています。そのため、`Visitor` クラスに用意された `visit(Task)`・`visit(TaskGroup)` のどちらを呼び出すかも、オーバーロード解決によってコンパイル時に決まります。

この「1 段目は実行時の型、2 段目はコンパイル時に確定する `this` の型」という 2 つの型情報を使って処理を振り分ける仕組みが、二重ディスパッチと呼ばれます。<br>
もし `accept` メソッドを `TaskComponent` クラス側にまとめて 1 つだけ実装していた場合、その中の `this` の型は `TaskComponent` 型に固定されてしまいます。`Visitor` クラスに `visit(TaskComponent)` というオーバーロードは存在しない（用意しても `Task` と `TaskGroup` を区別できない）ため、`Task`・`TaskGroup` それぞれのクラスで個別に `accept` メソッドを実装する必要があるのです。

<a id="深堀り2"></a>

## 【深堀り②】なぜ TaskGroup は Iterable を実装するのか

正しい実装の各 Visitor クラスは、`visit(TaskGroup)` メソッドの中で `for (TaskComponent child: taskGroup) { child.accept(this); }` という拡張 for 文を使って子要素を辿っています。この 1 行を成立させるために、`TaskGroup` クラス側に `Iterable<TaskComponent>` の実装が必要になります。

Java の拡張 for 文（`for (要素の型 変数名: 対象)`）は、対象が配列であるか、`Iterable<T>` インタフェースを実装したクラスのインスタンスでなければ使えないという言語仕様上の制約があります。`TaskGroup` クラスは配列ではないため、拡張 for 文の対象にするには `Iterable<TaskComponent>` を実装するほかありません。

では、なぜ `children` フィールドを `List<TaskComponent>` 型の getter として公開する（例えば `getChildren` メソッドを追加する）方法を取らなかったのでしょうか。それは、`List` をそのまま公開してしまうと、呼び出し側から `clear`・`remove` などのメソッドを直接呼び出せてしまい、`add` メソッドを経由しない不正な変更を許してしまうためです。<br>
`Iterable<TaskComponent>` を実装し、`iterator` メソッドで `Iterator<TaskComponent>` だけを返す形にすることで、呼び出し側に許可する操作を「順に読み取ること」だけに絞り込みつつ、拡張 for 文という簡潔な構文を使えるようにしています。

まとめると、Visitor 側の走査コードで拡張 for 文を使うためには、走査対象となるクラス（今回は `TaskGroup`）が `Iterable` を実装している必要がある、という結論になります。

<a id="深堀り3"></a>

## 【深堀り③】Composite パターンとの関係

今回の `TaskComponent`・`Task`・`TaskGroup` クラスは、個別要素と複合要素を同じ型として扱う Composite パターンの構造をすでに持っています。Visitor パターンは、こうした既存の Composite 構造の上に処理を追加する形で使われることが多いパターンです。

ただし、正しい実装の `visit(TaskGroup)` メソッドを振り返ると、`IncompleteTaskListVisitor`・`CompletedHoursVisitor`・`KeywordSearchVisitor` クラスのどれもが「子要素をループして `accept` を呼び直す」という同じ走査処理を個別に持っています。既存の `getEstimatedHours`・`printTaskList` メソッドでは、この走査処理は `TaskGroup` クラス側に 1 つだけ実装されていたのに対し、Visitor パターンでは新しい Visitor クラスを追加するたびにこの走査処理も一緒に複製されることになります。<br>
これは GoF の基本形どおりの実装であり、Visitor パターンが抱える既知のトレードオフです。走査処理そのものを共通化したい場合は、`TaskComponent` クラス側に「子要素へ `accept` を伝播させるデフォルト処理」を用意し、各 Visitor クラスはそれを呼び出すだけにするといった発展的な設計も考えられますが、本記事では GoF の基本形に忠実な実装にとどめています。

<a id="深堀り4"></a>

## 【深堀り④】Java 標準ライブラリにおける Visitor パターンの例

Visitor パターンは、`java.nio.file` パッケージのファイルツリー走査にも使われています。代表的なのが `FileVisitor` インタフェースと、その空実装を提供する `SimpleFileVisitor` クラスです。

```java
Files.walkFileTree(Path.of("."), new SimpleFileVisitor<Path>() {
    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
        System.out.println("file: " + file);
        return FileVisitResult.CONTINUE;
    }
});
```

このコードでは、`Files` クラスの `walkFileTree` メソッドが指定したディレクトリ以下を再帰的に走査し、ファイルを見つけるたびに `SimpleFileVisitor` クラス側の `visitFile` メソッドを呼び出しています（ディレクトリに入る前後には、それぞれ `preVisitDirectory`・`postVisitDirectory` メソッドが呼ばれます）。<br>
本記事の Visitor パターンでは、走査（`accept` メソッドの呼び出し）を `Task`・`TaskGroup` クラス側と各 Visitor クラス側の双方が分担していましたが、`walkFileTree` メソッドでは走査そのものを `java.nio.file` パッケージ側がすべて引き受け、呼び出し側は `FileVisitor` インタフェース側に「訪れた要素に対して何をするか」だけを実装すればよくなっています。実装の形は異なりますが、「構造をどう辿るか」と「辿った要素に対して何をするか」を分離するという考え方は、本記事の Visitor パターンと共通しています。

<a id="深堀り5"></a>

## 【深堀り⑤】OCP（オープン・クローズドの原則）

Visitor パターンは、OCP（オープン・クローズドの原則）に対して 2 つの異なる側面を持っています。

- **処理を追加する軸では OCP を満たす**：新しい集計・検索機能を追加する場合、新しい Visitor クラスを 1 つ追加するだけで済み、既存の `TaskComponent`・`Task`・`TaskGroup` クラスや、追加済みの他の Visitor クラスを変更する必要はありません。
- **要素の種類を追加する軸では OCP を満たさない**：もし `TaskComponent` を継承した新しい要素クラス（例えば「マイルストーン」を表すクラス）を追加する場合、`Visitor` クラスに新しい `visit` メソッドを追加する必要があり、それに伴って `IncompleteTaskListVisitor`・`CompletedHoursVisitor`・`KeywordSearchVisitor` クラスを含む、既存のすべての Visitor クラスにその `visit` メソッドの実装を追加しなければなりません。

つまり Visitor パターンは「処理の追加は容易だが、要素の種類の追加は困難」という二面性を持っています。そのため、要素の種類（今回でいう `Task`・`TaskGroup`）が安定していて、処理の種類（一覧表示・集計・検索など）が今後も増えていくことが見込まれる場面に向いたパターンだといえます。

<a id="深堀り6"></a>

## 【深堀り⑥】GoF デザインパターンとの位置づけ

今回使った Visitor パターンは、GoF（Gang of Four）の 23 のデザインパターンのうち「振る舞いパターン」に分類されます。<br>
詳しくは「GoF」で検索してみてください。
