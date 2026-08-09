package eu.mulk.aendggner.web;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Minimaler {@code multipart/form-data}-Parser für Datei- und Textfelder — bewusst ohne externe
 * Abhängigkeit (siehe Web-App-Plan: nur JDK-Bordmittel).
 */
final class Multipart {

  record Part(String name, String filename, byte[] data) {}

  private static final Pattern BOUNDARY_PATTERN = Pattern.compile("boundary=\"?([^\";]+)\"?");
  private static final Pattern NAME_PATTERN = Pattern.compile("name=\"([^\"]*)\"");
  private static final Pattern FILENAME_PATTERN = Pattern.compile("filename=\"([^\"]*)\"");
  private static final byte[] HEADER_BODY_SEPARATOR = "\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1);

  private Multipart() {}

  static String extraktBoundary(String contentType) {
    if (contentType == null) {
      return null;
    }
    var matcher = BOUNDARY_PATTERN.matcher(contentType);
    return matcher.find() ? matcher.group(1) : null;
  }

  static List<Part> parse(byte[] body, String boundary) {
    var delimiter = ("--" + boundary).getBytes(StandardCharsets.ISO_8859_1);

    var positions = new ArrayList<Integer>();
    for (int i = indexOf(body, delimiter, 0); i >= 0; i = indexOf(body, delimiter, i + delimiter.length)) {
      positions.add(i);
    }

    var parts = new ArrayList<Part>();
    for (int i = 0; i < positions.size() - 1; i++) {
      int start = positions.get(i) + delimiter.length;
      int end = positions.get(i + 1);

      if (start + 1 < end && body[start] == '\r' && body[start + 1] == '\n') {
        start += 2;
      }
      int contentEnd = end;
      if (contentEnd >= start + 2 && body[contentEnd - 2] == '\r' && body[contentEnd - 1] == '\n') {
        contentEnd -= 2;
      }

      int headerEnd = indexOf(body, HEADER_BODY_SEPARATOR, start);
      if (headerEnd < 0 || headerEnd > contentEnd) {
        continue;
      }
      var headerText = new String(body, start, headerEnd - start, StandardCharsets.UTF_8);
      var dataStart = headerEnd + HEADER_BODY_SEPARATOR.length;

      var nameMatcher = NAME_PATTERN.matcher(headerText);
      if (!nameMatcher.find()) {
        continue;
      }

      String filename = null;
      var filenameMatcher = FILENAME_PATTERN.matcher(headerText);
      if (filenameMatcher.find()) {
        filename = filenameMatcher.group(1);
      }

      var data = Arrays.copyOfRange(body, dataStart, Math.max(dataStart, contentEnd));
      parts.add(new Part(nameMatcher.group(1), filename, data));
    }

    return parts;
  }

  private static int indexOf(byte[] haystack, byte[] needle, int fromIndex) {
    outer:
    for (int i = fromIndex; i <= haystack.length - needle.length; i++) {
      for (int j = 0; j < needle.length; j++) {
        if (haystack[i + j] != needle[j]) {
          continue outer;
        }
      }
      return i;
    }
    return -1;
  }
}
