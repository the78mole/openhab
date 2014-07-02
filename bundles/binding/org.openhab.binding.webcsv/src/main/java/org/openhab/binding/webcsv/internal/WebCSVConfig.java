package org.openhab.binding.webcsv.internal;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is the configuration class for WebCSV. It holds all configuration data for a single data provider/server. It
 * reads the properties from a configuration file in src/main/resources to describe a csv based service.  
 * 
 * @author the78mole@github
 *
 */
class WebCSVConfig {

	/**
	 * The default LOGGER.
	 */
	static final Logger LOGGER = LoggerFactory.getLogger(WebCSVConfig.class);

	/**
	 * The Pattern for matching variables %{VARNAME}.
	 * <b>group(0)</b> then contains the full variable
	 * <b>group(1)</b> only the variable configName 
	 */
	static final Pattern VARIABLES_PATTERN = Pattern.compile("%\\{([0-9a-zA-Z_-]+)\\}");

	/**
	 * The serverId is defined in openhab.cfg to be referenced by items in the corresponding file.
	 */
	private String serverId;
	
	/**
	 * The host that is defined for this serverId in openhab.cfg.
	 */
	private String host;
	
	/**
	 * The name of the configuration as stated in the properties file.
	 */
	private String configName;
	
	/**
	 * The timeout of http requests. Some buggy devices (e.g. Powador inverters) sometimes even don't respond to 
	 * requests. Therefore, timeout is a neccessity.
	 */
	private int timeout = 3000;
	
	/**
	 * Contains a map to resolve a string representation of a variable to its value object.
	 */
	private Map<String, WebCSVValue> valueMap = new TreeMap<String, WebCSVValue>();
	// private Map<String, WebCSVValues> values = new HashMap<String, WebCSVValues>();

	/**
	 * Stores the maturity of the configuration. Possible values are: draft, unstable, testing, stable, rocksolid
	 */
	private String configMaturity;
	
	/**
	 * The author(s) of this configuration.
	 */
	private String configAuthor;

	/**
	 * A pattern to trim quotes and make varnames accessible by matcher groups from regular expression properties.
	 */
	private static final Pattern REGEX_TRANSFORM = Pattern.compile("\"([^\"]+)\",(.*)$");

	/**
	 * The default refresh rate in milliseconds for all hosts not defining an individual value. It is checked by the 
	 * {@link WebCSVGenericBindingProvider} with the granularity defined in openhab.cfg.
	 */
	private static final String DEFAULT_REFRESH = "30000";
	
	/**
	 * By default, the cache is activated with a timeout of refresh milliseconds.	
	 */
	private static final String DEFAULT_CACHE = "true";

	/**
	 * The constructor of this class.
	 *  
	 * @param locServerId the id that was given for this host by openhab.cfg
	 * @param locHost the hostname or IP address for the host linked to this configuration
	 * @param props the properties/configuration for this host
	 */
	public WebCSVConfig(String locServerId, String locHost, Properties props) {

		this.serverId = locServerId;
		if (host != null)
			this.host = locHost;
		else
			LOGGER.warn("No host argument supplied for constructor call! Probably problems will arise later on.");
		if (props != null)
			processProperties(props);
	}

	/**
	 * Resolves a variable name to a value object.
	 * 
	 * @param varname the name of the variable to resolve
	 * @return the value object for the requested variable
	 */
	public WebCSVValue getWebCSVValue(String varname) {
		return valueMap.get(varname);
	}
	
	/**
	 * Expands a given URL with the variables given in the vars Dictionary.
	 * 
	 * @deprecated As the Dictionary class itself is deprecated, the method 
	 * expandURL with the Map interface should be used. 
	 * 
	 * @param strurl
	 * 		The url to expand, containing variables.
	 * @param vars
	 * 		The variables the url should be expanded with as a Dictionary. 
	 * @return
	 * 		an URL object that contains the expanded url.
	 */
	public static URL expandURL(String strurl, Dictionary<String, String> vars) {
		
		Enumeration<String> varKeys = vars.keys();
		HashMap<String, String> varmap = new HashMap<String, String>();
		
		String varelem;
		while (varKeys.hasMoreElements()) {
			varelem = varKeys.nextElement();
			varmap.put(varelem, vars.get(varelem));
		}
		
		return expandURL(strurl, varmap);
	}

	/**
	 * Expands a given URL with the variables given in the vars Map.
	 * 
	 * @param strurl
	 * 		The url to expand, containing variables.
	 * @param vars
	 * 		The variables the url should be expanded with as a Map. 
	 * @return
	 * 		an URL object that contains the expanded url.
	 */
	public static URL expandURL(String strurl, Map<String, String> vars) {
	
		String strurlwork = new String(strurl);
		// Parse all stuff and substitute the variables
		for (String svar : vars.keySet()) {
			String value = vars.get(svar);
			Matcher varmatch = VARIABLES_PATTERN.matcher(strurlwork);
			while (varmatch.find()) {
				if (vars.containsKey(varmatch.group(1)))
					strurlwork = strurlwork.replace(varmatch.group(0),
							vars.get(varmatch.group(1)));
				else
					LOGGER.warn(
							"Substitution of variable {} in url property {} ({}) is not possible. Variable is unknown.",
							varmatch.group(1), strurl, strurlwork);
				varmatch = VARIABLES_PATTERN.matcher(strurlwork);
			}
			vars.put(svar, value);
		}
	
		URL tmpurl = null;
		try {
			tmpurl = new URL(strurlwork);
		} catch (MalformedURLException e) {
			LOGGER.error("Url {} is malformed, expanded from {}.", tmpurl, strurl);
		}
	
		return tmpurl;
	}

	/**
	 * This method processes the properties handed over from the constructor.
	 * 
	 * @param props the properties read in from configuration
	 */
	private void processProperties(Properties props) {

		// Get the configName, maturity and author of this config
		configName = props.getProperty("properties.name", "undefined");
		configMaturity = props.getProperty("properties.maturity", "draft");
		configAuthor = props.getProperty("properties.author", "the78mole@github");

		String msg = new String("You are using a {} version of WebCSV-Configuration {}.\r\n" 
				+ " Please report errors to the author {}.");
		
		if ("undefined".equals(configName))
			LOGGER.warn("properties.name is not defined in {}.", props);
		else if (LOGGER.isDebugEnabled() && "rocksolid".equalsIgnoreCase(configMaturity))
			LOGGER.debug(msg, configMaturity, configName, configAuthor);
		else if ("stable".equalsIgnoreCase(configMaturity))
			LOGGER.debug(msg, configMaturity, configName, configAuthor);
		else if ("testing".equalsIgnoreCase(configMaturity))
			LOGGER.info(msg, configMaturity, configName, configAuthor);
		else
			LOGGER.warn(msg, configMaturity, configName, configAuthor);

		// 2nd get the check
		// pdchecker = new WebCSVChecker();
		// pdactive = pdchecker.doWebCSVCheck(false);

		// 1st get all the vars and substitute themselves recursively
		Map<String, String> vars, options;
		
		vars = processPropertiesVars(props);

		// Now process the options
		options = processPropertiesOptions(props);

		// Now process the transform data suppliers
		Map<String, WebCSVTransformer> trans = processPropertiesTransforms(props, vars);

		// Now process the values
		processPropertiesValues(props, vars, options, trans, valueMap);

	}

	/**
	 * Processes the value properties and returns the map that resolves variable
	 * names to value collections, that handle the data retrieval.
	 *
	 * @param props the Properties to process
	 * @param vars the already extracted property variables from the properties to substitute the stuff
	 * @param options options from properties
	 * @param trans the already extracted transformation objects
	 * @param locValueMap the Map where to store the mapping variable to WebCSVValue object
	 * @return The Mapping from variable configName to value collection
	 */
	private Map<String, WebCSVValues> processPropertiesValues(Properties props, Map<String, String> vars, 
			Map<String, String> options, Map<String, WebCSVTransformer> trans, Map<String, WebCSVValue> locValueMap) {

		Map<String, WebCSVValues> vals;
		// First collect all variable names and create the values objects
		vals = preProcessPropertiesValues(props, options, locValueMap);
		// The collect the variables stuff
		vals = postProcessPropertiesValues(props, vars, trans, locValueMap, vals);

		return vals;
	}

	/**
	 * Preprocesses the variables list. Preprocessing creates an empty object to store all further information for this
	 * value. This is necessary, because properties can given in any order, e.g. variable options first, then variable
	 * names. So, creating the named objects first in a single preprocessing step avoids caching every information.  
	 * 
	 * @param props the properties for this config
	 * @param options options for this config
	 * @param locValueMap the value map 
	 * @return the map from variable names to values collections
	 */
	private Map<String, WebCSVValues> preProcessPropertiesValues(Properties props, Map<String, String> options, 
			Map<String, WebCSVValue> locValueMap) {
			
		String[] propkeysplit;
		String propval;
		Map<String, WebCSVValues> vals = new TreeMap<String, WebCSVValues>();
		for (Object sProp : props.keySet()) {
			propkeysplit = ((String) sProp).split("\\.");
			String propValId = propkeysplit[1],
					propSub = (propkeysplit.length > 2 ? propkeysplit[2] : null);
			propval = props.getProperty((String) sProp);

			if ("values".equals(propkeysplit[0])) {
				if ((propkeysplit.length == 4 && "transforms".equals(propSub))
						|| propkeysplit.length == 3) {

					// vars example
					// values.<valuesID>.vars=<var1>,<var2>,...,<varN>
					if ("vars".equals(propSub)) {
						WebCSVValues pvtmp = vals.get(propValId);
						if (pvtmp == null) {
							pvtmp = new WebCSVValues(propValId, propval.split(","), true);
							pvtmp.setTimeout(getTimeout());
						}
						// set a default value for refresh
						pvtmp.setRefresh(options.containsKey("refresh") ? options.get("refresh") : DEFAULT_REFRESH);
						pvtmp.setCacheEnabled(options.containsKey("cache") ? options.get("cache") : DEFAULT_CACHE);
//						pvtmp.setRefresh(props.getProperty("options.refresh", "30000"));
//						pvtmp.setCacheEnabled(props.getProperty("options.cache", "enabled"));

						for (WebCSVValue spv : pvtmp.getValues()) {
							locValueMap.put(spv.getName(), spv);
						}
						vals.put(propValId, pvtmp);
					}
				} else {
					LOGGER.error("Wrong property key {} for values property type."
							+ "Needs 4 parts with transform, otherwise 3 parts, separated by dot.", sProp);
				}

			}
		}
		return vals;
	}

	/**
	 * Postprocesses the variable configuration, resulting in a map with variable name as key and the value objects as
	 * values, containing all information necessary to access the variable content.
	 * 
	 * @param props the properties to process
	 * @param vars the map of variables
	 * @param trans transformations given in the configuration
	 * @param locValueMap a mapping for variable names to value objects
	 * @param vals a map containing the preprocessed values objects
	 * @return the completely processed variable names to values objects map
	 */
	private Map<String, WebCSVValues> postProcessPropertiesValues(Properties props, Map<String, String> vars,
			Map<String, WebCSVTransformer> trans, Map<String, WebCSVValue> locValueMap, 
			Map<String, WebCSVValues> vals) {
		
		String[] propkeysplit;
		String propval;
		for (Object sProp : props.keySet()) {
			propkeysplit = ((String) sProp).split("\\.");
			propval = props.getProperty((String) sProp);
			if ("values".equals(propkeysplit[0]) && propkeysplit.length >= 3) {

				String propValId = propkeysplit[1],
						propSub = propkeysplit[2],
						propSubTransVar = propkeysplit.length == 4 ? propkeysplit[3] : null;

				if (propkeysplit.length == 3 || (propkeysplit.length == 4 && "transforms".equals(propSub))) {

					WebCSVValues pvals = vals.get(propValId);
					if (pvals != null) {
						if ("factors".equals(propSub))
							pvals.setFactors(propval.split(","));
						else if ("types".equals(propSub))
							pvals.setTypes(propval.split(","));
						else if ("split".equals(propSub))
							pvals.setSplit(propval);
						else if ("url".equals(propSub))
							pvals.setUrl(expandURL(propval, vars));
						else if ("refresh".equals(propSub))
							pvals.setRefresh(propval);
						else if ("transforms".equals(propSub)) {
							String[] transOpts = propval.split(",");
							if (transOpts.length == 3) {
								String transKey = transOpts[0], transInRel = transOpts[1], transOutRel = transOpts[2];
								WebCSVTransformer ttmp = trans.get(transKey);
								WebCSVValue pvtmp = locValueMap.get(propSubTransVar);
								pvtmp.setTransform(ttmp, transInRel, transOutRel);
							} else
								LOGGER.error("Transformation property {} invalid. Syntax is: "
										+ "<transformation.name>,<inputvar>,<outputvar>", sProp);

						}
					} else
						LOGGER.error(
								"This should never happen. There are no vars specified for {}.",
								propValId);

				} else
					LOGGER.error("Malformed property configName {}", sProp);
			}
		}
		return vals;
	}
	
	/**
	 * Collects all transformation definitions from properties.
	 * 
	 * @param props the properties to process
	 * @param locVars the variables eventually needed for expanding the transformation url
	 * @return a map of tansforms, keyed by their names
	 */
	private Map<String, WebCSVTransformer> processPropertiesTransforms(
			Properties props, Map<String, String> locVars) {
		
		String[] propkeysplit;
		String propval;
		Map<String, WebCSVTransformer> trans = new TreeMap<String, WebCSVTransformer>();
		for (Object sProp : props.keySet()) {
			propkeysplit = ((String) sProp).split("\\.");
			
			if ("transforms".equals(propkeysplit[0]) && propkeysplit.length == 3) {

				String propValId = propkeysplit[1], 
						propSub = (propkeysplit.length > 2 ? propkeysplit[2] : null);
				propval = props.getProperty((String) sProp);

				WebCSVTransformer ttmp = trans.get(propValId);
				if (ttmp == null) {
					ttmp = new WebCSVTransformer(propValId);
					ttmp.setTimeout(getTimeout());
				}
				
				if ("url".equals(propSub))
					ttmp.setUrl(expandURL(propval, locVars));
				else if ("types".equals(propSub))
					ttmp.setTypes(propval.split(","));
				else if ("expr".equals(propSub)) {
					// ToDo split the expression stuff into
					// The expression itself (trim ")
					// The group variables
					Matcher matcher = REGEX_TRANSFORM.matcher(propval);
					if (matcher.find()) {
						ttmp.setExpr(Pattern.compile(matcher.group(1)));
						ttmp.setExprGroupVars(matcher.group(2).split(","));
					}
				} else if ("refresh".equals(propSub)) {
					Long refresh = null;
					try {
						refresh = Long.parseLong(propval);
					} catch (NumberFormatException e) {
						LOGGER.error("Could not parse {}={}. Is it really a long numeric value?", sProp, propval);
					}
					// If none is specified, set a default value for refresh.
					if (refresh == null)
						try {
							refresh = Long.parseLong(props.getProperty("options.refresh", "30000"));
						} catch (NumberFormatException e) {
							LOGGER.error("Could not parse options.refresh={}. "
									+ "Is it really a long numeric value?", propval);
						}
					ttmp.setRefresh(refresh);
						
				}
				trans.put(propkeysplit[1], ttmp);
			}
		}
		return trans;
	}

	/**
	 * Processes the options from properties.
	 * 
	 * @param props the properties containing the options
	 * @return a map with option values, keyed by their name.
	 */
	private Map<String, String> processPropertiesOptions(Properties props) {

		String sProp;
		String[] propkeysplit;
		String propval;
		Enumeration<Object> kProps = props.keys();
		Map<String, String> opts = new TreeMap<String, String>();
		while (kProps.hasMoreElements()) {
			sProp = (String) kProps.nextElement();
			propkeysplit = sProp.split("\\.");
			propval = props.getProperty(sProp);
			if ("options".equals(propkeysplit[0]) && propkeysplit.length == 2) {
				// We can simply put it. If it exists, it will be updated
				opts.put(propkeysplit[1], propval);
			}
		}
		return opts;
	}

	/**
	 * Processes the option variables, e.g. a baseurl or some string that is used more than once.
	 *  
	 * @param props the properties
	 * @return a map with variable values, keyed by their name
	 */
	private Map<String, String> processPropertiesVars(Properties props) {
		String sProp;
		String[] propkeysplit;
		String propval;
		Enumeration<Object> kProps = props.keys();
		Map<String, String> locVars = new TreeMap<String, String>();
		while (kProps.hasMoreElements()) {
			sProp = (String) kProps.nextElement();
			propkeysplit = sProp.split("\\.");
			propval = props.getProperty(sProp);
			if ("vars".equals(propkeysplit[0]) && propkeysplit.length == 2) {
				// We can simply put it. If it exists, it will be simply updated
				locVars.put(propkeysplit[1], propval);
			}
		}

		// Add the special variable host. This comes from openhab.cfg
		locVars.put("host", host);

		for (String svar : locVars.keySet()) {
			String value = locVars.get(svar);
			Matcher varmatch = VARIABLES_PATTERN.matcher(value);
			while (varmatch.find()) {
				if (locVars.containsKey(varmatch.group(1)))
					value = value.replace(varmatch.group(0),
							locVars.get(varmatch.group(1)));
				else
					LOGGER.warn(
							"Substitution of variable {} in property {} is not possible. Variable is unknown.",
							varmatch.group(1), svar);
				varmatch = VARIABLES_PATTERN.matcher(value);
			}
			locVars.put(svar, value);

		}

		return locVars;
	}

	/**
	 * The toString() method of this object.
	 * 
	 * @return the String conversion of this object
	 */
	public String toString() {
		return this.serverId + "=[configName=" + this.configName + ", host=" + this.host + "]";
	}

	/**
	 * Get the timeout (in milliseconds) for HTTP requests for this config.
	 * 
	 * @return the timeout in milliseconds
	 */
	public int getTimeout() {
		return timeout;
	}

	/**
	 * Set the timeout (in milliseconds) for HTTP requests for this config.
	 * 
	 * @param timeout in milliseconds
	 */
	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}
}
