import bsh.Parser;
import java.io.*;
public class ParseBeanShell {
  public static void main(String[] paths) throws Exception {
    for (String path : paths) {
      try (FileReader reader = new FileReader(path)) {
        Parser parser = new Parser(reader);
        while (!parser.Line()) {}
        System.out.println("PARSE OK " + new File(path).getName());
      }
    }
  }
}
