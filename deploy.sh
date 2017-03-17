#!/bin/bash

echo CF_GCP_ROOT is $CF_GCP_ROOT
echo "Synchronizing GCP Service Broker Asssets"
cp $CF_GCP_ROOT/gcp/src/gcp-service-broker/manifest-linux-binary.yml $CF_GCP_ROOT/stackdriver-trace-deployer/src/main/resources/gcp-service-broker-assets/manifest.yml
cp $CF_GCP_ROOT/gcp/bin/gcp-service-broker $CF_GCP_ROOT/stackdriver-trace-deployer/src/main/resources/gcp-service-broker-assets/gcp-service-broker


echo "Synchronizing Stackdriver Zipkin Proxy Assets"
cp $CF_GCP_ROOT/stackdriver-zipkin-cf/manifest.yml  $CF_GCP_ROOT/stackdriver-trace-deployer/src/main/resources/stackdriver-proxy-assets/manifest.yml
cp $CF_GCP_ROOT/stackdriver-zipkin-cf/target/stackdriver-zipkin-cf.jar  $CF_GCP_ROOT/stackdriver-trace-deployer/src/main/resources/stackdriver-proxy-assets/proxy.jar
