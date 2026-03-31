package chapter01;

import java.util.Iterator;

public class MainChapter01 {
    public static void main(String[] args) throws Exception {
        FruitBasket fruitBasket = new FruitBasket(3);
        // FruitBasket fruitBasket = new FruitBasket(new ArrayList<Fruit>());
        fruitBasket.appendFruit(new Fruit("りんご", 100));
        fruitBasket.appendFruit(new Fruit("バナナ", 300));
        fruitBasket.appendFruit(new Fruit("いちご", 500));
        // fruitBasket.addFruit(new Fruit("りんご", 100));
        // fruitBasket.addFruit(new Fruit("バナナ", 300));
        // fruitBasket.addFruit(new Fruit("いちご", 500));
        fruitBasket.appendFruit(new Fruit("キュウイ", 150));


        Iterator<Fruit> iterator = fruitBasket.iterator();
        while (iterator.hasNext()) {
            Fruit fruit = iterator.next();
            System.out.println(fruit.getFruitInfo());
        }

        System.out.println();

        for (Fruit fruit : fruitBasket) {
            System.out.println(fruit.getFruitInfo());
        }
    }
}
