module.exports = class RepoCrawlRequest
  constructor: (@source, @repo) ->

  toString: ->
    "#{@source}:#{@repo}"
