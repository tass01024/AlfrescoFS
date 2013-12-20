package rplp.alfresco.fs.impl;

import java.util.Arrays;
import java.util.List;

import com.sun.jna.Callback;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;

@SuppressWarnings("unused")
public class FuseOperations extends Structure {
	 public static class ByReference extends FuseOperations implements Structure.ByReference
	 {
	 }
    @Override
    @SuppressWarnings("rawtypes")
    protected List getFieldOrder() {
            return Arrays.asList(new String[] {
                    "pw_name", "pw_passwd", "pw_uid", "pw_gid", "pw_gecos", "pw_dir", "pw_shell",
            });
    }
	 public Callback getattr = new Callback()
	 {
	 public int callback(final String path,  final Pointer stat)
	 {
	 System.out.println("getattr was called");
	 return 0;
	 }
	 };
	 public Callback readlink = null;
	 public Callback mknod = null;
	 public Callback mkdir = null;
	 public Callback unlink = null;
	 public Callback rmdir = null;
	 public Callback symlink = null;
	 public Callback rename = null;
	 public Callback link = null;
	 public Callback chmod = null;
	 public Callback chown = null;
	 public Callback truncate = null;
	 public Callback utime = null;
	 public Callback open = new Callback()
	 {
	 public int callback(final String path,  final Pointer info)
	 {
	 System.out.println("open was called");
	 return 0;
	 }
	 };
	 public Callback read = new Callback()
	 {
	 public int callback(final String path,  final Pointer buffer,  final long size,  final long offset,  final Pointer fi)
	 {
	 System.out.println("read was called");
	 return 0;
	 }
	 };
	 public Callback write = null;
	 public Callback statfs = null;
	 public Callback flush = null;
	 public Callback release = null;
	 public Callback fsync = null;
	 public Callback setxattr = null;
	 public Callback getxattr = null;
	 public Callback listxattr = null;
	 public Callback removexattr = null;
	 public Callback opendir = null;
	 public Callback readdir = new Callback()
	 {
	 public int callback(final String path,  final Pointer buffer,  final Pointer filler,  final long offset, 
	 final Pointer fi)
	 {
	 System.out.println("readdir was called");
	 return 0;
	 }
	 };
	 public Callback releasedir = null;
	 public Callback fsyncdir = null;
	 public Callback init = null;
	 public Callback destroy = null;
	 public Callback access = null;
	 public Callback generate = null;
	 public Callback ftruncate = null;
	 public Callback fgetattr = null;
	 public Callback lock = null;
	 public Callback utimens = null;
	 public Callback bmap = null;
	 public int flag_nullpath_ok;
	 public int flag_reserved;
	 public Callback ioctl = null;
	 public Callback poll = null;
	 }

//	 public static void main(final String args)
//	 {
//	 final String actualArgs = { "-f",  "/some/mount/point" };
//	 final Fuse fuse = (Fuse) Native.loadLibrary("fuse",  Fuse.class);
//	 final FuseOperations.ByReference operations = new FuseOperations.ByReference();
//	 System.out.println("Mounting");
//	 final int solution = fuse.fuse_main_real(actualArgs.length  actualArgs  operations  operations.size()  null);
//	 System.out.println("solution: " + solution);
//	 System.out.println("Mounted");
//	 }
	