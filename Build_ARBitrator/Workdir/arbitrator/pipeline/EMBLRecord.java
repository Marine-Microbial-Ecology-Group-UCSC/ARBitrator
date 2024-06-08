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
 *    EMBLRecord.java
 *    Copyright (C) 2014 Philip Heller
 *
 */


package arbitrator.pipeline;

import java.io.*;
import java.util.*;
import arbitrator.utils.*;




//
// Instances are constructed by specifying GI of a protein sequence. Call convertToEmbl() to read the
// NCBI record and convert to a subset of EMBL format that the ARB input filter can handle.
//


public class EMBLRecord 
{
	private final static File	RESULTS_DIRF					= Pipeline.getResultsDirf();
	private final static File	EMBLS_DIRF						= Pipeline.getEMBLSDirf();
	private final static File	INTERMEDIATES_DIRF				= Pipeline.getIntermediatesDirf();
	

    private boolean				retainIntermediateFiles;		// true to debug, false for production
	private String 				proteinGI;
	private File				proteinGPFile;
	private File				nucleotideGPFile;
	private File				rawEmblFile;
	private File 				mergedQuotesEmblFile;
	private File				finalEmblFile;
    private PrintStream 		stderr = System.err;                                        
    private PrintStream			nullPrintStream = new PrintStream(new NullOutputStream());
    private boolean				verbose;
    private String				apiKey;

		
    EMBLRecord(String proteinGI, String apiKey)
	{
		this.proteinGI = proteinGI;
		this.apiKey = apiKey;

		proteinGPFile = new File(INTERMEDIATES_DIRF, "A__protein_" + proteinGI + ".gp");
		
		// The conversion steps read and write increasingly polished files. The intermediate files
		// are discarded unless the caller explicitly requests that they be retained.
		nucleotideGPFile = new File(INTERMEDIATES_DIRF, "B__nucleotides_" + proteinGI + ".gp");
		rawEmblFile = new File(INTERMEDIATES_DIRF, "C__rawEMBL_" + proteinGI + ".embl");	
		mergedQuotesEmblFile = new File(INTERMEDIATES_DIRF, "D__merged_quotes_" + proteinGI + ".embl");
		finalEmblFile = new File(EMBLS_DIRF, "ARB_ready_" + proteinGI + ".embl");
	}
    
    
    
    
    
		    
		    
		    ///////////////////////////////////////////////////////////////////////////////////
		    //                                                                               //
		    //                             CONVERSION: TOP LEVEL                             //
		    //                                                                               //
		    ///////////////////////////////////////////////////////////////////////////////////
			
    

	//
	// ARBitrator blasts against the nr protein database because it's so much cleaner than nt (which contains
	// whole genomes, fragments, scaffolds, and other records that we aren't interested in. But what we need
	// for aligning and tree building is nucleotide records. This method reads the NCBI protein record and
	// converts it to an EMBL nucleotide record.
	//
	public void convertToNucleotideEmbl() throws ConversionException, IOException
	{
		// jmagasin May 2017 : Reuse existing GP file (if retainIntermediateFiles is true)
		if (!nucleotideGPFile.exists()) {
			// Retrieve protein gp page. It's only used for extracting info from the "coded_by" tag for
			// requesting the nucleotide page.
			String proteinGPPage = BlastHTTPClient.getProteinGPPage(proteinGI, apiKey);
			if (verbose)
				sop("convertToNucleotideEmbl() wrote protein GP page to " + proteinGPFile.getAbsolutePath());
		
			// Parse "coded_by" field, and use resulting info to request nucleotide sequence. Store
			// nucleotide page in a file. URL is e.g. 
			//     https://eutils.ncbi.nlm.nih.gov/entrez/eutils/efetch.cgi?db=nucleotide&id=JX626159.1&rettype=gbwithparts&seq_start=1&seq_stop=378&strand=1
			String nukeFetchSurl = extractNukeFetchQueryFromProteinGPCodedBy(proteinGPPage);	
			BlastHTTPClient client = new BlastHTTPClient(nukeFetchSurl, apiKey);
			String nucleotidesPage = client.getResponsePageAsString();
			client.close();
			StringUtils.textToFile(nucleotidesPage, nucleotideGPFile); 
			if (verbose)
				sop("convertToNucleotideEmbl() wrote nucleotide GP page to " + nucleotideGPFile.getAbsolutePath());
		} else if (verbose) {
			sop("convertToNucleotideEmbl() used existing GP page " + nucleotideGPFile.getAbsolutePath());
		}
		// Run nucleotide file through readseq to generate C__rawEMBL_id.ebi. Command line is e.g.
		// 		java -cp readseq.jar run -f em /tmp/142330.gp -o /tmp/142330.embl
		// which can be passed into the run class as args[]. Redirect stderr, because readseq emits
		// one irritating stderr message per execution.
		//
		// readseq is public domain. See http://iubio.bio.indiana.edu/soft/molbio/readseq/java/
		String[] readseqArgs = new String[5];
		readseqArgs[0] = "-f";
		readseqArgs[1] = "em";
		readseqArgs[2] = nucleotideGPFile.getAbsolutePath();
		readseqArgs[3] = "-o";
		readseqArgs[4] = rawEmblFile.getAbsolutePath();
		// jmagasin May 2017: See Phil's comment about about readseq messages.  However, if
		// an Error occurs then nullPrintStream apparently squelches the stack trace.  E.g.
		// a manifest problem --> iubio classes not found --> Error --> silent pipeline exit.
		// Instead, catch() the error and assert.  (Fixed manifest, end of Error.)
		try
		{
			System.setErr(nullPrintStream);
			iubio.readseq.run.main(readseqArgs);			// 3P library call
			System.setErr(stderr);
		}
		catch (Error x)
		{
			sop("Error (" + x.toString() + "): " + x.getMessage());
			assert false : "Terminating with error when trying to use readseq.";
		}
		File emblFile = new File(readseqArgs[4]);
		if (emblFile == null  ||  !emblFile.exists()  ||  emblFile.length() < 500)
			throw new ConversionException(ConversionFailure.NUCLEOTIDE_PAGE_NOT_CONVERTED_TO_EMBL);
		if (verbose)
			sop("convertToNucleotideEmbl() wrote raw embl page to " + emblFile.getAbsolutePath());
		
		// Merge lines spanned by a literal quote. See comments in EMBLQuoteMerger re why this is
		// necessary. Create another intermediate file.
		EMBLQuoteMerger.merge(emblFile, mergedQuotesEmblFile);
		
		// In case of multiple coding sequences, get rid of all but widest. Create another intermediate file.
		EMBLDuplicateFeatureResolver resolver = new EMBLDuplicateFeatureResolver(mergedQuotesEmblFile);
		resolver.resolve(finalEmblFile);
		
		// Delete intermediate files.
		if (!retainIntermediateFiles)
			deleteIntermediateFiles();
		
		// If not fully converted, should have thrown before getting this far.
		assert isConverted();
	}
	
	
	void convertToNucleotideEmblUnlessAlreadyConverted() throws ConversionException, IOException
	{
		if (!isConverted())
			convertToNucleotideEmbl();
	}

	
	
	void deleteIntermediateFiles()
	{
		proteinGPFile.delete();
		nucleotideGPFile.delete();
		rawEmblFile.delete();
		mergedQuotesEmblFile.delete();
	}
	

	
	
	
	
    
		    
		    ///////////////////////////////////////////////////////////////////////////////////
		    //                                                                               //
		    //                       CONVERSION: PROTEIN TO NUCLEOTIDE                       //
		    //                                                                               //
		    ///////////////////////////////////////////////////////////////////////////////////
			
			
	
	// Given a protein gp page containing a "coded_by" field, generates an eUtils http query for retrieving
	// the nucleotide coding sequence. The gp page can be plaintext or xml.
	private String extractNukeFetchQueryFromProteinGPCodedBy(String gpPage) 
		throws ConversionException, IOException
	{		
		int codonStart = getCodonStartFromGPPage(gpPage);
		return (new LineIterator(gpPage).nextLine().contains("xml"))  ?
			extractNukeFetchQueryFromProteinGPCodedByXMLFormat(gpPage, codonStart)  :
			extractNukeFetchQueryFromProteinGPCodedByTextFormat(gpPage);
	}
	
	
	//
	//	If successful, returns a URL string for fetching a nucleotide sequence. Parses a line like:
	//
	//         /coded_by="M11579.1:525..1397"
	//                   or
	//         /coded_by="complement(NC_014248.1:3907725..3908705)"
	//
	//  Throws ConversionException if failure. Most common failure mode is missing "coded_by" field
	//	in older records.
	//	
	private String extractNukeFetchQueryFromProteinGPCodedByTextFormat(String gpPage) 
		throws ConversionException
	{		
		// Extract coding start position, to be used later for adjusting the DNA sequence.
		int codingStart = getCodonStartFromGPPage(gpPage);
		LineIterator iter = new LineIterator(gpPage);		
		String line = null;  
		while ((line = iter.readLine()) != null)
		{
			line = line.trim();
			if (line.startsWith("/coded_by"))
			{
				// Found "Coded by" section.
				line = StringUtils.crunch(line, "<>");		// Delete > and <
				String strand = (line.contains("complement"))  ?  "2"  :   "1";
				line = line.substring(line.indexOf('\"') + 1);
				if (!line.endsWith("\""))
				{
					// Malformed.
					throw new ConversionException(ConversionFailure.PROTEIN_GP_PAGE_NO_CODED_BY_TAG);
				}
				line = line.substring(0, line.length()-1); 		// quotes now stripped
				if (strand.equals("2"))
				{
					// Range spec for reverse strand has parens in it:
					// /coded_by="complement(NC_014248.1:3907725..3908705)"
					int indStart = line.indexOf('(') + 1;
					int indEnd = line.indexOf(')');
					line = line.substring(indStart, indEnd);
				}
				String[] pieces = line.split(":");				// accession# : range
				String accession = pieces[0];
				String range = pieces[1];
				try
				{
					// Compute from & to as if start at 1.
					String sFrom = range.substring(0, range.indexOf("."));
					int from = Integer.parseInt(sFrom);
					String sTo = range.substring(1+range.lastIndexOf("."));
					int to = Integer.parseInt(sTo);
					// Adjust from & to for coding start.
					if (strand.equals("1"))
					{
						from += codingStart - 1;					// codonStart is 1 2 or 3
						while ((to-from+1) % 3 != 0)
							to--;
					}
					else
					{
						assert strand.equals("2");
						to = to - codingStart + 1;
						while ((to-from+1) % 3 != 0)
							from++;
					}
					// jmagasin May 2019: Specify retmode=text (or you'll get xml), and change
					// db from nucelotide --> nuccore.
					String surl = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/efetch.cgi?";
					surl += "db=nucleotide&id=" + accession + "&rettype=gbwithparts&retmode=text";
					surl += "&seq_start=" + from + "&seq_stop=" + to + "&strand=" + strand;
					return surl;								// error-free return
				}
				catch (NumberFormatException x)
				{				
					throw new ConversionException(ConversionFailure.PROTEIN_GP_PAGE_BAD_NUMBER_FORMAT);
				}
			}
		}
		
		throw new ConversionException(ConversionFailure.PROTEIN_GP_PAGE_NO_CODED_BY_TAG);
	}
	
	
	//
	// In XML format, "coded by" looks like this:
	// 
    // <GBQualifier_name>coded_by</GBQualifier_name>
    // <GBQualifier_value>FN649279.1:&lt;1..&gt;429</GBQualifier_value>
	//
	// This corresponds to plaintext /coded_by="FN649279.1:<1..>429"
	//
	private String extractNukeFetchQueryFromProteinGPCodedByXMLFormat(String gpPage, int codingStart) 
		throws ConversionException
	{
		// Find line.
		LineIterator iter = new LineIterator(gpPage);		
		String line = null;  
		boolean found = false;
		while ((line = iter.readLine()) != null)
		{
			line = line.trim();
			if (line.contains("coded_by"))
			{
				line = iter.readLine();
				found = true;
				break;
			}
		}
		if (!found)
			throw new ConversionException(ConversionFailure.PROTEIN_GP_PAGE_NO_CODED_BY_TAG);
		
		// Parse.
		line = line.trim();
		if (!line.startsWith("<GBQualifier_value>"))
			throw new ConversionException(ConversionFailure.PROTEIN_GP_PAGE_NO_CODED_BY_TAG);
		if (!line.endsWith("</GBQualifier_value>"))
			throw new ConversionException(ConversionFailure.PROTEIN_GP_PAGE_NO_CODED_BY_TAG);
		line = line.substring(line.indexOf(">")+1, line.indexOf("</GBQ"));
		StringBuilder sb = new StringBuilder();
		int n = 0;
		while (n < line.length())
		{
			if (line.substring(n).startsWith("&lt;"))
			{
				sb.append("<");
				n += 4;
			}
			else if (line.substring(n).startsWith("&gt;"))
			{
				sb.append(">");
				n += 4;
			}
			else
			{
				sb.append(line.charAt(n));
				n += 1;
			}
		}
		
		// Delegate.
		String plaintext = "/coded_by=\"" + sb + "\"";
		return extractNukeFetchQueryFromCodedByLine(plaintext, codingStart);
	}
	
	
	// Assumes line format is as in text file e.g. /coded_by="M11579.1:525..1397". If parsing an
	// XML file, field is encoded and should first be decoded.
	private String extractNukeFetchQueryFromCodedByLine(String line, int codingStart) 
		throws ConversionException
	{
		line = line.trim();
		assert line.startsWith("/coded_by");

		line = StringUtils.crunch(line, "<>");		// Delete > and <
		String strand = (line.contains("complement"))  ?  "2"  :   "1";
		line = line.substring(line.indexOf('\"') + 1);
		if (!line.endsWith("\""))
		{
			// Malformed.
			throw new ConversionException(ConversionFailure.PROTEIN_GP_PAGE_NO_CODED_BY_TAG);
		}
		line = line.substring(0, line.length()-1); 		// quotes now stripped
		if (strand.equals("2"))
		{
			// Range spec for rev strand has parens in it:
			// /coded_by="complement(NC_014248.1:3907725..3908705)"
			int indStart = line.indexOf('(') + 1;
			int indEnd = line.indexOf(')');
			line = line.substring(indStart, indEnd);
		}
		String[] pieces = line.split(":");				// accession# : range
		String accession = pieces[0];
		String range = pieces[1];
		try
		{
			// Compute from & to as if start at 1.
			String sFrom = range.substring(0, range.indexOf("."));
			int from = Integer.parseInt(sFrom);
			String sTo = range.substring(1+range.lastIndexOf("."));
			int to = Integer.parseInt(sTo);
			// Adjust from & to for coding start.
			if (strand.equals("1"))
			{
				from += codingStart - 1;					// codonStart is 1 2 or 3
				while ((to-from+1) % 3 != 0)
					to--;
			}
			else
			{
				assert strand.equals("2");
				to = to - codingStart + 1;
				while ((to-from+1) % 3 != 0)
					from++;
			}
			// jmagasin May 2019: Specify retmode=text (or you'll get xml), and change
			// db from nucelotide --> nuccore.
			String surl = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/efetch.cgi?";
			surl += "db=nuccore&id=" + accession + "&rettype=gbwithparts&retmode=text";
			surl += "&seq_start=" + from + "&seq_stop=" + to + "&strand=" + strand;
			return surl;								// error-free return
		}
		catch (NumberFormatException x)
		{				
			throw new ConversionException(ConversionFailure.PROTEIN_GP_PAGE_BAD_NUMBER_FORMAT);
		}
	}
	
	
	//
	// Looks for a line like 
	//    /codon_start=3   (plaintext)
	// or
    //    <GBQualifier_name>codon_start</GBQualifier_name>
	//    <GBQualifier_value>3</GBQualifier_value>
	// The default codon start position is 1, and the "/codon_start=" line is optional. So the
	// only easiest approach is to read the entire file just in case it contains the line.
	//
	private int getCodonStartFromGPPage(String gpPage)
	{
		// Plaintext.
		if (!gpPage.substring(0, 50).contains("xml"))
		{
			LineIterator iter = new LineIterator(gpPage);		
			int ret = 1;			// default	
			String line = null;
			while ((line = iter.nextLine()) != null)
			{
				line = line.trim();
				if (!line.contains("codon_start"))
					continue;
				int index = line.indexOf('=') + 1;
				char ch = line.charAt(index);
				ret = ch - '0';
				break;
			}
			return ret;
		}
		
		// XML.
		else
		{			
			LineIterator iter = new LineIterator(gpPage);		
			int ret = 1;			// default	
			String line = null;
			while ((line = iter.nextLine()) != null)
			{
				line = line.trim();
				if (!line.contains("codon_start"))
					continue;
				line = iter.nextLine();							// <GBQualifier_value>3</GBQualifier_value>
				line = line.substring(1+line.indexOf(">"));		// 3</GBQualifier_value>
				line = line.substring(0, line.indexOf("<"));	// 3
				return Integer.parseInt(line);
			}
			return ret;
		}
	}
	
	
				
	
	
	
	
			
			    ///////////////////////////////////////////////////////////////////////////////////
			    //                                                                               //
			    //                           POST-CONVERSION ACCESSORS                           //
			    //                                                                               //
			    ///////////////////////////////////////////////////////////////////////////////////
			
	
	
	
	// Amino acid sequence appears in a line like this:
	// FT     /translation="MAM...AEEV"
	public String getAASequenceFromEMBLFile() throws IOException
	{
		boolean cachedRetain = retainIntermediateFiles;
		retainIntermediateFiles = true;
		if (!finalEmblFile.exists())
		{
			try
			{
				convertToNucleotideEmbl();
			}
			catch (ConversionException x)
			{
				return null;
			}
		}
		FileReader fr = new FileReader(finalEmblFile);
		LineNumberReader lnr = new LineNumberReader(fr);
		String line = null;
		String ret = null;
		while ((line = lnr.readLine()) != null)
		{
			if (!line.startsWith("FT"))
				continue;
			if (!line.contains("/translation="))
				continue;
			int n = line.indexOf("\"") + 1;
			int n1 = line.lastIndexOf("\"");
			ret = line.substring(n, n1);
			break;
		}
		retainIntermediateFiles = cachedRetain;
		if (!retainIntermediateFiles)
			deleteIntermediateFiles();
		return ret;
	}
	
	
	// Return null if couldn't convert.
	public String getNucleotideSequenceFromEMBLFile() throws IOException
	{
		boolean cachedRetain = retainIntermediateFiles;
		retainIntermediateFiles = true;
		if (!finalEmblFile.exists())
		{
			try
			{
				convertToNucleotideEmbl();
			}
			catch (ConversionException x)
			{
				return null;
			}
		}
		FileReader fr = new FileReader(finalEmblFile);
		BufferedReader br = new BufferedReader(fr);
		String line = null;
		String seq = "";
		boolean foundSeq = false;
		while ((line = br.readLine()) != null)
		{
			if (!foundSeq)
			{
				if (line.startsWith("SQ")  &&  line.contains("Sequence"))
					foundSeq = true;
			}
			else
			{
				for (char c: line.toCharArray())
				{
					if (Character.isWhitespace(c) || Character.isDigit(c) || c=='/')
						continue;
					seq += c;
				}
			}
		}
		br.close();
		fr.close();
		retainIntermediateFiles = cachedRetain;
		if (!retainIntermediateFiles)
			deleteIntermediateFiles();
		return seq;
	}
	
	
	//
	// Look for a line like this:
	// LOCUS       1G20_A                   492 aa            linear   BCT 27-DEC-2012
	//
	// Note: extracts from genbank gp file. You could also get the length by calling 
	// getAASequenceFromEMBLFile().length(), but that requires converting the genbank
	// file to an EMBL file.
	// 
	public int getAASequenceLengthFromGenBankFile() throws Exception
	{
		if (!proteinGPFile.exists())
		{
			String proteinGPPage = BlastHTTPClient.getProteinGPPage(proteinGI, apiKey);
			StringUtils.textToFile(proteinGPPage, proteinGPFile); // Maybe useful for debugging
			if (verbose)
				sop("getAASequenceLengthFromGenBankFile() wrote protein GP page to " + proteinGPFile.getAbsolutePath());
		}		

		FileReader fr = new FileReader(proteinGPFile);
		BufferedReader br = new BufferedReader(fr);
		String line = null;
		String sret = null;
		while ((line = br.readLine()) != null)
		{
			if (!line.startsWith("LOCUS"))
				continue;
			String[] pieces = line.split("\\s");
			Stack<String> stack = new Stack<String>();
			for (String piece: pieces)
			{
				if (piece.trim().length() == 0)
					continue;
				else if (piece.equals("aa"))
				{
					sret = stack.pop();
					break;
				}
				else
					stack.push(piece);
			}
		}
		br.close();
		fr.close();
		
		return (sret == null)  ?  -1  :  Integer.parseInt(sret);
	}
	
	
	public File getProteinGPFile()					{ return proteinGPFile;			 }
	public File getConvertedFile()					{ return finalEmblFile; 		 }
	public void deleteConvertedFile()				{ finalEmblFile.delete();		 }
	public boolean isConverted()					{ return finalEmblFile.exists(); }
	public void setRetainIntermediates(boolean b)	{ retainIntermediateFiles = b;	 }
	public void setVerbose(boolean b)				{ verbose = b;					 }
	static void sop(Object x)						{ System.out.println(x); 		 }
	
	
	public static void main(String[] args)
	{
		try
		{
			sop("STARTING");
			String gi = "13096249";
			EMBLRecord rec = new EMBLRecord(gi, null);
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

