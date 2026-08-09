package chapter12;

public class MainChapter12 {
    public static void main(String[] args) {
        Display display1 = new StringDisplay("Hello, World.");
        display1.show();
        // 練習問題12-1
        // Display display2 = new SideBorder(display1, '#');
        Display display2 = new UpDownBorder(display1, '~');
        display2.show();
        // 練習問題12-1
        // Display display3 = new FullBorder(display2);
        Display display3 = new SideBorder(display2, '*');
        display3.show();

        // 練習問題12-1
        // Display display4 = new SideBorder(
        // new FullBorder(new FullBorder(
        // new SideBorder(new FullBorder(new StringDisplay("Hello, World.")), '*'))),
        // '/');
        Display display4 =
                new FullBorder(new UpDownBorder(
                        new SideBorder(new UpDownBorder(
                                new SideBorder(new StringDisplay("Hello, World."), '*'), '='), '|'),
                        '/'));
        display4.show();

        // 練習問題12-2
        System.out.println();

        MultiStringDisplay display5 = new MultiStringDisplay();
        display5.add("Hi!");
        display5.add("Good morning.");
        display5.add("Good night!");
        display5.show();

        Display display6 = new SideBorder(display5, '$');
        display6.show();

        Display display7 = new FullBorder(display5);
        display7.show();
    }
}
