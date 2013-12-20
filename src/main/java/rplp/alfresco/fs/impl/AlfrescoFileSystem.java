package rplp.alfresco.fs.impl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.io.Writer;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.fusejna.DirectoryFiller;
import net.fusejna.ErrorCodes;
import net.fusejna.FlockCommand;
import net.fusejna.FuseException;
import net.fusejna.FuseFilesystem;
import net.fusejna.FuseJna;
import net.fusejna.StructFlock.FlockWrapper;
import net.fusejna.StructFuseFileInfo.FileInfoWrapper;
import net.fusejna.StructStat.StatWrapper;
import net.fusejna.StructStatvfs.StatvfsWrapper;
import net.fusejna.StructTimeBuffer.TimeBufferWrapper;
import net.fusejna.XattrListFiller;
import net.fusejna.types.TypeMode.ModeWrapper;
import net.fusejna.types.TypeMode.NodeType;
import net.sf.acegisecurity.Authentication;
import nl.runnable.alfresco.annotations.Transactional;
import nl.runnable.alfresco.webscripts.annotations.AuthenticationType;
import nl.runnable.alfresco.webscripts.annotations.HttpMethod;
import nl.runnable.alfresco.webscripts.annotations.Uri;
import nl.runnable.alfresco.webscripts.annotations.WebScript;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.model.Repository;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.cmr.lock.LockService;
import org.alfresco.service.cmr.lock.LockType;
import org.alfresco.service.cmr.model.FileFolderService;
import org.alfresco.service.cmr.model.FileInfo;
import org.alfresco.service.cmr.model.FileNotFoundException;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.ContentData;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.search.SearchService;
import org.alfresco.service.cmr.security.OwnableService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.namespace.RegexQNamePattern;
import org.alfresco.util.ISO9075;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.extensions.webscripts.WebScriptResponse;
import org.springframework.stereotype.Component;
//import net.sf.acegisecurity.Authentication;

import com.sun.jna.Pointer;

@Component
@WebScript(description = "Shows the entire category tree.", defaultFormat = "html", families = { "Alfresco FS"})
@nl.runnable.alfresco.webscripts.annotations.Authentication(AuthenticationType.ADMIN)
public class AlfrescoFileSystem extends FuseFilesystem implements InitializingBean, DisposableBean {
	private static Logger LOG = Logger.getLogger(AlfrescoFileSystem.class);

    @Autowired
    private SearchService search;
    @Autowired
    private ContentService content;
    @Autowired
    private NodeService nodeService;
    @Autowired
    private FileFolderService fileFolderService;
    @Autowired
    private DictionaryService dictionaryService;
    @Autowired
    private NamespaceService namespaceService;
	@Autowired
	private OwnableService ownableService;
	@Autowired
	private LockService lockService;
    

    @Autowired
    private Repository repository;
	// get nodeRef of company home
	NodeRef home;
	
	
    private ExecutorService main = Executors.newSingleThreadExecutor();
    public AlfrescoFileSystem() { }
    
	@Override
	public void afterPropertiesSet() throws Exception {
		//mount();
		LOG.setLevel(Level.TRACE);
		LOG.info("AlfrescoFileSystem.afterPropertiesSet");
		home = repository.getCompanyHome();
		File mountPoint = getConfiguredMountPoint();
		FuseJna.unmount(mountPoint);

		mountFileSystem();
	}

	@Uri(method = HttpMethod.GET, value = "/mount", defaultFormat = "html")
	public void mountWebscript(WebScriptResponse webResponse) throws Exception {
		
		mountFileSystem();
		webResponse.getWriter().write("mounted!");
		webResponse.getWriter().close();
	}

	private void mountFileSystem() throws IOException, FuseException {
		
		final File mountPoint = getConfiguredMountPoint();
		LOG.info("Mounting AlfrescoFS in " + mountPoint.getAbsolutePath());
		FuseUtil.prepareMountpoint(mountPoint);
		LOG.info("prepared mount of AlfrescoFS, wait termination");
		main.execute(new Runnable() {
			public void run() {
				try {
					AlfrescoFileSystem.this.log(true).mount(mountPoint);
				} catch (FuseException fe) {
					LOG.error("Error during mount", fe);
				}
			}
		});
		
	}

	private File getConfiguredMountPoint() {
		return new File("/tmp/AlfrescoFS");
	}
	
	@Uri(method = HttpMethod.GET, value = "/unmount", defaultFormat = "html")
	public void unmountWebscript(WebScriptResponse webResponse) throws Exception {
		File mountPoint = this.getMountPoint();
		if (null != mountPoint) {
			LOG.info("Unmounting AlfrescoFS in " + mountPoint.getAbsolutePath());

			this.unmount();
		} else {
			LOG.info("No mount point AlfrescoFS");
		}
		
		final Writer out = webResponse.getWriter();
		try {
			out.write("unmounted!");
		} finally {
			IOUtils.closeQuietly(out);
		}
	}

//	public interface Passwd {
//	    public String getLoginName();
//	    public String getPassword();
//	    public long getUID();
//	    public long getGID();
//	    public int getPasswdChangeTime();
//	    public String getAccessClass();
//	    public String getGECOS();
//	    public String getHome();
//	    public String getShell();
//	    public int getExpire();
//	}
//	public abstract class NativePasswd extends Structure implements Passwd {
//		
//	}



	

	protected static String getLogin(int which) {
	    try {
	        Passwd passwd = Clibrary.getpwuid(which);
	        if (null != passwd) {
	        	LOG.info("Got login");
	        	return passwd.pw_name;
	        }
	    } catch (Exception e) {
	    	LOG.error("Exception getting username", e);
	    }
	    return null;
	}
	
    @Override
    public int access(final String path, final int access) { 
    	if (LOG.isDebugEnabled()) LOG.debug("int access(final String path, final int access) - path:" + path + ", access:" + access);
    	return -ErrorCodes.ENOSYS(); }

    @Override
    public void afterUnmount(final File mountPoint) { }

    @Override
    public void beforeMount(final File mountPoint) { }

    @Override
    public void beforeUnmount(final File mountPoint) { }

    @Override
    public int bmap(final String path, final FileInfoWrapper info) { 
    	if (LOG.isDebugEnabled()) LOG.debug("int bmap(final String path, final FileInfoWrapper info) - path:" + path);
    	return 0; }
    @Transactional
    @Override
    public int chmod(final String path, final ModeWrapper mode) { return -ErrorCodes.ENOSYS(); }
    @Transactional
    @Override
    public int chown(final String path, final long uid, final long gid) { 
    	if (LOG.isDebugEnabled()) LOG.debug("int chown - path:" + path);
    	
    	runAsUser();
    	
    	FileInfo fi = resolvePathFileInfo(path);
    	if (null == fi) {
    		LOG.error("Path not found - " + path);
    		return -ErrorCodes.ENOENT();
    	}
    	if (LOG.isDebugEnabled()) LOG.debug("FileInfo - name: " + fi.getName() +  ", nodeRef:" + fi.getNodeRef());
    	
		Passwd passwd = Clibrary.getpwuid((int) uid);
		if (null != passwd) {
			ownableService.setOwner(fi.getNodeRef(), passwd.pw_name);
			if (LOG.isDebugEnabled()) LOG.debug("Owner set: " + passwd.pw_name);
			return 0;
		}
    	
    	return -ErrorCodes.EUSERS(); }
    @Transactional
    @Override
    public int create(final String path, final ModeWrapper mode, final FileInfoWrapper info) { 
    	
    	if (LOG.isDebugEnabled()) LOG.debug("int create(final String path, final ModeWrapper mode, final FileInfoWrapper info) - path:" + path);
    	return createNode(path, ContentModel.TYPE_CONTENT); }

    @Override
    public void destroy() { 
    	File mountPoint = getConfiguredMountPoint();
    	try {
    		
    		FuseJna.unmount(mountPoint);
    	} catch (IOException ioe) {
    		//ignore
    	}
    	if (LOG.isDebugEnabled()) LOG.debug("destroy()"); }

    @Override
    public int fgetattr(final String path, final StatWrapper stat, final FileInfoWrapper info)
    {
    	if (LOG.isDebugEnabled()) LOG.debug("int fgetattr(final String path, final StatWrapper stat, final FileInfoWrapper info) - path:" + path);
            return getattr(path, stat);
    }
    @Transactional
    @Override
    public int flush(final String path, final FileInfoWrapper info) { 
    	if (LOG.isDebugEnabled()) LOG.debug("int flush(final String path, final FileInfoWrapper info) - path:" + path);
//runAsUser();
//    	
//    	FileInfo fi = resolvePathFileInfo(path);
//    	if (null == fi) {
//    		LOG.error("Path not found - " + path);
//    		return -ErrorCodes.EFAULT();
//    	}
//    	if (LOG.isDebugEnabled()) LOG.debug("FileInfo - name: " + fi.getName() +  ", nodeRef:" + fi.getNodeRef());
//    	if (fi.isFolder()) {
//    		return -ErrorCodes.EISDIR();
//    	}
//    	try {
//    			ContentWriter writer = content.getWriter(fi.getNodeRef(), ContentModel.PROP_CONTENT, true);
//	    		
//    			FileChannel fileChannel = null;
//    			try {
//	    			fileChannel = writer.getFileChannel(false);
//	
//	    			fileChannel.force(true);
//	
//	                return 0;
//	    		} finally {
//	    			if (null != fileChannel) fileChannel.close();
//	    		}
//	    		
//    	} catch (Exception e) {
//    		return -ErrorCodes.EFAULT();
//    	}
    	return 0;
    }

    @Override
    public int fsync(final String path, final int datasync, final FileInfoWrapper info) { 
    	if (LOG.isDebugEnabled()) LOG.debug("int fsync - path:" + path);
    	return 0; }

    @Override
    public int fsyncdir(final String path, final int datasync, final FileInfoWrapper info) { 
    	if (LOG.isDebugEnabled()) LOG.debug("int fsyncdir - path:" + path);
    	return 0; }
    @Transactional
    @Override
    public int ftruncate(final String path, final long offset, final FileInfoWrapper info)
    {
    	if (LOG.isDebugEnabled()) LOG.debug("int ftruncate - path:" + path);
            return truncate(path, offset);
    }

    @Override
    public int getattr(final String path, final StatWrapper stat) { 
    	if (LOG.isDebugEnabled()) LOG.debug("int getattr - path:" + path + ", stat: " + stat);
    	runAsUser();
    	
    	FileInfo fi = resolvePathFileInfo(path);
    	if (null == fi) {
    		//stat.setMode(StructFuseFileInfo.O_CREAT);
    		LOG.error("Path not found - " + path);
    		return -ErrorCodes.ENOENT();
    	}
    	if (LOG.isDebugEnabled()) LOG.debug("FileInfo - name: " + fi.getName() +  ", nodeRef:" + fi.getNodeRef());
    	
    	try {
	    	stat.ctime(fi.getCreatedDate().getTime());
	    	stat.mtime(fi.getModifiedDate().getTime());
	    	Date accessed = (Date) fi.getProperties().get(ContentModel.PROP_ACCESSED);
	    	if (null != accessed) {
	    		stat.atime(accessed.getTime());
	    	}
	    	stat.blksize(4096); //why
	    	
	    	//8c94d0c6-143a-49a9-9bff-e33703251da0
	    	BigInteger bi = new BigInteger(fi.getNodeRef().getId().replaceAll("-", ""), 16);
	    	stat.ino(bi.longValue());
	    	
	    	String owner = ownableService.getOwner(fi.getNodeRef());
	    	if (null != owner) {
	    		if (LOG.isDebugEnabled()) LOG.debug("Owner: " + owner);
	    		Passwd passwd = Clibrary.getpwnam(owner);
	    		if (null != passwd) {
	    			stat.uid(passwd.pw_uid);
	    			stat.gid(passwd.pw_gid);
	    		}
	    	}
	    	if (fi.isLink()) {
//	    		stat.mode(NodeType.SYMBOLIC_LINK.getBits() | 664);
	    		stat.setMode(NodeType.SYMBOLIC_LINK,true,true,true,true,true,true,true,false,true);
	    		stat.nlink(1);
	    	} else if (fi.isFolder()) {
	    		//stat.mode(NodeType.DIRECTORY.getBits() | 775);
	    		stat.setMode(NodeType.DIRECTORY,true,true,true,true,true,true,true,false,true);
	    		stat.nlink(2); //Why
	    	} else {
	    		// file
		    	ContentData contentData = fi.getContentData();
		    	final long size = null !=  contentData ? contentData.getSize() : 0;
	    		stat.setMode(NodeType.FILE,true,true,false,true,true,false,true,false,false).size(size);
	    		stat.blocks(1 + (size / 512L));
		    	
	    	}
    	} catch (Exception e) {
    		LOG.error("Exception error during getAttr", e);
    	}
    	if (LOG.isDebugEnabled()) LOG.debug("return 0 - getattr - path:" + path + ", stat: " + stat);
		return 0;
    }

    @Override
    protected String getName() { return "AlfrescoFS"; }

    @Override
    protected String[] getOptions() { 
    	if (LOG.isDebugEnabled()) LOG.debug("getOptions)");
    	return null; }

    @Override
    public int getxattr(final String path, final String xattr, final ByteBuffer buf, final long size, final long position) { 
    	if (LOG.isDebugEnabled()) LOG.debug("int getxattr - path:" + path);
    	runAsUser();
    	
    	FileInfo fi = resolvePathFileInfo(path);
    	if (null == fi) {
    		LOG.error("Path not found - " + path);
    		return -ErrorCodes.EFAULT();
    	}
    	if (LOG.isDebugEnabled()) LOG.debug("FileInfo - name: " + fi.getName() +  ", nodeRef:" + fi.getNodeRef());
    	
    	for (Map.Entry<QName, Serializable> entry: fi.getProperties().entrySet()) {
    		if (entry.getKey().toPrefixString(namespaceService).equals(xattr)) {
    			buf.put(entry.getValue().toString().getBytes());
    			return 0;
    		}
    	}
    	return -ErrorCodes.ENOSYS(); 
    }

    @Override
    public void init() { 
    	if (LOG.isDebugEnabled()) LOG.debug("init");
    	
    }
    @Transactional
    @Override
    public int link(final String path, final String target) { 
    	if (LOG.isDebugEnabled()) LOG.debug("int link - path:" + path);
    	//hardlink
    	return -ErrorCodes.ENOSYS(); }

    @Override
    public int listxattr(final String path, final XattrListFiller filler) { 
    	if (LOG.isDebugEnabled()) LOG.debug("int listxattr - path:" + path);
    	runAsUser();
    	
    	FileInfo fi = resolvePathFileInfo(path);
    	if (null == fi) {
    		LOG.error("Path not found - " + path);
    		return -ErrorCodes.EFAULT();
    	}
    	if (LOG.isDebugEnabled()) LOG.debug("FileInfo - name: " + fi.getName() +  ", nodeRef:" + fi.getNodeRef());
    	for (QName qname : fi.getProperties().keySet()) {
    		filler.add(qname.toPrefixString(namespaceService));
    	}
    	return 0;
    	//return -ErrorCodes.ENOSYS(); 
    	}
    @Transactional
    @Override
    public int lock(final String path, final FileInfoWrapper info, final FlockCommand command, final FlockWrapper flock) { 
    	if (LOG.isDebugEnabled()) LOG.debug("int lock - path:" + path);
    	
    	runAsUser();
    	
    	FileInfo fi = resolvePathToParent(path);
    	if (null == fi) {
    		LOG.error("Path not found - " + path);
    		return -ErrorCodes.EFAULT();
    	}
    	if (LOG.isDebugEnabled()) LOG.debug("FileInfo - name: " + fi.getName() +  ", nodeRef:" + fi.getNodeRef());
    	
    	
		String owner = getLogin((int) info.lock_owner());
		
    	switch(command) {
    		case GET_LOCK : ;
    		case SET_LOCK : ;
    		case SET_LOCK_WRITE : ;
    	}
    	
    	final LockType lockType;
    	switch (flock.flockType()) {
    		case NO_LOCK : 
    			lockType = LockType.NODE_LOCK; 
    			break;
    		case READ_LOCK : 
    			lockType = LockType.READ_ONLY_LOCK;
    			break;
    		case WRITE_LOCK : 
    			lockType = LockType.WRITE_LOCK;
    			break;
    		default : 
    			return -ErrorCodes.EFAULT();
    	}
    	
    	lockService.lock(fi.getNodeRef(), lockType);
    	
    	return -ErrorCodes.ENOSYS(); }

    @Transactional
    @Override
    public int mkdir(final String path, final ModeWrapper mode) { 
    	if (LOG.isDebugEnabled()) LOG.debug("int mkdir - path: " + path + " mode: " + mode);
    	
    	return createNode(path, ContentModel.TYPE_FOLDER);
    }
    
	private final int createNode(final String path, QName type) {
		runAsUser();
    	
    	FileInfo fi = resolvePathToParent(path);
    	if (null == fi) {
    		LOG.error("Path not found - " + path);
    		return -ErrorCodes.EFAULT();
    	}
    	if (LOG.isDebugEnabled()) LOG.debug("FileInfo - name: " + fi.getName() +  ", nodeRef:" + fi.getNodeRef());
    	if (fi.isFolder()) {
    		FileInfo dir = null;
    		try {
    			dir = fileFolderService.create(fi.getNodeRef(), getLastNode(removeSlashes(path)), type);
    		} catch (Exception e) {
    			LOG.error("Could not create path: " + path, e);
    			return -ErrorCodes.EFAULT();
    		}
    		
    		if (null == dir) {
    			LOG.error("Could not create path: " + path);
    			return -ErrorCodes.EFAULT();
    		} else {
    			if (LOG.isDebugEnabled()) LOG.debug("Created FileInfo - name: " + dir.getName() +  ", nodeRef:" + dir.getNodeRef());
    		}
    	} else {
    		LOG.error("Parent is not a folder, path: " + path);
    		return -ErrorCodes.ENOTDIR();
    	}
    	
    	return 0;
	}
    @Transactional
    @Override
    public int mknod(final String path, final ModeWrapper mode, final long dev)
    {
    	if (LOG.isDebugEnabled()) LOG.debug("int mknod - path:" + path + " mode: " + mode + " dev: " + dev);
            return create(path, mode, null);
    }

    @Override
    public int open(final String path, final FileInfoWrapper info) {
    	long pointer = Pointer.nativeValue(getFuseContext().getPointer());
    	if (LOG.isDebugEnabled()) LOG.debug("int open- path:" + path + " info: " + info + " fuse ctx: " + pointer);
    	
    	
    	return 0; }

    @Override
    public int opendir(final String path, final FileInfoWrapper info) { 
    	long pointer = Pointer.nativeValue(getFuseContext().getPointer());
    	if (LOG.isDebugEnabled()) LOG.debug("int opendir - path:" + path + " info: " + info + " fuse ctx: " + pointer);
//    	info.append(true);
//    	info.create(true);
//    	info.direct_io(false);
//    	info.fh(arg0);
//    	info.fh_old(arg0);
//    	info.flockrelease(false);
//    	info.flush(true);
//    	info.keep_cache(true);
//    	info.lock_owner(true);
//    	info.nonseekable(false);
//    	info.openMode(OpenMode.READONLY);
    	return 0; }
 
	@Override
    public int read(final String path, final ByteBuffer buffer, final long size, final long offset, final FileInfoWrapper info)
    {
		long pointer = Pointer.nativeValue(getFuseContext().getPointer());
		if (LOG.isDebugEnabled()) LOG.debug("int read - path:" + path+ " fuse ctx: " + pointer);
    	runAsUser();
    	
    	FileInfo fi = resolvePathFileInfo(path);
    	if (null == fi) {
    		LOG.error("Path not found - " + path);
    		return -ErrorCodes.EFAULT();
    	}
    	if (LOG.isDebugEnabled()) LOG.debug("FileInfo - name: " + fi.getName() +  ", nodeRef:" + fi.getNodeRef());
    	if (fi.isFolder()) {
    		return -ErrorCodes.EISDIR();
    	}
    	final int MAX_READ = 1024*1024; //1Mb
    	try {
	    		ContentReader reader = content.getReader(fi.getNodeRef(), ContentModel.PROP_CONTENT);
	    		int bytesToRead = (int) Math.min(buffer.limit(), Math.min(MAX_READ, Math.min(reader.getSize() - offset, size)));
	    		
	    		InputStream is = null;
	    		try {
		    		is = reader.getContentInputStream();
		    		
	    			final byte[] bytesRead = new byte[bytesToRead];
	
	                final int nRead = is.read(bytesRead, (int) offset, bytesToRead);
	                
	                if (-1 < nRead) {
	                	buffer.put(bytesRead, 0, nRead);
	                }
	                return nRead;
	    		} finally {
	    			IOUtils.closeQuietly(is);
	    		}
	    		
    	} catch (Exception e) {
    		return -ErrorCodes.EFAULT();
    	}
    }

	private void runAsUser() {
		LOG.info("UID: " + getFuseContextUid().intValue());
		String username = getLogin(getFuseContextUid().intValue());
		LOG.info("username: " + username);
    	Authentication a = org.alfresco.repo.security.authentication.AuthenticationUtil.setRunAsUser(username);
    	LOG.info("using Alfresco user: " + a.getName());
	}

    /**
     * Remove slashes from string
     * 
     * @param value input string
     * @return String output string
     */
    public static String removeSlashes(String value)
    {
        value = value.replaceAll("//", "/");

        if (value.startsWith("/"))
            value = value.substring(1);
        if (value.endsWith("/"))
            value = value.substring(0, value.length() - 1);
        return value;
    }

    public static String removeLastNode(String value)
    {
    	final int index = value.lastIndexOf('/');
        
    	if (1 > index) {
    		return null;
    	}
        return value.substring(0, index);
    }
    
    public static String getLastNode(String value)
    {
    	final int index = value.lastIndexOf('/');
        
    	if (1 > index) {
    		return null;
    	}
        return value.substring(index + 1);
    }
    
    
    /**
     * Resolve file info for file with URL path
     *
     * @param initialURL URL path
     * @return FileInfo file info null if file or folder doesn't exist
     */
    public FileInfo resolvePathToParent(String initialURL)
    {
    	LOG.debug("resolvePathToParent");
        initialURL = removeLastNode(removeSlashes(initialURL));
        
        return resolvePath(initialURL);
    }
    /**
     * Resolve file info for file with URL path
     *
     * @param initialURL URL path
     * @return FileInfo file info null if file or folder doesn't exist
     */
    public FileInfo resolvePathFileInfo(String initialURL)
    {
    	LOG.debug("resolvePathFileInfo");
        initialURL = removeSlashes(initialURL);
        
        FileInfo fileInfo = resolvePath(initialURL);

        return fileInfo;
    }

	private FileInfo resolvePath(String initialURL) {
		NodeRef rootNodeRef = home;//getRootNodeRef();
        
        FileInfo fileInfo = null;
        LOG.debug("initialURL: " + initialURL + ", nodeRef: "+ rootNodeRef);
        if (initialURL.length() == 0)
        {
        	try {
        		fileInfo = fileFolderService.getFileInfo(rootNodeRef);
        	} catch (Exception e) {
        		LOG.error("could not get file info", e);
        	}
        }
        else
        {
            try
            {
                List<String> splitPath = Arrays.asList(initialURL.split("/"));
                fileInfo = fileFolderService.resolveNamePath(rootNodeRef, splitPath);
            }
            catch (FileNotFoundException e)
            {
            	LOG.error("could not get file info", e);
            }
        }

        if (LOG.isDebugEnabled())
        {
        	if (null != fileInfo) {
        		LOG.debug("Resolved file info for '" + initialURL + "' is " + fileInfo);
        	} else {
        		LOG.debug("Could not resolve '" + initialURL + "'");
        	}
        }
		return fileInfo;
	}
    @Override
    public int readdir(final String path, final DirectoryFiller filler) { 
    	long pointer = Pointer.nativeValue(getFuseContext().getPointer());
    	if (LOG.isDebugEnabled()) LOG.debug("int readdir - path:" + path + " fuse ctx: " + pointer);
    	if (LOG.isDebugEnabled()) LOG.debug("int readdir - ISO9075-path:" + ISO9075.encode(path));
    	//ISO9075.encode(path);
    	runAsUser();
    	
    	
    	FileInfo fi = resolvePathFileInfo(path);
    	if (null == fi) {
    		LOG.error("Path not found - " + path);
    		return -ErrorCodes.EFAULT();
    	}
    	
    	if (LOG.isDebugEnabled()) LOG.debug("FileInfo - name: " + fi.getName() +  ", nodeRef:" + fi.getNodeRef());
    	if (fi.isFolder()) {
    		List<ChildAssociationRef> children = nodeService.getChildAssocs(fi.getNodeRef(), ContentModel.ASSOC_CONTAINS, RegexQNamePattern.MATCH_ALL);
    		for (ChildAssociationRef caf : children) {
    			
    			filler.add((String) nodeService.getProperty(caf.getChildRef(), ContentModel.PROP_NAME));
    			
    		}
    		return 0;
    	}
    	return -ErrorCodes.ENOTDIR();
    }

    @Override
    public int readlink(final String path, final ByteBuffer buffer, final long size) { 
    	if (LOG.isDebugEnabled()) LOG.debug("int readlink - path:" + path);
    	return 0; }

    @Override
    public int release(final String path, final FileInfoWrapper info) { 
    	if (LOG.isDebugEnabled()) LOG.debug("int release - path:" + path);
    	return 0; }

    @Override
    public int releasedir(final String path, final FileInfoWrapper info) { 
    	if (LOG.isDebugEnabled()) LOG.debug("int releasedir - path:" + path);
    	return 0; }
    @Transactional
    @Override
    public int removexattr(final String path, final String xattr) { 
    	if (LOG.isDebugEnabled()) LOG.debug("int removexattr - path:" + path);
    	return 0; }
    @Transactional
    @Override
    public int rename(final String path, final String newName) { 
    	if (LOG.isDebugEnabled()) LOG.debug("int rename - path:" + path);

    	runAsUser();
    	
    	FileInfo fi = resolvePathFileInfo(path);
    	if (null == fi) {
    		LOG.error("Path not found - " + path);
    		return -ErrorCodes.ENOENT();
    	}
    	
    	if (LOG.isDebugEnabled()) LOG.debug("FileInfo - name: " + fi.getName() +  ", nodeRef:" + fi.getNodeRef());
    	
    	try {
    		fileFolderService.rename(fi.getNodeRef(), newName);
    	} catch (FileNotFoundException fnfe) {
    		return -ErrorCodes.ENOENT();
    	}
    	return 0;
    }
    @Transactional
    @Override
    public int rmdir(final String path) { 
    	if (LOG.isDebugEnabled()) LOG.debug("int rmdir(final String path - path:" + path);

    	runAsUser();
    	
    	FileInfo fi = resolvePathFileInfo(path);
    	if (null == fi) {
    		LOG.error("Path not found - " + path);
    		return -ErrorCodes.ENOENT();
    	}
    	
    	if (LOG.isDebugEnabled()) LOG.debug("FileInfo - name: " + fi.getName() +  ", nodeRef:" + fi.getNodeRef());
    	if (fi.isFolder()) {
    		fileFolderService.delete(fi.getNodeRef());
    		
    		return 0;
    	}
    	return -ErrorCodes.ENOTDIR();
    }
    @Transactional
    @Override
    public int setxattr(final String path, final ByteBuffer buf, final long size, final int flags, final long position) {
    	if (LOG.isDebugEnabled()) LOG.debug("int setxattr(final String path - path:" + path);
    	return -ErrorCodes.ENOSYS(); }

    @Override
    public int statfs(final String path, final StatvfsWrapper stat) { 
    	if (LOG.isDebugEnabled()) LOG.debug("int statfs(final String path - path:" + path);
    	
    	runAsUser();
    	
    	FileInfo fi = resolvePathFileInfo(path);
    	if (null == fi) {
    		//stat.setMode(StructFuseFileInfo.O_CREAT);
    		LOG.error("Path not found - " + path);
    		return -ErrorCodes.ENOENT();
    	}
    	if (LOG.isDebugEnabled()) LOG.debug("FileInfo - name: " + fi.getName() +  ", nodeRef:" + fi.getNodeRef());
    	
    	try {
//    	stat.blksize(4096); //why
    	
    	//8c94d0c6-143a-49a9-9bff-e33703251da0
    	if (!(fi.isFolder() || fi.isLink())) {
    		// file
	    	ContentData contentData = fi.getContentData();
	    	final long size = null !=  contentData ? contentData.getSize() : 0;
    		stat.blocks(1 + (size / 512L));
    	}
    	} catch (Exception e) {
    		LOG.error("Exception error during getAttr", e);
    	}
    	if (LOG.isDebugEnabled()) LOG.debug("return 0 - getattr - path:" + path + ", stat: " + stat);
		return 0; 
    }

    @Transactional
    @Override
    public int symlink(final String path, final String target) { 
    	if (LOG.isDebugEnabled()) LOG.debug("int symlink(final String path - path:" + path);
    	return 0; }

    @Transactional
    @Override
    public int truncate(final String path, final long offset) { 
    	if (LOG.isDebugEnabled()) LOG.debug("int truncate(final String path  - path:" + path);
    	
runAsUser();
    	
    	FileInfo fi = resolvePathFileInfo(path);
    	if (null == fi) {
    		LOG.error("Path not found - " + path);
    		return -ErrorCodes.EFAULT();
    	}
    	if (LOG.isDebugEnabled()) LOG.debug("FileInfo - name: " + fi.getName() +  ", nodeRef:" + fi.getNodeRef());
    	if (fi.isFolder()) {
    		return -ErrorCodes.EISDIR();
    	}
    	try {
    			ContentWriter writer = content.getWriter(fi.getNodeRef(), ContentModel.PROP_CONTENT, true);
	    		
    			FileChannel fileChannel = null;
    			try {
	    			fileChannel = writer.getFileChannel(false);
	
	    			fileChannel.truncate(offset);
	
	    			fileChannel.force(false);
	                
	                return 0;
	    		} finally {
	    			if (null != fileChannel) fileChannel.close();
	    		}
	    		
    	} catch (Exception e) {
    		return -ErrorCodes.EFAULT();
    	}
    }

    @Transactional
    @Override
    public int unlink(final String path) { 
    	if (LOG.isDebugEnabled()) LOG.debug("int unlink(final String path - path:" + path);
    	//rm
    	runAsUser();
    	
    	FileInfo fi = resolvePathFileInfo(path);
    	if (null == fi) {
    		LOG.error("Path not found - " + path);
    		return -ErrorCodes.ENOENT();
    	}
    	
    	if (LOG.isDebugEnabled()) LOG.debug("FileInfo - name: " + fi.getName() +  ", nodeRef:" + fi.getNodeRef());
    	if (fi.isFolder()) {
    		return -ErrorCodes.EISDIR();
    	}
    	if (fi.isLink()) {
    		return -ErrorCodes.ENOSYS();
    	}

    	fileFolderService.delete(fi.getNodeRef());
    	return 0;
	}

    @Transactional
    @Override
    public int utimens(final String path, final TimeBufferWrapper wrapper) { 
    	if (LOG.isDebugEnabled()) LOG.debug("int utimens(final String path - path:" + path);
    	return -ErrorCodes.ENOSYS(); }

    @Transactional
    @Override
    public int write(final String path, final ByteBuffer buffer, final long offset, final long writeOffset,
                    final FileInfoWrapper wrapper) { 
    	if (LOG.isDebugEnabled()) LOG.debug("int write(final String path ... - path:" + path);

runAsUser();
    	
    	FileInfo fi = resolvePathFileInfo(path);
    	if (null == fi) {
    		LOG.error("Path not found - " + path);
    		return -ErrorCodes.EFAULT();
    	}
    	if (LOG.isDebugEnabled()) LOG.debug("FileInfo - name: " + fi.getName() +  ", nodeRef:" + fi.getNodeRef());
    	if (fi.isFolder()) {
    		return -ErrorCodes.EISDIR();
    	}
    	try {
    			ContentWriter writer = content.getWriter(fi.getNodeRef(), ContentModel.PROP_CONTENT, true);
	    		
    			FileChannel fileChannel = null;
    			try {
	    			fileChannel = writer.getFileChannel(false);
	
	    			if (0 != writeOffset) {
	    				fileChannel.position(writeOffset);
	    			}
	    			fileChannel.write(buffer);
	
	    			fileChannel.force(false);
	
	    		
	                
	                return 0;
	    		} finally {
	    			if (null != fileChannel) fileChannel.close();
	    		}
	    		
    	} catch (Exception e) {
    		return -ErrorCodes.EFAULT();
    	}
    }

}
