package chapter13;

public abstract class Entry implements Element {
    // 名前取得
    public abstract String getName();

    // サイズ取得
    public abstract int getSize();

    @Override
    public String toString() {
        return getName() + " (" + getSize() + ")";
    }
}
