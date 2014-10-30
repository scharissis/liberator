GitHubApi = require 'github'
cogithub = require 'co-github'

module.exports = cogithub(new GitHubApi({
  version: '3.0.0',
  debug: false
}));
