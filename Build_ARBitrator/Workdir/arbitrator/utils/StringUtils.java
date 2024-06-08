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
 *    StringUtils.java
 *    Copyright (C) 2014 Philip Heller
 *
 */


package arbitrator.utils;

import java.io.*;
import java.util.Vector;


public class StringUtils 
{
	// Static access only.
	private StringUtils()	{ }
	
	
	// Removes every char in removeUS from crunchMe.
	public static String crunch(String crunchMe, String removeUs)
	{
		StringBuilder sb = new StringBuilder();
		for (int i=0; i<crunchMe.length(); i++)
		{
			char ch = crunchMe.charAt(i);
			if (removeUs.indexOf(ch) < 0)
				sb.append(ch);
		}
		return sb.toString();
	}
	
	
	// Can handle initial - sign.
	public static int parseFirstInt(String s)
	{
		// Find 1st digit.
		int indexFirstDigit = -1;
		for (int i=0; i<s.length(); i++)
		{
			if (Character.isDigit(s.charAt(i)))
			{
				indexFirstDigit = i;
				break;
			}
		}
		if (indexFirstDigit == -1)
			throw new IllegalArgumentException("No digits in " + s);
		
		// Parse.
		int ival = s.charAt(indexFirstDigit) - '0';
		int i = indexFirstDigit + 1;
		while (i<s.length())
		{
			char ch = s.charAt(i);
			if (Character.isDigit(ch))
			{
				ival *= 10;
				ival += ch - '0';
				i++;
			}
			else
				break;
		}
		
		// Check for - sign.
		if (indexFirstDigit > 0  &&  s.charAt(indexFirstDigit-1) == '-')
			ival = -ival;
		
		return ival;
	}
	
	
	public static void textToFile(String s, File file) throws IOException
	{
		FileWriter fw = new FileWriter(file);
		fw.write(s);
		fw.flush();
		fw.close();
	}
	
	
	public static String fileToText(File file) throws IOException
	{
		FileReader fr = new FileReader(file);
		StringBuilder sb = new StringBuilder();
		LineNumberReader lnr = new LineNumberReader(fr);
		String line = null;
		while ((line = lnr.readLine()) != null)
			sb.append(line.trim() + "\n");
		return sb.toString();
	}
}
