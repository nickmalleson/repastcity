export REPASTHOME=/home/ec2-user/ebs1/simburglar/Repast-Simphony-2.0.0-beta/eclipse/plugins/

java -Xss10M -Xmx4G -Xms4G -cp ..:../bin/:\
$REPASTHOME/repast.simphony.runtime_2.0.0/bin:\
$REPASTHOME/repast.simphony.runtime_2.0.0/lib/*:\
$REPASTHOME/repast.simphony.core_2.0.0/lib/*:\
$REPASTHOME/repast.simphony.bin_and_src_2.0.0/repast.simphony.bin_and_src.jar:\
$REPASTHOME/repast.simphony.gis_2.0.0/lib/* \
repastcity3.main.RepastCityMain ../repastcity3.rs/
