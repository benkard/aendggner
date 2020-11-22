package eu.mulk.aendggner;

import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command
public class AendGgner implements Callable<Integer> {

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
