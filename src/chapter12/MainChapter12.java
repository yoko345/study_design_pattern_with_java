package chapter12;

public class MainChapter12 {
    public static void main(String[] args) {
        Display display1 = new StringDisplay("Hello, World.");
        display1.show();
        Display display2 = new SideBorder(display1, '#');
        display2.show();
        Display display3 = new FullBorder(display2);
        display3.show();

        Display display4 = new SideBorder(
                new FullBorder(new FullBorder(
                        new SideBorder(new FullBorder(new StringDisplay("Hello, World.")), '*'))),
                '/');
        display4.show();
    }
}
