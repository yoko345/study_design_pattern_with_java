package chapter08;

import chapter08.factory.Factory;
import chapter08.factory.Link;
import chapter08.factory.Page;
import chapter08.factory.Tray;

public class MainChapter08 {
    public static void main(String[] args) {
        if (args.length != 2) {
            usage();
            System.exit(0);
        }

        String fileName = args[0];
        String className = args[1];

        Factory factory = Factory.getFactory(className);

        // Blog Site の中身の部分
        Link blog1 = factory.createLink("Blog 1", "https://sample.com/blog1");
        Link blog2 = factory.createLink("Blog 2", "https://sample.com/blog2");
        Link blog3 = factory.createLink("Blog 3", "https://sample.com/blog3");

        // Blog Site の作成
        Tray blogTray = factory.createTray("Blog Site");
        blogTray.add(blog1);
        blogTray.add(blog2);
        blogTray.add(blog3);

        // News Site の中身の部分
        Link news1 = factory.createLink("News 1", "https://sample.com/news1");
        Link news2 = factory.createLink("News 2", "https://sample.com/news2");
        Tray news3 = factory.createTray("News 3");
        news3.add(factory.createLink("News 3 (US)", "https://sample.com/news3us"));
        news3.add(factory.createLink("News 3 (JP)", "https://sample.com/news3jp"));

        // News Site の作成
        Tray newsTray = factory.createTray("News Site");
        newsTray.add(news1);
        newsTray.add(news2);
        newsTray.add(news3);

        // ページ全体の作成
        Page page = factory.createPage("Blog and News", "Sample Taro");
        page.add(blogTray);
        page.add(newsTray);

        // ファイルの出力
        page.output(fileName);

        // 練習問題8-2
        Page pageYahoo = factory.createYahooPage();
        pageYahoo.output("yahoo.html");
    }

    private static void usage() {
        System.out.println("Usage: java -cp bin Main filename.html class.name.of.ConcreteFactory");

        System.out.println(
                "Example 1: java -cp bin chapter08.MainChapter08 list.html chapter08.listfactory.ListFactory");
        System.out.println(
                "Example 2: java -cp bin chapter08.MainChapter08 div.html chapter08.divfactory.DivFactory");
    }
}
