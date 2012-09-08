package com.nutanix;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Iterator;
import java.util.Vector;

public class TestThread extends Thread{
	
	//MyOldFileCache cache;
	MyFileCache cache;
	Collection<String> fileNames;
	boolean write;
	//public TestThread(String name, MyOldFileCache cache, Collection<String> fileNames, boolean write) {
	public TestThread(String name, MyFileCache cache, Collection<String> fileNames, boolean write) {
		super(name);
		this.cache = cache;
		this.write = write;
		this.fileNames = new Vector<String>();
		this.fileNames.addAll(fileNames);
		start();
	}
	
	public String toString(){
		return getName();
	}
	
	public void run() {
		cache.pinFiles(fileNames);
		Iterator<String> fileNameIter = fileNames.iterator();
		while(fileNameIter.hasNext()) {
			String fileName = fileNameIter.next();
			if(write) {
				try {
				ByteBuffer buf = cache.mutableFileData(fileName);
				String str = "thread:"+getName() + "wrote.";
				buf.asCharBuffer().put(str);
				System.out.println(buf.asCharBuffer().toString());
				} catch(Exception e) {
					e.printStackTrace();
				}
			} else {
				try {
				ByteBuffer buf = cache.fileData(fileName);
				System.out.println("Thread : "+getName() + " printing " + fileName+ " :" + buf.asCharBuffer().toString());
				}catch (Exception e) {
					System.out.println("Thread :" +getName() + " caught an exception.");
					e.printStackTrace();
				}
			}
		}
		cache.unpinFiles(fileNames);
		
	}
	

}
