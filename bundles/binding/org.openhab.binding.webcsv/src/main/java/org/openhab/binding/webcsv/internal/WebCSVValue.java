package org.openhab.binding.webcsv.internal;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import org.openhab.core.items.Item;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// This needs to be in for working Class.forName() using the items package
import org.openhab.core.library.items.*;

// This makes warnings disappear for above import
@SuppressWarnings("unused")
public class WebCSVValue {
	private String name;
	public String rawValue = null;
	public String value = null;
	public String transValue = null;
	private WebCSVTransformer transform = null;
	private String transformOutVar = null;
	private String transformInVar = null;
	public Double factor = null;
	public WebCSVValues aggregator = null;
	public Long lastUpdate = null;
	private String type = null;
	
	/**
	 * The default LOGGER.
	 */
	static final Logger LOGGER = LoggerFactory.getLogger(WebCSVValue.class);

	public String getName() {
		return name;
	}

	public Boolean updateNeeded() {
		return aggregator.updateNeeded();
	}

	private void updateTime() {
		lastUpdate = System.currentTimeMillis();
	}
	
	/**
	 * Create a WebCSVValue with specified variable name
	 * 
	 * @param varname
	 * 		The name of the variable to create the WebCSVValue for
	 * @param WebCSVValues 
	 */
	public WebCSVValue(String varname, WebCSVValues aggregator) {
		this.name = varname;
		this.aggregator = aggregator;
		lastUpdate = 0L;
	}

	public void updateValue(String val) {
		rawValue = val;
		if(val != null) {
			if(factor == null)
				value = val;
			else
				value = String.valueOf(Double.valueOf(val) * factor);
			if(transform == null)
				transValue = value;
			else
				transValue = transform.execute(value, transformInVar, transformOutVar);
			updateTime();
		}
	}

	public void updateValue() {
		aggregator.updateValues();
	}
	
	public String getValue() {
		if(updateNeeded())
			updateValue();
		return transValue;
	}

	public void setFactor(String factor) {
		if(factor == null || factor.isEmpty() || "null".equalsIgnoreCase(factor))
			this.factor=null;
		else
			try {
				setFactor(Double.parseDouble(factor));
			} catch (NumberFormatException e) {
				LOGGER.error("{} can not be parsed to a Double value for {}.", factor, this.name);
			}
	}
	
	public void setFactor(Double factor) {
		this.factor = factor;
	}

	public void setTransform(String transVar, WebCSVTransformer webCSVTransformer, 
			String transInRel, String transOutRel) {
		if(this.transform != null && webCSVTransformer != null && !this.transform.equals(webCSVTransformer))
			LOGGER.info("Transformer {} for value {} was overwritten by {}");
		
		if(webCSVTransformer != null)
			this.transform = webCSVTransformer;
		else
			LOGGER.info("Resetting in ({}) and out ({}) variables to {} and {}.", 
					this.transformInVar, this.transformOutVar, transInRel, transOutRel);
		
		this.transformInVar = transInRel;
		this.transformOutVar = transOutRel;
	}

	public void setType(String type) {
		this.type = type;
	}
	
	public String getType() {
		// If we transform the value, we need to check the transformed type
		if(transform != null)
			return transform.getType(transformOutVar);
		else
			return this.type;
	}
	
		
	@SuppressWarnings("unchecked")
	public State getTypeMatchingValue(Class<? extends Item> itemType) {
		// First try to match the transformed value
		// Then try the recalculated value with a factor (is it defined or null?)
		// Last try is to use the raw value
		Class<? extends Item> typeItemClass;
		State state = null;
		String transType, tmpval, tmptype;

		// Ensure that values are up to date...
		tmpval = getValue();
		
		transType = transformOutVar != null ? transform.getType(transformOutVar) : null;

		String[] cnSplit = itemType.getName().split("\\.");
		String cName = null;
		if(cnSplit.length > 0)
			cName = cnSplit[cnSplit.length-1];
		
		if(cName != null && transType != null && cName.contains(transType) && transValue != null) {
			tmptype = transType;
			tmpval = transValue;
		} else if(cName != null && cName.contains(type)) {
			tmptype = type;
			if(value != null)
				tmpval = value;
			else
				tmpval = rawValue;			
		} else {
			tmptype = "String";
			tmpval = value;
		}		
		
		String strITC = "org.openhab.core.library.items."+tmptype+"Item";
		
		try { 
			typeItemClass = (Class<? extends Item>) Class.forName(strITC);
			Constructor<? extends Item> typeItemClassConstructor = typeItemClass.getConstructor(String.class);
			Item tmpItem = typeItemClassConstructor.newInstance(this.name);
			List<Class<? extends State>> acceptedTypes = tmpItem.getAcceptedDataTypes();
			
			for(Class<? extends State> aType : acceptedTypes) {
				
				try {
					Constructor<? extends State> aTypeConstructor = aType.getDeclaredConstructor(String.class);
					state = aTypeConstructor.newInstance(tmpval);
					break;
				} catch (NoSuchMethodException e) {
					LOGGER.warn("Was not able to creaty type {} from String because of {}.", aType.toString(), e);
				} catch	(SecurityException e) {
					LOGGER.warn("Was not able to creaty type {} from String because of {}.", aType.toString(), e);
				} catch	(IllegalArgumentException e) {
					LOGGER.warn("Was not able to creaty type {} from String because of {}.", aType.toString(), e);
				} catch	(InvocationTargetException e) {
					LOGGER.warn("Was not able to creaty type {} from String because of {}.", aType.toString(), e);
				}
					
			}
		} catch (ClassNotFoundException e) {
			LOGGER.error("The class {} could not be found for building state {}.", strITC, name);
		} catch (InstantiationException e) {
			LOGGER.error("Could not instantiate {}. Wired...!", strITC);
		} catch (IllegalAccessException e) {
			LOGGER.error("Some IllegalAccessException occured while trying to instantiate {} for state update of {}.", strITC, name);
			e.printStackTrace();
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
			LOGGER.error("Could not instantiate {} for state update of {}.", strITC, name);
			e.printStackTrace();
		}

		return state;
		
	}
}
