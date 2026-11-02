package chapter21;

public class MainChapter21 {
    public static void main(String[] args) {
        Printable printable = new PrinterProxy("Alice");
        System.out.println("名前は現在" + printable.getPrinterName() + "です。");
        printable.setPrinterName("Bob");
        System.out.println("名前は現在" + printable.getPrinterName() + "です。");
        printable.print("Hello World!");
    }
}
