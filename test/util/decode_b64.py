#!/usr/bin/python

# Reads GitHub-formatted Package.json JSON files and decodes
# the base64 'content' section (which is the actual package.json file).

import base64
import json
import sys

def pretty(d):
	return json.dumps(d, indent=2)

filename = sys.argv[1]
data = None
with open(filename, "r") as f:
	data = json.load(f)
content = base64.b64decode(data['content'])
data['content'] = json.loads(content)
print(pretty(data))
