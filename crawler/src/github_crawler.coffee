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
      (err) -> log.error("Error crawling #{repo_crawl_request}:", err, err.stack)
    )


  crawl_repository_info: (github_repo) ->
    github_repo_str = "#{github_repo.user}/#{github_repo.repo}"
    output_file = "/repos/raw/github/#{github_repo.user}/#{github_repo.repo}/repository.json"
    existing_etag = yield @extract_etag_from_file(output_file)
    request_params =
      user: github_repo.user
      repo: github_repo.repo
      headers:
        "If-None-Match": existing_etag

    log.debug("Crawling repository info for #{github_repo_str}")
    repo_info = yield github_api.repos.get request_params
    if repo_info.meta.status.indexOf("304") == 0
      log.debug("Repository info for #{github_repo_str} already crawled and is unchanged")
    else
      yield @write_json_file(output_file, repo_info)
      log.debug("Repository info for #{github_repo_str} written to #{output_file}")


  crawl_dependency_files: (github_repo) ->
    @crawl_file_history(github_repo, 'package.json')


  crawl_file_history: (github_repo, file_path) ->
    log.debug("Crawling #{github_repo.user}/#{github_repo.repo}/#{file_path}")
    commits = yield @commits_for_file(github_repo, file_path)
    # TODO probably want to limit simultaneous retrievals a bit here to avoid too many open connections?
    yield(@crawl_file_commit(github_repo, file_path, commit) for commit in commits)


  commits_for_file: (github_repo, file_path) ->
    page = 1
    page_size = 100
    commits = []
    loop
      log.debug("Retrieving commit history page #{page} for #{github_repo.user}/#{github_repo.repo}/#{file_path}")
      paged_commits = yield github_api.repos.getCommits
        user: github_repo.user
        repo: github_repo.repo
        path: file_path
        page: page
        per_page: page_size

      commits = commits.concat(paged_commits)
      break if paged_commits.length < page_size
      page++

    log.debug("Commit history for #{github_repo.user}/#{github_repo.repo}/#{file_path} retrieved #{commits.length} commits")
    return commits


  crawl_file_commit: (github_repo, file_path, commit) ->
    sha = commit.sha
    log.debug("Retrieving content for #{github_repo.user}/#{github_repo.repo}/#{file_path}##{sha}")
    timestamp = Date.parse commit.commit.committer.date
    file_dirname = path.dirname(file_path)
    file_extension = path.extname(file_path)
    file_basename = path.basename(file_path, file_extension)
    output_path = "/repos/raw/github/#{github_repo.user}/#{github_repo.repo}/#{file_dirname}/#{file_basename}_#{timestamp}_#{commit.sha}#{file_extension}"
    if not yield @output_file_system.exists(output_path)
      file_content = yield @crawl_file_contents(github_repo, file_path, sha)
      yield @write_json_file(output_path, file_content)
      log.debug("Content for #{github_repo.user}/#{github_repo.repo}/#{file_path}##{sha} written to #{output_path}")
    else
      log.debug("File #{github_repo.user}/#{github_repo.repo}/#{file_path}##{sha} already retrieved")


  crawl_file_contents: (github_repo, file_path, sha) ->
    co ->
      return yield github_api.repos.getContent
        user: github_repo.user
        repo: github_repo.repo
        path: file_path
        ref: sha
    .then(
      (val) -> return val
      (err) ->
        if err.code == 404
          log.debug("File #{github_repo.user}/#{github_repo.repo}/#{file_path}##{sha} was deleted")
          return null
        else
          throw err
    )


  parse_github_repo: (repo) ->
    [user, repo] = repo.split '/'
    return {
      user: user
      repo: repo
    }


  write_json_file: (file_path, content) ->
    if content
      content_to_write = if @pretty_json then JSON.stringify(content, null, 2) else JSON.stringify(content)
    else
      content_to_write = ""
    @output_file_system.write_file file_path, content_to_write


  extract_etag_from_file: (file_path) ->
    co =>
      file_content = yield @output_file_system.read_file(file_path)
    .then(
      (val) ->
        file_json = JSON.parse val
        return file_json.meta.etag
      (err) ->
        return null
    )
