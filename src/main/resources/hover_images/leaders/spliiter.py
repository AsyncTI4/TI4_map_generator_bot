from PIL import Image
f = "thunders-edge.back.jpg"
w = 5
h = 5

x = Image.open(f)
for i in range(w):
    for j in range(h):
        x.crop((x.width/w*i, x.height/h*j, x.width/w*(i+1), x.height/h*(j+1))).save(f"{f}_{i}_{j}.png")
