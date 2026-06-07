package chapter07.practice;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

public class MarkDownBuilder extends Builder {
    String filename = "test.md";
    StringBuilder sb = new StringBuilder();

    @Override
    public void makeTitle(String title) {
        sb.append("# ");
        sb.append(title);
        sb.append("\n\n");
    }

    @Override
    public void makeString(String str) {
        sb.append("## ");
        sb.append(str);
        sb.append("\n\n");
    }

    @Override
    public void makeItems(String[] items) {
        for (String item : items) {
            sb.append("- ");
            sb.append(item);
            sb.append("\n");
        }

        sb.append("\n");
    }

    @Override
    public void close() {
        try (Writer writer = new FileWriter("./src/chapter07/practice/" + filename)) {
            writer.write(sb.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
