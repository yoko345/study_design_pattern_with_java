package chapter06.practice.framework;


public interface Product {
    public abstract void use(String str);

    /* 模範解答 */
    public abstract Product createCopy();
}
