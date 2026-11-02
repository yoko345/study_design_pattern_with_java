package chapter21;

public class PrinterProxy implements Printable {
    private String name;
    // 練習問題21-1
    // private Printer realPrinter;
    private Printable realPrinter;
    private String className;

    public PrinterProxy() {
        this.name = "No name";
        this.realPrinter = null;
    }

    public PrinterProxy(String name) {
        this.name = name;
        this.realPrinter = null;
    }

    // 練習問題21-1
    public PrinterProxy(String name, String className) {
        this.name = name;
        this.realPrinter = null;
        this.className = className;
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
            // 練習問題21-1
            // realPrinter = new Printer(name);
            try {
                realPrinter = (Printable) Class.forName(className).getDeclaredConstructor().newInstance();
                realPrinter.setPrinterName(name);
            } catch (ClassNotFoundException e) {
                System.out.println("クラス " + className + " が見つかりません。");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
