package chapter02.practice;

public class MainPracticeChapter02 {
    public static void main(String[] args) {
        FileIO f = new FileProperties();

        try {
            f.readFromFile("file.txt");

            f.setValue("width", "1024");
            f.setValue("height", "512");
            f.setValue("depth", "32");
            f.writeToFile("newfile.txt");

            System.out.println("newfile.txt is created.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
