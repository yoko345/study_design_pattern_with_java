package chapter21;

public class Printer implements Printable {
    private String name;

    public Printer() {
        heavyJob("Printerのインスタンスを生成中");
    }

    public Printer(String name) {
        this.name = name;
        heavyJob("Printerのインスタンス（" + name + "）を生成中");
    }

    // 名前の設定
    @Override
    public void setPrinterName(String name) {
        this.name = name;
    }

    // 名前の取得
    @Override
    public String getPrinterName() {
        return name;
    }

    // 名前付きで表示
    @Override
    public void print(String string) {
        System.out.println("=== " + name + " ===");
        System.out.println(string);
    }

    private void heavyJob(String msg) {
        System.out.print(msg);

        for (int i = 0; i < 5; i++) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
            }

            System.out.print(".");
        }

        System.out.println("完了");
    }
}
