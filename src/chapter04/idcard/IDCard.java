package chapter04.idcard;

import chapter04.framework.Product;

public class IDCard extends Product {
    private String owner;
    private int productNumber;

    IDCard(String owner, int productNumber) {
        System.out.println(owner + "のカードを" + productNumber + "番で作ります。");
        this.owner = owner;
        this.productNumber = productNumber;
    }

    @Override
    public void use() {
        System.out.println(this + "を使います。");
    }

    @Override
    public String toString() {
        return "[ IDCard" + productNumber + "：" + owner + " ]";
    }
}
