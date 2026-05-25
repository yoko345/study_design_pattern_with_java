package chapter06.practice;

import chapter06.practice.framework.Manager;
import chapter06.practice.framework.Product;

public class Main06 {
    public static void main(String[] args) throws Exception {
        Manager manager = new Manager();
        UnderlinePen upen = new UnderlinePen('-');
        MessageBox mbox = new MessageBox('*');
        MessageBox sbox = new MessageBox('/');

        /* 自分の解答 */
        // manager.register("strong message", new UnderlinePen(upen));
        // manager.register("warning box", new MessageBox(mbox));
        // manager.register("slash box", new MessageBox(sbox));

        /* 模範解答 */
        manager.register("strong message", upen);
        manager.register("warning box", mbox);
        manager.register("slash box", sbox);

        Product productStrong = manager.create("strong message");
        productStrong.use("Hello, World!");

        Product productWarning = manager.create("warning box");
        productWarning.use("Hello, World!");

        Product productSlash = manager.create("slash box");
        productSlash.use("Hello, World!");

    }
}
