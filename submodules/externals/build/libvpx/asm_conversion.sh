#/bin/sh
for i in `find ../../libvpx/ -name "*asm"`; 
do 
	newname=`echo ${i} |sed 's/\.asm/.s/'`
	echo "Converting : ${i} -> ${newname}"
	cat ${i}|perl ../../libvpx/build/make/ads2gas.pl > ${newname}; 
done
