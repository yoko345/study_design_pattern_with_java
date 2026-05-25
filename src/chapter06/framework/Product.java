package chapter06.framework;


// public interface Product extends Cloneable {
public abstract class Product implements Cloneable {
    public abstract void use(String str);

    // public abstract Product createCopy();
    public Product createCopy() {
        Product product = null;

        try {
            product = (Product) clone();
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }

        return product;
    };
}
