package chapter02.practice;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Properties;

public class FileProperties implements FileIO {
    Properties property = new Properties();

    private String path;
    private StringBuilder basePath;

    @Override
    public String getValue(String key) {
        return property.getProperty(key);
    }

    @Override
    public void readFromFile(String fileName) throws IOException {
        try (FileReader reader = new FileReader(getBasePath().append(fileName).toString())) {
            property.load(reader);
        }
    }

    @Override
    public void setValue(String key, String value) {
        property.setProperty(key, value);
    }

    @Override
    public void writeToFile(String fileName) throws IOException {
        try (FileWriter writer = new FileWriter(getBasePath().append(fileName).toString())) {
            property.store(writer, "written by FileProperties");
        }
    }

    private StringBuilder getBasePath() {
        basePath = new StringBuilder();

        path = Paths.get("").toAbsolutePath().toString();
        basePath.append(path);
        basePath.append("/src/chapter02/practice/");

        return basePath;
    }
}
