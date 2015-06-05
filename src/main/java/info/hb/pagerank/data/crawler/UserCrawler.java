package info.hb.pagerank.data.crawler;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class UserCrawler implements Runnable {

	private static final String LOGIN_URL = "http://passport.cnblogs.com/login.aspx";
	private static final String USER_HOME = "http://home.cnblogs.com";
	// cookie
	private static List<String> cookies;
	private static int c = 0;

	private static AtomicBoolean stop;
	private int id;
	private UserBuffer mUserBuffer;
	private Saver saver;

	static {
		stop = new AtomicBoolean(false);
		try {
			login();
			Saver.getInstance().start();
		} catch (IOException e) {
			e.printStackTrace();
		}
		// new Thread(new CommandListener()).start();
	}

	public UserCrawler(UserBuffer userBuffer) {
		mUserBuffer = userBuffer;
		mUserBuffer.crawlerCountIncrease();
		id = c++;
		saver = Saver.getInstance();
	}

	@Override
	public void run() {
		if (id > 0) {
			try {
				TimeUnit.SECONDS.sleep(20 + id);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		System.out.println("UserCrawler " + id + " start");
		int retry = 3;
		while (stop.get() == false) {
			String userId = mUserBuffer.pickOne();
			if (userId == null) {
				retry--;
				if (retry <= 0)
					break;
				else
					continue;
			}
			try {
				List<String> fans = crawUser(userId, "/followers");
				List<String> heros = crawUser(userId, "/followees");
				StringBuilder sb = new StringBuilder(userId).append("\t");
				for (String friend : fans) {
					sb.append(friend).append("\t");
				}
				sb.deleteCharAt(sb.length() - 1).append("\n");
				saver.save(sb.toString());
				fans.addAll(heros);
				mUserBuffer.addUnCrawedUsers(fans);
			} catch (Exception e) {
				saver.log(e.getMessage());
				mUserBuffer.addErrorUser(userId);
			}
		}
		System.out.println("UserCrawler " + id + " stop");
		mUserBuffer.crawlerCountDecrease();
	}

	private List<String> crawUser(String userId, String tag) throws IOException {
		StringBuilder urlBuilder = new StringBuilder(USER_HOME);
		urlBuilder.append("/u/").append(userId).append(tag);
		String page = getPage(urlBuilder.toString());
		Document doc = Jsoup.parse(page);
		List<String> friends = new ArrayList<String>();
		friends.addAll(getOnePageFriends(doc));
		String nextUrl = null;
		while ((nextUrl = getNextUrl(doc)) != null) {
			page = getPage(nextUrl);
			doc = Jsoup.parse(page);
			friends.addAll(getOnePageFriends(doc));
		}
		return friends;
	}

	private List<String> getOnePageFriends(Document doc) {
		List<String> firends = new ArrayList<String>();
		Elements inputElements = doc.getElementsByClass("avatar_name");
		for (Element inputElement : inputElements) {
			Elements links = inputElement.getElementsByTag("a");
			for (Element link : links) {
				String href = link.attr("href");
				firends.add(href.substring(3, href.length() - 1));
			}
		}
		return firends;
	}

	private String getNextUrl(Document doc) {
		Elements inputElements = doc.getElementsByClass("pager");
		for (Element inputElement : inputElements) {
			Elements links = inputElement.getElementsByTag("a");
			for (Element link : links) {
				String text = link.text();
				if (text != null && text.contains("Next"))
					return USER_HOME + link.attr("href");
			}
		}
		return null;
	}

	private static String getPage(String pageUrl) throws IOException {
		URL url = new URL(pageUrl);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		if (cookies != null) {
			for (String cookie : cookies) {
				conn.addRequestProperty("Cookie", cookie);
			}
		}
		conn.setRequestMethod("GET");
		conn.setUseCaches(false);
		conn.setReadTimeout(10000);
		conn.setRequestProperty("Charset", "UTF-8");
		conn.setRequestProperty("User-Agent", "Mozilla/5.0");
		conn.connect();
		InputStream urlStream = conn.getInputStream();
		BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(urlStream, "utf-8"));
		StringBuilder sb = new StringBuilder();
		String line = null;
		while ((line = bufferedReader.readLine()) != null) {
			sb.append(line);
		}
		bufferedReader.close();
		return sb.toString();
	}

	public static void stop() {
		System.out.println("xxx...");
		stop.compareAndSet(false, true);
		UserBuffer.getInstance().prepareForStop();
	}

	private static void login() throws UnsupportedEncodingException, IOException {
		CookieHandler.setDefault(new CookieManager());
		String page = getPage(LOGIN_URL);
		Document doc = Jsoup.parse(page);
		Element loginform = doc.getElementById("frmLogin");
		Elements inputElements = loginform.getElementsByTag("input");
		List<String> paramList = new ArrayList<String>();
		for (Element inputElement : inputElements) {
			String key = inputElement.attr("name");
			String value = inputElement.attr("value");
			if (key.equals("tbUserName"))
				value = "xxx";
			else if (key.equals("tbPassword"))
				value = "xxx";
			paramList.add(key + "=" + URLEncoder.encode(value, "UTF-8"));
		}
		StringBuilder para = new StringBuilder();
		for (String param : paramList) {
			if (para.length() == 0) {
				para.append(param);
			} else {
				para.append("&" + param);
			}
		}
		String result = sendPost(LOGIN_URL, para.toString());
		if (!result.contains("followees")) {
			cookies = null;
			System.out.println("xxx");
		} else
			System.out.println("xxx");
	}

	private static String sendPost(String url, String postParams) throws IOException {
		URL obj = new URL(url);
		HttpURLConnection conn = (HttpURLConnection) obj.openConnection();
		conn.setUseCaches(false);
		conn.setRequestMethod("POST");
		conn.setRequestProperty("Charset", "UTF-8");
		conn.setRequestProperty("Host", "passport.cnblogs.com");
		conn.setRequestProperty("User-Agent", "Mozilla/5.0");
		conn.setRequestProperty("Connection", "keep-alive");
		conn.setRequestProperty("Referer", LOGIN_URL);
		conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
		conn.setRequestProperty("Content-Length", Integer.toString(postParams.length()));
		conn.setDoOutput(true);
		conn.setDoInput(true);
		DataOutputStream wr = new DataOutputStream(conn.getOutputStream());
		wr.writeBytes(postParams);
		wr.flush();
		wr.close();
		List<String> co = conn.getHeaderFields().get("Set-Cookie");
		if (co != null)
			for (String c : co) {
				cookies.add(c.split(";", 1)[0]);
			}
		BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
		String inputLine;
		StringBuffer response = new StringBuffer();
		while ((inputLine = in.readLine()) != null) {
			response.append(inputLine);
		}
		in.close();
		return response.toString();
	}

	@SuppressWarnings("unused")
	private static class CommandListener3 implements Runnable {

		public static final int STOP_CODE = 19;
		public final static int port = 8790;

		@Override
		public void run() {
			try {
				System.out.println("CommandListener start");
				ServerSocket serverSocket = new ServerSocket(port);
				Socket socket = serverSocket.accept();
				InputStream iStream = socket.getInputStream();
				int code = iStream.read();
				if (code == STOP_CODE) {
					stop.compareAndSet(false, true);
					System.out.println("Get stop command, will stop in seconds.");
				}
				serverSocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}

}
