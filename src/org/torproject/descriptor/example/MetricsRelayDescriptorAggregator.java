/* Copyright 2011 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.descriptor.example;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import org.torproject.descriptor.DescriptorFile;
import org.torproject.descriptor.DescriptorSourceFactory;
import org.torproject.descriptor.DescriptorStore;
import org.torproject.descriptor.RelayDescriptorDownloader;
import org.torproject.descriptor.RelayDescriptorReader;

/* This is a non-functional (though syntactically correct) example for how
 * metrics-db could use the DescripTor API to read relay descriptors from
 * two sources and download only missing descriptors from the directory
 * authorities.  metrics-db does more than aggregating relay descriptors,
 * but the other functions (sanitizing bridge descriptors, downloading
 * GetTor statistics) are too specific to metrics-db to add them to the
 * DescripTor API.  This class will go away once a real metrics-db uses
 * this API. */
public class MetricsRelayDescriptorAggregator {
  public static void main(String[] args) {

    /* Start by reading lists of previously processed descriptors from
     * disk.  We'll want to exclude these descriptors, plus any that we
     * learn in this execution, from the descriptors we download from the
     * directory authorities.  We should remove descriptors that were
     * published more than one week ago from the list, because they
     * wouldn't be referenced in a consensus anyway. */
    long lastKnownConsensusValidAfterTime = 1234567890000L;
    Map<String, Long> lastKnownVoteValidAfterTimes =
        new HashMap<String, Long>();
    lastKnownVoteValidAfterTimes.put(
        "1234567890ABCDEF1234567890ABCDEF12345678", 1234567890000L);
    Map<String, Long> knownServerDescriptorIdentifiers =
        new HashMap<String, Long>();
    Map<String, Long> knownExtraInfoDescriptorIdentifiers =
        new HashMap<String, Long>();

    /* Create a relay descriptor reader to read descriptors from cached
     * descriptor files in a local Tor data directory. */
    RelayDescriptorReader reader =
        DescriptorSourceFactory.createRelayDescriptorReader();

    /* Tell the reader where to find relay descriptor files to parse.  In
     * this case it's a Tor data directory with cached descriptor
     * files. */
    reader.addDirectory(new File("tor-data-dir"));

    /* Exclude cached descriptor files that haven't changed since we last
     * ran this application. */
    reader.setExcludeFile(new File("tor-data-dir/cached-descriptors"),
        1234567890000L);

    /* Let the reader index all files in the given directory.  The result
     * is a descriptor store with all found descriptors. */
    DescriptorStore store = reader.initialize();

    /* Go through the lists of consensuses, server descriptors, and
     * extra-info descriptors to update a TorStatus-specific database.
     * For this example, we only print out how many such descriptors were
     * read. */
    int consensuses = store.getAllRelayNetworkStatusConsensuses().size();
    int votes = store.getAllRelayNetworkStatusVotes().size();
    int serverDescriptors = store.getAllRelayServerDescriptors().size();
    int extraInfoDescriptors = store.getAllRelayExtraInfoDescriptors().
        size();
    System.out.println("We read " + consensuses + " consensuses, " + votes
        + " votes, " + serverDescriptors + " server descriptors, and "
        + extraInfoDescriptors + " extra-info descriptors.");

    /* Drop the descriptor store reference.  This may be a good time to
     * run garbage collection if we're low on memory. */
    store = null;

    /* Remember which descriptors we just processed to exclude them from
     * the download.  This code is independent of the API and therefore
     * not shown here. */

    /* Go through the list of parsed files and store their last
     * modification times, so that we can exclude them the next time if
     * they haven't changed. */
    for (DescriptorFile descriptorFile : store.getDescriptorFiles()) {
      File file = descriptorFile.getFile();
      long lastModified = descriptorFile.getLastModified();
    }

    /* Do the same operations as shown above for other local directories
     * containing relay descriptors.  For example, metrics-db rsyncs the
     * directory-archive script output from tor26 once per day and imports
     * them, too.  The operations are very similar.  We should use a new
     * RelayDescriptorReader for every directory. */

    /* Download missing descriptors from the directory authorities.
     * Create an instance of the descriptor downloader that contains the
     * logic to download descriptors from the directory authorities. */
    RelayDescriptorDownloader downloader =
        DescriptorSourceFactory.createRelayDescriptorDownloader();

    /* Make one or more directory authorities or directory mirrors known
     * to the downloader. */
    downloader.addDirectoryAuthority("gabelmoo", "212.112.245.170", 80);

    /* Tell the descriptor that we're interested in downloading pretty
     * much every descriptor type there is. */
    downloader.setIncludeCurrentConsensus();
    downloader.setIncludeCurrentReferencedVotes();
    downloader.setIncludeReferencedServerDescriptors();
    downloader.setIncludeReferencedExtraInfoDescriptors();

    /* Exclude the descriptors that we already know.  This is vital to
     * avoid putting too much load on the directories.  (Excluding the
     * consensus and votes if they have been processed before is not shown
     * here, because it requires some timestamp parsing; using the API for
     * this should be trivial, though.) */
    downloader.setExcludeServerDescriptors(
        knownServerDescriptorIdentifiers.keySet());
    downloader.setExcludeExtraInfoDescriptors(
        knownExtraInfoDescriptorIdentifiers.keySet());

    /* Set a request timeout of 2 minutes and a global timeout of 1 hour
     * to avoid being blocked forever by a slow download, but also to
     * avoid giving up too quickly. */
    downloader.setRequestTimeout(2L * 60L * 1000L);
    downloader.setGlobalTimeout(60L * 60L * 1000L);

    /* Run the previously configured downloads.  This method call blocks
     * the main thread until all downloads have finished or the global
     * timeout has expired.  The result is a descriptor store with all
     * received descriptors. */
    store = downloader.initialize();

    /* Write the list of processed descriptors to disk, so that we don't
     * download them in the next execution.  This code is independent of
     * the API and therefore not shown here. */

    /* That's it.  We're done. */
  }
}

