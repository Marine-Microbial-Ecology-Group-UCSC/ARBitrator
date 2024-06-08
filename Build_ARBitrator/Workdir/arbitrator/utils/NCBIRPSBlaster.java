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
 *    NCBIRPSBlaster.java
 *    Copyright (C) 2014 Philip Heller
 *
 */


package arbitrator.utils;

import java.io.*;
import java.util.*;
import java.net.*;


public class NCBIRPSBlaster 
{
	private final static String			SAFE_LF				= "%0A";
	private Vector<String>				queryProteinGIs;
	private String					apiKey;
	private boolean					verbose;
	
	
	public NCBIRPSBlaster(String queryProteinGI, String apiKey)
	{
		queryProteinGIs = new Vector<String>();
		queryProteinGIs.add(queryProteinGI);
		apiKey = apiKey;
	}
	
	
	public NCBIRPSBlaster(Vector<String> queryProteinGIs, String apiKey)
	{
		this.queryProteinGIs = queryProteinGIs;
		this.apiKey = apiKey;
	}
	
	
	public Vector<RPSTabularRecord> blast() throws IOException	
	{
		if (verbose)
			sop("Will blast");
		
		// Format a URL. GIs are separated by "%0A".
		String surl = "https://www.ncbi.nlm.nih.gov/Structure/bwrpsb/bwrpsb.cgi?queries=";		
		for (String gi: queryProteinGIs)
		{
			surl += gi;
			if (gi != queryProteinGIs.lastElement())
				surl += SAFE_LF;
		}			
		surl += "&useid1=true&tdata=hits&db=cdd&evalue=.01&dmode=all&maxhit=10";	
		
		// Submit request. Initial response is e.g.
		//     #Batch CD-search tool	NIH/NLM/NCBI
		//     #cdsid	QM3-qcdsearch-1314A4F913A52A2A-39B47A831E03737B
		//     #datatype	hits Concise data
		//     #status	3	msg	Job is still running
		String initialResponse = getResponsePageAsString(surl, false);
		StringReader sr = new StringReader(initialResponse);
		BufferedReader br = new BufferedReader(sr);
		br.readLine();
		String cdsidLine = br.readLine();			// #cdsid	QM3-qcdsearch-1314A4F913A52A2A-39B47A831E03737B
		if (!cdsidLine.trim().startsWith("#cdsid")) {  // jmagasin Apr 2017
			assert false : "Expected cdsid line from NCBI; got\n" + cdsidLine;
		}		
		br.readLine();
		String statusLine = br.readLine();
		if (!statusLine.trim().startsWith("#status")) {  // jmagasin Apr 2017
			assert false : "Expected status line from NCBI; got\n" + statusLine;
		}		
		int lastStatusCode = StringUtils.parseFirstInt(statusLine);
		br.close();
		sr.close();
		String cdsid = cdsidLine.substring(6).trim();
		
		// Poll for results. Response is as above, but now we look at "#status" line.
		// Status code = 0 means success.
		// jmagasin 20 Feb 2020: CD-Search has been under heavy load and fails.  Change
		// this loop to exit immediately if we get unexpected status codes (all seem
		// unrecoverable, in particular 4 [queue manager service error] which is what I've
		// seen of late). Note that reducing the batch size to 100 led to successful
		// requests *sometimes*, in the evening, but now even the usual 250 succeeds.
		surl = "https://www.ncbi.nlm.nih.gov/Structure/bwrpsb/bwrpsb.cgi?cdsid=" + cdsid;
		while (lastStatusCode != 0)
		{
			String pollResponse = getResponsePageAsString(surl, true);
			sr = new StringReader(pollResponse);
			br = new BufferedReader(sr);
			br.readLine();
			br.readLine();
			br.readLine();
			statusLine = br.readLine();
			if (!statusLine.trim().startsWith("#status")) {  // jmagasin Apr 2017
				assert false : "Expected status line from NCBI; got\n" + statusLine;
			}
			lastStatusCode = StringUtils.parseFirstInt(statusLine);
			if (verbose)
				sop("status code = " + lastStatusCode);
			if (lastStatusCode != 0 && lastStatusCode != 3) {  // jmagasin 20 Feb 2020
				assert false : "NCBI CD-Search for this batch failed with status code" +
				               lastStatusCode + ".  The cdsid was" + cdsid;
			}
			br.close();
			sr.close();
		}
		
		// Retrieve results.
		surl = "https://www.ncbi.nlm.nih.gov/Structure/bwrpsb/bwrpsb.cgi?cdsid=" + cdsid +
			"&tdata=aligns&alnfmt=xml&dmode=all";
		String results = getResponsePageAsString(surl, false);
		if (verbose)
			sop(results);
		return RPSTabularRecord.parse(results);
	}
	
	
	public String getResponsePageAsString(String surl, boolean polling) throws IOException
	{
		surl = appendToolAndEmailToUrl(appendApiKeyToUrl(surl, apiKey));
		if (polling)
		    NCBISnooze.beforePolling(surl);
		else
		    NCBISnooze.beforeNewRequest(surl);
		    
		// Connect a LineNumberReader to the initial response.
		URL url = new URL(surl);
		URLConnection urlConn = url.openConnection();
		InputStreamReader isr = new InputStreamReader(urlConn.getInputStream());
		LineNumberReader lnr = new LineNumberReader(isr);
		
		// Assemble response into a monolithic string.
		StringBuilder sb = new StringBuilder();
		String line = null;
		while ((line = lnr.readLine()) != null)
		{
			sb.append(line);
			if (!line.endsWith("\n"))
				sb.append("\n");
		}
		lnr.close();
		isr.close();
		return sb.toString();
	}	
	
	// Placeholder in case API keys are ever supported by NCBI APIs besides
	// E-utilities.  See notes in the BlastHTTPClient version of this function.
	private static String appendApiKeyToUrl(String surl, String apiKey)
	{
		if (apiKey != null && surl.contains("eutils.ncbi.nlm.nih.gov")) {
		   surl = surl + "&api_key=" + apiKey;
		}
		return surl;
	}

	// So NCBI can notify me if request rates are too high. Tao Tao (NCBI User
	// Services) suggested adding this (and also in Eutils docs).
	private static String appendToolAndEmailToUrl(String surl)
	{
		return surl + "&TOOL=ARBitrator&EMAIL=jmagasin@gmail.com";
	}
	
	public void setVerbose(boolean b)
	{
		verbose = b;
	}
	
	
	static void sop(Object x)	{ System.out.println(x); }
	
	
	public static void main(String[] args)
	{		
		try
		{
			sop("Starting " + new Date());
			String gi = "500676957";
			NCBIRPSBlaster blaster = new NCBIRPSBlaster(gi, null);
			Vector<RPSTabularRecord> hits = blaster.blast();
			int n = 10;
			for (RPSTabularRecord hit: hits)
			{
				sop(hit);
				if (n-- == 0)
					break;
			}
			sop("****************************************************************************************");
			RPSTabularRecord.retainSpecificAndNonspecificCDHits(hits);
			n = 10;
			for (RPSTabularRecord hit: hits)
			{
				sop(hit);
				if (n-- == 0)
					break;
			}
		}
		catch (Exception x)
		{
			sop("Stress: " + x.getMessage());
			x.printStackTrace();
		}
		finally
		{
			sop("DONE " + new Date());
		}
	}
}
