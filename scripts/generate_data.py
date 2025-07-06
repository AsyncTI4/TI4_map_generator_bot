#!/usr/bin/env python3
"""
Unified script to parse JSON files and generate TypeScript files.
"""

import json
import os
from pathlib import Path
from typing import List, Dict, Any, Optional
import argparse

# Configuration for different data types
CONFIGS = {
    'tech': {
        'input_folder': 'src/main/resources/data/technologies',
        'output_file': 'techs.ts',
        'type_import': 'Tech',
        'export_name': 'techs',
        'sort_key': 'alias',
        'is_array_data': True,
        'blacklist': [],
    },
    'tokens': {
        'input_folder': 'src/main/resources/data/tokens',
        'output_file': 'tokens.ts',
        'type_import': 'Token',
        'export_name': 'tokens',
        'sort_key': 'id',
        'is_array_data': True,
        'blacklist': [],
    },
    'units': {
        'input_folder': 'src/main/resources/data/units',
        'output_file': 'units.ts',
        'type_import': 'Unit',
        'export_name': 'units',
        'sort_key': 'id',
        'is_array_data': True,
        'blacklist': [],
    },
    'planets': {
        'input_folder': 'src/main/resources/planets',
        'output_file': 'planets.ts',
        'type_import': 'Planet',
        'export_name': 'planets',
        'sort_key': 'id',
        'is_array_data': False,  # planets.py doesn't handle arrays
        'blacklist': ['unitPositions'],
    },
    'relics': {
        'input_folder': 'src/main/resources/data/relics',
        'output_file': 'relics.ts',
        'type_import': 'Relic',
        'export_name': 'relics',
        'sort_key': 'alias',
        'is_array_data': True,
        'blacklist': [],
    },
    'secret_objectives': {
        'input_folder': 'src/main/resources/data/secret_objectives',
        'output_file': 'secretObjectives.ts',
        'type_import': 'SecretObjective',
        'export_name': 'secretObjectives',
        'sort_key': 'alias',
        'is_array_data': True,
        'blacklist': [],
    },
    'public_objectives': {
        'input_folder': 'src/main/resources/data/public_objectives',
        'output_file': 'publicObjectives.ts',
        'type_import': 'PublicObjective',
        'export_name': 'publicObjectives',
        'sort_key': 'alias',
        'is_array_data': True,
        'blacklist': [],
    },
    'promissory_notes': {
        'input_folder': 'src/main/resources/data/promissory_notes',
        'output_file': 'promissoryNotes.ts',
        'type_import': 'PromissoryNote',
        'export_name': 'promissoryNotes',
        'sort_key': 'alias',
        'is_array_data': True,
        'blacklist': [],
    },
    'leaders': {
        'input_folder': 'src/main/resources/data/leaders',
        'output_file': 'leaders.ts',
        'type_import': 'LeaderData',
        'export_name': 'leaders',
        'sort_key': 'alias',
        'is_array_data': True,
        'blacklist': [],
    },
    'colors': {
        'input_folder': 'src/main/resources/data/colors',
        'output_file': 'colors.ts',
        'type_import': 'Color',
        'export_name': 'colors',
        'sort_key': 'id',
        'is_array_data': True,
        'blacklist': [],
    },
    'agendas': {
        'input_folder': 'src/main/resources/data/agendas',
        'output_file': 'agendas.ts',
        'type_import': 'Agenda',
        'export_name': 'agendas',
        'sort_key': 'alias',
        'is_array_data': True,
        'blacklist': [],
    },
    'systems': {
        'input_folder': 'src/main/resources/systems',
        'output_file': 'systems.ts',
        'type_import': 'TileData',
        'export_name': 'systems',
        'sort_key': 'id',
        'is_array_data': False,
        'blacklist': [],
    },
    'abilities': {
        'input_folder': 'src/main/resources/data/abilities',
        'output_file': 'abilities.ts',
        'type_import': 'Ability',
        'export_name': 'abilities',
        'sort_key': 'alias',
        'is_array_data': True,
        'blacklist': [],
    }
}

def remove_blacklisted_keys(data: Dict[str, Any], blacklist: List[str]) -> Dict[str, Any]:
    """Remove blacklisted keys from a data dictionary"""
    if not blacklist:
        return data

    cleaned_data = {}
    for key, value in data.items():
        if key not in blacklist:
            cleaned_data[key] = value

    return cleaned_data

def aggregate_data(config: Dict[str, Any]) -> List[Dict[str, Any]]:
    """Generic function to aggregate data from JSON files"""
    input_folder = Path(config['input_folder'])

    # Check if the folder exists
    if not input_folder.exists():
        print(f"Error: Folder '{input_folder}' not found!")
        return []

    # List to store all data
    all_data = []

    # Process each JSON file in the folder
    json_files = list(input_folder.glob("*.json"))
    print(f"Found {len(json_files)} JSON files to process...")

    for json_file in json_files:
        try:
            with open(json_file, 'r', encoding='utf-8') as f:
                data = json.load(f)

            # Handle arrays if configured to do so
            if config['is_array_data'] and isinstance(data, list):
                # If it's an array, process each item
                for item in data:
                    if isinstance(item, dict):
                        item = remove_blacklisted_keys(item, config['blacklist'])
                    all_data.append(item)
            else:
                # If it's a single object or we don't handle arrays
                if isinstance(data, dict):
                    data = remove_blacklisted_keys(data, config['blacklist'])
                all_data.append(data)

            print(f"Processed: {json_file.name}")

        except json.JSONDecodeError as e:
            print(f"Error parsing {json_file.name}: {e}")
        except Exception as e:
            print(f"Error processing {json_file.name}: {e}")

    return all_data

def write_typescript_file(data: List[Dict[str, Any]], config: Dict[str, Any]):
    """Write the aggregated data to a TypeScript file"""
    # Sort data by the configured key
    data.sort(key=lambda x: x.get(config['sort_key'], ''))

    output_file = config['output_file']

    try:
        with open(output_file, 'w', encoding='utf-8') as f:
            # Write the auto-generated comment
            f.write('// Auto-generated file - Do not edit manually\n')
            f.write(f'// Generated from {config["input_folder"]}/*.json files\n\n')

            # Write the import statement
            f.write(f'import {{ {config["type_import"]} }} from "./types";\n\n')

            # Standard JSON formatting for all data types
            f.write(f'export const {config["export_name"]}: {config["type_import"]}[] = ')

            # Write the JSON data with proper formatting
            json_str = json.dumps(data, indent=2, ensure_ascii=False)
            f.write(json_str)

            # Add semicolon at the end
            f.write(';\n')

        print(f"\nSuccessfully aggregated {len(data)} items into '{output_file}'")
        print(f"Output file size: {os.path.getsize(output_file)} bytes")

    except Exception as e:
        print(f"Error writing output file: {e}")

def main():
    """Main function"""
    parser = argparse.ArgumentParser(description='Generate TypeScript files from JSON data')
    parser.add_argument('data_type',
                       choices=list(CONFIGS.keys()) + ['all'],
                       help='Type of data to generate (or "all" for all types)')
    parser.add_argument('--list-types', action='store_true',
                       help='List all available data types')

    args = parser.parse_args()

    if args.list_types:
        print("Available data types:")
        for data_type, config in CONFIGS.items():
            print(f"  {data_type}: {config['input_folder']} -> {config['output_file']}")
        return

    if args.data_type == 'all':
        # Process all data types
        for data_type, config in CONFIGS.items():
            print(f"\n{'='*50}")
            print(f"Processing {data_type}...")
            print(f"{'='*50}")

            data = aggregate_data(config)
            if data:
                write_typescript_file(data, config)
            else:
                print(f"No data found for {data_type}")
    else:
        # Process single data type
        config = CONFIGS[args.data_type]
        print(f"Processing {args.data_type}...")
        print(f"Input folder: {config['input_folder']}")
        print(f"Output file: {config['output_file']}")
        print("-" * 50)

        data = aggregate_data(config)
        if data:
            write_typescript_file(data, config)
        else:
            print("No data found")

if __name__ == "__main__":
    main()