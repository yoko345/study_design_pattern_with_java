package chapter13;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Directory extends Entry implements Iterable<Entry> {
    private String name;
    private List<Entry> directory = new ArrayList<>();

    public Directory(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getSize() {
        int size = 0;
        for (Entry entry : directory) {
            size += entry.getSize();
        }
        return size;
    }

    public Entry add(Entry entry) {
        directory.add(entry);
        return this;
    }

    // visitor.visit(this) の"this"はDirectory型なので、
    // オーバーロード解決によりvisit(Directory)側が呼ばれる（二重ディスパッチ）。
    @Override
    public void accept(Visitor visitor) {
        visitor.visit(this);
    }

    // 拡張for文（for (Entry entry: directory)）で中身を順に取り出せるようにするための実装。
    // 内部のListが持つIteratorをそのまま返している（自前で作り直す必要はない）。
    @Override
    public Iterator<Entry> iterator() {
        return directory.iterator();
    }
}
