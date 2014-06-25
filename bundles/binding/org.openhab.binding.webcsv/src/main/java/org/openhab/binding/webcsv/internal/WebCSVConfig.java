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

	public String serverId;
	public String host;
	private String configName;
	private Map<String, String> vars = new HashMap<String, String>();
	private Map<String, String> options = new HashMap<String, String>();
	private Map<String, WebCSVValues> values = new HashMap<String, WebCSVValues>();
	private Map<String, WebCSVValue> valueMap = new TreeMap<String, WebCSVValue>();

	private String configMaturity;
	private String configAuthor;

	/**
	 * A pattern to trim quotes and make varnames accessibble by matcher groups from regular expression properties.
	 */
	private static final Pattern REGEX_TRANSFORM = Pattern.compile("\"([^\"]+)\",(.*)$");

	public WebCSVConfig(String serverId, String host, Properties props,	Map<String, String> locVars) {

		this.serverId = serverId;
		if (this.vars == null)
			this.vars = new HashMap<String, String>();
		else if (locVars != null)
			this.vars.putAll(locVars);
		if (host != null)
			this.host = host;
		else
			LOGGER.warn("No host argument supplied for constructor call! Probably problems will arise later on.");
		if (props != null)
			processProperties(props);
	}

	public void updateNeeded(WebCSVValue pval) {
		pval.updateNeeded();
	}
	
	public WebCSVValue getWebCSVValue(String varname) {
		return valueMap.get(varname);
	}
	
	private void processProperties(Properties props) {

		// if(host != null)
		// vars.put("host", host);
		// else
		// LOGGER.warn("No host defined. This can lead to many problems...");

		// Get the configName of the config
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
		Map<String, String> locVars = processPropertiesVars(props, host);

		// Now process the options
		options = processPropertiesOptions(props);

		// Now process the transform data suppliers
		Map<String, WebCSVTransformer> trans = processPropertiesTransforms(
				props, locVars);

		// Now process the values
		values = processPropertiesValues(props, locVars, trans, valueMap);

	}

	/**
	 * Processes the value properties and returns the map that resolves variable
	 * names to value collections, that handle the data retrieval.
	 *
	 * @param props
	 *            The Properties to process
	 * @param locVars
	 *            The already extracted property variables from the properties
	 *            to substitute the stuff
	 * @param trans
	 *            The already extracted transformation objects
	 * @param locValueMap
	 *            The Map where to store the mapping variable to WebCSVValue
	 *            object
	 * @return The Mapping from variable configName to value collection
	 */
	private Map<String, WebCSVValues> processPropertiesValues(
			Properties props, Map<String, String> locVars,
			Map<String, WebCSVTransformer> trans,
			Map<String, WebCSVValue> locValueMap) {

		String[] propkeysplit;
		String propval;
		Map<String, WebCSVValues> vals = new TreeMap<String, WebCSVValues>();
		// First collect all varnames
		for (Object sProp : props.keySet()) {
			// while (kProps.hasMoreElements()) {
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
						}
						// set a default value for refresh
						pvtmp.setRefresh(props.getProperty("options.refresh", "30000"));
						pvtmp.setCacheEnabled(props.getProperty("options.cache", "enabled"));

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

		// Then collect the other variable stuff
		for (Object sProp : props.keySet()) {
			// while (kProps.hasMoreElements()) {
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
							pvals.setURL(expandURL(propval, locVars));
						else if ("refresh".equals(propSub))
							pvals.setRefresh(propval);
						else if ("transforms".equals(propSub)) {
							String[] transOpts = propval.split(",");
							if (transOpts.length == 3) {
								String transKey = transOpts[0], transInRel = transOpts[1], transOutRel = transOpts[2];
								WebCSVTransformer ttmp = trans.get(transKey);
								WebCSVValue pvtmp = locValueMap.get(propSubTransVar);
								pvtmp.setTransform(propSubTransVar, ttmp, transInRel, transOutRel);
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
				if (ttmp == null)
					ttmp = new WebCSVTransformer(propValId);
				
				if ("url".equals(propSub))
					ttmp.setURL(expandURL(propval, locVars));
				else if ("types".equals(propSub))
					ttmp.setTypes(propval.split(","));
				else if ("expr".equals(propSub)) {
					// ToDo split the expression stuff into
					// The expression itself (trim ")
					// The group variables
					Matcher matcher = REGEX_TRANSFORM.matcher(propval);
					if (matcher.find()) {
						ttmp.expr = Pattern.compile(matcher.group(1));
						ttmp.exprGroupVars = matcher.group(2).split(",");
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
					ttmp.refresh = refresh;
						
				}
				trans.put(propkeysplit[1], ttmp);
			}
		}		
		return trans;
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
				// We can simply put it. If it exists, it will be simply updated
				opts.put(propkeysplit[1], propval);
			}
		}
		return opts;
	}

	private Map<String, String> processPropertiesVars(Properties props,
			String host) {
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
}
