import sys
import json
import decode_b64


if len(sys.argv) < 2:
    sys.exit("Usage: {0} <filename.json>".format(sys.argv[0]))

filename = sys.argv[1]

dep_fields = ["dependencies", "devDependencies"]

def printDep(d):
    dep_name, dep_version, timestamp, commit = d
    timestamp = str(int(timestamp)/1000)
    print(
        "Dependency(\"{dep_name}\", List(Event(\"{dep_version}\", \"{event_type}\", \"{event_timestamp}\", \"{event_commit}\"))),"
        .format(dep_name=dep_name, dep_version=dep_version, event_type="new", event_timestamp=timestamp, event_commit=commit)
    )

deps = {}

timestamp, commit = ''.join(filename.split('/')[-1].split('.')[:-1]).split('_')[1:3]
j = decode_b64.decode(filename)['content']
for field in dep_fields:
    if field in j:
        for dep_name, dep_version in j[field].iteritems():
            printDep((dep_name, dep_version, timestamp, commit))
            deps[dep_name] = (dep_version, timestamp, commit)

print "{0} dependencies detected.".format(len(deps.keys()))
