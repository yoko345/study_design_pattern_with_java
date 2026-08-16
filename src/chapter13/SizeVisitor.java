package chapter13;

public class SizeVisitor extends Visitor {
    private int fileSize = 0;

    @Override
    public void visit(File file) {
        fileSize += file.getSize();
    }

    @Override
    public void visit(Directory directory) {
        for (Entry entry : directory) {
            entry.accept(this);
        }
    }

    public int getSize() {
        return fileSize;
    }
}
