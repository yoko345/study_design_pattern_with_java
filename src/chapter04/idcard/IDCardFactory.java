package chapter04.idcard;

import chapter04.framework.Factory;
import chapter04.framework.Product;

public class IDCardFactory extends Factory {
    private int productNumber;

    @Override
    protected Product createProduct(String owner) {
        return new IDCard(owner, ++productNumber);
    }

    @Override
    protected void registerProduct(Product product) {
        System.out.println(product + "を登録しました。");
    }

}
