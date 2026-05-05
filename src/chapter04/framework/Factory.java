package chapter04.framework;

public abstract class Factory {
    private int productNumber;

    protected abstract Product createProduct(String owner, int productNumber);

    protected abstract void registerProduct(Product product);

    public final Product create(String owner) {
        productNumber++;
        Product product = createProduct(owner, productNumber);
        registerProduct(product);

        return product;
    }
}
