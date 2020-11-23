package eu.mulk.aendggner;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
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
  private Path baseFile;

  @Parameters(arity = "*", description = "The diff relative to the base text.")
  private List<Path> patches;

  public static void main(String... args) {
    int exitCode = new CommandLine(new AendGgner()).execute(args);
    System.exit(exitCode);
  }

  @Override
  public final Integer call() throws TikaException, IOException {
    var tika = new TikaConfig();

    for (var file : patches) {
      var metadata = new Metadata();
      metadata.set(Metadata.RESOURCE_NAME_KEY, file.toString());
      try (var is = TikaInputStream.get(file)) {
        var mimetype = tika.getDetector().detect(
            TikaInputStream.get(file), metadata);
        System.out.printf("File %s is %s.\n", file, mimetype);
      }
    }

    return 0;
  }
}
