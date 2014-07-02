package org.openhab.binding.webcsv.internal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openhab.io.net.http.HttpUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is an aggregator for {@link WebCSVValue}s. It is responsible for fetching and caching the dataset and handing
 * the data over to the value class on request.
 * 
 * @author the78mole@github
 *
 */
public class WebCSVValues {

	/**
	 * The default LOGGER.
	 */
	static final Logger LOGGER = LoggerFactory.getLogger(WebCSVValues.class);
	/** The maximum lines to read from host before stopping. In fact, reading with {@link HttpUtil} retrieves the
	 * whole data and only {@link StringReader} is affected. */
	private static final int READ_MAX_LINES = 100;
	/** Unsplit String representation of {@link #CACHE_ENABLED} for better Javadoc. */
	private static final String CACHE_ENABLED_STRING = "true, enabled, on, cached";
	/** Unsplit String representation of {@link #CACHE_DISABLED} for better Javadoc. */
	private static final String CACHE_DISABLED_STRING = "false, disabled, off, uncached";
	/** Defines possible String values for enabling the caching. */
	private static final String[] CACHE_ENABLED = CACHE_ENABLED_STRING.split(",");
	/** Defines possible String values for disabling the caching. */	
	private static final String[] CACHE_DISABLED = CACHE_DISABLED_STRING.split(",");
	/** The HTTP request timeout. */
	private int timeout = 3000;
	/** The name of the values aggregator. */
	private String name;
	/** The url to retrieve the data. */
	private URL url;
	
	/**
	 * Sets the HTTP data url.
	 * 
	 * @param url of the data.
	 */
	public void setUrl(URL url) {
		this.url = url;
	}

	/** Regular expression for splitting the values. */
	private String split;
	
	/** {@link Pattern} for grouping lines to variables. This can be used instead of split for more complex data. */
	private Pattern expr;
	
	/** String array thats contains the variable names in same order as {@link #split} or groups of {@link #expr}. */
	private String[] vars;
	
	/**
	 * Gets the variable names.
	 * 
	 * @return the String array of variable names.
	 */
	public String[] getVars() {
		return vars;
	}

	/** Timestamp of last values update. <code>0L</code> for never being updated.*/
	private Long lastUpdate = 0L;
	
	/**
	 * Checks if an update is neccessary.
	 * 
	 * @return true if {@link #lastUpdate} is more than {@link #refresh} milliseconds ago, otherwise false.
	 */
	public Boolean updateNeeded() {
		if (refresh == null) return null;
		if (refresh == -1) return true;
		else if (refresh == 0) return false;
		return  System.currentTimeMillis() >= lastUpdate + refresh;
	}

	/** Defines the refresh interval in milliseconds for this instance. */
	private Long refresh;
	
	/** Defines if cache is enabled for this instance. */
	private Boolean cacheEnabled;
	
	/** Map of String (variable names) to variables instances ({@link WebCSVValue}s). */
	private Map<String, WebCSVValue> values = new TreeMap<String, WebCSVValue>();
	/**
	 * The constructor for this vlues class. It needs a name, the variables and a switch, if value objects shall be
	 * created instantanously.
	 * 
	 * @param name of this instance
	 * @param vars the variables that are aggregated with this instance
	 * @param createValueObjs true, if value objects shall be created
	 */
	public WebCSVValues(String name, String[] vars, boolean createValueObjs) {
		if (name != null) 
			this.name = name;
		this.vars = vars;
		if (createValueObjs)
			for (String svar : vars) 
				values.put(svar, new WebCSVValue(svar, this));
	}

	/**
	 * Checks if updates are needed and if so, updates all values aggregated with this instance.
	 * 
	 * @return true if update succeded, false if failed or was not needed
	 */
	public boolean updateValues() {
		long now = System.currentTimeMillis();
		boolean updated = false;
		if (split != null && expr != null) {
			LOGGER.error("Weather split nor an expression is defined for {}. Aborting data retrieval.", name);
			return false;
		}
		if (lastUpdate == 0L || refresh == -1L || (refresh > 0 && now >= lastUpdate + refresh)) {
			
			String tmp = HttpUtil.executeUrl("GET", url.toString(), timeout);
			
			// Check if we fetched successfully
			if (tmp == null) return false;
			
			StringReader sr = new StringReader(tmp);
			
			BufferedReader brin = new BufferedReader(sr);
			
			String line;
			
			String[] tmpres = new String[0];
			Matcher matcher = null;
			int countlines = READ_MAX_LINES;
			try {
				while (countlines-- > 0 && (line = brin.readLine()) != null && tmpres.length < vars.length) {
					if (split != null) { 
						tmpres = line.split(split);
					} else if (expr != null) {
						// TODO: Test this no-split regex version
						matcher = expr.matcher(line);
						int cnt = 0;
						if (matcher.matches())
							cnt = Math.min(matcher.groupCount(), vars.length);
							tmpres = new String[cnt];
							for (int i = 0; i < cnt; i++) {
								tmpres[i] = matcher.group(i);
							}
					}
				}
				brin.close();
			
				// Some hosts (e.g. Powador inverters) put a semicolon at the end of each line. 
				// Therefore it usually splits one value more than vars are given. 
				if (tmpres.length >= vars.length) {
					for (int i = 0; i < vars.length; i++) {
						values.get(vars[i]).updateValue(tmpres[i]);
					}
					lastUpdate = System.currentTimeMillis();
					updated = true;
				} else
					LOGGER.error("Result {} from WebCSV host was not matching the expected one from properties.", 
							this.name);

			} catch (IOException e) {
				LOGGER.error("IO-Exception occured on connecting/while reading for url {}.", url);
				e.printStackTrace();
			}
				 
			
		} else 
			LOGGER.debug("Update of values not needed. Skipping.");
		
		return updated;
	}
	
	/**
	 * Returns the value instance for a given variable name.
	 * 
	 * @param varname name of the requested variable 
	 * @return the value instance of the variable
	 */
	public WebCSVValue getWebCSVValue(String varname) {
		return values.get(varname);
	}
	
	/**
	 * Gets cache active state.
	 * 
	 * @return true if cache is enabled, false otherwise.
	 */
	public Boolean getCacheEnabled() {
		return cacheEnabled;
	}

	/**
	 * Sets cache active state.
	 * 
	 * @param cacheEnabled true if cache shall be enabled
	 */
	public void setCacheEnabled(Boolean cacheEnabled) {
		this.cacheEnabled = cacheEnabled;
	}

	/**
	 * Sets cache activation according to a settings String. {@value #CACHE_ENABLED_STRING} equals true, 
	 * {@value #CACHE_DISABLED_STRING} equals false.
	 * 
	 * @param cacheEnabled String representation of cache setting
	 */
	public void setCacheEnabled(String cacheEnabled) {
		String lcce = cacheEnabled.toLowerCase();
		if (Arrays.asList(CACHE_DISABLED).contains(lcce))
			this.cacheEnabled = false;
		else if (Arrays.asList(CACHE_ENABLED).contains(lcce))
			this.cacheEnabled = true;
		else {
			LOGGER.warn("{} is not a valid cache setting. Must be one of {} or {}", cacheEnabled, 
					Arrays.deepToString(CACHE_ENABLED), Arrays.deepToString(CACHE_DISABLED));
			this.cacheEnabled = true;
		}
	}

	/**
	 * Gets the value instances of this aggregator as an array, build from the internal map.
	 * 
	 * @return the array of values.
	 */
	public WebCSVValue[] getValues() {
		return values.values().toArray(new WebCSVValue[0]);
	}

	/**
	 * Sets the refresh rate, before cache times out.
	 * 
	 * @param refresh rate in milliseconds as a String.
	 */
	public void setRefresh(String refresh) {
		if (refresh == null) {
			this.refresh = 0L;
		} else
			try {
				this.refresh = Long.parseLong(refresh);
			} catch (NumberFormatException e) {
				this.refresh = 0L;
				LOGGER.error("Refresh with value {} can not be parsed to a Long value. Setting to not refresh.", 
						refresh);
			}
	}

	/**
	 * Sets the refresh rate, before cache times out.
	 * 
	 * @param refresh rate in milliseconds
	 */
	public void setRefresh(Long refresh) {
		this.refresh = refresh;
	}

	/**
	 * Gets the refresh rate for cache timeout.
	 * 
	 * @return the refresh rate in milliseconds
	 */
	public Long getRefresh() {
		return refresh;
	}

	/**
	 * Gets the HTTP request timeout.
	 * 
	 * @return the request timeout in milliseconds.
	 */
	public int getTimeout() {
		return timeout;
	}

	/**
	 * Sets the HTTP request timeout.
	 * 
	 * @param timeout in milliseconds
	 */
	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}

	/**
	 * Gets the name of this aggregator instance.
	 * 
	 * @return the name of this instance
	 */
	public String getName() {
		return name;
	}

	/**
	 * Sets the split regex that groups the HTTP responds to variables, defined by {@link #vars}. 
	 * This method strips also leading and trailing double quotes as used in config properties. 
	 * 
	 * @param split the splitter regular expression
	 */
	public void setSplit(String split) {
		this.split = split.replaceAll("(^\"|\"$)", "");
	}

	/**
	 * Gets the splitter regular expression as a String.
	 * 
	 * @return the splitter expression
	 */
	public String getSplit() {
		return split;
	}

	/**
	 * Sets the factors for converting rawValues to scaled values.
	 * 
	 * @param factors the String array of factors 
	 */
	public void setFactors(String[] factors) {
		if (factors.length == vars.length) {
			for (int i = 0; i < vars.length; i++) {
				values.get(vars[i]).setFactor(factors[i]);
			}
		} else
			LOGGER.warn("Count of vars ({}) and factors ({}) differ for values.{}.", 
					vars.length, factors.length, this.name);
	}

	/**
	 * Sets the types of the variables.
	 * 
	 * @param types String array of variable types
	 */
	public void setTypes(String[] types) {
		if (types.length == vars.length) {
			for (int i = 0; i < vars.length; i++) {
				values.get(vars[i]).setType(types[i]);
			}
		} else
			LOGGER.warn("Count of vars ({}) and types ({}) differ for values.{}.", 
					vars.length, types.length, this.name);
	}
	
	/**
	 * The toString() method, optimized for debugging.
	 * 
	 * @return a String information representation of this class.
	 */
	public String toString() {
		return "[url=" + (url != null ? url : "\"\"") 
				+ (expr != null ? ",expr=" + expr : "") + (split != null ? "," + "split=" + split : "")
				+ ",values={" + (values != null ? values.size() : "") + "},"
				+ "cacheEnabled=" + cacheEnabled + ",refresh=" + refresh + ",lastUpdate=" + lastUpdate + "]";
	}

}
