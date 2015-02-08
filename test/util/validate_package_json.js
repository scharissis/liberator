var readJson = require('read-package-json')
var fs = require('fs');
var path = require('path')

filepath = process.argv[2]
filename = path.basename(filepath)

// readJson(filename, [logFunction=noop], [strict=false], cb)
readJson(filepath, console.error, false, function (er, data) {
  if (er) {
    console.error("BAD : " + filename)
    console.error(er)
    return
  }

  //var b64string = data.content;
  //var buf = new Buffer(b64string, 'base64');
  //var data_decoded = buf.toString('utf8');
  

  console.error("GOOD: " + filename)
  fs.writeFile("healed/"+filename, data, function(err) {
    if(err) {
	console.log(err);
    } else {
	//console.log("The file was saved!");
    }
  });
});

