package chapter03;


public class MainChapter03 {
    public static void main(String[] args) throws Exception {
        AbstractDisplay charDisplay = new CharDisplay('H');
        AbstractDisplay stringDisplay = new StringDisplay("Hello World!");

        charDisplay.display();
        stringDisplay.display();
    }
}
