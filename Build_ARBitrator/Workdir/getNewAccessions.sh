#!/bin/bash

## Identify new accessions found by ARBitrator and make an EMBL with them.  This
## script is mainly useful for updating your ARB database. See usage docs.

ARBITRATORACCLIST="ARBitratorAccessions.tmp"
OLDACCLIST="oldAccessions.tmp"
NEWACCLIST="newAccessions.txt"
NEWEMBL="newEMBL.txt.gz"
MISSINGEMBL="missingEMBL.txt"

usageStr="
Compare the accessions found by ARBitrator to a list of accessions, which could
be the sequences in your ARB database. This script identifies new sequences that
ARBitrator found so that you can update your database.

Usage:
    getNewStuff.sh  accListGz  [ARBitratorDir]

Inputs:

  - accListGz: Path to file (gzip'd) with accessions e.g that are in your ARB
    database.

  - ARBitratorDir: Name of directory with ARBitrator results. If not provided
    then the current directory is used.  The following results are needed, and
    you must gzip the two files.
        outputListFor_nifh_as_ACCESSIONS.gz
        posSeqsWitNoCreatedEMBL.gz
        work/finished_embls     <-- Directory. Do not gzip contents.

Outputs:
  - ${NEWACCLIST}: List of new accessions.
  - ${NEWEMBL}: Concatenated EMBL records for all the new accessions. This file
      is like the ARBitrator output for option '-oe' except that this file will
      have just the new accessions and will be gzip'd.
  - ${MISSINGEMBL}: New accessions that for which ARBitrator failed to make EMBLs.
      Possible reasons these accessions have no EMBL include:
         - The NCBI server stopped responding during the ARBitrator run.
         - Missing information for the accession.
      Your ARBitrator log should provide information on problems for these accessions.
"

DBLIST=$1
ARBDIR=$2
if [ -z "$ARBDIR" ] ; then ARBDIR='.' ; fi
NIFLIST="$ARBDIR/outputListFor_nifh_as_ACCESSIONS.gz"
POSNOEMBL="$ARBDIR/posSeqsWitNoCreatedEMBL.gz"
FINISHEDEMBLSDIR="$ARBDIR/work/finished_embls"


[ -f "$DBLIST" ] && [ -f "$NIFLIST" ] && [ -f "$POSNOEMBL" ] && [ "$FINISHEDEMBLSDIR" ]
if [ "$?" -ne 0 ] ; then
    echo "$usageStr"
    exit -1
fi

# What accessions are new, excluding those that had no EMBL file.
zcat "$NIFLIST" "$POSNOEMBL" | sort | uniq -c | grep -v '    2 ' | sed 's/^ *1 //' > "$ARBITRATORACCLIST"
N=`cat "$ARBITRATORACCLIST" | wc -l` # | tr -sd '[:blank:]'`
echo "ARBitrator found $N accessions that had EMBL files."

zcat "$DBLIST" | sort | uniq > "$OLDACCLIST"
N=`cat "$OLDACCLIST" | wc -l` # | tr -sd '[:blank:]'`
echo "There are $N accessions in $DBLIST."

join -v 2 "$OLDACCLIST" "$ARBITRATORACCLIST" > "$NEWACCLIST"
N=`cat "$NEWACCLIST" | wc -l` # | tr -sd '[:blank:]'`
echo "$N new accessions were found by ARBitrator."
rm "$ARBITRATORACCLIST" "$OLDACCLIST"

## Deug: Intersection of new and DB accessions should be empty.
#cat "$NEWACCLIST" "$OLDACCLIST" | sort | uniq | grep -c '   2 '

echo "Creating $NEWEMBL"
if [ -f "$NEWEMBL"     ] ; then rm "$NEWEMBL" ; fi
if [ -f "$MISSINGEMBL" ] ; then rm "$MISSINGEMBL" ; fi

count=0
missing=0
while read acc; do
    wantEmbl=${FINISHEDEMBLSDIR}/ARB_ready_${acc}.embl
    if [ ! -f  "$wantEmbl" ] ; then
        echo "$acc" >> "$MISSINGEMBL"
        missing=$((missing + 1))
    else
        cat "$wantEmbl" | gzip >> "$NEWEMBL"
        count=$((count + 1))
    fi
done < "$NEWACCLIST"
echo "Done!  Concatenated $count EMBL files. $missing EMBL files were missing."
exit 0
