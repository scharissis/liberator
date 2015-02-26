#!/usr/bin/python

import base64
import json
import sys

def pretty(d):
	return json.dumps(d, indent=2)

filename = sys.argv[1]

data = None
with open(filename, "r") as f:
	data = json.load(f)
encoded = base64.b64encode(pretty(data))
print(encoded)
