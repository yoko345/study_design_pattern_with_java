package chapter20;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class BigChar {
    private static final String DATA_DIR = "src/chapter20/data/";
    // 文字の名前
    private char charName;
    // 大きな文字を表現する文字列（dataディレクトリに格納されている文字）
    private String fontData;

    public BigChar(char charName) {
        this.charName = charName;

        try {
            String fileName = DATA_DIR + "big" + charName + ".txt";
            StringBuilder sb = new StringBuilder();

            for (String line : Files.readAllLines(Path.of(fileName))) {
                sb.append(line);
                sb.append("\n");
            }

            this.fontData = sb.toString();
        } catch (IOException e) {
            this.fontData = charName + "?";
        }
    }

    public void print() {
        System.out.println(fontData);
    }
}
