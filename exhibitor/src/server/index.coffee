express = require 'express'
bodyParser = require 'body-parser'

app = express()
app.disable 'x-powered-by'
app.use bodyParser.json()
app.use bodyParser.urlencoded { extended: true }

app.get '/', (req, res) ->
  res.json "success": true

server = app.listen 3000, ->
  console.log 'App running on http://localhost:%d', server.address().port
