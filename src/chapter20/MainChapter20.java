package chapter20;

public class MainChapter20 {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: java -cp bin chapter20.MainChapter20 digits");
            System.out.println("Example: java -cp bin chapter20.MainChapter20 1234509876");
            System.exit(0);
        }

        // BigString bigString = new BigString(args[0]);
        // bigString.print();

        // 練習問題20-1,2
        // 【問題の本質】
        // ・大きな文字をいったん格納しておいて、後で使用したい。そのために、ループでは BigString を配列に格納しておく必要がある。
        // ・このとき、BigChar を使い回すときと使い回さないときで、メモリの使用量を比較する。
        // 【結果の理由】
        // ・true（少ない）：10,000 個の BigString が、pool にある 1〜3 の BigChar 3 個を共有して指している。
        //   ファイルから読み込んだ大きな文字のデータは 3 個分だけ。ファイルを読むのも文字の種類ごとに 1 回だけで済むため、表示も速い。
        // ・false（多い）：BigString 1 個ごとに自分専用の BigChar を 3 個持つため、10,000 個 × 3 文字 = 30,000 個分の大きな文字のデータがある。
        // 【まとめ】
        // ・メリット：同じものを大量に、同時に使う場面で、インスタンスの数とメモリを大きく減らせる。
        // ・デメリット：BigString を使い終わって捨てても、pool が BigChar を持ち続けるので、GC で回収されない。
        //   1〜3 が二度と使われない場合でも、プログラムが終わるまで残り続ける。
        //   共有するものの種類が多い場合（全漢字、大きな画像など）は、pool が増える一方になる。
        BigString[] bigStrings = new BigString[10000];
        for (int i = 0; i < bigStrings.length; i++) {
            bigStrings[i] = new BigString(args[0], true);
        }
        showMemory("1");
        bigStrings[0].print();
        // 123の場合
        // 使用メモリ1 = 1,848,008
        // 表示スピードに関して、こちらのほうが圧倒的に早い

        for (int i = 0; i < bigStrings.length; i++) {
            bigStrings[i] = new BigString(args[0], false);
        }
        showMemory("2");
        bigStrings[0].print();
        // 123の場合
        // 使用メモリ2 = 7,871,568
    }

    private static void showMemory(String numString) {
        Runtime.getRuntime().gc();
        long used = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        System.out.println("使用メモリ" + numString + " = " + used);
    }
}
