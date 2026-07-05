package chapter09;

public class MainChapter09 {
    public static void main(String[] args) {
        Display d1 = new Display(new StringDisplayImp("Hello, Japan."));
        d1.display();

        Display d2 = new CountDisplay(new StringDisplayImp("Hello, World."));
        d2.display();

        CountDisplay d3 = new CountDisplay(new StringDisplayImp("Hello, Universe."));
        d3.display();
        d3.multiDisplay(5);

        // 練習問題9-1
        RandomCountDisplay d4 = new RandomCountDisplay(new StringDisplayImp("Hello, Rondom."));
        d4.display();
        d4.randomDisplay(5);

        // 練習問題9-2
        Display d5 = new Display(new FileContentDisplayImp("practice9-2.txt"));
        d5.display();
    }
}
