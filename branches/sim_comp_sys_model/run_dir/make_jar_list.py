# Makes a list of all required jars and outputs them in a format to be
# added to a classpath

import os

REPASTHOME = "/Applications/Repast-Simphony-2.0.0-beta/eclipse/plugins/"

# List of directories to search for jar files:

dirs = []
dirs.append("repast.simphony.runtime_2.0.0/lib/")
dirs.append("repast.simphony.core_2.0.0/lib/")
dirs.append("repast.simphony.gis_2.0.0/lib/")

out = ""

for d in dirs:
    fulldir = REPASTHOME + d
    for filename in os.listdir(fulldir):
        if filename[-4:] == '.jar':
            print filename
            out += ("$REPASTHOME/"+d+filename+":\\\n") # Note trailling colon and slash
        else:
            print "Not jar:",filename

# Also add this entry 
out += ("$REPASTHOME/"+"repast.simphony.bin_and_src_2.0.0/repast.simphony.bin_and_src.jar:\n")


# And this one? $REPASTHOME/repast.simphony.runtime_2.0.0/bin/:\


print "*******"
print out
print "*******"
