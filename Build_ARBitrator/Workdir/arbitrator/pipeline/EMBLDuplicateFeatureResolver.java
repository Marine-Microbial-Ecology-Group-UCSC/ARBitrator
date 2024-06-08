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
 *    EMBLDuplicateFeatureResolver.java
 *    Copyright (C) 2014 Philip Heller
 *
 */


package arbitrator.pipeline;

import java.io.*;
import java.util.*;
import arbitrator.utils.StringUtils;


/*
 * If a sequence has multiple genes, there will be multiple "gene" and "CDS" fields in
 * the nucleotide record and everything downstream. This code removes all but the widest
 * gene and CDS fields. Pass in the embl file, after multiline quotes have been merged.
 */


class EMBLDuplicateFeatureResolver 
{
	private File 						srcFile;
	private File 						destFile;
	private List<String>				earlyVanillaLines;
	private Vector<Vector<String>>		geneBlocks;
	private Vector<Vector<String>>		CDSBlocks;
	private List<String>				lateVanillaLines;
	private boolean						vanillaIsEarly;
	
	
	private enum State { VANILLA, GENE, CDS };
	
	
	EMBLDuplicateFeatureResolver(File srcFile)
	{
		this.srcFile = srcFile;
		
		earlyVanillaLines = new ArrayList<String>();
		lateVanillaLines = new ArrayList<String>();
		geneBlocks = new Vector<Vector<String>>();
		CDSBlocks = new Vector<Vector<String>>();
	}
	
	
	void resolve(File destFile) throws IOException
	{
		this.destFile = destFile;
		parse();
		recombine();
	}
	
	
	// Distributes input file lines into 4 collections.
	private void parse() throws IOException
	{
		FileReader fr = new FileReader(srcFile);
		LineNumberReader lnr = new LineNumberReader(fr);
		State state = State.VANILLA;
		vanillaIsEarly = true;

		Vector<String> currentGeneBlock = null;
		Vector<String> currentCDSBlock = null;
		
		String line;
		while ((line = lnr.readLine()) != null)
		{
			String nonWhiteAfterTag = (line.length() == 2)  ?  ""  :  line.substring(2).trim();
			
			// Continuation line of current gene or CDS block.
			if (state != State.VANILLA  &&  nonWhiteAfterTag.startsWith("/"))
			{
				Vector<String> block = (state == State.GENE) ? currentGeneBlock : currentCDSBlock;
				block.add(line);
				continue;
			}
			
			// In any state, if line begins "FT   gene" or "FT   CDS" then make transition
			// to gene or CDS state and start a new gene/CDS block.
			if (line.startsWith("FT") && 
					(nonWhiteAfterTag.startsWith("gene") || nonWhiteAfterTag.startsWith("CDS")))
			{
				vanillaIsEarly = false;
				if (nonWhiteAfterTag.startsWith("gene"))			// found a gene block
				{
					currentGeneBlock = new Vector<String>();		
					currentGeneBlock.add(line);
					geneBlocks.add(currentGeneBlock);
					state = State.GENE;
				}
				else
				{
					assert nonWhiteAfterTag.startsWith("CDS");		// found a cds block
					currentCDSBlock = new Vector<String>();
					currentCDSBlock.add(line);
					CDSBlocks.add(currentCDSBlock);
					state = State.CDS;
				}
				continue;
			}
			
			// Neither starting nor continuing a gene or CDS block. Must be vanilla.
			List<String> vanLines = vanillaIsEarly ? earlyVanillaLines : lateVanillaLines;
			vanLines.add(line);
		}
		
		lnr.close();
		fr.close();
	}
	
	
	private void recombine() throws IOException
	{
		Vector<String> bestGeneBlock = extractWidestBlock(geneBlocks);
		Vector<String> bestCDSBlock = extractWidestBlock(CDSBlocks);
		
		FileWriter fw = new FileWriter(destFile);
		for (String line: earlyVanillaLines)
			fw.write(line + "\n");
		if (bestGeneBlock != null)
			for (String line: bestGeneBlock)
				fw.write(line + "\n");
		if (bestCDSBlock != null)
			for (String line: bestCDSBlock)
				fw.write(line + "\n");
		for (String line: lateVanillaLines)
			fw.write(line + "\n");
		fw.flush();
		fw.close();
	}
	
	
	private Vector<String> extractWidestBlock(Vector<Vector<String>> blocks)
	{
		int widest = -1;
		Vector<String> bestBlock = null;
		
		for (Vector<String> block: blocks)
		{
			int[] range = getRange(block.get(0));
			int w = range[1] - range[0] + 1;
			if (w > widest)
			{
				widest = w;
				bestBlock = block;
			}
		}
		
		return bestBlock;
	}
	
	
	//
	// Line is e.g.
	//
	// FT   CDS             <1..>786
	// FT   gene            <1..>786
	// FT   gene            <831   
	// FT   gene            complement(1..>2)
	//
	private int[] getRange(String line) throws IllegalArgumentException
	{
		line = StringUtils.crunch(line, "<>");		// eliminate < and > signs.
		int[] ret = new int[2];
		ret[0] = StringUtils.parseFirstInt(line);
		int nDotDot = line.indexOf("..");
		ret[1] = (nDotDot >= 0)  ?  StringUtils.parseFirstInt(line.substring(nDotDot))  :  ret[1];	
		return ret;
	}
}
