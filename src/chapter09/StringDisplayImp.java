package chapter09;

public class StringDisplayImp extends DisplayImp {
    private String string;
    private int strLength;

    public StringDisplayImp(String string) {
        this.string = string;
        this.strLength = string.length();
    }

    @Override
    public void rawOpen() {
        printLine();
    }

    @Override
    public void rawPrint() {
        System.out.println("|" + string + "|");
    }

    @Override
    public void rawClose() {
        printLine();
    }

    private void printLine() {
        System.out.print("+");
        for (int i = 0; i < strLength; i++) {
            System.out.print("-");
        }
        System.out.println("+");
    }
}
