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


generateDummyData = (lib) ->
  values = _.transform [1..100], (result, i) ->
    if !result.length
      result.push _.random(200)
    else
      result.push _.last(result) + _.random(-20, 20)

  _.flatten [lib, values]


app.get '/api/libraries', (req, res) ->
  libs = []
  libs.push(req.query.id)
  columns = (generateDummyData(lib) for lib in _.flatten(libs))
  data =
    columns: columns

  console.log(JSON.stringify(data))
  res.json "data": data

server = app.listen 3000, ->
  log.info 'Static app running on http://localhost:%d', server.address().port
