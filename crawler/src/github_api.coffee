_ = require 'lodash'
async = require 'async'
GitHubApi = require 'github'
log = require './log'

max_concurrent_requests = 20

raw_github_api = do ->
  api = new GitHubApi
    version: '3.0.0',
    debug: false

  github_api_token = process.env.GITHUB_API_TOKEN
  if !github_api_token
    log.warn('Using unauthenticated github API which is extremely rate limited. Please set GITHUB_API_TOKEN environment variable.')
  else
    api.authenticate
      type: 'oauth',
      token: github_api_token

  api


process_api_call = (api_call, cb) ->
  api_call.api_function.apply(this, api_call.args)
  cb()

api_call_queue = async.queue(process_api_call, max_concurrent_requests)
rate_limit_reset_time = null

handle_rate_limiting = ->
  if not api_call_queue.paused
    api_call_queue.pause()
    now = Date.now()
    rate_limit_reset_ms = (rate_limit_reset_time * 1000) - now + 5000
    setTimeout ->
      log.info("Rate limiting finished, resuming")
      api_call_queue.resume()
    , rate_limit_reset_ms

    rate_limit_reset_secs = Math.round(rate_limit_reset_ms / 1000)
    rate_limit_reset_mins = Math.round(rate_limit_reset_secs / 60)
    log.info("Github API calls have been rate limited. Requests have been paused. Will try again in #{rate_limit_reset_secs} seconds (#{rate_limit_reset_mins} mins).")

update_rate_limit_reset_time = (res) ->
  if res and res.meta && res.meta['x-ratelimit-reset']
    new_reset_time = Number(res.meta['x-ratelimit-reset'])
    if (rate_limit_reset_time && rate_limit_reset_time < new_reset_time) or !rate_limit_reset_time
      rate_limit_reset_time = new_reset_time

refresh_rate_limit_reset_time = (cb) ->
  log.debug("Fetching rate limiting info from github API")
  raw_github_api.misc.rateLimit {}, (err, res) ->
    update_rate_limit_reset_time(res)
    cb()

# Handles github api call result to respond to rate limiting
handle_api_result = (task, cb) ->
  (err, res) ->
    update_rate_limit_reset_time(res)
    if err and err.code == 403 and err.message and err.message.indexOf("rate") > -1
      if !rate_limit_reset_time
        refresh_rate_limit_reset_time ->
          handle_rate_limiting()

      else
        handle_rate_limiting()

      api_call_queue.unshift(task)

    cb(err, res)


# Wraps the github api call and returns a thunk.
call_github_api = (api, args...) ->
  return (cb) ->
    task = {api_function: api, args: args}
    args.push handle_api_result(task, cb)
    api_call_queue.push(task)

# Wraps calls to the github API so we can throttle requests and respond to rate limiting
github_api = do ->
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
    'markdown',
    'misc'
  ]

  wrappedApis = {}
  for prop in props
    apis = raw_github_api[prop] || {}
    wrappedApis[prop] = {}
    for key of apis
      wrappedApis[prop][key] = _.wrap apis[key], call_github_api

  wrappedApis


module.exports = github_api
