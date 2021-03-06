winston = require 'winston'

module.exports = new winston.Logger
  transports: [
    new winston.transports.Console
      colorize: true
      level: 'debug'
      timestamp: true
  ]
