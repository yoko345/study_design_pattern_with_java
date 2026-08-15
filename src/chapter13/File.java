package chapter13;

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

    // visitor.visit(this) の"this"はFile型なので、
    // オーバーロード解決によりvisit(File)側が呼ばれる（二重ディスパッチ）。
    @Override
    public void accept(Visitor visitor) {
        visitor.visit(this);
    }
}
