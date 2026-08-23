// SPDX-FileCopyrightText: 2020 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner.wasm;

import com.jcraft.jzlib.JZlib;
import com.oracle.svm.core.annotate.Inject;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import java.util.zip.DataFormatException;

/**
 * Ersetzt java.util.zip.Inflater durch die reine Java-Umsetzung von jzlib.
 *
 * <p>Web Image kennt die nativen zlib-Bindungen des JDK nicht (GR-65205); ohne Inflate ist kein PDF
 * lesbar, denn nahezu jeder Inhaltsstrom ist FlateDecode-komprimiert.
 */
@TargetClass(java.util.zip.Inflater.class)
final class Target_java_util_zip_Inflater {

  @Inject
  @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)
  com.jcraft.jzlib.Inflater impl;

  @Inject
  @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)
  boolean nowrap;

  @Inject
  @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)
  boolean fertig;

  @Inject
  @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)
  boolean braucheWoerterbuch;

  @Substitute
  Target_java_util_zip_Inflater(boolean nowrap) {
    this.nowrap = nowrap;
    this.impl = new com.jcraft.jzlib.Inflater();
    this.impl.init(nowrap);
  }

  @Substitute
  Target_java_util_zip_Inflater() {
    this(false);
  }

  @Substitute
  public void setInput(byte[] input, int off, int len) {
    impl.next_in = input;
    impl.next_in_index = off;
    impl.avail_in = len;
  }

  @Substitute
  public void setInput(byte[] input) {
    setInput(input, 0, input.length);
  }

  @Substitute
  public int inflate(byte[] output, int off, int len) throws DataFormatException {
    impl.next_out = output;
    impl.next_out_index = off;
    impl.avail_out = len;
    int err = impl.inflate(JZlib.Z_NO_FLUSH);
    int erzeugt = len - impl.avail_out;
    switch (err) {
      case JZlib.Z_STREAM_END:
        fertig = true;
        return erzeugt;
      case JZlib.Z_NEED_DICT:
        braucheWoerterbuch = true;
        return erzeugt;
      case JZlib.Z_OK:
      case JZlib.Z_BUF_ERROR:
        return erzeugt;
      default:
        throw new DataFormatException(impl.msg == null ? "Inflate-Fehler " + err : impl.msg);
    }
  }

  @Substitute
  public int inflate(byte[] output) throws DataFormatException {
    return inflate(output, 0, output.length);
  }

  @Substitute
  public boolean needsInput() {
    return impl.avail_in <= 0;
  }

  @Substitute
  public boolean needsDictionary() {
    return braucheWoerterbuch;
  }

  @Substitute
  public boolean finished() {
    return fertig;
  }

  @Substitute
  public int getRemaining() {
    return Math.max(impl.avail_in, 0);
  }

  @Substitute
  public long getBytesRead() {
    return impl.total_in;
  }

  @Substitute
  public long getBytesWritten() {
    return impl.total_out;
  }

  @Substitute
  public int getTotalIn() {
    return (int) impl.total_in;
  }

  @Substitute
  public int getTotalOut() {
    return (int) impl.total_out;
  }

  @Substitute
  public void setDictionary(byte[] dictionary, int off, int len) {
    var kopie = new byte[len];
    System.arraycopy(dictionary, off, kopie, 0, len);
    impl.setDictionary(kopie, len);
    braucheWoerterbuch = false;
  }

  @Substitute
  public void setDictionary(byte[] dictionary) {
    setDictionary(dictionary, 0, dictionary.length);
  }

  @Substitute
  public void reset() {
    impl.init(nowrap);
    fertig = false;
    braucheWoerterbuch = false;
  }

  @Substitute
  public void end() {
    impl.end();
  }
}
