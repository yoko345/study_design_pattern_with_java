package chapter11;


public class MainChapter11 {
    public static void main(String[] args) {
        System.out.println("Making root enties...");

        Directory rootdir = new Directory("root");
        Directory bindir = new Directory("bin");
        Directory tmpdir = new Directory("tmp");
        Directory userdir = new Directory("user");

        rootdir.add(bindir);
        rootdir.add(tmpdir);
        rootdir.add(userdir);

        bindir.add(new File("vi", 10000));
        bindir.add(new File("latex", 20000));

        rootdir.printList();

        System.out.println();

        System.out.println("Making user enties...");
        Directory yuki = new Directory("yuki");
        Directory hanako = new Directory("hanako");
        Directory tomura = new Directory("tomura");

        userdir.add(yuki);
        userdir.add(hanako);
        userdir.add(tomura);

        yuki.add(new File("diary.thml", 100));
        yuki.add(new File("Composite.java", 200));
        hanako.add(new File("memo.tex", 300));
        tomura.add(new File("game.doc", 400));
        tomura.add(new File("junk.mail", 500));

        rootdir.printList();
    }
}
