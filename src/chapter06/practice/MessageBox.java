package chapter06.practice;

import chapter06.practice.framework.Product;

public class MessageBox implements Product {
    private char decoChar;


    public MessageBox(char decoChar) {
        this.decoChar = decoChar;
    }


    public MessageBox(MessageBox prototype) {
        this.decoChar = prototype.decoChar;
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

    /* 模範解答 */
    @Override
    public Product createCopy() {
        return new MessageBox(this);
    }
}
