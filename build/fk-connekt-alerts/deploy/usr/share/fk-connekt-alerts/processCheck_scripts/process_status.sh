#!/usr/bin/env bash

sleep 300
exec 2>&1

pidFile='/var/run/fk-pf-connekt/fk-pf-connekt.pid'

pid=`cat $pidFile`
tag=`hostname -f | cut -d'-' -f3`

ts=`date +%s`
if [ `ps -eo pid | grep $pid` ]
then
	echo "$ts metrics.process.$tag.status.running 1 tag=$tag" | cosmos
else
	echo "$ts metrics.process.$tag.status.notRunning 1 tag=$tag" | cosmos
fi
