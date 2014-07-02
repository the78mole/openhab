/**
 * Copyright (c) 2010-2014, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.webcsv.internal;

import java.io.IOException;
import java.io.InputStream;
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
import org.openhab.binding.webcsv.IWebCSVBindingProvider;
import org.openhab.core.binding.AbstractActiveBinding;
import org.openhab.core.items.Item;
import org.openhab.core.types.State;
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
		AbstractActiveBinding<IWebCSVBindingProvider> implements
		ManagedService {

	/**
	 * The default LOGGER.
	 */
	static final Logger LOGGER = LoggerFactory.getLogger(WebCSVBinding.class);

	/**
	 * The timeout to use for connecting to a given host (defaults to 5000 milliseconds).
	 */
	private int timeout = 5000;

	/**
	 * the interval to find new refresh candidates (defaults to 1000 milliseconds).
	 */
	private long granularity = 10000;

	/**
	 *  RegEx to validate a config <code>'^(.*?)\\.(host|port)$'</code> .
	 */
	private static final Pattern EXTRACT_CONFIG_PATTERN = Pattern.compile("^(.*?)\\.(host|device)$");
	
	/**
	 * Holds the list of hosts (IDs from openHAB configuration file) with their corresponding config. 
	 */
	private Map<String, WebCSVConfig> serverList = new HashMap<String, WebCSVConfig>();
	
	/**
	 * Holds a list of available driver-like properties files.
	 */
	private Map<String, Properties> propList = null;

	/**
	 * Stores the data providers with the timestamps when data was last refreshed.
	 */
	private Map<IWebCSVBindingProvider, Long> lastUpdated = new HashMap<IWebCSVBindingProvider, Long>(); 

	/**
	 * Activates this binding. It will initially generate the properties file list.
	 */
	@Override
	public void activate() {
		LOGGER.debug("WebCSV: Activate");
		super.activate();
		
		rebuildPropertiesList();

	}


	/**
	 * Collects all WebCSV property files from src/main/resources and stores 
	 * it in propList.
	 * 
	 * <p>TODO Check if properties list is up to date and only actualize it. 
	 * With many property files this could lead to a reasonable performance improvement.
	 * Another possibility for many configurations could be to only keep stubs and testing 
	 * rules and finalize the unserialization when a matching configuration was found</p>
	 */
	private void rebuildPropertiesList() {

		propList = new HashMap<String, Properties>(); 
		
		// Now build the device driver stubs
		BundleContext bc = WebCSVActivator.getContext();
		Enumeration<URL> ePropFiles = bc.getBundle().findEntries("src/main/resources/", "*.properties", true);
		while (ePropFiles.hasMoreElements()) {
			Properties ptmp = new Properties();
			URL propFile = ePropFiles.nextElement();
			try {
				InputStream pis = propFile.openStream();			
				ptmp.load(pis);
				String stmp = null;
				stmp = ptmp.getProperty("name", stmp);
				stmp = ptmp.getProperty("properties.name", stmp);
				if (stmp != null)
					propList.put(stmp, ptmp);
				else
					LOGGER.warn("Properties for file {} do not contain a valid 'name' property. It is not read.", stmp);
			} catch (IOException e) {
				LOGGER.error("An IOException occured for property {}.", propFile.toString());
				e.printStackTrace();
			}
		}
	}

	/**
	 * {@inheritDoc} 
	 */
	@Override
	protected long getRefreshInterval() {
		return granularity;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected String getName() {
		return "WebCSV Refresh Service";
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void execute() {
		for (IWebCSVBindingProvider provider : providers) {
			
			for (String itemName : provider.getInBindingItemNames()) {
				
				// Get the unit host from the binding, and relate that to the config
				String unit = provider.getServerId(itemName);
				WebCSVConfig server = serverList.get(unit);
				
				String pname = provider.getName(itemName);
				
				WebCSVValue pv = server.getWebCSVValue(pname);
				
				Class<? extends Item> itemType = provider.getItemType(itemName);
				State pValState = pv.getTypeMatchingValue(itemType);
					
				if (pValState != null) {
					eventPublisher.postUpdate(itemName, pValState);
				}

				lastUpdated.put(provider, System.currentTimeMillis());
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public void updated(Dictionary<String, ?> config) throws ConfigurationException {
		if (config != null) {
			Enumeration<String> keys = config.keys();
			
			if (serverList == null) 
				serverList = new HashMap<String, WebCSVConfig>();
			
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
				
				if ("device".equals(spec)) {					
					
					// TODO implement the device access e.g. serial cable or virtual com port 
					LOGGER.warn("WebCSV device access not yet implemented.");
					continue;
					
				} else if ("host".equals(spec)) {

					String host = (String) config.get(key);
					WebCSVConfig deviceConfig = new WebCSVConfig(serverId, host, searchConfig(host));
					if (deviceConfig != null)
						serverList.put(serverId, deviceConfig);
					
				}
			
			}
			
			String timeoutString = (String) config.get("timeout");
			if (StringUtils.isNotBlank(timeoutString)) {
				timeout = Integer.parseInt(timeoutString);
			}
			for (WebCSVConfig sval : serverList.values())
				sval.setTimeout(timeout);

			String granularityString = (String) config.get("granularity");
			if (StringUtils.isNotBlank(granularityString)) {
				granularity = Long.parseLong(granularityString);
			}

			setProperlyConfigured(true);
			
		}
	}
	
	/**
	 * Searches all available properties until the meta information of the host 
	 * matches the url and regular expression defined in the properties. 
	 * 
	 * @param host the host to search a config for
	 * @return first property that matches with the hosts meta information
	 */
	private Properties searchConfig(String host) {
		
		if (propList == null)
			return null;
		
		Set<String> propKeys = propList.keySet();

		// Stepping through all the configs to find the handler for the given host.
		Iterator<String> ikey = propKeys.iterator();

		while (ikey.hasNext()) {
			String key = ikey.next();
			Properties sProp = propList.get(key);
			
			Boolean testResult = WebCSVChecker.doWebCSVCheck(sProp, true);
			
			if (testResult != null && testResult) 
				return sProp;
			
		}
		
		// We did not find a matching configuration...
		return null;
		
	}
	
}
