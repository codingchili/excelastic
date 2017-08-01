# parser-excel-elasticsearch [![Build Status](https://travis-ci.org/codingchili/parser-excel-elasticsearch.svg?branch=master)](https://travis-ci.org/codingchili/parser-excel-elasticsearch)

Parses XLSX files into ElasticSearch using column titles from specified row combined with data in columns on each row. For use with Kibana or other visualization applications, example result using a transaction log in excel format  [image](https://raw.githubusercontent.com/codingchili/parser-banktrans-es/master/sample-redacted.png). The application comes with a [web interface](https://raw.githubusercontent.com/codingchili/parser-excel-elasticsearch/master/sample-ui.png) to simplify uploading.

## Prerequisites
The application requires ElasticSearch as its output.

1. ElasticSearch (version 5+) should not require any additional configuration or installation, just download and run from [Elastic](https://www.elastic.co/products). 

2. Download the latest release of excelastic-1.2.0.jar and the configuration.json (optional) file from [GitHub releases](https://github.com/codingchili/parser-excel-elasticsearch/releases).

## Running
Running the application, filename, index and template is optional: use to import from the terminal.
```
java -jar excelastic-1.2.0.jar <filename> <index> <mapping>
```

When the application successfully connects to the ElasticSearch server, the browser will automatically open a new tab.

If any connection errors occur check that the ElasticSearch listen port matches with the elastic_port in the configuration file. Make sure that ElasticSearch is running by directing your browser at [localhost:9200](http://localhost:9200/).

Compiling a new fatjar and run tests,
```
mvn clean package
```

## Configuration

├── configuration.json


**web_port** (8080) port that the webserver will listen on. 

**elastic_port** (9200) port that ElasticSearch listens to, host is set to localhost. 

**elastic_host** (localhost) address of the ElasticSearch server.

If no configuration file is present a new configuration file will be created using the default values listed here.
