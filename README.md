# parser-banktrans-es [![Build Status](https://travis-ci.org/codingchili/parser-banktrans-es.svg?branch=master)](https://travis-ci.org/codingchili/parser-banktrans-es)

Parses XLSX transaction files exported from personal online banking accounts into ElasticSearch.
For use with Kibana or other visualization applications, example result [image](https://raw.githubusercontent.com/codingchili/parser-banktrans-es/master/sample-redacted.png). The application comes with a web interface to simplify uploading. To further improve the application, detecting the row_offset to allow for multiple XSLX formats at once should be implemented. 

## Prerequisites
The application requires ElasticSearch as its output.

ElasticSearch and Kibana (version 5.0.*) should not require any additional configuration or installation, just download and run from [Elastic](https://www.elastic.co/products). 

## Running
Running the application,
```
java -jar <filename.jar> run Launcher
```

If any connection errors occur check that the ElasticSearch listen port matches with the elastic_port in the configuration file. Make sure that ElasticSearch is running by directing your browser at [localhost:9200](http://localhost:9200/_count).

Compiling a new fatjar,
```
mvn clean package
```

## Configuration

├── configuration.json

**row_offset** specifies the row that contains the column names, must start in column A.

**web_port** port that the webserver will listen on.

**elastic_port** port that ElasticSearch listens to, host is set to localhost.

**elastic_index** name of the index where transaction items are inserted.
