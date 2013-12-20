package rplp.alfresco.fs.impl;
import com.sun.jna.Native;
import com.sun.jna.Platform;
 
public class TestJNADirect 
{
  public static void main(String[] args) throws InterruptedException 
  {
    //HelloJNA hj = new HelloJNA();
    HelloJNA.printf("Warm up 1\n");
    System.out.print("Warm up 2\n");
    int TOTAL = 1000;
 
    long start = System.currentTimeMillis();
 
    for(int i = 0; i < TOTAL; i++)
    {
    	HelloJNA.printf("Hello, World 1\n");
    }
 
    long end = System.currentTimeMillis();
    long time1 = end - start;
 
    long start2 = System.currentTimeMillis();
 
    for(int i = 0; i < TOTAL; i++)
    {
      System.out.print("Hello, World 2\n");
    }
 
    long end2 = System.currentTimeMillis();
    long time2 = end2 -start2;
    System.out.flush();
    System.out.println("***** ratio: ***** " + ((double)(time1))/((double)(time2)));
  }
}
 
class HelloJNA 
{
  //public static native void printf(String format, Object... args);
  public static native int printf(String format);
 
  static
  {
    Native.register((Platform.isWindows() ? "msvcrt" : "c"));
  }
}