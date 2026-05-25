package chapter06.practice.framework;

import java.util.HashMap;
import java.util.Map;

public class Manager {
    private Map<String, Product> map = new HashMap<>();

    public void register(String keyName, Product prototype) {
        map.put(keyName, prototype);
    }

    public Product create(String prototypeName) {
        return map.get(prototypeName);
    }
}
