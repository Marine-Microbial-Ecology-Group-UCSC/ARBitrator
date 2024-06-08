#!/bin/bash

# q and s as in Phil's paper and use the same nifH representatives.  Posdom cd02040
# and uninfdom cd02117 are mentioned in the paper.
# nifh_representatives_as_ACCESSIONS.txt has Phil's fifteen representatives as
# accesions rather than GI's.  Email June 7 from Phil okays these params (though I
# had two additional negative domains for testing only).

ARBitrator=ARBitrator.jar
if [ ! -f "$ARBitrator" ] ; then
    echo "Where is ${ARBitrator}?"
    exit -1
fi

# readseq.jar is no longer is within ARBitrator.jar.  The two jars should be in
# the directory from which this script is run.
if [ ! -f "readseq.jar" ] ; then
    echo "Where is readseq.jar?  ARBitrator requires it."
    exit -1
fi

if [ ! -z "$1" ] ; then
    if [[ "$1" =~ [^a-zA-Z0-9] ]]; then
        echo "Your API key has characters other than A-Z, a-z, 0-9. Did you mistype?"
        exit -1
    fi
    ApiKeyParam="-apikey $1"
fi

# jmagasin May 2019: Launch slightly differently now.  Manifest changed to
# specify Pipeline as the entry point, and also to include readseq.jar in the
# class path.
#
# jmagasin April 2022: Add uninformative domains pfam00142 and COG1348 to guard
# against false negatives when ARBitrator evaluates the RBS Blast results.
# (Recall that if a target sequence aligns slightly better to an uninformative
# domain than to cd02040, then ARBitrator will reject the target. ARBitrator
# requires the best hit to be to the positive domains.)  uninformative rejection
# of a true NifH.)
java -jar $ARBitrator \
     -q 2 -s 1 -replistfile nifh_representatives_as_ACCESSIONS.txt \
     -ol outputListFor_nifh_as_ACCESSIONS \
     -oe outputListFor_nifh_as_ACCESSIONS_EMBL \
     -oefails posSeqsWitNoCreatedEMBL \
     -posdom cd02040 \
     -uninfdom cd02117 \
     $ApiKeyParam

