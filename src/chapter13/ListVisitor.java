package chapter13;

public class ListVisitor extends Visitor {
    // 現在注目しているディレクトリ名
    private String currentDirectory = "";

    @Override
    public void visit(File file) {
        System.out.println(currentDirectory + "/" + file);
    }

    @Override
    public void visit(Directory directory) {
        System.out.println(currentDirectory + "/" + directory);
        // currentDirectoryは再帰全体で共有されるフィールドなので、潜る前の値を退避しておく。
        String saveDirectory = currentDirectory;
        currentDirectory += "/" + directory.getName();
        for (Entry entry : directory) {
            entry.accept(this);
        }
        // ループを抜けたら退避しておいた値に戻し、呼び出し元の階層のパスに復元する。
        currentDirectory = saveDirectory;
    }
}
