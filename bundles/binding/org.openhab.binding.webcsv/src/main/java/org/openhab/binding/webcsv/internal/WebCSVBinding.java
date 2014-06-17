/**
 * Copyright (c) 2010-2014, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.webcsv.internal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.openhab.binding.webcsv.WebCSVBindingProvider;
import org.openhab.core.binding.AbstractActiveBinding;
import org.openhab.core.items.Item;
import org.openhab.core.types.State;
import org.openhab.io.net.http.HttpUtil;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * An active binding which requests the WebCSV data.
 *
 * This class parses the information from the WebCSV configuration file.
 * 
 * @author the78mole (Daniel Glaser)
 * @since 1.5.0
 */
public class WebCSVBinding extends
		AbstractActiveBinding<WebCSVBindingProvider> implements
		ManagedService {

	static final Logger logger = LoggerFactory.getLogger(WebCSVBinding.class);

	/**
	 * the timeout to use for connecting to a given host (defaults to 5000
	 * milliseconds)
	 */
	// TODO forward this to the data retrievers
	private long timeout = 5000;

	/**
	 * the interval to find new refresh candidates (defaults to 1000
	 * milliseconds)
	 */
	private long granularity = 10000;

	/**
	 *  RegEx to validate a config <code>'^(.*?)\\.(host|port)$'</code> 
	 */
	private static final Pattern EXTRACT_CONFIG_PATTERN = Pattern.compile("^(.*?)\\.(host|device)$");
	
	private Map<String, WebCSVConfig> serverList = new HashMap<String, WebCSVConfig>();
	
	private Map<String, Properties> propList = new HashMap<String, Properties>();

//	private long refreshInterval = 0L;
	private Map<WebCSVBindingProvider, Long> lastUpdated = new HashMap<WebCSVBindingProvider, Long>(); 

	public WebCSVBinding() {
	}
	

	@Override
	public void activate() {
		logger.debug("WebCSV: Activate");
		super.activate();
		
		// Now build the device driver stubs (parse and unserialize only testing rules)
		BundleContext bc = WebCSVActivator.getContext();
		Enumeration<URL> ePropFiles = bc.getBundle().findEntries("src/main/resources/", "*.properties", true);
		while(ePropFiles.hasMoreElements()) {
			Properties ptmp = new Properties();
			try {
				InputStream propFile = ePropFiles.nextElement().openStream();
				InputStream pis = propFile;
				ptmp.load(pis);
				String stmp = null;
				stmp = ptmp.getProperty("name", stmp);
				stmp = ptmp.getProperty("properties.name", stmp);
				if(stmp != null)
					propList.put(stmp, ptmp);
				else
					logger.warn("Properties for file {} do not contain a valie 'name' property. It is not read.", stmp);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}

	/**
	 * @{inheritDoc}
	 */
	@Override
	protected long getRefreshInterval() {
		return granularity;
	}

	@Override
	protected String getName() {
		return "WebCSV Refresh Service";
	}
	
	/**
	 * @{inheritDoc}
	 */
	@Override
	public void execute() {
		for (WebCSVBindingProvider provider : providers) {
			
			for (String itemName : provider.getInBindingItemNames()) {
				
				// Get the unit host from the binding, and relate that to the config
				String unit = provider.getServerId(itemName);
				WebCSVConfig server = serverList.get(unit);
				
				String pname = provider.getName(itemName);
				
				WebCSVValue pv = server.getWebCSVValue(pname);
				
				Class<? extends Item> itemType = provider.getItemType(itemName);
				State pValState = pv.getTypeMatchingValue(itemType);
					
				if(pValState != null) {
					eventPublisher.postUpdate(itemName, pValState);
				}

				lastUpdated.put(provider,System.currentTimeMillis());
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public void updated(Dictionary<String, ?> config) throws ConfigurationException {
		if(config != null) {
			Enumeration<String> keys = config.keys();
			
			if ( serverList == null ) {
				serverList = new HashMap<String, WebCSVConfig>();
			}
			
			while (keys.hasMoreElements()) {
				String key = (String) keys.nextElement();

				// the config-key enumeration contains additional keys that we
				// don't want to process here ...
				if ("service.pid".equals(key)) {
					continue;
				}
				
				Matcher matcher = EXTRACT_CONFIG_PATTERN.matcher(key);

				if (!matcher.matches()) {
					continue;
				}

				matcher.reset();
				matcher.find();

				String serverId = matcher.group(1);
				String spec = matcher.group(2);
				
				if("device".equals(spec)) {
					logger.warn("WebCSV device access not yet implemented.");
					continue;
				} else if("host".equals(spec)) {

					String host = (String) config.get(key);
					WebCSVConfig deviceConfig = new WebCSVConfig(serverId, host, searchConfig(host), null);
					if(deviceConfig != null)
						serverList.put(serverId, deviceConfig);
					
				}
			
			}
			
			String timeoutString = (String) config.get("timeout");
			if (StringUtils.isNotBlank(timeoutString)) {
				timeout = Long.parseLong(timeoutString);
			}

			String granularityString = (String) config.get("granularity");
			if (StringUtils.isNotBlank(granularityString)) {
				granularity = Long.parseLong(granularityString);
			}

			setProperlyConfigured(true);
			
		}
	}
	
	private static final Pattern varsFind = Pattern.compile("(%\\{[0-9a-zA-Z_]+\\})"); 
	
	private Properties searchConfig(String host) {
		Set<String> propKeys = propList.keySet();

		Iterator<String> ikey = propKeys.iterator();
		boolean foundConfig = false;
		String foundKey = null;
		while(ikey.hasNext() && !foundConfig) {
			String key = ikey.next();
			Properties sProp = propList.get(key);
			if(!sProp.containsKey("test.url")) {
				logger.warn("Properties {} do not contain a check.", ikey);
				continue;
			}
			
			String testURL = sProp.getProperty("test.url");
			Matcher matcher = varsFind.matcher(testURL);
			
			while(matcher.find()) {
				// Substitute the vars with their corresponding values
				String embVar = matcher.group(1);
				String sVar = embVar.substring(2, embVar.length()-1);
				String varValue;
				if("host".equals(sVar))
					varValue = host;
				else
					varValue = sProp.getProperty("vars."+sVar);
				if(varValue != null) {
					testURL = testURL.replace(embVar, varValue);
					matcher = varsFind.matcher(testURL);
				} else {
					logger.error("Could not replace {} by {} in {}. Maybe the variable is unknown.", embVar, sVar, testURL);
				}
			}
			
			// Got the test location, now retrieving check data
			URL url = null;
			try {
				url = new URL(testURL);
				
				String retval = HttpUtil.executeUrl("GET", url.toString(), 3000);

				if(retval == null) {
					logger.warn("Could not connect to {}. Server returned {}. Seems not to be a {}", testURL, key);
					continue;
				}
				
				if(!sProp.containsKey("test.expr")) {
					// If no expression is given, a 200 OK status is all we need...
					// but we do not break search. Maybe we find a better one with matching regex
					logger.warn("Properties {} do not contain a regex check.", ikey);
					foundKey = key;
					continue;
				} else {
					// If we have an expression, we can check the type of the system

					BufferedReader brin = new BufferedReader(new StringReader(retval));
					
					// We read now line by line and check them against the pattern. 
					// If one matches, we assume that we have the appropriate system 
					String line;
					while((line = brin.readLine()) != null) {
						String mexpr = sProp.getProperty("test.expr").replaceAll("^\"", "").replaceAll("\"$", "");
						if (line.matches(mexpr)) {
							foundConfig = true;
							foundKey = key;
						}
					}
				}

			} catch (MalformedURLException e) {
				logger.error("{} is not a valid url used by {}.", url);
			} catch (IOException e) {
				logger.error("An IO Exception occured for {}: \r\n {}", url, e.toString());
			}

				
		}
		
		if(foundConfig && foundKey != null) {
			return propList.get(foundKey);
		}

		logger.warn("No config found for {}.", host);
		return null;


	}
	
}
