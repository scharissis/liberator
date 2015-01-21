_ = require 'lodash'
GitHubApi = require 'github'
log = require './log'

max_concurrent_requests = 20

create_github_api = ->
  github_api = new GitHubApi
    version: '3.0.0',
    debug: false

  github_api_token = process.env.GITHUB_API_TOKEN
  if !github_api_token
    log.warn('Using unauthenticated github API which is extremely rate limited. Please set GITHUB_API_TOKEN environment variable.')
  else
    github_api.authenticate
      type: 'oauth',
      token: github_api_token

  github_api


# Wraps calls to the github API so we can throttle requests and respond to rate limiting
wrap_github_api = (github) ->
  props = [
    'gists',
    'gitdata',
    'issues',
    'authorization',
    'orgs',
    'statuses',
    'pullRequests',
    'repos',
    'user' ,
    'events',
    'search',
    'markdown'
  ]

  wrappedApis = {}
  for prop in props
    apis = github[prop] || {}
    wrappedApis[prop] = {}
    for key of apis
      wrappedApis[prop][key] = _.wrap apis[key], call_github_api


  wrappedApis.authenticate = (options) ->
    github.authenticate(options)

  return wrappedApis


# Handles github api call result to respond to rate limiting
handle_api_result = (cb) ->
  (err, res) ->
    # TODO handle rate limit result
    cb(err, res)

# Wraps the github api call and returns a thunk.
call_github_api = (api, args...) ->
  return (cb) ->
    # TODO fail fast if rate limited
    # TODO throttle concurrent requests
    args.push handle_api_result(cb)
    api.apply(this, args)


module.exports = wrap_github_api(create_github_api());
