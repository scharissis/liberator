co = require 'co'

LocalFileSystem = require './local_file_system'
GithubArchive = require './github_archive'
GithubArchiveFile = require './github_archive_file'

co =>
  output_file_system = new LocalFileSystem("./output")
  github_archive = new GithubArchive
  github_archive_file = new GithubArchiveFile('2015-01-02', '13')
  github_archive.download(github_archive_file, output_file_system, '/repo_activity/github/new')
