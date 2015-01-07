path = require 'path'
co = require 'co'
fs = require 'co-fs'
mkdirp = require('co-fs-plus').mkdirp

module.exports = class LocalFileSystem
  constructor: (@base_dir) ->

  write_file: (filename, data) ->
    output_path = path.resolve(path.join(@base_dir, filename))
    yield mkdirp path.dirname(output_path)
    yield fs.writeFile(output_path, data)

  exists: (filename) ->
    full_path = path.resolve(path.join(@base_dir, filename))
    yield fs.exists(full_path)
