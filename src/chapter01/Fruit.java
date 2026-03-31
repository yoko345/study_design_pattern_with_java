package chapter01;

public class Fruit {
    private String name;
    private int price;

    public Fruit(String name, int price) {
        this.name = name;
        this.price = price;
    }

    public String getFruitInfo() {
        return "名前：" + name + ", 価格：" + price;
    }
}
