package chapter13;


public class MainChapter13 {
    public static void main(String[] args) {
        System.out.println("Making root entries...");

        Directory rootdir = new Directory("root");
        Directory bindir = new Directory("bin");
        Directory tmpdir = new Directory("tmp");
        Directory userdir = new Directory("user");

        rootdir.add(bindir);
        rootdir.add(tmpdir);
        rootdir.add(userdir);

        bindir.add(new File("vi", 10000));
        bindir.add(new File("latex", 20000));

        rootdir.accept(new ListVisitor());

        System.out.println();

        System.out.println("Making user entries...");
        Directory yuki = new Directory("yuki");
        Directory hanako = new Directory("hanako");
        Directory tomura = new Directory("tomura");

        userdir.add(yuki);
        userdir.add(hanako);
        userdir.add(tomura);

        yuki.add(new File("diary.html", 100));
        yuki.add(new File("Composite.java", 200));
        hanako.add(new File("memo.tex", 300));
        // 練習問題13-1（ここから）
        hanako.add(new File("index.html", 350));
        // 練習問題13-1（ここまで）
        tomura.add(new File("game.doc", 400));
        tomura.add(new File("junk.mail", 500));

        rootdir.accept(new ListVisitor());


        // 練習問題13-1
        System.out.println();

        FileFindVisitor fileFindVisitor = new FileFindVisitor(".html");
        rootdir.accept(fileFindVisitor);

        System.out.println("HTML files are: ");
        for (File file : fileFindVisitor.getFoundFiles()) {
            System.out.println(file);
        }
    }
}
