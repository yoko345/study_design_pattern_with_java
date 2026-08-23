package chapter14;


public abstract class Support {
    private String name;
    private Support nextSupport;

    public Support(String name) {
        this.name = name;
        this.nextSupport = null;
    }

    // たらい回し先の設定
    public Support setNextSupport(Support nextSupport) {
        this.nextSupport = nextSupport;
        return nextSupport;
    }

    // トラブル解決の手順を定める
    public void support(Trouble trouble) {
        // 練習問題14-3（模範解答）
        // if (resolve(trouble)) {
        //     done(trouble);
        // } else if (nextSupport != null) {
        //     nextSupport.support(trouble);
        // } else {
        //     fail(trouble);
        // }
        for (Support obj = this; true; obj = obj.nextSupport) {
            if (obj.resolve(trouble)) {
                obj.done(trouble);
                break;
            } else if (obj.nextSupport == null) {
                obj.fail(trouble);
                break;
            }
        }
    }

    // 解決しようとする
    protected abstract boolean resolve(Trouble trouble);

    // 解決した
    protected void done(Trouble trouble) {
        System.out.println(trouble + " is resolved by " + this + ".");
    }

    // 解決しなかった
    protected void fail(Trouble trouble) {
        System.out.println(trouble + " cannot be resolved.");
    }

    @Override
    public String toString() {
        return "[" + name + "]";
    }
}

// 練習問題14-3 自分の解答の問題点（メモ）
// 1. setNextSupport の戻り値を nextSupport ではなく this にしたため、A.setNextSupport(B).setNextSupport(C)... という
// fluent chain 呼び出しでB, C, D... が全て A の supportList に直接追加されてしまい、「各ハンドラは次のハンドラだけを知る」という鎖の構造が壊れていた。
// 2. 解決したハンドラの名前を this.name に代入していたため、A オブジェクトの name
// フィールドが解決した相手の名前で上書きされ、オブジェクトの同一性が壊れる副作用バグがあった（例: A.support() で B が解決すると、以降 A を表示すると [B] になる）。
//
// 模範解答（現在のこのファイル）との比較
// - フィールドは nextSupport 単一参照のまま変更していない（List化していない）。
// → 各ハンドラが「次の1つ」だけを知るという鎖の構造をそのまま維持できている。
// - setNextSupport の戻り値も return nextSupport; のまま変更していない。
// → fluent chain 呼び出し（A.setNextSupport(B).setNextSupport(C)...）が元の意図どおり A→B→C... という一本の鎖を正しく組み立てる。
// - support() 内でループ変数 obj を導入し、obj.resolve() / obj.done() / obj.fail() と「今見ているオブジェクト自身」に対して処理を行う。
// → this.name のような共有フィールドを書き換える必要がなく、副作用バグが発生しない。
// - 変更範囲が support() メソッドの中身（再帰→ループ）だけに閉じており、クラスの外部から見た構造（フィールド・メソッドシグネチャ）は元のまま。
// → 「再帰をループに変える」という設問の意図に対して最小限の差分になっている。
