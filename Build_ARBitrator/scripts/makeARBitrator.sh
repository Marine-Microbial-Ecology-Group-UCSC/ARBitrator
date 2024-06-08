#!/bin/bash

###
### This script builds ARBitrator from source code.  It then makes
### a tgz that has ARBitrator and other required files.  The final
### tgz is (once verified) should be shared with the world via the
### lab page.
###

desc=$1
if [ -z "$desc" ] ; then
    echo "Please provide a tag that will be used in the name of the distribution."
    echo "E.g. 'Apr2019' if you want ARBitrator.Apr2019.tgz"
    exit -1
fi
ARBitratorTGZ="ARBitrator.${desc}.tgz"
echo "Will create distribution in $ARBitratorTGZ"

## Disabled because I am no lonber using Eclipse's jar-in-jar loader,
## because it mysteriously is no longer working (???).  I changed the
## manifest accordingly.
##if [ ! -d "org" ] ; then
##    echo "There is no org directory, which has required eclipse classes for jar-in-jar class loading."
##    exit -1
##fi

## Still need readseq.jar to compile classes even if no longer including
## it in the ARBitrator jar.
if [ ! -f "readseq.jar" ] ; then
    echo "Where is readseq.jar?"
    exit -1
fi

echo "########## Making class files ##########"
javac -cp readseq.jar arbitrator/utils/*.java arbitrator/pipeline/*.java
if [ $? != 0 ] ; then
    echo "javac error"
    exit
fi
echo

echo "########## Making archive ##########"
## Old way, assuming Eclipse jar-in-jar support, is commented out.  The active
## code does not include 'org' (jar-in-jar), nor does it include readseq.jar because
## readseq.jar must sit alongside the ARBitrator jar.
##jar cvfm ARBitrator.${desc}.jar META-INF/MANIFEST.MF readseq.jar org arbitrator
jar cvfm ARBitrator.jar META-INF/MANIFEST.MF arbitrator

# Now make a tgz with ARBitrator as well as other files that we want to share
# with its users.  Note that "ARBitrator.jar" is used by runARBitrator.sh
echo
echo "Making the tgz..."
tar cvfz $ARBitratorTGZ \
          ARBitrator.jar readseq.jar \
          nifh_representatives_as_ACCESSIONS.txt \
          runARBitrator.sh getNewAccessions.sh \
          README
