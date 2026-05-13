package chapter05.practice02;


public class Main02 {
    public static void main(String[] args) throws Exception {
        /*
         * 自分の解答（わからなかった）
         */
        // Triple triple1 = Triple.getInstance("ALPHA");
        // System.out.println(triple1.getInstanceName());

        /* 模範解答 */
        System.out.println("開始");
        Triple a1 = Triple.getInstance("ALPHA");
        Triple b1 = Triple.getInstance("BETA");
        Triple c1 = Triple.getInstance("GANMA");
        Triple a2 = Triple.getInstance("ALPHA");
        Triple b2 = Triple.getInstance("BETA");
        Triple c2 = Triple.getInstance("GANMA");

        if (a1 == a2) {
            System.out.println("a1 == a2 (" + a1 + ")");
        } else {
            System.out.println("a1 != a2");
        }
        if (b1 == b2) {
            System.out.println("b1 == b2 (" + b1 + ")");
        } else {
            System.out.println("b1 != b2");
        }
        if (c1 == c2) {
            System.out.println("c1 == c2 (" + c1 + ")");
        } else {
            System.out.println("c1 != c2");
        }
        System.out.println("終了");
    }
}
