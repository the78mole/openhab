package org.openhab.binding.webcsv.internal;

import java.net.MalformedURLException;
import java.net.URL;
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

	static final Logger logger = LoggerFactory
			.getLogger(WebCSVConfig.class);

	// The Pattern for matching variables %{VARNAME}
	// group(0) then conains the full variable
	// group(1) only the variable configname
	static final Pattern varpat = Pattern.compile("%\\{([0-9a-zA-Z_-]+)\\}");

	public String serverId;
	public String host;
	private String configname;
	private Map<String, String> vars = new HashMap<String, String>();
	private Map<String, String> options = new HashMap<String, String>();
	private Map<String, WebCSVValues> values = new HashMap<String, WebCSVValues>();
	private Map<String, WebCSVValue> valueMap = new TreeMap<String, WebCSVValue>();

	private Map<String, WebCSVTransformer> transforms = new HashMap<String, WebCSVTransformer>();

	private WebCSVChecker pdchecker;

	private Boolean pdactive;

	private String configMaturity;
	private String configAuthor;

	private static final Pattern REGEX_TRANSFORM = Pattern
			.compile("\"([^\"]+)\",(.*)$");

	public WebCSVConfig(String serverId, String host, Properties props,
			Map<String, String> vars) {

		this.serverId = serverId;
		if (this.vars == null)
			this.vars = new HashMap<String, String>();
		else if (vars != null)
			this.vars.putAll(vars);
		if (host != null)
			this.host = host;
		else
			logger.warn("No host argument supplied for constructor call! Probably problems will arise later on.");
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
		// logger.warn("No host defined. This can lead to many problems...");

		// Get the configname of the config
		configname = props.getProperty("properties.name", "undefined");
		if("undefined".equals(configname))
			logger.warn("properties.name is not defined in {}.", props);

		configMaturity = props.getProperty("properties.maturity", "draft");
		configAuthor = props.getProperty("properties.author", "the78mole@github");
		
		String msg = new String("You are using a {} version of WebCSV-Configuration {}.\r\n" +
				"  Please report errors to the author {}.");
		if(logger.isDebugEnabled() && "rocksolid".equalsIgnoreCase(configMaturity))
			logger.debug(msg, configMaturity, configname, configAuthor);
		if("stable".equalsIgnoreCase(configMaturity))
			logger.debug(msg, configMaturity, configname, configAuthor);
		else if("testing".equalsIgnoreCase(configMaturity))
			logger.info(msg, configMaturity, configname, configAuthor);
		else 
			logger.warn(msg, configMaturity, configname, configAuthor);

		// 2nd get the check
		// pdchecker = new WebCSVChecker();
		// pdactive = pdchecker.doWebCSVCheck(false);

		// 1st get all the vars and substitute themselves recursively
		Map<String, String> vars = processPropertiesVars(props, host);

		// Now process the options
		options = processPropertiesOptions(props);

		// Now process the transform data suppliers
		Map<String, WebCSVTransformer> trans = processPropertiesTransforms(
				props, vars);

		// Now process the values
		values = processPropertiesValues(props, vars, trans, valueMap);

	}

	/**
	 * Processes the value properties and returns the map that resolves variable
	 * names to value collections, that handle the data retrieval.
	 * 
	 * @param props
	 *            The Properties to process
	 * @param vars
	 *            The already extracted property variables from the properties
	 *            to substitute the stuff
	 * @param trans
	 *            The already extracted transformation objects
	 * @param valueMap
	 *            The Map where to store the mapping variable to WebCSVValue
	 *            object
	 * @return The Mapping from variable configname to value collection
	 */
	private Map<String, WebCSVValues> processPropertiesValues(
			Properties props, Map<String, String> vars,
			Map<String, WebCSVTransformer> trans,
			Map<String, WebCSVValue> valueMap) {

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
						if (pvtmp == null)
							pvtmp = new WebCSVValues(propValId,propval.split(","), true);
						// set a default value for refresh
						pvtmp.setRefresh(props.getProperty("options.refresh","30000"));
						pvtmp.setCacheEnabled(props.getProperty("options.cache", "enabled"));
						for (WebCSVValue spv : pvtmp.getValues())
							valueMap.put(spv.getName(), spv);
						vals.put(propValId, pvtmp);
					}
				} else
					logger.error(
							"Wrong property key {} for values property type. Needs 4 parts with transform, otherwise 3 parts, separated by dot.",
							sProp);

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
						propSub_transVar = propkeysplit.length == 4 ? propkeysplit[3]	: null;

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
							pvals.setURL(expandURL(propval, vars));
						else if ("refresh".equals(propSub))
							pvals.setRefresh(propval);
						else if ("transforms".equals(propSub)) {
							String[] transOpts = propval.split(",");
							if (transOpts.length == 3) {
								String transKey = transOpts[0], transInRel = transOpts[1], transOutRel = transOpts[2];
								WebCSVTransformer ttmp = trans.get(transKey);
								WebCSVValue pvtmp = valueMap.get(propSub_transVar);
								pvtmp.setTransform(propSub_transVar,ttmp, transInRel,transOutRel);
							} else
								logger.error(
										"Transformation property {} invalid. Syntax is: <transformation.name>,<inputvar>,<outputvar>",
										sProp);

						}
					} else
						logger.error(
								"This should never happen. There are no vars specified for {}.",
								propValId);

				} else
					logger.error("Malformed property configname {}", sProp);
			}
		}

		return vals;
	}

	private Map<String, WebCSVTransformer> processPropertiesTransforms(
			Properties props, Map<String, String> vars) {
		
		String[] propkeysplit;
		String propval;
		Map<String, WebCSVTransformer> trans = new TreeMap<String, WebCSVTransformer>();
		for(Object sProp : props.keySet()) {
			propkeysplit = ((String) sProp).split("\\.");
			
			if ("transforms".equals(propkeysplit[0]) && propkeysplit.length == 3) {

				String propValId = propkeysplit[1], 
						propSub = (propkeysplit.length > 2 ? propkeysplit[2] : null);
				propval = props.getProperty((String) sProp);

				WebCSVTransformer ttmp = trans.get(propValId);
				if(ttmp == null)
					ttmp = new WebCSVTransformer(propValId);
				
				if ("url".equals(propSub))
					ttmp.setURL(expandURL(propval, vars));
				else if ("types".equals(propSub))
					ttmp.setTypes(propval.split(","));
				else if ("expr".equals(propSub)) {
					// ToDo split the expression stuff into
					// The expression itself (trim ")
					// The group variables
					Matcher matcher = REGEX_TRANSFORM.matcher(propval);
					if(matcher.find()) {
						ttmp.expr = Pattern.compile(matcher.group(1));
						ttmp.exprGroupVars = matcher.group(2).split(",");
					}
				} else if ("refresh".equals(propSub)) {
					Long refresh = null;
					try {
						refresh = Long.parseLong(propval);
					} catch (NumberFormatException e) {
						logger.error("Could not parse {}={}. Is it really a long numeric value?", sProp, propval);
					}
					// If none is specified, set a default value for refresh.
					if(refresh == null)
						try {
							refresh = Long.parseLong(props.getProperty("options.refresh", "30000"));
						} catch (NumberFormatException e) {
							logger.error("Could not parse options.refresh={}. Is it really a long numeric value?", propval);
						}
					ttmp.refresh = refresh;
						
				}
				trans.put(propkeysplit[1], ttmp);
			}
		}		
		return trans;
	}

	private URL expandURL(String strurl, Map<String, String> vars) {

		String strurlwork = new String(strurl);
		// Parse all stuff and substitute the variables
		for (String svar : vars.keySet()) {
			String value = vars.get(svar);
			Matcher varmatch = varpat.matcher(strurlwork);
			while (varmatch.find()) {
				if (vars.containsKey(varmatch.group(1)))
					strurlwork = strurlwork.replace(varmatch.group(0),
							vars.get(varmatch.group(1)));
				else
					logger.warn(
							"Substitution of variable {} in url property {} ({}) is not possible. Variable is unknown.",
							varmatch.group(1), strurl, strurlwork);
				varmatch = varpat.matcher(strurlwork);
			}
			vars.put(svar, value);
		}

		URL tmpurl = null;
		try {
			tmpurl = new URL(strurlwork);
		} catch (MalformedURLException e) {
			logger.error("Url {} is malformed, expanded from {}.", tmpurl, strurl);
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
		Map<String, String> vars = new TreeMap<String, String>();
		while (kProps.hasMoreElements()) {
			sProp = (String) kProps.nextElement();
			propkeysplit = sProp.split("\\.");
			propval = props.getProperty(sProp);
			if ("vars".equals(propkeysplit[0]) && propkeysplit.length == 2) {
				// We can simply put it. If it exists, it will be simply updated
				vars.put(propkeysplit[1], propval);
			}
		}

		vars.put("host", host);

		for (String svar : vars.keySet()) {
			String value = vars.get(svar);
			Matcher varmatch = varpat.matcher(value);
			while (varmatch.find()) {
				if (vars.containsKey(varmatch.group(1)))
					value = value.replace(varmatch.group(0),
							vars.get(varmatch.group(1)));
				else
					logger.warn(
							"Substitution of variable {} in property {} is not possible. Variable is unknown.",
							varmatch.group(1), svar);
				varmatch = varpat.matcher(value);
			}
			vars.put(svar, value);

		}

		return vars;

	}

	public String toString() {
		return this.serverId+"=[configname="+this.configname+",host="+this.host+"]";
	}
}