FROM anapsix/alpine-java:latest

MAINTAINER codingchili@github

# run mvn clean package to build the jar file.
# to build the docker image run: docker build .


RUN mkdir -p /opt/excelastic
COPY docker/configuration.json /opt/excelastic
COPY docker/bootstrap.sh /opt/excelastic
COPY excelastic-*.jar /opt/excelastic/excelastic.jar
RUN chmod +x /opt/excelastic/bootstrap.sh && \
    apk add gettext

WORKDIR /opt/excelastic

ENV es_host localhost
ENV es_port 9200

EXPOSE 5252:5252/tcp

ENTRYPOINT ["/bin/sh", "-c", "/opt/excelastic/bootstrap.sh"]