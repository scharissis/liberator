module.exports = class GithubArchiveFile
  constructor: (@iso_date, @hour) ->

  filename: ->
    "#{@iso_date}-#{@hour}.json.gz"

  github_archive_url: ->
    "http://data.githubarchive.org/#{@filename()}"
