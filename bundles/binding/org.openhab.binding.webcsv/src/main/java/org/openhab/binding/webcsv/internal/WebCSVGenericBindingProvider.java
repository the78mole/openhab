/**
 * Copyright (c) 2010-2014, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.webcsv.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openhab.binding.webcsv.IWebCSVBindingProvider;
import org.openhab.core.binding.BindingConfig;
import org.openhab.core.items.Item;
import org.openhab.core.library.types.StringType;
import org.openhab.core.types.Command;
import org.openhab.model.item.binding.AbstractGenericBindingProvider;
import org.openhab.model.item.binding.BindingConfigParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * <p>
 * This class parses the WebCSV item binding data. It registers as a 
 * {@link IWebCSVBindingProvider} service as well.
 * </p>
 * 
 * <p>Here are some examples for valid binding configuration strings:
 * <ul>
 * 	<li><code>{ webcsv="&lt;myserverid:serno:20000" }</code></li>
 * 	<li><code>{ webcsv="&lt;myserverid:acphases:20000" }</code></li>
 * 	<li><code>{ webcsv="&lt;myserverid:dcinputs:20000" }</code></li>
 * 	<li><code>{ webcsv="&lt;myserverid:uac1:20000" }</code></li>
 * </ul>
 * </p>
 * 
 * <p>The general form is:
 * <code>{ webcsv="[&lt;|&gt;]ServerID:VarName:RefreshInterval" }</code>
 * </p>
 * 
 * <p>Available VarNames (for Powador 14.0 TL3 V2.02 solar inverters)  are:
 * <table>
 * <tr><td><b>Realtime</b></td><td>t,udc1,udc2,uac1,uac2,uac3,idc1,idc2,iac1,iac2,iac3,pdc,temp,status</td></tr>
 * <tr><td><b>Meta</b></td>
 * 			<td>values.meta.vars=serno,type,mac,ip,rs485,countac,countdc,
 * 			acpower,dcpower,country,language,mmisw,dspacsw,dspdcsw</td></tr>
 * <tr><td><b>Initlog</b></td><td>ymd,ym,y</td></tr>
 * </table>
 * </p>
 * 
 * <p>The 'host' referenced in the binding string is configured in the openhab.cfg file -:
 * webcsv.&lt;serverId&gt;.host=192.168.2.1
 * </p>
 * 
 * <p>'serverId' can be any alphanumeric string as long as it is the same in the binding and
 * configuration file. <b>NOTE</b>: The parameter is case sensitive!
 * </p>
 * 
 * @author the78mole (Daniel Glaser)
 * @since 1.5.0
 */
public class WebCSVGenericBindingProvider extends AbstractGenericBindingProvider implements IWebCSVBindingProvider {

	/**
	 * The default LOGGER.
	 */
	static final Logger LOGGER = LoggerFactory.getLogger(WebCSVGenericBindingProvider.class);

	/** 
	 * Artificial command for the webcsv-in configuration.
	 */
	protected static final Command IN_BINDING_KEY = StringType.valueOf("IN_BINDING");

	/**
	 * The BASE_CONFIG_PATTERN regular expression.
	 */
	private static final String BASE_CONFIG_PATTERN_STRING = "(<|>)?([0-9.a-zA-Z]+:[0-9.a-zA-Z]+:[0-9]+)";
	
	/** <p>{@link Pattern} which matches a binding configuration part. </p>
	 * 	<p>The regular expression is: {@value #BASE_CONFIG_PATTERN_STRING}</p> 
	 */
	private static final Pattern BASE_CONFIG_PATTERN =
		Pattern.compile(BASE_CONFIG_PATTERN_STRING);

	/**
	 * The IN_BINDING_PATTERN regular expression.
	 */
	private static final String IN_BINDING_PATTERN_STRING = "([0-9.a-zA-Z]+):([0-9.a-zA-Z]+):([0-9]+)";
	
	/** <p>{@link Pattern} which matches an In-Binding. </p>
	 * <p>The regular expression is: {@value #IN_BINDING_PATTERN_STRING}</p> 
	 */
	private static final Pattern IN_BINDING_PATTERN =
		Pattern.compile(IN_BINDING_PATTERN_STRING);
	

	/**
	 * {@inheritDoc}
	 */
	public String getBindingType() {
		return "webcsv";
	}

	/**
	 * No type should be invalid in general. It depends on the 
	 * configuration that is done with properties for a special
	 * device.
	 * 
	 * @param item the item whose type is validated
	 * @param bindingConfig the config string which could be used to refine the validation
	 * 
	 * @throws BindingConfigParseException - if the type of item is invalid for this binding
	 */
	@Override
	public void validateItemType(Item item, String bindingConfig) throws BindingConfigParseException {
	}
	
	/**
	 * This method is called whenever it comes
	 * across a binding configuration string for an item.
	 * 
	 * @param context a string of the context from where this item comes from. Usually the file name of the config file
	 * @param item the item for which the binding is defined
	 * @param bindingConfig the configuration string that must be processed
	 * 
	 * @throws BindingConfigParseException if the configuration string is not valid
	 */
	@Override
	public void processBindingConfiguration(String context, Item item, String bindingConfig) 
			throws BindingConfigParseException {
		
		super.processBindingConfiguration(context, item, bindingConfig);
		
		if (bindingConfig != null) {
			WebCSVBindingConfig config = parseBindingConfig(item, bindingConfig);
			addBindingConfig(item, config);
		} else {
			LOGGER.warn("bindingConfig is NULL (item=" + item + ") -> process bindingConfig aborted!");
		}
	}
	
	/**
	 * Delegates parsing the <code>bindingConfig</code> with respect to the
	 * first character (<code>&lt;</code> or <code>&gt;</code>) to the 
	 * specialized parsing methods.
	 * 
	 * @param item to parse the binding config for
	 * @param bindingConfig the config string to parse
	 * @return binding config for this item
	 * @throws BindingConfigParseException - if parsing failed
	 */
	protected WebCSVBindingConfig parseBindingConfig(Item item, String bindingConfig) 
			throws BindingConfigParseException {
		
		WebCSVBindingConfig config = new WebCSVBindingConfig();
		config.itemType = item.getClass();
		
		Matcher matcher = BASE_CONFIG_PATTERN.matcher(bindingConfig);
		
		if (!matcher.matches()) {
			throw new BindingConfigParseException("bindingConfig '" + bindingConfig 
					+ "' doesn't contain a valid binding configuration");
		}
		matcher.reset();
				
		while (matcher.find()) {
			String direction = matcher.group(1);
			String bindingConfigPart = matcher.group(2);
			
			if (direction.equals(">")) {
				// for future use (there is no out-bindung supported by WebCSV webinterface yet)
				throw new BindingConfigParseException("Out-Binding is currently not supported.");
			} else { 
				// this usually satisfies for: if (direction.equals("<")) {
				
				// In-binding is the default direction for bindings. We can omit it
				config = parseInBindingConfig(item, bindingConfigPart, config);
			}
		}
		
		return config;
	}

	/**
	 * Parses a WebCSV-in configuration by using the regular expression
	 * <code>{@value #IN_BINDING_PATTERN}</code>
	 * <code>([0-9.a-zA-Z]+:[0-9.a-zA-Z]+:[0-9.a-zA-Z]+:[0-9]+)</code>. Where the groups should 
	 * contain the following content:
	 * <ul>
	 * <li>1 - Server Host</li>
	 * <li>2 - Server Port</li>
	 * <li>3 - Variable name</li>
	 * <li>4 - Refresh Interval</li>
	 * </ul>
	 * 
	 * @param item to parse the config for
	 * @param bindingConfig the config string to parse
	 * @param config the binding configuration
	 * 
	 * @return the filled {@link WebCSVBindingConfig}
	 * @throws BindingConfigParseException if the regular expression doesn't match
	 * the given <code>bindingConfig</code>
	 */
	protected WebCSVBindingConfig parseInBindingConfig(
			Item item, String bindingConfig, WebCSVBindingConfig config) 
					throws BindingConfigParseException {
		
		Matcher matcher = IN_BINDING_PATTERN.matcher(bindingConfig);
		
		if (!matcher.matches()) 
			throw new BindingConfigParseException("bindingConfig '" + bindingConfig 
					+ "' doesn't represent a valid in-binding-configuration. " 
					+ "A valid configuration is matched by the RegExp '" + IN_BINDING_PATTERN + "'");
		
		matcher.reset();
				
		WebCSVBindingConfigElement configElement;

		while (matcher.find()) {
			configElement = new WebCSVBindingConfigElement();
			configElement.serverId = matcher.group(1);
			configElement.name = matcher.group(2);
			configElement.refreshInterval = Integer.valueOf(matcher.group(3)).intValue();

			LOGGER.debug("WebCSV: " + configElement);
			config.put(IN_BINDING_KEY, configElement);
		}
		
		return config;
	}


	/**
	 * Get the type of a given item. The item is referenced by it's name.
	 * 
	 * @param itemName the name of the item to get the type for
	 * @return the type of the item
	 */
	public Class<? extends Item> getItemType(String itemName) {
		WebCSVBindingConfig config = (WebCSVBindingConfig) bindingConfigs.get(itemName);
		
		return config != null ? config.itemType : null;
	}
	
	/**
	 * Getting the host makes no sense here. The serverId is a delegation to the 
	 * correct data retriever and that should be used to access data.
	 * 
	 * @param itemName the name of the item to get the host for
	 * @return the host of this item
	 */
	public String getHost(String itemName) {
		WebCSVBindingConfig config = (WebCSVBindingConfig) bindingConfigs.get(itemName);
		
		return config != null && config.get(IN_BINDING_KEY) != null 
				? config.get(IN_BINDING_KEY).host : null;
	}
	
	/**
	 * Getting the port makes no sense here. The serverId is a delegation to the 
	 * correct data retriever and that should be used to access data.
	 * 
	 * @param itemName the name of the item
	 * @return the port the host uses for this item
	 */
	public String getPort(String itemName) {
		WebCSVBindingConfig config = (WebCSVBindingConfig) bindingConfigs.get(itemName);
		
		return config != null && config.get(IN_BINDING_KEY) != null 
				? config.get(IN_BINDING_KEY).port : null;
	}	
	
	/**
	 * {@inheritDoc}
	 */
	public String getName(String itemName) {
		WebCSVBindingConfig config = (WebCSVBindingConfig) bindingConfigs.get(itemName);
		return config != null && config.get(IN_BINDING_KEY) != null 
				? config.get(IN_BINDING_KEY).name : null;
	}

	/**
	 * {@inheritDoc}
	 */
	public int getRefreshInterval(String itemName) {
		WebCSVBindingConfig config = (WebCSVBindingConfig) bindingConfigs.get(itemName);
		return config != null && config.get(IN_BINDING_KEY) != null 
				? config.get(IN_BINDING_KEY).refreshInterval : 0;
	}
	
	/**
	 * {@inheritDoc}
	 */
	public List<String> getInBindingItemNames() {
		List<String> inBindings = new ArrayList<String>();
		
		for (String itemName : bindingConfigs.keySet()) {
			WebCSVBindingConfig httpConfig = (WebCSVBindingConfig) bindingConfigs.get(itemName);
			if (httpConfig.containsKey(IN_BINDING_KEY)) {
				inBindings.add(itemName);
			}
		}
		
		return inBindings;
	}

	
	/**
	 * Resolves the serverId for this binding.
	 * @param itemName the name of the item to get the serverId for
	 * @return the serverId
	 */
	@Override
	public String getServerId(String itemName) {
		WebCSVBindingConfig config = (WebCSVBindingConfig) bindingConfigs.get(itemName);
		
		return config != null && config.get(IN_BINDING_KEY) != null 
				? config.get(IN_BINDING_KEY).serverId : "";
	}


	/**
	 * This is an internal data structure to map commands to 
	 * {@link WebCSVBindingConfigElement }. There will be map like 
	 * <code>ON->WebCSVBindingConfigElement</code>
	 */
	static class WebCSVBindingConfig extends HashMap<Command, WebCSVBindingConfigElement> implements BindingConfig {
		
		/** The generated serial version UID of this inner class. */
		private static final long serialVersionUID = 946984678609385662L;
		
		/** Holds the item type. */
		private Class<? extends Item> itemType;
	}

	/**
	 * This is an internal data structure to store information from the binding
	 * config strings and use it to answer the requests to the HTTP binding 
	 * provider.
	 */
	static class WebCSVBindingConfigElement implements BindingConfig {
		
		/** The serverId for this config element. */
		private String serverId;
		/** The host for this config element. */
		private String host;
		/** The port the host uses for this config element. */
		private String port;
		/** The name of this config element. */
		private String name;
		/** The refresh interval for this config element. */
		private int refreshInterval;
		
		/**
		 * A simple toString method mostly useful for debugging.
		 * 
		 * @return a String representation for this object
		 */
		@Override
		public String toString() {
			return "WebCSVBindingConfigElement [serverId=" + serverId + ", host=" + host
					+ ", port=" + port + ", name=" + name + ", refreshInterval=" + refreshInterval + "]";
		}
	}
	
	
}
