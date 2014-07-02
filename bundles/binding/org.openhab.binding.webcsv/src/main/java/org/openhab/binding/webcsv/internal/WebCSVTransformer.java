package org.openhab.binding.webcsv.internal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openhab.io.net.http.HttpUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>This class implements the transformation that can be applied to csv values. The transformation is done by some
 * look-up using an url that itself replies with a file, containing one translation per line that itself can be devided 
 * by some regular expression. The most simple file has one key, a separator and the value, one per line.</p>
 * 
 * <h3>Example:</h3>
 * 
 * <p>The url <code>http://example.com/statetable.txt</code> contains the following data.<br>
 * <code>
 * 0,Error<br>
 * 1,Off<br>
 * 2,Startup<br>
 * 3,Running<br>
 * 4,Stopping
 * </code><br>
 * </p>
 * 
 * <p>The config would look like:<br>
 * <code>
 * <b>expr=</b>"^\\W*(\\d+),(.*?)\\W*$"<br>
 * <b>exprGroupVars=</b>statid,statdesc
 * </code><br>
 * </p>
 * 
 * <p>This will define a translation, where the key (statid) can be compared to the request and the result will be the
 * referenced value (statdesc). The order of keys and values is not relevant and only referenced through the variable
 * names. So if you would search for the status ID and the description is available, simply define the Description is 
 * the input variable.
 * </p>
 * 
 * @author the78mole@github
 *
 */
public class WebCSVTransformer {
	
	/** The name of this transformer. */
	private String name;
	/** The url this transformer uses to look up values. */
	private URL url;
	/** The pattern this transformer uses to separate a key and it's values. */
	private Pattern expr;
	/** The variable names for the groups of {@link #expr}. */	
	private String[] exprGroupVars;
	/** The content of last matching line (used for speed-caching). */
	private String lastMatchedLine = null;
	/** Cache for all data (not only the matching line). */
	private List<String> cacheData = null;

	/** Cache refresh interval (milliseconds), infinite (== 0) or disabled cache (&lt; 0). */
	private Long refresh;
	/** Timestamp of last update for this transformer. */	
	private Long lastUpdated = 0L;
	
	/** The types of the variables. */
	private String[] types;
	
	/**
	 * Maximum lines that should be read from transformation web service.
	 */
	private static final long MAXLINES = 1000;

	/**
	 * The default LOGGER.
	 */
	static final Logger LOGGER = LoggerFactory.getLogger(WebCSVTransformer.class);
	
	/** The timeout for the HTTP request. */
	private int timeout = 3000;

	/** 
	 * Constructor for the transformation class.
	 * 
	 * @param name of the transformer
	 */
	public WebCSVTransformer(String name) {
		this.name = name;
	}

	/**
	 * Retrieves the variable value from cache.
	 * 
	 * @param inValue the key value to look up from cache
	 * @param inVarPos the position of the key in {@link #exprGroupVars}
	 * @param outVarPos the position of the (result) value in {@link #exprGroupVars}
	 * @return the resulting value of the cache look up
	 */
	public String[] executeCached(String inValue, int inVarPos, int outVarPos) {
		String[] tmpres = null;
		for (String line : cacheData) {
			tmpres = matchLine(expr.matcher(line), inVarPos, outVarPos);
			if (tmpres != null && tmpres.length >= 2
					&& tmpres[0].equals(inValue))
				return tmpres;
		}
		return tmpres;
	}

	/**
	 * Helper function to retrieve the position of the named variable in a String array.
	 *  
	 * @param group the array of strings to search in
	 * @param varname the name to search for.
	 * @return the position of the requested name
	 */
	public static int getVarPos(String[] group, String varname) {
		return Arrays.asList(group).indexOf(varname);
	}

	/**
	 * Look up a value for a given key by executing a HTTP request.
	 * 
	 * @param inValue the value of the key to find
	 * @param inVar the name of the key variable
	 * @param outVar the name of the value variable
	 * @param fillCache if the request shall be cached for later look-ups
	 * @return the result of the request as an array with [0] containing the key and [1] containing the value 
	 */
	public String[] executeNonCached(String inValue, String inVar, String outVar, boolean fillCache) {

		String[] outval = null;

		int inVarPos = getVarPos(exprGroupVars, inVar), outVarPos = getVarPos(exprGroupVars, outVar);

		if (fillCache)
			cacheData = new ArrayList<String>();

		try {
			String tmp = HttpUtil.executeUrl("GET", url.toString(), timeout);
			
			StringReader sr = new StringReader(tmp);
			
			BufferedReader brin = new BufferedReader(sr);

			String line;
			String[] tmpres;
			boolean runner = true;
			long cntlines = MAXLINES;
			while (runner && cntlines > 0 && (line = brin.readLine()) != null) {
				tmpres = matchLine(expr.matcher(line), inVarPos, outVarPos);
				if (tmpres != null && tmpres.length >= 2) {
					if (tmpres[0].equals(inValue)) {
						outval = tmpres;
						// If we do not need to fill the cache, stop loop
						if (fillCache)
							lastMatchedLine = line;
						runner = (fillCache ? true : false);
					}
					if (fillCache)
						cacheData.add(line);
				}
				cntlines--;
			}

			lastUpdated = System.currentTimeMillis();

			brin.close();
		} catch (IOException e) {
			LOGGER.error(
					"IO Exception while connecting to {} in Transformer {}.",
					url.toString(), this.name);
			e.printStackTrace();
		}

		return outval;
	}

	/**
	 * Helper function to match a line by a given key, its position and a regular expression.
	 * 
	 * @param matcher the regex matcher to parse the line
	 * @param inVarPos the position of the input variable
	 * @param outVarPos the position of the output variable
	 * @return a String array with key at [0] and value at [1]
	 */
	private String[] matchLine(Matcher matcher, int inVarPos, int outVarPos) {
		if (matcher.matches()) {

			if (matcher.groupCount() <= exprGroupVars.length) {
				if (inVarPos >= 0 && outVarPos >= 0 && matcher.groupCount() >= inVarPos 
						&& matcher.groupCount() >= outVarPos) {
					
					return new String[] {
							matcher.group(inVarPos + 1),
							matcher.group(outVarPos + 1) };
					
				} else
					LOGGER.error(
							"Can not find variable position {} and {} in expression group variables {}.",
							inVarPos, outVarPos,
							Arrays.deepToString(exprGroupVars));
			} else
				LOGGER.error("Expression matched but not enough expression groups found ???");

		}

		return null;
	}

	/**
	 * Execution of the look-up procedure. With a given key value and position for key and result variable, it looks-up
	 * the request by executing the HTTP requrest with {@link #url}.
	 * 
	 * @param inValue the value of the key to search for
	 * @param transformInVar the variable name of the key
	 * @param transformOutVar the variable name of the result value
	 * @return the resulting String value from the look-up
	 */
	public String execute(String inValue, String transformInVar, String transformOutVar) {
		Long now = System.currentTimeMillis();
		boolean cacheDisabled = refresh < 0,
				cacheNeeded = lastUpdated == 0L || now > refresh + lastUpdated;
		
		int inVarPos = getVarPos(exprGroupVars, transformInVar), 
				outVarPos = getVarPos(exprGroupVars, transformOutVar);
				
		String[] tmpres = null;
		
		if (cacheNeeded)
			tmpres = executeNonCached(inValue, transformInVar, transformOutVar, !cacheDisabled);
		else
			if (lastMatchedLine != null) {
				tmpres = matchLine(expr.matcher(lastMatchedLine), inVarPos, outVarPos);
				
				if (tmpres[0].equals(inValue)) {
					LOGGER.debug("1st Level cache hit for {} == {} -> {} = {} in Transformer {}.", 
							transformInVar, inValue, transformOutVar, tmpres[1]);
					return tmpres[1];
				} else {
					LOGGER.debug("1st Level cache miss for {} != {} in Transformer {}. "
							+ " Executing 2nd level cached search.", transformInVar, inValue, 
							transformOutVar, tmpres[1]);
					tmpres = executeCached(inValue, inVarPos, outVarPos);
				}
			} else
				tmpres = executeCached(inValue, inVarPos, outVarPos);
		
		if (tmpres != null && tmpres.length >= 2)
			return tmpres[1];
		else
			return null;
	}

	/**
	 * Set the types of the variables named in {@link #exprGroupVars}.
	 * 
	 * @param types the Types of the variables. Must be a valid openHAB type 
	 * (org.openhab.core.library.items.* with "Item" stripped of from class name) 
	 */
	public void setTypes(String[] types) {
		this.types = types;
	}
	
	/**
	 * Get the types of the vaiables from {@link #exprGroupVars}.
	 * 
	 * @return the types of the variables
	 */
	public String[] getTypes() {
		return this.types;
	}
	
	/**
	 * Get the type of a defined variable. Valid types are defined  by org.openhab.core.library.items.* with 
	 * "Item" stripped of from class name. 
	 * 
	 * @param varname the name of the variable to request the type for
	 * @return the variable type as a Sting
	 */
	public String getType(String varname) {
		int inVarPos = getVarPos(exprGroupVars, varname);
		return (inVarPos < 0 ? null : types[inVarPos]);
	}

	/**
	 * Get the {@link Pattern} of this transformer.
	 * @return the Pattern this transformer uses
	 */
	public Pattern getExpr() {
		return expr;
	}

	/**
	 * Set the {@link Pattern} for this transformer.
	 * @param expr the pattern this transformer should use.
	 */
	public void setExpr(Pattern expr) {
		this.expr = expr;
	}

	/**
	 * Get the expression group variables which identify the {@link #expr}-Pattern group members.
	 * @return the String array of expression group variable names 
	 */
	public String[] getExprGroupVars() {
		return exprGroupVars;
	}

	/**
	 * Set the variable names of the {@link #expr}-Pattern groups.
	 * 
	 * @param exprGroupVars the String array of variable names
	 */
	public void setExprGroupVars(String[] exprGroupVars) {
		this.exprGroupVars = exprGroupVars;
	}

	/**
	 * Get the refresh interval of this transformer. If <code>0</code>, every look-up is resulting in a new request. 
	 * If <code>-1</code> a look-up is done only once after startup and always cached in future. 
	 * 
	 * @return the refresh interval in milliseconds
	 */
	public Long getRefresh() {
		return refresh;
	}

	
	/**
	 * Set the refresh interval of this transformer. If <code>0</code>, every look-up is resulting in a new request. 
	 * If <code>-1</code> a look-up is done only once after startup and always cached in future. 
	 *
	 * @param refresh refresh interval in milliseconds
	 */
	public void setRefresh(Long refresh) {
		this.refresh = refresh;
	}

	/**
	 * Get the HTTP request timeout of this transformer.
	 * 
	 * @return the timeout in milliseconds
	 */
	public int getTimeout() {
		return timeout;
	}

	/**
	 * Set the HTTP request timeout of this transformer.
	 *
	 * @param timeout the timeout in milliseconds
	 */
	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}
	
	/**
	 * Set the {@link #url} for this transformer.
	 * 
	 * @param url the look-up request url
	 */
	public void setUrl(URL url) {
		this.url = url;
	}
}
