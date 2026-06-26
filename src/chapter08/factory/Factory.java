package chapter08.factory;

public abstract class Factory {
    public static Factory getFactory(String className) {
        Factory factory = null;

        try {
            // newInstance() の静的な戻り値の型は Object
            factory = (Factory) Class.forName(className).getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException e) {
            System.out.println("クラス " + className + " が見つかりません。");
        } catch (Exception e) {
            e.printStackTrace();
        }

        return factory;
    }

    public abstract Link createLink(String caption, String url);

    public abstract Tray createTray(String caption);

    public abstract Page createPage(String title, String author);

    // 練習問題8-2
    public Page createYahooPage() {
        Link link = createLink("Yahoo!", "https://www.yahoo.com");
        Page page = createPage("Yahoo!", "Yahoo!");
        page.add(link);
        return page;
    }
}
