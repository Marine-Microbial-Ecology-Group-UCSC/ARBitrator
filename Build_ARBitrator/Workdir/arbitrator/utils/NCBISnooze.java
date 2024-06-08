/*
 *    This program is free software; you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 2 of the License, or
 *    (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program; if not, write to the Free Software
 *    Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */

/*
 *    NCBISnooze.java
 *    Copyright (C) 2017 Philip Heller
 *
 */

package arbitrator.utils;
import java.util.concurrent.TimeUnit;

//
// NCBI BLAST Developer Information includes a Usage Guidelines section that
// asks automated clients not to bog down the servers by limiting request/poll
// rates to those described in this class.  See the guidelines at:
//    https://blast.ncbi.nlm.nih.gov/Blast.cgi?CMD=Web&PAGE_TYPE=BlastDocs&DOC_TYPE=DeveloperInfo
//
//
// jmagasin: The above link is for the BLAST URL API. However, ARBitrator also
// uses E-utilities, when it generates EMBL files (gets the GP files, then the
// nucleotide sequences). The timing requirements for BUA and eUtils differ as
// of March 2022.  (I think they have always differed, and that the BUA rates
// were always slower than the eUtils rates, so max NCBI request rates were
// mainly not exceeded.)  Moreover, API key support was added to eUtils and this
// supports faster request rates.  API keys were not added to BUA (NCBI responded
// to my question about this) so presumably the rates in the very old BUA docs
// are still the max allowed.
//
//  -- API keys docs are here:
//        https://www.ncbi.nlm.nih.gov/books/NBK25497/
//
//  -- I had already revised NCBISnooze() to support API keys before realizing
//     that ARBitrator uses two URL based APIs and then learning that BUA does
//     not support API keys.  Testing had already showed that API keys in BUA
//     requests don't cause failures. In fact, I was able to blast every 2 sec
//     even though the max rate should be <= 1 per sec.
//
// Changes made to Phil's original NCBISnooze(): The original slept the caller
// for a few seconds, long enough to not violate the max rates allowed by BUA
// (see first link above).
//   -- Actually, the rates had to be adjusted when blasts started failing in
//      May 2019 due to too-fast requests. So maybe NCBI got more strict about
//      the rates.
//
//   -- Why the 2019 problem: Blast requests are multi-threaded via the
//      BlastCoordinator, so the calling threads could each snooze in parallel.
//      However, the threads probably were waking up around the same time
//      leading to a flurry of NCBI requests.
//
//   -- In contrast EMBL generation has always been serial (inspect the Pipeline
//      which generates each EMBL one at a time in a loop).  Though I have still
//      seen failures that I suspect stem from too frequent requests
//      (PROTEIN_GP_PAGE_NO_CODED_BY_TAG) since some of the failures were solved
//      by rerunning ARBitrator.
//
// The new NCBISnooze() was originally implemented as
// BlastHTTPClient.NCBIDelay() and solved the blast issue by tracking the last
// time any thread snoozed, i.e.  all requests from ARBitrator now happen on the
// same clock.  EMBL record problems still occurred since NCBIDelay() was only
// implemented for BlastHTTPClient.  So now (Mar 2022) I am replacing NCBISnooze
// with the NCBIDelay approach (as I should've done at the start).
//
// The rates used below were verified on March 28, 2022 with a non-API-key run
// done on thalassa and an API-key run done on my laptop. Both produced nearly
// identical results:  The same ~8K records failed to download despite different
// networks, rates, start times. And inspection of ~30 of the records showed
// problems in the records themselves (no ID, or no coded_by perhaps because
// computationally predicted). So I am confident that the rates below work.
//
public class NCBISnooze
{

        // BUA: One request per 10 sec.  Keep Phil's 5 sec.  API keys are not supported.
        //      However, I've tried using them with 2sec delay -- worked fine.
        // Eutils: If no API key, 3 requests/sec allowed so make it 0.4 sec.
        //      If API key, 10 requets/sec allowed so make it 0.13 sec.
        private final static int                        MIN_MSECS_BETWEEN_BUA_REQUESTS            = 5000;
        private final static int                        MIN_MSECS_BETWEEN_BUA_REQUESTS_APIKEY     = 5000;
        private final static int                        MIN_MSECS_BETWEEN_EUTILS_REQUESTS         =  400;
        private final static int                        MIN_MSECS_BETWEEN_EUTILS_REQUESTS_APIKEY  =  130;

        // BUA: "Do not poll for any single RID more often than once a minute."
        //      Keep Phil's original 80 sec.  Again, API keys are not supported,
        //      but 61 sec polling delay worked fine in testing.
        // Eutils:  No polling is done. (See code that generates EMBL records.)
        private final static int                        MIN_MSECS_BETWEEN_BUA_RID_POLLS           = 80000;
        private final static int                        MIN_MSECS_BETWEEN_BUA_RID_POLLS_APIKEY    = 80000;

        // Time of last request from ARBitrator.
        private static long                             ncbiLastRequestTime = System.currentTimeMillis();

        // The snooze length depends on the NCBI API (BLAST URL API or
        // E-utilities) and whether an API key is used.  We can determine both
        // by looking at the URL.
        public static void beforeNewRequest(String surl)
        {
            Boolean hasApiKey = surl.contains("api_key") || surl.contains("API_KEY");
            int msecs = hasApiKey ? MIN_MSECS_BETWEEN_BUA_REQUESTS_APIKEY :
                                    MIN_MSECS_BETWEEN_BUA_REQUESTS;
            if (surl.contains("eutils.ncbi.nlm.nih.gov")) {
                msecs = hasApiKey ? MIN_MSECS_BETWEEN_EUTILS_REQUESTS_APIKEY :
                                    MIN_MSECS_BETWEEN_EUTILS_REQUESTS;
            }
            snoozeMilliSecs(msecs);
        }

        // Like above but polling only happens for BLAST results.
        public static void beforePolling(String surl)
        {
            Boolean hasApiKey = surl.contains("api_key") || surl.contains("API_KEY");
            int msecs = hasApiKey ? MIN_MSECS_BETWEEN_BUA_RID_POLLS_APIKEY :
                                    MIN_MSECS_BETWEEN_BUA_RID_POLLS;
            snoozeMilliSecs(msecs);
        }

        // Synchronize access to ncbiLastRequestTime because blast requests are done
        // by multiple threads (but EMBL generation is not). Sleeping while holding
        // the lock is okay because the other threads may not contact NCBI until
        // this thread finishes snoozing.
        private static synchronized void snoozeMilliSecs(int msecs)
        {
            try {
                if (System.currentTimeMillis() - ncbiLastRequestTime <= msecs) {
                    TimeUnit.MILLISECONDS.sleep(msecs);
                }
                // Caller will immediately contact NCBI so note the time.
                ncbiLastRequestTime = System.currentTimeMillis();
            }
            catch (InterruptedException x) {
                x.printStackTrace();
            }
        }
}
