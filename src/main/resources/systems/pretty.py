from os import listdir
from json import dumps, loads

for i in listdir():
    if not i.endswith(".json"): continue
    with open(i, mode="r") as f:
        t = loads(f.read())
    with open(i, mode="w", newline="\n") as f:
        print(dumps(t, indent=4), file=f, end="")
