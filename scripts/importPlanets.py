import sys
import os
import json

TEXT_FILE="planetsToImport.txt"
DESTINATION_DIR="../src/main/resources/planets/"

def save_file(obj, data):
    filename = f"{data.get('id')}.json"
    path = os.path.join(DESTINATION_DIR, filename)
    with open(path, "w", encoding="utf-8") as f:
        f.write(obj)
    print(f"Saved {filename}")

def read_json(json_str: str):
    def fail(msg, e):
        print('Loading data failed: {}'.format(msg))
        print(e)
        sys.exit(1)

    try:
        data = json.loads(json_str)
        return data
    except json.JSONDecodeError as e:
        fail('Invalid JSON', e)
    except TypeError as e:
        fail('JSON does not match expected format', e)

if __name__ == '__main__':
    with open(TEXT_FILE, encoding='utf-8') as file:
        for line in file:
            data = read_json(line)
            save_file(line, data)
    print('Done.')
