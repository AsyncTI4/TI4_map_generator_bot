from os import listdir
from os.path import abspath, join, dirname
from json import dumps, loads

SYSTEMS_DIR = abspath(join(dirname(__file__), "..", "src", "main", "resources", "systems"))

for filename in listdir(SYSTEMS_DIR):
    if not filename.endswith(".json"): continue
    filepath = join(SYSTEMS_DIR, filename)
    with open(filepath, mode="r") as f:
        data = loads(f.read())
    with open(filepath, mode="w", newline="\n") as f:
        print(dumps(data, indent=4), file=f, end="")