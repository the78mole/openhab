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

/**
 * 
 * @author the78mole@github
 *
 */
// The suppress warnings makes warnings disappear for import org.openhab.core.library.items.*; 
@SuppressWarnings("unused")
public class WebCSVValue {
	/** The name of this value. */
	private String name;
	/** The raw value as read from host/device. */
	private String rawValue = null;
	/** The scaled by factor value. Same as rawValue if none or invalid factor is defined. */
	private String value = null;
	/** The transformed value. If none or invalid transformation is defined, same as value. */
	private String transValue = null;
	/** If a transformer is defined, it is kept here. */
	private WebCSVTransformer transform = null;
	/** The variable name of the transformation out value for this value. */
	private String transformOutVar = null;
	/** The name of the matching input variable for transformation. */
	private String transformInVar = null;
	/** The factor for scaling the rawValue. */
	private Double factor = null;
	/** The Values aggregating this value. Values are the base for data retrieval and caching. */
	private WebCSVValues aggregator = null;
	/** Last update of this value. */
	private String type = null;
	
	/**
	 * The default LOGGER.
	 */
	static final Logger LOGGER = LoggerFactory.getLogger(WebCSVValue.class);

	/**
	 * Get the name of this value.
	 * 
	 * @return a String representing the name
	 */
	public String getName() {
		return name;
	}

	/**
	 * Checks if an update is needed. This means, {@link WebCSVValues#refresh} == -1 or 
	 * {@link System#currentTimeMillis() current time} is greater than {@link WebCSVValues#lastUpdate} + 
	 * {@link WebCSVValues#refresh}.
	 * 
	 * @return true if update is needed, otherwise false
	 */
	public Boolean updateNeeded() {
		return aggregator.updateNeeded();
	}

	/**
	 * Create a WebCSVValue with specified variable name.
	 * 
	 * @param varname the name of the variable to create the WebCSVValue for
	 * @param aggregator aggregating this value and it's common data source
	 */
	public WebCSVValue(String varname, WebCSVValues aggregator) {
		this.name = varname;
		this.aggregator = aggregator;
	}

	/**
	 * Stores the value as {@link #rawValue} and triggers the scaling conversion to {@link #value} and the 
	 * transformation to {@link #transValue} through {@link WebCSVTransformer} linked with ({@link #transform}. 
	 * 
	 * @param val String representation of the value
	 */
	public void updateValue(String val) {
		rawValue = val;
		if (val != null) {
			if (factor == null)
				value = val;
			else
				value = String.valueOf(Double.valueOf(val) * factor);
			if (transform == null)
				transValue = value;
			else
				transValue = transform.execute(value, transformInVar, transformOutVar);
		}
	}

	/**
	 * Hands the update over to the aggregator, that checks if update is needed, if so, fetches the value and hands
	 * it over back to {@link #updateValue(String)}.
	 */
	public void updateValue() {
		aggregator.updateValues();
	}
	
	/**
	 * Method to request the actual value. It checks, if an update is needed and if so, triggers the update procedure
	 * with {@link #updateValue()}. It returns the {@link #transValue}, that is the value with deepest processing. If
	 * no transformation is specified, it returns the scaled {@link #value}, if also no factor is specified, it returns
	 * the {@link #rawValue}.
	 * 
	 * @return the most processed value
	 */
	public String getValue() {
		if (updateNeeded())
			updateValue();
		return transValue;
	}

	/**
	 * Tries to convert a String to a Double and sets it as a scaling factor for converting {@link #rawValue} to 
	 * {@link #value}.
	 * 
	 * @param factor String representation of the scaling factor.
	 */
	public void setFactor(String factor) {
		if (factor == null || factor.isEmpty() || "null".equalsIgnoreCase(factor))
			this.factor = null;
		else
			try {
				setFactor(Double.parseDouble(factor));
			} catch (NumberFormatException e) {
				LOGGER.error("{} can not be parsed to a Double value for {}.", factor, this.name);
			}
	}
	
	/**
	 * Sets a scaling factor for converting {@link #rawValue} to {@link #value}.
	 * 
	 * @param factor the scaling factor
	 */
	public void setFactor(Double factor) {
		this.factor = factor;
	}

	/**
	 * Sets the {@link WebCSVTransformer} to transform {@link #rawValue} or {@link #value} through look-up operations
	 * to {@link #transValue}.
	 *  
	 * @param transformer the transformer to use
	 * @param transInRel the variable that defines the input for transformation (the key to look up)
	 * @param transOutRel the variable that defines the output of the transformation (the value to look up)
	 */
	public void setTransform(WebCSVTransformer transformer, String transInRel, String transOutRel) {
		if (this.transform != null && transformer != null && !this.transform.equals(transformer))
			LOGGER.info("Transformer {} for value {} was overwritten by {}");
		
		if (transformer != null)
			this.transform = transformer;
		else
			LOGGER.info("Resetting in ({}) and out ({}) variables to {} and {}.", 
					this.transformInVar, this.transformOutVar, transInRel, transOutRel);
		
		this.transformInVar = transInRel;
		this.transformOutVar = transOutRel;
	}

	/**
	 * Sets the type of this value. Valid types are {@link Item}s class names with stripped "Item"-part.
	 * 
	 * @param type the type of this value
	 */
	public void setType(String type) {
		this.type = type;
	}
	
	
	/**
	 * Gets the type of this value. Valid types are {@link Item}s class names with stripped "Item"-part.
	 * 
	 * @return String representation of the type
	 */ 
	public String getType() {
		// If we transform the value, we need to check the transformed type
		if (transform != null)
			return transform.getType(transformOutVar);
		else
			return this.type;
	}
	
	
	/**
	 * If an output type is defined, this method tries to get the closest representation. E.g. if a String is 
	 * requested, #value is a Number and {@link #transValue} is a String description, it will return 
	 * {@link #transValue}. If a number is requested, it will return {@link #value}.
	 * 
	 * @param itemType the requested type of the value
	 * @return the resulting {@link State} with best matching type
	 */
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
		if (cnSplit.length > 0)
			cName = cnSplit[cnSplit.length - 1];
		
		if (cName != null && transType != null && cName.contains(transType) && transValue != null) {
			tmptype = transType;
			tmpval = transValue;
		} else if (cName != null && cName.contains(type)) {
			tmptype = type;
			if (value != null)
				tmpval = value;
			else
				tmpval = rawValue;			
		} else {
			tmptype = "String";
			tmpval = value;
		}		
		
		String strITC = "org.openhab.core.library.items." + tmptype + "Item";
		
		try { 
			typeItemClass = (Class<? extends Item>) Class.forName(strITC);
			Constructor<? extends Item> typeItemClassConstructor = typeItemClass.getConstructor(String.class);
			Item tmpItem = typeItemClassConstructor.newInstance(this.name);
			List<Class<? extends State>> acceptedTypes = tmpItem.getAcceptedDataTypes();
			
			for (Class<? extends State> aType : acceptedTypes) {
				
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
			LOGGER.error("Some IllegalAccessException occured while trying to instantiate {} for state update of {}.", 
					strITC, name);
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
