request = require 'superagent-promise'

module.exports =
  libraries: () ->
    res = yield request.get('http://localhost:3000/api/libraries').end()
    res.body.data
