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
 *    BlastCoordinator.java
 *    Copyright (C) 2014 Philip Heller
 *
 */


package arbitrator.pipeline;

import java.util.*;
import java.io.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import org.omg.CORBA.RepositoryIdHelper;

import arbitrator.utils.*;


//
// Blasts each representative in a separate thread, taking advantage of parallelism on the NCBI side. 
//


public class BlastCoordinator 
{
	private Collection<String>				representativeGIs;
	private File							blastResultsDirf;
	private int 							nUnfinishedBlasts;
	private boolean 						verbose;	
	private int								hitListSize;
	private double							expect;
	private String							apiKey;
	
	
	public BlastCoordinator(Collection<String> representativeGIs, File blastResultsDirf, 
				int hitListSize, double expect, String apiKey)
	{
		this.representativeGIs = representativeGIs;
		this.blastResultsDirf = blastResultsDirf;
		this.hitListSize = hitListSize;
		this.expect = expect;
		this.apiKey = apiKey;
	}
	
	
	public File getBlastResultsFile(String seedGI)		
	{
		return new File(blastResultsDirf, "blast_hits_" + seedGI); 			  
	}
	
	
	private class SingleBlaster extends Thread
	{
		private String			representativeGI;
		
		SingleBlaster(String representativeGI)	{ this.representativeGI = representativeGI; }
		
		public void run()
		{
			try
			{
				doRun();
			}
			catch (Exception x)
			{
				sop("SingleSeedBlaster run() stress: " + x.getMessage());
				sop("Are you sure your interconnection is ok?");
			}
		}
		
		private void doRun() throws IOException, ConversionException
		{
			// Set up for blast request.  Submit with getResponsePageAsString().
			BlastHTTPClient client = BlastHTTPClient.forInitiateTabularBlast(representativeGI, hitListSize, expect, apiKey);
			NCBISnooze.beforeNewRequest(client.getURLString());
			Date startTime = new Date();
			sop("Starting blast thread for representative " + representativeGI + " at " + startTime);
			if (verbose)
				sop("1st URL: " + client.getURLString());

			String firstResponse = client.getResponsePageAsString();
			RidAndRtoe rr = extractRIDAndRTOE(firstResponse);
				
			// Wait for results.
			sop("  " + representativeGI + " got 1st response: " + rr);
			boolean resultsReady = false;
			while (!resultsReady)
			{
				client = BlastHTTPClient.forRetrieveTabularBlastResults(rr.rid, hitListSize, apiKey);
				NCBISnooze.beforePolling(client.getURLString());
				if (verbose)
					sop("2nd URL: " + client.getURLString());
				LineNumberReader possibleBlastResultsLNR = client.getLineNumberReaderForResponse();
				if (isCompletedBlastPage(possibleBlastResultsLNR))
					resultsReady = true;
				possibleBlastResultsLNR.close();
				client.close();
			}
			
			// Retrieve results to a file.
			client = BlastHTTPClient.forRetrieveTabularBlastResults(rr.rid, hitListSize, apiKey);
			NCBISnooze.beforeNewRequest(client.getURLString());
			File blastResultsFile = getBlastResultsFile(representativeGI);
			client.writeResponsePageToFile(blastResultsFile);
			nUnfinishedBlasts--;		// atomic
		}
	}  // End of inner class SingleRepresentativeBlaster
	
	
	//
	// Initial response page is HTML, even if requested format is XML. (The
	// FORMAT_TYPE param only affects the results doc.) The initial response
	// page contains a comment like this:
	//     <!--QBlastInfoBegin
	//         RID = 5SJRJKR7015
	//         RTOE = 16
	//     QBlastInfoEnd
	//     -->
	// Line break position, indentation, and capitalization may vary.
	// Also note that RID and RTOE may be left blank if NCBI encountered
	// an error (perhaps including refusal due to too frequent server
	// requests).  The error would be mentioned elsewhere in the HTML.
	//
	public static RidAndRtoe extractRIDAndRTOE(String response)
	{
		RidAndRtoe ret = new RidAndRtoe();
		// Do everything in UC.  Extract the QBlastInfo comment and then
	        // the RID and RTOE within that.  (Probably can use one pattern
		// and then group(1) and group(2)...)
		response = response.toUpperCase();
		int qbiStart = response.indexOf("QBlastInfoBegin".toUpperCase());
		int qbiEnd = response.lastIndexOf("QBlastInfoEnd".toUpperCase(), qbiStart + 100);
		if (qbiStart < 0 || qbiEnd < 0)
		        throw new IllegalArgumentException("Can't find QBlastInfoBegin/End in response.");
		response = response.substring(qbiStart, qbiEnd);
		Pattern p = Pattern.compile("\\bRID\\s*=\\s*(\\w+)\\b");
		Matcher m = p.matcher(response);
		if (!m.find())
		    throw new IllegalArgumentException("Bad RID in response.");
		ret.rid = m.group(1);
		p = Pattern.compile("\\bRTOE\\s*=\\s*(\\d+)\\b");
		m = p.matcher(response);
		if (!m.find())
		    throw new IllegalArgumentException("Bad RTOE in response.");
		ret.rtoe = Integer.parseInt(m.group(1));
		return ret;
	}	
		

	// Ruins the lnr but leaves it open.
	private boolean isCompletedBlastPage(LineNumberReader lnr) throws IOException
	{
		String omen = "# Query:";
		String line = null;
		while ((line = lnr.readLine()) != null)
		{
			if (line.contains(omen))
			{
				return true;
			}
		}
		return false;
	}
	
	
	private static void snoozeMinutes(int nMins)
	{
		snoozeSecs(60*nMins);
	}
	
	
	private static void snoozeSecs(int nSecs)
	{
		try
		{
			Thread.sleep(nSecs*1000);
		}
		catch (InterruptedException x) { }
	}
	
	
	// On return, all blast files are in place.
	public void blastRepresentativesBlockUntilDone() throws IOException
	{
		nUnfinishedBlasts = 0;
		for (String gi: representativeGIs)
		{
			File resultsFile = getBlastResultsFile(gi);
			if (!resultsFile.exists())
			{
				nUnfinishedBlasts++;
				(new SingleBlaster(gi)).start();
				//jmagasin: Removed unneeded NCBISnooze.beforeNewRequest().
				//          See snooze in doRun().
			}
		}
		
		// Less efficient than a producer/consumer design pattern, but there's little for the CPU to
		// do until all the blasts complete. At the time scales involved, any scheduler will do enough
		// thread swapping for the workload to balance.
		while (nUnfinishedBlasts > 0)
		{
			dsop(nUnfinishedBlasts + " unfinished blasts");
			snoozeMinutes(1);
		}
	}	
	
	
	public void setVerbose(boolean verbose)			{ this.verbose = verbose;				}
	public void setHitListSize(int n)				{ this.hitListSize = n;					}
	static void sop(Object x)						{ System.out.println(x);       			}
	static void dsop(Object x)						{ sop(new java.util.Date() + ": " + x); } 

	
	
	
	
	
	public static void main(String[] args)
	{
		try
		{
			String rep = "443146";
			Set<String> reps = new HashSet<String>();
			reps.add(rep);
			BlastCoordinator coord = new BlastCoordinator(reps, Pipeline.getResultsDirf(), 100, 1.0e-1, null);
			coord.setVerbose(true);
			coord.blastRepresentativesBlockUntilDone();
		}
		catch (Exception x)
		{
			sop("Stress: " + x.getMessage());
			x.printStackTrace();
		}
		finally
		{
			sop("DONE");
		}
	}
}

