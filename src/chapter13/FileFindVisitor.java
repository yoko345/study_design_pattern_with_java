package chapter13;

import java.util.ArrayList;
import java.util.List;

public class FileFindVisitor extends Visitor {
    private String searchName;
    private List<File> foundFiles = new ArrayList<>();

    public FileFindVisitor(String searchName) {
        this.searchName = searchName;
    }

    @Override
    public void visit(File file) {
        if (file.getName().contains(searchName)) {
            foundFiles.add(file);
        }
    }

    @Override
    public void visit(Directory directory) {
        for (Entry entry : directory) {
            entry.accept(this);
        }
    }

    // 模範解答ではList<File>ではなくIterable<File>を返している。
    // 呼び出し側はfor-eachで反復するだけなので、公開する操作を反復のみに絞る狙いがある。
    public List<File> getFoundFiles() {
        return foundFiles;
    }
}
