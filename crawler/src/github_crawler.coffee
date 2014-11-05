co = require 'co'
log = require './log'
github_api = require './github_api'

module.exports = class GithubCrawler

  constructor: (@output_file_system, @pretty_json = true) ->

  crawl: (repo_crawl_request) ->
    log.info "Crawling #{repo_crawl_request}"
    github_repo = @parse_github_repo(repo_crawl_request.repo)
    @crawl_repository_info(github_repo)
    @crawl_package_json(github_repo)

  crawl_repository_info: (github_repo) ->
    do co =>
      github_repo_str = "#{github_repo.user}/#{github_repo.repo}"
      log.debug("Crawling repository info for #{github_repo_str}")
      repo_info = yield github_api.repos.get(github_repo)
      yield @write_json_file("/repos/raw/github/#{github_repo.user}/#{github_repo.repo}/repository.json", repo_info)
      log.debug("Crawl of repository info for #{github_repo_str} complete")

  crawl_package_json: (github_repo) ->
    do co =>
      github_repo_str = "#{github_repo.user}/#{github_repo.repo}"
      log.debug("Crawling package.json for #{github_repo_str}")
      package_json_content = yield github_api.repos.getContent
        user: github_repo.user
        repo: github_repo.repo
        path: 'package.json'

      yield @write_json_file("/repos/raw/github/#{github_repo.user}/#{github_repo.repo}/package.json", package_json_content)
      log.debug("Crawl of package.json for #{github_repo_str} complete")

  parse_github_repo: (repo) ->
    [user, repo] = repo.split '/'
    return {
      user: user
      repo: repo
    }

  write_json_file: (path, content) ->
    content_to_write = if @pretty_json then JSON.stringify(content, null, 2) else JSON.stringify(content)
    @output_file_system.write_file path, content_to_write
