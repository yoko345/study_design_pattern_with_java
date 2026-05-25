package chapter06;

import chapter06.framework.Manager;
import chapter06.framework.Product;

public class MainChapter06 {
    public static void main(String[] args) throws Exception {
        Manager manager = new Manager();
        UnderlinePen upen = new UnderlinePen('-');
        MessageBox mbox = new MessageBox('*');
        MessageBox sbox = new MessageBox('/');

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
