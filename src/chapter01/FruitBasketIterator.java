package chapter01;

import java.util.Iterator;
import java.util.NoSuchElementException;

public class FruitBasketIterator implements Iterator<Fruit> {

    private FruitBasket fruitBasket;
    private int index;

    public FruitBasketIterator(FruitBasket fruitBasket) {
        this.fruitBasket = fruitBasket;
        this.index = 0;
    }

    @Override
    public boolean hasNext() {
        if (index < fruitBasket.getLength()) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public Fruit next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        Fruit fruit = fruitBasket.getFruitAt(index);
        index++;
        return fruit;
    }
}
