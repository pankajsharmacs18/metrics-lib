/* Copyright 2012--2015 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.descriptor;

import java.util.Set;

/* Exit list containing all known exit scan results at a given time. */
public interface ExitList extends Descriptor {

  /* Return the download time of the exit list. */
  public long getDownloadedMillis();

  /* Return the unordered set of exit scan results. */
  public Set<ExitListEntry> getExitListEntries();
}

