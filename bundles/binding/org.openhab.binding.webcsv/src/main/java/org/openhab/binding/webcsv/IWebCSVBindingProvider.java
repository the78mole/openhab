/**
 * Copyright (c) 2010-2014, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.webcsv;

import java.util.List;

import org.openhab.core.binding.BindingProvider;
import org.openhab.core.items.Item;


/**
 * This interface is implemented by classes that can provide mapping information
 * between openHAB items and a WebCSV host.
 * 
 * @author the78mole (Daniel Glaser)
 */
public interface IWebCSVBindingProvider extends BindingProvider {

	/**
	 * Returns the Type of the Item identified by {@code itemName}.
	 * 
	 * @param itemName the name of the item to find the type for
	 * @return the type of the Item identified by {@code itemName}
	 */
	Class<? extends Item> getItemType(String itemName);
	
	/**
	 * Return the IP host for WebCSV host linked to the item.
	 * 
	 * @param itemName the item for which to find the corresponding host
	 * @return returns the hostname of the host handling the item
	 */
	String getHost(String itemName);

	/**
	 * Return the WebCSV hosts port for the item (linked to the parameter).
	 * 
	 * @param itemName the item for which to find the port
	 * @return returns the port of the host handling the item
	 */
	String getPort(String itemName);
	
	/**
	 * Return the WebCSV host ServerID linked to the item.
	 * 
	 * @param itemName the item for which to find the serverId
	 * @return the server ID of the server handling the item
	 */
	String getServerId(String itemName);
	

	/**
	 * Return the parameter 'name' for this item. The variable 'name' is the WebCSV parameter
	 * used for the item.
	 * 
	 * @param itemName the item for which to find the name
	 * @return the WebCSV internal name of the item
	 */
	String getName(String itemName);
	
	/**
	 * Returns the refresh interval to use according to <code>itemName</code>.
	 * Is used by WebCSV-In-Binding.
	 *  
	 * @param itemName the item for which to find a refresh interval
	 * 
	 * @return the matching refresh interval or <code>null</code> if no matching
	 * refresh interval could be found.
	 */
	int getRefreshInterval(String itemName);
	
	/**
	 * Returns all items which are mapped to a WebCSV-In-Binding.
	 * @return item which are mapped to a WebCSV-In-Binding
	 */
	List<String> getInBindingItemNames();
}
