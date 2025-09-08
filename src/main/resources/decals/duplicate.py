from os import listdir
from shutil import copyfile

old = 45
new = 101

##for i in listdir():
##    if f"cb_{old}_" in i and "blk.png" in i:
##        copyfile(i, i.replace(str(old), str(new)))

for i in listdir():
    if f"cb_{new}_" in i and "blk.png" in i:
        copyfile(i, i.replace("blk", "wht"))
    
