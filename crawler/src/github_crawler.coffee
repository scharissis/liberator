path = require 'path'
co = require 'co'
log = require './log'
github_api = require './github_api'

module.exports = class GithubCrawler

  constructor: (@output_file_system, @pretty_json = true) ->

  crawl: (repo_crawl_request) ->
    log.info "Crawling #{repo_crawl_request}"
    github_repo = @parse_github_repo(repo_crawl_request.repo)
    co =>
      yield [
        @crawl_repository_info(github_repo),
        @crawl_dependency_files(github_repo)
      ]
    .then(
      (val) -> log.info("Crawl of #{repo_crawl_request} complete")
      (err) -> log.error("Error crawling #{repo_crawl_request}:", err)
    )


  crawl_repository_info: (github_repo) ->
    github_repo_str = "#{github_repo.user}/#{github_repo.repo}"
    output_file = "/repos/raw/github/#{github_repo.user}/#{github_repo.repo}/repository.json"
    log.debug("Crawling repository info for #{github_repo_str}")
    repo_info = yield github_api.repos.get(github_repo)
    yield @write_json_file(output_file, repo_info)
    log.debug("Repository info for #{github_repo_str} written to #{output_file}")


  crawl_dependency_files: (github_repo) ->
    @crawl_file_history(github_repo, 'package.json')


  crawl_file_history: (github_repo, file_path) ->
    log.debug("Crawling #{github_repo.user}/#{github_repo.repo}/#{file_path}")
    commits = yield @commits_for_file(github_repo, file_path)
    yield(@crawl_file_commit(github_repo, file_path, commit) for commit in commits)


  crawl_file_commit: (github_repo, file_path, commit) ->
    sha = commit.sha
    log.debug("Retrieving content for #{github_repo.user}/#{github_repo.repo}/#{file_path}##{sha}")
    timestamp = Date.parse commit.commit.committer.date
    file_dirname = path.dirname(file_path)
    file_extension = path.extname(file_path)
    file_basename = path.basename(file_path, file_extension)
    output_path = "/repos/raw/github/#{github_repo.user}/#{github_repo.repo}/#{file_dirname}/#{file_basename}_#{timestamp}_#{commit.sha}#{file_extension}"
    file_content = yield github_api.repos.getContent
      user: github_repo.user
      repo: github_repo.repo
      path: file_path
      ref: sha

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
