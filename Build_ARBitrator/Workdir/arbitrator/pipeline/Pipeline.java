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
 *    Pipeline.java
 *    Copyright (C) 2014 Philip Heller
 *
 */


package arbitrator.pipeline;

import iubio.readseq.WriteseqOpts;

import java.util.*;
import java.io.*;
import arbitrator.utils.*;


public class Pipeline 
{
	private final static File		WORK_DIRF				= new File("./work");
	private final static File		INTERMEDIATES_DIRF			= new File(WORK_DIRF, "conversion_intermediates");
	private final static File		EMBLS_DIRF					= new File(WORK_DIRF, "finished_embls");
	private final static File[]		CHECKPOINT_FILES			=
	{
		new File(WORK_DIRF, "PositiveCheckpoint.txt"), new File(WORK_DIRF, "NegativeCheckpoint.txt") 
	};
	// March 2023: Increase from 50K to 100K because we are probably missing
    	// valid nifH, since all the blast results are hitting 50K (if -q 2).
    	// The maintainerNotes.txt have more information.
	private final static int		HIT_LIST_SIZE			= 100000;
	// April 2022: NCBI Batch CD-Search allows 4K proteins per search. However
	// ARBitrator uses GET requests which have a max URL length of ~1K chars. So
	// increasing the batch size from the original 250 to 2K failed (http error
	// 414).
	private final static int		RPS_BLAST_BATCH_SIZE 		=   250;	// worked nicely for nifH and nifD
	private final static String[]	CL_ARG_NAMES				= 
	{ 
		"-q", "-s", "-replistfile", "-posdom", "-uninfdom", "-ol", "-oe", "-oefails", "-ignore", "-norecovery", "-apikey"
	};

	private double					qualityThreshold;
	private double					superiorityThreshold;
	private File 					listOutputFile;
	private File 					emblOutputFile;
	private File					emblConversionFailuresFile;
	private Set<String>				representatives;
	private Vector<File>			ignoreFiles;
	private Set<String>				ignoreGIs;
	private Set<String>				positiveDomains;
	private Set<String>				uninformativeDomains;
	private Set<String>				positiveCallGIs;
	private Set<String>				negativeCallGIs;
	private Set<String>				conversionFailurePositiveCallGIs; // jmagasin Apr 2017: Long -> String
	private boolean					noRecovery;
	private String					apiKey;
	
	
					
	
	
	
	
	
	
					
					//////////////////////////////////////////////////////////////////
					//                                                              //
					//                         CONSTRUCTION                         //
					//                                                              //
					//////////////////////////////////////////////////////////////////
					
	
	
	
	// Args are command-line arguments. Call with "-help" for explanation.
	private Pipeline(String[] args)
	{
		for (String arg: args)
		{
			if (arg.equals("-help"))
			{
				printUsage();
				System.exit(0);
			}
		}
		
		Map<String, String> argnameToValue = tokenizeCommandLine(args);
		validateArgs(argnameToValue);
		
		// Help.
		if (argnameToValue.containsKey("-help"))
		{
			printUsage();
			System.exit(0);
		}
		
		// Quality threshold.
		if (argnameToValue.containsKey("-q"))
		{
			try
			{
				qualityThreshold = Double.parseDouble(argnameToValue.get("-q"));
			}
			catch (NumberFormatException x)
			{
				sop("Illegal quality threshold: " + argnameToValue.get("-q"));
				System.exit(1);
			}
		}
		else
		{
			sop("No quality threshold specified in command line.");
			printUsage();
			System.exit(0);
		}
		
		// Superiority threshold.
		if (argnameToValue.containsKey("-s"))
		{
			try
			{
				superiorityThreshold = Double.parseDouble(argnameToValue.get("-s"));
			}
			catch (NumberFormatException x)
			{
				sop("Illegal superiority threshold: " + argnameToValue.get("-s"));
				System.exit(1);
			}
		}
		else
		{
			sop("No superiority threshold specified in command line.");
			printUsage();
			System.exit(0);
		}
		
		// Representatives.
		representatives = new TreeSet<String>();
		if (argnameToValue.containsKey("-replistfile"))
		{
			File repFile = new File(argnameToValue.get("-replistfile"));
			try
			{
				FileReader fr = new FileReader(repFile);
				BufferedReader br = new BufferedReader(fr);
				String line = null;
				while ((line=br.readLine()) != null) {
				    line = line.trim();
				    if (line.length() > 0)
					representatives.add(line);
				}
				br.close();
				fr.close();
			}
			catch (IOException x)
			{
				sop("Trouble reading representatives from file " + repFile);
				System.exit(1);
			}
		}
		else
		{
			sop("No superiority threshold specified in command line.");
			printUsage();
			System.exit(0);
		}
		
		// Positive domains.
		positiveDomains = new HashSet<String>();
		if (!argnameToValue.containsKey("-posdom"))
		{
			sop("No positive domain specified.");
			claFail();
		}
		String[] domains = argnameToValue.get("-posdom").split(",");
		for (String domain: domains) {
			if (!domain.startsWith("cd")) {
				// Required by RPSTabularRecord.retainSpecificAndNonspecificCDHits().
				sop("Only NCBI-curated domains allowed. " + domain + " does not begin with 'cd'.");
				claFail();
			}
			positiveDomains.add(domain);
		}
		
		// Uninformative domains.
		uninformativeDomains = new HashSet<String>();
		if (argnameToValue.containsKey("-uninfdom"))
		{
			domains = argnameToValue.get("-uninfdom").split(",");
			for (String domain: domains) {
				if (!domain.startsWith("cd")) {
					// As above, see Required by RPSTabularRecord.retainSpecificAndNonspecificCDHits().
					sop("Only NCBI-curated domains allowed. " + domain + " does not begin with 'cd'.");
					claFail();
				}
				uninformativeDomains.add(domain);
			}
		}
		
		// Output files.
		if (argnameToValue.containsKey("-ol"))
			listOutputFile = new File(argnameToValue.get("-ol"));
		if (argnameToValue.containsKey("-oe"))
			emblOutputFile = new File(argnameToValue.get("-oe"));
		if (listOutputFile == null  &&  emblOutputFile == null)
		{
			sop("No list or embl output file specified.");
			claFail();
		}
		if (argnameToValue.containsKey("-oefails"))
			emblConversionFailuresFile = new File(argnameToValue.get("-oefails"));
		
		// Ignore files.
		ignoreFiles = new Vector<File>();
		ignoreGIs = new HashSet<String>();
		if (argnameToValue.containsKey("-ignore"))
		{
			String igList = argnameToValue.get("-ignore");		// comma-delimited
			String[] igPieces = igList.split(",");
			for (String fname: igPieces)
			{
				File file = new File(fname);
				ignoreFiles.add(file);
				if (!file.exists())
				{
					sop("Skipping nonexistent ignore file " + file.getAbsolutePath());
					continue;
				}
				try
				{
					ignoreGIs.addAll(extractGIs(file));
				}
				catch (IOException x)
				{
					sop("Trouble reading ignore file " + file.getAbsolutePath());
				}
			}
			sop("Will ignore " + ignoreGIs.size() + " sequences.");
		}
		
		// Incremental.
		if (argnameToValue.containsKey("-norecovery")  &&  argnameToValue.get("-norecovery").equalsIgnoreCase("true"))
			noRecovery = true;

		// API key.
		if (argnameToValue.containsKey("-apikey"))
			apiKey = argnameToValue.get("-apikey");
	}
	
	
	// Keys are arg names, e.g. "-q". Values are arg values as strings, e.g. "2.0".
	private static Map<String, String> tokenizeCommandLine(String[] args)
	{
		if (args.length % 2 != 0)
			claFail();
		
		Map<String, String> ret = new HashMap<String, String>();
		for (int i=0; i<args.length; i+=2)
		{
			String argname = args[i];
			if (!argname.startsWith("-"))
				claFail();
			String argval = args[i+1];
			ret.put(argname, argval);
		}
		
		return ret;
	}
	
	
	// Exits if trouble.
	private static void validateArgs(Map<String, String> argnameToValue)
	{
		Set<String> validArgNames = new HashSet<String>();
		for (String validName: CL_ARG_NAMES)
			validArgNames.add(validName);
		
		for (String argname: argnameToValue.keySet())
		{
			if (!validArgNames.contains(argname))
			{
				sop("Invalid arg name: " + argname);
				claFail();
			}
		}
	}
	
	
	private static void claFail()
	{
		assert false : "cla fail";
		printUsage();
		System.exit(1);
	}
	
	
	private static void printUsage()
	{
		assert false;
		String s = "Usage: java arbitrator.pipeline.Pipeline -q quality_threshold -s superiority_threshold " +
			"-replistfile representative_GI_filename -posdom positive_domain_list " +
			"-uninfdom uninformative_domain_list -ol list_output_file" +
			"-oe embl_output_file -oefails EMBL_failures_file -ignore ignore_file_list -norecovery true/false " +
			"-apikey your_key";
		sop(s);
		sop("\n  GIs of representative protein sequences should be 1 per line in file specified by \"replistfile\"");
		sop("\n  Positive and uninformative domain lists are comma-separated with no spaces. At least 1");
		sop("  positive domain must be specified. Only NCBI-curated conserved domains are allowed and they must");
		sop("  begin with lower case 'cd'.\n");
		sop("  \"-ol\" specifies output as a list of nucleotide GIs, 1 per line.");
		sop("  \"-oe\" specifies output as an ARB-compatible EMBL file.");
		sop("  1 or both of \"-ol\" and \"-oe\" must be specified.");
		sop("  Sometimes records cannot be converted to EMBL. Use -oefails to request a list of these " +
			"GIs to the specified file.");
		sop("\n  For incremental execution, any number of ignore files may be specified. These are");
		sop("  \"-ol\" and \"-oe\" files generated by previous runs; their GIs will not be output in the current run.");
		sop("  The list of ignore files should be comma-separated with no spaces.\n");
		sop("  \"-norecovery=true\" (UNIX only) deletes recovery checkpoint files, forcing a fresh complete run.\n"  +
				"  Under Windows, checkpoint files must be deleted manually (remove \"work\" subdir and contents)."  +
				"\n  Omit or use \"-norecovery=false\" to use checkpoint files and restart a prematurely termainated run.\n");
		sop("  \"-apikey\" is optionally used to specify an API KEY specific to your NCBI account. This has two");
		sop("  advantages: (1) NCBI permits higher request rates for E-utilities when an API key is used, so");
		sop("  ARBitrator will run faster. (2) If your network is such all traffic to NCBI appears to come from");
		sop("  one IP address, the max request rate allowed by NCBI will be shared across applications, and NCBI");
		sop("  may reject requests if the rate is too high. API keys allow NCBI to monitor the request rate for");
		sop("  the user associated with the key and should avoid this issue.  Note that ARBitrator monitors its");
		sop("  request rate so that it does not exceed max rates allowed by NCBI.\n");
	}
	
	
	public String toString()
	{
		// Thresholds.
		String s = "Pipeline: quality/superiority thresholds = " + qualityThreshold + "/" + superiorityThreshold + "\n";
		s += "  E-value for blast = " + getExpect();
		
		// Representatives.
		s += "\n  Representatives:";
		for (String rep: representatives)
			s += " " + rep;
		
		// Domains.
		s += "\n  Positive domain(s): ";
		for (String domain: positiveDomains)
			s+= " " + domain;
		s += "\n  Uninformative domain(s): ";
		if (uninformativeDomains.isEmpty())
			s += " None";
		else
			for (String domain: uninformativeDomains)
				s+= " " + domain;
		
		// Output files.
		if (listOutputFile != null)
			s += "\n  list output file = " + listOutputFile.getAbsolutePath();
		if (emblOutputFile != null)
			s += "\n  EMBL output file = " + emblOutputFile.getAbsolutePath();
		if (emblConversionFailuresFile != null)
			s += "\n  EMBL failures file = " + emblConversionFailuresFile.getAbsolutePath();
		
		// Ignore files.
		if (!ignoreFiles.isEmpty())
		{
			s += "\n  Ignore files:";
			for (File f: ignoreFiles)
				s += "\n    " + f.getAbsolutePath();
		}
		
		// Recovery.
		if (noRecovery)
			s += "\n  Delete recovery files and start a fresh execution. (UNIX only).";
		else
			s += "\n  Use recovery files for start where last run terminated.";
		
		// API key
		if (apiKey == null) {
			s += "\n  No API key will be used so ARBitrator can make up to 3 NCBI requests per sec.";
			s += "\n  For faster performance consider getting an API key as described here:";
			s += "\n    https://support.nlm.nih.gov/knowledgebase/article/KA-05317/en-us";
		} else {
			s += "\n  An API key will be used so ARBitrator can make up to 10 NCBI requests per sec.";
		}
		return s;
	}
	
	
	// An EMBL file contains 1 or more EMBL records, recognizable because they begin "ID", followed by more
	// text. A list file contains 1 word per line.
	private static Set<String> extractGIs(File file) throws IOException
	{
		Set<String> ret = new HashSet<String>();
		
		FileReader fr = new FileReader(file);
		BufferedReader br = new BufferedReader(fr);
		String firstLine = br.readLine();
		br.close();
		fr.close();
		
		if (firstLine.startsWith("ID"))
			return extractGIsFromEMBLFile(file);
		else if (firstLine.split("\\s").length == 1)
			return extractGIsFromListFile(file);
		else
		{
			sop("Skipping ignore file " + file.getName() + ": not a valid list or EMBL file");
			return ret;
		}
	}
	
	
	private static Set<String> extractGIsFromEMBLFile(File file) throws IOException
	{
		Set<String> ret = new HashSet<String>();
		FileReader fr = new FileReader(file);
		BufferedReader br = new BufferedReader(fr);
		String line = null;
		while ((line = br.readLine()) != null)
		{
			if (!line.startsWith("SV"))
				continue;
			boolean foundGI = false;
			String[] pieces = line.split("\\s");
			for (String piece: pieces)
			{
			    if (piece.startsWith("GI:")) // jmagasin: Bug, update since GI's obsolete.
				{
					ret.add(piece);
					foundGI = true;
					break;
				}
			}
			assert foundGI;
		}
		br.close();
		fr.close();
		return ret;
	}
	
	
	private static Set<String> extractGIsFromListFile(File file) throws IOException
	{
		Set<String> ret = new HashSet<String>();
		FileReader fr = new FileReader(file);
		BufferedReader br = new BufferedReader(fr);
		String line = null;
		while ((line = br.readLine()) != null)
			ret.add(line.trim());
		br.close();
		fr.close();
		return ret;
	}
			

	
	
	
				
	
				
				
				//////////////////////////////////////////////////////////////////
				//                                                              //
				//                          EXECUTION                           //
				//                                                              //
				//////////////////////////////////////////////////////////////////
				

	
	
	public void runPipeline() throws IOException
	{			
		// Delete recovery files from last run unless doing recovery.
		if (noRecovery)
		{
			String osName = System.getProperty("os.name");
			if (osName.toLowerCase().startsWith("windows"))
			{
				sop("Can't programmatically remove recovery files under Windows.");
				sop("Please manually delete " + WORK_DIRF.getAbsolutePath() + " and its contents.");
				System.exit(1);
			}			
			String cl = "rm -rf " + WORK_DIRF.getAbsolutePath();
			SystemCaller caller = new SystemCaller(cl);
			sop("Will remove all recovery files...");
			caller.execute();
			try
			{
				caller.blockUntilCompletion();
			}
			catch (InterruptedException x) { }
			sop("... done.");
			sop(cl);
		}
		
		// Ensure output dirs exist.
		if (!INTERMEDIATES_DIRF.exists())
			INTERMEDIATES_DIRF.mkdirs();
		if (!EMBLS_DIRF.exists())
			EMBLS_DIRF.mkdirs();
		
		// Blast if necessary.
		double expect = getExpect();
		BlastCoordinator blastCoordinator = new BlastCoordinator(representatives, WORK_DIRF, HIT_LIST_SIZE, expect, apiKey);
		blastCoordinator.blastRepresentativesBlockUntilDone();
		
		// Sequences probably appear in blast results for most/all representatives. Retain positive and
		// negative calls from all blasts, for instant classification when sequences reappear in later blasts.
		// Initialize with any checkpointed calls from a prior (and presumably aborted) run.
		positiveCallGIs = new TreeSet<String>();
		if (CHECKPOINT_FILES[0].exists())
			loadGIsFromFile(positiveCallGIs, CHECKPOINT_FILES[0]);
		negativeCallGIs = new TreeSet<String>();
		if (CHECKPOINT_FILES[1].exists())
			loadGIsFromFile(negativeCallGIs, CHECKPOINT_FILES[1]);
		conversionFailurePositiveCallGIs = new TreeSet<String>(); // jmagasin Apr 2017: Long -> String
		
		// Read blast results html file for each representative. Collect synonymous groups.
		Vector<SynonymousHitGroup> synoGroups = new Vector<SynonymousHitGroup>();
		for (String repGI: representatives)
		{
			File blastResultsFile = new File(WORK_DIRF, "blast_hits_" + repGI);
			assert blastResultsFile.exists()  :  "No such file: " + blastResultsFile.getAbsolutePath();
			FileReader fr = new FileReader(blastResultsFile);
			BufferedReader br = new BufferedReader(fr);
			String line = null;
			while ((line = br.readLine()) != null)
			{
			    // jmagasin 25 Apr 2019:  If this is the fields line, then make sure it
			    // is as expected with respect to order and required fields being present.
			    // The SynonymousHitGroup requires this!
			    if (line.startsWith("# Fields: ")) {
				String expectedFields = "# Fields: query acc.ver, subject acc.ver, % identity, " +
				    "alignment length, mismatches, gap opens, q. start, q. end, s. start, " +
				    "s. end, evalue, bit score, % positives";
				if (!line.equalsIgnoreCase(expectedFields)) {
				    sop("Error:  Expected BLAST results should have fields\n" +
					expectedFields + "\n" +
					"but that is not what is seen in" + 
					blastResultsFile.getAbsolutePath() + " which had\n" + line);
				}
			    }
			    
			    // jmagasin 25 Apr 2019:  Collect accessions from this line if it looks
			    // okay.  I have seen truncated HSP lines returned by NCBI.  Note that a
			    // line with just the query field will not be detected.
			    if (line.startsWith("#") || line.split("\\t").length < 13) {
				if (line.split("\\t").length > 1 && !line.trim().startsWith("Status=")) {
				    sop("Error: Incomplete HSP line in " +
					blastResultsFile.getAbsolutePath() + ".  Expected >= 13 fields. " +
					"If this occurred on the last line of the file, then it could " +
					"indicate subsequent lines were lost entirely.  The incomplete line " +
					"is: " + line);
				}
				continue;
			    }
				SynonymousHitGroup hitGroup = null;
				try
				{
					hitGroup = new SynonymousHitGroup(line);
				}
				catch (IllegalArgumentException x)
				{
					// jmagasin Apr 2017: Have seen bad line just that *after*
					// the one reported (truncated mid-line)
					sop("Error: " + blastResultsFile.getAbsolutePath() +
					    ": Couldn't parse:\n" + line);
					continue;
				}
				assert hitGroup != null;
				synoGroups.add(hitGroup);
			} // end of for-each-line-in-file loop
			br.close();
			fr.close();
		}
		sop("Collected " + synoGroups.size() + " synonymous hit groups. Will RPSBlast in batches of " + RPS_BLAST_BATCH_SIZE);
		
		// Classify each hit group. There's a nice efficiency benefit to batching the RPS-BLAST requests.
		// Batch size of 250 worked well for nifH and nifD.
		int nSynoGroups = 0;
		int totalSynoGroups = synoGroups.size();
		Vector<SynonymousHitGroup> batch = new Vector<SynonymousHitGroup>();
		while (!synoGroups.isEmpty())
		{
			SynonymousHitGroup shg = synoGroups.remove(0);
			nSynoGroups++;
			for (String gi: shg)
			{
				// If any member of the group has already been called, this group's call is known.
				boolean known = false;
				boolean call = false;
				if (positiveCallGIs.contains(gi)  ||  ignoreGIs.contains(gi))
				{
					known = true;
					call = true;
				}
				else if (negativeCallGIs.contains(gi))	
				{
					known = true;
				}
				if (known)
				{
					sop("Group " + nSynoGroups + " of " + totalSynoGroups + " = " + shg + " is known " + 
						(call ? "positive" : "negative"));
					shg.classify(call);
					recordCallsForSynoGroup(shg);
					break;
				}
			}
			if (shg.isCalled)
				continue;
			batch.add(shg);
			sop("Add to batch: Group " + nSynoGroups + " of " + totalSynoGroups + " = " + shg);
			if (batch.size() == RPS_BLAST_BATCH_SIZE)
			{
				dsop("Checkpointing " + positiveCallGIs.size() + " positive and " + negativeCallGIs.size() +
					" negative calls to filesystem.");
				checkpointCalls();
				classifyBatch(batch);
				batch.clear();
			}
		}
		if (!batch.isEmpty())
			classifyBatch(batch);
		
		// Output list.
		assert listOutputFile != null  ||  emblOutputFile != null;
		positiveCallGIs.removeAll(ignoreGIs);
		dsop("\n\nFinished classifying, will write output.");
		if (listOutputFile != null)
		{
			try
			{
				FileWriter listFW = new FileWriter(listOutputFile);
				for (String gi: positiveCallGIs)
					listFW.write(gi + "\n");
				listFW.flush();
				listFW.close();
			}
			catch (IOException x)
			{
				sop("Trouble writing list output file " + listOutputFile.getAbsolutePath() + ": " + x.getMessage());
			}
		}
		
		// Output EMBL. First write one file per record, then concatenate.
		if (emblOutputFile != null)
		{
			int nGoodWrites = 0;
			int nBadWrites = 0;
			try
			{
				// Generate an individual EMBL file for each record.
				for (String gi: positiveCallGIs)
				{
					try
					{
						EMBLRecord rec = new EMBLRecord(gi, apiKey);
						if (rec.isConverted()) {
							sop("Already have EMBL for accession or gi =" + gi + "...");
						} else {
							sop("Generating EMBL for " + (1+nGoodWrites+nBadWrites) + " of " + 
							    positiveCallGIs.size() + ", accession or gi =" + gi + "...");
							rec.convertToNucleotideEmblUnlessAlreadyConverted();
						}
						nGoodWrites++;
						sop("  ... Success:  Successes/Failures = " + nGoodWrites + "/" + nBadWrites);
					}
					catch (ConversionException x)
					{
						nBadWrites++;
						sop("  ... Failed: " + x.toString() + "   Successes/Failures = " + 
							nGoodWrites + "/" + nBadWrites);
						// jmagasin Apr 2017: Changed getMessage to toString. Next, removed
						// Long.parseLong(gi) since now using accessions (in gi).
						conversionFailurePositiveCallGIs.add(gi);
					}
				}
				// Concatenate.
				FileWriter emblFW = new FileWriter(emblOutputFile);
				int nAppends = 0;
				for (String kid: EMBLS_DIRF.list())
				{
					if (kid.endsWith(".embl"))
					{
						File kidf = new File(EMBLS_DIRF, kid);
						append(emblFW, kidf);
						if (++nAppends % 100 == 0)
							sop("Concatenated " + nAppends + " individual EMBL records.");
					}
				}
				emblFW.flush();
				emblFW.close();
			}
			catch (IOException x)
			{
  			        sop("Trouble writing embl output file " + x.getMessage());
				if (nGoodWrites + nBadWrites < positiveCallGIs.size())
				    sop("Unable to finish getting EMBL files.");
			}
		}
		
		// Output EMBL conversion failures.
		if (emblConversionFailuresFile != null)
		{
			FileWriter failuresFW = new FileWriter(emblConversionFailuresFile);
			// jmagasin Apr 2017: "gi" is now a String (not Long)
			for (String gi: conversionFailurePositiveCallGIs)
				failuresFW.write(gi + "\n");
			failuresFW.flush();
			failuresFW.close();
		}
		sop("Finished generating EMBL files.");  // jmagasin May 2017
	}	
	
	
	// Guards against crashes or hangups in the NCBI RPSBlast service. Writes all positive and
	// negative calls to files, which are read in when the pipeline next executes. Classification
	// of any synonymous group containing any of these GIs is rapid.
	private void checkpointCalls() throws IOException
	{
		Vector<Collection<String>> callSets = new Vector<Collection<String>>();
		callSets.add(positiveCallGIs);
		callSets.add(negativeCallGIs);
		
		for (int i=0; i<2; i++)
		{
			FileWriter fw = new FileWriter(CHECKPOINT_FILES[i]);
			for (String gi: callSets.get(i))
				fw.write(gi + "\n");
			fw.flush();
			fw.close();
		}
	}
	
	
	private static void append(FileWriter fw, File appendMe) throws IOException
	{
		FileReader fr = new FileReader(appendMe);
		BufferedReader br = new BufferedReader(fr);
		String line = null;
		while ((line = br.readLine()) != null)
			fw.write(line + "\n");
		br.close();
		fr.close();
	}
	
	
	private int countPositives()
	{
		return positiveCallGIs.size();
	}
	
	
	private void classifyBatch(Vector<SynonymousHitGroup> batch) throws IOException
	{
		// Blast the batch. Each synonymous group is blasted by blasting the first GI in the group.
		int nGIs = 0;
		for (SynonymousHitGroup shg: batch)
			nGIs += shg.size();
		dsop("\n*** " + countPositives() + " positives so far. Will rpsblast batch of " + batch.size() + 
			" groups containing " + nGIs + " sequences ***");
		Vector<String> gis = new Vector<String>();
		for (SynonymousHitGroup shg: batch)
			gis.add(shg.firstElement());
		NCBIRPSBlaster batchBlaster = new NCBIRPSBlaster(gis, apiKey);
		Vector<RPSTabularRecord> batchResults = batchBlaster.blast();
				
		// Remove hits to uninformative domains.
		RPSTabularRecord.retainSpecificAndNonspecificCDHits(batchResults);		
		Set<RPSTabularRecord> uninformatives = new HashSet<RPSTabularRecord>();
		for (RPSTabularRecord rec: batchResults)
			if (cdIsUninformative(rec.accession))
				uninformatives.add(rec);
		batchResults.removeAll(uninformatives);
		
		// Collect by query #.
		Map<Integer, Vector<RPSTabularRecord>> queryNumToHits = new TreeMap<Integer, Vector<RPSTabularRecord>>();
		for (Integer i=1; i<=batch.size(); i++)
			queryNumToHits.put(i, new Vector<RPSTabularRecord>());
		for (RPSTabularRecord rec: batchResults)
		{
			String numberedQuery = rec.query;		// e.g. "Q#1 - 1065303"
			String[] pieces = numberedQuery.substring(2).split("\\s");
			Integer qnum = Integer.valueOf(pieces[0]);
			queryNumToHits.get(qnum).add(rec);
		}
		
		// Classify.
		for (Integer qnum: queryNumToHits.keySet())
		{
			SynonymousHitGroup synoGroup = batch.get(qnum-1);
			Vector<RPSTabularRecord> hits = queryNumToHits.get(qnum);
			if (hits.isEmpty()  ||  !cdHitIsPositive(hits.firstElement()))
			{
				// 1st informative hit is not to target domain => classify as false.
				synoGroup.classify(false);
				sop("Group " + qnum + ": 1st informative hit not to positive domain => classify negative.");
			}
			else if (hits.size() == 1)
			{
				// 1st informative hit is to target domain and is the only hit => classify as true
				synoGroup.classify(true);
				sop("Group " + qnum + ": only 1 hit, which is to positive domain => classify positive.");
			}
			else
			{
				// 1st informative hit is to target domain => classify as true if superiority >= threshold.
				// April 2022: Superiority should only be over hits from non-positive domains. As described
				// in the paper, consider the top 3 hits. (Previous code compared to hit #2.)
				double eTarget = hits.get(0).expect;
				double eOther = Double.POSITIVE_INFINITY;  // eTarget = 0 results in superiority = inf + inf
				for (Integer i = 1; i <= 2 && i < hits.size(); i++) {
					if (!cdHitIsPositive(hits.get(i))) {
						eOther = hits.get(i).expect;
						break;
					}
				}
				double superiority = Math.log10(eOther) - Math.log10(eTarget);
				synoGroup.classify(superiority >= superiorityThreshold);	
				sop("Group " + qnum + " called " + synoGroup.callToString() + " based on superiority=" + superiority);
			}
			recordCallsForSynoGroup(synoGroup);
		}
	}
	
	
	private boolean cdHitIsPositive(RPSTabularRecord hit)
	{
		for (String cd: positiveDomains)
			if (hit.accession.equals(cd))
				return true;
		return false;
	}
	
	
	private boolean cdIsUninformative(String cd)
	{
		for (String uninformativeCd: uninformativeDomains)
			if (cd.equals(uninformativeCd))
				return true;
		return false;
	}
	
	
	void recordCallsForSynoGroup(SynonymousHitGroup synoGroup)
	{
		assert synoGroup.isCalled;
		if (synoGroup.calledPositive)
			positiveCallGIs.addAll(synoGroup);
		else
			negativeCallGIs.addAll(synoGroup);
	}
	
	
	private static void loadGIsFromFile(Collection<String> dest, File file) throws IOException
	{
		FileReader fr = new FileReader(file);
		BufferedReader br = new BufferedReader(fr);
		String line = null;
		while ((line=br.readLine()) != null)
			dest.add(line.trim());
	}
	
	
	private double getExpect()
	{
		return Math.pow(10, -qualityThreshold);
	}
	
	
	static File getResultsDirf()		{ return WORK_DIRF; }
	static File getEMBLSDirf()			{ return EMBLS_DIRF; }
	static File getIntermediatesDirf()	{ return INTERMEDIATES_DIRF; }
	static void sop(Object x)			{ System.out.println(x); }
	static void dsop(Object x)			{ sop(new java.util.Date() + ": " + x); } 
	
	
	/***
	private final static String			S_TEST_ARGS =
		"-q 2 -s 1 -posdom cd02040 -uninfdom cd02117 -ol listout_short.txt " +
		"-replistfile nifh_representatives.txt -ignore tmp/ignoreGIs.txt";
	***/
	
	public static void main(String[] args)
	{
		try
		{
			// Require Java 8 at least because Java 8 by default uses TLS v1.2 which
			// NCBI required after Sept. 2020.  Even if it is possible to use v1.2
			// with Java 6 and 7, they lack ciphers required by NCBI and we will get
			// a handshake error.
			String[] jverStr = System.getProperty("java.version").split("\\.");
			Double jver = Double.parseDouble(jverStr[0] + "." + jverStr[1]);
			sop("Java version is " + jver);
			if (jver < 1.8)
			{
				sop("You need Java 8 or later to meet NCBI security (SSL) requirements.");
				System.exit(1);
			}

			dsop("Starting the ARBitrator pipeline: " + args.length + " args");
			Pipeline pipeline = new Pipeline(args);
			sop("----------\n" + pipeline + "\n----------\n");
			pipeline.runPipeline();
			sop("-----------------------------\n" +
			    "Normal completion of pipeline" +
			    "\n----------------------------\n"); // jmagasin added
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

