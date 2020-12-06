package eu.mulk.aendggner;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.logging.LogManager;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParsingReader;
import org.jboss.logging.Logger;
import org.xml.sax.SAXException;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(
    name = "ÄndGgner",
    mixinStandardHelpOptions = true,
    version = "ÄndGgner 0.1",
    description = "Displays German amendment acts in a user-friendly, consolidated way.")
public class AendGgner implements Callable<Integer> {

  private static final Logger log = Logger.getLogger(AendGgner.class);

  @Parameters(index = "0", description = "The base text to modify.")
  private Path baseFile;

  @Parameters(arity = "*", description = "The diff relative to the base text.")
  private List<Path> patches;

  public static void main(String... args) {
    int exitCode = new CommandLine(new AendGgner()).execute(args);
    System.exit(exitCode);
  }

  @Override
  public final Integer call() throws TikaException, IOException, SAXException {
    setupLogging();

    log.debugf("Logging configured.");

    TikaConfig tika;
    try (var configResource =
        this.getClass().getResourceAsStream("/eu/mulk/aendggner/tika-config.xml")) {
      tika = new TikaConfig(configResource);
    }

    for (var file : patches) {
      var metadata = new Metadata();
      metadata.set(Metadata.RESOURCE_NAME_KEY, file.getFileName().toString());

      try (var is = TikaInputStream.get(file)) {
        var mimetype = tika.getDetector().detect(TikaInputStream.get(file), metadata);
        log.infof("File %s is %s.", file, mimetype);
      }

      var parser = new AutoDetectParser(tika);
      try (var in = Files.newInputStream(file);
          var reader =
              new BufferedReader(
                  new ParsingReader(parser, in, metadata, makeParseContext(parser)))) {
        log.infof("%s: %d lines of text.", file, reader.lines().count());
        // reader.lines().forEachOrdered(x -> log.infof("%s: %s", file, x));
      }
    }

    return 0;
  }

  private static ParseContext makeParseContext(Parser parser) {
    var parseContext = new ParseContext();
    parseContext.set(Parser.class, parser);
    return parseContext;
  }

  private static void setupLogging() throws IOException {
    try (var loggingProperties =
        AendGgner.class.getResourceAsStream("/eu/mulk/aendggner/logging.properties")) {
      LogManager.getLogManager().readConfiguration(loggingProperties);
    }
  }
}
