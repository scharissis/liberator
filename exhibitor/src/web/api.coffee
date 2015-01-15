request = require 'superagent-promise'

module.exports =
  libraries: () ->
    res = yield request.get('http://localhost:3000/api/libraries?id=npm/react&id=npm/grunt').end()
    res.body.data
