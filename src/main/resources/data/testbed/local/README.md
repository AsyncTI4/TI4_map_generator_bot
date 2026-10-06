# Your personal test bed files

Everything in this folder except this README and the empty subfolders is ignored by git, so your files stay on
your machine. Put them in these subfolders:

| Folder | What | Template |
| --- | --- | --- |
| `presets/` | Presets for `/testbed apply` | copy one from `data/testbed/*.json` |
| `scripts/` | Scripts for `/testbed run` | copy one from `data/testbed/scripts/` |
| `shortcuts/` | Test button groups for the panel | see below |

A local file with the same name as a shipped one replaces it. After editing, run `/testbed reload` (no restart
needed); it lists any file that does not validate. Move a file into the shared folders when the whole team should
have it.

Test button group template (`shortcuts/<feature>.json`):

```json
{
  "group": "My feature",
  "description": "What these buttons are for.",
  "shortcuts": [
    { "label": "Seat 1 gets Sabotage", "steps": [ { "do": "hand", "as": "seat1", "hand": { "acs": ["sabo1"] } } ] }
  ]
}
```

Full reference: `DEVELOPER_TESTBED.md` in the repository root.
