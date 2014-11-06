co = require 'co'
log = require './log'
github_api = require './github_api'

module.exports = class GithubCrawler

  constructor: (@output_file_system) ->

  crawl: (repo_crawl_request) ->
    @crawl_repository_info(repo_crawl_request)

  crawl_repository_info: (repo_crawl_request) ->
    do co =>
      log.info "Crawling #{repo_crawl_request}"
      github_repo = @parse_github_repo(repo_crawl_request.repo)
      repo_info = yield github_api.repos.get(github_repo)
      dest_file = "/repos/raw/github/#{repo_crawl_request.repo}/repository.json"
      yield @output_file_system.write_file dest_file, JSON.stringify(repo_info)
      log.info "Crawl of #{repo_crawl_request} complete"

  parse_github_repo: (repo)->
    [user, repo] = repo.split '/'
    return {
      user: user
      repo: repo
    }
