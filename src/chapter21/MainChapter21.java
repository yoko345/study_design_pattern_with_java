package chapter21;

public class MainChapter21 {
    public static void main(String[] args) {
        // 練習問題21-1
        // Printable printable = new PrinterProxy("Alice");
        Printable printable = new PrinterProxy("Alice", "chapter21.Printer");
        System.out.println("名前は現在" + printable.getPrinterName() + "です。");
        printable.setPrinterName("Bob");
        System.out.println("名前は現在" + printable.getPrinterName() + "です。");
        printable.print("Hello World!");
    }
}
