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
 *   BlastHTTPClient .java
 *    Copyright (C) 2014 Philip Heller
 *
 */


package arbitrator.pipeline;

import java.net.*;
import java.io.*;
import arbitrator.utils.*;

public class BlastHTTPClient
{
	private String					surl;
	private String					apiKey;
	private URLConnection			urlConn;
	private InputStreamReader		isr;
	private boolean					closed;
	
	public BlastHTTPClient(String surl, String apiKey)
	{
		this.surl = surl; 
		this.apiKey = apiKey;
	}


	public static BlastHTTPClient forInitiateTabularBlast(String seedGI, int hitListSize, double eValue, String apiKey)
	{
		String surl = buildInitiateTabularBlastSurl(seedGI, hitListSize, eValue, apiKey);
		return new BlastHTTPClient(surl, apiKey);
	}
	
	
	private static String buildInitiateTabularBlastSurl(String seedGI, int hitListSize, double eValue, String apiKey)
	{
		String surl = "https://blast.ncbi.nlm.nih.gov/Blast.cgi?QUERY=" + seedGI +
		       	      "&DATABASE=nr&PROGRAM=blastp" +
			      "&EXPECT=" + eValue +
			      "&HITLIST_SIZE=" + hitListSize + 
			      "&DESCRIPTIONS=" + hitListSize +
			      "&ALIGNMENTS=" + hitListSize +
			      "&CMD=Put";
		return appendToolAndEmailToUrl(appendApiKeyToUrl(surl, apiKey));
	}
	
	
	public static BlastHTTPClient forRetrieveTabularBlastResults(String rid, int hitListSize, String apiKey)
	{
		String surl = buildRetrieveTabularResultsSurl(rid, hitListSize, apiKey);
		return new BlastHTTPClient(surl, apiKey);
	}
	

	private static String buildRetrieveTabularResultsSurl(String rid, int hitListSize, String apiKey)
	{
		String surl = "https://blast.ncbi.nlm.nih.gov/Blast.cgi?" + 
			      "&CMD=Get" +
			      "&RID=" + rid + 
			      "&DESCRIPTIONS=" + hitListSize + 
			      "&ALIGNMENTS=" + hitListSize + 
			      "&ALIGNMENT_VIEW=Tabular" +
			      "&FORMAT_TYPE=Text";
		return appendApiKeyToUrl(surl, apiKey);
	}
	
	
	public static BlastHTTPClient forInitiateGPLookup(String accession, String apiKey)
	{
		String surl = buildInitiateGPLookupSurl(accession, apiKey);
		return new BlastHTTPClient(surl, apiKey);
	}
		
		
	private static String buildInitiateGPLookupSurl(String accession, String apiKey)
	{
		String surl = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi?" +
			      "&rettype=gp&usehistory=n&db=protein&term=" + accession;
		return appendToolAndEmailToUrl(appendApiKeyToUrl(surl, apiKey));
	}
	
	
	public static BlastHTTPClient forRetrieveGPFromEntrez(String accession, String apiKey)
	{
		String surl = buildRetrieveGPFromEntrez(accession, apiKey);
		return new BlastHTTPClient(surl, apiKey);
	}

		
	private static String buildRetrieveGPFromEntrez(String euID, String apiKey)
	{
		// jmagasin May 2019: If want a GenPept flat file, then also
		// specify retmode=text per Table 1 at:
		//    https://www.ncbi.nlm.nih.gov/books/NBK25499/
		String surl = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/efetch.fcgi?" +
			      "&rettype=gp&retmode=text&db=protein&id=" + euID;
		return appendToolAndEmailToUrl(appendApiKeyToUrl(surl, apiKey));
	}
	
	
	public static String getProteinGPPage(String accessionOrGI, String apiKey) throws IOException, ConversionException
	{		
		// Use eUtils to retrieve .gp page. 1st response page is XML. Retrieve 1st ID in <ID> tag.
		BlastHTTPClient client = forInitiateGPLookup(accessionOrGI, apiKey);
		String eutilsInitialResponse = client.getResponsePageAsString();
		if (eutilsInitialResponse == null)
			throw new ConversionException(ConversionFailure.PROTEIN_GP_PAGE_NO_INITIAL_RESPONSE);
		client.close();
		int index = eutilsInitialResponse.indexOf("<Id>");
		if (index < 0)
			throw new ConversionException(ConversionFailure.PROTEIN_GP_PAGE_NO_ID_TAG_IN_INITIAL_RESPONSE);
		index += "<Id>".length();
		eutilsInitialResponse = eutilsInitialResponse.substring(index);
		String euID = "";
		int n = 0;
		while (Character.isJavaIdentifierPart(eutilsInitialResponse.charAt(n)))
			euID += eutilsInitialResponse.charAt(n++);
		
		// Retrieve the .gp protein page from Entrez. It contains a "coded_by" tag that contains 
		// the nucleotide accession #, range, and strand that we need.
		client = forRetrieveGPFromEntrez(euID, apiKey);
		String gpPage = client.getResponsePageAsString();
		if (gpPage == null)
			throw new ConversionException(ConversionFailure.PROTEIN_GP_PAGE_NO_GP_PAGE);
		client.close();
		return gpPage;
	}
		
	
	public LineNumberReader getLineNumberReaderForResponse() throws MalformedURLException, IOException
	{
		URL url = new URL(surl);		// throws MalformedURLException
		// jmagasin May 2019: Added because NCBI was ignoring requests for nucleotide
		// pages (in getProteinGPPage(), during EMBL file generation) because it seems
		// we were (as of 2019) making too frequent requests.
		NCBISnooze.beforeNewRequest(surl);
		urlConn = url.openConnection();
		isr = new InputStreamReader(urlConn.getInputStream());
		return new LineNumberReader(isr);
	}
	
	
	// Converts IOException to ConversionException.
	public String getResponsePageAsString() throws ConversionException
	{		
		return getResponsePageAsString(-1);
	}

	
	// Converts IOException to ConversionException. Honors maxLines if >0. 
	public String getResponsePageAsString(int maxLines) throws ConversionException
	{
		try
		{
			LineNumberReader lnr = getLineNumberReaderForResponse();
			StringBuilder sb = new StringBuilder();
			String line = null;
			int nLines = 0;
			while ((line = lnr.readLine()) != null)
			{
				sb.append(line);
				if (!line.endsWith("\n"))
					sb.append("\n");
				if (maxLines > 0  &&  ++nLines >= maxLines)
					break;
			}
			lnr.close();
			close();
			return sb.toString();
		}
		catch (IOException x)
		{
			throw new ConversionException(ConversionFailure.NUCLEOTIDE_PAGE_NOT_RECEIVED);
		}
	}
	
	
	public void writeResponsePageToFile(File f) throws MalformedURLException, IOException
	{
		FileWriter fw = new FileWriter(f);
		LineNumberReader lnr = getLineNumberReaderForResponse();
		String line = null;
		while ((line = lnr.readLine()) != null)
		{
			if (!line.endsWith("\n"))
				line = line + "\n";
			fw.write(line);
		}
			
		lnr.close();
		close();
		fw.flush();
		fw.close();
	}
	
	
	public void close() throws IOException
	{
		if (closed)
			return;
		
		if (isr != null)
		{
			isr.close();
			isr = null;
		}
		
		closed = true;
	}
	
	
	String getURLString()					
	{ 
		return surl; 
	}
	

	// If non-null, attach the API key to an NCBI URL, but only for
	// E-utilities.  Eutils supports API supports keys. The BLAST URL API
	// does not. (NCBI responded to my question, March 2022.)  If BUA
	// ever changes to support API keys, change this code.
	//     Including API keys in a BUA request seems to do no harm. In
	//     March 2022 before hearing back from NCBI, I tested under the
	//     assumption that BUA supported API keys. Everything ran fine.
	private static String appendApiKeyToUrl(String surl, String apiKey)
	{
		if (apiKey != null && surl.contains("eutils.ncbi.nlm.nih.gov")) {
			surl = surl + "&api_key=" + apiKey;
		}
		return surl;
	}

	// Adding the tool and developer email to URLs allows NCBI to contact me if
	// problems occur e.g. too fast request rates. Tao Tao at NCBI suggested I
	// do this.
	private static String appendToolAndEmailToUrl(String surl)
	{
		return surl + "&TOOL=ARBitrator&EMAIL=jmagasin@gmail.com";
	}
}
