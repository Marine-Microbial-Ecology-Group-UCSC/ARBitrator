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

package arbitrator.utils;

import java.io.*;


public class SystemCaller 
{
	private String					commandLine;
	private Process					proc;
	private InputStream				is;
	private BufferedInputStream		bis;
	private InputStreamReader		isr;
	private BufferedReader			br;
	
	
	public SystemCaller(String commandLine)
	{
		this.commandLine = commandLine;
	}
	
	
	public void execute() throws IOException
	{
		proc = Runtime.getRuntime().exec(commandLine);
		is = proc.getInputStream();
		bis = new BufferedInputStream(is);
		isr = new InputStreamReader(bis);
		br = new BufferedReader(isr);
	}
	
	
	public String readStdoutLine() throws IOException
	{
		return br.readLine();
	}
	
	
	public int exitValue() throws IllegalThreadStateException
	{
		return proc.exitValue();
	}
	
	
	// Returns the exit value.
	public int blockUntilCompletion() throws InterruptedException
	{
		return proc.waitFor();
	}
	
	
	public void close() throws IOException
	{
		br.close();
		isr.close();
		bis.close();
		is.close();
	}
	
	
	static void sop(Object x)		{ System.out.println(x); }
	
	
	public static void main(String[] args) 
	{
		SystemCaller that = new SystemCaller("ls -l");
		try
		{
			that.execute();
			String line = null;
			while ((line = that.readStdoutLine()) != null)
				sop("STDOUT: " + line);
			that.blockUntilCompletion();
			sop("Exit code: " + that.exitValue());
			that.close();
		}
		catch (Exception x)
		{
			sop("YIKES: " + x.getMessage());
			x.printStackTrace();
		}
	}
}
