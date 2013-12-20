package rplp.alfresco.fs.impl;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;
 
interface CLibrary extends Library 
{
  static CLibrary INSTANCE = (CLibrary) Native.loadLibrary((Platform.isWindows() ? "msvcrt" : "c"), CLibrary.class);
  //void printf(String format, Object... args);
  int printf(String format, Object... args);
}
 
public class TestJNA 
{
	  public static void main(String[] args) throws InterruptedException 
	  {
		  CLibrary.INSTANCE.printf("Warm up 1\n");
		  System.out.print("Warm up 2\n");
		  
	    int TOTAL = 1000;
	 
	    long start = System.currentTimeMillis();
	 
	    for(int i = 0; i < TOTAL; i++)
	    {
	      CLibrary.INSTANCE.printf("Hello, World 1\n");
	    }
	 
	    long start2 = System.currentTimeMillis();
	 
	    for(int i = 0; i < TOTAL; i++)
	    {
	      System.out.print("Hello, World 2\n");
	    }
	 
	    long end = System.currentTimeMillis();
	    System.out.flush();
	 
	    Thread.sleep(1000);
	 
	    System.out.println("***** ratio: ***** " + ((double)(start2-start))/(double)(end-start2));
	  }
}

