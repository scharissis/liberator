path = require 'path'
_ = require 'lodash'
express = require 'express'
bodyParser = require 'body-parser'
cors = require 'cors'

log = require './log'
config = require './config'

pg = require 'pg'
db_conn = "postgres://liberator:liberator@localhost:5432/liberator"

app = express()
app.disable 'x-powered-by'
app.use bodyParser.json()
app.use bodyParser.urlencoded({ extended: true })

if config.hotReload
  log.info "Using hot reloaded webapp (NOT for production use)"
  cors = require 'cors'
  webpack = require 'webpack'
  WebpackDevServer = require 'webpack-dev-server'
  webpackConfig = require path.join(__dirname, '../../webpack.config.js')

  app.use cors()
  devServer = new WebpackDevServer webpack(webpackConfig),
    publicPath: webpackConfig.output.publicPath
    hot: true
    stats:
      chunks: false

  devServer.listen 3001, 'localhost', (err, result) ->
    if err
      log.error err

    log.info 'Hot reloading app running on http://localhost:3001'


app.use express.static(path.join(__dirname, '../../dist'))


date_to_string = (date) ->
  date.toISOString().slice(0, 10)

# Initializes a connection pool. It will keep idle connections open
# for a (configurable) 30 seconds and set a limit of 20 (also configurable).
fetch_library = (lib, callback) ->
  pg.connect db_conn, (err, client, done) ->
    if err
      return console.error('error fetching client from pool', err)
    client.query 'SELECT package_id, usage_date, usage_count FROM liberator_nodejs WHERE package_id=$1', [lib], (err, result) ->
      done()  # release the client back to the pool
      if err
        console.error('error running query', err)
        callback(err)
      dates = _.map(result.rows, (row, index) ->
        date_to_string(new Date(row.usage_date))
      )
      values = _.map(result.rows, (row, index) ->
        row.usage_count
      )
      callback(null, {
        id: lib,
        stats: { dates: dates, usage: values }
      })

app.get '/api/libraries', (req, res) ->
  libs = []
  libs.push(req.query.id)
  fetch_library libs[0], (err, result) ->   # TODO: Handle multiple libs
    if err
      return console.log "Error: fetch_library: ", err
    res.json([result])

server = app.listen 3000, ->
  log.info 'Static app running on http://localhost:%d', server.address().port
