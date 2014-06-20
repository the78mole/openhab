# General properties for accessing a CSV based service

The format of the properties file is according to the usual Java properties with a key and a value part, separated by an equal sign **=**.

The general settings for a CSV webservice description are:

* properties.name=Some_Descriptive_Name
* properties.maturity=(draft|unstable|testing|stable|rocksolid)
* properties.author=myname@github

## Variables

The properties file can define variables to make descriptions more hirarchical and easier to maintain. Variables can be defined with **vars.<VARNAME>** and access it in other parts of the properties with with %{<VARNAME>}. Currently there exists one predefined variable %{host} that contains the hostname from openhab.cfg.

It is suggested, to define a variable **vars.baseurl** where all other urls rely on. This usually looks like the following:

```
vars.baseurl=http://%{host}
```

## Mandatory properties

There exist some more or less mandatory properties:

* test.url
* test.expr

This two properties are used to test if given configuration is covered by this special properties file. For every defined host, all properties files are probed until one test expression matches. The matching one acts like a driver to access the information from this webservice. If only the url is given and the http access secceeds, it is assumed that this property file can act as the driver. Therefore it is highly suggested to provide a regular expression with **test.expr** to test the response against a well known pattern. For Powador properties it is trying to match the inverter type and the correct software version, since it is not guaranteed, that the webservices will keep the same when firmware is upgraded.

## Additional properties

The keyword for additional properties is **options**. With this property, it is possible to set some specialities of this driver. With options you can deactive or activate caching on a driver-by-driver basis and also define an individual refresh interval.

```
options.cache=true
options.refresh=30000
```

## Transformations

Some webservices are used for trnasformation purposes only. They consist mostly of a simple table with a key and a value in each line. This is mostly true for language purposes, where status IDs are translated to human readable sentences. Transformation services are defined by following properties (with example values):

* transforms.<ID>.url=%{baseurl}/pstatus.txt
* transforms.<ID>.expr="^\\W*(\\d+)\\s+(.*?)\\W*$",sid,sdesc
* transforms.<ID>.types=Number,String
* transforms.<ID>.refresh=0

**ID**: Defines a unique ID for this transformation. With this ID it will be accessed by the value services.

**url**: The URL key defines where the table can be accessed through the webservice of the device. Her also another host could be defined, not even related to the device itself. So even a webservice supplied by the developer of the properties file could be used.

**expr**: The expression devides a line of the response into two or more parts. Every matching group is then linked to the named variable (e.g. sid and sdesc), following the expression and separated by comma.

**types**: Defines the types of the variables of the expression. All types of openHAB could generally be used. In openHAB they belong to classes org.openhab.core.library.items.<TYPE>Item. The "Item"-Part of the classes name is stripped of for the properties file.

**refresh**: Defines, how often the look-up table is updated in milliseconds (only positive values). If ***-1*** is given, it is updated every time, the transformation is done. 0 means, the response is cached forever and never updated until the bundle or openHAB is restarted.

## Value webservices (the real meat)

The values key defines the webservices for requesting the values. Each webservice descriptor has its own cache for data, since it retrieves a set of data when it is accessed. With a dirty cache, only one access of the device's service is carried out for all value accesses of this service within the refresh interval. Examples taken from Powador, but abbreviated and therefore incompatible.

* values.<ID>.url=%{baseurl}/realtime.csv
* values.<ID>.split=";"
* values.<ID>.expr="^([^;]+);([^;]+);([^;]+);([^;]+);([^;]+)"
* values.<ID>.vars=t,udc1,udc2,uac1,status
* values.<ID>.factors=1000,0.0244,0.0244,0.0244,null
* values.<ID>.types=DateTime,Number,Number,Number,Number
* values.<ID>.transforms.<refID>=<TransID>,<TransInVar><TransOutVar>
* values.<ID>.refresh=10000

**ID** is a unique ID for this value webservice. It is only used for tying the properties together and not used for other user purposes at the moment.

**url** specifies the URL to access the value webservice.

**split/expr** is used to separate the values. If the service is pure CSV, the split-separator is the property of choice. It simply splits the values by the given expression. For CSV this is usually ";" or "," or "\t" (even if CSV would only allow ",", other separators are widely used). If the simple version is not sufficient, some regular expression can be supplied where each match-group (within round braces) defines a variable. The examples of split and expr above would lead to the same result. Only one of split or expr should be used.

**vars** labels the unnamed variables of split and expr definitions. This names are used later on in the items definition to address the values.

**factors** specifies a factor for a value. This ist useful for systems that supply very hardware related values. Many embedded systems have a value range aligned to binary boundaries like 0..65535 and don't tend to convert them internally. Usually the applications provided by device manufacturers do this in advance to presenting the data to the user. Here we need to do it ourselves, before posting the value to the event bus. The type of the value will not change. If no conversion is needed, "null"-factor does not affect the value. Factor conversion is only available to Number and realted items (also DateTime, when expressed as seconds since 1/1/1970 or alike).

**types** defines the types of the variables. Again, org.openhab.core.library.items.* contains allowd types with "Item" stripped of the classes names. e.g. NumberItem degrades to Number.

**transforms** is one of the most powerfol properties. It contains a look-up procedure to translate values to other values and even types. It needs a reference ID to define the **var** to translate. The **TransID** defines the transformation specified in this properties file. **TransInVar** references the variable that needs a match with refID to make TransOutVar the wanted value.

**refresh** is the interval in milliseconds the values will be cached.

## TODO and ideas for TODOs

* Provide a transform that uses a GET request and uses the whole response as the transformed value.
* Support history data sets (more than one line responses)
** Maybe this can be achieved by transformation abuse

# Example for Kaco Powador 6.0 to 20.0 TL3 Firmware Version 2.02

Properties file: Powador_6.0_20.0_TL3_V202.properties

All settings for accessing the Powador family 6.0 to 20.0 TL3 with Software/Firmware Version 2.02 are described here in detail.

The Powador 14.0 TL3 uses one-liners with a CSV format to answer webservice requests, that are usually requested by the dynamic webinterface of the device itself. Three different files can be requested for getting realtime status and meta data. Another file translates status codes to human readable status texts. Powador also supports history data request that are not handled by this binding.

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
1402066125;18480;21714;9636;9620;9607;1973;2444;3079;3097;3065;4297;5058;4;
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
| 12 | T    | Device temperature    | �C | 1 / 100       |   4720 |  47,2 �C |
| 13 | STAT | Status (pstatus.txt)  | - | LUT            |   4    | Power injection |

## pstatus.txt

```
0;Init phase;
1;Wait for injection;
[...]
```

| Field | Description                       |
|-------|-----------------------------------|
| 0     | Status code                       |
| 1     | Human readable status description |

