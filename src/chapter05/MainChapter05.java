package chapter05;


public class MainChapter05 {
    public static void main(String[] args) throws Exception {
        System.out.println("開始");
        Singleton obj1 = Singleton.getInstance();
        Singleton obj2 = Singleton.getInstance();
        if (obj1 == obj2) {
            System.out.println("obj1とobj2は同じインスタンスです。");
        } else {
            System.out.println("obj1とobj2は同じインスタンスではありません。");
        }
        System.out.println("終了");
    }
}
