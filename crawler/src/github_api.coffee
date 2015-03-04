_ = require 'lodash'
async = require 'async'
GitHubApi = require 'github'
log = require './log'

max_concurrent_requests = 10

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

update_rate_limit_reset_time = (res) ->
  if res and res.meta && res.meta['x-ratelimit-reset']
    new_reset_time = Number(res.meta['x-ratelimit-reset'])
    if (rate_limit_reset_time && rate_limit_reset_time < new_reset_time) or !rate_limit_reset_time
      rate_limit_reset_time = new_reset_time

refresh_rate_limit_reset_time = (cb) ->
  log.debug("Fetching rate limiting info from github API")
  raw_github_api.misc.rateLimit {}, (err, res) ->
    log.debug("Fetch rate limiting info complete")
    update_rate_limit_reset_time(res)
    cb()

set_resume_timeout = (resume_time_in_seconds) ->
  now = Date.now()
  if not resume_time_in_seconds
    # No information about rate limit reset time - just try again in a few minutes
    resume_time_in_seconds = Math.round(now/1000 + 5*60)

  resume_period_ms = (resume_time_in_seconds * 1000) - now + 5000
  setTimeout ->
    log.info("Rate limiting finished, resuming")
    api_call_queue.resume()
  , resume_period_ms

  resume_period_secs = Math.round(resume_period_ms / 1000)
  resume_period_mins = Math.round(resume_period_secs / 60)
  log.info("Github API calls have been rate limited. Requests have been paused. Will try again in #{resume_period_secs} seconds (#{resume_period_mins} mins).")

handle_rate_limiting = (task) ->
  if not api_call_queue.paused
    api_call_queue.pause()
    if not rate_limit_reset_time
      refresh_rate_limit_reset_time ->
        set_resume_timeout(rate_limit_reset_time)
    else
      set_resume_timeout(rate_limit_reset_time)

  api_call_queue.unshift(task)
  log.info("API call rate limited id " + task.id + ", queue size: " + api_call_queue.length())


# Returns a function that handles github api call result (responds to rate limiting etc)
api_result_handler = (task, cb) ->
  (err, res) ->
    log.info("Handling API result id " + task.id)
    update_rate_limit_reset_time(res)
    if err and err.code == 403 and err.message and err.message.indexOf("rate") > -1
      handle_rate_limiting(task)
    else
      log.info("API call successful id " + task.id + ", queue size: " + api_call_queue.length())
      cb(err, res)

id = 1

# Wraps the github api call and returns a thunk.
call_github_api = (api, args...) ->
  return (cb) ->
    task_id = id++
    task = {api_function: api, args: args, id: task_id}
    args.push api_result_handler(task, cb)
    api_call_queue.push(task)
    log.debug("Added new task API call id " + task_id + ", queue size: " + api_call_queue.length())

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
