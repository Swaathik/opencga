#!/bin/bash
SCRIPT_DIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )

#echo "$*"
$BABELOMICS_HOME/babelomics.sh --tool fatiscan $*


while [[ $# > 1 ]]
do
key="$1"

case $key in
    --outdir)
    OUTDIR="$2"
    shift
    ;;
    *)
            # unknown option
    ;;
esac
shift
done

echo "$OUTDIR"

for i in $( ls $OUTDIR | grep "go_molecular_function\|go_biological_process\|go_cellular_component" | grep -v "0.005\|0.05\|0.1\|0.01" | grep ".txt" ); do
echo Rscript $SCRIPT_DIR/fatigo-graph/get_GOsubgraph.r $OUTDIR/$i 0.005
    Rscript $SCRIPT_DIR/fatigo-graph/get_nodeAttributes_from_fatiGO.r $OUTDIR/$i 0.005

    Rscript $SCRIPT_DIR/fatigo-graph/get_GOsubgraph.r $OUTDIR/$i 0.05
    Rscript $SCRIPT_DIR/fatigo-graph/get_nodeAttributes_from_fatiGO.r $OUTDIR/$i 0.05

    Rscript $SCRIPT_DIR/fatigo-graph/get_GOsubgraph.r $OUTDIR/$i 0.01
    Rscript $SCRIPT_DIR/fatigo-graph/get_nodeAttributes_from_fatiGO.r $OUTDIR/$i 0.01

    Rscript $SCRIPT_DIR/fatigo-graph/get_GOsubgraph.r $OUTDIR/$i 0.1
    Rscript $SCRIPT_DIR/fatigo-graph/get_nodeAttributes_from_fatiGO.r $OUTDIR/$i 0.1

    name="${i%.*}"
    title="${name//_/ }"

    item='<item name="'${name}'_0.1" title="'${title}' 0.1 graph" type="" tags="GO_NETWORKVIEWER" style="" group="Significant Results.GO Network" context="">'${name}'_0.1_GOsubgraph.sif,'${name}'_0.1_GOsubgraph.attr,'${name}'_0.1_nodes.attr</item>'
    sed -i "/${i}/a ${item}" $OUTDIR/result.xml

    item='<item name="'${name}'_0.05" title="'${title}' 0.05 graph" type="" tags="GO_NETWORKVIEWER" style="" group="Significant Results.GO Network" context="">'${name}'_0.05_GOsubgraph.sif,'${name}'_0.05_GOsubgraph.attr,'${name}'_0.05_nodes.attr</item>'
    sed -i "/${i}/a ${item}" $OUTDIR/result.xml

    item='<item name="'${name}'_0.01" title="'${title}' 0.01 graph" type="" tags="GO_NETWORKVIEWER" style="" group="Significant Results.GO Network" context="">'${name}'_0.01_GOsubgraph.sif,'${name}'_0.01_GOsubgraph.attr,'${name}'_0.01_nodes.attr</item>'
    sed -i "/${i}/a ${item}" $OUTDIR/result.xml

    item='<item name="'${name}'_0.005" title="'${title}' 0.005 graph" type="" tags="GO_NETWORKVIEWER" style="" group="Significant Results.GO Network" context="">'${name}'_0.005_GOsubgraph.sif,'${name}'_0.005_GOsubgraph.attr,'${name}'_0.005_nodes.attr</item>'
    sed -i "/${i}/a ${item}" $OUTDIR/result.xml

done
