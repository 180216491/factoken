#!/bin/bash
# create multiresolution windows icon
ICON_DST=../../src/qt/res/icons/factoken.ico

convert ../../src/qt/res/icons/factoken-16.png ../../src/qt/res/icons/factoken-32.png ../../src/qt/res/icons/factoken-48.png ${ICON_DST}
