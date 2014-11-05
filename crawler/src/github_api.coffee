GitHubApi = require 'github'
cogithub = require 'co-github'
log = require './log'

githubApi = new GitHubApi
  version: '3.0.0',
  debug: false

github_api_token = process.env.GITHUB_API_TOKEN
if !github_api_token
  log.warn('Using unauthenticated github API which is extremely rate limited. Please set GITHUB_API_TOKEN environment variable.')
else
  githubApi.authenticate
    type: 'oauth',
    token: github_api_token

module.exports = cogithub githubApi;
