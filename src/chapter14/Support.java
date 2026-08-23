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
        if (resolve(trouble)) {
            done(trouble);
        } else if (nextSupport != null) {
            nextSupport.support(trouble);
        } else {
            fail(trouble);
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
