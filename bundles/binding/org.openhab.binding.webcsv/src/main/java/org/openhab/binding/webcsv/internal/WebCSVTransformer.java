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

public class WebCSVTransformer {
	private String name;
	private URL url;
	public Pattern expr;
	public String[] exprGroupVars;
	public String lastMatchedLine = null;
	public List<String> cacheData = null;

	/**
	 * Cache refresh intervall (milliseconds), infinite (== 0) or disabled cache
	 * (&lt; 0).
	 */
	public Long refresh;
	public Long lastUpdated = 0L;
	private static final long maxlines = 1000;
	private String[] types;

	static final Logger logger = LoggerFactory
			.getLogger(WebCSVTransformer.class);

	public void setURL(URL url) {
		this.url = url;
	}

	public WebCSVTransformer(String name) {
		this.name = name;
	}

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

	public static int getVarPos(String[] group, String varname) {
		return Arrays.asList(group).indexOf(varname);
	}

	public String[] executeNonCached(String inValue, String inVar,
			String outVar, boolean fillCache) {

		String[] outval = null;

		int inVarPos = getVarPos(exprGroupVars, inVar), outVarPos = getVarPos(
				exprGroupVars, outVar);

		if (fillCache)
			cacheData = new ArrayList<String>();

		try {
			// BufferedReader brin = URLUtil.openURLStream(url);

			String tmp = HttpUtil.executeUrl("GET", url.toString(), 3000);
			
			StringReader sr = new StringReader(tmp);
			
			BufferedReader brin = new BufferedReader(sr);

			String line;
			String[] tmpres;
			boolean runner = true;
			long cntlines = maxlines;
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
			logger.error(
					"IO Exception while connecting to {} in Transformer {}.",
					url.toString(), this.name);
			e.printStackTrace();
		}

		return outval;
	}

	private String[] matchLine(Matcher matcher, int inVarPos, int outVarPos) {
		if (matcher.matches()) {

			if (matcher.groupCount() <= exprGroupVars.length) {
				if (inVarPos >= 0 && outVarPos >= 0 && matcher.groupCount() >= inVarPos && matcher.groupCount() >= outVarPos) {
					return new String[] { matcher.group(inVarPos + 1),
							matcher.group(outVarPos + 1) };
				} else
					logger.error(
							"Can not find variable position {} and {} in expression group variables {}.",
							inVarPos, outVarPos,
							Arrays.deepToString(exprGroupVars));
			} else
				logger.error("Expression matched but not enough expression groups found ???");

		}

		return null;
	}

	public String execute(String inValue, String transformInVar, String transformOutVar) {
		Long now = System.currentTimeMillis();
		boolean cacheDisabled = refresh < 0,
				cacheNeeded = lastUpdated == 0L || now > refresh + lastUpdated;
		
		int inVarPos = getVarPos(exprGroupVars, transformInVar), 
				outVarPos = getVarPos(exprGroupVars, transformOutVar);
				
		String[] tmpres = null;
		
		if(cacheNeeded)
			tmpres = executeNonCached(inValue, transformInVar, transformOutVar, !cacheDisabled);
		else
			if(lastMatchedLine != null) {
				tmpres = matchLine(expr.matcher(lastMatchedLine), inVarPos, outVarPos);
				
				if(tmpres[0].equals(inValue)) {
					logger.debug("1st Level cache hit for {} == {} -> {} = {} in Transformer {}.", 
							transformInVar, inValue, transformOutVar, tmpres[1]);
					return tmpres[1];
				} else {
					logger.debug("1st Level cache miss for {} != {} in Transformer {}. Executing 2nd level cached search.", 
							transformInVar, inValue, transformOutVar, tmpres[1]);
					tmpres = executeCached(inValue, inVarPos, outVarPos);
				}
			} else
				tmpres = executeCached(inValue, inVarPos, outVarPos);
		
		if(tmpres != null && tmpres.length >= 2)
			return tmpres[1];
		else
			return null;
	}

	public void setTypes(String[] types) {
		this.types = types;
	}
	
	public String[] getTypes() {
		return this.types;
	}
	
	public String getType(String varname) {
		int inVarPos = getVarPos(exprGroupVars, varname);
		return (inVarPos < 0 ? null : types[inVarPos]);
	}
}
