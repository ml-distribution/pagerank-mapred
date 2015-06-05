package info.hb.pagerank.data.crawler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class UserBuffer {

	private static final String UNCRAWED_FILE = "unCrawedUsers.txt";
	private static final String ERROE_FILE = "errorUsers.txt";
	private static final String CRAWED_FILE = "crawedUsers.txt";

	private Set<String> crawedUsers;
	private Set<String> errorUsers;
	private Queue<String> unCrawedUsers;
	private int crawCount;
	private Object countLock;

	private static UserBuffer instance = new UserBuffer();

	public static UserBuffer getInstance() {
		return instance;
	}

	private UserBuffer() {
		crawedUsers = new HashSet<String>();
		errorUsers = new HashSet<String>();
		unCrawedUsers = new LinkedList<String>();

		crawCount = 0;
		countLock = new Object();
		initCrawState();
	}

	public void crawlerCountIncrease() {
		synchronized (countLock) {
			crawCount++;
		}
	}

	public boolean crawlerCountDecrease() {
		synchronized (countLock) {
			crawCount--;
			if (crawCount <= 0) {
				try {
					saveCrawState();
					Saver.getInstance().stop();
					return false;
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				}
			}
		}
		return true;
	}

	public void prepareForStop() {
		Thread thread = new Thread(new Runnable() {

			@Override
			public void run() {
				try {
					System.out.println("xxxx");
					TimeUnit.SECONDS.sleep(120);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				while (crawlerCountDecrease())
					;
			}

		});
		thread.setDaemon(true);
		thread.start();
	}

	private void initCrawState() {
		try {
			read(UNCRAWED_FILE, unCrawedUsers);
			System.out.println("xxx" + unCrawedUsers.size());
			read(ERROE_FILE, errorUsers);
			System.out.println("xxx" + errorUsers.size());
			read(CRAWED_FILE, crawedUsers);
			System.out.println("xxx" + crawedUsers.size());
		} catch (IOException e) {
			System.out.println("fisrt init");
			unCrawedUsers.add("fengfenggirl");
		}

	}

	private void read(String fileName, Collection<String> content) throws IOException {
		BufferedReader in = new BufferedReader(new FileReader(new File(fileName)));
		String line;
		while ((line = in.readLine()) != null) {
			content.add(line);
		}
		in.close();
	}

	private void saveCrawState() throws FileNotFoundException {
		save(UNCRAWED_FILE, unCrawedUsers);
		save(ERROE_FILE, errorUsers);
		save(CRAWED_FILE, crawedUsers);
	}

	private void save(String fileName, Collection<String> content) throws FileNotFoundException {
		PrintWriter out = new PrintWriter(new File(fileName));
		for (String ids : content) {
			out.print(ids);
			out.print("\n");
		}
		out.flush();
		out.close();
	}

	public synchronized void addUnCrawedUsers(List<String> users) {
		for (String user : users) {
			if (!crawedUsers.contains(user))
				unCrawedUsers.add(user);
		}
	}

	public synchronized String pickOne() {
		String newId = unCrawedUsers.poll();
		while (crawedUsers.contains(newId)) {
			newId = unCrawedUsers.poll();
		}
		crawedUsers.add(newId);
		return newId;
	}

	public synchronized void addErrorUser(String userId) {
		errorUsers.add(userId);
	}

}
