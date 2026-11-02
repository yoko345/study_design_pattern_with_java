package chapter21;

public class PrinterProxy implements Printable {
    private String name;
    private Printer realPrinter;

    public PrinterProxy() {
        this.name = "No name";
        this.realPrinter = null;
    }

    public PrinterProxy(String name) {
        this.name = name;
        this.realPrinter = null;
    }

    // 名前の設定
    @Override
    public synchronized void setPrinterName(String name) {
        if (realPrinter != null) {
            // 「本人」にも設定する
            realPrinter.setPrinterName(name);
        }

        this.name = name;
    }

    // 名前の取得
    @Override
    public String getPrinterName() {
        return name;
    }

    // 表示
    @Override
    public void print(String string) {
        realize();
        realPrinter.print(string);
    }

    // 「本人」を生成
    private synchronized void realize() {
        if (realPrinter == null) {
            realPrinter = new Printer(name);
        }
    }
}
