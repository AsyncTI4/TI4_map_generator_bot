import argparse
import shutil
import sys
import os
import json

TEXT_FILE="systemsToImport.txt"
DESTINATION_DIR="../src/main/resources/systems/"

def save_file(obj, data):
    filename = str(data.get('id')) + '.json'
    f = open(DESTINATION_DIR + filename, "w")
    f.write(obj)
    print("Saved " + filename)

def read_json(str):
    def fail(msg, e):
        print('Loading data failed: {}'.format(msg))
        print(e)
        sys.exit(1)

    try:
        data = json.loads(str)
        return data
    except json.JSONDecodeError as e:
        fail('Invalid JSON', e)
    except TypeError as e:
        fail('JSON does not match expected format', e)

if __name__ == '__main__':
    file = open(TEXT_FILE)
    objects = file.readlines()
    for obj in objects:
        data = read_json(obj)
        save_file(obj, data)
    print('Done.')