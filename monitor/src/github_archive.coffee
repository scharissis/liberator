http = require 'http'
co = require 'co'
log = require './log'

module.exports = class GithubArchive
  github_archive_base_url = "http://data.githubarchive.org"

  constructor: (@output_file_system, @output_dir) ->

  download: (github_archive_file, output_file_system, output_dir) ->
    url = github_archive_file.github_archive_url()
    filename = github_archive_file.filename()
    output_file_path = "#{output_dir}/#{filename}"

    tmp_filename = "#{filename}.tmp"
    tmp_output_file_path = "#{output_dir}/#{tmp_filename}"
    tmp_output_file = yield output_file_system.create_write_stream(tmp_output_file_path)
    log.info("Downloading #{url} to #{output_file_path}")
    request = http.get url, (response) =>
      response.pipe tmp_output_file
    .on 'close', =>
      log.info("done")
      co ->
        yield output_file_system.rename(tmp_output_file_path, output_file_path)
      log.info("Download of #{url} complete")
    .on 'error', (err) =>
      log.error("Download of #{url} failed", err, err.stack)
