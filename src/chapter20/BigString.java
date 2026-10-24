package chapter20;

public class BigString {
    // 「大きな文字列」の配列
    private BigChar[] bigChars;

    public BigString(String string) {
        BigFactory bigFactory = BigFactory.getInstance();
        bigChars = new BigChar[string.length()];
        for (int i = 0; i < bigChars.length; i++) {
            // 起動時に入力した文字列を最初から順に取り出して、getBigCharメソッドに渡す
            bigChars[i] = bigFactory.getBigChar(string.charAt(i));
        }
    }

    public void print() {
        for (BigChar bigChar : bigChars) {
            bigChar.print();
        }
    }
}
