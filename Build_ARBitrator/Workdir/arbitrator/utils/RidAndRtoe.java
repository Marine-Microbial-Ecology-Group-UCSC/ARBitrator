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
 *    RidAndRtoe.java
 *    Copyright (C) 2014 Philip Heller
 *
 */


package arbitrator.utils;


//
// After submission of a blast request, the NCBI server replies immediately with a page
// containing an rid (response id) and rtoe (remaining time of execution).
//

public class RidAndRtoe 
{
	public String  	rid;				// response ID
	public int 		rtoe;				// estimated # secs until response is ready
		
	public String toString()	{ return "rid=" + rid + " rtoe=" + rtoe + " secs"; }
}
