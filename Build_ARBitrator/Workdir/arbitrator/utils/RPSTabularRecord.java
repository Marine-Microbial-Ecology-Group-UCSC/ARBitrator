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
 *    RPSTabularRecord.java
 *    Copyright (C) 2014 Philip Heller
 *
 */


package arbitrator.utils;

import java.util.*;
import java.io.*;


//
// Header in tabular results page:
// Query	Hit type	PSSM-ID	From	To	E-Value	Bitscore	Accession	Short name	Incomplete	Superfamily
//
// Results are tab-delimited.
//


public class RPSTabularRecord 
{
	public String		query;			// E.g. "Q#1 - 1065303"
	public String		hitType;
	public String 		pssmID;
	public int			from;
	public int			to;
	public double		expect;
	public double		score;
	public String 		accession;
	public String 		shortName;
	public String		incomplete;
	public String		superfamily;
	
	
	private RPSTabularRecord(String line)
	{
		String[] pieces = line.split("\\t");
		int n = 0;
		query = pieces[n++];
		hitType = pieces[n++];
		pssmID = pieces[n++];
		from = Integer.parseInt(pieces[n++]);
		to = Integer.parseInt(pieces[n++]);
		expect = Double.parseDouble(pieces[n++]);
		score = Float.parseFloat(pieces[n++]);
		accession = pieces[n++];
		shortName = pieces[n++];
		incomplete = pieces[n++];
		superfamily = pieces[n++];
		assert n == 11;
	}
	
	
	public String toString()
	{
		String s = "RPSTabularRecord:\n  QUERY = " + query + "\n  HIT TYPE = " + hitType +
			"\n  EXPECT = " + expect + "\n  ACCESSION = " + accession + "\n  SHORT NAME = " + shortName;
		return s;
	}
	
	
	public static Vector<RPSTabularRecord> parse(Reader reader) throws IOException
	{
		BufferedReader br = new BufferedReader(reader);
		String line = null;
		Vector<RPSTabularRecord> ret = new Vector<RPSTabularRecord>();
		while ((line = br.readLine()) != null)
			if (line.startsWith("Q#"))
				ret.add(new RPSTabularRecord(line));
		br.close();
		return ret;
	}
	
	
	public static Vector<RPSTabularRecord> parse(File file) throws IOException
	{
		FileReader fr = new FileReader(file);
		return parse(fr);
	}
	
	
	public static Vector<RPSTabularRecord> parse(String src) throws IOException
	{
		StringReader sr = new StringReader(src);
		Vector<RPSTabularRecord> ret = parse(sr);
		sr.close();
		return ret;
	}
	
	
	public String getQueryGI()
	{
		String[] pieces = query.split("\\s");
		return pieces[pieces.length-1];
	}
	
	
	public int getQueryIndex()
	{
		String[] pieces = query.split("\\s");
		return Integer.parseInt(pieces[0].substring(2));
	}
	
	
	public static void retainSpecificAndNonspecificCDHits(Vector<RPSTabularRecord> vec)
	{
		Set<RPSTabularRecord> retains = new HashSet<RPSTabularRecord>();
		for (RPSTabularRecord rec: vec)
		{
			if (rec.hitType.contains("specific")  && rec.accession.startsWith("cd"))
				retains.add(rec);
		}
		vec.retainAll(retains);
	}
}
