package chapter01;

import java.util.Iterator;

public class FruitBasket implements Iterable<Fruit> {
    private Fruit[] fruits;
    private int lastIndex = 0;

    public FruitBasket(int maxNumber) {
        this.fruits = new Fruit[maxNumber];
    }

    public Fruit getFruitAt(int index) {
        return fruits[index];
    }

    public void appendFruit(Fruit fruit) {
        this.fruits[lastIndex] = fruit;
        lastIndex++;
    }

    public int getLength() {
        return lastIndex;
    }

    @Override
    public Iterator<Fruit> iterator() {
        return new FruitBasketIterator(this);
    }
}
