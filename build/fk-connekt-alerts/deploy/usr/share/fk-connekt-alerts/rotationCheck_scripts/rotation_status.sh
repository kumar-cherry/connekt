#!/usr/bin/env bash

sleep 300
exec 2>&1

url='http://localhost:28000/elb-healthcheck'

status_code=$(curl -o /dev/null --silent --head --write-out '%{http_code}\n' $url)

ts=`date +%s`
if [ $status_code -eq 200 ]
then
	echo "$ts metrics.process.receptors.status.inRotation 1 tag=receptors" | cosmos
else
	echo "$ts metrics.process.receptors.status.OutOfRotation 1 tag=receptors" | cosmos
fi
