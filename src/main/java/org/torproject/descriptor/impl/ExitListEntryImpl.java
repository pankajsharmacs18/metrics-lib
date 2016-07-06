/* Copyright 2012--2016 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.descriptor.impl;

import org.torproject.descriptor.DescriptorParseException;
import org.torproject.descriptor.ExitList;
import org.torproject.descriptor.ExitListEntry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.SortedSet;
import java.util.TreeSet;

public class ExitListEntryImpl implements ExitListEntry, ExitList.Entry {

  private byte[] exitListEntryBytes;

  private boolean failUnrecognizedDescriptorLines;

  private List<String> unrecognizedLines;

  protected List<String> getAndClearUnrecognizedLines() {
    List<String> lines = this.unrecognizedLines;
    this.unrecognizedLines = null;
    return lines;
  }

  @Deprecated
  private ExitListEntryImpl(String fingerprint, long publishedMillis,
      long lastStatusMillis, String exitAddress, long scanMillis) {
    this.fingerprint = fingerprint;
    this.publishedMillis = publishedMillis;
    this.lastStatusMillis = lastStatusMillis;
    this.exitAddresses.put(exitAddress, scanMillis);
  }

  @Deprecated
  List<ExitListEntry> oldEntries() {
    List<ExitListEntry> result = new ArrayList<>();
    if (this.exitAddresses.size() > 1) {
      for (Map.Entry<String, Long> entry
          : this.exitAddresses.entrySet()) {
        result.add(new ExitListEntryImpl(this.fingerprint,
            this.publishedMillis, this.lastStatusMillis, entry.getKey(),
            entry.getValue()));
      }
    } else {
      result.add(this);
    }
    return result;
  }

  protected ExitListEntryImpl(byte[] exitListEntryBytes,
      boolean failUnrecognizedDescriptorLines)
      throws DescriptorParseException {
    this.exitListEntryBytes = exitListEntryBytes;
    this.failUnrecognizedDescriptorLines =
        failUnrecognizedDescriptorLines;
    this.initializeKeywords();
    this.parseExitListEntryBytes();
    this.checkAndClearKeywords();
  }

  private SortedSet<String> keywordCountingSet;

  private void initializeKeywords() {
    this.keywordCountingSet = new TreeSet<>();
    this.keywordCountingSet.add("ExitNode");
    this.keywordCountingSet.add("Published");
    this.keywordCountingSet.add("LastStatus");
    this.keywordCountingSet.add("ExitAddress");
  }

  private void parsedExactlyOnceKeyword(String keyword)
      throws DescriptorParseException {
    if (!this.keywordCountingSet.contains(keyword)) {
      throw new DescriptorParseException("Duplicate '" + keyword
          + "' line in exit list entry.");
    }
    this.keywordCountingSet.remove(keyword);
  }

  private void checkAndClearKeywords() throws DescriptorParseException {
    for (String missingKeyword : this.keywordCountingSet) {
      throw new DescriptorParseException("Missing '" + missingKeyword
          + "' line in exit list entry.");
    }
    this.keywordCountingSet = null;
  }

  private void parseExitListEntryBytes()
      throws DescriptorParseException {
    Scanner s = new Scanner(new String(this.exitListEntryBytes))
        .useDelimiter(ExitList.EOL);
    while (s.hasNext()) {
      String line = s.next();
      String[] parts = line.split(" ");
      String keyword = parts[0];
      switch (keyword) {
        case "ExitNode":
          this.parseExitNodeLine(line, parts);
          break;
        case "Published":
          this.parsePublishedLine(line, parts);
          break;
        case "LastStatus":
          this.parseLastStatusLine(line, parts);
          break;
        case "ExitAddress":
          this.parseExitAddressLine(line, parts);
          break;
        default:
          if (this.failUnrecognizedDescriptorLines) {
            throw new DescriptorParseException("Unrecognized line '"
                + line + "' in exit list entry.");
          } else {
            if (this.unrecognizedLines == null) {
              this.unrecognizedLines = new ArrayList<>();
            }
            this.unrecognizedLines.add(line);
          }
      }
    }
  }

  private void parseExitNodeLine(String line, String[] parts)
      throws DescriptorParseException {
    if (parts.length != 2) {
      throw new DescriptorParseException("Invalid line '" + line + "' in "
          + "exit list entry.");
    }
    this.parsedExactlyOnceKeyword(parts[0]);
    this.fingerprint = ParseHelper.parseTwentyByteHexString(line,
        parts[1]);
  }

  private void parsePublishedLine(String line, String[] parts)
      throws DescriptorParseException {
    if (parts.length != 3) {
      throw new DescriptorParseException("Invalid line '" + line + "' in "
          + "exit list entry.");
    }
    this.parsedExactlyOnceKeyword(parts[0]);
    this.publishedMillis = ParseHelper.parseTimestampAtIndex(line, parts,
        1, 2);
  }

  private void parseLastStatusLine(String line, String[] parts)
      throws DescriptorParseException {
    if (parts.length != 3) {
      throw new DescriptorParseException("Invalid line '" + line + "' in "
          + "exit list entry.");
    }
    this.parsedExactlyOnceKeyword(parts[0]);
    this.lastStatusMillis = ParseHelper.parseTimestampAtIndex(line, parts,
        1, 2);
  }

  private void parseExitAddressLine(String line, String[] parts)
      throws DescriptorParseException {
    if (parts.length != 4) {
      throw new DescriptorParseException("Invalid line '" + line + "' in "
          + "exit list entry.");
    }
    this.keywordCountingSet.remove(parts[0]);
    this.exitAddresses.put(ParseHelper.parseIpv4Address(line, parts[1]),
        ParseHelper.parseTimestampAtIndex(line, parts, 2, 3));
  }

  private String fingerprint;

  @Override
  public String getFingerprint() {
    return this.fingerprint;
  }

  private long publishedMillis;

  @Override
  public long getPublishedMillis() {
    return this.publishedMillis;
  }

  private long lastStatusMillis;

  @Override
  public long getLastStatusMillis() {
    return this.lastStatusMillis;
  }

  private String exitAddress;

  @Override
  public String getExitAddress() {
    if (null == exitAddress) {
      Map.Entry<String, Long> randomEntry =
          this.exitAddresses.entrySet().iterator().next();
      this.exitAddress = randomEntry.getKey();
      this.scanMillis = randomEntry.getValue();
    }
    return this.exitAddress;
  }

  private Map<String, Long> exitAddresses = new HashMap<>();

  @Override
  public Map<String, Long> getExitAddresses() {
    return new HashMap<>(this.exitAddresses);
  }

  private long scanMillis;

  @Override
  public long getScanMillis() {
    if (null == exitAddress) {
      getExitAddress();
    }
    return scanMillis;
  }
}
