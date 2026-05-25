package chapter06;

import chapter06.framework.Product;

// public class MessageBox implements Product {
public class MessageBox extends Product {
    private char decoChar;

    public MessageBox(char decoChar) {
        this.decoChar = decoChar;
    }

    @Override
    public void use(String str) {
        int strLength = 1 + str.length() + 1;

        for (int i = 0; i < strLength; i++) {
            System.out.print(decoChar);
        }

        System.out.println();
        System.out.println(decoChar + str + decoChar);

        for (int i = 0; i < strLength; i++) {
            System.out.print(decoChar);
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
