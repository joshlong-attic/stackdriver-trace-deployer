#!/usr/bin/env bash

cf d -f stackdriver-zipkin-proxy

cf d -f gcp-service-broker
cf ds -f gcp-service-broker-db

cf ds -f proxy-stackdriver-trace

cf purge-service-instance -f proxy-stackdriver-trace
cf purge-service-offering -f  gcp-service-broker
cf delete-service-broker -f gcp-service-broker
