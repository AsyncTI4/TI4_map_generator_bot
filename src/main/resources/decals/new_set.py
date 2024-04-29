import shutil
from os import listdir

n = 37
units = ["ca", "csd", "cv", "dd", "dn", "ff", "fs", "gf", "mf", "pd", "sd", "ws"]

for i in units:
    shutil.copy(f"C:/Users/Bradley/Documents/GitHub/TI4_map_generator_bot/src/main/resources/units/blu_{i}.png",
                f"C:/Users/Bradley/Documents/GitHub/TI4_map_generator_bot/src\main/resources/decals/cb_{n}_{i}_blk.png")

##for i in units:
##    shutil.copy(f"C:/Users/Bradley/Documents/GitHub/TI4_map_generator_bot/src\main/resources/decals/cb_{n}_{i}_blk.png",
##                f"C:/Users/Bradley/Documents/GitHub/TI4_map_generator_bot/src\main/resources/decals/cb_{n}_{i}_wht.png")
