import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class MiniMars {
    public static void main(String[] args) {
        for (String arg : args) {
            if (Files.exists(Paths.get(arg))) {
                System.out.println(arg);
                File f = new File("output/" + arg + ".asm");
                try {
                    if(f.createNewFile()) {
                        System.out.println("Created: " + arg + ".asm");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}