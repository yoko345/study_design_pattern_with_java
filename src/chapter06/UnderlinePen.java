package chapter06;

import chapter06.framework.Product;

// public class UnderlinePen implements Product {
public class UnderlinePen extends Product {
    private char underLineChar;

    public UnderlinePen(char underLineChar) {
        this.underLineChar = underLineChar;
    }

    @Override
    public void use(String str) {
        int strLength = str.length();

        System.out.println(str);

        for (int i = 0; i < strLength; i++) {
            System.out.print(underLineChar);
        }

        System.out.println();
    }

    // @Override
    // public Product createCopy() {
    // Product product = null;

    // try {
    // product = (Product) clone();
    // } catch (CloneNotSupportedException e) {
    // e.printStackTrace();
    // }

    // return product;
    // }
}
