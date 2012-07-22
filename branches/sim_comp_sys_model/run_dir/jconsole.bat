export REPASTHOME=/Applications/Repast-Simphony-2.0.0-beta/eclipse/plugins/
jconsole &
java -Dcom.sun.management.jmxremote -Xss10M -Xmx600M -Xms400M -cp ..:../bin/:\
$REPASTHOME/repast.simphony.runtime_2.0.0/bin:\
$REPASTHOME/repast.simphony.runtime_2.0.0/lib/*:\
$REPASTHOME/repast.simphony.core_2.0.0/lib/*:\
$REPASTHOME/repast.simphony.bin_and_src_2.0.0/repast.simphony.bin_and_src.jar:\
$REPASTHOME/repast.simphony.gis_2.0.0/lib/* \
repastcity3.main.RepastCityMain ../repastcity3.rs/
