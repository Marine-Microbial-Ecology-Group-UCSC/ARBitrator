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
 *    TabularBlastHit.java
 *    Copyright (C) 2014 Philip Heller
 *
 */

package arbitrator.utils;

import java.util.*;


// 
// Tabular (-m 8) blast output is e.g.
// SCRPIER:1:1101:14543:1467#NGTAGC/1	646312021	85.47	117	17	0	35	151	1291015	1290899	8e-22	97.6
// Fields are query, subject, %ident, length, mismatches, gap opens, q start, q end, s start, s end, e, score
//
// jmagasin 25 April 2019:  Comment seems to be for legacy blastall.  This is not
// the format returned by NCBI blastp any more, which is checked in Pipeline.java
// (search "expectedFields").  It seems this class is not longer used though.

public class TabularBlastHit 
{
	public String				query;
	public String 				subject;
	public float				pctIdent;
	public int					length;
	private int					mismatches;
	private int					gapOpens;
	public double				e;
	public int					queryStart;
	public int					queryEnd;
	public int					subjectStart;
	public int					subjectEnd;
	public float				score;
	
	
	private enum Field
	{
		QUERY, SUBJECT, IDENT, LENGTH, MISMATCHES, GAP_OPENS, QSTART, QEND, SSTART, SEND, E, SCORE;	
	}
	
	
	public TabularBlastHit(String s) throws IllegalArgumentException
	{
		String[] pieces = s.split("\\s");
		Vector<String> vec = new Vector<String>();
		for (String piece: pieces)
			if (!piece.trim().isEmpty())
				vec.add(piece);		
		assert vec.size() == Field.values().length;
		int n = 0;
		try
		{
			query = vec.get(n);
			subject = vec.get(++n);
			pctIdent = Float.parseFloat(vec.get(++n));
			length = Integer.parseInt(vec.get(++n));
			mismatches = Integer.parseInt(vec.get(++n));
			gapOpens = Integer.parseInt(vec.get(++n));
			int q1 = Integer.parseInt(vec.get(++n));
			int q2 = Integer.parseInt(vec.get(++n));
			queryStart = Math.min(q1, q2);
			queryEnd = Math.max(q1, q2);
			int s1 = Integer.parseInt(vec.get(++n));
			int s2 = Integer.parseInt(vec.get(++n));
			subjectStart = Math.min(s1, s2);
			subjectEnd = Math.max(s1, s2);
			e = Double.parseDouble(vec.get(++n));
			score = Float.parseFloat(vec.get(++n));
		}
		catch (NumberFormatException nfx)
		{
			String err = "Can't parse field: " + Field.values()[n] + "\n" +
				"Fields: query, subject, %ident, length, mismatches, gap opens, q start, q end, s start, s end, e, score\n" +
				s + "\n";
			for (int i=0; i<pieces.length; i++)
				err += "\n  " + i + ": " + pieces[i];
			err += "\nNFE message: " + nfx.getMessage();
			assert false : err;	
			throw new IllegalArgumentException(err);
		}
		assert n == Field.values().length - 1;
	}
	
	
	public boolean meetsCriteria(float pctIdent, int length)
	{
		return this.pctIdent >= pctIdent  &&  this.length >= length;
	}
	
	
	public String toString()
	{
		return "Query=" + query + ", Sbjct=" + subject + ", %ident=" + pctIdent + " over " + length +
			" at " + queryStart + "-" + queryEnd + ", e-value=" + e;
	}
}
