path = require 'path'
co = require 'co'
fs = require 'co-fs'
mkdirp = require('co-fs-plus').mkdirp

module.exports = class LocalFileSystem
  constructor: (@base_dir) ->

  write_file: (filename, data) ->
    return new Promise (resolve, reject) =>
      do co =>
        output_path = path.resolve(path.join(@base_dir, filename))
        yield mkdirp path.dirname(output_path)
        yield fs.writeFile(output_path, data)
        resolve()
