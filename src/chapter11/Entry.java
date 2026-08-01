package chapter11;

public abstract class Entry {
    // 名前取得
    public abstract String getName();

    // サイズ取得
    public abstract int getSize();

    // 一覧を表示する
    public void printList() {
        printList("");
    }

    // prefixを前につけて一覧を表示する
    protected abstract void printList(String prefix);

    @Override
    public String toString() {
        return getName() + " (" + getSize() + ")";
    }
}
