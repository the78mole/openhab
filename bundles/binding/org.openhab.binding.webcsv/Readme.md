# General description

This binding supports many types of webservices that make values available as simple single line CSV tables. This e.g. applies for Powador Solar Inverters (tested with Powador 14.0 TL3).

The binding can be configured with property files included in the bundle itself or even through fragment bundles, providing propertiy files within the src/main/resource folder of the binding jar.

The given host is tested against each property file and if test is successful, the properties file serves as a driver to this device for getting the values.

* openhab.cfg (mandatory)

```
############################## WebCSV Binding ########################################
#
# WebCSV host (hostname or IP) and port (default 80) 
# e.g. webcsv:powador1.host=192.168.1.101
webcsv:<ServerID>.host=<HOSTNAME|IP>[:PORT]

# interval in milliseconds to check for cache timeouts (optional, defaults to 1000)
webcsv:granularity=10000

# the retry count in case no valid value was returned 
# upon read (optional, defaults to 3)
# Not implemented/tested yet (implemented in HTTPUtils)
webcsv:retry=3
```

* Items configuration
```
Number <ItemName> { webcsv="<<ServerId>:<ValueId>:<Refresh>"  }
````

Example:
```
Number PDUDC1 "Voltage DC1 [%.0f V]" <energy> (gMyInverter) { webcsv="<powador1:udc2:5000"  }
```

# 
