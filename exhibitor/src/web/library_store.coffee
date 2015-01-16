Reflux = require 'reflux'

LibraryActions = require 'web/library_actions'

module.exports = Reflux.createStore
  listenables: [LibraryActions]

  onSearchCompleted: (libraries) ->
    this.trigger(libraries)

  onSearchFailed: (err) ->
    # TODO error handling
    console.log("Library search failed ", err)
