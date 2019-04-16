#!/usr/bin/env python

import os
import subprocess

for filename in os.listdir('res/drawable-xhdpi/'):
    resourcename = os.path.splitext(filename)[0]
    if resourcename[-2:] == '.9':
        resourcename = resourcename[:-2]
    p = subprocess.Popen(['grep', '-nr', 'R.drawable.' + resourcename, 'src/'], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    out, err = p.communicate()
    if str(out) is "":
        p = subprocess.Popen(['grep', '-nr', '@drawable/' + resourcename, 'res/'], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        out, err = p.communicate()
        if str(out) is "":
            #os.remove('res/drawable-xhdpi/' + filename)
            print 'Unused file : ' + filename

for filename in os.listdir('res/drawable/'):
    resourcename = os.path.splitext(filename)[0]
    if resourcename[-2:] == '.9':
        resourcename = resourcename[:-2]
    p = subprocess.Popen(['grep', '-nr', 'R.drawable.' + resourcename, 'src/'], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    out, err = p.communicate()
    if str(out) is "":
        p = subprocess.Popen(['grep', '-nr', '@drawable/' + resourcename, 'res/'], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        out, err = p.communicate()
        if str(out) is "":
            #os.remove('res/drawable/' + filename)
            print 'Unused file : ' + filename