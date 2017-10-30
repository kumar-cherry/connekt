#!/usr/bin/env bash

[ "$USER"="root" ] || die "You need to be root"

envFile="/etc/profile.d/alerts.sh"

if [  -f ${envFile} ]; then
    source ${envFile}
fi

action=$1
case "$action" in
  var-show)
  		if [ -f ${envFile} ]; then
  			cat ${envFile} | awk -F' ' '{print $2}';
  		else
  			echo "${envFile} missing";
  		fi
        ;;
  var-set)
		echo "export $2=$3" >> ${envFile};
		chmod 0755 ${envFile}
        ;;
  var-delete)
  		if [ -f ${envFile} ]; then
  		    echo -n "Are you sure to delete : $2. [y/N] ";
  			read choice;
  			if [ "${choice}" = "y" ]; then
			    sed -i "/$2/d" ${envFile};
			else
			    echo "Quitting delete.";
			fi
  		else
  			echo "${envFile} missing";
  		fi
        ;;
  *)
    	echo "Usage: $0 {var-show|var-set|var-delete}" >&2
    	exit 1
    	;;
esac
