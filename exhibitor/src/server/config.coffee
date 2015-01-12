dotenv = require 'dotenv'
dotenv.load()

module.exports =
  hotReload: process.env['EXHIBITOR_HOT_RELOAD'] == "true"
