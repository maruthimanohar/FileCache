package com.nutanix;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Calendar;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Vector;


public class MyFileCache extends FileCache {
	
	private final static int MAX_FILE_SIZE = 10240;
	
	private boolean debugEnabled = false;
	/* cache - contains the mapping from fileName to the FileCacheEntry */
	private Map<String, FileCacheEntry> cache;
	private Vector<FileCacheEntry> freeList;
	private Thread bgThread;
	private boolean stopped = false;
	
	private Object lock;
	
	public MyFileCache(final int maxCacheEntries, final int dirtyTimeSecs) {
		
		super(maxCacheEntries, dirtyTimeSecs);
		
		freeList = new Vector<FileCacheEntry>(maxCacheEntries);
		for(int i=0; i< maxCacheEntries; i++) {
			freeList.add(new FileCacheEntry());
		}
		
		cache = new Hashtable<String, FileCacheEntry>();
		lock = new Object();
		
		/*Thread which runs in the back ground, to clean the dirty entries in the cache*/
		bgThread = new Thread() {

			public void run() {
				
				while(true) {
					try{
						sleep(dirtyTimeSecs * 1000);
					} catch(InterruptedException e) {
						// sleep is interrupted, so go and clear dirty pages.
					}
					
					/*Synchronize on lock object to have exclusive access to the cache*/
					synchronized(lock) {
						log("Started the background thread and cleaning the dirty pages by writing them to disk.");
						Set<String> keySet = cache.keySet();
						Iterator<String> keyIter = keySet.iterator();
						while(keyIter.hasNext()) {
							String fileName = keyIter.next();
							FileCacheEntry cEntry = cache.get(fileName);
							
						//	synchronized(cEntry) {
								long currentTime = System.currentTimeMillis();
								if(stopped == true && cEntry.isDirty() || 
										( cEntry.getRefCount() == 0 && cEntry.isDirty() && 
										  (currentTime - cEntry.getDirtyStartTime()) > (dirtyTimeSecs *1000) 
										) ) {
									log("The file " + fileName + " was dirty for more than dirtyTimeSecs, so writing to disc.");
									try{
										BufferedWriter out = new BufferedWriter(new FileWriter(fileName));
										ByteBuffer buf = cEntry.getReadBuf();
										out.write(buf.asCharBuffer().toString());
										out.flush();
										out.close();
									} catch(IOException e) {
										log("Error while writing to the file " + fileName + " on the disk");
										/* Got an IOexception while writing to the file. Ignore error cases for now.*/	
									}
								}
								cEntry.setClean();
								/* We will not delete the cache entry. This will be deleted only when need comes.
								 * that is in the pinfiles method, when there is no free cache.*/
						//	}
						}
						
						lock.notifyAll();
						if(stopped) {
							log("The File cache is stopped. So exiting the background thread");
							break;
						}
					}
					
				}
			}
		};
		bgThread.start();
	}
	
/**
 * @param fileNames : vector of fileNames.
 * 
 * Given a collection of fileNames, pinFiles try to pin these files in the cache one by one.
 * 
 * */
	
	public void pinFiles(Collection<String> fileNames){
		if(fileNames == null)
			return;
		
		synchronized(lock) {
			
			Iterator<String> fileNameIter = fileNames.iterator();
			
			while(fileNameIter.hasNext()) {
				String fileName = fileNameIter.next();
				pinFile(fileName);
			}
		}
	}
	
	/**
	 * 
	 * @param fileName  - file name to be pinned.
	 * Utility method used by pinFiles, to pin each given file.
	 * 
	 * If the file is already in cache -- increment the reference count of this file.
	 * If the file is not in the cache, and there is a free cache entry 
	 *    -- use this cache entry for this file. remove it from the freeList, add it to the hashtable.
	 * If there is no free cache entry i.e; if the freeList is empty:
	 *    -- try to remove a unreferenced and clean file cache entry. and use it.
	 *    -- if none found, wait for the background thread to clear some dirty unpinned file cache entries.
	 */
	/*This method will be called under the synchronization of lock object, no need of synchronized block here*/
	private void pinFile(String fileName) {
		
		FileCacheEntry cEntry = cache.get(fileName);
		if(cEntry != null) {
		//	synchronized(cEntry) {
				cEntry.incrementRefCount();
		//	}
			return ;
		}
		
		if(cEntry == null) {
			cEntry = getFreeCacheEntry();
			if(cEntry == null) {
				/*Try to free a cache Entry, to accommodate this request.*/
				cEntry = tryAndGetAFreeEntry();
			}
			if(cEntry != null) {
			//	synchronized(cEntry) {
					updateCacheEntry(cEntry, fileName);
					cache.put(fileName, cEntry);
					cEntry.incrementRefCount();
			//	}
			}
			 else {
				
				/* wait on MyFileCache until the background thread clears some cache entries*/
				try {
					log("The cache is full, so waiting for the background thread to clear some dirty entries.");
					lock.wait();
				} catch (InterruptedException e) {
					//e.printStackTrace();
					/* Go back to the start of this method, because some other thread might have been notified earlier
					 * and either it might have used the free slots or it might have already pinned this new file.*/
				} finally {
					/* Thread woke up by the bgThread, go and try to pinFile again.*/
					pinFile(fileName);
				}
			}
		}
	}
	
	/**
	 * utility method used by pinFile.
	 * If there is a cache entry in the free list, return it and remove it from the free list.
	 * 
	 * @return cEntry  -- a free FileCacheEntry
	 */
	private FileCacheEntry getFreeCacheEntry(){
		FileCacheEntry cEntry = null;
		if(freeList.size() > 0) {
			cEntry = freeList.firstElement();
			freeList.remove(cEntry);
		}
		return cEntry;
	}

	/**
	 * 
	 * @param cEntry  -- Free cache entry that can be added to the freeList.
	 */
	private void addToFreeList(FileCacheEntry cEntry) {
		freeList.add(cEntry);
	}
	
	/**
	 * utility method used by pinFile.
	 * If the cache is full and we need a cache entry, 
	 * try to remove some unpinned (unreferenced) nondirty , file cache entry
	 * and return it.
	 * 
	 * @return cEntry  -- FileCacheEntry
	 */
	private FileCacheEntry tryAndGetAFreeEntry() {
		FileCacheEntry ret = null;
		Set<String> keySet = cache.keySet();
		Iterator<String> keyIter = keySet.iterator();
		while(keyIter.hasNext()) {
			String fileName = keyIter.next();
			FileCacheEntry cEntry = cache.get(fileName);
		//	synchronized(cEntry) {
				if(cEntry.getRefCount() == 0 &&  !cEntry.isDirty() ) {
					log("The file "+ fileName + " is not dirty and is not referenced, So removing it from cache.");
					/*Removing just one entry should be fine, as the request is only for one cache entry.*/
					keyIter.remove();
					ret = cEntry;
					/*Removing just one entry should be fine, as the request is only for one cache entry.*/
					break;
				}
		//	}
		}
		return ret;
	}
	
	/**
	 * Utility method used by pinFile.
	 * Once we got a free FileCacheEntry from the freelist, 
	 * update the FileCacheEntry object with the current file that we need. 
	 * 
	 * @param cEntry -- FileCacheEntry
	 * @param fileName  -- fileName
	 * 
	 */
	private void updateCacheEntry(FileCacheEntry cEntry, String fileName){
		File f = new File(fileName);
		BufferedReader in = null;
		char[] cbuf = new char[MAX_FILE_SIZE];
		try {
			if(!f.exists()) {
				log("File " + fileName + " does not exist in File system, so creating one.");
				f.createNewFile();
			}
			in = new BufferedReader(new FileReader(fileName));
			in.read(cbuf,0,MAX_FILE_SIZE);
			
		} catch(IOException e) {
			
		} finally {
			if(in != null) {
				try {
					in.close();
				} catch (IOException e1) {
				}
			}
		}
		cEntry.reset(fileName, cbuf);
	}
	
	/**
	 * Given a collection of fileNames, will unpin these files.
	 * Will reduce the reference counts of these file cache entries.
	 * If the file was marked for delete earlier, and if the reference count becomes zero
	 * will delete the file permanently from the local disk.
	 * and then remove the cacheEntry from the hashtable and add it to the freeList.
	 * 
	 * @param  fileNames  -- vector of filenames that should be unpinned.
	 */
	public void unpinFiles(Collection<String> fileNames) {
		if(fileNames == null)
			return;
		synchronized(lock) {
			Iterator<String> fileNameIter = fileNames.iterator();
			while(fileNameIter.hasNext()) {

				String fileName = fileNameIter.next();
				FileCacheEntry cEntry = cache.get(fileName);
				if(cEntry != null) {
				//	synchronized(cEntry) {
						log("Decremeting the refCount for file :" + fileName);
						cEntry.decrementRefCount();
						/* If the refcount becomes 0 and file was marked to delete,
						 * delete the file. */
						if(cEntry.getRefCount() == 0) {
							if(cEntry.isMarkedToDelete()) {
								log("File " + fileName + " is marked for delete, so deleting it.");
								File f = new File(fileName);
								if(f.exists())
									f.delete();
								cache.remove(cEntry);
								addToFreeList(cEntry);
							}
							
							/* Do not unpin the files from cache, they might be used again.
							 *  We will delete it from cache only when free cache is not available during the pinfiles method. 
							 */
							
							/* if(!cEntry.isDirty()) {
								 I assume that unpin file means, it can be removed from cache, if no one is referring it.
								log("Removing the file " + fileName + " from cache.");
								cache.remove(fileName);
							}*/
						}
				//	}
				} else {
					/* Do nothing, calling unpin on files which are not pinned.*/
				}
			}
		}
		
	}
	
	/**
	 * Given a fileName, if the file was already pinned, 
	 * will return a read only byteBuffer that maps to the corresponding cache entry.
	 *  
	 * @param  fileName  -- fileName for which we need to return read only buffer.
	 */
	public ByteBuffer fileData(String fileName) {
		ByteBuffer buf = null;
		synchronized(lock) {
			FileCacheEntry cEntry = cache.get(fileName);
			if(cEntry!= null) {
			//	synchronized(cEntry) {
					buf = cEntry.getReadBuf();
					return buf;
			//	}
			}
		}
		return buf;
	}
	
	/**
	 * Given a fileName, if the file was already pinned, 
	 * will return a writable byteBuffer that maps to the corresponding cache entry.
	 * 
	 * @param fileName  -- fileName for which we need to return mutable buffer.
	 */
	public ByteBuffer mutableFileData(String fileName) {
		ByteBuffer buf = null;		
		synchronized(lock) {
			FileCacheEntry cEntry = cache.get(fileName);
			if(cEntry != null) {
			//	synchronized(cEntry) {
					buf = cEntry.getWriteBuf();
					return buf;
			//	}
			}
		}
		return buf;
	}
	
	/**
	 * Given a fileName -- 
	 *       if the file is pinned, the file will be marked for delete,
	 *              it will get deleted when the file is unpinned by all the threads. i.e when refcount become zero.
	 *       if the file is not pinned and the reference count = 0
	 *              the file will be deleted from the local disk
	 *              the cache entry will be added to the free list and is removed from the hashtable.
	 *       if the file was never pinned and is not in the cache.
	 *              the file will be deleted from the local disk.
	 *                        
	 * @param fileName -- fileName that needs to be deleted from the local disk. 
	 */
	public void deleteFile(String fileName) {
		synchronized(lock) {
			FileCacheEntry cEntry = cache.get(fileName);
			
			if(cEntry != null) {
			//	synchronized(cEntry) {
					if(cEntry.getRefCount() == 0) {
						File f = new File(fileName);
						if(f.exists()) {
							log("Deleting the file : " + fileName);
							f.delete();
						}
						cache.remove(fileName);
						addToFreeList(cEntry);
					} else {
						log("Marked the file " + fileName + " to delete.");
						cEntry.markToDelete();
					}
			//	}
			} else {
				
				log("The file not found in the cache. So deleting the file from the file system directly.");
				log("Deleting the file : " + fileName);
				File f = new File(fileName);
				if(f.exists()) {
					f.delete();
				}
			}
		}
	}
	
	/**
	 *  Stop the file cache. The boolean stopped will be set to true.
	 *  So the bgThread can look at this, write all the dirty data and exit.
	 *  
	 */
	public void stop() {
		 synchronized(lock) {
			 stopped = true;
		 }
	}
	
	/**
	 *  utility method for logging.
	 *  
	 * @param str  -- log message
	 */
	private void log(String str) {
		if(debugEnabled) {
			System.out.println("DEBUG -- " +Thread.currentThread().getName() + " : " + Calendar.getInstance().getTime() + " : " + str);
		}
	}
	
	/**
	 * 
	 * utility method, to enable or disable the debug for file cache.
	 * @param debug  -- debug boolean
	 */
	public void setDebugEnabled(boolean debug) {
		this.debugEnabled = debug;
	}
	
}
