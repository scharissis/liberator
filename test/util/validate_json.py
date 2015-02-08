import json
import sys

filename = sys.argv[1]

with open(filename) as f:
	try:
		j = json.load(f)
	except Exception as e:
		sys.exit(e)
