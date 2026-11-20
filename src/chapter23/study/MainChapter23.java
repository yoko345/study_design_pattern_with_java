package chapter23.study;

import java.nio.file.Files;
import java.nio.file.Path;

public class MainChapter23 {
    public static void main(String[] args) {
        try {
            for (String text : Files.readAllLines(Path.of("src/chapter23/study/program.txt"))) {
                System.out.println("text = \"" + text + "\"");
                Node node = new ProgramNode();
                node.parse(new Context(text));
                System.out.println("node = " + node);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
