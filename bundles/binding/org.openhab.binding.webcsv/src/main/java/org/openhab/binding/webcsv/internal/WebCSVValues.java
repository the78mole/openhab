package org.openhab.binding.webcsv.internal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.openhab.io.net.http.HttpUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebCSVValues {

	private String name;
	public String getName() {
		return name;
	}

	public URL url;
	public void setURL(URL url) {
		this.url = url;
	}

	public String split;
	public void setSplit(String split) {
		this.split=split.replaceAll("(^\"|\"$)", "");
	}
	
	public String getSplit() {
		return split;
	}

	public Pattern expr;
	protected String[] vars;
	public String[] getVars() {
		return vars;
	}

	public String[] transOutVars;
	public Long lastUpdate = 0L;
	public Boolean updateNeeded() {
		if(refresh == null) return null;
		if(refresh == -1) return true;
		else if(refresh == 0) return false;
		return  System.currentTimeMillis() >= lastUpdate + refresh;
	}

	public Boolean updateNeeded(WebCSVValue pval) {
		if(System.currentTimeMillis() >= pval.lastUpdate + refresh) return true;
		if(lastUpdate == 0L) return true;
		if(refresh == null) return null;
		if(refresh == -1) return true;
		else if(refresh == 0) return false;
		return  System.currentTimeMillis() >= lastUpdate + refresh;		
	}

	private Long refresh;
	public void setRefresh(String refresh) {
		if(refresh == null) {
			this.refresh = 0L;
		} else
			try {
				this.refresh = Long.parseLong(refresh);
			} catch (NumberFormatException e) {
				this.refresh = 0L;
				LOGGER.error("Refresh with value {} can not be parsed to a Long value. Setting to not refresh.", refresh);
			}
	}

	public void setRefresh(Long refresh) {
		this.refresh = refresh;
	}

	public Long getRefresh() {
		return refresh;
	}

	private Boolean cacheEnabled;
	public Map<String,WebCSVValue> values = new TreeMap<String, WebCSVValue>();
	public WebCSVValue[] getValues() {
		return values.values().toArray(new WebCSVValue[0]);
	}

	/**
	 * The default LOGGER.
	 */
	static final Logger LOGGER = LoggerFactory.getLogger(WebCSVValues.class);
	private static final int READ_MAX_LINES = 100;
	
	private static final String[] CACHE_ENABLED = new String[] {"true", "enabled", "on", "cached"};
	private static final String[] CACHE_DISABLED = new String[] {"false", "disabled", "off", "uncached"};
	
	public WebCSVValues(String name, String[] vars, boolean createValueObjs) {
		if(name != null) 
			this.name = name;
		this.vars = vars;
		if(createValueObjs)
			for(String svar : vars) 
				values.put(svar, new WebCSVValue(svar, this));
	}

	public Boolean getCacheEnabled() {
		return cacheEnabled;
	}

	public void setCacheEnabled(Boolean cacheEnabled) {
		this.cacheEnabled = cacheEnabled;
	}

	public void setCacheEnabled(String cacheEnabled) {
		String lcce = cacheEnabled.toLowerCase();
		if(Arrays.asList(CACHE_DISABLED).contains(lcce))
			this.cacheEnabled = false;
		else if(Arrays.asList(CACHE_ENABLED).contains(lcce))
			this.cacheEnabled = true;
		else {
			LOGGER.warn("{} is not a valid cache setting. Must be one of {} or {}", cacheEnabled, 
					Arrays.deepToString(CACHE_ENABLED), Arrays.deepToString(CACHE_DISABLED));
			this.cacheEnabled = true;
		}
	}

	public boolean updateValues() {
		long now = System.currentTimeMillis();
		boolean updated = false;
		if(lastUpdate == 0L || refresh == -1L || (refresh > 0 && now >= lastUpdate + refresh)) {
			
			String tmp = HttpUtil.executeUrl("GET", url.toString(), 3000);
			
			// Check if we fetched successfully
			if(tmp == null) return false;
			
			StringReader sr = new StringReader(tmp);
			
			BufferedReader brin = new BufferedReader(sr);
			
			String line;
			// TODO: Implement the non split regex version and extend it as needed
			
			String[] tmpres = new String[0];
			int countlines = READ_MAX_LINES;
			try {
				while(countlines-- > 0 && (line = brin.readLine()) != null && tmpres.length < vars.length) {
					tmpres = line.split(this.split);
				}
				brin.close();
			
				// Some hosts (e.g. Powador inverters) put a semicolon at the end of each line. 
				// Therefore it usually splits one value more than vars are given. 
				if(tmpres.length >= vars.length) {
					for(int i = 0; i < vars.length; i++) {
						values.get(vars[i]).updateValue(tmpres[i]);
					}
					lastUpdate = System.currentTimeMillis();
					updated = true;
				} else
					LOGGER.error("Result {} from WebCSV host was not matching the expected one from properties.", this.name);

			} catch (IOException e) {
				LOGGER.error("IO-Exception occured on connecting/while reading for url {}.", url);
				e.printStackTrace();
			}
				 
			
		} else 
			LOGGER.debug("Update of values not needed. Skipping.");
		
		return updated;
	}
	
	public WebCSVValue getWebCSVValue(String varname) {
		return values.get(varname);
	}
	
	public void setFactors(String[] factors) {
		if(factors.length == vars.length) {
			for(int i=0; i<vars.length; i++) {
				values.get(vars[i]).setFactor(factors[i]);
			}
		} else
			LOGGER.warn("Count of vars ({}) and factors ({}) differ for values.{}.", vars.length, factors.length, this.name);
	}

	public void setTypes(String[] types) {
		if(types.length == vars.length) {
			for(int i=0; i<vars.length; i++) {
				values.get(vars[i]).setType(types[i]);
			}
		} else
			LOGGER.warn("Count of vars ({}) and types ({}) differ for values.{}.", vars.length, types.length, this.name);
	}
	
	public String toString() {
		return "[url="+(url != null ? url : "\"\"")+
				( expr != null ? ",expr="+ expr : "" )+
				( split != null ? ","+"split="+ split : "" )+
				",values={"+(values!=null ? values.size() : "")+"},"+
				"cacheEnabled="+cacheEnabled+",refresh="+refresh+",lastUpdate="+lastUpdate+"]";
	}

}
