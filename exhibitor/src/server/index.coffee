path = require 'path'
express = require 'express'
bodyParser = require 'body-parser'
cors = require 'cors'

log = require './log'
config = require './config'

app = express()
app.disable 'x-powered-by'
app.use bodyParser.json()
app.use bodyParser.urlencoded({ extended: true })

if config.liveReload
  log.info "Using live reload webapp"
  cors = require 'cors'
  webpack = require 'webpack'
  WebpackDevServer = require 'webpack-dev-server'
  webpackConfig = require path.join(__dirname, '../../webpack.config.js')

  app.use cors()
  devServer = new WebpackDevServer webpack(webpackConfig),
    publicPath: webpackConfig.output.publicPath
    hot: true

  devServer.listen 3001, 'localhost', (err, result) ->
    if err
      log.error err

    log.info 'App running on http://localhost:3001'

else
  log.info("Using static webapp")
  app.use express.static(path.join(__dirname, '../../dist'))

app.get '/', (req, res) ->
  res.json "success": true

server = app.listen 3000, ->
  if !config.liveReload
    log.info 'App running on http://localhost:%d', server.address().port
