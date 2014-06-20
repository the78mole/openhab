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

# Example

The Powador 14.0 TL3 uses one-liners with a csv-format to answer webservice requests. Three different files can be requested for getting status and meta data. Another file translates status codes to human readable status texts. Powador also supports history data request that are not handled by this binding.

## meta.csv

```
14.0TL01258483;Powador 14.0 TL3;00:04:A3:A4:72:2E;172.16.22.115;1;3;2;12500.0;14000.0; DE;deDE;142;202;000; 
```
| Field | Description |
|-------|-------------|
| 0     | Serial number |
| 1     | Device type |
| 2     | MAC address |
| 3     | IP Address (IPv4) |
| 4     | Inverters RS-485 address (Range 1-31) |
| 5     | AC phases count |
| 6     | DC input count |
| 7     | Nominal AC power [W] |
| 8     | Nominal DC power [W] |
| 9     | Country code (ISO 3166) |
| 10    | Language code (ISO 639) |
| 11    | MMI SW version (e.g. 101 => 1.01) |
| 12    | DSP AC SW version (e.g. 123 => 1.23) |
| 13    | DSP DC SW version (e.g. 123 => 1.23) |

## initlog.txt

Information on the first power up of the inverter

``` 
20130605;201306;2013;
```

| Field | Description |
|-------|-------------|
| 0     | Init date (year, month & day) |
| 1     | Init month (year & month) |
| 2     | Init year |

## realtime.csv

This file provides realtime information of the Inverter.

```

```

Description:

| Field | Short | Long | Unit | Factor | Example | Value |
|-------|-------|------|------|--------|---------|-------|
| 00 | T    | Time                  | s | 1000           |  1402078227 |  6.6.14 20:10:27 +0200 |
| 01 | UDC1 | DC voltage 1, MPPT1   | V | 1600 / 65535   |  18293 |  446,6 V |
| 02 | UDC2 | DC voltage 2, MPPT2   | V | 1600 / 65535   |  19867 |  485,0 V |
| 03 | UAC1 | AC voltage 1          | V | 1600 / 65535   |   9397 |  229,4 V |
| 04 | UAC1 | AC voltage 2          | V | 1600 / 65535   |   9425 |  230,1 V |
| 05 | UAC1 | AC voltage 3          | V | 1600 / 65535   |   9416 |  229,8 V |
| 06 | IDC1 | DC cvurrent 1, MPPT1  | A | 200 / 65535    |    331 |   1,01 A |
| 07 | IDC2 | DC crrent 2, MPPT2    | A | 200 / 65535    |   2384 |   7,27 A |
| 08 | IAC1 | AC current 1, Phase 1 | A | 200 / 65535    |   1877 |   5,72 A |
| 09 | IAC2 | AC current 2, Phase 2 | A | 200 / 65535    |   1886 |   5,76 A |
| 10 | IAC3 | AC current 3, Phase 3 | A | 200 / 65535    |   1862 |   5,68 A |
| 11 | PDC  | DC totalpower         | W | 100000 / 65535 |   2560 |   3906 W | 
| 12 | T    | Device temperature    | °C | 1 / 100       |   4720 |  47,2 °C |
| 13 | STAT | Status (pstatus.txt)  | - | LUT            |   4    | Power injection |


