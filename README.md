# parser-excel-elasticsearch [![Build Status](https://travis-ci.org/codingchili/parser-excel-elasticsearch.svg?branch=master)](https://travis-ci.org/codingchili/parser-excel-elasticsearch)

Parses XLSX files into ElasticSearch using column titles from specified row combined with data in columns on each row. For use with Kibana or other visualization applications, example result using a transaction log in excel format  [image](https://raw.githubusercontent.com/codingchili/parser-banktrans-es/master/sample-redacted.png). The application comes with a [web interface](https://raw.githubusercontent.com/codingchili/parser-excel-elasticsearch/master/sample-ui.png) to simplify uploading.

## Prerequisites
The application requires ElasticSearch as its output.

ElasticSearch and Kibana (version 5.2.4) should not require any additional configuration or installation, just download and run from [Elastic](https://www.elastic.co/products).

## Running
Running the application,
```
java -jar excelastic-1.2.0.jar
```

If any connection errors occur check that the ElasticSearch listen port matches with the elastic_port in the configuration file. Make sure that ElasticSearch is running by directing your browser at [localhost:9200](http://localhost:9200/_count). When the server is started browse to [localhost:8080](http://localhost:8080/) for the web interface.

Compiling a new fatjar,
```
mvn clean package
```

## Configuration

├── configuration.json


**web_port** (8080) port that the webserver will listen on. 

**elastic_port** (9200) port that ElasticSearch listens to, host is set to localhost. 

**elastic_host** (localhost) address of the ElasticSearch server.
