from PIL import Image, ImageDraw
from os import listdir, rename
from random import randrange, choice
from shutil import copyfile

c = [(0,0,255), (0,255,0), (255,0,0),
     (255,255,0), (255,0,255), (0,255,255)]
##d = "../"
##d = "../../command_token/"
##for i in listdir(d):
##    if not i.startswith("grn_"): continue
##    if not i.endswith("_grn.png"): continue
##    copyfile(d+i, d+i.replace("grn_", "hqn_"))
##    rename(d+i, d+i.replace("_mgm", "_gcr"))
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

def duplicate(oldshort, newshort, oldlong, newlong):
    d = "../"
    for i in listdir(d):
        if i.startswith(oldshort+"_"):
            copyfile(d+i, d+i.replace(oldshort+"_", newshort+"_"))
        if i.startswith(oldlong+"_"):
            copyfile(d+i, d+i.replace(oldlong+"_", newlong+"_"))
    d = "../../command_token/"
    for i in listdir(d):
        if i.endswith("_"+oldshort+".png"):
            copyfile(d+i, d+i.replace("_"+oldshort+".png", "_"+newshort+".png"))
    d = "../../emojis/colors/"
    for i in listdir(d):
        if i == oldlong + ".png":
            copyfile(d+i, d+i.replace(oldlong, newlong))

##duplicate("red", "pld", "red", "plaid")
duplicate("grn", "hqn", "green", "harlequin")
