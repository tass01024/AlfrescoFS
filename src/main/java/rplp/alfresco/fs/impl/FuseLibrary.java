package rplp.alfresco.fs.impl;

import com.sun.jna.Native;

/*fuse*/
public class FuseLibrary {
	static {
		Native.register("fuse");
	}
//	public static native int fuse_main	(
//			int  	argc,
//			char *  	argv[],
//			const struct fuse_operations *  	op,
//			 	user_data 
//		);
//	int fuse_main	( 	int  	argc,
//			char *  	argv[],
//			const struct fuse_operations *  	op,
//			void *  	user_data 
//		); 	
//	public static native Passwd getpwuid(int which);
// 
//	public static native Passwd getpwnam(String name);
}
