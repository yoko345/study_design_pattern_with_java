package chapter08.divfactory;

import chapter08.factory.Factory;
import chapter08.factory.Link;
import chapter08.factory.Page;
import chapter08.factory.Tray;

public class DivFactory extends Factory {

    @Override
    public Link createLink(String caption, String url) {
        return new DivLink(caption, url);
    }

    @Override
    public Tray createTray(String caption) {
        return new DivTray(caption);
    }

    @Override
    public Page createPage(String title, String author) {
        return new DivPage(title, author);
    }

}
