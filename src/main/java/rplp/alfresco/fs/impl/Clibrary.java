package rplp.alfresco.fs.impl;

import com.sun.jna.Native;
import com.sun.jna.Platform;

public class Clibrary {
	static {
		Native.register(Platform.C_LIBRARY_NAME);
	}
	public static native Passwd getpwuid(int which);
 
	public static native Passwd getpwnam(String name);
}

//private C c;
//
//private static interface C extends Library {
//
//	/*check unsafe for performance: http://mishadoff.github.io/blog/java-magic-part-4-sun-dot-misc-dot-unsafe/*/
//	
//	/*direct mapped should be quicker
//* public class CLibrary {
//static {
//Native.register("c");
//}
//}
//* */
//    public Passwd getpwuid(int which);
//    
//    public Passwd getpwnam(String name);
//}

