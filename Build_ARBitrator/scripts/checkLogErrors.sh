#!/bin/bash

###
### If ARBitrator output was captured in a log, then use this script to tally
### error messages.
###

logFile=$1
if [ ! -f "$logFile" ] ; then
    echo "Please specify a log file (i.e., stdout/err from your arbitrator run)."
    exit -1
fi


grep 'Failed' $logFile > failures.tmp
totFailed=`wc -l failures.tmp`
echo "There were a total of $totFailed 'Failed' messages."

convFailed=`grep -c '... Failed: Conversion failure:' failures.tmp`
echo "There were a total of $convFailed failures to convert protein records to nucleotide records."
echo "Here is the breakdown of errors"
grep "Conversion failure" failures.tmp | sed 's/^.*Conversion failure: //' | cut -d' ' -f1 | sort | uniq -c
rm failures.tmp
