package chapter14;

// 練習問題14-3
import java.util.ArrayList;
import java.util.List;

public abstract class Support {
    private String name;
    // 練習問題14-3
    // private Support nextSupport;
    private List<Support> supportList = new ArrayList<>();

    public Support(String name) {
        this.name = name;
        // 練習問題14-3
        // this.nextSupport = null;
    }

    // たらい回し先の設定
    public Support setNextSupport(Support nextSupport) {
        // 練習問題14-3
        // this.nextSupport = nextSupport;
        supportList.add(nextSupport);
        return this;
    }

    // トラブル解決の手順を定める
    public void support(Trouble trouble) {
        if (resolve(trouble)) {
            done(trouble);
        // 練習問題14-3
        // } else if (nextSupport != null) {
            // nextSupport.support(trouble);
        } else {
            // 練習問題14-3
            int count = supportList.size();
            for (int i = 0; i < count; i++) {
                if (supportList.get(i).resolve(trouble)) {
                    name = supportList.get(i).name;
                    done(trouble);
                    break;
                } else if (i == count - 1) {
                    fail(trouble);
                }
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
