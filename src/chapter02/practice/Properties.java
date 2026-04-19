package chapter02.practice;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Date;

public class Properties {
    private String OPENING_TEXT = "#written by FileProperties\n";
    private int charrcter;

    public void load(Reader reader) throws IOException {
        while ((charrcter = reader.read()) != -1) {
            System.out.print((char) charrcter);
        }
        System.out.println();
    };

    public void store(Writer writer, String comments) throws IOException {
        writer.write(OPENING_TEXT);
        writer.write("#" + new Date());
        writer.write("\n");
        writer.write(comments);

        writer.flush();
    };
}


