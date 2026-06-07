package chapter07;

public class MainChapter07 {
    public static void main(String[] args) {
        if (args.length > 1) {
            usage();
            System.exit(0);
        }

        if (args[0].equals("text")) {
            TextBuilder textBuilder = new TextBuilder();
            Director director = new Director(textBuilder);
            director.construct();
            String result = textBuilder.getTextResult();
            System.err.println(result);
        } else if (args[0].equals("html")) {
            HTMLBuilder htmlBuilder = new HTMLBuilder();
            Director director = new Director(htmlBuilder);
            director.construct();
            String filename = htmlBuilder.getHTMLResult();
            System.err.println("HTMLファイル " + filename + " が作成されました。");
        } else {
            usage();
            System.exit(0);
        }
    }

    private static void usage() {
        System.out.println("Usage: java -cp bin chapter07.MainChapter07 text");
        System.out.println("Usage: java -cp bin chapter07.MainChapter07 html");
    }
}
