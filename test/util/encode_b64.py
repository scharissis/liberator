#!/usr/bin/python

import base64
import json
import sys

filename = sys.argv[1]

data = None
with open(filename, "r") as f:
	data = json.load(f)
encoded = base64.b64encode(str(data))
print(encoded)
