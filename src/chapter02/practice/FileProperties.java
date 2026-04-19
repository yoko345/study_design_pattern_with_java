package chapter02.practice;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;

public class FileProperties extends Properties implements FileIO {
    private String path;
    private StringBuilder basePath;
    private String comments = "";

    @Override
    public String getValue(String key) {
        return null;
    }

    @Override
    public void readFromFile(String fileName) throws IOException {
        try (FileReader reader = new FileReader(getBasePath().append(fileName).toString())) {
            load(reader);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void setValue(String key, String value) {
        comments += key + "=" + value + "\n";
    }

    @Override
    public void writeToFile(String fileName) throws IOException {
        try (FileWriter writer = new FileWriter(getBasePath().append(fileName).toString())) {
            store(writer, comments);
        } catch (IOException e) {
            e.printStackTrace();
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
