package org.openhab.binding.webcsv.internal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openhab.io.net.http.HttpUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TODO Not fully tested!!!
 * 
 * <p>The WebCSV checker is called to test if the host is up and/or 
 * against a given configuration (properties) file.</p>
 * 
 * @author the78mole@github
 *
 */
public class WebCSVChecker {

	/**
	 * The name of this checker.
	 */
	private String name;
	
	/**
	 * The url this checker is linked to.
	 */
	private URL url;
	
	/**
	 * The pattern to check the data from url.
	 */
	private Pattern pattern;

	/**
	 * The default LOGGER.
	 */
	static final Logger LOGGER = LoggerFactory.getLogger(WebCSVConfig.class);

	/**
	 * This function tests, if the WebCSV host links with this configuration
	 * is up and is the right inverter type to be handled by this config.
	 * 
	 * @return
	 * 			true if the host is up and a corresponding config is available
	 */
	public boolean doWebCSVCheck() {
		return doWebCSVCheck(url, pattern, name, true);
	}

	/**
	 * This function tests, if the WebCSV host links with this configuration
	 * is up and is the right inverter type to be handled by this config.
	 * 
	 * @param fullCheck
	 *            If true, it does a full check including a type check from meta
	 *            information acced through the test.url in properties. If
	 *            false, it simply pings the host. This can be used for checking
	 *            host status before trying to acces other data.
	 * @return
	 * 			true if the host is up and a corresponding config is available
	 */
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
	 * 			true if the host is up and a corresponding config is available
	 */
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
			LOGGER.error("Test URL {} in {} is malformed", (url == null ? "null" : url.toString()), name);
		}
		return null;
	}

	/**
	 * This static function tests, if the WebCSV host links with this configuration
	 * is up and is the right inverter type to be handled by this config.
	 * 
	 * @param url
	 * 			The url of the host to check including port
	 * @param pattern
	 * 			The pattern to test the host response for
	 * @param name
	 * 			This is optional and mostly interesting for debug purposes. If 
	 * 			called by {@link #doWebCSVCheck(Properties, boolean)}, the name
	 * 			is taken from the properties.name value to reference a given 
	 * 			config file. 
	 * @param fullCheck
	 *            If true, it does a full check including a type check from meta
	 *            information acced through the test.url in properties. If
	 *            false, it simply pings the host. This can be used for checking
	 *            host status before trying to acces other data.
	 * @return
	 * 			true if the host is up and a corresponding config is available
	 */
	public static boolean doWebCSVCheck(URL url, Pattern pattern, String name,
			boolean fullCheck) {
		// Do an inital ping to check if the host is up
		if (!pingHostPort(url.getHost(), url.getPort()))
			return false;
	
		// Request the meta information and compare with regex
		if (fullCheck && !checkMeta(url, pattern, name, 50))
			return false;
	
		return true;
	}

	/**
	 * Tests if the host is up and the specified port is open.
	 * 
	 * @param host
	 * 			The hostname or IP address of the host to ping 
	 * @param port
	 * 			The port to ping the host
	 * @return
	 * 			true if the host port is open and responds
	 */
	public static boolean pingHostPort(String host, int port) {
		Socket socket = null;
		boolean reachable = false;
		try {
			socket = new Socket(host, 80);
			reachable = true;
		} catch (UnknownHostException e) {
			LOGGER.error("Host {} is unknown/can not be resolved.", host, port);
		} catch (IOException e) {
			LOGGER.error("IOException occured while probing host {}:{}.", host,
					port);
		} finally {
			if (socket != null)
				try {
					socket.close();
				} catch (IOException e) {
					LOGGER.error("Host {} is not reachable on port {}.", host,
							port);
				}
		}
		return reachable;
	}

	/**
	 * Checks the meta information of the host defined by <b>url</b> 
	 * against a given <b>pattern</b>.
	 * 
	 * @param url
	 * 			URL, where the host can be reached including port.
	 * @param pattern
	 * 			The pattern to check the response for
	 * @param name
	 * 			The name where the test properties come from. Most useful
	 * 			for debugging purposes.
	 * @param maxlines
	 * 			Maximum count of lines the pattern is checked with.
	 * @return
	 * 			True if pattern matched to the host response
	 */
	public static boolean checkMeta(URL url, Pattern pattern, String name,
			long maxlines) {
		
		String retval = HttpUtil.executeUrl("GET", url.toString(), 3000);


		if (retval != null) {
			if (pattern == null) {
				LOGGER.warn("No pattern defined for {}. Assuming it works, since url {} provided at least no error.", 
						name, url.toString());
				return true;
			}

			try {
				BufferedReader brin = new BufferedReader(new StringReader(retval));
				String line;
				Matcher matcher;
				while (maxlines > 0 && (line = brin.readLine()) != null) {
					matcher = pattern.matcher(line.trim());
					matcher.reset();
					if (matcher.matches())
						return true;
					maxlines--;
				}
			} catch (IOException e) {
				LOGGER.error("IO Exception while checking {} in {},",
						url.toString(), name);
			}
		} else
			LOGGER.info("Could not match the pattern {} for {}.", pattern.toString(), url.toString());

		return false;
	}

	/**
	 * Processes the config properties, extracts the check/test properties 
	 * and stores it within this object.
	 *  
	 * @param props
	 * 			Properties to test the host for.
	 */
	public void processTestProps(Properties props) {
		name = props.getProperty("properties.name",
				props.getProperty("name", "noname"));
		try {
			url = new URL((String) props.get("test.url"));
			String testExpr = props.getProperty("test.expr");
			if (testExpr != null)
				pattern = Pattern.compile(props.getProperty("test.expr"));
			else
				LOGGER.warn("No test expression defined in {}. " 
						+ "If a connection to the given url can be established, it is assumed to work." 
						+ "This is not a reliable/good test approach.", name);
		} catch (MalformedURLException e) {
			LOGGER.error("Test URL {} in {} is malformed", url.toString(), name);
		}
	}

}