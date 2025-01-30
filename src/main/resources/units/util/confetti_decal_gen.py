from PIL import Image, ImageDraw
from os import listdir, rename
from random import randrange, choice
from shutil import copyfile

c = [(0,0,255), (0,255,0), (255,0,0),
     (255,255,0), (255,0,255), (0,255,255)]
d = "../"
for i in listdir(d):
    if not i.startswith("rbw_"): continue
    rename(d+i, d+i.replace("rbw_", "sbt_"))
##    if not i.endswith("_blu.png"): continue
##    if i != "blue.png": continue
##    x = Image.open(d+i)
##    y = Image.new("RGBA", x.size)
##    z = ImageDraw.Draw(y)
##    for j in range(50000):
##        r = max(randrange(8),randrange(8))+1
##        u = randrange(-r, x.width)
##        v = randrange(-r, x.height)
##        z.ellipse([u, v, u+r, v+r], fill=choice(c))
##    y.save(d+i.replace("_blu", "_ptb"), dpi=(300,300))
##    y.close()
