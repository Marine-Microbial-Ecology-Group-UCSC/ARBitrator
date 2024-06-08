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
 *    SynonymousHitGroup.java
 *    Copyright (C) 2014 Philip Heller
 *
 */


package arbitrator.pipeline;

import java.io.*;
import java.util.*;


//
// Members are protein GIs. Encapsulates a set of records with identical protein sequences. Instances are
// parsed from blast results obtained in sensitivity phase. Only 1 member of a synonymous group needs to go 
// through the specificity test. 
//
// The constructor only parses an input line. Classification, which happens externally after construction, 
// sets the superiority, isCalled, and calledPositive fields.
//


public class SynonymousHitGroup extends Vector<String>
{
	public double			e;
	public double			superiority;
	public boolean			isCalled;
	public boolean			calledPositive;
	
	
	//
	// Parse from a line of text from a tabular blast results file, e.g.:
	// gi|1171710|sp|P46034.1|NIFH_FRASP  gi|148667|gb|AAA24916.1|;gi|259512040|sp|A8L2C4.1  94.43	97.56  287	16	0	1	287	1	287	0.0	  565
	//                                                                                                                                  ^^^ E-value
        //
        // jmagasin 25 Apr 2019:  This tabular blast format is no longer used!  At some point, NCBI
	// moved the % positives from field # 4 to field #13.  Also, during the phasing out of GI's,
	// I changed the code to parse the subject field (#2) to get an accession rather
	// than a GI.  This is described in the following comment from 2017:
        //   jmagasin Apr 2017:  Try to get an accession rather than a GI since NCBI phased out
        //   GI's (no longer assigned).  See Chap. 16, Appx. 1, Table 1 in The NCBI Handbook
        //   for DB's in defline and which have accessions, at the following link:
        //      https://www.ncbi.nlm.nih.gov/books/NBK21097/table/A632/?report=objectonly
        //   The following have format "<db>|<accession>|...":  GenBank (gb), EMBL (emb),
        //   DDBJ (dbj), SWISS-PROT (sp), RefSeq (ref).  If there is a GI it will be first.
        //    -- By Table 1 PDB entries lack accessions.  However, it looks like NCBI
        //       treats "<entry>_<chain>" as an accession.  That is supported below.
	// However, now it seems only one accession is returned in the subject field!  So I have
	// disabled code below that parses to find an accession.
	// Important:  The Pipeline code that parses each line of the blast results (and for each
	// line constructs a SynonymousHitGroup) checks that the expected fields, in the expected
	// order, are present.  Absolutely essential for SynonymousHitGroup.
        //
	public SynonymousHitGroup(String line) throws IllegalArgumentException
	{
		// Indexing from 0 and not counting blank fields, subjects are field 1 (semicolon-delimited list)
		// and e-value is field 11.
		String[] pieces = line.split("\\s");
		Vector<String> nonWhitespacePieces = new Vector<String>();
		for (String piece: pieces)
			if (!piece.trim().isEmpty())
				nonWhitespacePieces.add(piece);
		if (nonWhitespacePieces.size() < 13)  // jmagasin: 12 --> 13
			throw new IllegalArgumentException(line);
		String sSubjects = nonWhitespacePieces.get(1);
		String sE = nonWhitespacePieces.get(11);
		e = Double.parseDouble(sE);
		
		// jmagasin 25 April 2019: Disabled code that extracts an accession
		// from a multi-accession subject field.
		if (true) {
		    if (sSubjects.split(";").length > 1) {
			// No longer expecting multiple subjects.
			throw new IllegalArgumentException(line);
		    }
		    add(sSubjects);
		} else {
		    // Parse subject gis: gi|148667|gb|AAA24916.1;gi|259512040|sp|A8L2C4.1
		    String[] dbsInOrder = {"ref","gb","emb","sp","dbj","pdb","gi"};
		    pieces = sSubjects.split(";");
		    for (String piece: pieces) {
			String acc = null;  // should include the version
			String[] sbjctPieces = piece.split("\\|");
			for (int i=0; i <= dbsInOrder.length-1; i++) {
			    for (int j=0; j <= sbjctPieces.length-1; j++) {
				if (sbjctPieces[j].equals(dbsInOrder[i])) {
				    // Found the DB.  By Table 1, there is a next field with the
				    // accession (or the GI if we reached the end of dbsInOrder).
				    acc = sbjctPieces[j+1];
				    if (acc == "pdb") {
					// Special: Tack on the chain.  (See note above).
					acc = acc + "_" + sbjctPieces[j+2];
				    }
				    break;
				}
			    }
			    if (acc != null) break;
			}
			if (acc != null) {
			    add(acc);
			} else {
			    throw new IllegalArgumentException(line);
			}
		    }
		}
	}
	
	
	public void classify(boolean calledPositive)
	{
		isCalled = true;
		this.calledPositive = calledPositive;
	}
	
	
	String gisToString()
	{
		String s = "";
		for (String gi: this)
			s += " " + gi;
		return s.trim();
	}
	
	
	String callToString()
	{
		return calledPositive ? "positive" : "negative";
	}
	
	
	public String toString()
	{
		return size() + " synonymous hit(s): " + gisToString() + " .. Expect = " + e;
	}
}	
