package com.nutanix;

import java.nio.ByteBuffer;

public class FileCacheEntry {
	
	private final static int MAX_FILE_SIZE = 10240;
	private String fileName;
	private int refCount;
	ByteBuffer buf;
	boolean dirty;
	boolean markedToDelete;
	long dirtyStartTime;
	
	protected FileCacheEntry() {
	}
	protected  FileCacheEntry(String fileName, char[] buf) {
		this.fileName = new String(fileName);
		refCount = 0;
		dirty = false;
		markedToDelete = false;
		this.buf = ByteBuffer.allocate(2 * MAX_FILE_SIZE);
		this.buf.asCharBuffer().put(buf);
	}
	
	
	protected  void reset(String fileName, char[] buf) {
		this.fileName = new String(fileName);
		refCount = 0;
		dirty = false;
		markedToDelete = false;
		/*Create a new ByteBuffer, because, earlier threads even though they might have unpinned the files, 
		 * they might still use the Bytebuffers that were returned to them.
		 *  */
		this.buf = ByteBuffer.allocate(2 * MAX_FILE_SIZE);
		this.buf.asCharBuffer().put(buf);
	}
		
	protected int getRefCount() {
		return refCount;
	}
	
	protected void incrementRefCount() {
		refCount++;
		System.out.println(fileName +" : Incremented the refcount :"+refCount);
	}
	
	protected void decrementRefCount() {
		refCount--;
	}
	
	protected void setDirty() {
		dirtyStartTime = System.currentTimeMillis();
		dirty = true;
	}
	
	protected void setClean() {
		dirty = false;
	}
	
	protected boolean isDirty() {
		return dirty;
	}
	
	protected ByteBuffer getReadBuf() {
		return buf.asReadOnlyBuffer();
	}
	
	protected ByteBuffer getWriteBuf() {
		if(!isDirty()) {
			setDirty();
		}
		return buf.duplicate();
	}
	
	protected long getDirtyStartTime() {
		return dirtyStartTime;
	}
	
	protected void markToDelete() {
		markedToDelete = true;
	}
	
	protected boolean isMarkedToDelete() {
		return markedToDelete;
	}
}
