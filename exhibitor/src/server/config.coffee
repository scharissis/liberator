dotenv = require 'dotenv'
dotenv.load()

module.exports =
  liveReload: process.env['EXHIBITOR_LIVE_RELOAD'] == "true"
