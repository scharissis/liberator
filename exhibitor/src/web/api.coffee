request = require 'superagent-promise'

module.exports =
  libraries: (library_ids) ->
    query_string = library_ids.map((id) -> "id=#{id}").join('&')
    request.get("http://localhost:3000/api/libraries?#{query_string}")
      .end()
      .then (res) ->
        res.body
