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
        // 練習問題13-2
        // int size = 0;
        // for (Entry entry : directory) {
        // size += entry.getSize();
        // }
        // return size;
        SizeVisitor visitor = new SizeVisitor();
        // 下記の自分の解答は、thisの静的型がDirectoryと分かっているため直接visit(Directory)を呼べてしまうが、それはaccept()が担うはずの二重ディスパッチの責務を呼び出し側が肩代わりしている状態。
        // visitor.visit(this);
        // 模範解答であるaccept(visitor)を使えば、要素は常に「acceptされるだけ」というVisitorパターンの規約を守れる。
        accept(visitor);
        return visitor.getSize();
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
