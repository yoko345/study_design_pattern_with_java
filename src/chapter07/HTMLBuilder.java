package chapter07;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

public class HTMLBuilder extends Builder {
    private String filename = "test.html";
    private StringBuilder sb = new StringBuilder();

    @Override
    public void makeTitle(String title) {
        this.filename = title + ".html";
        sb.append("<!DOCTYPE html>\n");
        sb.append("<html lang=\"ja\">");
        sb.append("<head>\n<title>");
        sb.append(title);
        sb.append("</title>\n</head>");
        sb.append("<body>\n");
        sb.append("<h1>");
        sb.append(title);
        sb.append("</h1>\n\n");
    }

    @Override
    public void makeString(String str) {
        sb.append("<p>■");
        sb.append(str);
        sb.append("</p>\n\n");
    }

    @Override
    public void makeItems(String[] items) {
        sb.append("<ul>");

        for (String item : items) {
            sb.append("<li>");
            sb.append(item);
            sb.append("</li>\n");
        }

        sb.append("</ul>\n");
    }

    @Override
    public void close() {
        sb.append("</body>\n");
        sb.append("</html>\n");

        try (Writer writer = new FileWriter("src/chapter07/" + filename)) {
            writer.write(sb.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getHTMLResult() {
        return filename;
    }
}
