package chapter06.framework;

import java.util.HashMap;
import java.util.Map;

public class Manager {
    private Map<String, Product> map = new HashMap<>();

    public void register(String keyName, Product prototype) {
        map.put(keyName, prototype);
    }

    public Product create(String prototypeName) {
        Product product = map.get(prototypeName);
        return product.createCopy();
    }
}
