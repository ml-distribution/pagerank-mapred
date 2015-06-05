package info.hb.pagerank.data.crawler;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class Saver {

	private BlockingQueue<String> dataBuffer;
	private BlockingQueue<String> logBuffer;
	private SaveThread saveThread;
	private Logger logger;
	private static Saver instance = new Saver();

	private Saver() {
		dataBuffer = new LinkedBlockingQueue<String>();
		logBuffer = new LinkedBlockingQueue<String>();
		saveThread = new SaveThread();
		logger = new Logger();
	}

	public static Saver getInstance() {
		return instance;
	}

	public void start() {
		saveThread.start();
		logger.start();
	}

	public void save(String line) {
		try {
			dataBuffer.put(line);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	public void log(String log) {
		try {
			logBuffer.put(log);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	private class SaveThread extends Thread {

		private static final String FILE_NAME = "fans.txt";
		private int count = 0;

		@Override
		public void run() {
			PrintWriter out;
			try {
				out = new PrintWriter(new FileOutputStream(new File(FILE_NAME), true /* append = true */));
				try {
					while (true) {
						out.print(dataBuffer.take());
						count++;
						if (count % 100 == 0)
							System.out.println(count);
					}
				} catch (InterruptedException e) {
					out.flush();
					out.close();
					System.out.println("SaveThread stop");
					return;
				}
			} catch (FileNotFoundException e1) {
				e1.printStackTrace();
			}
		}

	};

	private class Logger extends Thread {

		private static final String FILE_NAME = "logs.txt";

		@Override
		public void run() {
			PrintWriter out;
			try {
				out = new PrintWriter(new FileOutputStream(new File(FILE_NAME), true /* append = true */));
				try {
					while (true) {
						out.print(logBuffer.take());
						out.print("\n");
						out.flush();
					}
				} catch (InterruptedException e) {
					out.flush();
					out.close();
					System.out.println("Logger stop");
					return;
				}
			} catch (FileNotFoundException e1) {
				e1.printStackTrace();
			}
		}
	};

	public void stop() {
		int count = 0;
		String state = "xxx";
		while (dataBuffer.size() > 0 || logBuffer.size() > 0) {
			try {
				TimeUnit.SECONDS.sleep(1);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			count++;
			System.out.print(".");
			if (count > 20) {
				state = "xxxx!";
				break;
			}
		}
		System.out.println(state);
		saveThread.interrupt();
		logger.interrupt();
	}

	public static void main(String[] args) {
		Saver s = new Saver();
		for (int i = 0; i < 10; i++)
			s.save("test" + i);
		s.start();
		try {
			TimeUnit.SECONDS.sleep(3);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		s.stop();
	}

}
