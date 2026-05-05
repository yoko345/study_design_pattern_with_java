package chapter04;

import chapter04.framework.Factory;
import chapter04.framework.Product;
import chapter04.idcard.IDCardFactory;

public class MainChapter04 {
    public static void main(String[] args) throws Exception {
        Factory factory = new IDCardFactory();
        Product card1 = factory.create("田中 太郎");
        Product card2 = factory.create("山田 花子");

        card1.use();
        card2.use();
    }
}
