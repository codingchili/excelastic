# parser-excel-elasticsearch [![Build Status](https://travis-ci.org/codingchili/parser-excel-elasticsearch.svg?branch=master)](https://travis-ci.org/codingchili/parser-excel-elasticsearch)

Parses XLSX files into ElasticSearch using column titles from specified row combined with data in columns on each row. For use with Kibana or other visualization applications, example result using a transaction log in excel format  [image](https://raw.githubusercontent.com/codingchili/parser-banktrans-es/master/sample-redacted.png). The application comes with a web-interface to simplify uploading.

![sample UI image](https://raw.githubusercontent.com/codingchili/parser-excel-elasticsearch/master/excelastic.png)

## Features
- import excel (.xlsx/.xls) files into elasticsearch.
- easy to use web interface, with support for commandline imports too.
- csv files can be converted to .xlsx using office and then imported.
- clear the index before importing, or append to existing index.
- basic authentication when uploading from the application to elasticsearch.

## Prerequisites
The application requires ElasticSearch as its output.

1. ElasticSearch (version 5+/6+) should not require any additional configuration or installation, just download and run from [Elastic](https://www.elastic.co/products). 

2. Download the latest release of excelastic-1.2.6.jar and the configuration.json (optional) file from [GitHub releases](https://github.com/codingchili/parser-excel-elasticsearch/releases).

Tested with ElasticSearch 5.6.2 and 6.1.0.

## Running
Running the application, filename and index is required, to import from the terminal run:
```
java -Xmx1g -jar excelastic-1.2.6.jar <filename> <index> <mapping> --clear
```
If running with --clear, then the existing index will be cleared before the import starts.

To run with the web interface, run the following in your terminal:
```
java -Xmx1g -jar excelastic-1.2.6.jar
```
When the application successfully connects to the ElasticSearch server, the browser will automatically open a new tab.

If any connection errors occur check that the ElasticSearch listen port matches with the elastic_port in the configuration file. Make sure that ElasticSearch is running by directing your browser at [localhost:9200](http://localhost:9200/).

Compiling a new fatjar and run tests,
```
mvn clean package
```

## Configuration

├── configuration.json

The configuration file is placed in the same directory as the jar.
An example of the configuration:
```
{
  "web_port": 0,                    // the port the web interface listens on
  "elastic_port": 9200,             // the port elasticsearch listens on
  "elastic_host": "localhost",      // address to elasticsearch
  "elastic_tls": false,             // set to true to use tls when indexing
  "authentication": false,          // sends an "Authentication" header if true.
  "basic": "username:password"      // if authentication is true this is used as basic authentication.
}
```
If no configuration file is present the values in the above example will be used.
Note that the comments cannot be included in the configuration file.

If no configuration file is present a new configuration file will be created using the default values listed here.
