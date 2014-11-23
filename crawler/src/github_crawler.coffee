path = require 'path'
co = require 'co'
log = require './log'
github_api = require './github_api'

module.exports = class GithubCrawler

  constructor: (@output_file_system, @pretty_json = true) ->


  crawl: (repo_crawl_request) ->
    log.info "Crawling #{repo_crawl_request}"
    github_repo = @parse_github_repo(repo_crawl_request.repo)
    @crawl_repository_info(github_repo)
    @crawl_dependency_files(github_repo)


  crawl_repository_info: (github_repo) ->
    do co =>
      github_repo_str = "#{github_repo.user}/#{github_repo.repo}"
      log.debug("Crawling repository info for #{github_repo_str}")
      repo_info = yield github_api.repos.get(github_repo)
      yield @write_json_file("/repos/raw/github/#{github_repo.user}/#{github_repo.repo}/repository.json", repo_info)
      log.debug("Crawl of repository info for #{github_repo_str} complete")


  crawl_dependency_files: (github_repo) ->
    @crawl_file_history(github_repo, 'package.json')


  crawl_file_history: (github_repo, file_path) ->
    do co =>
      log.debug("Crawling #{github_repo.user}/#{github_repo.repo}/#{file_path}")
      commits = yield @commits_for_file(github_repo, file_path)
      @crawl_file_commit(github_repo, file_path, commit) for commit in commits


  crawl_file_commit: (github_repo, file_path, commit) ->
    do co =>
      sha = commit.sha
      log.debug("Retrieving #{github_repo.user}/#{github_repo.repo}/#{file_path}##{sha} contents")
      timestamp = Date.parse commit.commit.committer.date
      file_content = yield github_api.repos.getContent
        user: github_repo.user
        repo: github_repo.repo
        path: file_path
        ref: sha

      file_dirname = path.dirname(file_path)
      file_extension = path.extname(file_path)
      file_basename = path.basename(file_path, file_extension)
      output_path = "/repos/raw/github/#{github_repo.user}/#{github_repo.repo}/#{file_dirname}/#{file_basename}_#{timestamp}_#{commit.sha}#{file_extension}"
      yield @write_json_file(output_path, file_content)
      log.debug("Content for #{github_repo.user}/#{github_repo.repo}/#{file_path}##{sha} written to #{output_path}")


  commits_for_file: (github_repo, file_path) ->
    log.debug("Retrieving commit history for #{github_repo.user}/#{github_repo.repo}/#{file_path}")
    commits = yield github_api.repos.getCommits
      user: github_repo.user
      repo: github_repo.repo
      path: file_path
      per_page: 100

    # TODO pagination
    log.debug("Commit history for #{github_repo.user}/#{github_repo.repo}/#{file_path} retrieved")
    return commits


  parse_github_repo: (repo) ->
    [user, repo] = repo.split '/'
    return {
      user: user
      repo: repo
    }


  write_json_file: (file_path, content) ->
    content_to_write = if @pretty_json then JSON.stringify(content, null, 2) else JSON.stringify(content)
    @output_file_system.write_file file_path, content_to_write
