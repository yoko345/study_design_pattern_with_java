package chapter15.pagemaker;

import java.io.IOException;
import java.io.Writer;

class HTMLWriter {
    private Writer writer;

    public HTMLWriter(Writer writer) {
        this.writer = writer;
    }

    // タイトルの出力
    public void title(String title) throws IOException {
        writer.write("<!DOCTYPE html>\n");
        writer.write("<html>");
        writer.write("<head>\n<title>");
        writer.write(title);
        writer.write("</title>\n</head>");
        writer.write("<body>\n");
        writer.write("<h1>");
        writer.write(title);
        writer.write("</h1>\n\n");
    }

    // 段落の出力
    public void paragraph(String msg) throws IOException {
        writer.write("<p>");
        writer.write(msg);
        writer.write("</p>\n\n");
    }

    // リンクの出力
    public void link(String href, String caption) throws IOException {
        paragraph("<a href=\"" + href + "\">" + caption + "</a>");
    }

    // メールアドレスの出力
    public void mailTo(String email, String userName) throws IOException {
        link("mailto: " + email, userName);
    }

    // 閉じる
    public void close() throws IOException {
        writer.write("</body>\n");
        writer.write("</html>\n");
        writer.close();
    }
}
