package com.nutanix;


import java.util.Collection;
import java.util.Vector;

public class TestCode {

	public static void main(String[] args) {
		//MyOldFileCache cache = new MyOldFileCache(5,20);
		MyFileCache cache = new MyFileCache(5,5);
		cache.setDebugEnabled(true);
		Collection<String> fileNames1 = new Vector<String>();
		fileNames1.add("D:\\Interview\\NutanixFileCache\\testfiles\\test1.txt");
		fileNames1.add("D:\\Interview\\NutanixFileCache\\testfiles\\test2.txt");
		fileNames1.add("D:\\Interview\\NutanixFileCache\\testfiles\\test3.txt");
		fileNames1.add("D:\\Interview\\NutanixFileCache\\testfiles\\test4.txt");

		Collection<String> fileNames2 = new Vector<String>();
		fileNames2.add("D:\\Interview\\NutanixFileCache\\testfiles\\test7.txt");
		fileNames2.add("D:\\Interview\\NutanixFileCache\\testfiles\\test4.txt");
		fileNames2.add("D:\\Interview\\NutanixFileCache\\testfiles\\test5.txt");
		fileNames2.add("D:\\Interview\\NutanixFileCache\\testfiles\\test6.txt");
		
		Collection<String> fileNames3 = new Vector<String>();
		fileNames3.add("D:\\Interview\\NutanixFileCache\\testfiles\\test5.txt");
		fileNames3.add("D:\\Interview\\NutanixFileCache\\testfiles\\test6.txt");
		fileNames3.add("D:\\Interview\\NutanixFileCache\\testfiles\\test7.txt");
		fileNames3.add("D:\\Interview\\NutanixFileCache\\testfiles\\test8.txt");
		
		Collection<String> fileNames4 = new Vector<String>();
		fileNames4.add("D:\\Interview\\NutanixFileCache\\testfiles\\test7.txt");
		fileNames4.add("D:\\Interview\\NutanixFileCache\\testfiles\\test8.txt");
		fileNames4.add("D:\\Interview\\NutanixFileCache\\testfiles\\test9.txt");
		fileNames4.add("D:\\Interview\\NutanixFileCache\\testfiles\\test1.txt");
		fileNames4.add("D:\\Interview\\NutanixFileCache\\testfiles\\test2.txt");
		
		
		TestThread test1 = new TestThread("test1",cache, fileNames1, true);
		
		TestThread test2 = new TestThread("test2",cache, fileNames1, false);
		TestThread test3 = new TestThread("test3",cache, fileNames2, true);
		TestThread test4 = new TestThread("test4",cache, fileNames2, false);
		TestThread test5 = new TestThread("test5",cache, fileNames3, true);
		TestThread test6 = new TestThread("test6",cache, fileNames3, false);
		TestThread test7 = new TestThread("test7",cache, fileNames4, true);
		TestThread test8 = new TestThread("test8",cache, fileNames4, false);
		TestThread test9 = new TestThread("test9",cache, fileNames1, true);
		TestThread test10 = new TestThread("test10",cache, fileNames2, false);
		TestThread test11 = new TestThread("test11",cache, fileNames3, false);
		
		
		
		try {
			System.out.println("Joining the thread.");
			test1.join();
			test2.join();
			test2.join();
			test3.join();
			test4.join();
			test5.join();
			test6.join();
			test7.join();
			test8.join();
			test9.join();
			test10.join();
			test11.join();
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}
		System.out.println("The threads are done running");
		System.out.println("Stopping the cache.");
		cache.stop();
		

	}
}
