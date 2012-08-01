#/bin/sh
for file in *.png
do
	convert $file -resize 67% $file
done
