package chapter11;

public abstract class Entry {
    // 練習問題11-2（模範解答）
    private Entry parent;

    // 名前取得
    public abstract String getName();

    // サイズ取得
    public abstract int getSize();

    // 練習問題11-2
    // パス取得
    public abstract String getFullPath();

    // 練習問題11-2（模範解答）（ここから）
    protected void setParent(Entry parent) {
        this.parent = parent;
    }

    public String getFullName() {
        StringBuilder fullName = new StringBuilder();
        Entry entry = this;
        do {
            fullName.insert(0, entry.getName());
            fullName.insert(0, "/");
            entry = entry.parent;
        } while (entry != null);
        return fullName.toString();
    }
    // 練習問題11-2（模範解答）（ここまで）

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
