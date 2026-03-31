package chapter01;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;



public class FruitBasket implements Iterable<Fruit> {
    // private Fruit[] fruits;
    // private ArrayList<Fruit> fruits;
    private List<Fruit> fruits;
    // private int lastIndex = 0;

    // public FruitBasket(int maxNumber) {
    // this.fruits = new Fruit[maxNumber];
    // }
    // public FruitBasket(ArrayList<Fruit> fruits) {
    // this.fruits = fruits;
    // }
    public FruitBasket(int initialsize) {
        this.fruits = new ArrayList<>(initialsize);
    }


    public Fruit getFruitAt(int index) {
        // return fruits[index];
        return fruits.get(index);
    }


    // public void appendFruit(Fruit fruit) {
    // this.fruits[lastIndex] = fruit;
    // lastIndex++;
    // }
    public void appendFruit(Fruit fruit) {
        // this.fruits.add(fruit);
        fruits.add(fruit);
    }

    public int getLength() {
        // return lastIndex;
        return this.fruits.size();
    }

    @Override
    public Iterator<Fruit> iterator() {
        return new FruitBasketIterator(this);
    }
}
