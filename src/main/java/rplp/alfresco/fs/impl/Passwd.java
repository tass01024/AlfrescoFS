package rplp.alfresco.fs.impl;

import java.util.Arrays;
import java.util.List;

import com.sun.jna.Structure;

public class Passwd extends Structure {

    public String pw_name; /* getlogin() */
    public String pw_passwd; /* "" */
    public int pw_uid; /* getuid() */
    public int pw_gid; /* getgid() */
    public String pw_gecos; /* getlogin() */
    public String pw_dir; /* "/" or getenv("HOME") */
    public String pw_shell; /* "/bin/sh" or getenv("SHELL") */

    @Override
    @SuppressWarnings("rawtypes")
    protected List getFieldOrder() {
            return Arrays.asList(new String[] {
                    "pw_name", "pw_passwd", "pw_uid", "pw_gid", "pw_gecos", "pw_dir", "pw_shell",
            });
    }

}