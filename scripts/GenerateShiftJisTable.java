/*
 * Copyright 2026 Mukesh Solanki
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates the Kotlin Shift_JIS lookup table from the JDK's Shift_JIS charset.
 * Run from the repository root with {@code java scripts/GenerateShiftJisTable.java}.
 */
public final class GenerateShiftJisTable {

  /** Destination of the generated Kotlin file, relative to the repository root. */
  private static final Path OUTPUT = Paths.get(
      "barcodes-core/src/commonMain/kotlin/com/mukeshsolanki/barcodes/internal/charset/generated/ShiftJisTable.kt");

  /** Maximum number of chars per string literal; 3 UTF-8 bytes each keeps a chunk under 65535 bytes. */
  private static final int CHUNK_CHARS = 16000;

  /** JDK Shift_JIS charset used as the source of truth. */
  private static final Charset SJIS = Charset.forName("Shift_JIS");

  /** Not instantiable. */
  private GenerateShiftJisTable() {
  }

  /** Entry point: builds the round-trip and encode-only tables and writes the Kotlin file. */
  public static void main(String[] args) throws IOException {
    StringBuilder codes = new StringBuilder();
    StringBuilder chars = new StringBuilder();
    boolean[] roundTripChars = new boolean[0x10000];
    for (int b = 0x00; b <= 0xFF; b++) {
      record(new byte[] {(byte) b}, b, codes, chars, roundTripChars);
    }
    for (int lead = 0x81; lead <= 0xFC; lead++) {
      if (lead > 0x9F && lead < 0xE0) {
        continue;
      }
      for (int trail = 0x40; trail <= 0xFC; trail++) {
        record(new byte[] {(byte) lead, (byte) trail}, (lead << 8) | trail, codes, chars, roundTripChars);
      }
    }
    StringBuilder encodeChars = new StringBuilder();
    StringBuilder encodeCodes = new StringBuilder();
    collectEncodeOnly(roundTripChars, encodeChars, encodeCodes);
    Files.createDirectories(OUTPUT.getParent());
    Files.writeString(OUTPUT, render(codes, chars, encodeChars, encodeCodes));
    System.out.println("Wrote " + OUTPUT + ": " + codes.length() + " round-trip entries, "
        + encodeChars.length() + " encode-only entries, " + Files.size(OUTPUT) + " bytes");
  }

  /** Records {@code bytes} when it decodes to one non-replacement BMP char that encodes back to the same bytes. */
  private static void record(byte[] bytes, int code, StringBuilder codes, StringBuilder chars, boolean[] seen) {
    String decoded = decodeStrict(bytes);
    if (decoded == null || decoded.length() != 1) {
      return;
    }
    char c = decoded.charAt(0);
    if (c == '�' || Character.isSurrogate(c) || seen[c]) {
      return;
    }
    byte[] encoded = encodeStrict(decoded);
    if (encoded == null || !java.util.Arrays.equals(encoded, bytes)) {
      return;
    }
    seen[c] = true;
    codes.append((char) code);
    chars.append(c);
  }

  /** Collects chars the JDK encodes (via getBytes) but that are not in the round-trip table. */
  private static void collectEncodeOnly(boolean[] roundTripChars, StringBuilder chars, StringBuilder codes) {
    for (int c = 0; c < 0x10000; c++) {
      if (roundTripChars[c] || Character.isSurrogate((char) c)) {
        continue;
      }
      byte[] encoded = encodeStrict(String.valueOf((char) c));
      if (encoded == null || encoded.length == 0 || encoded.length > 2) {
        continue;
      }
      int code = encoded.length == 1 ? encoded[0] & 0xFF : ((encoded[0] & 0xFF) << 8) | (encoded[1] & 0xFF);
      chars.append((char) c);
      codes.append((char) code);
    }
  }

  /** Decodes with errors reported, returning null for malformed or unmappable input. */
  private static String decodeStrict(byte[] bytes) {
    CharsetDecoder decoder = SJIS.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT);
    try {
      return decoder.decode(ByteBuffer.wrap(bytes)).toString();
    } catch (CharacterCodingException e) {
      return null;
    }
  }

  /** Encodes with errors reported, returning null for unmappable input. */
  private static byte[] encodeStrict(String s) {
    CharsetEncoder encoder = SJIS.newEncoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT);
    try {
      ByteBuffer buffer = encoder.encode(CharBuffer.wrap(s));
      byte[] out = new byte[buffer.remaining()];
      buffer.get(out);
      return out;
    } catch (CharacterCodingException e) {
      return null;
    }
  }

  /** Renders the Kotlin source for the four parallel tables. */
  private static String render(CharSequence codes, CharSequence chars, CharSequence encodeChars,
                               CharSequence encodeCodes) {
    StringBuilder out = new StringBuilder();
    out.append("/*\n")
        .append(" * GENERATED FILE. DO NOT EDIT.\n")
        .append(" * Generated by scripts/GenerateShiftJisTable.java from the JDK Shift_JIS charset (")
        .append(System.getProperty("java.version")).append(").\n")
        .append(" */\n")
        .append("package com.mukeshsolanki.barcodes.internal.charset.generated\n\n")
        .append("/** Shift_JIS mapping tables generated from the JDK; codes above 0xFF are two-byte sequences. */\n")
        .append("internal object ShiftJisTable {\n");
    appendTable(out, "CODES", "Shift_JIS codes that round-trip, parallel to [CHARS].", codes);
    appendTable(out, "CHARS", "Unicode chars for each entry of [CODES].", chars);
    appendTable(out, "ENCODE_ONLY_CHARS",
        "Unicode chars the JDK encodes but never decodes to, parallel to [ENCODE_ONLY_CODES].", encodeChars);
    appendTable(out, "ENCODE_ONLY_CODES", "Shift_JIS codes for each entry of [ENCODE_ONLY_CHARS].", encodeCodes);
    out.append("}\n");
    return out.toString();
  }

  /** Appends chunked private literals plus a joined public property named {@code name}. */
  private static void appendTable(StringBuilder out, String name, String doc, CharSequence data) {
    List<String> parts = new ArrayList<>();
    for (int start = 0, index = 0; start < data.length(); start += CHUNK_CHARS, index++) {
      String part = name + "_" + index;
      parts.add(part);
      out.append("\n    /** Chunk ").append(index).append(" of [").append(name).append("]. */\n")
          .append("    private const val ").append(part).append(" = \"");
      int end = Math.min(data.length(), start + CHUNK_CHARS);
      for (int i = start; i < end; i++) {
        out.append(String.format("\\u%04X", (int) data.charAt(i)));
      }
      out.append("\"\n");
    }
    out.append("\n    /** ").append(doc).append(" */\n")
        .append("    val ").append(name).append(": String = listOf(")
        .append(String.join(", ", parts)).append(").joinToString(\"\")\n");
  }
}
