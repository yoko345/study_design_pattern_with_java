package chapter15.pagemaker;

import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

public class Database {
    public static Properties getProperties(String dbname) throws IOException {
        String fileName = "src/chapter15/" + dbname + ".txt";
        Properties prop = new Properties();
        prop.load(new FileReader(fileName));
        return prop;
    }
}
