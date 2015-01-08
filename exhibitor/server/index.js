var express = require('express');
var bodyParser = require('body-parser');

var app = express();
app.disable('x-powered-by');
app.use(bodyParser.json());
app.use(bodyParser.urlencoded({extended: true}));

app.get('/', function(req, res) {
  res.json({"success": true});
});

var server = app.listen(3000, function() {
  console.log('App running on http://localhost:%d', server.address().port);
});
