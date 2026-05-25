package chapter06.practice;

import chapter06.practice.framework.Product;

public class UnderlinePen implements Product {
    private char underLineChar;

    public UnderlinePen(char underLineChar) {
        this.underLineChar = underLineChar;
    }

    public UnderlinePen(UnderlinePen prototype) {
        this.underLineChar = prototype.underLineChar;
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

    /* 模範解答 */
    @Override
    public Product createCopy() {
        return new UnderlinePen(this);
    }
}
