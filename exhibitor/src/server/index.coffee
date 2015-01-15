path = require 'path'
_ = require 'lodash'
express = require 'express'
bodyParser = require 'body-parser'
cors = require 'cors'

log = require './log'
config = require './config'

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

generate_dummy_data = (lib) ->
  num_days = 100
  max_usage = 200
  per_day_variability = 20

  millis_per_day = 24*60*60*1000
  current_date_millis = new Date().getTime()
  start_date = current_date_millis - (200*millis_per_day)
  dates = _.transform [1..num_days], (result, i) ->
    result.push date_to_string(new Date(start_date + ((i-1)*millis_per_day)))

  values = _.transform [1..num_days], (result, i) ->
    if !result.length
      result.push _.random(max_usage)
    else
      result.push _.max([0, _.last(result) + _.random(-per_day_variability, per_day_variability)])

  { dates: dates, values: values }

fetch_library = (lib) ->
  # Will eventually be a real database fetch
  {
    id: lib,
    usage: generate_dummy_data(lib)
  }

app.get '/api/libraries', (req, res) ->
  libs = []
  libs.push(req.query.id)
  res.json (fetch_library(lib) for lib in _.flatten(libs))

server = app.listen 3000, ->
  log.info 'Static app running on http://localhost:%d', server.address().port
