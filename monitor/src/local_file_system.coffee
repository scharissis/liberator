path = require 'path'
co = require 'co'
raw_fs = require 'fs'
fs = require 'co-fs'
mkdirp = require('co-fs-plus').mkdirp

module.exports = class LocalFileSystem
  constructor: (@base_dir) ->

  read_file: (filename) ->
    full_path = path.resolve(path.join(@base_dir, filename))
    yield fs.readFile full_path, 'utf8'

  write_file: (filename, data) ->
    output_path = path.resolve(path.join(@base_dir, filename))
    yield mkdirp path.dirname(output_path)
    yield fs.writeFile(output_path, data)

  exists: (filename) ->
    full_path = path.resolve(path.join(@base_dir, filename))
    yield fs.exists(full_path)

  create_write_stream: (filename) ->
    full_path = path.resolve(path.join(@base_dir, filename))
    yield mkdirp path.dirname(full_path)
    raw_fs.createWriteStream(full_path)

  rename: (old_filename, new_filename) ->
    old_full_path = path.resolve(path.join(@base_dir, old_filename))
    new_full_path = path.resolve(path.join(@base_dir, new_filename))
    yield fs.rename(old_full_path, new_full_path)
