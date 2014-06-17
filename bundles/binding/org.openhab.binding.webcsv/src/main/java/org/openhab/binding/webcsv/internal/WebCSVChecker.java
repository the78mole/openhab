package org.openhab.binding.webcsv.internal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebCSVChecker {

	private String name;
	private URL url;
	private Pattern pattern;

	static final Logger logger = LoggerFactory
			.getLogger(WebCSVConfig.class);

	public boolean doWebCSVCheck() {
		return doWebCSVCheck(url, pattern, name, true);
	}

	public boolean doWebCSVCheck(boolean fullCheck) {
		return doWebCSVCheck(url, pattern, name, fullCheck);
	}

	/**
	 * This static function tests, if the WebCSV host links with this configuration
	 * is up and is the right inverter type to be handled by this config.
	 * 
	 * @param props
	 *            The property list for a specific WebCSV host
	 * @param fullCheck
	 *            If true, it does a full check including a type check from meta
	 *            information acced through the test.url in properties. If
	 *            false, it simply pings the host. This can be used for checking
	 *            host status before trying to acces other data.
	 * @return
	 */
	@SuppressWarnings("null")
	public static Boolean doWebCSVCheck(Properties props, boolean fullCheck) {
		String name = props.getProperty("properties.name",
				props.getProperty("name", "noname"));
		URL url = null;
		Pattern pattern;
		try {
			url = new URL((String) props.get("test.url"));
			pattern = (fullCheck ? Pattern.compile(props
					.getProperty("test.expr")) : null);
			return doWebCSVCheck(url, pattern, name, fullCheck);
		} catch (MalformedURLException e) {
			logger.error("Test URL {} in {} is malformed", url.toString(), name);
		}
		return null;
	}

	public static boolean pingHostPort(String host, int port) {
		Socket socket = null;
		boolean reachable = false;
		try {
			socket = new Socket(host, 80);
			reachable = true;
		} catch (UnknownHostException e) {
			logger.error("Host {} is unknown/can not be resolved.", host, port);
		} catch (IOException e) {
			logger.error("IOException occured while probing host {}:{}.", host,
					port);
		} finally {
			if (socket != null)
				try {
					socket.close();
				} catch (IOException e) {
					logger.error("Host {} is not reachable on port {}.", host,
							port);
				}
		}
		return reachable;
	}

	public static boolean doWebCSVCheck(URL url, Pattern pattern, String name,
			boolean fullCheck) {
		// Do am inital ping to check the host is up
		if (!pingHostPort(url.getHost(), url.getPort()))
			return false;

		// Request the meta information and compare with regex
		if (fullCheck && !checkMeta(url, pattern, name, 50))
			return false;

		return true;
	}

	public static boolean checkMeta(URL url, Pattern pattern, String name,
			long maxlines) {
		try {
			URLConnection con = url.openConnection();
			InputStream in;
			in = con.getInputStream();
			if (con.getContentEncoding().equalsIgnoreCase("deflate"))
				in = new InflaterInputStream(in);
			if (con.getContentEncoding().equalsIgnoreCase("gzip"))
				in = new GZIPInputStream(in);
			String encoding = con.getContentEncoding();
			encoding = encoding == null ? "UTF-8" : encoding;

			BufferedReader brin = new BufferedReader(new InputStreamReader(in));
			String line;
			Matcher matcher;
			while (maxlines > 0 && (line = brin.readLine()) != null) {
				matcher = pattern.matcher(line.trim());
				matcher.reset();
				if (matcher.matches())
					return true;
				maxlines--;
			}
		} catch (MalformedURLException e) {
			logger.error("Test URL {} in {} is malformed", url.toString(), name);
		} catch (IOException e) {
			logger.error("IO Exception while checking {} in {},",
					url.toString(), name);
		}

		return false;
	}

	public void processTestProps(Properties props) {
		name = props.getProperty("properties.name",
				props.getProperty("name", "noname"));
		try {
			url = new URL((String) props.get("test.url"));
			pattern = Pattern.compile(props.getProperty("test.expr"));
		} catch (MalformedURLException e) {
			logger.error("Test URL {} in {} is malformed", url.toString(), name);
		}
	}

}