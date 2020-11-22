package eu.mulk.aendggner;

import java.io.File;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(
    name = "ÄndGgner",
    mixinStandardHelpOptions = true,
    version = "ÄndGgner 0.1",
    description = "Displays German amendment acts in a user-friendly, consolidated way.")
public class AendGgner implements Callable<Integer> {

  @Parameters(index = "0", description = "The base text to modify.")
  private File baseFile;

  @Parameters(index = "1", description = "The diff relative to the base text.")
  private File diffFile;

  public static void main(String... args) {
    int exitCode = new CommandLine(new AendGgner()).execute(args);
    System.exit(exitCode);
  }

  @Override
  public final Integer call() {
    System.out.println("Hi.");
    return 0;
  }
}
