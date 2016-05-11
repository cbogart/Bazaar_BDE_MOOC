#!/bin/bash
#usage: ./run.sh roomname num condition [numcols [width height]]

agentdir="../BazaarAgent/"
jarname="BazaarAgent.jar";


roomname=${1:-"Week0_"};
num=${2:-1};
# hyeju changed 3 => 2
condition=${3:-""}
cols=${4:-3};
width=${5:-300};
height=${6:-180};
rows=4;

cd $agentdir;
echo loading room $roomname$(printf '%02d' $num);
echo condition:$condition;

x=$(( (($num-1) % $cols)*$width ))
y=$(( ((($num-1) / $cols)%rows)*$height ))

echo $cols across, $width x $height at $x, $y;

echo java -Xmx128M -jar "$jarname" -room $roomname$(printf '%02d' $num) -out logs -x$x -y$y -condition "$condition" -launch &

java -Xmx128M -jar "$jarname" -room $roomname$(printf '%02d' $num) -out logs -x$x -y$y -condition "$condition" -launch &
