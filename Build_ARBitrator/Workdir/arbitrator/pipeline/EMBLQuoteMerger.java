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
 *    EMBLQuoteMerger.java
 *    Copyright (C) 2014 Philip Heller
 *
 */


package arbitrator.pipeline;

import java.io.*;
import java.util.*;
import arbitrator.utils.*;


//
// Some EMBL records contain literal quotes that span multiple lines, e.g. (148791433):
//
// FT                   /PCR_primers="fwd_seq: gghaargghgghathggnaartc, rev_seq:
// FT                   ggcatngcraanccvccrcanac"
//
// Although this is valid EMBL format, the ARB input filter can't handle it. This class
// merges literal quotes to eliminate line-break spanning. The output is valid EMBL
// that the ARB filter can handle.
//


public class EMBLQuoteMerger 
{
	// Static access only.
	private EMBLQuoteMerger()		{ }
	
	
	public static void merge(File inputEmblFile, File outputEmblFile) throws IOException
	{
		StringBuilder sb = new StringBuilder();
		boolean balanced = true;
		FileReader fr = new FileReader(inputEmblFile);
		LineNumberReader lnr = new LineNumberReader(fr);
		String line = null;
		Vector<String> mergeUs = new Vector<String>();
		while ((line = lnr.readLine()) != null)
		{
			if (!line.endsWith("\n"))
				line += "\n";
			
			boolean balancedInCurrentLine = nQuotes(line) % 2 == 0;
			if (balanced)
			{
				if (balancedInCurrentLine)
					sb.append(line);				// vanilla
				else
				{
					balanced = false;
					mergeUs.add(line);				// open a multiline quote
				}
			}
			else
			{
				if (balancedInCurrentLine)
					mergeUs.add(line);
				else
				{
					assert mergeUs.size() >= 1 :	// close a multiline quote
						"Empty merge list.\n" + line;
					mergeUs.add(line);
					String merged = merge(mergeUs);
					sb.append(merged);
					mergeUs.clear();
					balanced = true;
				}
			}
		}
		lnr.close();
		fr.close();
		StringUtils.textToFile(sb.toString(), outputEmblFile);
	}
	
	
	// Treat token groups as words that should be separated by spaces, unless this is
	// the amino acid translation.
	private static String merge(Vector<String> mergeUs)
	{
		boolean aa = mergeUs.get(0).startsWith("FT")  &&  mergeUs.get(0).contains("/translation");
		String ret = mergeUs.remove(0).trim();
		if (!aa)
			ret += " ";
		for (String mergeMe: mergeUs)
		{
			mergeMe = mergeMe.substring(2).trim();
			ret += mergeMe;
			if (!aa)
				ret += " ";
		}
		ret = ret.trim() + "\n";
		return ret;
	}


	private static int nQuotes(String s)
	{
		int n = 0;
		for (int i=0; i<s.length(); i++)
			if (s.charAt(i) == '"')
				n++;
		return n;
	}
}