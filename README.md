# ARBitrator

ARBitrator is a pipeline that retrieves and quality-filters _nifH_ genes in GenBank for incorporation into a curated database such as the [ARB _nifH_ database](https://www.jzehrlab.com/about-3). With a few parameter changes, other genes can be targeted. ARBitrator was created by [Philip Heller](https://www.sjsu.edu/people/philip.heller) ([Heller et al., 2014](https://doi.org/10.1093/bioinformatics/btu417) during his PhD in the [Zehr Lab at UC Santa Cruz](https://www.jzehrlab.com). Since then modifications have been made to accommodate changes on the NCBI side including the retirement of GI numbers, the switch to HTTPS, and faster query rates when using API keys.  Newer versions of ARBitrator are functionally the same as the original.

### Running ARBitrator
Please download *[ARBitrator.31Mar2022.tgz](Versions/31Mar2022/ARBitrator.31Mar2022.tgz)* which is the most recent version and is compatible with NCBI as of Apriel 2024.  (The link in the previous sentence takes you to the .tgz which can be downloaded using the "..." on the right side of the page.)  In the .tgz you will find a README explaining how to run ARBitrator using the script runARBitrator.sh.  This script launches ARBitrator with Java as described in the 2014 paper.

### Building ARBitrator
If you want to examine or modify the source code or build your own ARBitrator, then please clone the repository and see [Build_ARBitrator/README](Build_ARBitrator/README).
