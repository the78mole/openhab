# General description

This binding supports many types of webservices that make values available as simple single line CSV tables. This e.g. applies for Powador Solar Inversters (tested with Powador 14.0 TL3).

The binding can be configured with property files included in the bundle itself or even through fragment bundles, providing propertiy files within the resource folder.

* openhab.cfg (mandatory)

```
############################## WebCSV Binding ########################################
#
# WebCSV host (hostname or IP) and port (default 80) 
webcsv:<ServerID>.host=<HOSTNAME|IP>[:PORT]
# e.g. webcsv:powador1.host=192.168.1.101

# interval in milliseconds to check for cache timeouts (optional, defaults to 10000)
webcsv:granularity=10000

# the retry count in case no valid value was returned 
# upon read (optional, defaults to 3)
# Not implemented/tested yet
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
