package chapter11;

public class File extends Entry {
    private String name;
    private int size;

    public File(String name, int size) {
        this.name = name;
        this.size = size;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getSize() {
        return size;
    }

    @Override
    protected void printList(String prefix) {
        // ファイルは子を持たないため、ここで再帰が止まる（Compositeパターンの葉）
        System.out.println(prefix + "/" + this);
    }
}
