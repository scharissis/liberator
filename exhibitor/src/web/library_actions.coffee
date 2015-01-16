Reflux = require 'reflux'

api = require 'web/api'

search_action = Reflux.createAction
  asyncResult: true

search_action.listenAndPromise (search_string) ->
  library_ids = search_string.split(',').map (s) -> s.trim()
  api.libraries(library_ids)

module.exports =
  search: search_action
