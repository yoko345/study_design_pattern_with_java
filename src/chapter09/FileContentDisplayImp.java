package chapter09;

import java.io.FileReader;

public class FileContentDisplayImp extends DisplayImp {
    private StringDisplayImp stringDisplayImp;
    private String fileName;
    private StringBuilder sb = new StringBuilder();

    public FileContentDisplayImp(String fileName) {
        this.fileName = fileName;
        stringDisplayImp = new StringDisplayImp(readFileContent());
    }

    @Override
    public void rawOpen() {
        stringDisplayImp.rawOpen();
    }

    @Override
    public void rawPrint() {
        stringDisplayImp.rawPrint();
    }

    @Override
    public void rawClose() {
        stringDisplayImp.rawClose();
    }

    private String readFileContent() {
        try (FileReader reader = new FileReader("./src/chapter09/" + fileName)) {
            int character;
            while ((character = reader.read()) != -1) {
                sb.append((char) character);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return sb.toString();
    }
}
