package chapter02;


public class MainChapter02 {
    public static void main(String[] args) throws Exception {
        Print p = new PrintBanner("Hello");

        p.printWeak();
        p.printStrong();
    }
}
