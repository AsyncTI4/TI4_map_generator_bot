from PIL import Image
from os import listdir, rename
from random import randrange, choice
from shutil import copyfile

##c = [(0,0,255), (0,255,0), (255,0,0),
##     (255,255,0), (255,0,255), (0,255,255)]
##for i in listdir():
##    if not i.startswith("cb_52_") or not i.endswith("_wht.png"): continue
##    x = Image.open(i)
##    y = Image.new("RGBA", x.size)
##    for j in range(x.width):
##        for k in range(x.height):
####            if (x.getpixel((j,k))[3] > 0 and randrange(10) == 0):
##            if randrange(10) == 0:
##                y.putpixel((j,k), choice(c))
##    y.save(i.replace("cb_52_", "cb_87_"), dpi=(300,300))

##c = [(0,0,255), (0,255,0), (255,0,0),
##     (255,255,0), (255,0,255), (0,255,255),
##     (0,0,0), (255,255,255)]
##for i in listdir():
##    if not i.startswith("cb_52_") or not i.endswith("_wht.png"): continue
##    x = Image.open(i)
##    for n, t in zip([83, 82, 84, 90, 89, 88, 85, 86], c):
##        y = Image.new("RGBA", x.size)
##        for j in range(x.width):
##            for k in range(x.height):
##    ##            if (x.getpixel((j,k))[3] > 0 and randrange(10) == 0):
##                if randrange(10) == 0:
##                    y.putpixel((j,k), t)
##        y.save(i.replace("cb_52_", f"cb_{n}_"), dpi=(300,300))

##y = Image.new("RGBA", (68,68))
##for j in range(68):
##    for k in range(68):
##        if randrange(10) == 0:
##            y.putpixel((j,k), (0,0,255))
##y.save("cb_83_csd_wht.png", dpi=(300,300))

t = [83, 82, 84, 90, 89, 88, 85, 86]
for i in listdir():
    if any(f"cb_{j}_" in i for j in t):
        copyfile(i, i.replace("_wht", "_blk"))
