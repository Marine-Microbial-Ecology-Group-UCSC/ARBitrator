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
 *    LineIterator.java
 *    Copyright (C) 2014 Philip Heller
 *
 */


package arbitrator.utils;

import java.util.*;


//
// I don't remember why I use this rather than StringReader. I think I had plans for the peek() method.
//


public class LineIterator 
{
	private String 			source;
	private int 			cursor;
	private boolean			done;
	private Stack<String>	pushBackStack;
	
	
	public LineIterator(String source)
	{
		this.source = source;
		done = source.length() == 0;
		pushBackStack = new Stack<String>();
	}
	
	
	public boolean hasNextLine()		{ return !done; }
	
	
	public String nextLine()
	{
		if (done)
			return null;
		
		if (!pushBackStack.isEmpty())
			return pushBackStack.pop();
		
		int nextCursor = cursor + 1;
		while (nextCursor < source.length()  &&  source.charAt(nextCursor) != '\n')
			nextCursor++;
		if (nextCursor >= source.length())
			done = true;
		String ret =  done  ?  
				      source.substring(cursor)  :  
				      source.substring(cursor, nextCursor); 
		if (ret.endsWith("\n"))
			return ret.substring(0, ret.length()-1);
		cursor = nextCursor  + 1;
		return ret;
	}
	
	
	public String readLine()
	{
		return nextLine();
	}
	
	
	public String peek()
	{
		String ret = nextLine();
		pushBackStack.push(ret);
		return ret;
	}
	
	
	public void reset()
	{
		cursor = 0;
		pushBackStack.clear();
	}
	
	
	public static void main(String[] args)
	{
		String s = "abc\ndef\nghijk";
		LineIterator liter = new LineIterator(s);
		while (liter.hasNextLine())
			System.out.println("** " + liter.nextLine());
	}
}
