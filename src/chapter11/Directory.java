package chapter11;

import java.util.ArrayList;
import java.util.List;

public class Directory extends Entry {
    private String name;
    private List<Entry> directory = new ArrayList<>();
    // 練習問題11-2
    private List<String> fullPathsList = new ArrayList<>();

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

    // 練習問題11-2（ここから）
    @Override
    public String getFullPath() {
        return "";
    }

    public void printFullPath() {
        for (String fullPath : fullPathsList) {
            System.out.println(fullPath);
        }
    }
    // 練習問題11-2（ここまで）


    @Override
    protected void printList(String prefix) {
        // 自分自身を "親から渡されたprefix + 自分の名前" で表示する
        System.out.println(prefix + "/" + this);
        for (Entry entry : directory) {
            // 子には「自分のパス」を新しいprefixとして渡し、再帰的に一覧表示させる
            entry.printList(prefix + "/" + name);

            // 練習問題11-2
            fullPathsList.add(entry.getFullPath());
        }
    }

    public Entry add(Entry entry) {
        directory.add(entry);
        return this;
    }
}
