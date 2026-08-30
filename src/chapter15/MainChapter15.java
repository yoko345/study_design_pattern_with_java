package chapter15;

import chapter15.pagemaker.PageMaker;

public class MainChapter15 {
    public static void main(String[] args) {
        PageMaker.makeWelcomPage("hyuki@example.com", "welcome.html");
        PageMaker.makeWelcomPage("tomura@example.com", "hello.html");

        // 練習問題15-2
        PageMaker.makeLinkPage("linkpage.html");

        // 練習問題15-3
        // テキストブロックを使用した学習
        String html = """
                <!DOCTYPE html>
                <html>
                    <head>
                        <title>Welcome!</title>
                    </head>
                    <body>
                        <h1 style="text-align: center">Hello World!</h1>
                    </body>
                </html>
                """;
        System.out.println(html);

        String title = "Welcome!!!";
        String message = "Hello World!!!";
        String html1 = """
                <!DOCTYPE html>
                <html>
                    <head>
                        <title>%s</title>
                    </head>
                    <body>
                        <h1 style="text-align: center">%s</h1>
                    </body>
                </html>
                """.formatted(title, message);
        System.out.println(html1);
    }
}
